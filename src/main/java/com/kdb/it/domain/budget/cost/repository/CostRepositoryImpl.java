package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.common.util.ListPageParams;
import com.kdb.it.domain.budget.common.repository.BudgetListVersionScope;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.QBcostm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 전산관리비(Bcostm) 커스텀 리포지토리 QueryDSL 구현체
 *
 * <p>{@link CostRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다. 복잡한 동적 쿼리(apfSts 필터링 서브쿼리 포함)를 타입 안전하게
 * 처리합니다.
 *
 * <p>클래스 명명 규칙: Spring Data JPA가 자동 감지하려면 반드시 {@code [메인Repository명]Impl} 형태여야 합니다. ({@code
 * CostRepositoryImpl})
 *
 * <p>{@code apfSts} 서브쿼리 전략:
 *
 * <ul>
 *   <li>{@code "none"}: NOT EXISTS — CAPPLA에 연결 레코드가 없는 전산관리비
 *   <li>그 외 값: EXISTS — 최신 신청서(APF_DCM_NO MAX)의 CAPPLM 결재상태가 일치하는 전산관리비
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class CostRepositoryImpl implements CostRepositoryCustom {

    /** 목록 API가 한 요청에서 조립할 수 있는 최대 행 수입니다. */
    private static final long MAX_LIST_ROWS = 500;

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    /**
     * 검색 조건으로 전산관리비 목록 동적 조회
     *
     * <p>[처리 순서] 1. DEL_YN='N' 기본 조건 설정 2. apfSts 조건 분기 처리 (none / 특정값 / null) 3. 나머지 단순 필드 조건 추가
     * (biceDpmC, biceTemC, infPrtYn) 4. BooleanBuilder로 조합된 WHERE 절로 쿼리 실행
     *
     * <p>apfSts='none' 생성 SQL (NOT EXISTS):
     *
     * <pre>{@code
     * WHERE NOT EXISTS (
     *   SELECT 1 FROM TPRMPP_CAPPLA ca
     *   WHERE ca.FNT_TB_NM = 'BCOSTM'
     *     AND ca.PK_COL_NM = c.BG_NO
     *     AND ca.FNT_TB_CRY_SNO = c.BG_SNO
     * )
     * }</pre>
     *
     * <p>apfSts='결재중' 생성 SQL (EXISTS + MAX 서브쿼리):
     *
     * <pre>{@code
     * WHERE EXISTS (
     *   SELECT 1 FROM TPRMPP_CAPPLA ca
     *   JOIN TPRMPP_CAPPLM cm ON ca.APF_DCM_NO = cm.APF_DCM_NO
     *   WHERE ca.FNT_TB_NM = 'BCOSTM'
     *     AND ca.PK_COL_NM = c.BG_NO
     *     AND ca.FNT_TB_CRY_SNO = c.BG_SNO
     *     AND cm.IT_PTL_APF_PRG_STS_C = '1'
     *     AND ca.APF_DCM_NO = (
     *       SELECT MAX(ca2.APF_DCM_NO) FROM TPRMPP_CAPPLA ca2
     *       WHERE ca2.FNT_TB_NM = 'BCOSTM'
     *         AND ca2.PK_COL_NM = c.BG_NO
     *         AND ca2.FNT_TB_CRY_SNO = c.BG_SNO
     *     )
     * )
     * }</pre>
     *
     * <p>정렬은 예산번호 내림차순(최근 채번 우선)입니다. 상한이나 페이지 크기에 걸려 잘리는 쪽이 항상 오래된 건이 되도록 하기 위한 것으로, 오름차순이면 최근 등록한
     * 전산업무비가 목록에서 사라집니다. 페이지 경계에서 행이 겹치거나 빠지지 않도록 (예산번호, 예산순번)으로 안정 정렬합니다.
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 전산관리비 목록 (상한까지)
     */
    @Override
    public List<Bcostm> searchByCondition(CostDto.SearchCondition condition) {
        return searchByCondition(condition, ListPageParams.unpaged());
    }

    @Override
    public List<Bcostm> searchByCondition(
            CostDto.SearchCondition condition, ListPageParams paging) {
        QBcostm bcostm = QBcostm.bcostm;
        BooleanBuilder builder = buildConditionPredicate(condition);
        ListPageParams.Slice slice = paging.slice(MAX_LIST_ROWS);

        return queryFactory
                .selectFrom(bcostm)
                .where(builder)
                .orderBy(bcostm.costBgNo.desc(), bcostm.bgSno.asc())
                .offset(slice.offset())
                .limit(slice.limit())
                .fetch();
    }

    /**
     * 목록 경량 프로젝션 조회(#7) — {@link #searchByCondition}와 동일 WHERE, select만 목록 표시 컬럼으로 축소.
     *
     * <p>QueryDSL {@code Projections.constructor}는 위치 기반이므로 select 인자 순서가 {@link
     * CostDto.CostListRow} 컴포넌트 순서와 정확히 일치해야 한다.
     */
    @Override
    public List<CostDto.CostListRow> searchListByCondition(CostDto.SearchCondition condition) {
        QBcostm bcostm = QBcostm.bcostm;
        ListPageParams.Slice slice = ListPageParams.unpaged().slice(MAX_LIST_ROWS);
        // 동일 WHERE·정렬·구간 재사용 — searchByCondition과 결과 행 집합 동일, select만 경량화
        return queryFactory
                .select(
                        Projections.constructor(
                                CostDto.CostListRow.class,
                                bcostm.costBgNo,
                                bcostm.bgSno,
                                bcostm.lstYn,
                                bcostm.ioeC,
                                bcostm.cttNm,
                                bcostm.cttOppNm,
                                bcostm.costTotXpAmt,
                                bcostm.curC,
                                bcostm.sectSysUtzYn,
                                bcostm.costSvnDpmC,
                                bcostm.svnTemC,
                                bcostm.bseYy,
                                bcostm.abusTc,
                                bcostm.delYn))
                .from(bcostm)
                .where(buildConditionPredicate(condition))
                .orderBy(bcostm.costBgNo.desc(), bcostm.bgSno.asc())
                .offset(slice.offset())
                .limit(slice.limit())
                .fetch();
    }

    /**
     * 검색 조건에 해당하는 전산관리비 건수 (COUNT 쿼리, 전체 적재 회피)
     *
     * <p>{@link #searchByCondition(CostDto.SearchCondition)}와 동일한 WHERE 조건을 {@link
     * #buildConditionPredicate(CostDto.SearchCondition)}로 공유하므로 {@code
     * searchByCondition(...).size()}와 결과가 정확히 일치합니다.
     */
    @Override
    public long countBySearchCondition(CostDto.SearchCondition condition) {
        QBcostm bcostm = QBcostm.bcostm;
        Long cnt =
                queryFactory
                        .select(bcostm.count())
                        .from(bcostm)
                        .where(buildConditionPredicate(condition))
                        .fetchOne();
        return cnt == null ? 0L : cnt;
    }

    /**
     * 검색 조건 → QueryDSL WHERE 절(BooleanBuilder) 조립.
     *
     * <p>{@code searchByCondition}(목록)과 {@code countBySearchCondition}(건수)가 동일 조건을 공유하도록 조건 조립부를
     * 추출한 헬퍼입니다. apfSts EXISTS/NOT EXISTS 서브쿼리 포함.
     *
     * @param condition 검색 조건 DTO
     * @return DEL_YN='N' 및 동적 조건이 누적된 BooleanBuilder
     */
    private BooleanBuilder buildConditionPredicate(CostDto.SearchCondition condition) {
        QBcostm bcostm = QBcostm.bcostm;
        // 서브쿼리용 CAPPLA Q타입 별칭 (자기 참조 서브쿼리 충돌 방지)
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();

        // 미상신·결재중·반려·회수 목록은 LST_YN='N'인 재상신 초안도 보여야 하며,
        // 결재완료와 필터 없는 일반 업무는 최종본만 사용한다.
        builder.and(bcostm.delYn.eq("N"));
        // 라벨("결재중")로 들어온 입력을 코드("1")로 먼저 정규화해 이후 비교가 코드만 다루게 한다.
        String apfSts = BudgetListVersionScope.normalize(condition.getApfSts());
        if (!BudgetListVersionScope.includesDrafts(apfSts)) {
            builder.and(bcostm.lstYn.eq("Y"));
        }

        // === apfSts 필터 처리 ===
        if (apfSts != null && !apfSts.isBlank()) {
            if (BudgetListVersionScope.SCOPE_NONE.equals(apfSts)) {
                // 미상신(재상신 가능 포함): 활성(1 결재중) 또는 완료(2 결재완료)인 CAPPLM이 없는 경우.
                // - 한 번도 상신 안 한 경우 → CAPPLA 자체 없음 → 자동 매칭
                // - 반려(3)/회수(4)만 존재하는 경우 → 활성/완료가 없으므로 매칭 (재상신 허용)
                // - 진행 중(1) 또는 완료(2)가 있으면 → 차단
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BCOSTM"),
                                        cappla.pkColNm.eq(bcostm.costBgNo),
                                        cappla.fntTbCrySno.eq(bcostm.bgSno),
                                        capplm.itPtlApfPrgStsC.in(
                                                com.kdb.it.common.approval.domain.ApprovalStatus
                                                        .IN_PROGRESS
                                                        .code(),
                                                com.kdb.it.common.approval.domain.ApprovalStatus
                                                        .COMPLETED
                                                        .code()))
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
                                        // apfSts는 앞단에서 코드로 정규화했다.
                                        capplm.itPtlApfPrgStsC.eq(apfSts),
                                        // 해당 전산관리비에 연결된 신청서 중 가장 최신(APF_DCM_NO 최대)인 것만 검사
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BCOSTM"),
                                                                cappla2.pkColNm.eq(bcostm.costBgNo),
                                                                cappla2.fntTbCrySno.eq(
                                                                        bcostm.bgSno))))
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

        return builder;
    }

    /**
     * 전년도 예산 합계 일괄 조회
     *
     * <p>costBgNo별 전년도(prevYear) 예산 합계를 집계하여 반환합니다. 외화(curC≠'KRW') 행은 화면 예산 컬럼과 동일 기준인
     * FC_AMT(외화금액)를, 원화 행은 AMT(전산업무비예산금액)를 합산합니다.
     */
    @Override
    public Map<String, BigDecimal> sumPrevBgByCostBgNos(List<String> costBgNos, String prevYear) {
        if (costBgNos == null || costBgNos.isEmpty()) return Map.of();
        QBcostm bcostm = QBcostm.bcostm;
        // 외화 행은 fcAmt, 원화(또는 외화금액 미입력) 행은 costTotXpAmt 기준
        NumberExpression<BigDecimal> prevAmt =
                new CaseBuilder()
                        .when(
                                bcostm.curC
                                        .isNotNull()
                                        .and(bcostm.curC.ne("KRW"))
                                        .and(bcostm.fcAmt.isNotNull()))
                        .then(bcostm.fcAmt)
                        .otherwise(bcostm.costTotXpAmt);
        NumberExpression<BigDecimal> prevAmtSum = prevAmt.sum();
        List<Tuple> results =
                queryFactory
                        .select(bcostm.costBgNo, prevAmtSum)
                        .from(bcostm)
                        .where(
                                bcostm.bseYy.eq(prevYear),
                                bcostm.costBgNo.in(costBgNos),
                                bcostm.delYn.eq("N"),
                                bcostm.lstYn.eq("Y"))
                        .groupBy(bcostm.costBgNo)
                        .fetch();
        return results.stream()
                .collect(
                        Collectors.toMap(
                                t -> t.get(bcostm.costBgNo),
                                t -> {
                                    BigDecimal sum = t.get(prevAmtSum);
                                    return sum != null ? sum : BigDecimal.ZERO;
                                }));
    }
}
