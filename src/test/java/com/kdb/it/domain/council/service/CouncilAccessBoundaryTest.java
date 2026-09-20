package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.controller.CouncilFeasibilityController;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import com.kdb.it.domain.council.repository.SelfCheckRepository;
import com.kdb.it.exception.GlobalExceptionHandler;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** 실제 서비스 경계를 통해 타 부서 접근과 진행 상태 역행을 검증합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilAccessBoundaryTest {
    private static final String ID = "ASCT-2026-0001";
    @Mock CouncilRepository councilRepository;
    @Mock ProjectRepository projectRepository;
    @Mock CommitteeRepository committeeRepository;
    @Mock ProjectOverviewRepository projectOverviewRepository;
    @Mock PerformanceRepository performanceRepository;
    @Mock SelfCheckRepository selfCheckRepository;
    @Mock EntityManager entityManager;
    @InjectMocks CouncilService councilService;
    private FeasibilityService feasibilityService;
    private Basctm council;

    @BeforeEach
    void setUp() {
        council =
                Basctm.builder()
                        .itPtlAsctId(ID)
                        .abusMngNo("PRJ-1")
                        .sno(1)
                        .itPtlAsctDbrTc("03")
                        .itPtlAsctPrgStsTc("01")
                        .delYn("N")
                        .build();
        when(councilRepository.findByItPtlAsctIdAndDelYn(ID, "N")).thenReturn(Optional.of(council));
        when(councilRepository.findByIdForUpdate(ID)).thenReturn(Optional.of(council));
        Bprojm project = mock(Bprojm.class);
        when(project.getSvnDpmC()).thenReturn("OWNER");
        when(project.getDelYn()).thenReturn("N");
        when(projectRepository.findById(any())).thenReturn(Optional.of(project));
        feasibilityService =
                new FeasibilityService(
                        projectOverviewRepository,
                        performanceRepository,
                        selfCheckRepository,
                        councilService);
        ReflectionTestUtils.setField(feasibilityService, "entityManager", entityManager);
        ReflectionTestUtils.setField(
                councilService,
                "councilAccessGuard",
                new CouncilAccessGuard(councilRepository, projectRepository, committeeRepository));
        login("OTHER");
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void otherDepartmentCannotReadDetail() {
        assertThatThrownBy(() -> councilService.getCouncil(ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void otherDepartmentCannotReadFeasibility() {
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(projectOverviewRepository);
    }

    @Test
    void otherDepartmentCannotSaveFeasibility() {
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager, projectOverviewRepository);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "99"
            })
    void nonDraftCannotBeOverwritten(String status) {
        login("OWNER");
        council.changeStatus(status);
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(entityManager, projectOverviewRepository);
    }

    @Test
    void owningDepartmentCanReadAndSaveDraft() {
        login("OWNER");
        assertThat(feasibilityService.getFeasibility(ID)).isNull();
        feasibilityService.saveFeasibility(ID, request("10"));
        verify(councilRepository).findByIdForUpdate(ID);
        verify(entityManager).persist(any());
        assertThat(council.getItPtlAsctPrgStsTc()).isEqualTo("01");
    }

    @Test
    void completeDraftMovesToSubmittedAndRejectsSecondSave() {
        login("OWNER");
        feasibilityService.saveFeasibility(ID, request("20"));
        assertThat(council.getItPtlAsctPrgStsTc()).isEqualTo("02");
        clearInvocations(entityManager, projectOverviewRepository);
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("20")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(entityManager, projectOverviewRepository);
    }

    @Test
    void assignedCommitteeCanReadButCannotEditOtherDepartmentsDraft() {
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThat(feasibilityService.getFeasibility(ID)).isNull();
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager);
    }

    @Test
    void adminCanSaveDraftButCannotOverwriteCompletedCouncil() {
        loginAs("ITPAD001");
        feasibilityService.saveFeasibility(ID, request("10"));
        council.changeStatus("13");
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void infoSecManagerCannotReadUnrelatedType() {
        loginAs("ITPAD002");
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void infoSecManagerCanManageInfoSecDraft() {
        loginAs("ITPAD002");
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "04");
        assertThat(feasibilityService.getFeasibility(ID)).isNull();
        feasibilityService.saveFeasibility(ID, request("10"));
        verify(entityManager).persist(any());
    }

    @Test
    void planCommitteeCanReadWithoutProjectKeyButCannotCreateFeasibility() {
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "02");
        ReflectionTestUtils.setField(council, "sno", null);
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThat(feasibilityService.getFeasibility(ID)).isNull();
        verify(projectRepository, never()).findById(any());
        loginAs("ITPAD001");
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void missingDepartmentDoesNotGrantOwnership() {
        login(null);
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).findById(any());
    }

    @Test
    void missingAuthenticationIsRejected() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(AccessDeniedException.class);
        verify(councilRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void unexpectedPrincipalCannotGrantAdminAccess() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "admin",
                                null,
                                List.of(
                                        new org.springframework.security.core.authority
                                                .SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingEmployeeNumberCannotGrantOwnership() {
        var user = new CustomUserDetails(null, List.of("ITPZZ001"), "OWNER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        assertThatThrownBy(() -> feasibilityService.getFeasibility(ID))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager, projectOverviewRepository);
    }

    @Test
    void deletedCouncilCannotBeWritten() {
        login("OWNER");
        council.delete();
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(entityManager);
    }

    @Test
    void lockTimeoutIsConflictAndDoesNotWrite() {
        login("OWNER");
        when(councilRepository.findByIdForUpdate(ID))
                .thenThrow(new jakarta.persistence.LockTimeoutException("timeout"));
        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ID, request("10")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(entityManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void httpWritesReturnForbiddenForOtherDepartmentAndConflictForSubmitted(String method)
            throws Exception {
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new CouncilFeasibilityController(feasibilityService))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        mvc.perform(
                        MockMvcRequestBuilders.request(
                                        HttpMethod.valueOf(method),
                                        "/api/council/" + ID + "/feasibility")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"kpnTc\":\"10\"}"))
                .andExpect(status().isForbidden());
        login("OWNER");
        council.changeStatus("02");
        mvc.perform(
                        MockMvcRequestBuilders.request(
                                        HttpMethod.valueOf(method),
                                        "/api/council/" + ID + "/feasibility")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"kpnTc\":\"10\"}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(entityManager, projectOverviewRepository);
    }

    private void loginAs(String role) {
        var user = new CustomUserDetails("USER-1", List.of(role), "OTHER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void login(String department) {
        var user = new CustomUserDetails("USER-1", List.of("ITPZZ001"), department);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private CouncilDto.FeasibilityRequest request(String type) {
        return new CouncilDto.FeasibilityRequest(
                "사업", "2026", null, null, null, null, "N", null, null, type, null, "FILE-1", null);
    }
}
