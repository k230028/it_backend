package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectQueryAssemblerTest {

    private ProjectItemRepository itemRepository;
    private BprojaRepository bprojaRepository;
    private ProjectQueryAssembler assembler;

    @BeforeEach
    void setUp() {
        itemRepository = mock(ProjectItemRepository.class);
        bprojaRepository = mock(BprojaRepository.class);
        CodeRepository codeRepository = mock(CodeRepository.class);
        CodeService codeService = mock(CodeService.class);
        assembler =
                new ProjectQueryAssembler(
                        mock(ApplicationMapRepository.class),
                        mock(ApplicationRepository.class),
                        itemRepository,
                        mock(OrganizationRepository.class),
                        mock(UserRepository.class),
                        mock(ApproverRepository.class),
                        codeRepository,
                        mock(BbugtmRepository.class),
                        codeService,
                        new ProjectBudgetSummaryService(codeService),
                        bprojaRepository,
                        new CodeNameMapBuilder(codeRepository),
                        mock(ProjectRepository.class));
    }

    @Test
    @DisplayName("상세 조립: 단계 상태 최댓값과 활성 품목을 같은 응답에 반영한다")
    void assembleDetail_대표상태와품목_반영() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-001").sno(1).delYn("N").build();
        Bproja writing =
                Bproja.builder().abusMngNo("PRJ-001").cncdRfrNo("STEP-1").stsTc("01").build();
        Bproja approved =
                Bproja.builder().abusMngNo("PRJ-001").cncdRfrNo("STEP-2").stsTc("09").build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-001")
                        .abusMngNo("PRJ-001")
                        .fntTbCrySno(1)
                        .gclNm("서버")
                        .build();
        given(bprojaRepository.findByAbusMngNoAndDelYn("PRJ-001", "N"))
                .willReturn(List.of(writing, approved));
        given(itemRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-001", 1, "N"))
                .willReturn(List.of(item));

        ProjectDto.Response result = assembler.assembleDetail(project);

        assertThat(result.getStsTc()).isEqualTo("09");
        assertThat(result.getBprojaStsCodes()).containsExactly("01", "09");
        assertThat(result.getItems())
                .extracting(ProjectDto.BitemmDto::getGclNm)
                .containsExactly("서버");
    }
}
