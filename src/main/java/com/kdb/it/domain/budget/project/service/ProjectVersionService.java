package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정보화사업 재신청 개정본의 생성과 확정 전환을 담당합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectVersionService {

    private static final String PROJECT_TABLE = "BPROJM";

    private final ProjectRepository projectRepository;
    private final ProjectItemRepository projectItemRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /**
     * 결재완료된 최종 사업을 다음 순번의 재신청 초안으로 만듭니다.
     *
     * @param abusMngNo 재신청할 사업관리번호
     * @return 생성된 초안의 명시적 버전 식별값
     * @throws IllegalArgumentException 최종본이 없거나 결재완료되지 않았거나 주관부서가 없는 경우
     */
    @Transactional
    public ProjectVersion createReapplication(String abusMngNo) {
        return createReapplication(abusMngNo, null, false);
    }

    /**
     * 인증 사용자의 부서 범위를 확인하고 결재완료 사업을 재신청 초안으로 복제합니다.
     *
     * @param abusMngNo 재신청할 사업관리번호
     * @param actor 인증 사용자
     * @return 생성된 초안의 명시적 버전 식별값
     * @throws org.springframework.security.access.AccessDeniedException 대상 부서 조회 권한이 없는 경우
     */
    @Transactional
    public ProjectVersion createReapplication(String abusMngNo, CustomUserDetails actor) {
        return createReapplication(abusMngNo, actor, true);
    }

    private ProjectVersion createReapplication(
            String abusMngNo, CustomUserDetails actor, boolean verifyActor) {
        Bprojm source =
                projectRepository
                        .findCurrentVersionForUpdate(abusMngNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "재신청할 최종 사업이 없습니다: " + abusMngNo));
        if (verifyActor) {
            BudgetDetailAccessVerifier.verifyReadable(source.getSvnDpmC(), actor);
        }
        // 원본을 잠근 뒤 미결 초안 존재를 확인한다. 잠금이 동시 요청을 직렬화하므로
        // 두 번째 트랜잭션은 여기서 차단되어 초안 v2·v3가 함께 생기지 않는다.
        if (projectRepository.existsByAbusMngNoAndLstYnAndDelYn(abusMngNo, "N", "N")) {
            throw new IllegalStateException("이미 재신청 초안이 있습니다: " + abusMngNo);
        }
        if (source.getSvnDpmC() == null || source.getSvnDpmC().isBlank()) {
            throw new IllegalArgumentException("주관부서가 없는 사업은 재신청할 수 없습니다: " + abusMngNo);
        }
        String latestStatus =
                applicationMapRepository
                        .findLatestApplicationStatus(PROJECT_TABLE, abusMngNo, source.getSno())
                        .orElse(null);
        if (!ApprovalStatus.COMPLETED.code().equals(latestStatus)) {
            throw new IllegalArgumentException("결재완료된 사업만 재신청할 수 있습니다: " + abusMngNo);
        }

        Bprojm draft =
                source.createReapplicationDraft(projectRepository.getNextVersionSno(abusMngNo));
        projectRepository.save(draft);
        cloneItems(source, draft);
        return new ProjectVersion(draft.getAbusMngNo(), draft.getSno(), draft.getLstYn());
    }

    /**
     * 사업관리번호에 속한 모든 미삭제 개정본을 순번순으로 조회합니다.
     *
     * @param abusMngNo 사업관리번호
     * @return 최종본과 재신청 초안을 모두 포함한 이력
     */
    public List<Bprojm> findHistory(String abusMngNo) {
        return projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc(abusMngNo, "N");
    }

    /**
     * 인증 사용자의 부서 범위를 확인하고 정보화사업 이력을 조회합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param actor 인증 사용자
     * @return 최종본과 재신청 초안을 모두 포함한 이력
     */
    public List<Bprojm> findHistory(String abusMngNo, CustomUserDetails actor) {
        List<Bprojm> history = findHistory(abusMngNo);
        history.forEach(
                project -> BudgetDetailAccessVerifier.verifyReadable(project.getSvnDpmC(), actor));
        return history;
    }

    /**
     * 재신청 이력에서 정확한 개정본을 조회합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param sno 개정 순번
     * @return 해당 개정본. 삭제되었거나 없으면 빈 값
     */
    public Optional<Bprojm> findVersion(String abusMngNo, Integer sno) {
        return projectRepository.findByAbusMngNoAndSnoAndDelYn(abusMngNo, sno, "N");
    }

    /**
     * 인증 사용자의 부서 범위를 확인하고 특정 정보화사업 개정본을 조회합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param sno 개정 순번
     * @param actor 인증 사용자
     * @return 해당 개정본. 삭제되었거나 없으면 빈 값
     */
    public Optional<Bprojm> findVersion(String abusMngNo, Integer sno, CustomUserDetails actor) {
        return findVersion(abusMngNo, sno)
                .map(
                        project -> {
                            BudgetDetailAccessVerifier.verifyReadable(project.getSvnDpmC(), actor);
                            return project;
                        });
    }

    /**
     * 결재가 완료된 정확한 개정본을 현재 최종본으로 원자적으로 전환합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param sno 승인된 개정 순번
     */
    @Transactional
    public void promoteApprovedVersion(String abusMngNo, Integer sno) {
        projectRepository
                .findVersionForUpdate(abusMngNo, sno)
                .orElseThrow(() -> new IllegalArgumentException("승격할 사업 개정본이 없습니다: " + abusMngNo));
        verifyNotRegressing(abusMngNo, sno);
        projectRepository.clearCurrentVersion(abusMngNo, sno);
        if (projectRepository.markVersionCurrent(abusMngNo, sno) != 1) {
            throw new IllegalStateException("승격할 사업 개정본이 없습니다: " + abusMngNo);
        }
        projectItemRepository.clearCurrentVersionItems(abusMngNo, sno);
        projectItemRepository.markVersionItemsCurrent(abusMngNo, sno);
    }

    /**
     * 현재 최종본보다 낮은 순번으로 되돌리는 승격을 막습니다.
     *
     * <p>초안이 중복 생성돼 둘 다 승인되거나, 상신 시 잘못된 순번이 결재 매핑에 실려 오면 나중 승격이 이미 승인된 최신본을 조용히 강등시킵니다. 사용자에게는 사업
     * 내용이 과거로 롤백된 것처럼 보이므로 실패로 드러냅니다.
     *
     * @param abusMngNo 사업관리번호
     * @param sno 승격하려는 개정 순번
     * @throws IllegalStateException 현재 최종본보다 낮은 순번인 경우
     */
    private void verifyNotRegressing(String abusMngNo, Integer sno) {
        Integer currentSno =
                projectRepository
                        .findByAbusMngNoAndLstYnAndDelYn(abusMngNo, "Y", "N")
                        .map(Bprojm::getSno)
                        .orElse(null);
        if (currentSno != null && sno != null && sno < currentSno) {
            throw new IllegalStateException(
                    "이전 개정본으로 되돌릴 수 없습니다: %s (현재 최종본 %d, 요청 %d)"
                            .formatted(abusMngNo, currentSno, sno));
        }
    }

    /** 원본 개정본에 속한 활성 품목을 새 식별자와 새 부모 순번으로 복제합니다. */
    private void cloneItems(Bprojm source, Bprojm draft) {
        int itemSno = 0;
        for (var item :
                projectItemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        source.getAbusMngNo(), source.getSno(), "N")) {
            String gclMngNo =
                    "GCL-%s-%04d"
                            .formatted(
                                    java.time.LocalDate.now().getYear(),
                                    projectItemRepository.getNextSequenceValue());
            projectItemRepository.save(
                    com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                            .gclMngNo(gclMngNo)
                            .sno(++itemSno)
                            .abusMngNo(draft.getAbusMngNo())
                            .fntTbCrySno(draft.getSno())
                            .ioeC(item.getIoeC())
                            .gclNm(item.getGclNm())
                            .qty(item.getQty())
                            .curC(item.getCurC())
                            .xcr(item.getXcr())
                            .xcrBseDt(item.getXcrBseDt())
                            .cncdFdtnCone(item.getCncdFdtnCone())
                            .bseYm(item.getBseYm())
                            .dfrCleC(item.getDfrCleC())
                            .sectSysUtzYn(item.getSectSysUtzYn())
                            .itrInfrYn(item.getItrInfrYn())
                            .lstYn("N")
                            .amt(item.getAmt())
                            .mplAmt(item.getMplAmt())
                            .fcAmt(item.getFcAmt())
                            .delYn("N")
                            .build());
        }
    }

    /** 정보화사업 개정본을 식별하는 최소 응답입니다. */
    public record ProjectVersion(String abusMngNo, Integer sno, String lstYn) {}
}
