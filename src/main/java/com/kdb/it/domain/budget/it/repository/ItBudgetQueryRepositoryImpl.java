package com.kdb.it.domain.budget.it.repository;
import com.kdb.it.common.code.CommonCodeGroups;

import com.kdb.it.common.code.entity.QCcodem;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.it.dto.ItBudgetDto;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.budget.work.entity.QBbugtm;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 정보기술부문 예산 집계 QueryDSL 구현체
 *
 * <p>
 * 4개의 집계 쿼리(BITEMM 요청액, BCOSTM 요청액, BITEMM 기반 BBUGTM 편성액, BCOSTM 기반 BBUGTM 편성액)를
 * 실행한 후 서비스 계층으로 병합하지 않고 이 Repository에서 직접 병합하여 반환합니다.
 * </p>
 */
@Repository
@RequiredArgsConstructor
public class ItBudgetQueryRepositoryImpl implements ItBudgetQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 정보보호여부 'Y' */
    private static final String INF_PRT_Y = "Y";
    /** IOE 코드 cId */
    private static final String C_ID_IOE = CommonCodeGroups.IOE;
    /** BBUGTM 원본테이블: 품목 */
    private static final String ORC_TB_ITEM = "BITEMM";
    /** BBUGTM 원본테이블: 전산업무비 */
    private static final String ORC_TB_COST = "BCOSTM";

    /**
     * {@inheritDoc}
     *
     * <p>집계 흐름:</p>
     * <ol>
     *   <li>BITEMM × BPROJM → (ioeC, infPrtYn) 별 편성요청액</li>
     *   <li>BCOSTM → (ioeC, infPrtYn) 별 편성요청액</li>
     *   <li>BBUGTM(orcTb=BITEMM) × BITEMM → (ioeC, infPrtYn) 별 편성액</li>
     *   <li>BBUGTM(orcTb=BCOSTM) × BCOSTM → (ioeC, infPrtYn) 별 편성액</li>
     *   <li>CCODEM(cId='IOE') → ioeCode → cdvaNm 조회</li>
     *   <li>전체 병합 후 ioeCode 오름차순 정렬</li>
     * </ol>
     */
    @Override
    public List<ItBudgetDto.CategoryRow> findSummary(String bgYy) {
        // 집계 맵: key = ioeCode, value = [itReq, itAdj, secReq, secAdj] (원 단위)
        Map<String, long[]> accumulator = new HashMap<>();

        // 1. BITEMM × BPROJM 편성요청액
        accumulateItemReq(bgYy, accumulator);

        // 2. BCOSTM 편성요청액
        accumulateCostReq(bgYy, accumulator);

        // 3. BBUGTM(BITEMM) 편성액
        accumulateItemAdj(bgYy, accumulator);

        // 4. BBUGTM(BCOSTM) 편성액
        accumulateCostAdj(bgYy, accumulator);

        // 5. 코드명 조회
        Map<String, String> codeNmMap = loadCodeNames();

        // 6. 결과 빌드 (ioeCode 오름차순)
        List<ItBudgetDto.CategoryRow> rows = new ArrayList<>();
        for (String ioeCode : new TreeSet<>(accumulator.keySet())) {
            long[] v = accumulator.get(ioeCode);
            long itReq = toThousand(v[0]);
            long itAdj = toThousand(v[1]);
            long secReq = toThousand(v[2]);
            long secAdj = toThousand(v[3]);
            rows.add(new ItBudgetDto.CategoryRow(
                    ioeCode,
                    codeNmMap.getOrDefault(ioeCode, ioeCode),
                    itReq, itAdj, secReq, secAdj,
                    itReq + secReq, itAdj + secAdj
            ));
        }
        return rows;
    }

    /** BITEMM × BPROJM → 편성요청액 누적 */
    private void accumulateItemReq(String bgYy, Map<String, long[]> acc) {
        QBitemm i = QBitemm.bitemm;
        QBprojm p = QBprojm.bprojm;

        NumberExpression<BigDecimal> effectiveAmt = Expressions.numberTemplate(
                BigDecimal.class, "COALESCE({0}, 1.0) * {1}", i.xcr, i.amt);

        List<Tuple> rows = queryFactory
                .select(i.ioeC, i.sectSysUtzYn, effectiveAmt.sum())
                .from(i)
                .join(p).on(
                        p.abusMngNo.eq(i.abusMngNo),
                        p.sno.eq(i.fntTbCrySno))
                .where(
                        i.delYn.eq("N"),
                        i.lstYn.eq("Y"),
                        i.amt.isNotNull(),
                        p.delYn.eq("N"),
                        p.lstYn.eq("Y"),
                        p.bseYy.eq(bgYy))
                .groupBy(i.ioeC, i.sectSysUtzYn)
                .fetch();

        for (Tuple row : rows) {
            String ioeC = row.get(i.ioeC);
            String prtYn = row.get(i.sectSysUtzYn);
            BigDecimal amt = row.get(effectiveAmt.sum());
            if (ioeC == null || amt == null) continue;
            long[] v = acc.computeIfAbsent(ioeC, k -> new long[4]);
            if (INF_PRT_Y.equals(prtYn)) {
                v[2] += amt.longValue(); // secReq
            } else {
                v[0] += amt.longValue(); // itReq
            }
        }
    }

    /** BCOSTM → 편성요청액 누적 */
    private void accumulateCostReq(String bgYy, Map<String, long[]> acc) {
        QBcostm c = QBcostm.bcostm;

        List<Tuple> rows = queryFactory
                .select(c.ioeC, c.sectSysUtzYn, c.costTotXpAmt.sum())
                .from(c)
                .where(
                        c.delYn.eq("N"),
                        c.lstYn.eq("Y"),
                        c.bseYy.eq(bgYy),
                        c.ioeC.isNotNull(),
                        c.costTotXpAmt.isNotNull())
                .groupBy(c.ioeC, c.sectSysUtzYn)
                .fetch();

        for (Tuple row : rows) {
            String ioeC = row.get(c.ioeC);
            String prtYn = row.get(c.sectSysUtzYn);
            BigDecimal amt = row.get(c.costTotXpAmt.sum());
            if (ioeC == null || amt == null) continue;
            long[] v = acc.computeIfAbsent(ioeC, k -> new long[4]);
            if (INF_PRT_Y.equals(prtYn)) {
                v[2] += amt.longValue(); // secReq
            } else {
                v[0] += amt.longValue(); // itReq
            }
        }
    }

    /** BBUGTM(orcTb=BITEMM) × BITEMM → 편성액 누적 */
    private void accumulateItemAdj(String bgYy, Map<String, long[]> acc) {
        QBbugtm bg = QBbugtm.bbugtm;
        QBitemm bi = new QBitemm("bi");

        List<Tuple> rows = queryFactory
                .select(bg.ioeC, bi.sectSysUtzYn, bg.bgDupAmt.sum())
                .from(bg)
                .join(bi).on(
                        bg.fntTbNm.eq(ORC_TB_ITEM),
                        bg.pkColNm.eq(bi.gclMngNo),
                        bg.fntTbCrySno.eq(bi.sno),
                        bi.delYn.eq("N"),
                        bi.lstYn.eq("Y"))
                .where(
                        bg.bseYy.eq(bgYy),
                        bg.delYn.eq("N"),
                        bg.fntTbNm.eq(ORC_TB_ITEM))
                .groupBy(bg.ioeC, bi.sectSysUtzYn)
                .fetch();

        for (Tuple row : rows) {
            String ioeC = row.get(bg.ioeC);
            String prtYn = row.get(bi.sectSysUtzYn);
            BigDecimal amt = row.get(bg.bgDupAmt.sum());
            if (ioeC == null || amt == null) continue;
            long[] v = acc.computeIfAbsent(ioeC, k -> new long[4]);
            if (INF_PRT_Y.equals(prtYn)) {
                v[3] += amt.longValue(); // secAdj
            } else {
                v[1] += amt.longValue(); // itAdj
            }
        }
    }

    /** BBUGTM(orcTb=BCOSTM) × BCOSTM → 편성액 누적 */
    private void accumulateCostAdj(String bgYy, Map<String, long[]> acc) {
        QBbugtm bg = QBbugtm.bbugtm;
        QBcostm bc = new QBcostm("bc");

        List<Tuple> rows = queryFactory
                .select(bg.ioeC, bc.sectSysUtzYn, bg.bgDupAmt.sum())
                .from(bg)
                .join(bc).on(
                        bg.fntTbNm.eq(ORC_TB_COST),
                        bg.pkColNm.eq(bc.costBgNo),
                        bg.fntTbCrySno.eq(bc.bgSno),
                        bc.delYn.eq("N"),
                        bc.lstYn.eq("Y"))
                .where(
                        bg.bseYy.eq(bgYy),
                        bg.delYn.eq("N"),
                        bg.fntTbNm.eq(ORC_TB_COST))
                .groupBy(bg.ioeC, bc.sectSysUtzYn)
                .fetch();

        for (Tuple row : rows) {
            String ioeC = row.get(bg.ioeC);
            String prtYn = row.get(bc.sectSysUtzYn);
            BigDecimal amt = row.get(bg.bgDupAmt.sum());
            if (ioeC == null || amt == null) continue;
            long[] v = acc.computeIfAbsent(ioeC, k -> new long[4]);
            if (INF_PRT_Y.equals(prtYn)) {
                v[3] += amt.longValue(); // secAdj
            } else {
                v[1] += amt.longValue(); // itAdj
            }
        }
    }

    /** CCODEM(cId='IOE') 에서 cdva → cdvaNm 맵 로드 */
    private Map<String, String> loadCodeNames() {
        QCcodem c = QCcodem.ccodem;
        // 시작·종료일자는 'YYYYMMDD' 문자열이므로 기준일자도 동일 형식으로 비교
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        List<Tuple> rows = queryFactory
                .select(c.cdva, c.cdvaNm)
                .from(c)
                .where(
                        c.cId.eq(C_ID_IOE),
                        c.delYn.eq("N"),
                        c.sttDt.loe(today),
                        c.endDt.isNull().or(c.endDt.goe(today)))
                .fetch();

        Map<String, String> map = new LinkedHashMap<>();
        for (Tuple row : rows) {
            String cdva = row.get(c.cdva);
            String nm = row.get(c.cdvaNm);
            if (cdva != null) map.put(cdva, nm != null ? nm : cdva);
        }
        return map;
    }

    /** 원 단위 합계를 천원 단위로 변환 (TRUNCATE) */
    private long toThousand(long amtWon) {
        return amtWon / 1000;
    }
}
