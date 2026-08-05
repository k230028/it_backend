package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectQueryServiceTest {

    private ProjectRepository projectRepository;
    private ProjectQueryService queryService;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        CodeRepository codeRepository = mock(CodeRepository.class);
        CodeService codeService = mock(CodeService.class);
        ProjectQueryAssembler assembler =
                new ProjectQueryAssembler(
                        mock(ApplicationMapRepository.class),
                        mock(ApplicationRepository.class),
                        mock(ProjectItemRepository.class),
                        mock(OrganizationRepository.class),
                        mock(UserRepository.class),
                        mock(ApproverRepository.class),
                        codeRepository,
                        mock(BbugtmRepository.class),
                        codeService,
                        new ProjectBudgetSummaryService(codeService),
                        mock(BprojaRepository.class),
                        new CodeNameMapBuilder(codeRepository),
                        projectRepository);
        queryService = new ProjectQueryService(projectRepository, assembler);
    }

    @Test
    @DisplayName("단건 조회: 활성 사업이 없으면 관리번호를 포함한 예외를 반환한다")
    void getProject_미존재_예외() {
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-NOT-FOUND", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getProject("PRJ-NOT-FOUND"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRJ-NOT-FOUND");
    }

    @Test
    @DisplayName("일괄 조회: 입력 순서대로 성공 항목과 누락 관리번호를 분리한다")
    void getProjectsByIds_부분성공_입력순서보존() {
        Bprojm first = Bprojm.builder().abusMngNo("PRJ-001").sno(1).delYn("N").build();
        Bprojm third = Bprojm.builder().abusMngNo("PRJ-003").sno(1).delYn("N").build();
        given(
                        projectRepository.findByAbusMngNoInAndDelYn(
                                List.of("PRJ-001", "PRJ-002", "PRJ-003"), "N"))
                .willReturn(List.of(third, first));
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("PRJ-001", "PRJ-002", "PRJ-003"));

        ProjectDto.BulkResponse result = queryService.getProjectsByIds(request);

        assertThat(result.items())
                .extracting(ProjectDto.Response::getAbusMngNo)
                .containsExactly("PRJ-001", "PRJ-003");
        assertThat(result.failedIds()).containsExactly("PRJ-002");
    }
}
