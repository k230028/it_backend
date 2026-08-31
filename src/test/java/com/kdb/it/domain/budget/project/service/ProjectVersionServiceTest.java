package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectVersionServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;

    @InjectMocks private ProjectVersionService service;

    @Test
    @DisplayName("결재완료 사업을 재신청하면 다음 순번의 비최종 초안이 생긴다")
    void 결재완료_사업을_재신청하면_다음_순번의_비최종_초안이_생긴다() {
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .willReturn(Optional.of(source));
        given(
                        applicationMapRepository
                                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        1,
                                        java.util.List.of(ApprovalStatus.COMPLETED.code())))
                .willReturn(true);
        given(projectRepository.getNextVersionSno("PRJ-2026-0001")).willReturn(2);

        var result = service.createReapplication("PRJ-2026-0001");

        assertThat(result.sno()).isEqualTo(2);
        assertThat(result.lstYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("반려되었거나 완료 이력이 없는 사업은 재신청하지 못한다")
    void 반려되었거나_완료이력이_없는_사업은_재신청하지_못한다() {
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .willReturn(Optional.of(source));
        given(
                        applicationMapRepository
                                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        1,
                                        java.util.List.of(ApprovalStatus.COMPLETED.code())))
                .willReturn(false);

        assertThatThrownBy(() -> service.createReapplication("PRJ-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재완료");
        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이력과 명시 버전 조회는 비최종 초안을 포함한다")
    void 이력과_명시_버전_조회는_비최종_초안을_포함한다() {
        Bprojm first = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).lstYn("N").build();
        Bprojm draft = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(2).lstYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(java.util.List.of(first, draft));
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn("PRJ-2026-0001", 2, "N"))
                .willReturn(Optional.of(draft));

        assertThat(service.findHistory("PRJ-2026-0001")).containsExactly(first, draft);
        assertThat(service.findVersion("PRJ-2026-0001", 2)).contains(draft);
    }

    @Test
    @DisplayName("완료된 정확한 개정본만 최종본으로 승격한다")
    void 완료된_정확한_개정본만_최종본으로_승격한다() {
        service.promoteApprovedVersion("PRJ-2026-0001", 2);

        verify(projectRepository).clearCurrentVersion("PRJ-2026-0001", 2);
        verify(projectRepository).markVersionCurrent("PRJ-2026-0001", 2);
        verify(projectItemRepository).clearCurrentVersionItems("PRJ-2026-0001", 2);
        verify(projectItemRepository).markVersionItemsCurrent("PRJ-2026-0001", 2);
    }

    @Test
    @DisplayName("재신청 초안은 새 품목 식별자로 원본 품목을 복제한다")
    void 재신청_초안은_새_품목_식별자로_원본_품목을_복제한다() {
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        Bitemm sourceItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(1)
                        .abusMngNo("PRJ-2026-0001")
                        .fntTbCrySno(1)
                        .gclNm("원본 품목")
                        .dfrCleC("0")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .willReturn(Optional.of(source));
        given(
                        applicationMapRepository
                                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        1,
                                        java.util.List.of(ApprovalStatus.COMPLETED.code())))
                .willReturn(true);
        given(projectRepository.getNextVersionSno("PRJ-2026-0001")).willReturn(2);
        given(projectItemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                        "PRJ-2026-0001", 1, "N"))
                .willReturn(java.util.List.of(sourceItem));
        given(projectItemRepository.getNextSequenceValue()).willReturn(9L);

        service.createReapplication("PRJ-2026-0001");

        org.mockito.ArgumentCaptor<Bitemm> captor =
                org.mockito.ArgumentCaptor.forClass(Bitemm.class);
        verify(projectItemRepository).save(captor.capture());
        assertThat(captor.getValue().getGclMngNo())
                .isEqualTo("GCL-%s-%04d".formatted(LocalDate.now().getYear(), 9));
        assertThat(captor.getValue().getFntTbCrySno()).isEqualTo(2);
        assertThat(captor.getValue().getLstYn()).isEqualTo("N");
    }
}
