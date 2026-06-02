package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.kdb.it.domain.budget.work.entity.QBbugtm;

import lombok.RequiredArgsConstructor;

/**
 * 예산(BBUGTM) 커스텀 리포지토리 QueryDSL 구현체
 *
 * <p>
 * 결재완료 필터 + 비목 접두어 매칭 쿼리를 타입 안전하게 처리합니다.
 * 기존 {@code CostRepositoryImpl}, {@code ProjectRepositoryImpl}의
 * CAPPLA+CAPPLM 서브쿼리 패턴을 재사용합니다.
 * </p>
 *
 * // Design Ref: §4.6 — QueryDSL 구현 (결재완료 필터 + 비목 매칭)
 */
@RequiredArgsConstructor
public class BbugtmRepositoryImpl implements BbugtmRepositoryCustom {

    /** QueryDSL 쿼리 팩토리 */
    private final JPAQueryFactory queryFactory;

    /**
     * 결재완료 전산업무비(BCOSTM) 중 비목코드가 접두어와 매칭되는 목록 조회
     *
     * <p>
     * [생성 SQL 예시]
     * </p>
     * <pre>{@code
     * SELECT * FROM TPRMPP_BCOSTM c
     * WHERE c.DEL_YN = 'N' AND c.LST_YN = 'Y'
     *   AND c.IOE_C LIKE '237%'
     *   AND EXISTS (
     *     SELECT 1 FROM TPRMPP_CAPPLA ca
     *     JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
     *     WHERE ca.FNT_TB_NM = 'BCOSTM'
     *       AND ca.PK_COL_NM = c.IT_MNGC_NO
     *       AND ca.FNT_TB_CRY_SNO = c.BG_SNO
     *       AND cm.APF_PRG_STS_C = '002'
     *       AND ca.APF_SNO = (
     *         SELECT MAX(ca2.APF_SNO) FROM TPRMPP_CAPPLA ca2
     *         WHERE ca2.FNT_TB_NM = 'BCOSTM'
     *           AND ca2.PK_COL_NM = c.IT_MNGC_NO
     *           AND ca2.FNT_TB_CRY_SNO = c.BG_SNO
     *       )
     *   )
     * }</pre>
     */
    @Override
    public List<Bcostm> findApprovedCostsByIoeCValues(Set<String> ioeCValues, String bgYy) {
        if (ioeCValues == null || ioeCValues.isEmpty()) return List.of();
        QBcostm bcostm = QBcostm.bcostm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();

        // 기본 조건: 삭제되지 않은 최종 레코드만
        builder.and(bcostm.delYn.eq("N"));
        builder.and(bcostm.lstYn.eq("Y"));

        // 비목코드 IN 매칭 (V003 마이그레이션 후 IOE_C는 단축 cdva 저장)
        builder.and(bcostm.ioeC.in(ioeCValues));

        // 예산연도 필터
        builder.and(bcostm.bgYy.eq(bgYy));

        // 결재완료 서브쿼리 (CostRepositoryImpl 패턴 동일)
        builder.and(
                JPAExpressions.selectOne()
                        .from(cappla, capplm)
                        .where(
                                cappla.apfDcmNo.eq(capplm.apfMngNo),
                                cappla.fntTbNm.eq("BCOSTM"),
                                cappla.pkColNm.eq(bcostm.itMngcNo),
                                cappla.fntTbCrySno.eq(bcostm.itMngcSno),
                                capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                cappla.apfDcmNo.eq(
                                        JPAExpressions.select(cappla2.apfDcmNo.max())
                                                .from(cappla2)
                                                .where(
                                                        cappla2.fntTbNm.eq("BCOSTM"),
                                                        cappla2.pkColNm.eq(bcostm.itMngcNo),
                                                        cappla2.fntTbCrySno.eq(bcostm.itMngcSno))))
                        .exists());

        return queryFactory
                .selectFrom(bcostm)
                .where(builder)
                .fetch();
    }

    /**
     * 결재완료 품목(BITEMM) 중 품목구분이 접두어와 매칭되는 목록 조회
     *
     * <p>
     * BITEMM은 BPROJM의 하위 테이블이므로, BPROJM 기준으로 결재완료를 확인한 뒤
     * 해당 BPROJM에 속하는 BITEMM 중 GCL_DTT가 접두어와 매칭되는 것을 반환합니다.
     * </p>
     *
     * <p>
     * [생성 SQL 예시]
     * </p>
     * <pre>{@code
     * SELECT i.* FROM TPRMPP_BITEMM i
     * WHERE i.DEL_YN = 'N' AND i.LST_YN = 'Y'
     *   AND i.GCL_DTT LIKE '237%'
     *   AND EXISTS (
     *     SELECT 1 FROM TPRMPP_BPROJM p
     *     WHERE p.PRJ_MNG_NO = i.PRJ_MNG_NO AND p.PRJ_SNO = i.PRJ_SNO
     *       AND p.DEL_YN = 'N' AND p.LST_YN = 'Y'
     *       AND EXISTS (
     *         SELECT 1 FROM TPRMPP_CAPPLA ca
     *         JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
     *         WHERE ca.FNT_TB_NM = 'BPROJM'
     *           AND ca.PK_COL_NM = p.PRJ_MNG_NO
     *           AND ca.FNT_TB_CRY_SNO = p.PRJ_SNO
     *           AND cm.APF_PRG_STS_C = '002'
     *           AND ca.APF_SNO = (
     *             SELECT MAX(ca2.APF_SNO) FROM TPRMPP_CAPPLA ca2
     *             WHERE ca2.FNT_TB_NM = 'BPROJM'
     *               AND ca2.PK_COL_NM = p.PRJ_MNG_NO
     *               AND ca2.FNT_TB_CRY_SNO = p.PRJ_SNO
     *           )
     *       )
     *   )
     * }</pre>
     */
    @Override
    public List<Bitemm> findApprovedItemsByIoeCValues(Set<String> ioeCValues, String bgYy) {
        if (ioeCValues == null || ioeCValues.isEmpty()) return List.of();
        QBitemm bitemm = QBitemm.bitemm;
        QBprojm bprojm = QBprojm.bprojm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        // BPROJM 결재완료 서브쿼리
        BooleanBuilder projApprovalBuilder = new BooleanBuilder();
        projApprovalBuilder.and(
                JPAExpressions.selectOne()
                        .from(cappla, capplm)
                        .where(
                                cappla.apfDcmNo.eq(capplm.apfMngNo),
                                cappla.fntTbNm.eq("BPROJM"),
                                cappla.pkColNm.eq(bprojm.prjMngNo),
                                cappla.fntTbCrySno.eq(bprojm.prjSno),
                                capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                cappla.apfDcmNo.eq(
                                        JPAExpressions.select(cappla2.apfDcmNo.max())
                                                .from(cappla2)
                                                .where(
                                                        cappla2.fntTbNm.eq("BPROJM"),
                                                        cappla2.pkColNm.eq(bprojm.prjMngNo),
                                                        cappla2.fntTbCrySno.eq(bprojm.prjSno))))
                        .exists());

        // BITEMM 조건: 삭제되지 않은 최종 레코드 + 비목코드 IN 매칭
        BooleanBuilder builder = new BooleanBuilder();
        builder.and(bitemm.delYn.eq("N"));
        builder.and(bitemm.lstYn.eq("Y"));
        builder.and(bitemm.ioeC.in(ioeCValues));

        // BITEMM의 상위 BPROJM이 결재완료 상태 + 예산연도 일치 확인
        builder.and(
                JPAExpressions.selectOne()
                        .from(bprojm)
                        .where(
                                bprojm.prjMngNo.eq(bitemm.prjMngNo),
                                bprojm.prjSno.eq(bitemm.prjSno),
                                bprojm.delYn.eq("N"),
                                bprojm.lstYn.eq("Y"),
                                bprojm.bgYy.eq(bgYy),
                                projApprovalBuilder)
                        .exists());

        return queryFactory
                .selectFrom(bitemm)
                .where(builder)
                .fetch();
    }

    /**
     * 정보화사업(BPROJM)별 편성예산(DUP_BG) 합계 일괄 조회
     *
     * <p>
     * 정보화사업 편성 데이터는 BITEMM 기준으로 저장되므로 BITEMM 조인 후
     * prjMngNo별 SUM(DUP_BG)를 집계합니다.
     * </p>
     */
    @Override
    public Map<String, BigDecimal> sumDupBgByPrjMngNos(List<String> prjMngNos, String bgYy) {
        if (prjMngNos == null || prjMngNos.isEmpty()) return Map.of();
        QBbugtm bbugtm = QBbugtm.bbugtm;
        QBitemm bitemm = QBitemm.bitemm;
        // 정보화사업 편성은 BITEMM 단위로 저장(ORC_TB='BITEMM', ORC_PK_VL=GCL_MNG_NO)
        // → BITEMM.PRJ_MNG_NO 기준으로 JOIN 후 GROUP BY
        List<Tuple> results = queryFactory
                .select(bitemm.prjMngNo, bbugtm.dupBgAmt.sum())
                .from(bbugtm)
                .join(bitemm).on(
                        bbugtm.orcPkVl.eq(bitemm.gclMngNo),
                        bbugtm.orcSnoVl.eq(bitemm.gclSno))
                .where(
                        bbugtm.bgYy.eq(bgYy),
                        bbugtm.orcTb.eq("BITEMM"),
                        bitemm.prjMngNo.in(prjMngNos),
                        bbugtm.delYn.eq("N"),
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"))
                .groupBy(bitemm.prjMngNo)
                .fetch();
        Map<String, BigDecimal> map = new HashMap<>();
        for (Tuple t : results) {
            String key = t.get(bitemm.prjMngNo);
            BigDecimal sum = t.get(bbugtm.dupBgAmt.sum());
            if (key != null) map.put(key, sum != null ? sum : BigDecimal.ZERO);
        }
        return map;
    }

    /**
     * 전산업무비(BCOSTM)별 편성예산(DUP_BG) 합계 일괄 조회
     *
     * <p>
     * ORC_TB='BCOSTM' 조건으로 orcPkVl(itMngcNo)별 SUM(DUP_BG)를 집계합니다.
     * </p>
     */
    @Override
    public Map<String, BigDecimal> sumDupBgByItMngcNos(List<String> itMngcNos, String bgYy) {
        if (itMngcNos == null || itMngcNos.isEmpty()) return Map.of();
        QBbugtm bbugtm = QBbugtm.bbugtm;
        List<Tuple> results = queryFactory
                .select(bbugtm.orcPkVl, bbugtm.dupBgAmt.sum())
                .from(bbugtm)
                .where(
                        bbugtm.bgYy.eq(bgYy),
                        bbugtm.orcTb.eq("BCOSTM"),
                        bbugtm.orcPkVl.in(itMngcNos),
                        bbugtm.delYn.eq("N"))
                .groupBy(bbugtm.orcPkVl)
                .fetch();
        Map<String, BigDecimal> map = new HashMap<>();
        for (Tuple t : results) {
            String key = t.get(bbugtm.orcPkVl);
            BigDecimal sum = t.get(bbugtm.dupBgAmt.sum());
            if (key != null) map.put(key, sum != null ? sum : BigDecimal.ZERO);
        }
        return map;
    }

    /**
     * 정보화사업별 자본예산 편성예산(DUP_BG) 합계 조회 (gclDtt 코드 기준)
     */
    @Override
    public Map<String, BigDecimal> sumAssetDupBgByPrjMngNos(List<String> prjMngNos, String bgYy, Set<String> assetGclDttCodes) {
        if (prjMngNos == null || prjMngNos.isEmpty() || assetGclDttCodes == null || assetGclDttCodes.isEmpty())
            return Map.of();
        QBbugtm bbugtm = QBbugtm.bbugtm;
        QBitemm bitemm = QBitemm.bitemm;
        List<Tuple> results = queryFactory
                .select(bitemm.prjMngNo, bbugtm.dupBgAmt.sum())
                .from(bbugtm)
                .join(bitemm).on(
                        bbugtm.orcPkVl.eq(bitemm.gclMngNo),
                        bbugtm.orcSnoVl.eq(bitemm.gclSno))
                .where(
                        bbugtm.bgYy.eq(bgYy),
                        bbugtm.orcTb.eq("BITEMM"),
                        bitemm.prjMngNo.in(prjMngNos),
                        bitemm.ioeC.in(assetGclDttCodes),
                        bbugtm.delYn.eq("N"),
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"))
                .groupBy(bitemm.prjMngNo)
                .fetch();
        Map<String, BigDecimal> map = new HashMap<>();
        for (Tuple t : results) {
            String key = t.get(bitemm.prjMngNo);
            BigDecimal sum = t.get(bbugtm.dupBgAmt.sum());
            if (key != null) map.put(key, sum != null ? sum : BigDecimal.ZERO);
        }
        return map;
    }

    /**
     * 정보화사업별 일반관리비 편성예산(DUP_BG) 합계 조회 (gclDtt 코드 기준)
     */
    @Override
    public Map<String, BigDecimal> sumCostDupBgByPrjMngNos(List<String> prjMngNos, String bgYy, Set<String> costGclDttCodes) {
        if (prjMngNos == null || prjMngNos.isEmpty() || costGclDttCodes == null || costGclDttCodes.isEmpty())
            return Map.of();
        QBbugtm bbugtm = QBbugtm.bbugtm;
        QBitemm bitemm = QBitemm.bitemm;
        List<Tuple> results = queryFactory
                .select(bitemm.prjMngNo, bbugtm.dupBgAmt.sum())
                .from(bbugtm)
                .join(bitemm).on(
                        bbugtm.orcPkVl.eq(bitemm.gclMngNo),
                        bbugtm.orcSnoVl.eq(bitemm.gclSno))
                .where(
                        bbugtm.bgYy.eq(bgYy),
                        bbugtm.orcTb.eq("BITEMM"),
                        bitemm.prjMngNo.in(prjMngNos),
                        bitemm.ioeC.in(costGclDttCodes),
                        bbugtm.delYn.eq("N"),
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"))
                .groupBy(bitemm.prjMngNo)
                .fetch();
        Map<String, BigDecimal> map = new HashMap<>();
        for (Tuple t : results) {
            String key = t.get(bitemm.prjMngNo);
            BigDecimal sum = t.get(bbugtm.dupBgAmt.sum());
            if (key != null) map.put(key, sum != null ? sum : BigDecimal.ZERO);
        }
        return map;
    }

    /**
     * 비목 접두어별 결재완료 요청금액 합계 조회
     *
     * <p>
     * BCOSTM의 IT_MNGC_BG 합계와 BITEMM의 GCL_AMT 합계를 더합니다.
     * </p>
     */
    @Override
    public BigDecimal sumApprovedAmountByIoeCValues(Set<String> ioeCValues, String bgYy) {
        if (ioeCValues == null || ioeCValues.isEmpty()) return BigDecimal.ZERO;
        BigDecimal costSum = sumApprovedCostAmountByIoeCValues(ioeCValues, bgYy);
        BigDecimal itemSum = sumApprovedItemAmountByIoeCValues(ioeCValues, bgYy);

        BigDecimal total = BigDecimal.ZERO;
        if (costSum != null) total = total.add(costSum);
        if (itemSum != null) total = total.add(itemSum);
        return total;
    }

    /**
     * 결재완료 BCOSTM의 비목코드 집합별 금액 합계
     */
    private BigDecimal sumApprovedCostAmountByIoeCValues(Set<String> ioeCValues, String bgYy) {
        QBcostm bcostm = QBcostm.bcostm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(bcostm.delYn.eq("N"));
        builder.and(bcostm.lstYn.eq("Y"));
        builder.and(bcostm.ioeC.in(ioeCValues));
        builder.and(bcostm.bgYy.eq(bgYy));

        // 결재완료 서브쿼리
        builder.and(
                JPAExpressions.selectOne()
                        .from(cappla, capplm)
                        .where(
                                cappla.apfDcmNo.eq(capplm.apfMngNo),
                                cappla.fntTbNm.eq("BCOSTM"),
                                cappla.pkColNm.eq(bcostm.itMngcNo),
                                cappla.fntTbCrySno.eq(bcostm.itMngcSno),
                                capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                cappla.apfDcmNo.eq(
                                        JPAExpressions.select(cappla2.apfDcmNo.max())
                                                .from(cappla2)
                                                .where(
                                                        cappla2.fntTbNm.eq("BCOSTM"),
                                                        cappla2.pkColNm.eq(bcostm.itMngcNo),
                                                        cappla2.fntTbCrySno.eq(bcostm.itMngcSno))))
                        .exists());

        return queryFactory
                .select(bcostm.itMngcBgAmt.sum())
                .from(bcostm)
                .where(builder)
                .fetchOne();
    }

    /**
     * 결재완료 BITEMM의 비목코드 집합별 금액 합계 (환율 적용)
     *
     * <p>
     * SUM(GCL_AMT * COALESCE(XCR, 1)) — 외화 품목은 환율을 곱하여 원화로 변환합니다.
     * </p>
     */
    private BigDecimal sumApprovedItemAmountByIoeCValues(Set<String> ioeCValues, String bgYy) {
        QBitemm bitemm = QBitemm.bitemm;
        QBprojm bprojm = QBprojm.bprojm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();
        builder.and(bitemm.delYn.eq("N"));
        builder.and(bitemm.lstYn.eq("Y"));
        builder.and(bitemm.ioeC.in(ioeCValues));

        // BITEMM의 상위 BPROJM 결재완료 + 예산연도 서브쿼리
        builder.and(
                JPAExpressions.selectOne()
                        .from(bprojm)
                        .where(
                                bprojm.prjMngNo.eq(bitemm.prjMngNo),
                                bprojm.prjSno.eq(bitemm.prjSno),
                                bprojm.delYn.eq("N"),
                                bprojm.lstYn.eq("Y"),
                                bprojm.bgYy.eq(bgYy),
                                JPAExpressions.selectOne()
                                        .from(cappla, capplm)
                                        .where(
                                                cappla.apfDcmNo.eq(capplm.apfMngNo),
                                                cappla.fntTbNm.eq("BPROJM"),
                                                cappla.pkColNm.eq(bprojm.prjMngNo),
                                                cappla.fntTbCrySno.eq(bprojm.prjSno),
                                                capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                                cappla.apfDcmNo.eq(
                                                        JPAExpressions.select(cappla2.apfDcmNo.max())
                                                                .from(cappla2)
                                                                .where(
                                                                        cappla2.fntTbNm.eq("BPROJM"),
                                                                        cappla2.pkColNm.eq(bprojm.prjMngNo),
                                                                        cappla2.fntTbCrySno.eq(bprojm.prjSno))))
                                        .exists())
                        .exists());

        // SUM(GCL_AMT * COALESCE(XCR, 1)) — 환율 적용된 원화 금액 합산
        return queryFactory
                .select(Expressions.numberTemplate(BigDecimal.class,
                        "SUM({0} * COALESCE({1}, 1))", bitemm.gclAmt, bitemm.xcr))
                .from(bitemm)
                .where(builder)
                .fetchOne();
    }
}
