package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.controller.CouncilFeasibilityController;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import com.kdb.it.domain.council.repository.QnaRepository;
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
    @Mock ProjectItemRepository projectItemRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationService applicationService;
    @Mock QnaRepository qnaRepository;
    @InjectMocks CouncilService councilService;
    private CouncilAccessGuard guard;
    private FeasibilityService feasibilityService;
    private CommitteeService committeeService;
    private CouncilApprovalService approvalService;
    private QnaService qnaService;
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
        guard =
                new CouncilAccessGuard(
                        councilRepository,
                        projectRepository,
                        committeeRepository,
                        projectItemRepository);
        ReflectionTestUtils.setField(councilService, "councilAccessGuard", guard);
        committeeService =
                new CommitteeService(committeeRepository, userRepository, councilService, guard);
        ReflectionTestUtils.setField(committeeService, "entityManager", entityManager);
        approvalService =
                new CouncilApprovalService(
                        councilService, projectOverviewRepository, applicationService, guard);
        qnaService = new QnaService(qnaRepository, councilRepository, projectRepository, guard);
        ReflectionTestUtils.setField(qnaService, "entityManager", entityManager);
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

    @Test
    void manageableRejectsOwningDepartmentAndCommittee() {
        login("OWNER");
        assertThatThrownBy(() -> guard.verifyManageable(council))
                .isInstanceOf(AccessDeniedException.class);
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThatThrownBy(() -> guard.verifyManageable(council))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void manageableAllowsAdminAndInfoSecOnlyForInfoSecType() {
        loginAs("ITPAD001");
        assertThatCode(() -> guard.verifyManageable(council)).doesNotThrowAnyException();
        loginAs("ITPAD002");
        assertThatThrownBy(() -> guard.verifyManageable(council))
                .isInstanceOf(AccessDeniedException.class);
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "04");
        assertThatCode(() -> guard.verifyManageable(council)).doesNotThrowAnyException();
    }

    @Test
    void owningOrManageableAllowsOwnerButNotCommittee() {
        login("OWNER");
        assertThatCode(() -> guard.verifyOwningOrManageable(council)).doesNotThrowAnyException();
        login("OTHER");
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThatThrownBy(() -> guard.verifyOwningOrManageable(council))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void committeeOrManageableAllowsCommitteeAndManagerOnly() {
        login("OTHER");
        assertThatThrownBy(() -> guard.verifyCommitteeOrManageable(council))
                .isInstanceOf(AccessDeniedException.class);
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThatCode(() -> guard.verifyCommitteeOrManageable(council)).doesNotThrowAnyException();
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.empty());
        loginAs("ITPAD001");
        assertThatCode(() -> guard.verifyCommitteeOrManageable(council)).doesNotThrowAnyException();
        login("OWNER");
        assertThatThrownBy(() -> guard.verifyCommitteeOrManageable(council))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void creatableFollowsRoleAndDepartmentMatrix() {
        // 일반 사용자: 본인 부서 사업 03 허용, 타 부서 03 거부, 01·02·05 거부
        login("OWNER");
        assertThatCode(() -> guard.verifyCreatable("03", "PRJ-1", 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.verifyCreatable("01", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.verifyCreatable("02", "PLN-1", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.verifyCreatable("05", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
        login("OTHER");
        assertThatThrownBy(() -> guard.verifyCreatable("03", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
        // 일반 사용자 04: 정보보호 항목이 있어야 허용
        login("OWNER");
        when(projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn("PRJ-1", "Y", "N"))
                .thenReturn(false);
        assertThatThrownBy(() -> guard.verifyCreatable("04", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
        when(projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn("PRJ-1", "Y", "N"))
                .thenReturn(true);
        assertThatCode(() -> guard.verifyCreatable("04", "PRJ-1", 1)).doesNotThrowAnyException();
        // 정보보호관리자: 04만
        loginAs("ITPAD002");
        assertThatCode(() -> guard.verifyCreatable("04", "PRJ-9", 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.verifyCreatable("03", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
        // 시스템관리자: 01·02·03·05 무조건, 04는 정보보호 항목 있을 때
        loginAs("ITPAD001");
        assertThatCode(() -> guard.verifyCreatable("02", "PLN-1", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.verifyCreatable("03", "PRJ-9", 1)).doesNotThrowAnyException();
        when(projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn("PRJ-9", "Y", "N"))
                .thenReturn(false);
        assertThatThrownBy(() -> guard.verifyCreatable("04", "PRJ-9", 1))
                .isInstanceOf(AccessDeniedException.class);
        when(projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn("PRJ-9", "Y", "N"))
                .thenReturn(true);
        assertThatCode(() -> guard.verifyCreatable("04", "PRJ-9", 1)).doesNotThrowAnyException();
    }

    @Test
    void creatableRejectsMissingAuthentication() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> guard.verifyCreatable("03", "PRJ-1", 1))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void committeeSaveRejectsOwningDepartmentAndCommittee() {
        council.changeStatus("05");
        login("OWNER");
        assertThatThrownBy(() -> committeeService.saveCommittee(ID, committeeRequest()))
                .isInstanceOf(AccessDeniedException.class);
        login("OTHER");
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThatThrownBy(() -> committeeService.saveCommittee(ID, committeeRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager);
    }

    @Test
    void committeeSaveAllowsAdminInPreparationStatuses() {
        loginAs("ITPAD001");
        council.changeStatus("04");
        committeeService.saveCommittee(ID, committeeRequest());
        council.changeStatus("05");
        committeeService.saveCommittee(ID, committeeRequest());
        verify(entityManager, org.mockito.Mockito.times(2)).persist(any());
    }

    @Test
    void committeeSaveAllowsInfoSecManagerOnlyForInfoSecType() {
        loginAs("ITPAD002");
        council.changeStatus("05");
        assertThatThrownBy(() -> committeeService.saveCommittee(ID, committeeRequest()))
                .isInstanceOf(AccessDeniedException.class);
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "04");
        committeeService.saveCommittee(ID, committeeRequest());
        verify(entityManager).persist(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"01", "02", "03", "06", "07", "08", "09", "10", "11", "12", "13", "99"})
    void committeeSaveRejectsOtherStatusesWithConflict(String status) {
        loginAs("ITPAD001");
        council.changeStatus(status);
        assertThatThrownBy(() -> committeeService.saveCommittee(ID, committeeRequest()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
        verifyNoInteractions(entityManager);
    }

    @Test
    void approvalRequestRejectsOtherDepartmentAndCommittee() {
        council.changeStatus("02");
        CustomUserDetails other = login("OTHER");
        assertThatThrownBy(
                        () ->
                                approvalService.requestApproval(
                                        ID, new CouncilDto.ApprovalRequest("E-9", null), other))
                .isInstanceOf(AccessDeniedException.class);
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        assertThatThrownBy(
                        () ->
                                approvalService.requestApproval(
                                        ID, new CouncilDto.ApprovalRequest("E-9", null), other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(applicationService, projectOverviewRepository);
    }

    @Test
    void approvalRequestChecksPermissionBeforeStatus() {
        // 작성중(01) 원장이라도 권한 없는 호출자는 상태 오류(400)가 아니라 403을 받는다.
        CustomUserDetails other = login("OTHER");
        assertThatThrownBy(
                        () ->
                                approvalService.requestApproval(
                                        ID, new CouncilDto.ApprovalRequest("E-9", null), other))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(applicationService, projectOverviewRepository);
    }

    @Test
    void approvalRequestPermissionAllowsOwnerAdminAndInfoSecForInfoSecType() {
        login("OWNER");
        assertThatCode(() -> guard.verifyOwningOrManageable(council)).doesNotThrowAnyException();
        loginAs("ITPAD001");
        assertThatCode(() -> guard.verifyOwningOrManageable(council)).doesNotThrowAnyException();
        loginAs("ITPAD002");
        assertThatThrownBy(() -> guard.verifyOwningOrManageable(council))
                .isInstanceOf(AccessDeniedException.class);
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "04");
        assertThatCode(() -> guard.verifyOwningOrManageable(council)).doesNotThrowAnyException();
    }

    @Test
    void qnaCreateRejectsUnrelatedUserAndOwningDepartment() {
        CustomUserDetails other = login("OTHER");
        assertThatThrownBy(
                        () ->
                                qnaService.createQna(
                                        ID, new CouncilDto.QnaCreateRequest("질문"), other))
                .isInstanceOf(AccessDeniedException.class);
        CustomUserDetails owner = login("OWNER");
        assertThatThrownBy(
                        () ->
                                qnaService.createQna(
                                        ID, new CouncilDto.QnaCreateRequest("질문"), owner))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entityManager, qnaRepository);
    }

    @Test
    void qnaCreateAllowsCommitteeAndManagers() {
        CustomUserDetails member = login("OTHER");
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.of(mock(Bcmmtm.class)));
        qnaService.createQna(ID, new CouncilDto.QnaCreateRequest("질문"), member);
        when(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ID, "USER-1", "N"))
                .thenReturn(Optional.empty());
        CustomUserDetails admin = loginAs("ITPAD001");
        qnaService.createQna(ID, new CouncilDto.QnaCreateRequest("질문"), admin);
        CustomUserDetails infoSec = loginAs("ITPAD002");
        assertThatThrownBy(
                        () ->
                                qnaService.createQna(
                                        ID, new CouncilDto.QnaCreateRequest("질문"), infoSec))
                .isInstanceOf(AccessDeniedException.class);
        ReflectionTestUtils.setField(council, "itPtlAsctDbrTc", "04");
        qnaService.createQna(ID, new CouncilDto.QnaCreateRequest("질문"), infoSec);
        verify(entityManager, org.mockito.Mockito.times(3)).persist(any());
    }

    @Test
    void councilCreateRejectsBeforeAnyPersistence() {
        CustomUserDetails other = login("OTHER");
        assertThatThrownBy(
                        () ->
                                councilService.createCouncil(
                                        new CouncilDto.CreateRequest("PRJ-1", 1, "03", null),
                                        other))
                .isInstanceOf(AccessDeniedException.class);
        CustomUserDetails owner = login("OWNER");
        assertThatThrownBy(
                        () ->
                                councilService.createCouncil(
                                        new CouncilDto.CreateRequest("PRJ-1", 1, "01", null),
                                        owner))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                councilService.createCouncil(
                                        new CouncilDto.CreateRequest(null, null, "02", "PLN-1"),
                                        owner))
                .isInstanceOf(AccessDeniedException.class);
        verify(councilRepository, never()).getNextSequenceValue();
        verify(councilRepository, never()).findByAbusMngNoAndDelYn(any(), any());
        verifyNoInteractions(entityManager);
    }

    private CouncilDto.CommitteeRequest committeeRequest() {
        return new CouncilDto.CommitteeRequest(
                "03", List.of(new CouncilDto.CommitteeMemberRequest("E-1", "02")));
    }

    private CustomUserDetails loginAs(String role) {
        var user = new CustomUserDetails("USER-1", List.of(role), "OTHER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return user;
    }

    private CustomUserDetails login(String department) {
        var user = new CustomUserDetails("USER-1", List.of("ITPZZ001"), department);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return user;
    }

    private CouncilDto.FeasibilityRequest request(String type) {
        return new CouncilDto.FeasibilityRequest(
                "사업", "2026", null, null, null, null, "N", null, null, type, null, "FILE-1", null);
    }
}
