package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.kdb.it.domain.budget.work.entity.QBbugtm;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 예산 편성 결과 집계 쿼리 QueryDSL 구현체
 *
 * <p>결재완료 필터 + ioeC/gclDtt 별 GROUP BY 집계를 단일 쿼리로 처리합니다.
 * {@code BbugtmRepositoryImpl}의 결재완료 서브쿼리 패턴을 재사용합니다.</p>
 */
@Repository
@RequiredArgsConstructor
public class BudgetWorkQueryRepositoryImpl implements BudgetWorkQueryRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * {@inheritDoc}
     *
     * <p>생성 SQL 예시:
     * <pre>{@code
     * SELECT c.IOE_C, SUM(c.IT_MNGC_BG)
     * FROM BCOSTM c
     * WHERE c.DEL_YN='N' AND c.LST_YN='Y' AND c.BG_YY=? AND c.IOE_C IS NOT NULL
     *   AND EXISTS ( SELECT 1 FROM CAPPLA ca, CAPPLM cm ... AND cm.APF_STS='결재완료' )
     * GROUP BY c.IOE_C
     * }</pre></p>
     */
    @Override
    public Map<String, BigDecimal> findApprovedCostAmountByIoeC(String bgYy, java.util.Collection<String> srcPks) {
        QBcostm bcostm = QBcostm.bcostm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        List<Tuple> rows = queryFactory
                .select(bcostm.ioeC, bcostm.costTotXpAmt.sum())
                .from(bcostm)
                .where(
                        bcostm.delYn.eq("N"),
                        bcostm.lstYn.eq("Y"),
                        bcostm.bseYy.eq(bgYy),
                        bcostm.ioeC.isNotNull(),
                        bcostm.costTotXpAmt.isNotNull(),
                        // 선택 원본 한정(정보기술부문 계획 카드): 지정 시 해당 costBgNo만 요청액 집계
                        (srcPks == null || srcPks.isEmpty()) ? null : bcostm.costBgNo.in(srcPks),
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BCOSTM"),
                                        cappla.pkColNm.eq(bcostm.costBgNo),
                                        cappla.fntTbCrySno.eq(bcostm.bgSno),
                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BCOSTM"),
                                                                cappla2.pkColNm.eq(bcostm.costBgNo),
                                                                cappla2.fntTbCrySno.eq(bcostm.bgSno))))
                                .exists())
                .groupBy(bcostm.ioeC)
                .fetch();

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            String ioeC = row.get(bcostm.ioeC);
            BigDecimal amount = row.get(bcostm.costTotXpAmt.sum());
            if (ioeC != null && amount != null) {
                result.put(ioeC, amount);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>생성 SQL 예시:
     * <pre>{@code
     * SELECT i.GCL_DTT, SUM(COALESCE(i.XCR, 1.0) * i.GCL_AMT)
     * FROM BITEMM i
     * WHERE i.DEL_YN='N' AND i.LST_YN='Y' AND i.GCL_DTT IS NOT NULL AND i.GCL_AMT IS NOT NULL
     *   AND EXISTS ( SELECT 1 FROM BPROJM p WHERE p.BG_YY=? ... AND EXISTS(...결재완료...) )
     * GROUP BY i.GCL_DTT
     * }</pre></p>
     */
    @Override
    public Map<String, BigDecimal> findApprovedItemAmountByGclDtt(String bgYy, java.util.Collection<String> srcPks) {
        QBitemm bitemm = QBitemm.bitemm;
        QBprojm bprojm = QBprojm.bprojm;
        QBbugtm bbugtm = QBbugtm.bbugtm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        // BITEMM.amt는 이미 원화(KRW) 정규화 금액이므로 환율을 곱하지 않고 그대로 합산한다.
        // (과거 COALESCE(xcr,1)*amt는 외화 품목을 이중환산해 요청액을 부풀렸음 — 편성액과 동일 기준으로 정렬)
        List<Tuple> rows = queryFactory
                .select(bitemm.ioeC, bitemm.amt.sum())
                .from(bitemm)
                .where(
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"),
                        bitemm.ioeC.isNotNull(),
                        bitemm.amt.isNotNull(),
                        // 선택 원본 한정(정보기술부문 계획 카드): 지정 시 해당 gclMngNo만 요청액 집계
                        (srcPks == null || srcPks.isEmpty()) ? null : bitemm.gclMngNo.in(srcPks),
                        // 당해연도 예산작업(BBUGTM) 편성 대상 품목만 요청액으로 집계한다.
                        // 편성액(dupAmount)과 동일한 원본 기준이 되어 비목별 편성률 ≤ 100%가 보장된다.
                        // (사업의 bseYy로 거르면 2026 예산작업에 편성된 계속(2025) 사업 품목의 요청이 누락되어
                        //  편성 > 요청(>100%)이 되던 문제 수정)
                        JPAExpressions.selectOne()
                                .from(bbugtm)
                                .where(
                                        bbugtm.bseYy.eq(bgYy),
                                        bbugtm.delYn.eq("N"),
                                        bbugtm.fntTbNm.eq("BITEMM"),
                                        bbugtm.pkColNm.eq(bitemm.gclMngNo))
                                .exists(),
                        JPAExpressions.selectOne()
                                .from(bprojm)
                                .where(
                                        bprojm.abusMngNo.eq(bitemm.abusMngNo),
                                        bprojm.sno.eq(bitemm.fntTbCrySno),
                                        bprojm.delYn.eq("N"),
                                        bprojm.lstYn.eq("Y"),
                                        JPAExpressions.selectOne()
                                                .from(cappla, capplm)
                                                .where(
                                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                                        cappla.fntTbNm.eq("BPROJM"),
                                                        cappla.pkColNm.eq(bprojm.abusMngNo),
                                                        cappla.fntTbCrySno.eq(bprojm.sno),
                                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                                        cappla.apfDcmNo.eq(
                                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                                        .from(cappla2)
                                                                        .where(
                                                                                cappla2.fntTbNm.eq("BPROJM"),
                                                                                cappla2.pkColNm.eq(bprojm.abusMngNo),
                                                                                cappla2.fntTbCrySno.eq(bprojm.sno))))
                                                .exists())
                                .exists())
                .groupBy(bitemm.ioeC)
                .fetch();

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            String ioeC = row.get(bitemm.ioeC);
            BigDecimal amount = row.get(bitemm.amt.sum());
            if (ioeC != null && amount != null) {
                result.put(ioeC, amount);
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>결재완료 판정은 {@link #findApprovedCostAmountByIoeC}/{@link #findApprovedItemAmountByGclDtt}와
     * 동일한 CAPPLA/CAPPLM 최신 결재 EXISTS 패턴을 사용하여 요청금액 집계 기준과 일치시킵니다.</p>
     */
    @Override
    public Set<String> findApprovedSourcePks(String bgYy) {
        Set<String> pks = new HashSet<>();

        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        // 결재완료 전산업무비(BCOSTM) costBgNo
        QBcostm bcostm = QBcostm.bcostm;
        List<String> costPks = queryFactory
                .select(bcostm.costBgNo)
                .from(bcostm)
                .where(
                        bcostm.delYn.eq("N"),
                        bcostm.lstYn.eq("Y"),
                        // bseYy 제약 없음: 원본의 사업연도와 무관하게 "결재완료"인지만 판정한다.
                        // (예: bseYy=2025 사업이 2026 예산작업에 편성될 수 있으므로 연도로 제외하면 안 됨)
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BCOSTM"),
                                        cappla.pkColNm.eq(bcostm.costBgNo),
                                        cappla.fntTbCrySno.eq(bcostm.bgSno),
                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BCOSTM"),
                                                                cappla2.pkColNm.eq(bcostm.costBgNo),
                                                                cappla2.fntTbCrySno.eq(bcostm.bgSno))))
                                .exists())
                .fetch();
        pks.addAll(costPks);

        // 결재완료 정보화사업(BPROJM) 소속 품목(BITEMM) gclMngNo
        QBitemm bitemm = QBitemm.bitemm;
        QBprojm bprojm = QBprojm.bprojm;
        List<String> itemPks = queryFactory
                .select(bitemm.gclMngNo)
                .from(bitemm)
                .where(
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"),
                        JPAExpressions.selectOne()
                                .from(bprojm)
                                .where(
                                        bprojm.abusMngNo.eq(bitemm.abusMngNo),
                                        bprojm.sno.eq(bitemm.fntTbCrySno),
                                        bprojm.delYn.eq("N"),
                                        bprojm.lstYn.eq("Y"),
                                        // bseYy 제약 없음: 결재완료 여부만 판정 (연도 다른 사업도 예산작업 편성 대상)
                                        JPAExpressions.selectOne()
                                                .from(cappla, capplm)
                                                .where(
                                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                                        cappla.fntTbNm.eq("BPROJM"),
                                                        cappla.pkColNm.eq(bprojm.abusMngNo),
                                                        cappla.fntTbCrySno.eq(bprojm.sno),
                                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                                        cappla.apfDcmNo.eq(
                                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                                        .from(cappla2)
                                                                        .where(
                                                                                cappla2.fntTbNm.eq("BPROJM"),
                                                                                cappla2.pkColNm.eq(bprojm.abusMngNo),
                                                                                cappla2.fntTbCrySno.eq(bprojm.sno))))
                                                .exists())
                                .exists())
                .fetch();
        pks.addAll(itemPks);

        return pks;
    }
}
