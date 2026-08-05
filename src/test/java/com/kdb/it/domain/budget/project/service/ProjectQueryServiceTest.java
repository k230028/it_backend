package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import com.kdb.it.exception.DataCorruptionException;
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
    @DisplayName("단건 조회: 활성 사업을 실제 상세 응답으로 조립한다")
    void getProject_활성사업_상세응답반환() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-DETAIL-001").sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-DETAIL-001", "N"))
                .willReturn(Optional.of(project));

        ProjectDto.Response result = queryService.getProject("PRJ-DETAIL-001");

        assertThat(result.getAbusMngNo()).isEqualTo("PRJ-DETAIL-001");
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getBprojaStsCodes()).isEmpty();
        verify(projectRepository).findByAbusMngNoAndDelYn("PRJ-DETAIL-001", "N");
    }

    @Test
    @DisplayName("전체 목록 조회: 활성 사업만 조회해 실제 조립 결과를 반환한다")
    void getProjectList_활성사업_조립결과반환() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-LIST-001").sno(1).delYn("N").build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));

        List<ProjectDto.Response> result = queryService.getProjectList();

        assertThat(result)
                .extracting(ProjectDto.Response::getAbusMngNo)
                .containsExactly("PRJ-LIST-001");
        assertThat(result.getFirst().getItems()).isEmpty();
        verify(projectRepository).findAllByDelYn("N");
    }

    @Test
    @DisplayName("검색 목록 조회: 검색 조건을 그대로 적용하고 실제 조립 결과를 반환한다")
    void searchProjectList_검색조건_조립결과반환() {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setBseYy("2027");
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-SEARCH-001")
                        .sno(1)
                        .bseYy("2027")
                        .delYn("N")
                        .build();
        given(projectRepository.searchByCondition(condition)).willReturn(List.of(project));

        List<ProjectDto.Response> result = queryService.searchProjectList(condition);

        assertThat(result)
                .singleElement()
                .satisfies(
                        value -> {
                            assertThat(value.getAbusMngNo()).isEqualTo("PRJ-SEARCH-001");
                            assertThat(value.getBseYy()).isEqualTo("2027");
                        });
        verify(projectRepository).searchByCondition(condition);
    }

    @Test
    @DisplayName("일괄 조회: 요청이 null이면 저장소 호출 없이 빈 성공·실패 목록을 반환한다")
    void getProjectsByIds_null요청_빈응답() {
        ProjectDto.BulkResponse result = queryService.getProjectsByIds(null);

        assertThat(result.items()).isEmpty();
        assertThat(result.failedIds()).isEmpty();
        verifyNoInteractions(projectRepository);
    }

    @Test
    @DisplayName("일괄 조회: 관리번호 목록이 비어 있으면 저장소 호출 없이 빈 응답을 반환한다")
    void getProjectsByIds_빈목록_빈응답() {
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of());

        ProjectDto.BulkResponse result = queryService.getProjectsByIds(request);

        assertThat(result.items()).isEmpty();
        assertThat(result.failedIds()).isEmpty();
        verifyNoInteractions(projectRepository);
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

    @Test
    @DisplayName("일괄 조회: 같은 관리번호의 활성 기본행이 둘이면 데이터 손상 예외를 반환한다")
    void getProjectsByIds_중복활성행_데이터손상예외() {
        Bprojm first = Bprojm.builder().abusMngNo("PRJ-DUP").sno(1).delYn("N").build();
        Bprojm duplicate = Bprojm.builder().abusMngNo("PRJ-DUP").sno(2).delYn("N").build();
        given(projectRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-DUP"), "N"))
                .willReturn(List.of(first, duplicate));
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("PRJ-DUP"));

        assertThatThrownBy(() -> queryService.getProjectsByIds(request))
                .isInstanceOf(DataCorruptionException.class)
                .hasMessageContaining("PRJ-DUP");
    }
}
