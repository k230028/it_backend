package com.kdb.it.domain.budget.project.repository;

import java.util.List;

import com.kdb.it.common.approval.entity.QCappla;
import com.kdb.it.common.approval.entity.QCapplm;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.QBprojm;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

/**
 * 정보화사업(Bprojm) 커스텀 리포지토리 QueryDSL 구현체
 *
 * <p>
 * {@link ProjectRepositoryCustom} 인터페이스의 QueryDSL 구현체입니다.
 * 복잡한 동적 쿼리(apfSts 필터링 서브쿼리 포함)를 타입 안전하게 처리합니다.
 * </p>
 *
 * <p>
 * 클래스 명명 규칙: Spring Data JPA가 자동 감지하려면
 * 반드시 {@code [메인Repository명]Impl} 형태여야 합니다. ({@code ProjectRepositoryImpl})
 * </p>
 *
 * <p>
 * {@code apfSts} 서브쿼리 전략:
 * </p>
 * <ul>
 * <li>{@code "none"}: NOT EXISTS — CAPPLA에 연결 레코드가 없는 프로젝트</li>
 * <li>그 외 값: EXISTS — 최신 CAPPLA(APF_REL_SNO MAX)의 CAPPLM 결재상태가 일치하는 프로젝트</li>
 * </ul>
 */
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
public class ProjectRepositoryImpl implements ProjectRepositoryCustom {

    /** QueryDSL 쿼리 팩토리: JPA 쿼리 생성 및 실행 담당 */
    private final JPAQueryFactory queryFactory;

    /**
     * 검색 조건으로 정보화사업 목록 동적 조회
     *
     * <p>
     * [처리 순서]
     * 1. DEL_YN='N' 기본 조건 설정
     * 2. apfSts 조건 분기 처리 (none / 특정값 / null)
     * 3. 나머지 단순 필드 조건 추가 (bgYy, prjSts, prjTp, itDpm, svnDpm)
     * 4. BooleanBuilder로 조합된 WHERE 절로 쿼리 실행
     * </p>
     *
     * @param condition 검색 조건 DTO
     * @return 조건에 맞는 정보화사업 목록
     */
    @Override
    public List<Bprojm> searchByCondition(ProjectDto.SearchCondition condition) {
        QBprojm bprojm = QBprojm.bprojm;
        // 서브쿼리용 CAPPLA Q타입 별칭 (자기 참조 서브쿼리 충돌 방지)
        QCappla cappla = new QCappla("cappla");
        QCappla cappla2 = new QCappla("cappla2");
        QCapplm capplm = QCapplm.capplm;

        BooleanBuilder builder = new BooleanBuilder();

        // 기본 조건: 삭제되지 않은 프로젝트만 조회
        builder.and(bprojm.delYn.eq("N"));

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
                                        cappla.fntTbNm.eq("BPROJM"),
                                        cappla.pkColNm.eq(bprojm.abusMngNo),
                                        cappla.fntTbCrySno.eq(bprojm.sno),
                                        capplm.apfPrgStsC.in("01", "02"))
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
                                        capplm.apfPrgStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.hasLabel(apfSts)
                                                ? com.kdb.it.common.approval.domain.ApprovalStatus.ofLabel(apfSts).code()
                                                : apfSts),
                                        // 해당 프로젝트에 연결된 신청서 중 가장 최신(APF_DCM_NO 최대)인 것만 검사
                                        cappla.apfDcmNo.eq(
                                                JPAExpressions.select(cappla2.apfDcmNo.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.fntTbNm.eq("BPROJM"),
                                                                cappla2.pkColNm.eq(bprojm.abusMngNo),
                                                                cappla2.fntTbCrySno.eq(bprojm.sno))))
                                .exists());
            }
        }

        // === 단순 필드 조건 처리 (null이면 해당 조건 미적용) ===

        // 사업연도 필터
        if (condition.getBseYy() != null && !condition.getBseYy().isBlank()) {
            builder.and(bprojm.bseYy.eq(condition.getBseYy()));
        }
        // 프로젝트상태 필터
        if (condition.getStsTc() != null && !condition.getStsTc().isBlank()) {
            builder.and(bprojm.stsTc.eq(condition.getStsTc()));
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

        return queryFactory
                .selectFrom(bprojm)
                .where(builder)
                .fetch();
    }
}
