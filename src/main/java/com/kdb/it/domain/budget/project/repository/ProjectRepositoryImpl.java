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
                                        cappla.apfMngNo.eq(capplm.apfMngNo),
                                        cappla.orcTbCd.eq("BPROJM"),
                                        cappla.orcPkVl.eq(bprojm.prjMngNo),
                                        cappla.orcSnoVl.eq(bprojm.prjSno),
                                        capplm.apfStsC.in("001", "002"))
                                .notExists());
            } else {
                // 특정 결재상태: 최신 신청서(APF_REL_SNO 최대값)의 결재상태가 일치하는 경우
                builder.and(
                        JPAExpressions.selectOne()
                                .from(cappla, capplm)
                                .where(
                                        cappla.apfMngNo.eq(capplm.apfMngNo),
                                        cappla.orcTbCd.eq("BPROJM"),
                                        cappla.orcPkVl.eq(bprojm.prjMngNo),
                                        cappla.orcSnoVl.eq(bprojm.prjSno),
                                        capplm.apfStsC.eq(com.kdb.it.common.approval.domain.ApprovalStatus.hasLabel(apfSts)
                                                ? com.kdb.it.common.approval.domain.ApprovalStatus.ofLabel(apfSts).code()
                                                : apfSts),
                                        // 해당 프로젝트에 연결된 신청서 중 가장 최신(APF_REL_SNO 최대)인 것만 검사
                                        cappla.apfRelSno.eq(
                                                JPAExpressions.select(cappla2.apfRelSno.max())
                                                        .from(cappla2)
                                                        .where(
                                                                cappla2.orcTbCd.eq("BPROJM"),
                                                                cappla2.orcPkVl.eq(bprojm.prjMngNo),
                                                                cappla2.orcSnoVl.eq(bprojm.prjSno))))
                                .exists());
            }
        }

        // === 단순 필드 조건 처리 (null이면 해당 조건 미적용) ===

        // 사업연도 필터
        if (condition.getBgYy() != null && !condition.getBgYy().isBlank()) {
            builder.and(bprojm.bgYy.eq(condition.getBgYy()));
        }
        // 프로젝트상태 필터
        if (condition.getPrjSts() != null && !condition.getPrjSts().isBlank()) {
            builder.and(bprojm.prjSts.eq(condition.getPrjSts()));
        }
        // 프로젝트유형 필터
        if (condition.getPrjTp() != null && !condition.getPrjTp().isBlank()) {
            builder.and(bprojm.prjTp.eq(condition.getPrjTp()));
        }
        // IT부서 필터
        if (condition.getItDpm() != null && !condition.getItDpm().isBlank()) {
            builder.and(bprojm.itDpm.eq(condition.getItDpm()));
        }
        // 주관부서 필터
        if (condition.getSvnDpm() != null && !condition.getSvnDpm().isBlank()) {
            builder.and(bprojm.svnDpm.eq(condition.getSvnDpm()));
        }

        // 경상여부 필터
        // 'Y': 경상사업만 조회 (ORN_YN='Y')
        // 'N': 일반 정보화사업만 조회 (ORN_YN IS NULL 또는 ORN_YN != 'Y')
        if (condition.getOrnYn() != null && !condition.getOrnYn().isBlank()) {
            if ("Y".equals(condition.getOrnYn())) {
                builder.and(bprojm.ornYn.eq("Y"));
            } else {
                builder.and(bprojm.ornYn.isNull().or(bprojm.ornYn.ne("Y")));
            }
        }

        return queryFactory
                .selectFrom(bprojm)
                .where(builder)
                .fetch();
    }
}
