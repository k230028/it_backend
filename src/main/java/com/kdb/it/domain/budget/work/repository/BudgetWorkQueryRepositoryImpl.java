package com.kdb.it.domain.budget.work.repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.kdb.it.domain.budget.project.entity.QBitemm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
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
    public Map<String, BigDecimal> findApprovedCostAmountByIoeC(String bgYy) {
        QBcostm bcostm = QBcostm.bcostm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        List<Tuple> rows = queryFactory
                .select(bcostm.ioeC, bcostm.itMngcBgAmt.sum())
                .from(bcostm)
                .where(
                        bcostm.delYn.eq("N"),
                        bcostm.lstYn.eq("Y"),
                        bcostm.bgYy.eq(bgYy),
                        bcostm.ioeC.isNotNull(),
                        bcostm.itMngcBgAmt.isNotNull(),
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfMngNo.eq(capplm.apfMngNo),
                                        cappla.orcTbCd.eq("BCOSTM"),
                                        cappla.orcPkVl.eq(bcostm.itMngcNo),
                                        cappla.orcSnoVl.eq(bcostm.itMngcSno),
                                        capplm.apfStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                        cappla.apfRelSno.eq(
                                                JPAExpressions.select(cappla2.apfRelSno.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.orcTbCd.eq("BCOSTM"),
                                                                cappla2.orcPkVl.eq(bcostm.itMngcNo),
                                                                cappla2.orcSnoVl.eq(bcostm.itMngcSno))))
                                .exists())
                .groupBy(bcostm.ioeC)
                .fetch();

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            String ioeC = row.get(bcostm.ioeC);
            BigDecimal amount = row.get(bcostm.itMngcBgAmt.sum());
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
    public Map<String, BigDecimal> findApprovedItemAmountByGclDtt(String bgYy) {
        QBitemm bitemm = QBitemm.bitemm;
        QBprojm bprojm = QBprojm.bprojm;
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        NumberTemplate<BigDecimal> effectiveAmt = Expressions.numberTemplate(
                BigDecimal.class, "COALESCE({0}, 1.0) * {1}", bitemm.xcr, bitemm.gclAmt);

        List<Tuple> rows = queryFactory
                .select(bitemm.ioeC, effectiveAmt.sum())
                .from(bitemm)
                .where(
                        bitemm.delYn.eq("N"),
                        bitemm.lstYn.eq("Y"),
                        bitemm.ioeC.isNotNull(),
                        bitemm.gclAmt.isNotNull(),
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
                                                        cappla.apfMngNo.eq(capplm.apfMngNo),
                                                        cappla.orcTbCd.eq("BPROJM"),
                                                        cappla.orcPkVl.eq(bprojm.prjMngNo),
                                                        cappla.orcSnoVl.eq(bprojm.prjSno),
                                                        capplm.apfStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.COMPLETED.code()),
                                                        cappla.apfRelSno.eq(
                                                                JPAExpressions.select(cappla2.apfRelSno.max())
                                                                        .from(cappla2)
                                                                        .where(
                                                                                cappla2.orcTbCd.eq("BPROJM"),
                                                                                cappla2.orcPkVl.eq(bprojm.prjMngNo),
                                                                                cappla2.orcSnoVl.eq(bprojm.prjSno))))
                                                .exists())
                                .exists())
                .groupBy(bitemm.ioeC)
                .fetch();

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Tuple row : rows) {
            String ioeC = row.get(bitemm.ioeC);
            BigDecimal amount = row.get(effectiveAmt.sum());
            if (ioeC != null && amount != null) {
                result.put(ioeC, amount);
            }
        }
        return result;
    }
}
