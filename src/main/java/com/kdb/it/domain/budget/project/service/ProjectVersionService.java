package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
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
        Bprojm source =
                projectRepository
                        .findByAbusMngNoAndLstYnAndDelYn(abusMngNo, "Y", "N")
                        .orElseThrow(() -> new IllegalArgumentException("재신청할 최종 사업이 없습니다: " + abusMngNo));
        if (source.getSvnDpmC() == null || source.getSvnDpmC().isBlank()) {
            throw new IllegalArgumentException("주관부서가 없는 사업은 재신청할 수 없습니다: " + abusMngNo);
        }
        if (!applicationMapRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                PROJECT_TABLE,
                abusMngNo,
                source.getSno(),
                java.util.List.of(ApprovalStatus.COMPLETED.code()))) {
            throw new IllegalArgumentException("결재완료된 사업만 재신청할 수 있습니다: " + abusMngNo);
        }

        Bprojm draft = source.createReapplicationDraft(projectRepository.getNextVersionSno(abusMngNo));
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
     * 결재가 완료된 정확한 개정본을 현재 최종본으로 원자적으로 전환합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param sno 승인된 개정 순번
     */
    @Transactional
    public void promoteApprovedVersion(String abusMngNo, Integer sno) {
        projectRepository.clearCurrentVersion(abusMngNo, sno);
        projectRepository.markVersionCurrent(abusMngNo, sno);
        projectItemRepository.clearCurrentVersionItems(abusMngNo, sno);
        projectItemRepository.markVersionItemsCurrent(abusMngNo, sno);
    }

    /** 원본 개정본에 속한 활성 품목을 새 식별자와 새 부모 순번으로 복제합니다. */
    private void cloneItems(Bprojm source, Bprojm draft) {
        int itemSno = 0;
        for (var item :
                projectItemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        source.getAbusMngNo(), source.getSno(), "N")) {
            String gclMngNo =
                    "GCL-%s-%04d".formatted(
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
