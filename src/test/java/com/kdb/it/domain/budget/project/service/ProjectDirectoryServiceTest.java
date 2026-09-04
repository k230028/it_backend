package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectDirectoryServiceTest {

    private ProjectRepository projectRepository;
    private BprojaRepository bprojaRepository;
    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private CodeService codeService;
    private ProjectDirectoryService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        bprojaRepository = mock(BprojaRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        codeService = mock(CodeService.class);
        service =
                new ProjectDirectoryService(
                        projectRepository,
                        bprojaRepository,
                        organizationRepository,
                        userRepository,
                        codeService);
    }

    @Test
    @DisplayName("사업 검색 디렉터리는 부서 범위 없이 전 사업의 안전한 담당자 요약을 반환한다")
    void findAll_모든부서_안전한요약반환() {
        Bprojm project = project("PRJ-OTHER", "타 부서 디지털 사업");
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-OTHER"), "N"))
                .willReturn(List.of(step("PRJ-OTHER", "79")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(List.of("D200")))
                .willReturn(List.of());
        given(userRepository.findNameViewsByEnoIn(List.of("10002", "10003"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC"))
                .willReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IT_PTL_STS_TC")
                                        .cdva("79")
                                        .cdvaNm("사업 추진")
                                        .sttDt("20260101")
                                        .build()));

        List<ProjectDirectoryDto.Response> result = service.findAll();

        assertThat(result)
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.abusMngNo()).isEqualTo("PRJ-OTHER");
                            assertThat(entry.abusNm()).isEqualTo("타 부서 디지털 사업");
                            assertThat(entry.stsTc()).isEqualTo("79");
                            assertThat(entry.stsTcNm()).isEqualTo("사업 추진");
                            assertThat(entry.svnDpmCNm()).isEqualTo("리스크관리부");
                            assertThat(entry.tlrUsid()).isEqualTo("10002");
                            assertThat(entry.tlrUsidNm()).isEqualTo("김팀장");
                            assertThat(entry.usid()).isEqualTo("10003");
                            assertThat(entry.usidNm()).isEqualTo("이담당");
                        });
    }

    @Test
    @DisplayName("사업 검색 디렉터리 단건은 상세 권한과 무관하게 같은 안전한 요약을 반환한다")
    void findOne_타부서사업_안전한요약반환() {
        Bprojm project = project("PRJ-OTHER", "타 부서 디지털 사업");
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-OTHER", "N"))
                .willReturn(Optional.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-OTHER"), "N"))
                .willReturn(List.of(step("PRJ-OTHER", "79")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(List.of("D200")))
                .willReturn(List.of());
        given(userRepository.findNameViewsByEnoIn(List.of("10002", "10003"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC")).willReturn(List.of());

        ProjectDirectoryDto.Response result = service.findOne("PRJ-OTHER");

        assertThat(result.abusMngNo()).isEqualTo("PRJ-OTHER");
        assertThat(result.svnDpmCNm()).isEqualTo("리스크관리부");
        assertThat(result.tlrUsidNm()).isEqualTo("김팀장");
        assertThat(result.usidNm()).isEqualTo("이담당");
    }

    private static Bprojm project(String id, String name) {
        return Bprojm.builder()
                .abusMngNo(id)
                .sno(1)
                .lstYn("Y")
                .delYn("N")
                .abusNm(name)
                .svnDpmC("D200")
                .svnDpmNm("리스크관리부")
                .tlrUsid("10002")
                .tlrNm("김팀장")
                .usid("10003")
                .usrNm("이담당")
                .build();
    }

    private static Bproja step(String projectId, String status) {
        return Bproja.builder()
                .abusMngNo(projectId)
                .cncdRfrNo(projectId)
                .stsTc(status)
                .delYn("N")
                .build();
    }
}
