package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.common.util.ListPageParams;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.dto.ProjectListRow;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.QBproja;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 정보화사업(Bprojm) 커스텀 리포지토리 QueryDSL 구현체
 *
 * <p>{@link ProjectRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다. 복잡한 동적 쿼리(apfSts 필터링 서브쿼리 포함)를 타입 안전하게
 * 처리합니다.
 *
 * <p>클래스 명명 규칙: Spring Data JPA가 자동 감지하려면 반드시 {@code [메인Repository명]Impl} 형태여야 합니다. ({@code
 * ProjectRepositoryImpl})
 *
 * <p>{@code apfSts} 서브쿼리 전략:
 *
 * <ul>
 *   <li>{@code "none"}: NOT EXISTS — CAPPLA에 연결 레코드가 없는 프로젝트
 *   <li>그 외 값: EXISTS — 최신 신청서(APF_DCM_NO MAX)의 CAPPLM 결재상태가 일치하는 프로젝트
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {

    /** 목록 API가 한 요청에서 조립할 수 있는 최대 행 수입니다. */
    private static final long MAX_LIST_ROWS = 500;

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    /**
     * 검색 조건으로 정보화사업 목록 동적 조회
     *
     * <p>[처리 순서] 1. DEL_YN='N' 기본 조건 설정 2. apfSts 조건 분기 처리 (none / 특정값 / null) 3. 나머지 단순 필드 조건 추가
     * (bgYy, prjSts, prjTp, itDpm, svnDpm) 4. BooleanBuilder로 조합된 WHERE 절로 쿼리 실행
     *
     * <p>정렬은 사업관리번호 내림차순(최근 채번 우선)입니다. 상한이나 페이지 크기에 걸려 잘리는 쪽이 항상 오래된 건이 되도록 하기 위한 것으로, 오름차순이면 최근
     * 등록한 사업이 목록에서 사라집니다. 페이지 경계에서 행이 겹치거나 빠지지 않도록 (사업관리번호, 순번)으로 안정 정렬합니다.
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 정보화사업 목록 (상한까지)
     */
    @Override
    public List<Bprojm> searchByCondition(ProjectDto.SearchCondition condition) {
        return searchByCondition(condition, ListPageParams.unpaged());
    }

    @Override
    public List<Bprojm> searchByCondition(
            ProjectDto.SearchCondition condition, ListPageParams paging) {
        QBprojm bprojm = QBprojm.bprojm;
        BooleanBuilder builder = buildConditionPredicate(condition);
        ListPageParams.Slice slice = paging.slice(MAX_LIST_ROWS);

        return queryFactory
                .selectFrom(bprojm)
                .where(builder)
                .orderBy(bprojm.abusMngNo.desc(), bprojm.sno.asc())
                .offset(slice.offset())
                .limit(slice.limit())
                .fetch();
    }

    /**
     * 목록 경량 프로젝션 조회(#7) — {@link #searchByCondition}와 동일 WHERE, select만 대용량 텍스트 제외 컬럼으로 축소.
     *
     * <p>QueryDSL {@code Projections.constructor}는 위치 기반이므로 select 인자 순서가 {@link ProjectListRow}
     * 컴포넌트 순서와 정확히 일치해야 한다.
     */
    @Override
    public List<ProjectListRow> searchListByCondition(ProjectDto.SearchCondition condition) {
        QBprojm bprojm = QBprojm.bprojm;
        ListPageParams.Slice slice = ListPageParams.unpaged().slice(MAX_LIST_ROWS);
        // 동일 WHERE·정렬·구간 재사용 — searchByCondition과 결과 행 집합 동일, select만 경량화
        return queryFactory
                .select(
                        Projections.constructor(
                                ProjectListRow.class,
                                bprojm.abusMngNo,
                                bprojm.sno,
                                bprojm.abusNm,
                                bprojm.bzTpC,
                                bprojm.svnDpmC,
                                bprojm.dvmDpmC,
                                bprojm.sttDtm,
                                bprojm.endDtm,
                                bprojm.bseYy,
                                bprojm.odnYn,
                                bprojm.abusTc,
                                bprojm.rprStsTc,
                                bprojm.delYn))
                .from(bprojm)
                .where(buildConditionPredicate(condition))
                .orderBy(bprojm.abusMngNo.desc(), bprojm.sno.asc())
                .offset(slice.offset())
                .limit(slice.limit())
                .fetch();
    }

    /**
     * 검색 조건에 해당하는 정보화사업 건수 (COUNT 쿼리, 전체 적재 회피)
     *
     * <p>{@link #searchByCondition(ProjectDto.SearchCondition)}와 동일한 WHERE 조건을 {@link
     * #buildConditionPredicate(ProjectDto.SearchCondition)}로 공유하므로 {@code
     * searchByCondition(...).size()}와 결과가 정확히 일치합니다.
     */
    @Override
    public long countBySearchCondition(ProjectDto.SearchCondition condition) {
        QBprojm bprojm = QBprojm.bprojm;
        Long cnt =
                queryFactory
                        .select(bprojm.count())
                        .from(bprojm)
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
    private BooleanBuilder buildConditionPredicate(ProjectDto.SearchCondition condition) {
        QBprojm bprojm = QBprojm.bprojm;
        // 서브쿼리용 CAPPLA Q타입 별칭 (자기 참조 서브쿼리 충돌 방지)
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();

        builder.and(bprojm.delYn.eq("N"));

        // === apfSts 필터 처리 ===
        String apfSts = condition.getApfSts();
        // 일반 업무는 확정본만 쓰되, 결재 상신 대상 조회는 결재 전 재신청 초안(SNO+1)도 보여야 한다.
        // 미상신 조건이 원본 결재완료본을 NOT EXISTS로 제외하므로 초안만 상신 대상으로 남는다.
        if (!"none".equals(apfSts)) {
            builder.and(bprojm.lstYn.eq("Y"));
        }
        if (apfSts != null && !apfSts.isBlank()) {
            if ("none".equals(apfSts)) {
                // 미상신(재상신 가능 포함): 활성(1 결재중) 또는 완료(2 결재완료)인 CAPPLM이 없는 경우.
                // - 한 번도 상신 안 한 경우 → CAPPLA 자체 없음 → 자동 매칭
                // - 반려(3)/회수(4)만 존재하는 경우 → 활성/완료가 없으므로 매칭 (재상신 허용)
                // - 진행 중(1) 또는 완료(2)가 있으면 → 차단
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfDcmNo.eq(capplm.apfMngNo),
                                        cappla.fntTbNm.eq("BPROJM"),
                                        cappla.pkColNm.eq(bprojm.abusMngNo),
                                        cappla.fntTbCrySno.eq(bprojm.sno),
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
                                        cappla.fntTbNm.eq("BPROJM"),
                                        cappla.pkColNm.eq(bprojm.abusMngNo),
                                        cappla.fntTbCrySno.eq(bprojm.sno),
                                        capplm.itPtlApfPrgStsC.eq(
                                                com.kdb.it.common.approval.domain.ApprovalStatus
                                                                .hasLabel(apfSts)
                                                        ? com.kdb.it.common.approval.domain
                                                                .ApprovalStatus.ofLabel(apfSts)
                                                                .code()
                                                        : apfSts),
                                        // 해당 프로젝트에 연결된 신청서 중 가장 최신(APF_DCM_NO 최대)인 것만 검사
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BPROJM"),
                                                                cappla2.pkColNm.eq(
                                                                        bprojm.abusMngNo),
                                                                cappla2.fntTbCrySno.eq(
                                                                        bprojm.sno))))
                                .exists());
            }
        }

        // === 단순 필드 조건 처리 (null이면 해당 조건 미적용) ===

        // 사업연도 필터
        if (condition.getBseYy() != null && !condition.getBseYy().isBlank()) {
            builder.and(bprojm.bseYy.eq(condition.getBseYy()));
        }
        // 프로젝트상태 필터: 대표상태가 조건과 일치하는 프로젝트만.
        // BPROJA는 (ABUS_MNG_NO, CNCD_RFR_NO) 단위의 단계 문서별 상태 테이블이고 사업 자신의 상태는
        // CNCD_RFR_NO = ABUS_MNG_NO인 행 하나뿐이므로 그 행만 본다. 종전에는 행 전체의 MAX와 비교해
        // 상위 계획·사업계획 등 다른 문서의 상태로 필터가 걸렸다(BE-33). 자신의 행은 하나라
        // "이 행이 최댓값인가"를 확인하던 서브쿼리도 함께 사라졌다.
        // 표시값(ProjectQueryAssembler.representativeStatus)과 같은 규칙을 쓴다.
        if (condition.getStsTc() != null && !condition.getStsTc().isBlank()) {
            QBproja bproja = new QBproja("bproja");
            builder.and(
                    JPAExpressions.selectOne()
                            .from(bproja)
                            .where(
                                    bproja.abusMngNo.eq(bprojm.abusMngNo),
                                    bproja.cncdRfrNo.eq(bprojm.abusMngNo),
                                    bproja.delYn.eq("N"),
                                    bproja.stsTc.eq(condition.getStsTc()))
                            .exists());
        }
        // 프로젝트유형 필터
        if (condition.getBzTpC() != null && !condition.getBzTpC().isBlank()) {
            builder.and(bprojm.bzTpC.eq(condition.getBzTpC()));
        }
        // IT부서 필터
        if (condition.getDvmDpmC() != null && !condition.getDvmDpmC().isBlank()) {
            builder.and(bprojm.dvmDpmC.eq(condition.getDvmDpmC()));
        }
        // 주관부서 필터
        if (condition.getSvnDpmC() != null && !condition.getSvnDpmC().isBlank()) {
            builder.and(bprojm.svnDpmC.eq(condition.getSvnDpmC()));
        }

        // 경상여부 필터
        // 'Y': 경상사업만 조회 (ODN_YN='Y')
        // 'N': 일반 정보화사업만 조회 (ODN_YN IS NULL 또는 ODN_YN != 'Y')
        if (condition.getOdnYn() != null && !condition.getOdnYn().isBlank()) {
            if ("Y".equals(condition.getOdnYn())) {
                builder.and(bprojm.odnYn.eq("Y"));
            } else {
                builder.and(bprojm.odnYn.isNull().or(bprojm.odnYn.ne("Y")));
            }
        }

        return builder;
    }
}
