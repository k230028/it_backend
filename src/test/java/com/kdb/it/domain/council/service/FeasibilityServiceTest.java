package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bchklm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import com.kdb.it.domain.council.repository.SelfCheckRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FeasibilityService 단위 테스트
 *
 * <p>타당성검토표 서비스의 조회·저장(임시/완료) 메서드를 검증합니다. Bpovwm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로
 * 생성합니다. EntityManager(@PersistenceContext)는 성과지표 교체 경로(replacePerformances)에만 사용되므로, 해당 경로를 포함하지
 * 않는 테스트에서는 주입하지 않습니다. Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeasibilityServiceTest {

    @Mock private ProjectOverviewRepository projectOverviewRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private SelfCheckRepository selfCheckRepository;
    @Mock private CouncilService councilService;
    @Mock private EntityManager entityManager;

    @InjectMocks private FeasibilityService feasibilityService;

    @BeforeEach
    void injectEntityManager() {
        // @PersistenceContext 필드는 Mockito 생성자 주입 대상이 아니므로 테스트에서 명시적으로 연결한다.
        ReflectionTestUtils.setField(feasibilityService, "entityManager", entityManager);
    }

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // getFeasibility
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFeasibility: 사업개요 미작성이면 null을 반환한다")
    void getFeasibility_미작성상태_null반환() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getFeasibility: 사업개요·성과지표를 통합한 응답을 반환한다")
    void getFeasibility_데이터있음_통합응답반환() {
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getAbusNm()).willReturn("테스트사업");
        given(overview.getAbusTrmCone()).willReturn("2026");
        given(overview.getLwRglYn()).willReturn("N");
        given(overview.getKpnTpTc()).willReturn("20");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(overview));
        given(performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(ASCT_ID, "N"))
                .willReturn(List.of());

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.prjNm()).isEqualTo("테스트사업");
        assertThat(result.performances()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // saveFeasibility — 유효성 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveFeasibility: COMPLETE 타입에 첨부파일이 없으면 IllegalArgumentException을 던진다")
    void saveFeasibility_COMPLETE_첨부파일없음_IllegalArgumentException발생() {
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업", "2026", null, null, null, null, "N", null, null, "20", null, null,
                        null);

        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
    }

    // ───────────────────────────────────────────────────────
    // saveFeasibility — 상태 전이
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveFeasibility: COMPLETE 타입 정상 요청이면 SUBMITTED 상태로 전이한다")
    void saveFeasibility_COMPLETE_정상요청_SUBMITTED전이() {
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "20",
                        null,
                        "FL_00000001",
                        null);
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(councilService).changeStatus(ASCT_ID, "02");
    }

    @Test
    @DisplayName("saveFeasibility: TEMP 타입이면 상태 전이를 수행하지 않는다")
    void saveFeasibility_TEMP_상태전이없음() {
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업", "2026", null, null, null, null, "N", null, null, "10", null, null,
                        null);
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(councilService, never()).changeStatus(any(), any());
    }

    // ───────────────────────────────────────────────────────
    // saveFeasibility — 기존 데이터 upsert 경로 및 추가 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveFeasibility: 기존 사업개요가 있으면 update()를 호출하고 save()는 호출하지 않는다")
    void saveFeasibility_기존사업개요있음_update호출() {
        // given: 이미 저장된 사업개요 존재
        Bpovwm existing = mock(Bpovwm.class);
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(existing));

        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "수정된사업명", "2027", null, null, null, null, "N", null, null, "01", null, null,
                        null);

        // when
        feasibilityService.saveFeasibility(ASCT_ID, request);

        // then: 신규 INSERT가 아닌 update() 호출, save()는 호출되지 않음
        verify(existing)
                .update("수정된사업명", "2027", null, null, null, null, "N", null, null, "01", null);
        verify(projectOverviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveFeasibility: 성과지표가 있으면 기존 성과지표를 삭제하고 새 지표를 persist한다")
    void saveFeasibility_성과지표있음_교체저장() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        ReflectionTestUtils.setField(feasibilityService, "entityManager", entityManager);
        Query deleteQuery = mock(Query.class);
        given(entityManager.createQuery("DELETE FROM Bperfm b WHERE b.itPtlAsctId = :asctId"))
                .willReturn(deleteQuery);
        given(deleteQuery.setParameter("asctId", ASCT_ID)).willReturn(deleteQuery);
        given(deleteQuery.executeUpdate()).willReturn(1);
        List<CouncilDto.PerformanceRequest> performances =
                List.of(new CouncilDto.PerformanceRequest(1, "성과지표", "내용", "정량", "분기", "자동"));
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "01",
                        performances,
                        null,
                        null);

        feasibilityService.saveFeasibility(ASCT_ID, request);

        // P1 #2: DELETE 실행(executeUpdate) 직후, 신규 persist 이전에 flush가 호출되어야
        // DELETE→INSERT 순서가 보장된다(PK 충돌/유령 행 방지).
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(deleteQuery, entityManager);
        inOrder.verify(deleteQuery).executeUpdate();
        inOrder.verify(entityManager).flush();
        inOrder.verify(entityManager).persist(any(com.kdb.it.domain.council.entity.Bperfm.class));
    }

    // ───────────────────────────────────────────────────────
    // saveFeasibility — 자체점검(saveOrUpdateSelfChecks) 경로
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveFeasibility: COMPLETE 타입에 첨부파일 번호가 공백이면 IllegalArgumentException을 던진다")
    void saveFeasibility_COMPLETE_첨부파일공백_예외발생() {
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업", "2026", null, null, null, null, "N", null, null, "20", null, " ",
                        null);

        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("첨부파일");
    }

    @Test
    @DisplayName("saveFeasibility: 자체점검·성과지표가 빈 목록이면 두 저장을 모두 건너뛴다")
    void saveFeasibility_자체점검빈목록_저장건너뜀() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업", "2026", null, null, null, null, "N", null, null, "10", List.of(),
                        null, List.of());

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(selfCheckRepository, never()).findByItPtlAsctIdAndDelYn(any(), any());
        verify(performanceRepository, never())
                .findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(any(), any());
    }

    @Test
    @DisplayName("saveFeasibility: 임시저장 시 기존 자체점검 항목은 update, 신규 항목은 persist한다")
    void saveFeasibility_임시저장_자체점검upsert() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        Bchklm existing = mock(Bchklm.class);
        given(existing.getItPtlCkgItmTc()).willReturn("01");
        Bchklm duplicate = mock(Bchklm.class);
        given(duplicate.getItPtlCkgItmTc()).willReturn("01"); // 중복 항목코드는 첫 행 유지(merge 분기)
        given(selfCheckRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(existing, duplicate));
        List<CouncilDto.SelfCheckItem> selfChecks =
                List.of(
                        new CouncilDto.SelfCheckItem("01", 4, "기존 항목 수정"),
                        new CouncilDto.SelfCheckItem("02", null, null)); // 임시저장은 부분 입력 허용
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "10",
                        null,
                        null,
                        selfChecks);

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(existing).update(4, "기존 항목 수정");
        verify(entityManager).persist(any(Bchklm.class));
    }

    @Test
    @DisplayName("saveFeasibility: 작성완료 시 전 항목이 채워져 있으면 자체점검을 저장하고 상태를 전이한다")
    void saveFeasibility_작성완료_자체점검정상_저장및전이() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        given(selfCheckRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        List<CouncilDto.SelfCheckItem> selfChecks =
                List.of(new CouncilDto.SelfCheckItem("01", 4, "적정"));
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "20",
                        null,
                        "FL_00000001",
                        selfChecks);

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(entityManager).persist(any(Bchklm.class));
        verify(councilService).changeStatus(ASCT_ID, "02");
    }

    @Test
    @DisplayName("saveFeasibility: 작성완료 시 점검점수 누락 항목이 있으면 항목명을 포함한 예외를 던진다")
    void saveFeasibility_작성완료_점검점수누락_예외발생() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        List<CouncilDto.SelfCheckItem> selfChecks =
                List.of(new CouncilDto.SelfCheckItem("01", null, "의견"));
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "20",
                        null,
                        "FL_00000001",
                        selfChecks);

        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("점검점수")
                .hasMessageContaining("경영전략/계획 부합");
    }

    @Test
    @DisplayName("saveFeasibility: 작성완료 시 점검의견이 null인 항목이 있으면 예외를 던진다")
    void saveFeasibility_작성완료_점검의견null_예외발생() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        List<CouncilDto.SelfCheckItem> selfChecks =
                List.of(new CouncilDto.SelfCheckItem("02", 3, null));
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "20",
                        null,
                        "FL_00000001",
                        selfChecks);

        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("점검의견")
                .hasMessageContaining("재무 효과");
    }

    @Test
    @DisplayName("saveFeasibility: 작성완료 시 점검의견이 공백인 미등록 항목코드는 코드를 그대로 표기해 예외를 던진다")
    void saveFeasibility_작성완료_점검의견공백_미등록코드표기_예외발생() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());
        List<CouncilDto.SelfCheckItem> selfChecks =
                List.of(new CouncilDto.SelfCheckItem("99", 3, " ")); // 미등록 코드 → getOrDefault 폴백
        CouncilDto.FeasibilityRequest request =
                new CouncilDto.FeasibilityRequest(
                        "테스트사업",
                        "2026",
                        null,
                        null,
                        null,
                        null,
                        "N",
                        null,
                        null,
                        "20",
                        null,
                        "FL_00000001",
                        selfChecks);

        assertThatThrownBy(() -> feasibilityService.saveFeasibility(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("점검의견")
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getFeasibility: 자체점검 엔티티를 응답 DTO로 변환한다")
    void getFeasibility_자체점검있음_DTO변환() {
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getLwRglYn()).willReturn("N");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(overview));
        Bchklm check = mock(Bchklm.class);
        given(check.getItPtlCkgItmTc()).willReturn("01");
        given(check.getQuelRcrd()).willReturn(4);
        given(check.getCkgOpnn()).willReturn("적정");
        given(selfCheckRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(check));
        given(performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(ASCT_ID, "N"))
                .willReturn(List.of());

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result.selfChecks()).hasSize(1);
        assertThat(result.selfChecks().get(0).ckgItmC()).isEqualTo("01");
        assertThat(result.selfChecks().get(0).ckgRcrd()).isEqualTo(4);
    }

    @Test
    @DisplayName("getFeasibility: 성과지표 엔티티를 응답 DTO로 변환한다")
    void getFeasibility_점검성과지표있음_DTO변환() {
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getAbusNm()).willReturn("성과사업");
        given(overview.getLwRglYn()).willReturn("Y");
        given(overview.getKpnTpTc()).willReturn("10");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(overview));
        com.kdb.it.domain.council.entity.Bperfm perf =
                mock(com.kdb.it.domain.council.entity.Bperfm.class);
        given(perf.getEvlDtpSno()).willReturn(1);
        given(perf.getEvlDtpNm()).willReturn("성과지표");
        given(performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(ASCT_ID, "N"))
                .willReturn(List.of(perf));

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result.performances()).hasSize(1);
        assertThat(result.performances().get(0).dtpNm()).isEqualTo("성과지표");
    }
}
