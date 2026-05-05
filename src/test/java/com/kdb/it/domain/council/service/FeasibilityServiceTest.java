package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.FeasibilityCheckRepository;
import com.kdb.it.domain.council.repository.PerformanceRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;

/**
 * FeasibilityService 단위 테스트
 *
 * <p>
 * 타당성검토표 서비스의 조회·저장(임시/완료) 메서드를 검증합니다.
 * Bpovwm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * EntityManager(@PersistenceContext)는 성과지표 교체 경로(replacePerformances)에만
 * 사용되므로, 해당 경로를 포함하지 않는 테스트에서는 주입하지 않습니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeasibilityServiceTest {

    @Mock private ProjectOverviewRepository projectOverviewRepository;
    @Mock private FeasibilityCheckRepository feasibilityCheckRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private CouncilService councilService;

    @InjectMocks
    private FeasibilityService feasibilityService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // getFeasibility
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFeasibility: 사업개요 미작성이면 null을 반환한다")
    void getFeasibility_미작성상태_null반환() {
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getFeasibility: 사업개요·자체점검·성과지표를 통합한 응답을 반환하고 점검항목은 6개 고정이다")
    void getFeasibility_데이터있음_통합응답반환() {
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getPrjNm()).willReturn("테스트사업");
        given(overview.getPrjTrm()).willReturn("2026");
        given(overview.getLglRglYn()).willReturn("N");
        given(overview.getKpnTp()).willReturn("COMPLETE");
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(overview));
        given(feasibilityCheckRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        given(performanceRepository.findByAsctIdAndDelYnOrderByDtpSnoAsc(ASCT_ID, "N")).willReturn(List.of());

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.prjNm()).isEqualTo("테스트사업");
        assertThat(result.checkItems()).hasSize(6);
        assertThat(result.performances()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // saveFeasibility — 유효성 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveFeasibility: COMPLETE 타입에 첨부파일이 없으면 IllegalArgumentException을 던진다")
    void saveFeasibility_COMPLETE_첨부파일없음_IllegalArgumentException발생() {
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "COMPLETE", null, null, null);

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
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "COMPLETE", null, null, "FL_00000001");
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(councilService).changeStatus(ASCT_ID, "SUBMITTED");
    }

    @Test
    @DisplayName("saveFeasibility: TEMP 타입이면 상태 전이를 수행하지 않는다")
    void saveFeasibility_TEMP_상태전이없음() {
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "TEMP", null, null, null);
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

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
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(existing));

        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "수정된사업명", "2027", null, null, null, null, "N", null, null,
                "TEMP", null, null, null);

        // when
        feasibilityService.saveFeasibility(ASCT_ID, request);

        // then: 신규 INSERT가 아닌 update() 호출, save()는 호출되지 않음
        verify(existing).update(
                "수정된사업명", "2027", null, null, null, null, "N", null, null, "TEMP", null);
        verify(projectOverviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveFeasibility: 자체점검 항목이 있으면 항목별 조회를 수행한다")
    void saveFeasibility_자체점검항목있음_항목별조회수행() {
        // given: 기존 데이터 없음, 자체점검 항목 2개 포함
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

        List<CouncilDto.CheckItemRequest> checkItems = List.of(
                new CouncilDto.CheckItemRequest("MGMT_STR", "경영전략 부합 검토", 4),
                new CouncilDto.CheckItemRequest("FIN_EFC", "재무 효과 검토", 3)
        );

        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "TEMP", checkItems, null, null);

        // when
        feasibilityService.saveFeasibility(ASCT_ID, request);

        // then: 각 항목 코드별로 upsert 조회 호출 확인
        verify(feasibilityCheckRepository).findByAsctIdAndCkgItmCAndDelYn(ASCT_ID, "MGMT_STR", "N");
        verify(feasibilityCheckRepository).findByAsctIdAndCkgItmCAndDelYn(ASCT_ID, "FIN_EFC", "N");
    }

    @Test
    @DisplayName("saveFeasibility: COMPLETE 타입이고 기존 자체점검이 있으면 update()를 호출한다")
    void saveFeasibility_COMPLETE_기존점검항목있음_update호출() {
        // given: COMPLETE 타입, 첨부파일 있음, 기존 점검항목 존재
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

        com.kdb.it.domain.council.entity.Bchklc existingCheck =
                mock(com.kdb.it.domain.council.entity.Bchklc.class);
        given(feasibilityCheckRepository.findByAsctIdAndCkgItmCAndDelYn(ASCT_ID, "MGMT_STR", "N"))
                .willReturn(Optional.of(existingCheck));

        List<CouncilDto.CheckItemRequest> checkItems = List.of(
                new CouncilDto.CheckItemRequest("MGMT_STR", "수정된검토내용", 5)
        );

        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "COMPLETE", checkItems, null, "FL_00000001");

        // when
        feasibilityService.saveFeasibility(ASCT_ID, request);

        // then: 기존 점검항목 update() 호출 및 SUBMITTED 상태 전이
        verify(existingCheck).update("수정된검토내용", 5);
        verify(councilService).changeStatus(ASCT_ID, "SUBMITTED");
    }

    @Test
    @DisplayName("getFeasibility: 자체점검 항목 코드가 고정 6개 순서로 응답에 포함된다")
    void getFeasibility_점검항목코드_고정6개순서확인() {
        // given
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getPrjNm()).willReturn("순서확인사업");
        given(overview.getKpnTp()).willReturn("TEMP");
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(overview));
        given(feasibilityCheckRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        given(performanceRepository.findByAsctIdAndDelYnOrderByDtpSnoAsc(ASCT_ID, "N"))
                .willReturn(List.of());

        // when
        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        // then: 6개 항목이 고정 순서(MGMT_STR → ETC)로 반환
        assertThat(result.checkItems()).hasSize(6);
        assertThat(result.checkItems().get(0).ckgItmC()).isEqualTo("MGMT_STR");
        assertThat(result.checkItems().get(5).ckgItmC()).isEqualTo("ETC");
    }
}
