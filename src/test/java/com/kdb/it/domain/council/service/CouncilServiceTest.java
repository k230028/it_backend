package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectBudgetSummaryService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;

/**
 * CouncilService 단위 테스트
 *
 * <p>
 * 협의회 기본 서비스의 상태 전이 메서드와 단건 조회를 검증합니다.
 * Basctm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilServiceTest {

    @Mock
    private CouncilRepository councilRepository;

    @Mock
    private ProjectOverviewRepository projectOverviewRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ProjectItemRepository projectItemRepository;

    @Mock
    private ProjectBudgetSummaryService projectBudgetSummaryService;

    @InjectMocks
    private CouncilService councilService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // findActiveCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findActiveCouncil: 존재하지 않는 협의회ID이면 IllegalArgumentException을 던진다")
    void findActiveCouncil_존재하지않는협의회_IllegalArgumentException발생() {
        // given
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> councilService.findActiveCouncil(ASCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ASCT_ID);
    }

    @Test
    @DisplayName("findActiveCouncil: 존재하는 협의회ID이면 Basctm 엔티티를 반환한다")
    void findActiveCouncil_존재하는협의회_엔티티반환() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctId()).willReturn(ASCT_ID);
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        Basctm result = councilService.findActiveCouncil(ASCT_ID);

        // then
        assertThat(result.getItPtlAsctId()).isEqualTo(ASCT_ID);
    }

    // ───────────────────────────────────────────────────────
    // changeStatus
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus: 협의회 상태를 지정한 값으로 변경한다")
    void changeStatus_정상호출_상태변경() {
        // given
        Basctm council = mock(Basctm.class);
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        councilService.changeStatus(ASCT_ID, "05");

        // then
        verify(council).changeStatus("05");
    }

    // ───────────────────────────────────────────────────────
    // startCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("startCouncil: 협의회 상태가 SCHEDULED가 아니면 IllegalStateException을 던진다")
    void startCouncil_SCHEDULED아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when & then
        assertThatThrownBy(() -> councilService.startCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("06");
    }

    @Test
    @DisplayName("startCouncil: SCHEDULED 상태이면 IN_PROGRESS로 전이한다")
    void startCouncil_SCHEDULED상태_IN_PROGRESS전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("06");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        councilService.startCouncil(ASCT_ID);

        // then
        verify(council).changeStatus("07");
    }

    // ───────────────────────────────────────────────────────
    // skipCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("skipCouncil: 협의회 상태가 APPROVED가 아니면 IllegalStateException을 던진다")
    void skipCouncil_APPROVED아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("01");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when & then
        assertThatThrownBy(() -> councilService.skipCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("04");
    }

    @Test
    @DisplayName("skipCouncil: APPROVED 상태이면 SKIPPED로 전이하고 사업 상태를 업데이트한다")
    void skipCouncil_APPROVED상태_SKIPPED전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("04");
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        councilService.skipCouncil(ASCT_ID);

        verify(council).changeStatus("SKIPPED");
        verify(councilRepository).updateProjectStatus("PRJ-2026-0001", 1, "39");
    }

    // ───────────────────────────────────────────────────────
    // getCouncilList
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCouncilList: 관리자이면 전체 사업 목록을 반환한다")
    void getCouncilList_관리자_전체목록반환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        given(councilRepository.findProjectsForCouncilAll(anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCouncilList: 관리자 조회 행은 날짜 타입과 적용 여부를 변환하고 당해예산을 품목 파생값으로 반환한다")
    void getCouncilList_관리자_행변환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        // row[12]는 DB에서 NULL(TOT_RQM_AMT 컬럼 제거) — 당해예산은 품목 파생으로 산출
        Object[] row = new Object[]{
                "PRJ-2026-0001",
                BigDecimal.ONE,
                "정보화사업",
                ASCT_ID,
                "01",
                "03",
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 9, 10, 0)),
                "10:00",                                    // cnrcTm (PRD §25 추가)
                BigDecimal.ONE,
                "2026",
                "신규",
                "101",
                null,                                       // rqmBgAmt: TOT_RQM_AMT 컬럼 제거로 NULL
                Date.valueOf(LocalDate.of(2026, 1, 1)),
                LocalDateTime.of(2026, 12, 31, 0, 0),
                "IT",
                "설명",
                "Y"                                          // csfHeldYn (PRD_c_20260620 #1)
        };
        // 품목 파생 당해예산: 활성 품목 1건(amt=5000, mplAmt=0) → totRqmAmt=5000 반환 시뮬레이션
        given(projectItemRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(List.of(mock(Bitemm.class)));
        doAnswer(inv -> {
            ProjectDto.Response resp = inv.getArgument(0);
            resp.setTotRqmAmt(new BigDecimal("5000"));
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummary(any(ProjectDto.Response.class), anyList());
        given(councilRepository.findProjectsForCouncilAll(anyString(), anyString()))
                .willReturn(java.util.Collections.singletonList(row));

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prjSno()).isEqualTo(1);
        assertThat(result.get(0).csfHeldYn()).isEqualTo("Y");
        assertThat(result.get(0).cnrcDt()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(result.get(0).applied()).isTrue();
        // 당해예산은 row[12] 값이 아닌 품목 파생값(5000)으로 채워진다
        assertThat(result.get(0).prjBg()).isEqualByComparingTo("5000");
        assertThat(result.get(0).sttDt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(0).endDt()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("getCouncilList: 행 변환 시 문자열 날짜와 null 값을 방어적으로 처리한다")
    void getCouncilList_관리자_문자열날짜와null변환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        Object[] row = new Object[]{
                "PRJ-2026-0001",
                null,
                "정보화사업",
                ASCT_ID,
                "01",
                "03",
                "2026-05-09",
                "10:00",
                null,
                "2026",
                "신규",
                "101",
                null,
                "20260101",
                "invalid",
                "IT",
                "설명",
                null                                          // csfHeldYn (미확정)
        };
        given(councilRepository.findProjectsForCouncilAll(anyString(), anyString()))
                .willReturn(java.util.Collections.singletonList(row));

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prjSno()).isNull();
        assertThat(result.get(0).cnrcDt()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(result.get(0).applied()).isFalse();
        // 품목이 없으면(projectItemRepository 빈 목록 반환) applyBudgetSummary가 totRqmAmt를 설정하지 않아 null
        assertThat(result.get(0).prjBg()).isNull();
        assertThat(result.get(0).sttDt()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(result.get(0).endDt()).isNull();
    }

    @Test
    @DisplayName("getCouncilList: 평가위원이면 배정된 협의회 목록을 반환한다")
    void getCouncilList_평가위원_배정협의회반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctId()).willReturn(ASCT_ID);
        given(councilRepository.findByCommitteeMember("10001", "N")).willReturn(List.of(council));
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getCouncilList: 평가위원 조회는 사업개요명을 우선하고 사업 상세 필드를 함께 채운다")
    void getCouncilList_평가위원_사업개요명우선반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctId()).willReturn(ASCT_ID);
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        given(council.getItPtlAsctPrgStsTc()).willReturn("06");
        given(council.getItPtlAsctDbrTc()).willReturn("03");
        given(council.getCnrcDt()).willReturn(LocalDate.of(2026, 5, 9));
        given(council.getCnrcSttTm()).willReturn("10:00");
        Bpovwm overview = Bpovwm.builder()
                .itPtlAsctId(ASCT_ID)
                .abusNm("사업개요명")
                .build();
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-2026-0001")
                .sno(1)
                .abusNm("사업마스터명")
                .bseYy("2026")
                .bzTpC("신규")
                .svnDpmC("101")
                .sttDtm(LocalDate.of(2026, 1, 1))
                .endDtm(LocalDate.of(2026, 12, 31))
                .dvmDpmC("IT")
                .abusCone("사업설명")
                .build();
        given(councilRepository.findByCommitteeMember("10001", "N")).willReturn(List.of(council));
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(overview));
        given(projectRepository.findById(any())).willReturn(Optional.of(project));
        // 품목 파생 당해예산: 활성 품목 조회 후 applyBudgetSummary가 totRqmAmt=3000 설정 시뮬레이션
        given(projectItemRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(List.of(mock(Bitemm.class)));
        doAnswer(inv -> {
            ProjectDto.Response resp = inv.getArgument(0);
            resp.setTotRqmAmt(new BigDecimal("3000"));
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummary(any(ProjectDto.Response.class), anyList());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).singleElement()
                .satisfies(item -> {
                    assertThat(item.abusNm()).isEqualTo("사업개요명");
                    assertThat(item.prjYy()).isEqualTo("2026");
                    assertThat(item.prjTp()).isEqualTo("신규");
                    // 당해예산은 품목 파생값(∑AMT − ∑MPL_AMT)으로 산출됨
                    assertThat(item.prjBg()).isEqualByComparingTo("3000");
                    assertThat(item.sttDt()).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(item.endDt()).isEqualTo(LocalDate.of(2026, 12, 31));
                    assertThat(item.itDpm()).isEqualTo("IT");
                    assertThat(item.prjDes()).isEqualTo("사업설명");
                });
    }

    @Test
    @DisplayName("getCouncilList: 일반 사용자이면 부서별 사업 목록을 반환한다")
    void getCouncilList_일반사용자_부서별목록반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        given(councilRepository.findByCommitteeMember("10001", "N")).willReturn(List.of());
        given(councilRepository.findProjectsForCouncilByDepartment(
                anyString(), anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCouncil: 존재하는 협의회이면 DetailResponse를 반환한다")
    void getCouncil_존재하는협의회_DetailResponse반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctId()).willReturn(ASCT_ID);
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findById(any())).willReturn(Optional.empty());

        CouncilDto.DetailResponse result = councilService.getCouncil(ASCT_ID);

        assertThat(result.asctId()).isEqualTo(ASCT_ID);
    }

    @Test
    @DisplayName("getCouncil: 프로젝트가 있으면 사업 상세 기본값을 함께 반환한다")
    void getCouncil_프로젝트있음_상세기본값반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctId()).willReturn(ASCT_ID);
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        Bprojm project = Bprojm.builder()
                .abusMngNo("PRJ-2026-0001")
                .sno(1)
                .abusNm("정보화사업")
                .edrtTc("전결권자")
                .sttDtm(LocalDate.of(2026, 1, 1))
                .endDtm(LocalDate.of(2026, 12, 31))
                .abusNcsCone("필요성")
                .abusCone("사업설명")
                .dgogPpoCone("기대효과")
                .build();
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findById(any())).willReturn(Optional.of(project));
        // 품목 파생 당해예산: 활성 품목 조회 후 applyBudgetSummary가 totRqmAmt=2000 설정 시뮬레이션
        given(projectItemRepository.findByAbusMngNoAndDelYn("PRJ-2026-0001", "N"))
                .willReturn(List.of(mock(Bitemm.class)));
        doAnswer(inv -> {
            ProjectDto.Response resp = inv.getArgument(0);
            resp.setTotRqmAmt(new BigDecimal("2000"));
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummary(any(ProjectDto.Response.class), anyList());

        CouncilDto.DetailResponse result = councilService.getCouncil(ASCT_ID);

        assertThat(result.abusNm()).isEqualTo("정보화사업");
        assertThat(result.edrt()).isEqualTo("전결권자");
        // 당해예산은 품목 파생값(∑AMT − ∑MPL_AMT)으로 산출되어야 한다
        assertThat(result.prjBg()).isEqualByComparingTo("2000");
    }

    // ───────────────────────────────────────────────────────
    // createCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCouncil: 정상 요청이면 ASCT-{연도}-{순번} 형식의 협의회ID를 반환한다")
    void createCouncil_정상요청_협의회ID반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.CreateRequest request = new CouncilDto.CreateRequest("PRJ-2026-0001", 1, "03");
        given(councilRepository.getNextSequenceValue()).willReturn(1L);

        String result = councilService.createCouncil(request, user);

        assertThat(result).startsWith("ASCT-");
        verify(councilRepository).save(any(Basctm.class));
    }

    // ───────────────────────────────────────────────────────
    // completeCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("completeCouncil: IN_PROGRESS가 아닌 상태이면 IllegalStateException을 던진다")
    void completeCouncil_IN_PROGRESS아닌상태_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("01");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("completeCouncil: 평가위원이 없으면 IllegalStateException을 던진다")
    void completeCouncil_평가위원없음_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가위원");
    }

    @Test
    @DisplayName("completeCouncil: 평가 미완료 위원이 있으면 IllegalStateException을 던진다")
    void completeCouncil_평가미완료위원있음_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        Bcmmtm evaluator = mock(Bcmmtm.class);
        given(evaluator.getItPtlAsctMebTc()).willReturn("01");
        given(evaluator.getEno()).willReturn("10002");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(evaluator));
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "10002", "N")).willReturn(List.of());

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미완료");
    }

    @Test
    @DisplayName("completeCouncil: 모든 평가위원이 6항목 제출 완료이면 RESULT_WRITING으로 전이한다")
    void completeCouncil_정상완료_RESULT_WRITING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        Bcmmtm evaluator = mock(Bcmmtm.class);
        given(evaluator.getItPtlAsctMebTc()).willReturn("01");
        given(evaluator.getEno()).willReturn("10002");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(evaluator));
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "10002", "N"))
                .willReturn(List.of(mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class),
                        mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class)));

        councilService.completeCouncil(ASCT_ID);

        verify(council).changeStatus("09");
    }

    @Test
    @DisplayName("completeCouncil: EVALUATING 상태에서도 간사를 제외한 평가 완료 여부만 확인한다")
    void completeCouncil_EVALUATING상태_간사제외하고완료() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        Bcmmtm secretary = mock(Bcmmtm.class);
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(secretary.getEno()).willReturn("10001");
        Bcmmtm caller = mock(Bcmmtm.class);
        given(caller.getItPtlAsctMebTc()).willReturn("02");
        given(caller.getEno()).willReturn("10002");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(secretary, caller));
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, "10002", "N"))
                .willReturn(List.of(mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class),
                        mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class)));

        councilService.completeCouncil(ASCT_ID);

        verify(council).changeStatus("09");
    }

    // ───────────────────────────────────────────────────────
    // notifyCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyCouncil: COMPLETED가 아닌 상태이면 IllegalStateException을 던진다")
    void notifyCouncil_COMPLETED아닌상태_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.notifyCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("13");
    }

    @Test
    @DisplayName("notifyCouncil: COMPLETED 상태이면 사업 상태를 갱신하고 수신자 정보를 반환한다")
    void notifyCouncil_정상통보_NotifyResponse반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("13");
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        given(council.getFstEnrUsid()).willReturn(null);
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        CouncilDto.NotifyResponse result = councilService.notifyCouncil(ASCT_ID);

        verify(councilRepository).updateProjectStatus("PRJ-2026-0001", 1, "39");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("notifyCouncil: 최초 등록자와 부서가 있으면 수신자 정보를 채운다")
    void notifyCouncil_수신자와부서있음_수신자정보반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("13");
        given(council.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(council.getSno()).willReturn(1);
        given(council.getFstEnrUsid()).willReturn("10001");
        CuserI user = CuserI.builder()
                .eno("10001")
                .usrNm("홍길동")
                .temNm("개발팀")
                .bbrC("101")
                .build();
        CorgnI org = CorgnI.builder()
                .prlmOgzCCone("101")
                .bbrNm("IT부")
                .build();
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(organizationRepository.findById("101")).willReturn(Optional.of(org));

        CouncilDto.NotifyResponse result = councilService.notifyCouncil(ASCT_ID);

        assertThat(result.eno()).isEqualTo("10001");
        assertThat(result.usrNm()).isEqualTo("홍길동");
        assertThat(result.bbrNm()).isEqualTo("IT부");
        assertThat(result.temNm()).isEqualTo("개발팀");
    }
}
