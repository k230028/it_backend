package com.kdb.it.domain.budget.cost.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.querydsl.core.Tuple;
import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 전산관리비(Bcostm) 커스텀 리포지토리 QueryDSL 구현체
 *
 * <p>
 * {@link CostRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다.
 * 복잡한 동적 쿼리(apfSts 필터링 서브쿼리 포함)를 타입 안전하게 처리합니다.
 * </p>
 *
 * <p>
 * 클래스 명명 규칙: Spring Data JPA가 자동 감지하려면
 * 반드시 {@code [메인Repository명]Impl} 형태여야 합니다. ({@code CostRepositoryImpl})
 * </p>
 *
 * <p>
 * {@code apfSts} 서브쿼리 전략:
 * </p>
 * <ul>
 * <li>{@code "none"}: NOT EXISTS — CAPPLA에 연결 레코드가 없는 전산관리비</li>
 * <li>그 외 값: EXISTS — 최신 CAPPLA(APF_REL_SNO MAX)의 CAPPLM 결재상태가 일치하는 전산관리비</li>
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class CostRepositoryImpl implements CostRepositoryCustom {

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    /**
     * 검색 조건으로 전산관리비 목록 동적 조회
     *
     * <p>
     * [처리 순서]
     * 1. DEL_YN='N' 기본 조건 설정
     * 2. apfSts 조건 분기 처리 (none / 특정값 / null)
     * 3. 나머지 단순 필드 조건 추가 (biceDpmC, biceTemC, infPrtYn)
     * 4. BooleanBuilder로 조합된 WHERE 절로 쿼리 실행
     * </p>
     *
     * <p>
     * apfSts='none' 생성 SQL (NOT EXISTS):
     * </p>
     *
     * <pre>{@code
     * WHERE NOT EXISTS (
     *   SELECT 1 FROM TPRMPP_CAPPLA ca
     *   WHERE ca.ORC_TB_CD = 'BCOSTM'
     *     AND ca.ORC_PK_VL = c.IT_MNGC_NO
     *     AND ca.ORC_SNO_VL = c.IT_MNGC_SNO
     * )
     * }</pre>
     *
     * <p>
     * apfSts='결재중' 생성 SQL (EXISTS + MAX 서브쿼리):
     * </p>
     *
     * <pre>{@code
     * WHERE EXISTS (
     *   SELECT 1 FROM TPRMPP_CAPPLA ca
     *   JOIN TPRMPP_CAPPLM cm ON ca.APF_MNG_NO = cm.APF_MNG_NO
     *   WHERE ca.ORC_TB_CD = 'BCOSTM'
     *     AND ca.ORC_PK_VL = c.IT_MNGC_NO
     *     AND ca.ORC_SNO_VL = c.IT_MNGC_SNO
     *     AND cm.APF_STS = '결재중'
     *     AND ca.APF_REL_SNO = (
     *       SELECT MAX(ca2.APF_REL_SNO) FROM TPRMPP_CAPPLA ca2
     *       WHERE ca2.ORC_TB_CD = 'BCOSTM'
     *         AND ca2.ORC_PK_VL = c.IT_MNGC_NO
     *         AND ca2.ORC_SNO_VL = c.IT_MNGC_SNO
     *     )
     * )
     * }</pre>
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 전산관리비 목록
     */
    @Override
    public List<Bcostm> searchByCondition(CostDto.SearchCondition condition) {
        QBcostm bcostm = QBcostm.bcostm;
        // 서브쿼리용 CAPPLA Q타입 별칭 (자기 참조 서브쿼리 충돌 방지)
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();

        // 기본 조건: 삭제되지 않은 전산관리비만 조회
        builder.and(bcostm.delYn.eq("N"));

        // === apfSts 필터 처리 ===
        String apfSts = condition.getApfSts();
        if (apfSts != null && !apfSts.isBlank()) {
            if ("none".equals(apfSts)) {
                // 미상신: CAPPLA 연결 없음 OR 최신 CAPPLM의 APF_STS_C가 반려(003)/회수(004)
                BooleanExpression notLinked = JPAExpressions.selectOne()
                        .from(cappla)
                        .where(
                                cappla.orcTbCd.eq("BCOSTM"),
                                cappla.orcPkVl.eq(bcostm.itMngcNo),
                                cappla.orcSnoVl.eq(bcostm.itMngcSno))
                        .notExists();

                BooleanExpression latestTerminatedNonComplete = Expressions.numberTemplate(Integer.class,
                        "(SELECT CASE WHEN c.APF_STS_C IN ('003','004') THEN 1 ELSE 0 END " +
                        "  FROM TPRMPP_CAPPLM c JOIN TPRMPP_CAPPLA m ON c.APF_MNG_NO = m.APF_MNG_NO " +
                        "  WHERE m.ORC_TB_CD = 'BCOSTM' AND m.ORC_PK_VL = {0} AND m.ORC_SNO_VL = {1} " +
                        "  ORDER BY c.RQS_DT DESC FETCH FIRST 1 ROWS ONLY)",
                        bcostm.itMngcNo, bcostm.itMngcSno).eq(1);

                builder.and(notLinked.or(latestTerminatedNonComplete));
            } else {
                // 특정 결재상태: 최신 신청서(APF_REL_SNO 최대값)의 결재상태가 일치하는 경우
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfMngNo.eq(capplm.apfMngNo),
                                        cappla.orcTbCd.eq("BCOSTM"),
                                        cappla.orcPkVl.eq(bcostm.itMngcNo),
                                        cappla.orcSnoVl.eq(bcostm.itMngcSno),
                                        capplm.apfSts.eq(apfSts),
                                        // 해당 전산관리비에 연결된 신청서 중 가장 최신(APF_REL_SNO 최대)인 것만 검사
                                        cappla.apfRelSno.eq(
                                                JPAExpressions.select(cappla2.apfRelSno.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.orcTbCd.eq("BCOSTM"),
                                                                cappla2.orcPkVl.eq(bcostm.itMngcNo),
                                                                cappla2.orcSnoVl.eq(bcostm.itMngcSno))))
                                .exists());
            }
        }

        // === 단순 필드 조건 처리 (null이면 해당 조건 미적용) ===

        // 연관부서 필터
        if (condition.getBiceDpmC() != null && !condition.getBiceDpmC().isBlank()) {
            builder.and(bcostm.biceDpmC.eq(condition.getBiceDpmC()));
        }
        // 연관팀 필터
        if (condition.getBiceTemC() != null && !condition.getBiceTemC().isBlank()) {
            builder.and(bcostm.biceTemC.eq(condition.getBiceTemC()));
        }
        // 정보보호여부 필터
        if (condition.getInfPrtYn() != null && !condition.getInfPrtYn().isBlank()) {
            builder.and(bcostm.infPrtYn.eq(condition.getInfPrtYn()));
        }
        // 예산연도 필터
        if (condition.getBgYy() != null && !condition.getBgYy().isBlank()) {
            builder.and(bcostm.bgYy.eq(condition.getBgYy()));
        }

        return queryFactory
                .selectFrom(bcostm)
                .where(builder)
                .fetch();
    }

    /**
     * 전년도 예산 합계 일괄 조회
     *
     * <p>IT_MNGC_NO별 전년도(prevYear) IT_MNGC_BG 합계를 집계하여 반환합니다.</p>
     */
    @Override
    public Map<String, BigDecimal> sumPrevBgByItMngcNos(List<String> itMngcNos, String prevYear) {
        if (itMngcNos == null || itMngcNos.isEmpty()) return Map.of();
        QBcostm bcostm = QBcostm.bcostm;
        List<Tuple> results = queryFactory
                .select(bcostm.itMngcNo, bcostm.itMngcBgAmt.sum())
                .from(bcostm)
                .where(
                        bcostm.bgYy.eq(prevYear),
                        bcostm.itMngcNo.in(itMngcNos),
                        bcostm.delYn.eq("N"))
                .groupBy(bcostm.itMngcNo)
                .fetch();
        return results.stream().collect(Collectors.toMap(
                t -> t.get(bcostm.itMngcNo),
                t -> {
                    BigDecimal sum = t.get(bcostm.itMngcBgAmt.sum());
                    return sum != null ? sum : BigDecimal.ZERO;
                }));
    }
}
