package com.kdb.it.domain.budget.plan.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경상예산 재신청 개정본의 생성, 조회와 최종본 전환을 담당합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanVersionService {

    private static final String PLAN_TABLE = "BPLANM";

    private final BplanmRepository bplanmRepository;
    private final BplanaRepository bplanaRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 결재완료된 최종 계획을 다음 순번의 재신청 초안으로 복제합니다.
     *
     * @param reqDocNo 부모 계획관리번호
     * @return 생성한 초안의 부모 번호·순번·최종본 여부
     * @throws IllegalArgumentException 최종본이 없거나 결재완료되지 않았거나 주관부서가 없는 경우
     */
    @Transactional
    public PlanVersion createReapplication(String reqDocNo) {
        return createReapplication(reqDocNo, null, false);
    }

    /**
     * 인증 사용자의 부서 범위를 검증하고 결재완료된 최종 계획을 재신청 초안으로 복제합니다.
     *
     * @param reqDocNo 부모 계획관리번호
     * @param actor 인증 사용자
     * @return 생성한 초안의 부모 번호·순번·최종본 여부
     * @throws org.springframework.security.access.AccessDeniedException 대상 부서 열람 권한이 없는 경우
     * @throws IllegalArgumentException 최종본이 없거나 결재완료되지 않았거나 주관부서가 없는 경우
     */
    @Transactional
    public PlanVersion createReapplication(String reqDocNo, CustomUserDetails actor) {
        return createReapplication(reqDocNo, actor, true);
    }

    private PlanVersion createReapplication(
            String reqDocNo, CustomUserDetails actor, boolean verifyActor) {
        Bplanm source =
                bplanmRepository
                        .findCurrentVersionForUpdate(reqDocNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "재신청할 최종 계획이 없습니다: " + reqDocNo));
        if (verifyActor) {
            BudgetDetailAccessVerifier.verifyReadable(source.getSvnDpmC(), actor);
        }
        if (source.getSvnDpmC() == null || source.getSvnDpmC().isBlank()) {
            throw new IllegalArgumentException("주관부서가 없는 계획은 재신청할 수 없습니다: " + reqDocNo);
        }
        assertCompleted(reqDocNo, source.getSno());

        Bplanm draft = source.createReapplicationDraft(bplanmRepository.getNextVersionSno(reqDocNo));
        bplanmRepository.save(draft);
        cloneRelations(source, draft);
        return new PlanVersion(draft.getReqDocNo(), draft.getSno(), draft.getLstYn());
    }

    /**
     * 작성부서가 열람할 수 있는 모든 개정본 이력과 각 순번의 최신 결재 상태를 반환합니다.
     *
     * @param reqDocNo 부모 계획관리번호
     * @param actor 인증 사용자
     * @return 순번 오름차순의 이력 응답
     * @throws org.springframework.security.access.AccessDeniedException 대상 부서 열람 권한이 없는 경우
     */
    public List<PlanDto.VersionResponse> findHistory(String reqDocNo, CustomUserDetails actor) {
        return bplanmRepository.findByReqDocNoAndDelYnOrderBySnoAsc(reqDocNo, "N").stream()
                .peek(plan -> BudgetDetailAccessVerifier.verifyReadable(plan.getSvnDpmC(), actor))
                .map(
                        plan ->
                                PlanDto.VersionResponse.fromEntity(
                                        plan,
                                        applicationMapRepository
                                                .findLatestApplicationStatus(
                                                        PLAN_TABLE,
                                                        plan.getReqDocNo(),
                                                        plan.getSno())
                                                .orElse(null)))
                .toList();
    }

    /**
     * 명시적 순번으로 특정 계획 개정본을 조회합니다.
     *
     * @param reqDocNo 부모 계획관리번호
     * @param sno 개정 순번
     * @param actor 인증 사용자
     * @return 삭제되지 않은 정확한 개정본. 없으면 빈 값
     * @throws org.springframework.security.access.AccessDeniedException 대상 부서 열람 권한이 없는 경우
     */
    public Optional<Bplanm> findVersion(String reqDocNo, Integer sno, CustomUserDetails actor) {
        return bplanmRepository
                .findByReqDocNoAndSnoAndDelYn(reqDocNo, sno, "N")
                .map(
                        plan -> {
                            BudgetDetailAccessVerifier.verifyReadable(plan.getSvnDpmC(), actor);
                            return plan;
                        });
    }

    /**
     * 결재완료된 정확한 개정본을 한 트랜잭션에서 최종본으로 승격합니다.
     *
     * @param reqDocNo 부모 계획관리번호
     * @param sno 결재완료된 개정 순번
     * @throws IllegalArgumentException 대상이 없거나 결재완료 상태가 아닌 경우
     * @throws IllegalStateException 주관부서 또는 현재 최종본이 없는 경우
     */
    @Transactional
    public void promoteApprovedVersion(String reqDocNo, Integer sno) {
        bplanmRepository
                .findCurrentVersionForUpdate(reqDocNo)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "현재 최종 계획이 없어 개정본을 승격할 수 없습니다: " + reqDocNo));
        Bplanm target =
                bplanmRepository
                        .findVersionForUpdate(reqDocNo, sno)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "승격할 계획 개정본이 없습니다: " + reqDocNo));
        if (target.getSvnDpmC() == null || target.getSvnDpmC().isBlank()) {
            throw new IllegalStateException("주관부서가 없는 계획은 승격할 수 없습니다: " + reqDocNo);
        }
        assertCompleted(reqDocNo, sno);
        if ("Y".equals(target.getLstYn())) {
            return;
        }

        bplanmRepository.clearCurrentVersion(reqDocNo, sno);
        if (bplanmRepository.markVersionCurrent(reqDocNo, sno) != 1) {
            throw new IllegalStateException("승격할 계획 개정본이 없습니다: " + reqDocNo);
        }
    }

    private void assertCompleted(String reqDocNo, Integer sno) {
        String latestStatus =
                applicationMapRepository
                        .findLatestApplicationStatus(PLAN_TABLE, reqDocNo, sno)
                        .orElse(null);
        if (!ApprovalStatus.COMPLETED.code().equals(latestStatus)) {
            throw new IllegalArgumentException("결재완료된 계획만 재신청 또는 승격할 수 있습니다: " + reqDocNo);
        }
    }

    private void cloneRelations(Bplanm source, Bplanm draft) {
        for (Bplana relation :
                bplanaRepository.findAllByReqDocNoAndSnoAndDelYn(
                        source.getReqDocNo(), source.getSno(), "N")) {
            bplanaRepository.save(
                    Bplana.builder()
                            .prjMngNo(relation.getPrjMngNo())
                            .reqDocNo(draft.getReqDocNo())
                            .sno(draft.getSno())
                            .build());
        }
    }

    /** 재신청 생성 결과의 부모 계획번호와 명시적 순번입니다. */
    public record PlanVersion(String reqDocNo, Integer sno, String lstYn) {}
}
