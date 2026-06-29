package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bpovwm;
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
    @Mock private PerformanceRepository performanceRepository;
    @Mock private CouncilService councilService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private FeasibilityService feasibilityService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // getFeasibility
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getFeasibility: 사업개요 미작성이면 null을 반환한다")
    void getFeasibility_미작성상태_null반환() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

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
        given(overview.getKpnTpTc()).willReturn("02");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(overview));
        given(performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(ASCT_ID, "N")).willReturn(List.of());

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
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "02", null, null);

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
                "02", null, "FL_00000001");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        feasibilityService.saveFeasibility(ASCT_ID, request);

        verify(councilService).changeStatus(ASCT_ID, "02");
    }

    @Test
    @DisplayName("saveFeasibility: TEMP 타입이면 상태 전이를 수행하지 않는다")
    void saveFeasibility_TEMP_상태전이없음() {
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, "N", null, null,
                "01", null, null);
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

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

        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "수정된사업명", "2027", null, null, null, null, "N", null, null,
                "01", null, null);

        // when
        feasibilityService.saveFeasibility(ASCT_ID, request);

        // then: 신규 INSERT가 아닌 update() 호출, save()는 호출되지 않음
        verify(existing).update(
                "수정된사업명", "2027", null, null, null, null, "N", null, null, "01", null);
        verify(projectOverviewRepository, never()).save(any());
    }




    @Test
    @DisplayName("saveFeasibility: 성과지표가 있으면 기존 성과지표를 삭제하고 새 지표를 persist한다")
    void saveFeasibility_성과지표있음_교체저장() {
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());
        ReflectionTestUtils.setField(feasibilityService, "entityManager", entityManager);
        Query deleteQuery = mock(Query.class);
        given(entityManager.createQuery("DELETE FROM Bperfm b WHERE b.itPtlAsctId = :asctId")).willReturn(deleteQuery);
        given(deleteQuery.setParameter("asctId", ASCT_ID)).willReturn(deleteQuery);
        given(deleteQuery.executeUpdate()).willReturn(1);
        List<CouncilDto.PerformanceRequest> performances = List.of(
                new CouncilDto.PerformanceRequest(1, "성과지표", "내용", "정량", "분기", "자동"));
        CouncilDto.FeasibilityRequest request = new CouncilDto.FeasibilityRequest(
                "테스트사업", "2026", null, null, null, null, null, null, null,
                "01", performances, null);

        feasibilityService.saveFeasibility(ASCT_ID, request);

        // P1 #2: DELETE 실행(executeUpdate) 직후, 신규 persist 이전에 flush가 호출되어야
        // DELETE→INSERT 순서가 보장된다(PK 충돌/유령 행 방지).
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(deleteQuery, entityManager);
        inOrder.verify(deleteQuery).executeUpdate();
        inOrder.verify(entityManager).flush();
        inOrder.verify(entityManager).persist(any(com.kdb.it.domain.council.entity.Bperfm.class));
    }

    @Test
    @DisplayName("getFeasibility: 성과지표 엔티티를 응답 DTO로 변환한다")
    void getFeasibility_점검성과지표있음_DTO변환() {
        Bpovwm overview = mock(Bpovwm.class);
        given(overview.getAbusNm()).willReturn("성과사업");
        given(overview.getLwRglYn()).willReturn("Y");
        given(overview.getKpnTpTc()).willReturn("01");
        given(projectOverviewRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(overview));
        com.kdb.it.domain.council.entity.Bperfm perf = mock(com.kdb.it.domain.council.entity.Bperfm.class);
        given(perf.getEvlDtpSno()).willReturn(1);
        given(perf.getEvlDtpNm()).willReturn("성과지표");
        given(performanceRepository.findByItPtlAsctIdAndDelYnOrderByEvlDtpSnoAsc(ASCT_ID, "N")).willReturn(List.of(perf));

        CouncilDto.FeasibilityResponse result = feasibilityService.getFeasibility(ASCT_ID);

        assertThat(result.performances()).hasSize(1);
        assertThat(result.performances().get(0).dtpNm()).isEqualTo("성과지표");
    }
}
