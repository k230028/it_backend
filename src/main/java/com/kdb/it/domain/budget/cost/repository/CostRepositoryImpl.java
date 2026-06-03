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
 * <li>그 외 값: EXISTS — 최신 CAPPLA(APF_SNO MAX)의 CAPPLM 결재상태가 일치하는 전산관리비</li>
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
     *   WHERE ca.FNT_TB_NM = 'BCOSTM'
     *     AND ca.PK_COL_NM = c.IT_MNGC_NO
     *     AND ca.FNT_TB_CRY_SNO = c.BG_SNO
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
     *   JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
     *   WHERE ca.FNT_TB_NM = 'BCOSTM'
     *     AND ca.PK_COL_NM = c.IT_MNGC_NO
     *     AND ca.FNT_TB_CRY_SNO = c.BG_SNO
     *     AND cm.APF_PRG_STS_C = '001'
     *     AND ca.APF_SNO = (
     *       SELECT MAX(ca2.APF_SNO) FROM TPRMPP_CAPPLA ca2
     *       WHERE ca2.FNT_TB_NM = 'BCOSTM'
     *         AND ca2.PK_COL_NM = c.IT_MNGC_NO
     *         AND ca2.FNT_TB_CRY_SNO = c.BG_SNO
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
                // 미상신(재상신 가능 포함): 활성(001 결재중) 또는 완료(002 결재완료)인 CAPPLM이 없는 경우.
                // - 한 번도 상신 안 한 경우 → CAPPLA 자체 없음 → 자동 매칭
                // - 반려(003)/회수(004)만 존재하는 경우 → 활성/완료가 없으므로 매칭 (재상신 허용)
                // - 진행 중(001) 또는 완료(002)가 있으면 → 차단
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BCOSTM"),
                                        cappla.pkColNm.eq(bcostm.costBgNo),
                                        cappla.fntTbCrySno.eq(bcostm.bgSno),
                                        capplm.apfPrgStsC.in("01", "02"))
                                .notExists());
            } else {
                // 특정 결재상태: 최신 신청서(APF_DCM_NO 최대값)의 결재상태가 일치하는 경우
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BCOSTM"),
                                        cappla.pkColNm.eq(bcostm.costBgNo),
                                        cappla.fntTbCrySno.eq(bcostm.bgSno),
                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.hasLabel(apfSts)
                                                ? com.kdb.it.common.approval.domain.ApprovalStatus.ofLabel(apfSts).code()
                                                : apfSts),
                                        // 해당 전산관리비에 연결된 신청서 중 가장 최신(APF_DCM_NO 최대)인 것만 검사
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BCOSTM"),
                                                                cappla2.pkColNm.eq(bcostm.costBgNo),
                                                                cappla2.fntTbCrySno.eq(bcostm.bgSno))))
                                .exists());
            }
        }

        // === 단순 필드 조건 처리 (null이면 해당 조건 미적용) ===

        // 연관부서 필터
        if (condition.getCostSvnDpmC() != null && !condition.getCostSvnDpmC().isBlank()) {
            builder.and(bcostm.costSvnDpmC.eq(condition.getCostSvnDpmC()));
        }
        // 연관팀 필터
        if (condition.getSvnTemC() != null && !condition.getSvnTemC().isBlank()) {
            builder.and(bcostm.svnTemC.eq(condition.getSvnTemC()));
        }
        // 정보보호여부 필터
        if (condition.getSectSysUtzYn() != null && !condition.getSectSysUtzYn().isBlank()) {
            builder.and(bcostm.sectSysUtzYn.eq(condition.getSectSysUtzYn()));
        }
        // 예산연도 필터
        if (condition.getBseYy() != null && !condition.getBseYy().isBlank()) {
            builder.and(bcostm.bseYy.eq(condition.getBseYy()));
        }

        return queryFactory
                .selectFrom(bcostm)
                .where(builder)
                .fetch();
    }

    /**
     * 전년도 예산 합계 일괄 조회
     *
     * <p>costBgNo별 전년도(prevYear) TOT_XP_AMT 합계를 집계하여 반환합니다.</p>
     */
    @Override
    public Map<String, BigDecimal> sumPrevBgByCostBgNos(List<String> costBgNos, String prevYear) {
        if (costBgNos == null || costBgNos.isEmpty()) return Map.of();
        QBcostm bcostm = QBcostm.bcostm;
        List<Tuple> results = queryFactory
                .select(bcostm.costBgNo, bcostm.costTotXpAmt.sum())
                .from(bcostm)
                .where(
                        bcostm.bseYy.eq(prevYear),
                        bcostm.costBgNo.in(costBgNos),
                        bcostm.delYn.eq("N"))
                .groupBy(bcostm.costBgNo)
                .fetch();
        return results.stream().collect(Collectors.toMap(
                t -> t.get(bcostm.costBgNo),
                t -> {
                    BigDecimal sum = t.get(bcostm.costTotXpAmt.sum());
                    return sum != null ? sum : BigDecimal.ZERO;
                }));
    }
}
