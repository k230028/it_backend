package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDirectoryDto;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.exception.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectDirectoryServiceTest {

    private ProjectRepository projectRepository;
    private BprojaRepository bprojaRepository;
    private ApplicationMapRepository applicationMapRepository;
    private ApplicationRepository applicationRepository;
    private OrganizationRepository organizationRepository;
    private UserRepository userRepository;
    private CodeService codeService;
    private ProjectDirectoryService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        bprojaRepository = mock(BprojaRepository.class);
        applicationMapRepository = mock(ApplicationMapRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        userRepository = mock(UserRepository.class);
        codeService = mock(CodeService.class);
        service =
                new ProjectDirectoryService(
                        projectRepository,
                        bprojaRepository,
                        applicationMapRepository,
                        applicationRepository,
                        organizationRepository,
                        userRepository,
                        codeService);
    }

    @Test
    @DisplayName("사업 검색 디렉터리는 현재 개정본의 최신 신청서 상태와 경상사업 여부를 반환한다")
    void findAll_최신신청서_신청서상태와경상사업여부반환() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-ORDINARY")
                        .sno(2)
                        .lstYn("Y")
                        .delYn("N")
                        .odnYn("Y")
                        .abusNm("경상 유지보수")
                        .build();
        ApplicationMapRepository.ApplicationMapView currentApplication =
                applicationMapView("APF-2026-00000002", "PRJ-ORDINARY", 2);
        ApplicationMapRepository.ApplicationMapView previousApplication =
                applicationMapView("APF-2026-00000001", "PRJ-ORDINARY", 1);
        ApplicationRepository.ApplicationSummaryView summary =
                applicationSummaryView("APF-2026-00000002", "1");
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(
                        applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                "BPROJM", List.of("PRJ-ORDINARY")))
                .willReturn(List.of(currentApplication, previousApplication));
        given(applicationRepository.findSummaryViewsByApfMngNoIn(List.of("APF-2026-00000002")))
                .willReturn(List.of(summary));

        ProjectDirectoryDto.Response result = service.findAll().getFirst();

        assertThat(result.odnYn()).isEqualTo("Y");
        assertThat(result.apfStsC()).isEqualTo("1");
        assertThat(result.apfSts()).isEqualTo("결재중");
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
                            assertThat(entry.apfSts()).isNull();
                            assertThat(entry.apfStsC()).isNull();
                            assertThat(entry.svnDpmCNm()).isEqualTo("리스크관리부");
                            assertThat(entry.tlrUsid()).isEqualTo("10002");
                            assertThat(entry.tlrUsidNm()).isEqualTo("김팀장");
                            assertThat(entry.usid()).isEqualTo("10003");
                            assertThat(entry.usidNm()).isEqualTo("이담당");
                        });
        verifyNoInteractions(applicationRepository);
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

    @Test
    @DisplayName("최종본 사업이 없으면 부서·사용자·코드 조회 없이 빈 목록을 반환한다")
    void findAll_사업없음_추가조회없이빈목록() {
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of());

        assertThat(service.findAll()).isEmpty();

        verifyNoInteractions(
                bprojaRepository,
                applicationMapRepository,
                applicationRepository,
                organizationRepository,
                userRepository,
                codeService);
    }

    @Test
    @DisplayName("조회된 부서명과 사용자명이 있으면 사업 스냅샷 값보다 우선한다")
    void findAll_조회성공_스냅샷대신최신명칭사용() {
        Bprojm project = project("PRJ-OTHER", "타 부서 디지털 사업");
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-OTHER"), "N"))
                .willReturn(List.of(step("PRJ-OTHER", "79")));
        OrganizationRepository.OrganizationNameView renamedDepartment =
                organizationView("D200", "리스크관리부(개편)");
        UserRepository.UserNameView leaderView = userView("10002", "김현재");
        UserRepository.UserNameView managerView = userView("10003", "이현재");
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of(renamedDepartment));
        given(userRepository.findNameViewsByEnoIn(anyCollection()))
                .willReturn(List.of(leaderView, managerView));
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC")).willReturn(List.of());

        ProjectDirectoryDto.Response result = service.findAll().getFirst();

        assertThat(result.svnDpmCNm()).isEqualTo("리스크관리부(개편)");
        assertThat(result.tlrUsidNm()).isEqualTo("김현재");
        assertThat(result.usidNm()).isEqualTo("이현재");
    }

    @Test
    @DisplayName("조회된 부서명이 공백이면 사업 스냅샷의 부서명으로 되돌린다")
    void findAll_조회부서명공백_스냅샷부서명사용() {
        Bprojm project = project("PRJ-OTHER", "타 부서 디지털 사업");
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-OTHER"), "N"))
                .willReturn(List.of(step("PRJ-OTHER", "79")));
        OrganizationRepository.OrganizationNameView blankDepartment =
                organizationView("D200", "  ");
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of(blankDepartment));
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC")).willReturn(List.of());

        assertThat(service.findAll().getFirst().svnDpmCNm()).isEqualTo("리스크관리부");
    }

    @Test
    @DisplayName("담당자 컬럼에 사번 대신 이름이 저장된 사업은 사번을 노출하지 않고 이름만 표시한다")
    void findAll_담당자컬럼에이름저장_사번노출없이이름표시() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-NAME")
                        .sno(1)
                        .lstYn("Y")
                        .delYn("N")
                        .abusNm("이름 저장 사업")
                        .svnDpmC("D200")
                        .svnDpmNm("리스크관리부")
                        .tlrUsid("홍길동")
                        .usid("Luke Buckingham-Brown")
                        .build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-NAME"), "N"))
                .willReturn(List.of(step("PRJ-NAME", "79")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC")).willReturn(List.of());

        ProjectDirectoryDto.Response result = service.findAll().getFirst();

        assertThat(result.tlrUsid()).isNull();
        assertThat(result.tlrUsidNm()).isEqualTo("홍길동");
        assertThat(result.usid()).isNull();
        assertThat(result.usidNm()).isEqualTo("Luke Buckingham-Brown");
    }

    @Test
    @DisplayName("담당부서·담당자 값이 비어 있으면 조회 키에 넣지 않는다")
    void findAll_빈담당값_조회키에서제외() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-BLANK")
                        .sno(1)
                        .lstYn("Y")
                        .delYn("N")
                        .abusNm("담당 미지정 사업")
                        .svnDpmC(null)
                        .svnDpmNm("리스크관리부")
                        .tlrUsid("   ")
                        .usid("10003")
                        .build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-BLANK"), "N"))
                .willReturn(List.of(step("PRJ-BLANK", "79")));
        given(organizationRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IT_PTL_STS_TC")).willReturn(List.of());

        service.findAll();

        ArgumentCaptor<Collection<String>> organizationKeys = ArgumentCaptor.captor();
        ArgumentCaptor<Collection<String>> userKeys = ArgumentCaptor.captor();
        verify(organizationRepository).findNameViewsByPrlmOgzCConeIn(organizationKeys.capture());
        verify(userRepository).findNameViewsByEnoIn(userKeys.capture());
        assertThat(organizationKeys.getValue()).isEmpty();
        assertThat(userKeys.getValue()).containsExactly("10003");
    }

    @Test
    @DisplayName("없는 사업의 단건 조회는 빈 요약이 아니라 조회 실패로 구분한다")
    void findOne_없는사업_NotFound예외() {
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-MISSING", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne("PRJ-MISSING"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("정보화사업");
    }

    private static OrganizationRepository.OrganizationNameView organizationView(
            String code, String name) {
        OrganizationRepository.OrganizationNameView view =
                mock(OrganizationRepository.OrganizationNameView.class);
        given(view.getPrlmOgzCCone()).willReturn(code);
        given(view.getBbrNm()).willReturn(name);
        return view;
    }

    private static UserRepository.UserNameView userView(String eno, String name) {
        UserRepository.UserNameView view = mock(UserRepository.UserNameView.class);
        given(view.getEno()).willReturn(eno);
        given(view.getUsrNm()).willReturn(name);
        return view;
    }

    private static ApplicationMapRepository.ApplicationMapView applicationMapView(
            String applicationId, String projectId, int sequence) {
        ApplicationMapRepository.ApplicationMapView view =
                mock(ApplicationMapRepository.ApplicationMapView.class);
        given(view.getApfDcmNo()).willReturn(applicationId);
        given(view.getPkColNm()).willReturn(projectId);
        given(view.getFntTbCrySno()).willReturn(sequence);
        return view;
    }

    private static ApplicationRepository.ApplicationSummaryView applicationSummaryView(
            String applicationId, String statusCode) {
        ApplicationRepository.ApplicationSummaryView view =
                mock(ApplicationRepository.ApplicationSummaryView.class);
        given(view.getApfMngNo()).willReturn(applicationId);
        given(view.getItPtlApfPrgStsC()).willReturn(statusCode);
        return view;
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
