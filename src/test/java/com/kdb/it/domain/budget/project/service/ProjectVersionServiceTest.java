package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
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
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(projectRepository.getNextVersionSno("PRJ-2026-0001")).willReturn(2);

        var result = service.createReapplication("PRJ-2026-0001");

        assertThat(result.sno()).isEqualTo(2);
        assertThat(result.lstYn()).isEqualTo("N");
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(projectRepository);
        inOrder.verify(projectRepository).findCurrentVersionForUpdate("PRJ-2026-0001");
        inOrder.verify(projectRepository).getNextVersionSno("PRJ-2026-0001");
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
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.REJECTED.code()));

        assertThatThrownBy(() -> service.createReapplication("PRJ-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재완료");
        verify(projectRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("과거 완료 이력이 있어도 최신 신청서가 반려면 재신청하지 못한다")
    void 최신_신청서가_반려면_과거_완료이력이_있어도_재신청하지_못한다() {
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.REJECTED.code()));

        assertThatThrownBy(() -> service.createReapplication("PRJ-2026-0001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재완료");
        verify(projectRepository, never()).getNextVersionSno("PRJ-2026-0001");
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
        Bprojm target = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(2).delYn("N").build();
        given(projectRepository.findVersionForUpdate("PRJ-2026-0001", 2))
                .willReturn(Optional.of(target));
        given(projectRepository.markVersionCurrent("PRJ-2026-0001", 2)).willReturn(1);

        service.promoteApprovedVersion("PRJ-2026-0001", 2);

        var locks = org.mockito.Mockito.inOrder(projectRepository);
        locks.verify(projectRepository).findAllVersionsForUpdate("PRJ-2026-0001");
        locks.verify(projectRepository).findVersionForUpdate("PRJ-2026-0001", 2);
        locks.verify(projectRepository).clearCurrentVersion("PRJ-2026-0001", 2);

        verify(projectRepository).clearCurrentVersion("PRJ-2026-0001", 2);
        verify(projectRepository).markVersionCurrent("PRJ-2026-0001", 2);
        verify(projectItemRepository).clearCurrentVersionItems("PRJ-2026-0001", 2);
        verify(projectItemRepository).markVersionItemsCurrent("PRJ-2026-0001", 2);
    }

    @Test
    @DisplayName("승격 대상이 없으면 기존 최종본을 내리지 않는다")
    void 승격_대상이_없으면_기존_최종본을_내리지_않는다() {
        given(projectRepository.findVersionForUpdate("PRJ-2026-0001", 2))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.promoteApprovedVersion("PRJ-2026-0001", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승격할 사업 개정본");
        verify(projectRepository, never()).clearCurrentVersion("PRJ-2026-0001", 2);
        verify(projectRepository, never()).markVersionCurrent("PRJ-2026-0001", 2);
    }

    @Test
    @DisplayName("승격 갱신 행이 없으면 트랜잭션을 롤백하도록 실패한다")
    void 승격_갱신행이_없으면_트랜잭션을_롤백하도록_실패한다() {
        Bprojm target = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(2).delYn("N").build();
        given(projectRepository.findVersionForUpdate("PRJ-2026-0001", 2))
                .willReturn(Optional.of(target));
        given(projectRepository.markVersionCurrent("PRJ-2026-0001", 2)).willReturn(0);

        assertThatThrownBy(() -> service.promoteApprovedVersion("PRJ-2026-0001", 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("승격할 사업 개정본");
    }

    @Test
    @DisplayName("같은 승인 이벤트의 재처리는 같은 최종 상태 전환을 반복해도 안전하다")
    void 같은_승인이벤트의_재처리는_멱등적이다() {
        Bprojm target = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(2).delYn("N").build();
        given(projectRepository.findVersionForUpdate("PRJ-2026-0001", 2))
                .willReturn(Optional.of(target));
        given(projectRepository.markVersionCurrent("PRJ-2026-0001", 2)).willReturn(1);

        service.promoteApprovedVersion("PRJ-2026-0001", 2);
        service.promoteApprovedVersion("PRJ-2026-0001", 2);

        verify(projectRepository, times(2)).clearCurrentVersion("PRJ-2026-0001", 2);
        verify(projectRepository, times(2)).markVersionCurrent("PRJ-2026-0001", 2);
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
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-2026-0001", 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(projectRepository.getNextVersionSno("PRJ-2026-0001")).willReturn(2);
        given(projectItemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
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

    @Test
    @DisplayName("이미 미결 재신청 초안이 있으면 재신청을 거부한다 — 더블클릭·동시 요청으로 초안이 중첩되지 않는다")
    void 활성_초안이_있으면_재신청을_거부한다() {
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(projectRepository.existsByAbusMngNoAndSnoGreaterThanAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(true);

        assertThatThrownBy(() -> service.createReapplication("PRJ-2026-0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재신청 초안");

        verify(projectRepository, never()).getNextVersionSno("PRJ-2026-0001");
    }

    @Test
    @DisplayName("현재 최종본보다 낮은 순번으로는 승격하지 않는다 — 승인된 최신본이 조용히 강등되는 것을 막는다")
    void 이전_순번으로의_승격을_거부한다() {
        Bprojm current =
                Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(2).lstYn("Y").delYn("N").build();
        Bprojm stale =
                Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).lstYn("N").delYn("N").build();
        given(projectRepository.findVersionForUpdate("PRJ-2026-0001", 1))
                .willReturn(Optional.of(stale));
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-2026-0001", "Y", "N"))
                .willReturn(Optional.of(current));

        assertThatThrownBy(() -> service.promoteApprovedVersion("PRJ-2026-0001", 1))
                .isInstanceOf(IllegalStateException.class);

        verify(projectRepository, never()).clearCurrentVersion("PRJ-2026-0001", 1);
    }

    @Test
    @DisplayName("승격으로 강등된 과거 버전이 남아 있어도 재신청할 수 있다")
    void 강등된_과거버전은_초안으로_보지_않는다() {
        // 최종본이 sno=4인 사업에는 sno=1·3이 LST_YN='N', DEL_YN='N'으로 남아 있다.
        // 이들을 미결 초안으로 오인하면 그 사업은 이후 재신청이 영구 차단된다.
        Bprojm source =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(4)
                        .svnDpmC("D001")
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findCurrentVersionForUpdate("PRJ-2026-0001"))
                .willReturn(Optional.of(source));
        given(projectRepository.existsByAbusMngNoAndSnoGreaterThanAndDelYn("PRJ-2026-0001", 4, "N"))
                .willReturn(false);
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-2026-0001", 4))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(projectRepository.getNextVersionSno("PRJ-2026-0001")).willReturn(5);

        var result = service.createReapplication("PRJ-2026-0001");

        assertThat(result.sno()).isEqualTo(5);
        assertThat(result.lstYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("최종본이 없으면 재신청을 거부한다")
    void 재신청_최종본없음_거부() {
        given(projectRepository.findCurrentVersionForUpdate("PRJ-404"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReapplication("PRJ-404"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRJ-404");
    }

    @Test
    @DisplayName("주관부서가 빈 최종본은 재신청하지 못한다")
    void 재신청_주관부서빈값_거부() {
        Bprojm source = Bprojm.builder().abusMngNo("PRJ-1").sno(1).svnDpmC(" ").lstYn("Y").build();
        given(projectRepository.findCurrentVersionForUpdate("PRJ-1"))
                .willReturn(Optional.of(source));

        assertThatThrownBy(() -> service.createReapplication("PRJ-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주관부서");

        verify(applicationMapRepository, never()).findLatestApplicationStatus("BPROJM", "PRJ-1", 1);
    }

    @Test
    @DisplayName("같은 부서 사용자는 권한 검증 후 재신청한다")
    void 재신청_같은부서_허용() {
        Bprojm source =
                Bprojm.builder().abusMngNo("PRJ-1").sno(1).svnDpmC("D001").lstYn("Y").build();
        given(projectRepository.findCurrentVersionForUpdate("PRJ-1"))
                .willReturn(Optional.of(source));
        given(applicationMapRepository.findLatestApplicationStatus("BPROJM", "PRJ-1", 1))
                .willReturn(Optional.of(ApprovalStatus.COMPLETED.code()));
        given(projectRepository.getNextVersionSno("PRJ-1")).willReturn(2);
        CustomUserDetails actor =
                new CustomUserDetails(
                        "10001", java.util.List.of(CustomUserDetails.ATH_USER), "D001");

        ProjectVersionService.ProjectVersion result = service.createReapplication("PRJ-1", actor);

        assertThat(result.sno()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 개정 순번은 빈 결과로 반환한다")
    void 개정본_미존재_빈결과() {
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn("PRJ-1", 99, "N"))
                .willReturn(Optional.empty());

        assertThat(service.findVersion("PRJ-1", 99)).isEmpty();
    }

    @Test
    @DisplayName("현재 최종본과 같은 순번의 승격은 허용한다")
    void 승격_현재순번과같음_허용() {
        Bprojm target = Bprojm.builder().abusMngNo("PRJ-1").sno(2).lstYn("Y").build();
        given(projectRepository.findVersionForUpdate("PRJ-1", 2)).willReturn(Optional.of(target));
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn("PRJ-1", "Y", "N"))
                .willReturn(Optional.of(target));
        given(projectRepository.markVersionCurrent("PRJ-1", 2)).willReturn(1);

        service.promoteApprovedVersion("PRJ-1", 2);

        verify(projectRepository).clearCurrentVersion("PRJ-1", 2);
    }
}
