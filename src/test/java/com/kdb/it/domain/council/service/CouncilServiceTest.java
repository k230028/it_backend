package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;

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
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.budget.project.service.ProjectBudgetSummaryService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
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

    @Mock
    private BprojaSyncService bprojaSyncService;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CouncilService councilService;

    @BeforeEach
    void injectEntityManager() {
        // @PersistenceContext 필드는 Mockito 생성자 주입 대상이 아니므로 테스트에서 명시적으로 연결한다.
        ReflectionTestUtils.setField(councilService, "entityManager", entityManager);
    }

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

        verify(council).changeStatus("99");
        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PRJ-2026-0001", "39");
    }

    // ───────────────────────────────────────────────────────
    // getCouncilList
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCouncilList: 관리자이면 전체 사업 목록을 반환한다")
    void getCouncilList_관리자_전체목록반환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        given(councilRepository.findProjectRowsForCouncilAll(anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCouncilList: 관리자 조회 행은 날짜 타입과 적용 여부를 변환하고 당해예산을 품목 파생값으로 반환한다")
    void getCouncilList_관리자_행변환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        // row[12]는 DB에서 NULL(TOT_RQM_AMT 컬럼 제거) — 당해예산은 품목 파생으로 산출.
        // fromRow를 거쳐 native Object[]의 날짜/적용여부 타입 변환이 그대로 검증되도록 한다.
        CouncilProjectRow row = CouncilProjectRow.fromRow(new Object[]{
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
        });
        // 품목 파생 당해예산: 배치 조회로 활성 품목 1건(amt=5000, mplAmt=0) → totRqmAmt=5000 반환 시뮬레이션
        ProjectItemRepository.ProjectItemBudgetView item = mock(ProjectItemRepository.ProjectItemBudgetView.class);
        given(item.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(projectItemRepository.findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        doAnswer(inv -> {
            ProjectDto.Response resp = inv.getArgument(0);
            resp.setTotRqmAmt(new BigDecimal("5000"));
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummaryViews(any(ProjectDto.Response.class), anyList());
        given(councilRepository.findProjectRowsForCouncilAll(anyString(), anyString()))
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
        // 문자열 날짜(yyyy-MM-dd / yyyyMMdd)·null·파싱불가("invalid")가 fromRow에서 방어적으로 처리되는지 검증
        CouncilProjectRow row = CouncilProjectRow.fromRow(new Object[]{
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
        });
        given(councilRepository.findProjectRowsForCouncilAll(anyString(), anyString()))
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
        // 품목 파생 당해예산: 배치 조회 후 applyBudgetSummary가 totRqmAmt=3000 설정 시뮬레이션
        ProjectItemRepository.ProjectItemBudgetView bitemm = mock(ProjectItemRepository.ProjectItemBudgetView.class);
        given(bitemm.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(projectItemRepository.findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(bitemm));
        doAnswer(inv -> {
            ProjectDto.Response resp = inv.getArgument(0);
            resp.setTotRqmAmt(new BigDecimal("3000"));
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummaryViews(any(ProjectDto.Response.class), anyList());

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
        given(councilRepository.findProjectRowsForCouncilByDepartment(
                anyString(), anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("협의회 목록 당해예산 파생은 품목을 1회 배치 조회하고 행별 값이 배치 결과와 동일하다 (행별 N+1 없음)")
    void 목록_당해예산_품목_배치조회() {
        // given: 서로 다른 abusMngNo를 가진 2개 행으로 구성된 관리자 협의회 목록.
        // row1은 품목을 가지고(배치 조회 결과에 포함), row2는 품목이 없다(빈 목록).
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        CouncilProjectRow row1 = listRowWithAbusMngNo("PRJ-2026-0001", ASCT_ID);
        CouncilProjectRow row2 = listRowWithAbusMngNo("PRJ-2026-0002", "ASCT-2026-0002");
        given(councilRepository.findProjectRowsForCouncilAll(anyString(), anyString()))
                .willReturn(List.of(row1, row2));
        // 품목은 1회 배치 조회로만 가져온다. PRJ-2026-0001만 활성 품목 1건을 가진다.
        ProjectItemRepository.ProjectItemBudgetView item = mock(ProjectItemRepository.ProjectItemBudgetView.class);
        given(item.getAbusMngNo()).willReturn("PRJ-2026-0001");
        given(projectItemRepository.findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N")))
                .willReturn(List.of(item));
        // 품목이 있는 사업은 applyBudgetSummary가 totRqmAmt=7000을 설정, 빈 목록은 설정하지 않아 null 유지.
        doAnswer(inv -> {
            List<ProjectItemRepository.ProjectItemBudgetView> items = inv.getArgument(1);
            if (!items.isEmpty()) {
                ProjectDto.Response resp = inv.getArgument(0);
                resp.setTotRqmAmt(new BigDecimal("7000"));
            }
            return null;
        }).when(projectBudgetSummaryService).applyBudgetSummaryViews(any(ProjectDto.Response.class), anyList());

        // when
        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        // then: 배치 조회 1회, 행별 단건 조회는 0회
        then(projectItemRepository).should(times(1)).findBudgetViewsByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        then(projectItemRepository).should(never()).findByAbusMngNoInAndDelYn(anyCollection(), eq("N"));
        then(projectItemRepository).should(never()).findByAbusMngNoAndDelYn(anyString(), anyString());
        // 그리고 품목이 있는 행(PRJ-2026-0001)의 당해예산은 배치 합산 결과(7000)와 동일하다.
        assertThat(result).hasSize(2);
        CouncilDto.ListResponse withItems = result.stream()
                .filter(r -> "PRJ-2026-0001".equals(r.prjMngNo()))
                .findFirst().orElseThrow();
        assertThat(withItems.prjBg()).isEqualByComparingTo("7000");
    }

    /**
     * 목록 행 생성 헬퍼 — 지정한 abusMngNo/asctId를 가진 최소 유효 행을 native Object[]로 만든 뒤
     * {@link CouncilProjectRow#fromRow(Object[])}로 변환해 봉인 경로와 동일한 DTO를 돌려준다.
     */
    private CouncilProjectRow listRowWithAbusMngNo(String abusMngNo, String asctId) {
        return CouncilProjectRow.fromRow(new Object[]{
                abusMngNo,                                  // row[0] abusMngNo
                BigDecimal.ONE,                             // row[1] sno
                "정보화사업",                               // row[2] prjNm
                asctId,                                     // row[3] asctId
                "01",                                       // row[4] asctStsC
                "03",                                       // row[5] dbrTc
                Timestamp.valueOf(LocalDateTime.of(2026, 5, 9, 10, 0)), // row[6] cnrcDt
                "10:00",                                    // row[7] cnrcTm
                BigDecimal.ONE,                             // row[8] applied
                "2026",                                     // row[9] prjYy
                "신규",                                     // row[10] prjTp
                "101",                                      // row[11] svnDpm
                null,                                       // row[12] rqmBgAmt(NULL)
                Date.valueOf(LocalDate.of(2026, 1, 1)),     // row[13] sttDt
                LocalDateTime.of(2026, 12, 31, 0, 0),       // row[14] endDt
                "IT",                                       // row[15] itDpm
                "설명",                                     // row[16] prjDes
                "Y"                                         // row[17] csfHeldYn
        });
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
        CouncilDto.CreateRequest request = new CouncilDto.CreateRequest("PRJ-2026-0001", 1, "03", null);
        given(councilRepository.getNextSequenceValue()).willReturn(1L);

        String result = councilService.createCouncil(request, user);

        assertThat(result).startsWith("ASCT-");
        verify(entityManager).persist(any(Basctm.class));
        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PRJ-2026-0001", "32");
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
        // 배치 COUNT 결과에 평가자가 없으면(미제출) getOrDefault(...,0L)<6 으로 미완료 판정
        given(evaluationRepository.countByEnoForCouncil(ASCT_ID, "N")).willReturn(List.of());

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
        given(evaluationRepository.countByEnoForCouncil(ASCT_ID, "N"))
                .willReturn(List.<Object[]>of(new Object[]{"10002", 6L}));

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
        // 간사(10001)는 평가 의무 제외 → 배치 결과에 없어도 무방. 평가위원(10002)만 6건 제출.
        given(evaluationRepository.countByEnoForCouncil(ASCT_ID, "N"))
                .willReturn(List.<Object[]>of(new Object[]{"10002", 6L}));

        councilService.completeCouncil(ASCT_ID);

        verify(council).changeStatus("09");
    }

    @Test
    @DisplayName("completeCouncil: 평가자 N명이어도 배치 COUNT를 1회만 호출하고 평가자별 단건 조회 루프가 없다 (#4 N+1 제거)")
    void completeCouncil_평가완료검증_배치COUNT_N플러스1없음() {
        // Arrange
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // 평가 의무 위원 2명(MAND 01, CALL 02) — 둘 다 6항목 제출 완료
        Bcmmtm e1 = mock(Bcmmtm.class);
        given(e1.getItPtlAsctMebTc()).willReturn("01");
        given(e1.getEno()).willReturn("10001");
        Bcmmtm e2 = mock(Bcmmtm.class);
        given(e2.getItPtlAsctMebTc()).willReturn("02");
        given(e2.getEno()).willReturn("10002");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(e1, e2));

        // 배치 COUNT 결과: 두 평가자 모두 6건 제출
        given(evaluationRepository.countByEnoForCouncil(ASCT_ID, "N")).willReturn(List.of(
                new Object[]{"10001", 6L},
                new Object[]{"10002", 6L}));

        // Act
        councilService.completeCouncil(ASCT_ID);

        // Assert
        verify(council).changeStatus("09");
        then(evaluationRepository).should(times(1)).countByEnoForCouncil(ASCT_ID, "N");
        then(evaluationRepository).should(never())
                .findByItPtlAsctIdAndEnoAndDelYn(anyString(), anyString(), anyString());
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

        verify(bprojaSyncService).upsert("PRJ-2026-0001", "PRJ-2026-0001", "39");
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

    @Test
    @DisplayName("startPreparation: 결재완료 상태이면 개최준비로 전이한다")
    void startPreparation_결재완료_개최준비전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("04");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        councilService.startPreparation(ASCT_ID);

        verify(council).changeStatus("05");
    }

    @Test
    @DisplayName("startPreparation: 결재완료 상태가 아니면 전이를 거부한다")
    void startPreparation_결재완료아님_상태예외() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.startPreparation(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("04");
    }

    @Test
    @DisplayName("verifyCouncilManager: IT관리자는 모든 심의유형을 관리할 수 있다")
    void verifyCouncilManager_IT관리자_통과() {
        CustomUserDetails admin = new CustomUserDetails(
                "A001", List.of(CustomUserDetails.ATH_ADMIN), "D001");

        org.assertj.core.api.Assertions.assertThatCode(
                () -> councilService.verifyCouncilManager(ASCT_ID, admin))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verifyCouncilManager: 정보보호관리자는 정보보호시스템 협의회를 관리할 수 있다")
    void verifyCouncilManager_정보보호관리자_정보보호심의통과() {
        CustomUserDetails admin = new CustomUserDetails(
                "S001", List.of(CustomUserDetails.ATH_INFOSEC_ADMIN), "D001");
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("04");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        org.assertj.core.api.Assertions.assertThatCode(
                () -> councilService.verifyCouncilManager(ASCT_ID, admin))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("verifyCouncilManager: 정보보호관리자의 일반 심의 관리를 거부한다")
    void verifyCouncilManager_정보보호관리자_일반심의거부() {
        CustomUserDetails admin = new CustomUserDetails(
                "S001", List.of(CustomUserDetails.ATH_INFOSEC_ADMIN), "D001");
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("03");
        given(councilRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.verifyCouncilManager(ASCT_ID, admin))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("verifyCouncilManager: 인증 정보가 없으면 관리를 거부한다")
    void verifyCouncilManager_인증없음_거부() {
        assertThatThrownBy(() -> councilService.verifyCouncilManager(ASCT_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("verifyAdmin: IT관리자만 통과하고 일반사용자와 미인증 요청은 거부한다")
    void verifyAdmin_권한별검증() {
        CustomUserDetails admin = new CustomUserDetails(
                "A001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        CustomUserDetails user = new CustomUserDetails(
                "U001", List.of(CustomUserDetails.ATH_USER), "D001");

        org.assertj.core.api.Assertions.assertThatCode(() -> councilService.verifyAdmin(admin))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> councilService.verifyAdmin(user))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> councilService.verifyAdmin(null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getCouncilList: 정보보호관리자는 미신청 사업과 정보보호 심의만 조회한다")
    void getCouncilList_정보보호관리자_허용범위필터() {
        CustomUserDetails admin = new CustomUserDetails(
                "S001", List.of(CustomUserDetails.ATH_INFOSEC_ADMIN), "D001");
        CouncilProjectRow notApplied = listRowWithAbusMngNo("PRJ-001", null);
        CouncilProjectRow infoSec = listRowWithAbusMngNo("PRJ-002", "ASCT-002");
        CouncilProjectRow general = listRowWithAbusMngNo("PRJ-003", "ASCT-003");
        // 적용된 행의 심의유형을 필터에서 구분하도록 native row mock의 접근값을 지정한다.
        infoSec = org.mockito.Mockito.spy(infoSec);
        general = org.mockito.Mockito.spy(general);
        notApplied = org.mockito.Mockito.spy(notApplied);
        org.mockito.Mockito.doReturn(false).when(notApplied).applied();
        org.mockito.Mockito.doReturn("04").when(infoSec).itPtlAsctDbrTc();
        org.mockito.Mockito.doReturn("03").when(general).itPtlAsctDbrTc();
        given(councilRepository.findProjectRowsForCouncilAll(anyString(), anyString()))
                .willReturn(List.of(notApplied, infoSec, general));

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).hasSize(2);
    }
}
