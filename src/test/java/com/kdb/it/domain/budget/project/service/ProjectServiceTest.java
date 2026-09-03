package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.exception.DataCorruptionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ProjectService 단위 테스트
 *
 * <p>모든 Repository를 Mock 처리하여 Oracle DB 없이 비즈니스 로직을 검증합니다. 특히 결재중/결재완료 프로젝트 삭제 거부 등 핵심 비즈니스 제약을
 * 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceTest {

    private record NameView(String eno, String usrNm, String ptCNm)
            implements UserRepository.UserNameView {
        /** 직위명이 검증 대상이 아닌 기존 케이스용 축약 생성자. */
        private NameView(String eno, String usrNm) {
            this(eno, usrNm, null);
        }

        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return ptCNm;
        }
    }

    private record OrgNameView(String prlmOgzCCone, String bbrNm)
            implements OrganizationRepository.OrganizationNameView {
        @Override
        public String getPrlmOgzCCone() {
            return prlmOgzCCone;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }
    }

    private record ApplicationMapView(String apfDcmNo, String pkColNm, Integer fntTbCrySno)
            implements ApplicationMapRepository.ApplicationMapView {
        @Override
        public String getApfDcmNo() {
            return apfDcmNo;
        }

        @Override
        public String getPkColNm() {
            return pkColNm;
        }

        @Override
        public Integer getFntTbCrySno() {
            return fntTbCrySno;
        }
    }

    private record ApplicationSummaryView(
            String apfMngNo,
            String itPtlApfPrgStsC,
            String dcdReqTtl,
            String dcdReqUsid,
            LocalDate dcdReqDtm,
            String rgprDcdReqCone)
            implements ApplicationRepository.ApplicationSummaryView {
        @Override
        public String getApfMngNo() {
            return apfMngNo;
        }

        @Override
        public String getItPtlApfPrgStsC() {
            return itPtlApfPrgStsC;
        }

        @Override
        public String getDcdReqTtl() {
            return dcdReqTtl;
        }

        @Override
        public String getDcdReqUsid() {
            return dcdReqUsid;
        }

        @Override
        public LocalDate getDcdReqDtm() {
            return dcdReqDtm;
        }

        @Override
        public String getRgprDcdReqCone() {
            return rgprDcdReqCone;
        }
    }

    private record ApproverReadView(
            String dcdMngNo,
            Integer dcrSqnSno,
            String dcrEno,
            String itPtlDcdStsC,
            LocalDate dcdDtm,
            String dcrOpnnCone,
            String lstDcdYn)
            implements ApproverRepository.ApproverReadView {
        @Override
        public String getDcdMngNo() {
            return dcdMngNo;
        }

        @Override
        public Integer getDcrSqnSno() {
            return dcrSqnSno;
        }

        @Override
        public String getDcrEno() {
            return dcrEno;
        }

        @Override
        public String getItPtlDcdStsC() {
            return itPtlDcdStsC;
        }

        @Override
        public LocalDate getDcdDtm() {
            return dcdDtm;
        }

        @Override
        public String getDcrOpnnCone() {
            return dcrOpnnCone;
        }

        @Override
        public String getLstDcdYn() {
            return lstDcdYn;
        }
    }

    @Mock private ProjectRepository projectRepository;
    @Mock private ApplicationMapRepository capplaRepository;
    @Mock private ApplicationRepository capplmRepository;
    @Mock private ProjectItemRepository bitemmRepository;
    @Mock private CodeRepository ccodemRepository;
    @Mock private CodeService codeService;
    @Mock private OrganizationRepository corgnIRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private ApproverRepository cdecimRepository;
    @Mock private BbugtmRepository bbugtmRepository;

    /** 환율 표준 조회 헬퍼 (CONTEXT.md 결정 E / R3.7 — Wave 5 추가 의존성) */
    @Mock private com.kdb.it.domain.budget.cost.util.XcrLookupService xcrLookupService;

    @Mock private ProjectBudgetSummaryService projectBudgetSummaryService;

    /** 정보화사업관계(BPROJA) 리포지토리 (대표상태 MAX 계산 — 읽기 경로 의존성) */
    @Mock private com.kdb.it.domain.budget.project.repository.BprojaRepository bprojaRepository;

    /** 정보화사업관계(BPROJA) 동기화 서비스 (예산편성 작성중 '01' 적재 의존성) */
    @Mock private BprojaSyncService bprojaSyncService;

    /** 공통코드 cId→cdva→코드명 맵 생성 공통 헬퍼 (CodeNameMapBuilder 추출 후 의존성) */
    @Mock private com.kdb.it.common.util.CodeNameMapBuilder codeNameMapBuilder;

    /** 조직코드→조직명 해석기 (주관부서명/주관팀명 스냅샷 주입) */
    @Mock private com.kdb.it.common.iam.service.OrgNameResolver orgNameResolver;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks private ProjectService projectService;

    private void stubBulkDetail(Bprojm... projects) {
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(projects));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                eq("BPROJM"), anyList()))
                .willReturn(List.of());
        given(capplmRepository.findSummaryViewsByApfMngNoIn(anyList())).willReturn(List.of());
        given(cdecimRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(anyList()))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
    }

    @BeforeEach
    void setUpSecurity() {
        CustomUserDetails adminUser =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        given(securityContext.getAuthentication()).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(adminUser);
        SecurityContextHolder.setContext(securityContext);
        // projectRepository.save mock: 인자로 받은 엔티티를 그대로 반환(실제 JPA merge/persist 동작 흉내).
        // createProject가 이제 반환값을 project 변수에 재대입하므로(managed 인스턴스 캡처), 스텁하지
        // 않으면 Mockito 기본값(null)이 대입되어 이후 모든 사용처에서 NPE가 난다.
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        doAnswer(
                        invocation -> {
                            ProjectDto.Response response = invocation.getArgument(0);
                            List<Bitemm> items = invocation.getArgument(1);
                            new ProjectBudgetSummaryService(
                                            codeService, new ProjectAmountCalculator())
                                    .applyBudgetSummary(response, items);
                            return null;
                        })
                .when(projectBudgetSummaryService)
                .applyBudgetSummary(any(ProjectDto.Response.class), anyList());
        // projectBudgetSummaryService.calculateAmountSnapshot mock: 실제 구현 위임
        // (Mock 기본 응답은 record 타입에 대해 null이라 스텁하지 않으면 applyAmountSnapshot에서 NPE 발생)
        doAnswer(
                        invocation -> {
                            List<Bitemm> items = invocation.getArgument(0);
                            BigDecimal paidAmt = invocation.getArgument(1);
                            return new ProjectBudgetSummaryService(
                                            codeService, new ProjectAmountCalculator())
                                    .calculateAmountSnapshot(items, paidAmt);
                        })
                .when(projectBudgetSummaryService)
                .calculateAmountSnapshot(anyList(), any(BigDecimal.class));
        // 기본 조직명 스냅샷: 미등록 코드로 간주해 null 반환 (기존 테스트 무영향)
        org.mockito.Mockito.lenient()
                .when(orgNameResolver.resolveName(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
        ProjectQueryAssembler queryAssembler =
                new ProjectQueryAssembler(
                        capplaRepository,
                        capplmRepository,
                        bitemmRepository,
                        corgnIRepository,
                        cuserIRepository,
                        cdecimRepository,
                        ccodemRepository,
                        bbugtmRepository,
                        codeService,
                        projectBudgetSummaryService,
                        bprojaRepository,
                        codeNameMapBuilder,
                        projectRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(
                projectService,
                "projectQueryService",
                new ProjectQueryService(projectRepository, queryAssembler));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createProject: 주관팀코드(SVN_TEM_C)/개발팀코드(DVM_TEM_C)를 담당자 소속 팀코드로 채운다")
    void createProject_populatesTeamCodesFromManagers() {
        // Arrange: 주관부서담당자(USID)=10003 소속 팀 18010, IT부서담당자(DVM_USID)=10001 소속 팀 21020
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(cuserIRepository.findByEno("10003"))
                .willReturn(Optional.of(CuserI.builder().eno("10003").temC("18010").build()));
        given(cuserIRepository.findByEno("10001"))
                .willReturn(Optional.of(CuserI.builder().eno("10001").temC("21020").build()));
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("담당자 팀코드 사업")
                        .bseYy("2026")
                        .usid("10003")
                        .dvmUsid("10001")
                        .build();

        // Act
        projectService.createProject(request);

        // Assert: 저장 엔티티의 주관팀/개발팀 코드가 각 담당자 팀코드로 채워진다
        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getSvnTemC()).isEqualTo("18010");
        assertThat(captor.getValue().getDvmTemC()).isEqualTo("21020");
    }

    @Test
    @DisplayName("프로젝트 생성 시 주관부서명/주관팀명을 CORGNI 스냅샷으로 저장한다")
    void createProject_storesSvnOrgNameSnapshot() {
        // Arrange: 주관부서담당자(USID)=10003 소속 팀 18010 → 팀명 스냅샷 검증
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(cuserIRepository.findByEno("10003"))
                .willReturn(
                        Optional.of(
                                CuserI.builder().eno("10003").temC("18010").temNm("PMO팀").build()));
        given(orgNameResolver.resolveName("BBR001")).willReturn("주관부서명A");
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("조직명 스냅샷 사업")
                        .bseYy("2026")
                        .svnDpmC("BBR001")
                        .usid("10003")
                        .build();

        // Act
        projectService.createProject(request);

        // Assert: 저장 엔티티에 주관부서명/주관팀명 스냅샷이 함께 저장된다
        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getSvnDpmNm()).isEqualTo("주관부서명A");
        assertThat(captor.getValue().getSvnTemNm()).isEqualTo("PMO팀");
    }

    @Test
    @DisplayName("getProjectList - DEL_YN=N 프로젝트 목록 반환")
    void getProjectList_전체목록반환() {
        // given
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        // setApplicationInfo 내부의 findBy... 호출 → Mockito 기본값(빈 리스트) 자동 처리
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), anyString(), eq(1)))
                .willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), eq(1), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("getProject - 존재하는 프로젝트 관리번호 조회 시 Response 반환")
    void getProject_존재하는프로젝트_반환() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAbusMngNo()).isEqualTo(prjMngNo);
    }

    @Test
    @DisplayName("getProject - 미존재 프로젝트 조회 시 IllegalArgumentException 발생")
    void getProject_미존재프로젝트_예외발생() {
        // given
        given(projectRepository.findByAbusMngNoAndDelYn("INVALID", "N"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> projectService.getProject("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    @DisplayName("deleteProject - 결재중 신청서 존재 시 IllegalStateException 발생")
    void deleteProject_결재중상태_예외발생() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc(prjMngNo, "N"))
                .willReturn(List.of(project));
        // 결재중 신청서 존재
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(true);

        // when & then (기본 인증 주체가 시스템관리자이므로 차단 사유는 결재중뿐이다)
        assertThatThrownBy(() -> projectService.deleteProject(prjMngNo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재중인 프로젝트는 삭제할 수 없습니다");
    }

    @Test
    @DisplayName("deleteProject - 정상 상태 프로젝트 삭제 시 project.delete() 호출 (delYn=Y)")
    void deleteProject_정상상태_SoftDelete() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc(prjMngNo, "N"))
                .willReturn(List.of(project));
        // 결재중 신청서 없음
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, 1)).willReturn(List.of());

        // when
        projectService.deleteProject(prjMngNo);

        // then: Soft Delete 검증
        assertThat(project.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteProject: 문서 삭제는 최종본뿐 아니라 남아 있는 재신청 초안까지 함께 지운다")
    void deleteProject_문서삭제시_초안까지_함께삭제한다() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm current = mock(Bprojm.class);
        Bprojm draft = mock(Bprojm.class);
        given(current.getAbusMngNo()).willReturn(prjMngNo);
        given(current.getSno()).willReturn(1);
        given(current.getFstEnrUsid()).willReturn("10001");
        given(current.getSvnDpmC()).willReturn("BBR001");
        given(draft.getAbusMngNo()).willReturn(prjMngNo);
        given(draft.getSno()).willReturn(2);
        given(draft.getFstEnrUsid()).willReturn("10001");
        given(draft.getSvnDpmC()).willReturn("BBR001");
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc(prjMngNo, "N"))
                .willReturn(List.of(current, draft));
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, 1)).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, 2)).willReturn(List.of());

        projectService.deleteProject(prjMngNo);

        verify(current).delete();
        verify(draft).delete();
    }

    @Test
    @DisplayName("deleteProject - 미존재 프로젝트 삭제 시 IllegalArgumentException 발생")
    void deleteProject_미존재프로젝트_예외발생() {
        // given
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("INVALID", "N"))
                .willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> projectService.deleteProject("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project not found");
    }

    // ───────────────────────────────────────────────────────
    // getProjectList (신규) — 2건 반환
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectList: DEL_YN=N 프로젝트 2건이 있으면 2건을 반환한다")
    void getProjectList_2건반환() {
        // given: 두 개의 프로젝트 빌더 생성
        Bprojm project1 = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        Bprojm project2 = Bprojm.builder().abusMngNo("PRJ-2026-0002").sno(1).delYn("N").build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project1, project2));
        // 배치 조회: 신청서·부서·사용자 없음
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        // 예산 합계 계산용 코드 조회
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0001");
        assertThat(result.get(1).getAbusMngNo()).isEqualTo("PRJ-2026-0002");
    }

    @Test
    @DisplayName("getProjectList: 사업계획서 사업일정(BBIZSM) 범위를 bizplanSttDt/EndDt에 주입한다")
    void getProjectList_사업계획일정범위주입() {
        // given: 사업 1건 + 해당 사업의 사업계획 일정 범위(MIN STT, MAX END)
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());
        // 네이티브 집계 결과: {ABUS_MNG_NO, MIN(STT_DT), MAX(END_DT)}
        given(projectRepository.findBizplanScheduleRange(anyList()))
                .willReturn(
                        List.<Object[]>of(new Object[] {"PRJ-2026-0001", "20260301", "20260930"}));

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then: 사업계획 일정 범위가 응답에 주입된다
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBizplanSttDt()).isEqualTo("20260301");
        assertThat(result.get(0).getBizplanEndDt()).isEqualTo("20260930");
    }

    @Test
    @DisplayName("getProjectList: 사업계획 일정이 없는 사업은 bizplanSttDt/EndDt가 null이다")
    void getProjectList_사업계획일정없음_null유지() {
        // given: 사업 1건, 사업계획 일정 집계 결과 없음(빈 목록)
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());
        given(projectRepository.findBizplanScheduleRange(anyList())).willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then: 미작성 사업은 null 유지(프론트가 예산 일정으로 폴백)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBizplanSttDt()).isNull();
        assertThat(result.get(0).getBizplanEndDt()).isNull();
    }

    // ───────────────────────────────────────────────────────
    // searchProjectList (신규) — 검색 조건 전달 확인
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchProjectList: 검색 조건을 repository에 전달하고 결과를 반환한다")
    void searchProjectList_검색조건전달확인() {
        // given: 검색 조건 설정
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();

        given(
                        projectRepository.searchByCondition(
                                org.mockito.ArgumentMatchers.eq(condition),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.searchProjectList(condition);

        // then: searchByCondition이 호출되었고 결과 1건 반환
        verify(projectRepository)
                .searchByCondition(
                        org.mockito.ArgumentMatchers.eq(condition),
                        org.mockito.ArgumentMatchers.any());
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("searchProjectList/countProjectList: 일반 사용자의 위조 부서 조건을 인증 부서로 강제한다")
    void searchAndCountProjectList_일반사용자_인증부서로강제() {
        ProjectDto.SearchCondition listCondition = new ProjectDto.SearchCondition();
        listCondition.setSvnDpmC("999");
        ProjectDto.SearchCondition countCondition = new ProjectDto.SearchCondition();
        countCondition.setSvnDpmC("999");
        CustomUserDetails user =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "101");
        given(
                        projectRepository.searchByCondition(
                                any(ProjectDto.SearchCondition.class),
                                any(com.kdb.it.common.util.ListPageParams.class)))
                .willReturn(List.of());
        given(projectRepository.countBySearchCondition(any(ProjectDto.SearchCondition.class)))
                .willReturn(3L);

        List<ProjectDto.Response> result =
                projectService.searchProjectList(
                        listCondition, user, com.kdb.it.common.util.ListPageParams.unpaged());
        long count = projectService.countProjectList(countCondition, user);

        assertThat(result).isEmpty();
        assertThat(count).isEqualTo(3L);
        assertThat(listCondition.getSvnDpmC()).isEqualTo("101");
        assertThat(countCondition.getSvnDpmC()).isEqualTo("101");
        verify(projectRepository)
                .searchByCondition(
                        eq(listCondition), any(com.kdb.it.common.util.ListPageParams.class));
        verify(projectRepository).countBySearchCondition(countCondition);
    }

    @Test
    @DisplayName("searchProjectList/countProjectList: 인증 정보가 없으면 저장소 조회 없이 거부한다")
    void searchAndCountProjectList_인증정보없음_거부() {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();

        assertThatThrownBy(
                        () ->
                                projectService.searchProjectList(
                                        condition,
                                        null,
                                        com.kdb.it.common.util.ListPageParams.unpaged()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> projectService.countProjectList(condition, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never())
                .searchByCondition(
                        any(ProjectDto.SearchCondition.class),
                        any(com.kdb.it.common.util.ListPageParams.class));
        verify(projectRepository, never())
                .countBySearchCondition(any(ProjectDto.SearchCondition.class));
    }

    // ───────────────────────────────────────────────────────
    // createProject (신규) — 관리번호 자동 채번
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 관리번호가 없으면 Oracle 시퀀스로 자동 채번하여 저장한다")
    void createProject_관리번호자동채번() {
        // given: 관리번호 미입력
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("신규 정보화사업").bseYy("2026").build();

        given(projectRepository.getNextSequenceValue()).willReturn(1L);

        // when
        String result = projectService.createProject(request);

        // then: 자동 채번된 관리번호 형식 검증 (PRJ-2026-0001)
        assertThat(result).matches("PRJ-2026-\\d{4}");
        // repository.save() 호출 확인
        verify(projectRepository).save(any(Bprojm.class));
    }

    @Test
    @DisplayName("createProject: 예산편성 요청 작성중 상태(IT_PTL_STS_TC='01')를 BPROJA에 적재한다")
    void createProject_BPROJA_작성중01_적재() {
        // given: 관리번호 미입력 → PRJ-2026-0001 채번
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("신규 정보화사업").bseYy("2026").build();
        given(projectRepository.getNextSequenceValue()).willReturn(1L);

        // when
        String result = projectService.createProject(request);

        // then: 단계 key(CNCD_RFR_NO)=프로젝트관리번호 자신, 상태 '01'로 upsert
        verify(bprojaSyncService).upsert(result, result, "01");
    }

    @Test
    @DisplayName("편성요청서 반입 프로젝트는 예산편성 요청 결재완료 이관 상태 10을 적재한다")
    void markRequestFormImportApproved_BPROJA_결재완료이관10_적재() {
        String projectNo = "PRJ-2026-0001";

        projectService.markRequestFormImportApproved(projectNo);

        verify(bprojaSyncService).upsert(projectNo, projectNo, "10");
    }

    // ───────────────────────────────────────────────────────
    // updateProject (신규) — 정상 수정
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: 정상 수정 시 관리번호를 반환한다")
    void updateProject_정상수정_관리번호반환() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        // 결재중/결재완료 신청서 없음 → 수정 허용
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        // 기존 품목 없음
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정된 사업명").build();

        // when
        String result = projectService.updateProject(prjMngNo, request);

        // then
        assertThat(result).isEqualTo(prjMngNo);
    }

    @Test
    @DisplayName("updateProject: 담당자 소속 팀코드로 주관팀(SVN_TEM_C)/개발팀(DVM_TEM_C)을 갱신한다")
    void updateProject_refreshesTeamCodesFromManagers() {
        // given: 기존 프로젝트, 결재 없음, 품목 없음 + 담당자 팀코드 스텁
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        // 주관부서담당자(USID)=10003 → 팀 18010/PMO팀, IT부서담당자(DVM_USID)=10001 → 팀 21020
        given(cuserIRepository.findByEno("10003"))
                .willReturn(
                        Optional.of(
                                CuserI.builder().eno("10003").temC("18010").temNm("PMO팀").build()));
        given(cuserIRepository.findByEno("10001"))
                .willReturn(Optional.of(CuserI.builder().eno("10001").temC("21020").build()));

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정된 사업명")
                        .usid("10003")
                        .dvmUsid("10001")
                        .build();

        // when
        projectService.updateProject(prjMngNo, request);

        // then: 수정 대상 엔티티(Dirty Checking)의 팀코드/주관팀명이 담당자 기준으로 갱신된다
        assertThat(project.getSvnTemC()).isEqualTo("18010");
        assertThat(project.getDvmTemC()).isEqualTo("21020");
        assertThat(project.getSvnTemNm()).isEqualTo("PMO팀");
    }

    // ───────────────────────────────────────────────────────
    // 의무완료기한(FLF_FSG_DT) yyyyMMdd 정규화 — ORA-12899 회귀 방지
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 의무완료기한(flfFsgDt) ISO 입력을 yyyyMMdd 8자리로 정규화하여 저장한다")
    void createProject_의무완료기한_정규화() {
        // given: 프론트가 "YYYY-MM-DD"(10자)로 전송
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("신규 정보화사업")
                        .bseYy("2026")
                        .flfFsgDt("2025-12-31")
                        .build();
        given(projectRepository.getNextSequenceValue()).willReturn(1L);

        // when
        projectService.createProject(request);

        // then: VARCHAR2(8) 컬럼에 맞게 하이픈 제거된 8자리로 저장
        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getFlfFsgDt()).isEqualTo("20251231");
    }

    @Test
    @DisplayName("updateProject: 의무완료기한(flfFsgDt) ISO 입력을 yyyyMMdd 8자리로 정규화하여 반영한다")
    void updateProject_의무완료기한_정규화() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정된 사업명").flfFsgDt("2025-12-31").build();

        // when (Dirty Checking으로 엔티티에 직접 반영)
        projectService.updateProject(prjMngNo, request);

        // then
        assertThat(project.getFlfFsgDt()).isEqualTo("20251231");
    }

    // ───────────────────────────────────────────────────────
    // 사업구분(ABUS_TC) NOT NULL 정규화 — ORA-01400 회귀 방지
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 사업구분(abusTc) 빈값을 해당없음('0')으로 정규화하여 저장한다")
    void createProject_사업구분_빈값_정규화() {
        // given: 프론트에서 사업구분을 선택하지 않아 빈 문자열이 전달됨
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("신규 정보화사업")
                        .bseYy("2026")
                        .abusTc("")
                        .build();
        given(projectRepository.getNextSequenceValue()).willReturn(1L);

        // when
        projectService.createProject(request);

        // then: Oracle은 빈 문자열을 NULL로 저장해 ABUS_TC NOT NULL 제약(ORA-01400)에 걸리므로
        // '0'(해당없음)으로 정규화되어야 한다
        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository).save(captor.capture());
        assertThat(captor.getValue().getAbusTc()).isEqualTo("0");
    }

    // ───────────────────────────────────────────────────────
    // getProjectsByIds (신규) — 존재+미존재 필터링
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectsByIds: 존재하는 항목만 반환하고 미존재 항목은 제외한다")
    void getProjectsByIds_존재미존재필터링() {
        // given: 첫 번째만 존재, 두 번째는 미존재
        String existingNo = "PRJ-2026-0001";
        String missingNo = "PRJ-NOTEXIST-9999";

        Bprojm project = Bprojm.builder().abusMngNo(existingNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                eq("BPROJM"), anyList()))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(existingNo, missingNo));

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then: 존재하는 1건만 반환, 미존재 1건은 failedIds로 노출
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getAbusMngNo()).isEqualTo(existingNo);
        assertThat(result.failedIds()).containsExactly(missingNo);
        verify(projectRepository, times(1)).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(any(), any());
    }

    @Test
    @DisplayName("getProjectsByIds: 사번을 이름·직위명으로, 전결권 코드를 코드명으로 채운다")
    void getProjectsByIds_이름직위전결권명_채움() {
        // given: 담당자 4명과 전결권 코드가 있는 사업 1건
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .delYn("N")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .edrtTc("3")
                        .build();

        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                eq("BPROJM"), anyList()))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new NameView("10001", "이아이티", "대리"),
                                new NameView("10002", "최아이티", "팀장"),
                                new NameView("10003", "박현업", "차장"),
                                new NameView("10004", "김팀장", null)));
        given(codeNameMapBuilder.build(eq(com.kdb.it.common.code.CommonCodeGroups.EDRT), any()))
                .willReturn(java.util.Map.of("3", "부장"));

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(prjMngNo));

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then
        ProjectDto.Response item = result.items().get(0);
        assertThat(item.getDvmUsidNm()).isEqualTo("이아이티");
        assertThat(item.getDvmUsidPtCNm()).isEqualTo("대리");
        assertThat(item.getDvmTlrUsidPtCNm()).isEqualTo("팀장");
        assertThat(item.getUsidPtCNm()).isEqualTo("차장");
        // 직위 미등록 사용자는 이름만 채우고 직위명은 null로 남긴다
        assertThat(item.getTlrUsidNm()).isEqualTo("김팀장");
        assertThat(item.getTlrUsidPtCNm()).isNull();
        assertThat(item.getEdrtTcNm()).isEqualTo("부장");
    }

    @Test
    @DisplayName("getProjectsByIds: 같은 관리번호의 활성 기본행이 둘이면 데이터 손상으로 실패한다")
    void getProjectsByIds_중복활성행_데이터손상예외() {
        Bprojm first = Bprojm.builder().abusMngNo("PRJ-DUP").sno(1).delYn("N").build();
        Bprojm second = Bprojm.builder().abusMngNo("PRJ-DUP").sno(2).delYn("N").build();
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(first, second));
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("PRJ-DUP"));

        assertThatThrownBy(() -> projectService.getProjectsByIds(request))
                .isInstanceOf(DataCorruptionException.class)
                .hasMessageContaining("PRJ-DUP");
    }

    @Test
    @DisplayName("getProjectsByIds: 단건과 일괄 상세의 신청·상태·품목 응답이 같다")
    void getProjectsByIds_단건일괄_상세동등성() {
        String projectNo = "PRJ-PARITY";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(projectNo)
                        .sno(1)
                        .abusNm("동등성 사업")
                        .svnDpmNm("주관부서 스냅샷")
                        .delYn("N")
                        .build();
        Bproja status =
                Bproja.builder()
                        .abusMngNo(projectNo)
                        .cncdRfrNo("BIZ-" + projectNo)
                        .stsTc("29")
                        .delYn("N")
                        .build();
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-PARITY")
                        .sno(1)
                        .abusMngNo(projectNo)
                        .fntTbCrySno(1)
                        .ioeC("IOE-351-1100-1")
                        .amt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();
        ApplicationMapView cappla = new ApplicationMapView("APF-PARITY", projectNo, 1);
        ApplicationSummaryView capplm =
                new ApplicationSummaryView(
                        "APF-PARITY",
                        ApprovalStatus.IN_PROGRESS.code(),
                        "동등성 결재",
                        null,
                        null,
                        null);

        given(projectRepository.findByAbusMngNoAndDelYn(projectNo, "N"))
                .willReturn(Optional.of(project));
        given(projectRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(project));
        given(
                        capplaRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BPROJM", projectNo, 1))
                .willReturn(List.of(cappla));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                eq("BPROJM"), anyList()))
                .willReturn(List.of(cappla));
        given(capplmRepository.findSummaryViewsByApfMngNoIn(anyList())).willReturn(List.of(capplm));
        given(cdecimRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-PARITY"))
                .willReturn(List.of());
        given(cdecimRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(anyList()))
                .willReturn(List.of());
        given(bprojaRepository.findByAbusMngNoAndDelYn(projectNo, "N")).willReturn(List.of(status));
        given(bprojaRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(status));
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(projectNo, 1, "N"))
                .willReturn(List.of(item));
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyCollection()))
                .willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(ccodemRepository.findByCIdWithValidDate("IOE_351_1100", null))
                .willReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_351_1100")
                                        .cdva("1")
                                        .cdvaNm("개발비")
                                        .build()));

        ProjectDto.Response single = projectService.getProject(projectNo);
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(projectNo));
        ProjectDto.Response bulk = projectService.getProjectsByIds(request).items().getFirst();

        assertThat(bulk).usingRecursiveComparison().isEqualTo(single);
    }

    @Test
    @DisplayName("getProjectsByIds: 여러 사업의 같은 IOE 코드그룹은 한 번만 조회한다")
    void getProjectsByIds_동일Ioe그룹_한번조회() {
        Bprojm first = Bprojm.builder().abusMngNo("PRJ-IOE-1").sno(1).delYn("N").build();
        Bprojm second = Bprojm.builder().abusMngNo("PRJ-IOE-2").sno(1).delYn("N").build();
        stubBulkDetail(first, second);
        given(bitemmRepository.findByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(
                        List.of(
                                Bitemm.builder()
                                        .gclMngNo("GCL-IOE-1")
                                        .sno(1)
                                        .abusMngNo("PRJ-IOE-1")
                                        .fntTbCrySno(1)
                                        .ioeC("IOE-351-1100-1")
                                        .delYn("N")
                                        .build(),
                                Bitemm.builder()
                                        .gclMngNo("GCL-IOE-2")
                                        .sno(1)
                                        .abusMngNo("PRJ-IOE-2")
                                        .fntTbCrySno(1)
                                        .ioeC("IOE-351-1100-2")
                                        .delYn("N")
                                        .build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE_351_1100", null)).willReturn(List.of());
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("PRJ-IOE-1", "PRJ-IOE-2"));

        projectService.getProjectsByIds(request);

        verify(ccodemRepository, times(1)).findByCIdWithValidDate("IOE_351_1100", null);
    }

    // ───────────────────────────────────────────────────────
    // updateProject — 결재중 예외 경로
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: 결재 상태가 차단 대상이면 IllegalStateException을 던진다")
    void updateProject_결재중상태_예외발생() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(true);

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정 시도").build();

        // 기본 인증 주체가 시스템관리자이므로 차단 사유는 결재중뿐이다
        assertThatThrownBy(() -> projectService.updateProject(prjMngNo, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재중인 프로젝트는 수정할 수 없습니다");
    }

    @Test
    @DisplayName("updateProject: 시스템관리자는 결재완료를 차단 상태에서 제외하고 결재중만 확인한다")
    void updateProject_관리자_결재완료제외() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().abusNm("관리자 정정").build());

        // 결재완료(02)를 조회 조건에서 빼야 결재완료 사업이 관리자에게 열린다
        verify(capplaRepository)
                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        "BPROJM", prjMngNo, 1, List.of(ApprovalStatus.IN_PROGRESS.code()));
    }

    @Test
    @DisplayName("updateProject: 관리자가 아니면 결재완료도 차단 상태에 포함한다")
    void updateProject_비관리자_결재완료포함() {
        String prjMngNo = "PRJ-2026-0001";
        CustomUserDetails owner =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "101");
        given(authentication.getPrincipal()).willReturn(owner);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101")
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(true);

        assertThatThrownBy(
                        () ->
                                projectService.updateProject(
                                        prjMngNo,
                                        ProjectDto.UpdateRequest.builder().abusNm("수정 시도").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재중이거나 결재완료된 프로젝트는 수정할 수 없습니다");

        verify(capplaRepository)
                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        "BPROJM",
                        prjMngNo,
                        1,
                        List.of(
                                ApprovalStatus.IN_PROGRESS.code(),
                                ApprovalStatus.COMPLETED.code()));
    }

    @Test
    @DisplayName("deleteProject: 시스템관리자는 결재완료를 차단 상태에서 제외하고 결재중만 확인한다")
    void deleteProject_관리자_결재완료제외() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc(prjMngNo, "N"))
                .willReturn(List.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(prjMngNo, 1)).willReturn(List.of());

        projectService.deleteProject(prjMngNo);

        assertThat(project.getDelYn()).isEqualTo("Y");
        verify(capplaRepository)
                .existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                        "BPROJM", prjMngNo, 1, List.of(ApprovalStatus.IN_PROGRESS.code()));
    }

    // ───────────────────────────────────────────────────────
    // createProject — 기존 관리번호 중복 예외
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 제공된 관리번호가 이미 존재하면 IllegalArgumentException을 던진다")
    void createProject_기존관리번호_중복예외발생() {
        String prjMngNo = "PRJ-2026-EXIST";
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusMngNo(prjMngNo).bseYy("2026").build();

        given(projectRepository.existsByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(true);

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Project already exists");
    }

    // ───────────────────────────────────────────────────────
    // createProject — 품목 포함 생성
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 품목이 포함된 요청이면 품목도 함께 저장한다")
    void createProject_품목포함_save호출() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("소프트웨어 구매");
        item.setAmt(java.math.BigDecimal.valueOf(1_000_000));

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("품목포함 사업")
                        .bseYy("2026")
                        .items(List.of(item))
                        .build();

        // when
        String result = projectService.createProject(request);

        // then: 프로젝트 + 품목 각 1회 save
        assertThat(result).matches("PRJ-2026-\\d{4}");
        org.mockito.Mockito.verify(projectRepository).save(any(Bprojm.class));
        org.mockito.Mockito.verify(bitemmRepository)
                .save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
    }

    @Test
    @DisplayName("createProject: 품목의 당해와 예정 금액은 독립적으로 저장된다")
    void createPreservesIndependentItemPlannedAmount() {
        // given: 당해 요청금액 100원, 내년 이후 요청금액 500원
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(java.math.BigDecimal.ONE);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("소프트웨어 구매");
        item.setAmt(java.math.BigDecimal.valueOf(100));
        item.setMplAmt(java.math.BigDecimal.valueOf(500));

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("독립 예정금액 테스트 사업")
                        .bseYy("2026")
                        .items(List.of(item))
                        .build();

        // when
        projectService.createProject(request);

        // then: AMT와 MPL_AMT를 각각 입력값 그대로 보존한다.
        ArgumentCaptor<Bitemm> itemCaptor = ArgumentCaptor.forClass(Bitemm.class);
        org.mockito.Mockito.verify(bitemmRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getAmt())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(100));
        assertThat(itemCaptor.getValue().getMplAmt())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(500));
    }

    @Test
    @DisplayName("createProject: 품목 예정금액이 음수면 거부한다")
    void createRejectsNegativeItemPlannedAmount() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(xcrLookupService.resolveXcr(any(), any())).willReturn(java.math.BigDecimal.ONE);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("소프트웨어 구매");
        item.setAmt(java.math.BigDecimal.valueOf(100));
        item.setMplAmt(java.math.BigDecimal.valueOf(-1));

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("음수 예정금액 테스트 사업")
                        .bseYy("2026")
                        .items(List.of(item))
                        .build();

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("예정금액은 0 이상이어야 합니다.");
    }

    // ───────────────────────────────────────────────────────
    // updateProject — 신규 품목 추가
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: 신규 품목(gclMngNo=null)이 포함된 요청이면 품목을 save한다")
    void updateProject_신규품목추가_save호출() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(bitemmRepository.getNextSequenceValue()).willReturn(2L);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto newItem = new ProjectDto.BitemmDto();
        newItem.setIoeC("IOE-351-0100");
        newItem.setGclNm("신규 품목");
        newItem.setAmt(java.math.BigDecimal.valueOf(500_000));

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정 사업명").items(List.of(newItem)).build();

        // when
        String result = projectService.updateProject(prjMngNo, request);

        // then: 신규 품목 save 호출
        assertThat(result).isEqualTo(prjMngNo);
        org.mockito.Mockito.verify(bitemmRepository)
                .save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
    }

    // ───────────────────────────────────────────────────────
    // updateProject — 기존 품목 삭제(요청에 없는 항목 soft-delete)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: 요청에 없는 기존 품목은 Soft Delete 된다")
    void updateProject_기존품목삭제_SoftDelete() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        // 기존 품목 1건 (gclMngNo="GCL-0001")
        com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-237-0700")
                        .gclNm("기존 품목")
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        // 요청에 품목 없음 → 기존 품목 전부 soft-delete
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder().abusNm("수정 사업명").items(List.of()).build();

        // when
        projectService.updateProject(prjMngNo, request);

        // then: 기존 품목이 DEL_YN='Y'로 soft-delete 됨
        assertThat(existingItem.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("createProject: 관리번호가 있고 중복이 없으면 제공된 관리번호로 저장한다")
    void createProject_제공관리번호_중복없음_저장() {
        String prjMngNo = "PRJ-2026-MANUAL";
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusMngNo(prjMngNo)
                        .abusNm("수기 관리번호 사업")
                        .abusCone("<script>alert(1)</script><p>설명</p>")
                        .abusRngCone("<b>범위</b>")
                        .build();
        given(projectRepository.existsByAbusMngNoAndDelYn(prjMngNo, "N")).willReturn(false);

        String result = projectService.createProject(request);

        assertThat(result).isEqualTo(prjMngNo);
        assertThat(request.getAbusCone()).doesNotContain("<script>");
        verify(projectRepository).save(any(Bprojm.class));
    }

    @Test
    @DisplayName("createProject: 사업연도가 없으면 현재 연도로 채번한다")
    void createProject_사업연도없음_현재연도채번() {
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder().abusNm("연도 기본값 사업").build();
        given(projectRepository.getNextSequenceValue()).willReturn(3L);

        String result = projectService.createProject(request);

        assertThat(result).matches("PRJ-\\d{4}-0003");
        assertThat(request.getBseYy()).matches("\\d{4}");
    }

    @Test
    @DisplayName("updateProject: 기존 품목이 변경되면 새 레코드 추가 없이 기존 레코드를 제자리 수정한다")
    void updateProject_기존품목변경_제자리수정() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-237-0700")
                        .gclNm("기존 품목")
                        .qty(java.math.BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(java.math.BigDecimal.ONE)
                        .cncdFdtnCone("기존 근거")
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(java.math.BigDecimal.valueOf(1000))
                        .delYn("N")
                        .build();
        ProjectDto.BitemmDto changedItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0001")
                        .ioeC("IOE-237-0700")
                        .gclNm("변경 품목")
                        .qty(java.math.BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(java.math.BigDecimal.ONE)
                        .cncdFdtnCone("기존 근거")
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(java.math.BigDecimal.valueOf(1000))
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        projectService.updateProject(
                prjMngNo,
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정 사업")
                        .items(List.of(changedItem))
                        .build());

        // 제자리 수정: 기존 레코드가 삭제되지 않고(DEL_YN='N') 필드만 갱신되며, 신규 save는 호출되지 않는다
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getSno()).isEqualTo(1);
        assertThat(existingItem.getGclNm()).isEqualTo("변경 품목");
        verify(bitemmRepository, org.mockito.Mockito.never())
                .save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
    }

    @Test
    @DisplayName("updateProject: 기존 품목이 변경되지 않으면 버저닝 저장하지 않는다")
    void updateProject_기존품목변경없음_저장없음() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        com.kdb.it.domain.budget.project.entity.Bitemm existingItem =
                com.kdb.it.domain.budget.project.entity.Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-237-0700")
                        .gclNm("동일 품목")
                        .qty(java.math.BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(java.math.BigDecimal.ONE)
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(java.math.BigDecimal.valueOf(1000))
                        .delYn("N")
                        .build();
        ProjectDto.BitemmDto sameItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0001")
                        .ioeC("IOE-237-0700")
                        .gclNm("동일 품목")
                        .qty(java.math.BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(java.math.BigDecimal.ONE)
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(java.math.BigDecimal.valueOf(1000))
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(sameItem)).build());

        assertThat(existingItem.getDelYn()).isEqualTo("N");
        org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.never())
                .save(any(com.kdb.it.domain.budget.project.entity.Bitemm.class));
    }

    @Test
    @DisplayName("getProjectsByIds: 배경연도가 있으면 편성예산을 자본/경상으로 분류한다")
    void getProjectsByIds_배경연도있음_편성예산분류() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        stubBulkDetail(project);
        given(codeService.findCodeEntitiesByCId("IOE_CPIT"))
                .willReturn(List.of(Ccodem.builder().cId("IOE-ASSET").cdvaDes("개발비").build()));
        given(codeService.findCodeEntitiesByCId("IOE_IDR"))
                .willReturn(List.of(Ccodem.builder().cId("IOE-COST").build()));
        given(codeService.findCodeEntitiesByCId("IOE_SEVS")).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IOE_XPN")).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("IOE_LEAFE")).willReturn(List.of());
        given(bbugtmRepository.sumDupBgByPrjMngNos(List.of(prjMngNo), "2026"))
                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(1000)));
        given(bbugtmRepository.sumAssetDupBgByPrjMngNos(eq(List.of(prjMngNo)), eq("2026"), any()))
                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(700)));
        given(bbugtmRepository.sumCostDupBgByPrjMngNos(eq(List.of(prjMngNo)), eq("2026"), any()))
                .willReturn(java.util.Map.of(prjMngNo, java.math.BigDecimal.valueOf(300)));
        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(prjMngNo));
        request.setBseYy("2026");

        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getDupBgAmt())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(1000));
        assertThat(result.items().get(0).getAssetDupBg())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(700));
        assertThat(result.items().get(0).getCostDupBg())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(300));
    }

    @Test
    @DisplayName("getProject: 신청서, 코드명, 예산 합계를 함께 채운다")
    void getProject_상세보강정보_함께반환() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .dvmDpmC("101")
                        .svnDpmC("102")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .delYn("N")
                        .build();
        Bitemm devItem =
                Bitemm.builder()
                        .ioeC("101")
                        .amt(BigDecimal.valueOf(100))
                        .xcr(BigDecimal.TEN)
                        .build();
        Bitemm machItem =
                Bitemm.builder()
                        .ioeC("102")
                        .amt(BigDecimal.valueOf(200))
                        .xcr(BigDecimal.ZERO)
                        .build();
        Bitemm costItem =
                Bitemm.builder().ioeC("103").amt(BigDecimal.valueOf(300)).xcr(null).build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        "BPROJM", prjMngNo, 1))
                .willReturn(List.of(new ApplicationMapView("APF-001", prjMngNo, 1)));
        given(capplmRepository.findSummaryViewsByApfMngNoIn(List.of("APF-001")))
                .willReturn(
                        List.of(
                                new ApplicationSummaryView(
                                        "APF-001",
                                        ApprovalStatus.IN_PROGRESS.code(),
                                        "신청서",
                                        "10001",
                                        LocalDate.of(2026, 7, 21),
                                        "요청")));
        given(cdecimRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-001"))
                .willReturn(
                        List.of(new ApproverReadView("APF-001", 1, "10002", "1", null, null, "Y")));
        given(corgnIRepository.findNameViewByPrlmOgzCCone("101"))
                .willReturn(Optional.of(new OrgNameView("101", "IT부")));
        given(corgnIRepository.findNameViewByPrlmOgzCCone("102"))
                .willReturn(Optional.of(new OrgNameView("102", "현업부")));
        given(cuserIRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "담당자", "차장")));
        given(cuserIRepository.findNameViewByEno("10002"))
                .willReturn(Optional.of(new NameView("10002", "팀장", "팀장")));
        given(cuserIRepository.findNameViewByEno("10003"))
                .willReturn(Optional.of(new NameView("10003", "현업담당", "대리")));
        given(cuserIRepository.findNameViewByEno("10004"))
                .willReturn(Optional.of(new NameView("10004", "현업팀장", "부장")));
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(
                        List.of(
                                devItem,
                                machItem,
                                costItem,
                                Bitemm.builder().ioeC(null).amt(BigDecimal.ONE).build(),
                                Bitemm.builder().ioeC("IOE-NULL").amt(null).build()));
        given(codeService.findCodeEntitiesByCIdWithoutCache("IOE_C"))
                .willReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("101")
                                        .cdvaNm("개발비")
                                        .cTp("IOE_DVC")
                                        .build(),
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("102")
                                        .cdvaNm("기계장치")
                                        .cTp("IOE_HW")
                                        .build(),
                                Ccodem.builder().cId("IOE_C").cdva("103").cTp("IOE_IDR").build()));
        given(ccodemRepository.findByCIdWithValidDate("IOE_C", null))
                .willReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("101")
                                        .cdvaNm("개발비")
                                        .cTp("IOE_DVC")
                                        .build(),
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("102")
                                        .cdvaNm("기계장치")
                                        .cTp("IOE_HW")
                                        .build(),
                                Ccodem.builder().cId("IOE_C").cdva("103").cTp("IOE_IDR").build()));

        ProjectDto.Response result = projectService.getProject(prjMngNo);

        assertThat(result.getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.getApfSts()).isEqualTo("결재중");
        assertThat(result.getApplicationInfo().getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.getApplicationInfo().getApprovers()).hasSize(1);
        assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
        assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
        assertThat(result.getDvmUsidNm()).isEqualTo("담당자");
        assertThat(result.getDvmUsidPtCNm()).isEqualTo("차장");
        assertThat(result.getDvmTlrUsidPtCNm()).isEqualTo("팀장");
        assertThat(result.getUsidPtCNm()).isEqualTo("대리");
        assertThat(result.getTlrUsidPtCNm()).isEqualTo("부장");
        assertThat(result.getAssetBg()).isEqualByComparingTo("300");
        assertThat(result.getDvcBg()).isEqualByComparingTo("100");
        assertThat(result.getHwBg()).isEqualByComparingTo("200");
        assertThat(result.getCostBg()).isEqualByComparingTo("300");
        assertThat(result.getItems().get(0).getIoeCNm()).isEqualTo("개발비");
        assertThat(result.getItems().get(1).getIoeCNm()).isEqualTo("기계장치");
        verify(capplaRepository)
                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BPROJM", prjMngNo, 1);
        verify(capplmRepository).findSummaryViewsByApfMngNoIn(List.of("APF-001"));
        verify(cdecimRepository).findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-001");
    }

    @Test
    @DisplayName("updateProject: 일반사용자가 타인 작성 프로젝트를 수정하면 거부된다")
    void updateProject_일반사용자_타인작성수정거부() {
        CustomUserDetails user =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "999");
        given(authentication.getPrincipal()).willReturn(user);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));

        assertThatThrownBy(
                        () ->
                                projectService.updateProject(
                                        "PRJ-2026-0001",
                                        ProjectDto.UpdateRequest.builder().abusNm("수정").build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("updateProject: 부서관리자가 다른 부서 프로젝트를 수정하면 거부된다")
    void updateProject_부서관리자_타부서수정거부() {
        CustomUserDetails manager =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
        given(authentication.getPrincipal()).willReturn(manager);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));

        assertThatThrownBy(
                        () ->
                                projectService.updateProject(
                                        "PRJ-2026-0001",
                                        ProjectDto.UpdateRequest.builder().abusNm("수정").build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteProject: 품목이 있으면 프로젝트와 품목을 함께 논리삭제한다")
    void deleteProject_품목포함_함께논리삭제() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        Bitemm item = Bitemm.builder().gclMngNo("GCL-0001").sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1))
                .willReturn(List.of(item));

        projectService.deleteProject("PRJ-2026-0001");

        assertThat(project.getDelYn()).isEqualTo("Y");
        assertThat(item.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteProject: 재신청 초안을 삭제해도 원본 사업과 품목은 보존한다")
    void deleteProject_재신청초안삭제_원본사업과품목보존() {
        String projectNo = "PRJ-2027-0600";
        Bprojm original =
                Bprojm.builder().abusMngNo(projectNo).sno(1).lstYn("Y").delYn("N").build();
        Bprojm draft = Bprojm.builder().abusMngNo(projectNo).sno(2).lstYn("N").delYn("N").build();
        Bitemm originalItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0932")
                        .sno(1)
                        .abusMngNo(projectNo)
                        .fntTbCrySno(1)
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        Bitemm draftItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0933")
                        .sno(1)
                        .abusMngNo(projectNo)
                        .fntTbCrySno(2)
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn(projectNo, 2, "N"))
                .willReturn(Optional.of(draft));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(projectNo), eq(2), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno(projectNo, 2))
                .willReturn(List.of(draftItem));

        projectService.deleteProject(projectNo, 2);

        assertThat(draft.getDelYn()).isEqualTo("Y");
        assertThat(draftItem.getDelYn()).isEqualTo("Y");
        assertThat(original.getDelYn()).isEqualTo("N");
        assertThat(originalItem.getDelYn()).isEqualTo("N");
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(projectNo, "N");
        verify(bitemmRepository, never()).findByAbusMngNoAndFntTbCrySno(projectNo, 1);
    }

    @Test
    @DisplayName("getProjectList: 배치 보강으로 신청서, 부서명, 담당자명을 설정한다")
    void getProjectList_배치보강정보설정() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .dvmDpmC("101")
                        .svnDpmC("102")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .delYn("N")
                        .build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                "BPROJM", List.of("PRJ-2026-0001")))
                .willReturn(
                        List.of(
                                new ApplicationMapView("APF-001", "PRJ-2026-0001", 1),
                                new ApplicationMapView("APF-OLD", "PRJ-2026-0001", 1)));
        given(capplmRepository.findSummaryViewsByApfMngNoIn(List.of("APF-001")))
                .willReturn(
                        List.of(
                                new ApplicationSummaryView(
                                        "APF-001",
                                        ApprovalStatus.IN_PROGRESS.code(),
                                        "신청서",
                                        "10001",
                                        LocalDate.of(2026, 7, 21),
                                        "요청")));
        given(cdecimRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(List.of("APF-001")))
                .willReturn(
                        List.of(new ApproverReadView("APF-001", 1, "10002", "1", null, null, "Y")));
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(any()))
                .willReturn(List.of(new OrgNameView("101", "IT부"), new OrgNameView("102", "현업부")));
        given(cuserIRepository.findNameViewsByEnoIn(any()))
                .willReturn(
                        List.of(
                                new NameView("10001", "IT담당", "대리"),
                                new NameView("10002", "IT팀장", "팀장"),
                                new NameView("10003", "현업담당", "차장"),
                                new NameView("10004", "현업팀장", "부장")));
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        List<ProjectDto.Response> result = projectService.getProjectList();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.get(0).getApfSts()).isEqualTo("결재중");
        assertThat(result.get(0).getApplicationInfo().getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.get(0).getDvmDpmCNm()).isEqualTo("IT부");
        assertThat(result.get(0).getSvnDpmCNm()).isEqualTo("현업부");
        assertThat(result.get(0).getDvmUsidNm()).isEqualTo("IT담당");
        assertThat(result.get(0).getTlrUsidNm()).isEqualTo("현업팀장");
        assertThat(result.get(0).getDvmUsidPtCNm()).isEqualTo("대리");
        assertThat(result.get(0).getTlrUsidPtCNm()).isEqualTo("부장");
        verify(capplaRepository)
                .findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BPROJM", List.of("PRJ-2026-0001"));
        verify(capplmRepository).findSummaryViewsByApfMngNoIn(List.of("APF-001"));
        verify(cdecimRepository).findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(List.of("APF-001"));
    }

    @Test
    @DisplayName("updateProject: 인증 주체가 CustomUserDetails가 아니면 거부된다")
    void updateProject_인증주체비정상_거부() {
        given(authentication.getPrincipal()).willReturn("anonymous");
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));

        assertThatThrownBy(
                        () ->
                                projectService.updateProject(
                                        "PRJ-2026-0001",
                                        ProjectDto.UpdateRequest.builder().build()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("인증 정보");
    }

    @Test
    @DisplayName("updateProject: 부서관리자는 같은 부서 프로젝트를 수정할 수 있다")
    void updateProject_부서관리자_동일부서허용() {
        CustomUserDetails manager =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "101");
        given(authentication.getPrincipal()).willReturn(manager);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of());

        String result =
                projectService.updateProject(
                        "PRJ-2026-0001", ProjectDto.UpdateRequest.builder().build());

        assertThat(result).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("updateProject: 일반사용자는 본인 작성 프로젝트를 수정할 수 있다")
    void updateProject_일반사용자_본인작성허용() {
        CustomUserDetails user =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "999");
        given(authentication.getPrincipal()).willReturn(user);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of());

        String result =
                projectService.updateProject(
                        "PRJ-2026-0001", ProjectDto.UpdateRequest.builder().build());

        assertThat(result).isEqualTo("PRJ-2026-0001");
    }

    // ───────────────────────────────────────────────────────
    // buildCodeNameMap — cdvas 필터 + merge 람다 커버
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectList: 배치 보강 시 buildCodeNameMap이 지정 cdvas만 필터링하여 코드명을 반환한다")
    void buildCodeNameMap_cdvas필터와merge람다커버() {
        // given: 두 개의 프로젝트 (사업유형은 컬럼에 코드값명을 직접 저장)
        Bprojm project1 =
                Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").bzTpC("일반사업").build();
        Bprojm project2 =
                Bprojm.builder().abusMngNo("PRJ-2026-0002").sno(2).delYn("N").bzTpC("일반사업").build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project1, project2));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then: 두 프로젝트 모두 반환되며 bzTpCNm이 컬럼값(명) 그대로 설정됨
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBzTpCNm()).isEqualTo("일반사업");
        assertThat(result.get(1).getBzTpCNm()).isEqualTo("일반사업");
    }

    // ───────────────────────────────────────────────────────
    // getProjectsByIds — bgYy 없음 분기 (편성예산 계산 skip)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectsByIds: bgYy가 null이면 편성예산 계산을 건너뛰고 응답만 반환한다")
    void getProjectsByIds_bgYy없음_편성예산skip() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        stubBulkDetail(project);

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(prjMngNo));
        request.setBseYy(null); // bgYy 없음 → 편성예산 skip 분기

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then: 프로젝트 반환, bbugtmRepository.sumDupBgByPrjMngNos 미호출
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).getAbusMngNo()).isEqualTo(prjMngNo);
        org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                .sumDupBgByPrjMngNos(anyList(), anyString());
    }

    // ───────────────────────────────────────────────────────
    // enrichProjectListBatch — orgCodes/userEnos 없는 경우 분기 커버
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectList: cappla 있고 capplm 없으면 apfSts만 설정하지 않고 응답을 반환한다")
    void getProjectList_cappla있고capplm없음_apfMngNo만설정() {
        // given: cappla 있음, capplmRepository 반환 없음 → capplm == null 분기 커버
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(
                        capplaRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                                anyString(), anyList()))
                .willReturn(List.of(new ApplicationMapView("APF-NOCAPLM", "PRJ-2026-0001", 1)));
        // capplmRepository.findSummaryViewsByApfMngNoIn → 빈 목록 → capplmMap.get() == null → capplm
        // null 분기
        given(capplmRepository.findSummaryViewsByApfMngNoIn(anyList())).willReturn(List.of());
        given(cdecimRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then: apfMngNo는 설정, apfSts는 null
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApfMngNo()).isEqualTo("APF-NOCAPLM");
        assertThat(result.get(0).getApfSts()).isNull();
    }

    @Test
    @DisplayName("getProjectList: bzDtt/tchnTp/mnUsr/rprSts/prjPulPtt/pulDtt 있는 프로젝트의 코드명을 배치 조회한다")
    void getProjectList_추가코드필드_배치코드명조회() {
        // given: 코드 필드가 모두 있는 프로젝트 → buildCodeNameMap 분기 다수 커버
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .delYn("N")
                        // 사업유형/업무구분/기술분야/고객유형은 컬럼에 코드값명을 직접 저장
                        .bzTpC("사업유형A")
                        .bzDttNm("업무구분B1")
                        .sklTpTc("기술분야C1")
                        .cstTpTc("주요사용자D1")
                        .rprStsTc("E1")
                        .exePttYn("F1")
                        .abusTc("G1")
                        .build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        // codeNameMapBuilder: 요청된 cdva 집합을 전체 코드명 맵에서 필터링(헬퍼 동작 모사)
        Map<String, String> allCodeNames = Map.of("E1", "보고상태E1", "F1", "추진가능F1", "G1", "사업구분G1");
        given(codeNameMapBuilder.build(anyString(), any()))
                .willAnswer(
                        inv -> {
                            Set<String> requested = inv.getArgument(1);
                            Map<String, String> filtered = new HashMap<>();
                            if (requested != null) {
                                for (String cdva : requested) {
                                    if (allCodeNames.containsKey(cdva)) {
                                        filtered.put(cdva, allCodeNames.get(cdva));
                                    }
                                }
                            }
                            return filtered;
                        });
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then: 프로젝트 1건 반환
        assertThat(result).hasSize(1);
        // 사업유형/업무구분은 컬럼값(코드값명)을 그대로 *Nm 필드에 노출
        assertThat(result.get(0).getBzTpCNm()).isEqualTo("사업유형A");
        assertThat(result.get(0).getBzDttNmNm()).isEqualTo("업무구분B1");
    }

    @Test
    @DisplayName("getProjectList: itDpm/svnDpm 없는 프로젝트도 정상 처리된다")
    void getProjectList_부서정보없음_정상처리() {
        // given: 부서/담당자 정보가 null인 프로젝트 (enrichProjectListBatch null-check 분기 커버)
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0009")
                        .sno(1)
                        .delYn("N")
                        .dvmDpmC(null)
                        .svnDpmC(null)
                        .dvmUsid(null)
                        .dvmTlrUsid(null)
                        .usid(null)
                        .tlrUsid(null)
                        .bzTpC(null)
                        .bzDttNm(null)
                        .sklTpTc(null)
                        .cstTpTc(null)
                        .rprStsTc(null)
                        .exePttYn(null)
                        .abusTc(null)
                        .build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAbusMngNo()).isEqualTo("PRJ-2026-0009");
    }

    @Test
    @DisplayName("updateProject: 기존 품목의 마지막 금액 필드만 달라도 변경으로 판단해 제자리 수정한다")
    void updateProject_기존품목_금액만변경_제자리수정() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-237")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(null)
                        .xcrBseDt("20260101")
                        .cncdFdtnCone("근거")
                        .bseYm("2026-02")
                        .dfrCleC("매월")
                        .sectSysUtzYn("Y")
                        .itrInfrYn("Y")
                        .amt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();
        ProjectDto.BitemmDto changed =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0001")
                        .ioeC("IOE-237")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(null)
                        .xcrBseDt("20260101")
                        .cncdFdtnCone("근거")
                        .bseYm("2026-02")
                        .dfrCleC("매월")
                        .sectSysUtzYn("Y")
                        .itrInfrYn("Y")
                        .amt(BigDecimal.valueOf(200))
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(changed)).build());

        // 금액만 바뀌어도 변경으로 판단 → 제자리 수정 (삭제·신규 save 없음)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getAmt()).isEqualByComparingTo(BigDecimal.valueOf(200));
        verify(bitemmRepository, org.mockito.Mockito.never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("getProjectList: 프로젝트가 없으면 배치 보강 없이 빈 목록을 반환한다")
    void getProjectList_빈목록_빈목록반환() {
        given(projectRepository.findAllByDelYn("N")).willReturn(List.of());

        List<ProjectDto.Response> result = projectService.getProjectList();

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getProjectsByIds — bgYy null/blank (편성예산 조회 미실행)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectsByIds: bgYy가 null이면 편성예산 조회 없이 기본 정보만 반환한다")
    void getProjectsByIds_bgYyNull_기본정보만반환() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        stubBulkDetail(project);

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(prjMngNo));
        request.setBseYy(null); // bgYy = null → 편성예산 조회 건너뜀

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then
        assertThat(result.items()).hasSize(1);
        org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                .sumDupBgByPrjMngNos(any(), any());
    }

    @Test
    @DisplayName("getProjectsByIds: bgYy가 공백이면 편성예산 조회 없이 기본 정보만 반환한다")
    void getProjectsByIds_bgYyBlank_기본정보만반환() {
        // given
        String prjMngNo = "PRJ-2026-0002";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        stubBulkDetail(project);

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of(prjMngNo));
        request.setBseYy("   "); // 공백 → isBlank() true

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then
        assertThat(result.items()).hasSize(1);
        org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                .sumDupBgByPrjMngNos(any(), any());
    }

    @Test
    @DisplayName("getProjectsByIds: 요청 목록이 모두 미존재이면 빈 목록을 반환한다")
    void getProjectsByIds_모두미존재_빈목록() {
        // given
        given(projectRepository.findByAbusMngNoAndDelYn("INVALID-1", "N"))
                .willReturn(Optional.empty());
        given(projectRepository.findByAbusMngNoAndDelYn("INVALID-2", "N"))
                .willReturn(Optional.empty());

        ProjectDto.BulkGetRequest request = new ProjectDto.BulkGetRequest();
        request.setPrjMngNos(List.of("INVALID-1", "INVALID-2"));
        request.setBseYy("2026");

        // when
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(request);

        // then: bbugtmRepository 미호출 (responses가 empty)
        assertThat(result.items()).isEmpty();
        org.mockito.Mockito.verify(bbugtmRepository, org.mockito.Mockito.never())
                .sumDupBgByPrjMngNos(any(), any());
    }

    @Test
    @DisplayName("getProjectsByIds: 전건 미존재면 items empty, failedIds 전부 (bseYy null)")
    void getProjectsByIds_allMissing_collectsFailedIds() {
        given(projectRepository.findByAbusMngNoAndDelYn(anyString(), eq("N")))
                .willReturn(Optional.empty());
        ProjectDto.BulkGetRequest req = new ProjectDto.BulkGetRequest();
        req.setPrjMngNos(List.of("PRJ-X", "PRJ-Y"));
        req.setBseYy(null);
        ProjectDto.BulkResponse result = projectService.getProjectsByIds(req);
        assertThat(result.items()).isEmpty();
        assertThat(result.failedIds()).containsExactly("PRJ-X", "PRJ-Y");
    }

    // ───────────────────────────────────────────────────────
    // deleteProject — RBAC 권한 검증 (일반사용자/부서관리자)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProject: 일반사용자가 본인 작성 프로젝트를 삭제할 수 있다")
    void deleteProject_일반사용자_본인작성_삭제허용() {
        // given
        CustomUserDetails user =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "999");
        given(authentication.getPrincipal()).willReturn(user);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001") // 본인 작성
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1))
                .willReturn(List.of());

        // when
        projectService.deleteProject("PRJ-2026-0001");

        // then
        assertThat(project.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteProject: 일반사용자가 타인 작성 프로젝트를 삭제하면 거부된다")
    void deleteProject_일반사용자_타인작성_거부() {
        // given
        CustomUserDetails user =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_USER), "999");
        given(authentication.getPrincipal()).willReturn(user);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001") // 타인 작성
                        .svnDpmC("101")
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteProject: 부서관리자가 같은 부서 프로젝트를 삭제할 수 있다")
    void deleteProject_부서관리자_동일부서_삭제허용() {
        // given
        CustomUserDetails manager =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "101");
        given(authentication.getPrincipal()).willReturn(manager);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101") // 같은 부서
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq("PRJ-2026-0001"), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySno("PRJ-2026-0001", 1))
                .willReturn(List.of());

        // when
        projectService.deleteProject("PRJ-2026-0001");

        // then
        assertThat(project.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteProject: 부서관리자가 다른 부서 프로젝트를 삭제하면 거부된다")
    void deleteProject_부서관리자_타부서_거부() {
        // given
        CustomUserDetails manager =
                new CustomUserDetails("20001", List.of(CustomUserDetails.ATH_DEPT_MGR), "999");
        given(authentication.getPrincipal()).willReturn(manager);
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .fstEnrUsid("10001")
                        .svnDpmC("101") // 다른 부서
                        .delYn("N")
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteProject: 인증 주체가 비정상이면 거부된다")
    void deleteProject_인증주체비정상_거부() {
        // given
        given(authentication.getPrincipal()).willReturn("anonymous");
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYnOrderBySnoAsc("PRJ-2026-0001", "N"))
                .willReturn(List.of(project));

        // when & then
        assertThatThrownBy(() -> projectService.deleteProject("PRJ-2026-0001"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("인증 정보");
    }

    // ───────────────────────────────────────────────────────
    // getProject — 신청서 없는/CAPPLM 없는 경우
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProject: 신청서가 없으면 apfMngNo가 null이다")
    void getProject_신청서없음_apfMngNoNull() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of()); // 신청서 없음
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getApfMngNo()).isNull();
    }

    @Test
    @DisplayName("getProject: CAPPLA는 있지만 CAPPLM이 없으면 apfSts가 null이다")
    void getProject_capplaExist_capplmMissing_apfStsNull() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository
                                .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                        anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of(new ApplicationMapView("APF-001", prjMngNo, 1)));
        given(capplmRepository.findSummaryViewsByApfMngNoIn(List.of("APF-001")))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then
        assertThat(result.getApfMngNo()).isEqualTo("APF-001");
        assertThat(result.getApfSts()).isNull();
    }

    // ───────────────────────────────────────────────────────
    // getProject — IOE_CPIT cdvaDes 기반 자본예산 세부 분류
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProject: IOE_CPIT 코드의 cdvaDes에 따라 단말기/기계장치/기타무형자산으로 분류된다")
    void getProject_ioeCpit_cdvaDes분류() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm devItem =
                Bitemm.builder().ioeC("DEV-001").amt(BigDecimal.valueOf(100)).xcr(null).build();
        Bitemm machItem =
                Bitemm.builder()
                        .ioeC("MACH-001")
                        .amt(BigDecimal.valueOf(200))
                        .xcr(BigDecimal.ZERO)
                        .build();
        Bitemm intanItem =
                Bitemm.builder()
                        .ioeC("INTAN-001")
                        .amt(BigDecimal.valueOf(300))
                        .xcr(BigDecimal.ONE)
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(devItem, machItem, intanItem));
        given(codeService.findCodeEntitiesByCIdWithoutCache("IOE_C"))
                .willReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("DEV-001")
                                        .cTp("IOE_CPIT")
                                        .cdvaDes("단말기")
                                        .build(),
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("MACH-001")
                                        .cTp("IOE_CPIT")
                                        .cdvaDes("기계장치")
                                        .build(),
                                Ccodem.builder()
                                        .cId("IOE_C")
                                        .cdva("INTAN-001")
                                        .cTp("IOE_CPIT")
                                        .cdvaDes("기타무형자산")
                                        .build()));
        given(ccodemRepository.findByCIdWithValidDate(eq("IOE_C"), any())).willReturn(List.of());

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then
        assertThat(result.getAssetBg()).isEqualByComparingTo("600");
        assertThat(result.getDvcBg()).isEqualByComparingTo("100");
        assertThat(result.getHwBg()).isEqualByComparingTo("200");
    }

    // ───────────────────────────────────────────────────────
    // searchProjectList — 빈 결과
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchProjectList: 조건에 맞는 프로젝트가 없으면 빈 목록을 반환한다")
    void searchProjectList_조건불일치_빈목록() {
        // given
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        given(projectRepository.searchByCondition(condition)).willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.searchProjectList(condition);

        // then
        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // updateProject — items null (품목 동기화 건너뜀)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: items가 null이면 품목 CUD 동기화를 건너뛴다")
    void updateProject_itemsNull_품목동기화건너뜀() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        // 금액 스냅샷 재계산(applyAmountSnapshot)은 items 유무와 무관하게 항상 수행된다
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정 사업명")
                        .items(null) // null → 품목 CUD 동기화는 건너뜀
                        .build();

        // when
        String result = projectService.updateProject(prjMngNo, request);

        // then: 품목 CUD(추가/수정/삭제)는 발생하지 않지만, 금액 스냅샷 재계산을 위한 조회는 1회 수행된다
        assertThat(result).isEqualTo(prjMngNo);
        verify(bitemmRepository, times(1)).findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N");
        verify(bitemmRepository, never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("updateProject: 재신청 초안 순번을 지정하면 최종본이 아닌 해당 초안의 품목만 동기화한다")
    void updateProject_재신청초안순번_초안품목만동기화() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm draft = Bprojm.builder().abusMngNo(prjMngNo).sno(2).delYn("N").lstYn("N").build();
        given(projectRepository.findByAbusMngNoAndSnoAndDelYn(prjMngNo, 2, "N"))
                .willReturn(Optional.of(draft));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(2), anyList()))
                .willReturn(false);
        Bitemm draftItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0002")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .fntTbCrySno(2)
                        .curC("KRW")
                        .amt(new BigDecimal("100"))
                        .mplAmt(BigDecimal.ZERO)
                        .lstYn("Y")
                        .delYn("N")
                        .build();
        given(bitemmRepository.findAllByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 2, "N"))
                .willReturn(List.of(draftItem));

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("재신청 수정")
                        .items(
                                List.of(
                                        ProjectDto.BitemmDto.builder()
                                                .gclMngNo("GCL-2026-0002")
                                                .curC("KRW")
                                                .amt(new BigDecimal("250"))
                                                .mplAmt(BigDecimal.ZERO)
                                                .build()))
                        .build();

        // when
        projectService.updateProject(prjMngNo, 2, request);

        // then
        verify(projectRepository).findByAbusMngNoAndSnoAndDelYn(prjMngNo, 2, "N");
        verify(projectRepository, never()).findByAbusMngNoAndDelYn(prjMngNo, "N");
        verify(bitemmRepository, times(2))
                .findAllByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 2, "N");
        verify(bitemmRepository, never()).findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N");
        assertThat(draftItem.getAmt()).isEqualByComparingTo("250");
        assertThat(draft.getTotRqmAmt()).isEqualByComparingTo("250");
    }

    // ───────────────────────────────────────────────────────
    // updateProject — 요청 품목의 gclMngNo가 기존에 없는 경우
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: 요청 품목의 gclMngNo가 기존에 없는 값이면 기존 품목이 soft-delete된다")
    void updateProject_요청품목_기존없는gclMngNo_무시() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-001")
                        .gclNm("기존품목")
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        // 존재하지 않는 gclMngNo로 수정 요청 (existingItems에 없음)
        ProjectDto.BitemmDto notFoundItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-NOTEXIST")
                        .ioeC("IOE-002")
                        .gclNm("없는품목")
                        .amt(BigDecimal.valueOf(100))
                        .build();

        // when
        projectService.updateProject(
                prjMngNo,
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정")
                        .items(List.of(notFoundItem))
                        .build());

        // then: processedGclMngNos에 없는 existingItem은 soft-delete
        assertThat(existingItem.getDelYn()).isEqualTo("Y");
    }

    // ───────────────────────────────────────────────────────
    // createProject — XSS 새니타이징
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: XSS 스크립트가 포함된 prjDes/prjRng는 새니타이징된다")
    void createProject_xss새니타이징() {
        // given
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("XSS 테스트 사업")
                        .bseYy("2026")
                        .abusCone("<script>alert('xss')</script><p>설명</p>")
                        .abusRngCone("<script>alert('xss2')</script><b>범위</b>")
                        .build();
        given(projectRepository.getNextSequenceValue()).willReturn(10L);

        // when
        String result = projectService.createProject(request);

        // then: script 태그 제거됨
        assertThat(result).matches("PRJ-2026-\\d{4}");
        assertThat(request.getAbusCone()).doesNotContain("<script>");
        assertThat(request.getAbusRngCone()).doesNotContain("<script>");
    }

    // ───────────────────────────────────────────────────────
    // updateProject — XSS 새니타이징
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: XSS 스크립트가 포함된 prjDes/prjRng는 수정 시 새니타이징된다")
    void updateProject_xss새니타이징() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("XSS 수정 테스트")
                        .abusCone("<script>alert('xss')</script><p>설명</p>")
                        .abusRngCone("<script>alert('xss2')</script><b>범위</b>")
                        .build();

        // when
        String result = projectService.updateProject(prjMngNo, request);

        // then
        assertThat(result).isEqualTo(prjMngNo);
        assertThat(request.getAbusCone()).doesNotContain("<script>");
        assertThat(request.getAbusRngCone()).doesNotContain("<script>");
    }

    // ───────────────────────────────────────────────────────
    // getProject — setCodeNames 코드명 필드 전체 분기 커버
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProject: prjTp/bzDtt/tchnTp/mnUsr/rprSts/prjPulPtt/pulDtt 코드명이 설정된다")
    void getProject_setCodeNames_모든코드명필드설정() {
        // given: 모든 코드명 필드가 설정된 프로젝트
        String prjMngNo = "PRJ-2026-CODE";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        // 사업유형/업무구분/기술분야/고객유형은 컬럼에 코드값명을 직접 저장
                        .bzTpC("신규개발")
                        .bzDttNm("금융")
                        .sklTpTc("AI")
                        .cstTpTc("직접관리")
                        .rprStsTc("RS01")
                        .exePttYn("PP01")
                        .abusTc("PD01")
                        .dvmDpmC("101")
                        .svnDpmC("102")
                        .dvmUsid("10001")
                        .dvmTlrUsid("10002")
                        .usid("10003")
                        .tlrUsid("10004")
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        // 부서명/사용자명
        given(corgnIRepository.findNameViewByPrlmOgzCCone("101"))
                .willReturn(Optional.of(new OrgNameView("101", "IT부")));
        given(corgnIRepository.findNameViewByPrlmOgzCCone("102"))
                .willReturn(Optional.of(new OrgNameView("102", "현업부")));
        given(cuserIRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "담당자1")));
        given(cuserIRepository.findNameViewByEno("10002"))
                .willReturn(Optional.of(new NameView("10002", "팀장1")));
        given(cuserIRepository.findNameViewByEno("10003"))
                .willReturn(Optional.of(new NameView("10003", "담당자2")));
        given(cuserIRepository.findNameViewByEno("10004"))
                .willReturn(Optional.of(new NameView("10004", "팀장2")));

        // 공통코드 코드명 설정 (사업유형/업무구분/기술분야/고객유형은 컬럼값=명이므로 해석 불필요)
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("IT_PTL_RPR_STS_TC", "RS01", null))
                .willReturn(
                        Optional.of(
                                Ccodem.builder()
                                        .cId("IT_PTL_RPR_STS_TC")
                                        .cdva("RS01")
                                        .cdvaNm("검토중")
                                        .build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("EXE_PTT_YN", "PP01", null))
                .willReturn(
                        Optional.of(
                                Ccodem.builder()
                                        .cId("EXE_PTT_YN")
                                        .cdva("PP01")
                                        .cdvaNm("정규")
                                        .build()));
        given(ccodemRepository.findByCIdAndCdvaWithValidDate("ABUS_TC", "PD01", null))
                .willReturn(
                        Optional.of(
                                Ccodem.builder().cId("ABUS_TC").cdva("PD01").cdvaNm("연초").build()));

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then: 코드명 필드 확인
        assertThat(result.getBzTpCNm()).isEqualTo("신규개발");
        assertThat(result.getBzDttNmNm()).isEqualTo("금융");
        assertThat(result.getSklTpTcNm()).isEqualTo("AI");
        assertThat(result.getDvmDpmCNm()).isEqualTo("IT부");
        assertThat(result.getSvnDpmCNm()).isEqualTo("현업부");
        assertThat(result.getDvmUsidNm()).isEqualTo("담당자1");
    }

    @Test
    @DisplayName("getProject: 코드값이 null이면 코드명 조회를 건너뛴다")
    void getProject_setCodeNames_코드값null_건너뜀() {
        // given: 코드명 필드 모두 null
        String prjMngNo = "PRJ-2026-NULLCODES";
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .bzTpC(null)
                        .bzDttNm(null)
                        .sklTpTc(null)
                        .cstTpTc(null)
                        .rprStsTc(null)
                        .exePttYn(null)
                        .abusTc(null)
                        .dvmDpmC(null)
                        .svnDpmC(null)
                        .dvmUsid(null)
                        .dvmTlrUsid(null)
                        .usid(null)
                        .tlrUsid(null)
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                anyString(), eq(prjMngNo), eq(1)))
                .willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of());
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        // when
        ProjectDto.Response result = projectService.getProject(prjMngNo);

        // then: ccodemRepository 미호출
        assertThat(result).isNotNull();
        org.mockito.Mockito.verify(ccodemRepository, org.mockito.Mockito.never())
                .findByCIdAndCdvaWithValidDate(any(), any(), any());
    }

    // ───────────────────────────────────────────────────────
    // setBudgetSummary — items가 이미 설정된 경우 (목록 조회 분기)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectList: 품목이 있는 경우 items DTO를 설정하고 예산 합계를 계산한다")
    void getProjectList_품목있음_예산합계계산() {
        // given: 품목 1건이 있는 프로젝트
        String prjMngNo = "PRJ-2026-ITEM";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm item =
                Bitemm.builder()
                        .ioeC("IOE-001")
                        .amt(BigDecimal.valueOf(500))
                        .xcr(BigDecimal.ONE)
                        .build();

        given(projectRepository.findAllByDelYn("N")).willReturn(List.of(project));
        given(capplaRepository.findByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(anyString(), anyList()))
                .willReturn(List.of());
        given(corgnIRepository.findNameViewsByPrlmOgzCConeIn(anyList())).willReturn(List.of());
        given(cuserIRepository.findNameViewsByEnoIn(anyList())).willReturn(List.of());
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(item));
        // IOE 코드 없음 → assetBg=0, costBg=0
        given(codeService.findCodeEntitiesByCId(anyString())).willReturn(List.of());

        // when
        List<ProjectDto.Response> result = projectService.getProjectList();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getItems()).isNotNull();
    }

    // ───────────────────────────────────────────────────────
    // isItemChanged — 개별 필드별 변경 감지 분기
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProject: ioeC만 변경되면 버저닝이 발생한다")
    void updateProject_ioeC변경_버저닝() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-OLD")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        ProjectDto.BitemmDto changedItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0001")
                        .ioeC("IOE-NEW") // ioeC만 변경
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(BigDecimal.valueOf(100))
                        .build();

        // when
        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(changedItem)).build());

        // then: 변경 감지 → 제자리 수정 (삭제·신규 save 없음)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getIoeC()).isEqualTo("IOE-NEW");
        verify(bitemmRepository, org.mockito.Mockito.never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("updateProject: cur만 변경되면 버저닝이 발생한다")
    void updateProject_cur변경_버저닝() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0002")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("USD") // 기존 USD
                        .xcr(BigDecimal.ONE)
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(BigDecimal.valueOf(100))
                        .fcAmt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        ProjectDto.BitemmDto changedItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0002")
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("EUR") // curC 변경
                        .xcr(BigDecimal.ONE)
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(BigDecimal.valueOf(100))
                        .fcAmt(BigDecimal.valueOf(100))
                        .build();

        given(xcrLookupService.resolveXcr(eq("EUR"), any(java.time.LocalDate.class)))
                .willReturn(BigDecimal.ONE);

        // when
        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(changedItem)).build());

        // then: 변경 감지 → 제자리 수정 (삭제·신규 save 없음)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getCurC()).isEqualTo("EUR");
        verify(bitemmRepository, org.mockito.Mockito.never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("updateProject: xcrBseDt만 변경되면 버저닝이 발생한다")
    void updateProject_xcrBseDt변경_버저닝() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0003")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .xcrBseDt("20260101") // 기존 날짜
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        ProjectDto.BitemmDto changedItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0003")
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .xcrBseDt("20260601") // 날짜 변경
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(BigDecimal.valueOf(100))
                        .build();

        // when
        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(changedItem)).build());

        // then: 변경 감지 → 제자리 수정 (삭제·신규 save 없음)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getXcrBseDt()).isEqualTo("20260601");
        verify(bitemmRepository, org.mockito.Mockito.never()).save(any(Bitemm.class));
    }

    @Test
    @DisplayName("updateProject: bgFdtnCone만 변경되면 버저닝이 발생한다")
    void updateProject_bgFdtnCone변경_버저닝() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0004")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .cncdFdtnCone("기존근거")
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .amt(BigDecimal.valueOf(100))
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));

        ProjectDto.BitemmDto changedItem =
                ProjectDto.BitemmDto.builder()
                        .gclMngNo("GCL-0004")
                        .ioeC("IOE-001")
                        .gclNm("동일")
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .xcr(BigDecimal.ONE)
                        .cncdFdtnCone("변경근거") // bgFdtnCone 변경
                        .sectSysUtzYn(null)
                        .itrInfrYn(null)
                        .amt(BigDecimal.valueOf(100))
                        .build();

        // when
        projectService.updateProject(
                prjMngNo, ProjectDto.UpdateRequest.builder().items(List.of(changedItem)).build());

        // then: 변경 감지 → 제자리 수정 (삭제·신규 save 없음)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getCncdFdtnCone()).isEqualTo("변경근거");
        verify(bitemmRepository, org.mockito.Mockito.never()).save(any(Bitemm.class));
    }

    // ───────────────────────────────────────────────────────
    // createProject — 품목 여러 건 저장
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 품목 여러 건이 포함되면 각각 save 호출된다")
    void createProject_품목여러건_모두저장() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(5L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L).willReturn(2L);

        ProjectDto.BitemmDto item1 = new ProjectDto.BitemmDto();
        item1.setIoeC("IOE-001");
        item1.setGclNm("품목1");
        item1.setAmt(BigDecimal.valueOf(100_000));

        ProjectDto.BitemmDto item2 = new ProjectDto.BitemmDto();
        item2.setIoeC("IOE-002");
        item2.setGclNm("품목2");
        item2.setAmt(BigDecimal.valueOf(200_000));

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("다중품목 사업")
                        .bseYy("2026")
                        .items(List.of(item1, item2))
                        .build();

        // when
        String result = projectService.createProject(request);

        // then: 프로젝트 1회 + 품목 2회 save
        assertThat(result).matches("PRJ-2026-\\d{4}");
        org.mockito.Mockito.verify(projectRepository).save(any(Bprojm.class));
        org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.times(2))
                .save(any(Bitemm.class));
    }

    // ───────────────────────────────────────────────────────
    // plan 03-04: 외화 서버 재계산 (Bitemm) — 신규 3건
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createProject: 외화 품목 입력 시 gclAmt = fcAmt × xcr로 서버 재계산되어 저장된다")
    void createProject_외화품목_gclAmt_서버재계산() {
        // given
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("외화 라이선스");
        item.setCurC("USD");
        item.setFcAmt(new BigDecimal("500.000"));
        item.setXcr(new BigDecimal("1300.0000"));
        item.setAmt(new BigDecimal("999")); // 클라 위조 — 무시되어야 함

        // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다 (CONTEXT.md 결정 E)
        given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                .willReturn(new BigDecimal("1300.0000"));

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("외화 품목 사업")
                        .bseYy("2026")
                        .items(List.of(item))
                        .build();

        // when
        projectService.createProject(request);

        // then: 저장된 Bitemm 캡처 — 서버 재계산값 검증
        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        org.mockito.Mockito.verify(bitemmRepository).save(captor.capture());
        assertThat(captor.getValue().getAmt())
                .as("서버 재계산: 500.000 × 1300.0000 = 650000.0000")
                .isEqualByComparingTo(new BigDecimal("650000.0000"));
        assertThat(captor.getValue().getFcAmt()).isEqualByComparingTo(new BigDecimal("500.000"));
    }

    @Test
    @DisplayName("createProject: 원화(KRW) 품목 입력 시 fcAmt=null로 강제되고 gclAmt는 클라값 그대로 저장된다")
    void createProject_원화품목_fcAmt_null_저장() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC("IOE-237-0700");
        item.setGclNm("원화 소프트웨어");
        item.setCurC("KRW");
        item.setAmt(new BigDecimal("1000000"));
        item.setFcAmt(null);
        item.setXcr(null);

        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusNm("원화 품목 사업")
                        .bseYy("2026")
                        .items(List.of(item))
                        .build();

        projectService.createProject(request);

        ArgumentCaptor<Bitemm> captor = ArgumentCaptor.forClass(Bitemm.class);
        org.mockito.Mockito.verify(bitemmRepository).save(captor.capture());
        assertThat(captor.getValue().getFcAmt()).isNull();
        assertThat(captor.getValue().getAmt()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName(
            "updateProject: 기존 품목의 fcAmt만 변경되어도 isItemChanged가 true로 판정되어 제자리 수정된다 (null-safe 포함)")
    void updateProject_fcAmt만변경_변경검출_제자리수정() {
        // given
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();

        // 기존 품목: 외화 USD 500.000 (fcAmt 있음)
        Bitemm existingItem =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .sno(1)
                        .ioeC("IOE-237-0700")
                        .gclNm("외화 라이선스")
                        .curC("USD")
                        .fcAmt(new BigDecimal("500.000"))
                        .xcr(new BigDecimal("1300.0000"))
                        .amt(new BigDecimal("650000.000"))
                        .sectSysUtzYn("N")
                        .itrInfrYn("N")
                        .delYn("N")
                        .lstYn("Y")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(existingItem));
        given(codeService.findCodeEntitiesByCId(any())).willReturn(List.of());

        // 동일 품목, fcAmt만 500.000 → 600.000으로 변경 (xcr 동일)
        ProjectDto.BitemmDto changed = new ProjectDto.BitemmDto();
        changed.setGclMngNo("GCL-2026-0001"); // 기존 식별자
        changed.setIoeC("IOE-237-0700");
        changed.setGclNm("외화 라이선스");
        changed.setCurC("USD");
        changed.setFcAmt(new BigDecimal("600.000")); // 변경
        changed.setXcr(new BigDecimal("1300.0000"));
        changed.setAmt(new BigDecimal("650000.000")); // 클라가 동일하게 보냄 — 서버 재계산

        // Wave 5: 서버가 Ccodem 환율로 클라 xcr를 덮어쓴다 (CONTEXT.md 결정 E)
        given(xcrLookupService.resolveXcr(eq("USD"), any(java.time.LocalDate.class)))
                .willReturn(new BigDecimal("1300.0000"));

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("외화 품목 사업")
                        .bseYy("2026")
                        .items(List.of(changed))
                        .build();

        // when
        projectService.updateProject(prjMngNo, request);

        // then: isItemChanged → true 판정되어 기존 레코드를 제자리 수정 (신규 save 없이 서버 재계산 반영)
        assertThat(existingItem.getDelYn()).isEqualTo("N");
        assertThat(existingItem.getFcAmt()).isEqualByComparingTo(new BigDecimal("600.000"));
        assertThat(existingItem.getAmt())
                .as("서버 재계산: 600.000 × 1300.0000 = 780000.0000")
                .isEqualByComparingTo(new BigDecimal("780000.0000"));
        org.mockito.Mockito.verify(bitemmRepository, org.mockito.Mockito.never())
                .save(any(Bitemm.class));
    }

    // ───────────────────────────────────────────────────────
    // applyAmountSnapshot — 저장 시 금액 스냅샷 기록 및 지급금액(dfrAmt) 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("생성 시 중앙 계산기로 현재·예정·지급 금액을 합산한 스냅샷을 기록한다")
    void createProject_recordsAmountSnapshot() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L, 2L);
        stubActiveItems(
                Bitemm.builder()
                        .ioeC("A01")
                        .curC("KRW")
                        .amt(new BigDecimal("100"))
                        .mplAmt(new BigDecimal("500"))
                        .build(),
                Bitemm.builder()
                        .ioeC("B01")
                        .curC("USD")
                        .amt(new BigDecimal("140000"))
                        .mplAmt(new BigDecimal("50"))
                        .xcr(new BigDecimal("1400"))
                        .build());

        ProjectDto.CreateRequest request = validCreateRequest();
        request.setDfrAmt(new BigDecimal("20"));
        request.setItems(
                List.of(
                        itemDto("A01", new BigDecimal("200"), new BigDecimal("50")),
                        itemDto("B01", new BigDecimal("100"), new BigDecimal("50"))));

        projectService.createProject(request);

        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository, atLeastOnce()).save(captor.capture());
        Bprojm saved = captor.getValue();
        assertThat(saved.getTotRqmAmt()).isEqualByComparingTo("210620");
        assertThat(saved.getMplAmt()).isEqualByComparingTo("70500");
        assertThat(saved.getDfrAmt()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("지급금액이 음수면 400으로 거부한다")
    void createProject_rejectsNegativeDfrAmt() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        stubActiveItems(
                Bitemm.builder()
                        .ioeC("A01")
                        .amt(new BigDecimal("1000"))
                        .mplAmt(BigDecimal.ZERO)
                        .build());

        ProjectDto.CreateRequest request = validCreateRequest();
        request.setDfrAmt(new BigDecimal("-1"));
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        assertThatThrownBy(() -> projectService.createProject(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 이상");
    }

    @Test
    @DisplayName("기 지급금액은 현재 요청금액보다 커도 총소요금액에 합산해 저장한다")
    void createProject_allowsPaidAmountGreaterThanCurrentRequest() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        stubActiveItems(
                Bitemm.builder()
                        .ioeC("A01")
                        .amt(new BigDecimal("1000"))
                        .mplAmt(BigDecimal.ZERO)
                        .build());

        ProjectDto.CreateRequest request = validCreateRequest();
        request.setDfrAmt(new BigDecimal("1001"));
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        assertThatCode(() -> projectService.createProject(request)).doesNotThrowAnyException();

        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getTotRqmAmt()).isEqualByComparingTo("2001");
    }

    @Test
    @DisplayName("지급금액이 현재 요청금액과 같아도 총소요금액에 합산한다")
    void createProject_includesPaidAmountEqualToCurrentRequest() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        stubActiveItems(
                Bitemm.builder()
                        .ioeC("A01")
                        .amt(new BigDecimal("1000"))
                        .mplAmt(BigDecimal.ZERO)
                        .build());

        ProjectDto.CreateRequest request = validCreateRequest();
        request.setDfrAmt(new BigDecimal("1000"));
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        projectService.createProject(request);

        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getTotRqmAmt()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("지급금액 미전송(null)은 0으로 저장한다")
    void createProject_nullDfrAmtBecomesZero() {
        given(projectRepository.getNextSequenceValue()).willReturn(1L);
        given(projectRepository.save(any(Bprojm.class))).willAnswer(inv -> inv.getArgument(0));
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        stubActiveItems(
                Bitemm.builder()
                        .ioeC("A01")
                        .amt(new BigDecimal("1000"))
                        .mplAmt(BigDecimal.ZERO)
                        .build());

        ProjectDto.CreateRequest request = validCreateRequest();
        request.setDfrAmt(null);
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        projectService.createProject(request);

        ArgumentCaptor<Bprojm> captor = ArgumentCaptor.forClass(Bprojm.class);
        verify(projectRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getDfrAmt()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("updateProject: 품목 동기화 후 재조회 합계로 금액 스냅샷을 기록한다 (soft-delete된 품목은 합계에서 빠진다)")
    void updateProject_recordsAmountSnapshot_afterItemSync() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        Bitemm keptItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0001")
                        .sno(1)
                        .abusMngNo(prjMngNo)
                        .fntTbCrySno(1)
                        .ioeC("A01")
                        .gclNm("유지 품목")
                        .curC("KRW")
                        .amt(new BigDecimal("500"))
                        .mplAmt(new BigDecimal("100"))
                        .delYn("N")
                        .build();
        Bitemm removedItem =
                Bitemm.builder()
                        .gclMngNo("GCL-0002")
                        .sno(2)
                        .abusMngNo(prjMngNo)
                        .fntTbCrySno(1)
                        .ioeC("B01")
                        .gclNm("삭제 품목")
                        .curC("KRW")
                        .amt(new BigDecimal("300"))
                        .mplAmt(BigDecimal.ZERO)
                        .delYn("N")
                        .build();

        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        // 1차 호출(품목 동기화 시작 시 기존 목록 조회)은 두 품목 모두 반환하고, 2차 호출
        // (applyAmountSnapshot의 재조회)은 removedItem이 soft-delete되어 실제 DB라면 DEL_YN='N'
        // 필터에서 빠졌을 상태를 흉내 낸다. 이 재조회 설계가 없었다면(=CUD 루프 중 누적) 300이 계속
        // 합계에 남으므로, 최종 합계가 500(keptItem만)인지로 재조회 반영 여부를 검증한다.
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(List.of(keptItem, removedItem), List.of(keptItem));

        ProjectDto.BitemmDto keptDto = new ProjectDto.BitemmDto();
        keptDto.setGclMngNo("GCL-0001");
        keptDto.setIoeC("A01");
        keptDto.setGclNm("유지 품목");
        keptDto.setCurC("KRW");
        keptDto.setAmt(new BigDecimal("500"));
        keptDto.setMplAmt(new BigDecimal("100"));

        // 요청 품목에 keptItem만 포함 → removedItem은 요청에서 빠져 soft-delete 대상이 된다
        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정 사업명")
                        .dfrAmt(new BigDecimal("200"))
                        .items(List.of(keptDto))
                        .build();

        projectService.updateProject(prjMngNo, request);

        assertThat(removedItem.getDelYn()).isEqualTo("Y");
        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("800");
        assertThat(project.getMplAmt()).isEqualByComparingTo("100");
        assertThat(project.getDfrAmt()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("updateProject: 지급금액 상한 없이 총소요금액에 합산한다")
    void updateProject_includesPaidAmountWithoutLegacyCeiling() {
        String prjMngNo = "PRJ-2026-0001";
        Bprojm project = Bprojm.builder().abusMngNo(prjMngNo).sno(1).delYn("N").build();
        given(projectRepository.findByAbusMngNoAndDelYn(prjMngNo, "N"))
                .willReturn(Optional.of(project));
        given(
                        capplaRepository.existsByFntTbNmAndPkColNmAndFntTbCrySnoAndApfStsIn(
                                eq("BPROJM"), eq(prjMngNo), eq(1), anyList()))
                .willReturn(false);
        // items=null이어도 스냅샷 재계산은 항상 수행되어 재조회 현재 요청금액에 지급금액을 합산한다.
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, 1, "N"))
                .willReturn(
                        List.of(
                                Bitemm.builder()
                                        .ioeC("A01")
                                        .amt(new BigDecimal("1000"))
                                        .mplAmt(BigDecimal.ZERO)
                                        .build()));

        ProjectDto.UpdateRequest request =
                ProjectDto.UpdateRequest.builder()
                        .abusNm("수정 사업명")
                        .dfrAmt(new BigDecimal("1001"))
                        .build();

        assertThatCode(() -> projectService.updateProject(prjMngNo, request))
                .doesNotThrowAnyException();
        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("2001");
    }

    // ───────────────────────────────────────────────────────
    // assignDeclaredAmounts — 요청서가 선언한 전체기간·예정·지급 금액 보존
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("요청서의 전체기간·예정·지급 금액을 그대로 스냅샷에 기록한다")
    void assignDeclaredAmounts_keepsDeclaredSnapshot() {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").sno(1).build();
        Bitemm item =
                Bitemm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .fntTbCrySno(1)
                        .curC("KRW")
                        .amt(new BigDecimal("100"))
                        .mplAmt(new BigDecimal("300"))
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of(item));

        projectService.assignDeclaredAmounts(
                "PRJ-2026-0001",
                new BigDecimal("999"),
                new BigDecimal("888"),
                new BigDecimal("20"));

        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("999");
        assertThat(project.getMplAmt()).isEqualByComparingTo("888");
        assertThat(project.getDfrAmt()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("구 이관 경로도 음수 DFR을 거부하고 기존 금액 스냅샷을 유지한다")
    void assignDeclaredAmounts_rejectsNegativePaidAmountWithoutChangingSnapshot() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .totRqmAmt(new BigDecimal("420.000"))
                        .mplAmt(new BigDecimal("300.000"))
                        .dfrAmt(new BigDecimal("20.000"))
                        .build();
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));

        assertThatThrownBy(
                        () ->
                                projectService.assignDeclaredAmounts(
                                        "PRJ-2026-0001",
                                        new BigDecimal("999"),
                                        new BigDecimal("888"),
                                        new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지급금액은 0 이상이어야 합니다.");
        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("420.000");
        assertThat(project.getMplAmt()).isEqualByComparingTo("300.000");
        assertThat(project.getDfrAmt()).isEqualByComparingTo("20.000");
    }

    @Test
    @DisplayName("이관 경로가 사업을 못 찾으면 실패한다")
    void assignDeclaredAmounts_failsWhenProjectMissing() {
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-9999", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                projectService.assignDeclaredAmounts(
                                        "PRJ-2026-9999",
                                        BigDecimal.ONE,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRJ-2026-9999");
    }

    @Test
    @DisplayName("기존 지급금액은 품목 현재 요청금액과 비교하지 않고 다시 저장할 수 있다")
    void updateProject_allowsResavingPaidAmount() {
        Bprojm project = existingProjectWithDfrAmt(new BigDecimal("734375300"));
        ProjectDto.UpdateRequest request = validUpdateRequest();
        request.setDfrAmt(new BigDecimal("734375300"));
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        assertThatCode(() -> projectService.updateProject(project.getAbusMngNo(), request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기존 지급금액보다 늘리는 수정도 총소요금액에 합산한다")
    void updateProject_allowsIncreasingPaidAmount() {
        Bprojm project = existingProjectWithDfrAmt(new BigDecimal("734375300"));
        ProjectDto.UpdateRequest request = validUpdateRequest();
        request.setDfrAmt(new BigDecimal("734375301"));
        request.setItems(List.of(itemDto("A01", new BigDecimal("1000"), BigDecimal.ZERO)));

        assertThatCode(() -> projectService.updateProject(project.getAbusMngNo(), request))
                .doesNotThrowAnyException();
        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("734375301");
        assertThat(project.getDfrAmt()).isEqualByComparingTo("734375301");
    }

    /** 필수 필드만 채운 생성 요청. 개별 테스트가 필요한 필드만 덮어쓴다. */
    private ProjectDto.CreateRequest validCreateRequest() {
        ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
        request.setAbusNm("테스트 사업");
        request.setBseYy("2026");
        request.setSvnDpmC("D001");
        request.setDvmDpmC("D002");
        request.setAbusTc("10");
        return request;
    }

    /** 필수 필드만 채운 수정 요청. 개별 테스트가 필요한 필드만 덮어쓴다. */
    private ProjectDto.UpdateRequest validUpdateRequest() {
        ProjectDto.UpdateRequest request = new ProjectDto.UpdateRequest();
        request.setAbusNm("수정 사업명");
        return request;
    }

    /**
     * 이미 지급금액이 기록된 기존 사업을 재현한다.
     *
     * <p>{@code updateProject}의 다른 테스트와 같은 방식으로 {@code projectRepository.findByAbusMngNoAndDelYn}을
     * 스텁해, 이 헬퍼가 반환한 사업을 곧바로 수정 대상으로 조회할 수 있게 한다.
     */
    private Bprojm existingProjectWithDfrAmt(BigDecimal dfrAmt) {
        Bprojm project = Bprojm.builder().abusMngNo("PRJ-2026-0001").build();
        project.assignAmountSnapshot(dfrAmt, BigDecimal.ZERO, dfrAmt);
        given(projectRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(Optional.of(project));
        return project;
    }

    /** 합계 검증용 최소 품목 DTO. */
    private ProjectDto.BitemmDto itemDto(String ioeC, BigDecimal amt, BigDecimal mplAmt) {
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setIoeC(ioeC);
        item.setGclNm("품목-" + ioeC);
        item.setCurC("KRW");
        item.setAmt(amt);
        item.setMplAmt(mplAmt);
        return item;
    }

    /**
     * 활성 품목 재조회({@code bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn}) 응답을 스텁한다.
     *
     * <p>사업관리번호·순번은 broad matcher로 받아, 자동 채번되어 테스트에서 값을 예측할 수 없는 {@code createProject} 경로에서도 재사용할 수
     * 있게 한다.
     *
     * @param items 재조회가 반환할 활성 품목 목록
     */
    private void stubActiveItems(Bitemm... items) {
        given(
                        bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(
                                anyString(), any(), anyString()))
                .willReturn(List.of(items));
    }
}
