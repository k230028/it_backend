package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class ProjectVersionAccessTest {

    private ProjectRepository projectRepository;
    private ProjectVersionService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        service =
                new ProjectVersionService(
                        projectRepository,
                        mock(ProjectItemRepository.class),
                        mock(ApplicationMapRepository.class));
    }

    @Test
    @DisplayName("타 부서 일반 사용자는 프로젝트 이력과 명시 버전을 조회할 수 없다")
    void 타부서_일반사용자는_프로젝트이력과_명시버전을_조회할수없다() {
        Bprojm revision = Bprojm.builder().abusMngNo("PRJ-1").sno(2).svnDpmC("D100").build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-1", "N"))
                .willReturn(List.of(revision));
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn("PRJ-1", 2, "N"))
                .willReturn(Optional.of(revision));

        assertThatThrownBy(() -> service.findHistory("PRJ-1", otherDepartmentUser()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findVersion("PRJ-1", 2, otherDepartmentUser()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("타 부서 일반 사용자는 프로젝트 재신청을 만들 수 없다")
    void 타부서_일반사용자는_프로젝트재신청을_만들수없다() {
        given(projectRepository.findCurrentVersionForUpdate("PRJ-1"))
                .willReturn(
                        Optional.of(
                                Bprojm.builder()
                                        .abusMngNo("PRJ-1")
                                        .sno(1)
                                        .svnDpmC("D100")
                                        .build()));

        assertThatThrownBy(() -> service.createReapplication("PRJ-1", otherDepartmentUser()))
                .isInstanceOf(AccessDeniedException.class);

        verify(projectRepository, never()).getNextVersionSno("PRJ-1");
    }

    private static CustomUserDetails otherDepartmentUser() {
        return new CustomUserDetails("USER", List.of(CustomUserDetails.ATH_USER), "D200");
    }
}
