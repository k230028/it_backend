package com.kdb.it.domain.budget.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.budget.project.service.ProjectService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * PlanService 단위 테스트
 *
 * <p>BplanmRepository, BplanaRepository, ProjectService를 Mock 처리하여 Oracle DB 없이 정보기술부문 계획 생성·조회·삭제
 * 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private record PlanListViewRow(
            String reqDocNo,
            String itPtlPlnTpC,
            String bseYy,
            BigDecimal aduTotAmt,
            BigDecimal cpitBgApvAmt,
            BigDecimal totXpAmt,
            java.time.LocalDateTime fstEnrDtm,
            String fstEnrUsid,
            String redtConeInf)
            implements BplanmRepository.PlanListView {
        @Override
        public String getReqDocNo() {
            return reqDocNo;
        }

        @Override
        public String getItPtlPlnTpC() {
            return itPtlPlnTpC;
        }

        @Override
        public String getBseYy() {
            return bseYy;
        }

        @Override
        public BigDecimal getAduTotAmt() {
            return aduTotAmt;
        }

        @Override
        public BigDecimal getCpitBgApvAmt() {
            return cpitBgApvAmt;
        }

        @Override
        public BigDecimal getTotXpAmt() {
            return totXpAmt;
        }

        @Override
        public java.time.LocalDateTime getFstEnrDtm() {
            return fstEnrDtm;
        }

        @Override
        public String getFstEnrUsid() {
            return fstEnrUsid;
        }

        @Override
        public String getRedtConeInf() {
            return redtConeInf;
        }
    }

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

    @Mock private BplanmRepository bplanmRepository;
    @Mock private BplanaRepository bplanaRepository;
    @Mock private ProjectService projectService;
    @Mock private CostService costService;
    @Mock private ProjectRepository projectRepository;
    @Mock private CodeService codeService;
    @Mock private UserRepository cuserIRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private BprojaSyncService bprojaSyncService;

    @InjectMocks private PlanService planService;

    // =========================================================================
    // getPlans
    // =========================================================================

    @Test
    @DisplayName("getPlans - 삭제되지 않은 계획 목록을 반환한다")
    void getPlans_목록반환() {
        // given
        BplanmRepository.PlanListView plan =
                new PlanListViewRow(
                        "PLN-2026-0001", "신규", "2026", null, null, null, null, "USER001", null);
        given(bplanmRepository.findListViewsByDelYnOrderByFstEnrDtmDesc("N"))
                .willReturn(List.of(plan));
        given(cuserIRepository.findNameViewsByEnoIn(List.of("USER001")))
                .willReturn(List.of(new NameView("USER001", "홍길동")));
        given(codeService.findCodeEntitiesByCId("ABUS_TC")).willReturn(List.of());

        // when
        List<PlanDto.ListResponse> result = planService.getPlans();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReqDocNo()).isEqualTo("PLN-2026-0001");
    }

    @Test
    @DisplayName("getPlans - 계획이 없으면 빈 목록을 반환한다")
    void getPlans_빈목록반환() {
        // given
        given(bplanmRepository.findListViewsByDelYnOrderByFstEnrDtmDesc("N")).willReturn(List.of());

        // when
        List<PlanDto.ListResponse> result = planService.getPlans();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPlans - 스냅샷 사업유형으로 신규와 계속 건수를 계산한다")
    void getPlans_스냅샷사업유형_건수계산() {
        PlanService service =
                new PlanService(
                        bplanmRepository,
                        bplanaRepository,
                        projectService,
                        costService,
                        codeService,
                        cuserIRepository,
                        new ObjectMapper(),
                        bprojaSyncService);
        BplanmRepository.PlanListView plan =
                new PlanListViewRow(
                        "PLN-2026-0002",
                        "신규",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "USER002",
                        "{\"prjSnapshots\":[{\"id\":1,\"pulDtt\":\"001\"},{\"id\":2,\"pulDtt\":\"002\"},{\"id\":3,\"pulDtt\":\"001\"}]}");
        given(bplanmRepository.findListViewsByDelYnOrderByFstEnrDtmDesc("N"))
                .willReturn(List.of(plan));
        given(cuserIRepository.findNameViewsByEnoIn(List.of("USER002"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("ABUS_TC"))
                .willReturn(
                        List.of(
                                Ccodem.builder().cdva("10").cdvaNm("신규").build(),
                                Ccodem.builder().cdva("20").cdvaNm("계속").build()));

        List<PlanDto.ListResponse> result = service.getPlans();

        assertThat(result.get(0).getItPrjCnt()).isEqualTo(3);
        assertThat(result.get(0).getNewPrjCnt()).isEqualTo(2);
        assertThat(result.get(0).getContPrjCnt()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPlans - 손상된 스냅샷은 사업 건수를 0으로 유지한다")
    void getPlans_손상된스냅샷_건수0유지() {
        PlanService service =
                new PlanService(
                        bplanmRepository,
                        bplanaRepository,
                        projectService,
                        costService,
                        codeService,
                        cuserIRepository,
                        new ObjectMapper(),
                        bprojaSyncService);
        BplanmRepository.PlanListView plan =
                new PlanListViewRow(
                        "PLN-2026-0003", null, null, null, null, null, null, "USER003", "{");
        given(bplanmRepository.findListViewsByDelYnOrderByFstEnrDtmDesc("N"))
                .willReturn(List.of(plan));
        given(cuserIRepository.findNameViewsByEnoIn(List.of("USER003"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("ABUS_TC")).willReturn(List.of());

        List<PlanDto.ListResponse> result = service.getPlans();

        assertThat(result.get(0).getItPrjCnt()).isZero();
    }

    // =========================================================================
    // getPlan
    // =========================================================================

    @Test
    @DisplayName("getPlan - 존재하는 계획관리번호 조회 시 DetailResponse 반환")
    void getPlan_존재하는번호_반환() {
        // given
        String reqDocNo = "PLN-2026-0001";
        Bplanm plan =
                Bplanm.builder()
                        .reqDocNo(reqDocNo)
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .aduTotAmt(BigDecimal.valueOf(100000000))
                        .build();

        given(bplanmRepository.findByReqDocNoAndDelYn(reqDocNo, "N")).willReturn(Optional.of(plan));
        given(bplanaRepository.findAllByReqDocNoAndDelYn(reqDocNo, "N")).willReturn(List.of());

        // when
        PlanDto.DetailResponse result = planService.getPlan(reqDocNo);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getReqDocNo()).isEqualTo(reqDocNo);
    }

    @Test
    @DisplayName("getPlan - 미존재 계획관리번호 조회 시 ResponseStatusException(404) 발생")
    void getPlan_미존재번호_404예외발생() {
        // given
        given(bplanmRepository.findByReqDocNoAndDelYn("INVALID", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> planService.getPlan("INVALID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("존재하지 않는 계획입니다");
    }

    @Test
    @DisplayName("getPlan - 일반 사용자는 서비스 계층에서 거부한다")
    void getPlan_일반사용자_서비스계층에서거부() {
        CustomUserDetails user =
                new CustomUserDetails(
                        "USER", List.of(CustomUserDetails.ATH_USER), "D100");

        assertThatThrownBy(() -> planService.getPlan("PLN-2026-0001", user))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // =========================================================================
    // createPlan
    // =========================================================================

    @Test
    @DisplayName("createPlan - prjMngNos가 비어있으면 ResponseStatusException(400) 발생")
    void createPlan_빈프로젝트목록_400예외발생() {
        // given: 프로젝트 목록이 빈 요청
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(List.of()) // 빈 목록
                        .build();

        // when & then
        assertThatThrownBy(() -> planService.createPlan(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("대상사업을 1개 이상 선택해야 합니다");
    }

    @Test
    @DisplayName("createPlan - prjMngNos가 null이면 ResponseStatusException(400) 발생")
    void createPlan_null프로젝트목록_400예외발생() {
        // given
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(null)
                        .build();

        // when & then
        assertThatThrownBy(() -> planService.createPlan(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("대상사업을 1개 이상 선택해야 합니다");
    }

    @Test
    @DisplayName("createPlan - 정상 요청 시 PLN-{year}-{seq} 형식의 계획관리번호 반환")
    void createPlan_정상요청_계획관리번호반환() throws Exception {
        // given
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(List.of("PRJ-2026-0001"))
                        .build();

        ProjectDto.Response mockProject =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .assetBg(BigDecimal.valueOf(30000000))
                        .costBg(BigDecimal.valueOf(20000000))
                        .build();

        given(projectService.getProjectsByIds(any()))
                .willReturn(new ProjectDto.BulkResponse(List.of(mockProject), List.of()));
        given(bplanmRepository.getNextSequenceValue()).willReturn(1L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        String result = planService.createPlan(request);

        // then
        assertThat(result).isEqualTo("PLN-2026-0001");
        verify(bplanmRepository, times(1)).save(any(Bplanm.class));
        // 프로젝트-계획 관계도 저장되어야 함
        verify(bplanaRepository, times(1)).save(any(Bplana.class));
    }

    @Test
    @DisplayName("createPlan - 전산업무비만 선택해도 예산 합계와 관계를 저장한다")
    void createPlan_전산업무비만선택_계획생성() throws Exception {
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .itMngcNos(List.of("COST-2026-0001"))
                        .build();
        CostDto.Response cost =
                CostDto.Response.builder()
                        .costBgNo("COST-2026-0001")
                        .cttNm("전산업무비")
                        .tmnYn("유지보수")
                        .costSvnDpmC("001")
                        .costSvnDpmNm(null)
                        .costTotXpAmt(BigDecimal.valueOf(100))
                        // 합계는 BBUGTM 편성예산(assetDupBg/costDupBg) 기준 — 자본 70 + 일반관리비 30 = 100
                        .assetDupBg(BigDecimal.valueOf(70))
                        .costDupBg(BigDecimal.valueOf(30))
                        .build();
        given(costService.getCostsByIds(any()))
                .willReturn(new CostDto.BulkResponse(List.of(cost), List.of()));
        given(bplanmRepository.getNextSequenceValue()).willReturn(2L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        String result = planService.createPlan(request);

        assertThat(result).isEqualTo("PLN-2026-0002");
        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getAduTotAmt()).isEqualByComparingTo("100");
        assertThat(planCaptor.getValue().getCpitBgApvAmt()).isEqualByComparingTo("70");
        assertThat(planCaptor.getValue().getTotXpAmt()).isEqualByComparingTo("30");
        verify(bplanaRepository).save(any(Bplana.class));
    }

    @Test
    @DisplayName("createPlan - 프로젝트와 전산업무비의 null 예산은 0으로 계산한다")
    void createPlan_null예산_0으로계산() throws Exception {
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("조정")
                        .prjMngNos(List.of("PRJ-2026-0001"))
                        .itMngcNos(List.of("COST-2026-0001"))
                        .build();
        ProjectDto.Response project =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .abusNm("정보화사업")
                        .prlmHrkOgzCCone(null)
                        .bzTpC(null)
                        .assetBg(null)
                        .costBg(null)
                        .build();
        CostDto.Response cost =
                CostDto.Response.builder()
                        .costBgNo("COST-2026-0001")
                        .cttNm("전산업무비")
                        .costSvnDpmNm("IT부")
                        .costTotXpAmt(null)
                        .assetBg(null)
                        .costBg(null)
                        .build();
        given(projectService.getProjectsByIds(any()))
                .willReturn(new ProjectDto.BulkResponse(List.of(project), List.of()));
        given(costService.getCostsByIds(any()))
                .willReturn(new CostDto.BulkResponse(List.of(cost), List.of()));
        given(bplanmRepository.getNextSequenceValue()).willReturn(3L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        planService.createPlan(request);

        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getAduTotAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(planCaptor.getValue().getCpitBgApvAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(planCaptor.getValue().getTotXpAmt()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bplanaRepository, times(2)).save(any(Bplana.class));
    }

    @Test
    @DisplayName("createPlan - 부문별 목록에는 경상사업을 IT기획부 대표 1건으로 합산한다")
    void createPlan_스냅샷그룹목록_경상사업_IT기획부대표건으로합산() throws Exception {
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(List.of("PRJ-GENERAL", "PRJ-ORDINARY"))
                        .itMngcNos(List.of("COST-001"))
                        .build();
        ProjectDto.Response generalProject =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-GENERAL")
                        .abusNm("일반 정보화사업")
                        .bzTpC("개발")
                        .prlmHrkOgzCCone("IT부문")
                        .odnYn("N")
                        .build();
        ProjectDto.Response ordinaryProject =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-ORDINARY")
                        .abusNm("경상사업")
                        .bzTpC("운영")
                        .prlmHrkOgzCCone("IT부문")
                        .tyyBgAmt(BigDecimal.valueOf(300))
                        .assetBg(BigDecimal.valueOf(200))
                        .costBg(BigDecimal.valueOf(100))
                        .odnYn("Y")
                        .build();
        CostDto.Response cost =
                CostDto.Response.builder().costBgNo("COST-001").cttNm("전산업무비").tmnYn("관리비").build();
        given(projectService.getProjectsByIds(any()))
                .willReturn(
                        new ProjectDto.BulkResponse(
                                List.of(generalProject, ordinaryProject), List.of()));
        given(costService.getCostsByIds(any()))
                .willReturn(new CostDto.BulkResponse(List.of(cost), List.of()));
        given(bplanmRepository.getNextSequenceValue()).willReturn(4L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        planService.createPlan(request);

        ArgumentCaptor<PlanDto.SnapshotDto> snapshotCaptor =
                ArgumentCaptor.forClass(PlanDto.SnapshotDto.class);
        verify(objectMapper).writeValueAsString(snapshotCaptor.capture());
        PlanDto.SnapshotDto snapshot = snapshotCaptor.getValue();
        List<String> departmentIds =
                snapshot.getByDepartment().stream()
                        .flatMap(group -> ((List<?>) group.get("projects")).stream())
                        .map(item -> ((PlanDto.ProjectSnapshot) item).getPrjMngNo())
                        .toList();
        List<String> projectTypeIds =
                snapshot.getByProjectType().stream()
                        .flatMap(group -> ((List<?>) group.get("projects")).stream())
                        .map(item -> ((PlanDto.ProjectSnapshot) item).getPrjMngNo())
                        .toList();

        assertThat(departmentIds).containsExactly("PRJ-GENERAL", "__ORDINARY_PROJECT_SUMMARY__");
        assertThat(projectTypeIds).containsExactly("PRJ-GENERAL");
        assertThat(snapshot.getProjects())
                .extracting(project -> project.getPrjMngNo())
                .contains("__ORDINARY_PROJECT_SUMMARY__")
                .doesNotContain("PRJ-ORDINARY");
        PlanDto.ProjectSnapshot ordinarySummary =
                snapshot.getByDepartment().stream()
                        .flatMap(group -> ((List<?>) group.get("projects")).stream())
                        .map(item -> (PlanDto.ProjectSnapshot) item)
                        .filter(item -> "__ORDINARY_PROJECT_SUMMARY__".equals(item.getPrjMngNo()))
                        .findFirst()
                        .orElseThrow();
        assertThat(ordinarySummary.getAbusNm()).isEqualTo("2026년 경상사업 (경상사업 등 1건)");
        assertThat(ordinarySummary.getSvnHdq()).isEqualTo("IT·AI본부");
        assertThat(ordinarySummary.getSvnDpm()).isEqualTo("180");
        assertThat(ordinarySummary.getSvnDpmNm()).isEqualTo("IT기획부");
        assertThat(ordinarySummary.getPulDtt()).isEqualTo("01");
        assertThat(ordinarySummary.getPrjBg()).isEqualByComparingTo("300");
        assertThat(ordinarySummary.getAssetBg()).isEqualByComparingTo("200");
        assertThat(ordinarySummary.getCostBg()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("createPlanForMigration - 대상 사업 여럿의 조정액을 합산해 계획을 만든다")
    void createPlanForMigration_대상사업_조정액을_합산한다() throws Exception {
        ProjectDto.Response project1 =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .abusNm("문자메시지 안심마크 도입")
                        .bzTpC("개발")
                        .abusTc("20")
                        .prlmHrkOgzCCone("IT·AI본부")
                        .svnDpmC("180")
                        .svnDpmCNm("IT기획부")
                        .build();
        ProjectDto.Response project2 =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-2026-0002")
                        .abusNm("정보기술부문계획 조정 대상")
                        .bzTpC("운영")
                        .abusTc("10")
                        .prlmHrkOgzCCone("IT·AI본부")
                        .svnDpmC("181")
                        .svnDpmCNm("IT인프라부")
                        .build();
        given(projectService.getProject("PRJ-2026-0001")).willReturn(project1);
        given(projectService.getProject("PRJ-2026-0002")).willReturn(project2);
        given(bplanmRepository.getNextSequenceValue()).willReturn(9L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        String result =
                planService.createPlanForMigration(
                        "2026",
                        "조정",
                        List.of("PRJ-2026-0001", "PRJ-2026-0002"),
                        // capitalAmounts는 null을 허용하므로(0으로 취급) List.of()가 아니라
                        // 널 허용 리스트가 필요하다.
                        java.util.Arrays.asList(new BigDecimal("1000000"), null),
                        java.util.Arrays.asList(new BigDecimal("300000"), null),
                        Map.of("PRJ-2026-0001", Map.of("사업진행", "진행(품의)")));

        assertThat(result).isEqualTo("PLN-2026-0009");
        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getItPtlPlnTpC()).isEqualTo("조정");
        // null 금액은 0으로 취급되므로 자본 합계는 1,000,000, 일반관리비 합계는 300,000이다.
        assertThat(planCaptor.getValue().getCpitBgApvAmt()).isEqualByComparingTo("1000000");
        // 일반관리비가 TOT_XP_AMT로 실제 반영되고 총액이 자본+일반이어야 한다 (§5.4).
        // 하드코딩 ZERO로 되돌리면 이 두 단정이 깨진다.
        assertThat(planCaptor.getValue().getTotXpAmt()).isEqualByComparingTo("300000");
        assertThat(planCaptor.getValue().getAduTotAmt()).isEqualByComparingTo("1300000");
        verify(bplanaRepository, times(2)).save(any(Bplana.class));
        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PLN-2026-0009", "11");
        verify(bprojaSyncService).upsert("PRJ-2026-0002", "PLN-2026-0009", "11");
    }

    @Test
    @DisplayName("createPlanForMigration - 사업 목록과 조정액 목록의 크기가 다르면 예외를 던진다")
    void createPlanForMigration_크기가_다르면_예외를_던진다() {
        assertThatThrownBy(
                        () ->
                                planService.createPlanForMigration(
                                        "2026",
                                        "조정",
                                        List.of("PRJ-2026-0001", "PRJ-2026-0002"),
                                        List.of(new BigDecimal("1000000")),
                                        List.of(BigDecimal.ZERO, BigDecimal.ZERO),
                                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("크기가 다릅니다");
    }

    @Test
    @DisplayName("createPlan - 스냅샷 직렬화 실패 시 500 예외가 발생한다")
    void createPlan_스냅샷직렬화실패_500예외발생() throws Exception {
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(List.of("PRJ-2026-0001"))
                        .build();
        given(projectService.getProjectsByIds(any()))
                .willReturn(
                        new ProjectDto.BulkResponse(
                                List.of(
                                        ProjectDto.Response.builder()
                                                .abusMngNo("PRJ-2026-0001")
                                                .build()),
                                List.of()));
        given(objectMapper.writeValueAsString(any()))
                .willThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> planService.createPlan(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계획 스냅샷 직렬화");
    }

    @Test
    @DisplayName("createPlan - 계획 예산 합계는 BBUGTM 편성예산(assetDupBg/costDupBg) 기준으로 집계한다")
    void 계획작성_합계는_편성예산기준() throws Exception {
        // given
        // 폼 미리보기와 동일하게 총예산 = 자본 편성액(assetDupBg) + 일반관리비 편성액(costDupBg).
        // 요청/소요 금액(totRqmAmt·assetBg·costBg)을 합산하면 미리보기보다 큰 값이 저장되던 버그를
        // 회귀 방지하기 위해 요청 금액과 편성예산을 명확히 다른 값으로 둔다.
        PlanDto.CreateRequest request =
                PlanDto.CreateRequest.builder()
                        .bseYy("2026")
                        .itPtlPlnTpC("신규")
                        .prjMngNos(List.of("PRJ-2026-DUP-TEST"))
                        .build();

        ProjectDto.Response mockProject =
                ProjectDto.Response.builder()
                        .abusMngNo("PRJ-2026-DUP-TEST")
                        // 요청/소요 금액 — 합산 대상이 아님 (편성예산과 구분되는 값)
                        .tyyBgAmt(BigDecimal.valueOf(1000))
                        .assetBg(BigDecimal.valueOf(800))
                        .costBg(BigDecimal.valueOf(200))
                        // BBUGTM 편성예산 — 실제 합산 대상
                        .assetDupBg(BigDecimal.valueOf(600))
                        .costDupBg(BigDecimal.valueOf(150))
                        .build();

        given(projectService.getProjectsByIds(any()))
                .willReturn(new ProjectDto.BulkResponse(List.of(mockProject), List.of()));
        given(bplanmRepository.getNextSequenceValue()).willReturn(10L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        planService.createPlan(request);

        // then: 편성예산 기준 — 자본 600, 일반관리비 150, 총 750.
        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        Bplanm saved = planCaptor.getValue();
        assertThat(saved.getCpitBgApvAmt()).isEqualByComparingTo(BigDecimal.valueOf(600));
        assertThat(saved.getTotXpAmt()).isEqualByComparingTo(BigDecimal.valueOf(150));
        assertThat(saved.getAduTotAmt())
                .as("총예산은 편성예산 합계(자본 600 + 일반관리비 150 = 750)이어야 한다 — 요청 금액(1000)이면 버그")
                .isEqualByComparingTo(BigDecimal.valueOf(750));
        // 명시적 음성 검증: 요청/소요 금액(totRqmAmt 1000)이 아님
        assertThat(saved.getAduTotAmt()).isNotEqualByComparingTo(BigDecimal.valueOf(1000));
    }

    // =========================================================================
    // deletePlan
    // =========================================================================

    @Test
    @DisplayName("deletePlan - 미존재 계획관리번호 삭제 시 ResponseStatusException(404) 발생")
    void deletePlan_미존재번호_404예외발생() {
        // given
        given(bplanmRepository.findByReqDocNoAndDelYn("INVALID", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> planService.deletePlan("INVALID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("존재하지 않는 계획입니다");
    }

    @Test
    @DisplayName("deletePlan - 정상 삭제 시 plan.delete() 호출 및 관계 레코드도 삭제")
    void deletePlan_정상삭제_SoftDelete() {
        // given
        String reqDocNo = "PLN-2026-0001";
        Bplanm plan = Bplanm.builder().reqDocNo(reqDocNo).build();
        Bplana relation = Bplana.builder().prjMngNo("PRJ-2026-0001").reqDocNo(reqDocNo).build();

        given(bplanmRepository.findByReqDocNoAndDelYn(reqDocNo, "N")).willReturn(Optional.of(plan));
        given(bplanaRepository.findAllByReqDocNoAndDelYn(reqDocNo, "N"))
                .willReturn(List.of(relation));

        // when
        planService.deletePlan(reqDocNo);

        // then: 계획 Soft Delete 확인
        assertThat(plan.getDelYn()).isEqualTo("Y");
        // then: 관계 레코드 Soft Delete 확인
        assertThat(relation.getDelYn()).isEqualTo("Y");
    }
}
