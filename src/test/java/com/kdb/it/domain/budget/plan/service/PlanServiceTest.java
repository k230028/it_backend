package com.kdb.it.domain.budget.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.code.entity.Ccodem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.entity.Bplanm;
import com.kdb.it.domain.budget.plan.entity.Bproja;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.plan.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;

/**
 * PlanService 단위 테스트
 *
 * <p>
 * BplanmRepository, BprojaRepository, ProjectService를 Mock 처리하여
 * Oracle DB 없이 정보기술부문 계획 생성·조회·삭제 로직을 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private BplanmRepository bplanmRepository;
    @Mock
    private BprojaRepository bprojaRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private CostService costService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CodeService codeService;
    @Mock
    private UserRepository cuserIRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PlanService planService;

    // =========================================================================
    // getPlans
    // =========================================================================

    @Test
    @DisplayName("getPlans - 삭제되지 않은 계획 목록을 반환한다")
    void getPlans_목록반환() {
        // given
        Bplanm plan = Bplanm.builder()
                .plnMngNo("PLN-2026-0001")
                .plnYy("2026")
                .plnTp("신규")
                .build();
        ReflectionTestUtils.setField(plan, "fstEnrUsid", "USER001");
        given(bplanmRepository.findAllByDelYnOrderByFstEnrDtmDesc("N")).willReturn(List.of(plan));
        given(cuserIRepository.findAllById(List.of("USER001"))).willReturn(List.of(
                CuserI.builder().eno("USER001").usrNm("홍길동").build()));
        given(codeService.findCodeEntitiesByCId("PUL_DTT")).willReturn(List.of());

        // when
        List<PlanDto.ListResponse> result = planService.getPlans();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlnMngNo()).isEqualTo("PLN-2026-0001");
    }

    @Test
    @DisplayName("getPlans - 계획이 없으면 빈 목록을 반환한다")
    void getPlans_빈목록반환() {
        // given
        given(bplanmRepository.findAllByDelYnOrderByFstEnrDtmDesc("N")).willReturn(List.of());

        // when
        List<PlanDto.ListResponse> result = planService.getPlans();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getPlans - 스냅샷 사업유형으로 신규와 계속 건수를 계산한다")
    void getPlans_스냅샷사업유형_건수계산() {
        PlanService service = new PlanService(
                bplanmRepository, bprojaRepository, projectService, costService,
                codeService, cuserIRepository, new ObjectMapper());
        Bplanm plan = Bplanm.builder()
                .plnMngNo("PLN-2026-0002")
                .plnYy("2026")
                .plnTp("신규")
                .plnDtlInf("{\"prjSnapshots\":[{\"pulDtt\":\"001\"},{\"pulDtt\":\"002\"},{\"pulDtt\":\"001\"}]}")
                .build();
        ReflectionTestUtils.setField(plan, "fstEnrUsid", "USER002");
        given(bplanmRepository.findAllByDelYnOrderByFstEnrDtmDesc("N")).willReturn(List.of(plan));
        given(cuserIRepository.findAllById(List.of("USER002"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("PUL_DTT")).willReturn(List.of(
                Ccodem.builder().cdva("001").cNm("신규").build(),
                Ccodem.builder().cdva("002").cNm("계속").build()));

        List<PlanDto.ListResponse> result = service.getPlans();

        assertThat(result.get(0).getItPrjCnt()).isEqualTo(3);
        assertThat(result.get(0).getNewPrjCnt()).isEqualTo(2);
        assertThat(result.get(0).getContPrjCnt()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPlans - 손상된 스냅샷은 사업 건수를 0으로 유지한다")
    void getPlans_손상된스냅샷_건수0유지() {
        PlanService service = new PlanService(
                bplanmRepository, bprojaRepository, projectService, costService,
                codeService, cuserIRepository, new ObjectMapper());
        Bplanm plan = Bplanm.builder()
                .plnMngNo("PLN-2026-0003")
                .plnDtlInf("{")
                .build();
        ReflectionTestUtils.setField(plan, "fstEnrUsid", "USER003");
        given(bplanmRepository.findAllByDelYnOrderByFstEnrDtmDesc("N")).willReturn(List.of(plan));
        given(cuserIRepository.findAllById(List.of("USER003"))).willReturn(List.of());
        given(codeService.findCodeEntitiesByCId("PUL_DTT")).willReturn(List.of());

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
        String plnMngNo = "PLN-2026-0001";
        Bplanm plan = Bplanm.builder()
                .plnMngNo(plnMngNo)
                .plnYy("2026")
                .plnTp("신규")
                .ttlBg(BigDecimal.valueOf(100000000))
                .build();

        given(bplanmRepository.findByPlnMngNoAndDelYn(plnMngNo, "N")).willReturn(Optional.of(plan));
        given(bprojaRepository.findAllByBzMngNoAndDelYn(plnMngNo, "N")).willReturn(List.of());

        // when
        PlanDto.DetailResponse result = planService.getPlan(plnMngNo);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getPlnMngNo()).isEqualTo(plnMngNo);
    }

    @Test
    @DisplayName("getPlan - 미존재 계획관리번호 조회 시 ResponseStatusException(404) 발생")
    void getPlan_미존재번호_404예외발생() {
        // given
        given(bplanmRepository.findByPlnMngNoAndDelYn("INVALID", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> planService.getPlan("INVALID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("존재하지 않는 계획입니다");
    }

    // =========================================================================
    // createPlan
    // =========================================================================

    @Test
    @DisplayName("createPlan - prjMngNos가 비어있으면 ResponseStatusException(400) 발생")
    void createPlan_빈프로젝트목록_400예외발생() {
        // given: 프로젝트 목록이 빈 요청
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
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
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
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
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
                .prjMngNos(List.of("PRJ-2026-0001"))
                .build();

        ProjectDto.Response mockProject = ProjectDto.Response.builder()
                .abusMngNo("PRJ-2026-0001")
                .rqmBgAmt(BigDecimal.valueOf(50000000))
                .assetBg(BigDecimal.valueOf(30000000))
                .costBg(BigDecimal.valueOf(20000000))
                .build();

        given(projectService.getProjectsByIds(any())).willReturn(List.of(mockProject));
        given(bplanmRepository.getNextSequenceValue()).willReturn(1L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        String result = planService.createPlan(request);

        // then
        assertThat(result).isEqualTo("PLN-2026-0001");
        verify(bplanmRepository, times(1)).save(any(Bplanm.class));
        // 프로젝트-계획 관계도 저장되어야 함
        verify(bprojaRepository, times(1)).save(any(Bproja.class));
    }

    @Test
    @DisplayName("createPlan - 전산업무비만 선택해도 예산 합계와 관계를 저장한다")
    void createPlan_전산업무비만선택_계획생성() throws Exception {
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
                .itMngcNos(List.of("COST-2026-0001"))
                .build();
        CostDto.Response cost = CostDto.Response.builder()
                .itMngcNo("COST-2026-0001")
                .cttNm("전산업무비")
                .itMngcTp("유지보수")
                .biceDpmC("001")
                .biceDpmNm(null)
                .itMngcBgAmt(BigDecimal.valueOf(100))
                .assetBg(BigDecimal.valueOf(70))
                .costBg(BigDecimal.valueOf(30))
                .build();
        given(costService.getCostsByIds(any())).willReturn(List.of(cost));
        given(bplanmRepository.getNextSequenceValue()).willReturn(2L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        String result = planService.createPlan(request);

        assertThat(result).isEqualTo("PLN-2026-0002");
        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getTtlBg()).isEqualByComparingTo("100");
        assertThat(planCaptor.getValue().getCptBg()).isEqualByComparingTo("70");
        assertThat(planCaptor.getValue().getMngc()).isEqualByComparingTo("30");
        verify(bprojaRepository).save(any(Bproja.class));
    }

    @Test
    @DisplayName("createPlan - 프로젝트와 전산업무비의 null 예산은 0으로 계산한다")
    void createPlan_null예산_0으로계산() throws Exception {
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("조정")
                .prjMngNos(List.of("PRJ-2026-0001"))
                .itMngcNos(List.of("COST-2026-0001"))
                .build();
        ProjectDto.Response project = ProjectDto.Response.builder()
                .abusMngNo("PRJ-2026-0001")
                .prjNm("정보화사업")
                .prlmHrkOgzCCone(null)
                .prjBzTc(null)
                .rqmBgAmt(null)
                .assetBg(null)
                .costBg(null)
                .build();
        CostDto.Response cost = CostDto.Response.builder()
                .itMngcNo("COST-2026-0001")
                .cttNm("전산업무비")
                .biceDpmNm("IT부")
                .itMngcBgAmt(null)
                .assetBg(null)
                .costBg(null)
                .build();
        given(projectService.getProjectsByIds(any())).willReturn(List.of(project));
        given(costService.getCostsByIds(any())).willReturn(List.of(cost));
        given(bplanmRepository.getNextSequenceValue()).willReturn(3L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        planService.createPlan(request);

        ArgumentCaptor<Bplanm> planCaptor = ArgumentCaptor.forClass(Bplanm.class);
        verify(bplanmRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getTtlBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(planCaptor.getValue().getCptBg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(planCaptor.getValue().getMngc()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(bprojaRepository, times(2)).save(any(Bproja.class));
    }

    @Test
    @DisplayName("createPlan - 스냅샷 그룹 목록에서는 경상사업과 전산업무비를 제외한다")
    void createPlan_스냅샷그룹목록_경상사업과전산업무비제외() throws Exception {
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
                .prjMngNos(List.of("PRJ-GENERAL", "PRJ-ORDINARY"))
                .itMngcNos(List.of("COST-001"))
                .build();
        ProjectDto.Response generalProject = ProjectDto.Response.builder()
                .abusMngNo("PRJ-GENERAL")
                .prjNm("일반 정보화사업")
                .prjBzTc("개발")
                .prlmHrkOgzCCone("IT부문")
                .odnYn("N")
                .build();
        ProjectDto.Response ordinaryProject = ProjectDto.Response.builder()
                .abusMngNo("PRJ-ORDINARY")
                .prjNm("경상사업")
                .prjBzTc("운영")
                .prlmHrkOgzCCone("IT부문")
                .odnYn("Y")
                .build();
        CostDto.Response cost = CostDto.Response.builder()
                .itMngcNo("COST-001")
                .cttNm("전산업무비")
                .itMngcTp("관리비")
                .build();
        given(projectService.getProjectsByIds(any())).willReturn(List.of(generalProject, ordinaryProject));
        given(costService.getCostsByIds(any())).willReturn(List.of(cost));
        given(bplanmRepository.getNextSequenceValue()).willReturn(4L);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        planService.createPlan(request);

        ArgumentCaptor<PlanDto.SnapshotDto> snapshotCaptor = ArgumentCaptor.forClass(PlanDto.SnapshotDto.class);
        verify(objectMapper).writeValueAsString(snapshotCaptor.capture());
        PlanDto.SnapshotDto snapshot = snapshotCaptor.getValue();
        List<String> departmentIds = snapshot.getByDepartment().stream()
                .flatMap(group -> ((List<?>) group.get("projects")).stream())
                .map(item -> ((PlanDto.ProjectSnapshot) item).getPrjMngNo())
                .toList();
        List<String> projectTypeIds = snapshot.getByProjectType().stream()
                .flatMap(group -> ((List<?>) group.get("projects")).stream())
                .map(item -> ((PlanDto.ProjectSnapshot) item).getPrjMngNo())
                .toList();

        assertThat(departmentIds).containsExactly("PRJ-GENERAL");
        assertThat(projectTypeIds).containsExactly("PRJ-GENERAL");
    }

    @Test
    @DisplayName("createPlan - 스냅샷 직렬화 실패 시 500 예외가 발생한다")
    void createPlan_스냅샷직렬화실패_500예외발생() throws Exception {
        PlanDto.CreateRequest request = PlanDto.CreateRequest.builder()
                .plnYy("2026")
                .plnTp("신규")
                .prjMngNos(List.of("PRJ-2026-0001"))
                .build();
        given(projectService.getProjectsByIds(any())).willReturn(List.of(ProjectDto.Response.builder()
                .abusMngNo("PRJ-2026-0001")
                .build()));
        given(objectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("boom") {});

        assertThatThrownBy(() -> planService.createPlan(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("계획 스냅샷 직렬화");
    }

    // =========================================================================
    // deletePlan
    // =========================================================================

    @Test
    @DisplayName("deletePlan - 미존재 계획관리번호 삭제 시 ResponseStatusException(404) 발생")
    void deletePlan_미존재번호_404예외발생() {
        // given
        given(bplanmRepository.findByPlnMngNoAndDelYn("INVALID", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> planService.deletePlan("INVALID"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("존재하지 않는 계획입니다");
    }

    @Test
    @DisplayName("deletePlan - 정상 삭제 시 plan.delete() 호출 및 관계 레코드도 삭제")
    void deletePlan_정상삭제_SoftDelete() {
        // given
        String plnMngNo = "PLN-2026-0001";
        Bplanm plan = Bplanm.builder().plnMngNo(plnMngNo).build();
        Bproja relation = Bproja.builder()
                .prjMngNo("PRJ-2026-0001")
                .bzMngNo(plnMngNo)
                .build();

        given(bplanmRepository.findByPlnMngNoAndDelYn(plnMngNo, "N")).willReturn(Optional.of(plan));
        given(bprojaRepository.findAllByBzMngNoAndDelYn(plnMngNo, "N")).willReturn(List.of(relation));

        // when
        planService.deletePlan(plnMngNo);

        // then: 계획 Soft Delete 확인
        assertThat(plan.getDelYn()).isEqualTo("Y");
        // then: 관계 레코드 Soft Delete 확인
        assertThat(relation.getDelYn()).isEqualTo("Y");
    }
}
