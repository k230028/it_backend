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
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Brsltm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ResultRepository;

/**
 * ResultService 단위 테스트
 *
 * <p>
 * 협의회 결과서 서비스의 조회·저장·확정 메서드를 검증합니다.
 * Basctm·Brsltm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CouncilService·EvaluationService는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultServiceTest {

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private CouncilService councilService;

    @Mock
    private EvaluationService evaluationService;

    @Mock
    private CommitteeRepository committeeRepository;

    @InjectMocks
    private ResultService resultService;

    // ───────────────────────────────────────────────────────
    // getResult
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getResult: 결과서가 아직 없으면 avgScores만 채워진 빈 DTO를 반환한다")
    void getResult_결과서없음_빈DTO반환() {
        // given
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N")).willReturn(Optional.empty());
        given(evaluationService.buildAvgScores("ASCT-2026-0001")).willReturn(List.of());

        // when
        CouncilDto.ResultResponse result = resultService.getResult("ASCT-2026-0001");

        // then
        assertThat(result.synOpnn()).isNull();
        assertThat(result.ckgOpnn()).isNull();
        assertThat(result.flMngNo()).isNull();
        assertThat(result.avgScores()).isEmpty();
    }

    @Test
    @DisplayName("getResult: 결과서가 있으면 내용을 DTO로 반환한다")
    void getResult_결과서있음_DTO반환() {
        // given
        Basctm council = mock(Basctm.class);
        Brsltm resultEntity = mock(Brsltm.class);
        given(resultEntity.getSynOpnn()).willReturn("종합의견");
        given(resultEntity.getCkgOpnn()).willReturn("타당성검토의견");
        given(resultEntity.getFlMpnId()).willReturn("FL_00000001");

        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.of(resultEntity));
        given(evaluationService.buildAvgScores("ASCT-2026-0001")).willReturn(List.of());

        // when
        CouncilDto.ResultResponse result = resultService.getResult("ASCT-2026-0001");

        // then
        assertThat(result.synOpnn()).isEqualTo("종합의견");
        assertThat(result.ckgOpnn()).isEqualTo("타당성검토의견");
        assertThat(result.flMngNo()).isEqualTo("FL_00000001");
    }

    // ───────────────────────────────────────────────────────
    // saveResult
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveResult: 기존 결과서가 있으면 update()를 호출한다")
    void saveResult_기존결과서있음_update호출() {
        // given
        Basctm council = mock(Basctm.class);
        Brsltm existingResult = mock(Brsltm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.of(existingResult));

        // when
        resultService.saveResult("ASCT-2026-0001",
                new CouncilDto.ResultRequest("종합의견", "타당성의견", null));

        // then
        verify(existingResult).update("종합의견", "타당성의견", null);
        verify(resultRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveResult: 결과서가 없으면 신규 저장한다")
    void saveResult_결과서없음_신규저장() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N")).willReturn(Optional.empty());

        // when
        resultService.saveResult("ASCT-2026-0001",
                new CouncilDto.ResultRequest("종합의견", "타당성의견", null));

        // then
        verify(resultRepository).save(any(Brsltm.class));
    }

    @Test
    @DisplayName("saveResult: 협의회 상태가 EVALUATING이면 RESULT_WRITING으로 상태를 전이한다")
    void saveResult_EVALUATING상태_RESULT_WRITING전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N")).willReturn(Optional.empty());

        // when
        resultService.saveResult("ASCT-2026-0001",
                new CouncilDto.ResultRequest("종합의견", "타당성의견", null));

        // then
        verify(councilService).changeStatus("ASCT-2026-0001", "09");
    }

    // ───────────────────────────────────────────────────────
    // confirmResult
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmResult: 결과서가 없으면 IllegalStateException을 던진다")
    void confirmResult_결과서없음_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resultService.confirmResult("ASCT-2026-0001"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결과서");
    }

    @Test
    @DisplayName("confirmResult: 결과서가 있으면 RESULT_REVIEW로 상태를 전이한다")
    void confirmResult_결과서있음_RESULT_REVIEW전이() {
        // given
        Basctm council = mock(Basctm.class);
        Brsltm resultEntity = mock(Brsltm.class);
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.of(resultEntity));

        // when
        resultService.confirmResult("ASCT-2026-0001");

        // then
        verify(councilService).changeStatus("ASCT-2026-0001", "10");
    }

    // ───────────────────────────────────────────────────────
    // saveResult — 추가 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveResult: 이미 RESULT_WRITING 상태이면 중복 상태 전이를 수행하지 않는다")
    void saveResult_RESULT_WRITING상태_상태전이없음() {
        // given: 이미 RESULT_WRITING 상태 — 중복 전이 방지 시나리오
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.empty());

        // when
        resultService.saveResult("ASCT-2026-0001",
                new CouncilDto.ResultRequest("종합의견", "타당성의견", null));

        // then: EVALUATING 상태가 아니므로 changeStatus 호출 없음
        verify(councilService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("saveResult: 결과서 신규 저장 시 resultRepository.save()가 호출된다")
    void saveResult_신규저장시_save호출() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.empty());

        // when
        resultService.saveResult("ASCT-2026-0001",
                new CouncilDto.ResultRequest("종합의견", "타당성의견", "FL_00000001"));

        // then: 신규 저장 및 EVALUATING → RESULT_WRITING 전이
        verify(resultRepository).save(any(Brsltm.class));
        verify(councilService).changeStatus("ASCT-2026-0001", "09");
    }

    // ───────────────────────────────────────────────────────
    // getResult — 추가 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getResult: avgScores 목록이 있으면 DTO에 포함되어 반환된다")
    void getResult_avgScores있음_DTO포함반환() {
        // given: 평가위원의 항목별 평균점수 2건 존재
        Basctm council = mock(Basctm.class);
        List<CouncilDto.CheckItemAvgScore> avgScores = List.of(
                new CouncilDto.CheckItemAvgScore("MGMT_STR", "경영전략/계획 부합", 4.5),
                new CouncilDto.CheckItemAvgScore("FIN_EFC", "재무 효과", 3.0)
        );
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(resultRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(Optional.empty());
        given(evaluationService.buildAvgScores("ASCT-2026-0001")).willReturn(avgScores);

        // when
        CouncilDto.ResultResponse result = resultService.getResult("ASCT-2026-0001");

        // then: avgScores 2건 포함, 첫 번째 항목 코드·점수 검증
        assertThat(result.avgScores()).hasSize(2);
        assertThat(result.avgScores().get(0).ckgItmC()).isEqualTo("MGMT_STR");
        assertThat(result.avgScores().get(0).avgScore()).isEqualTo(4.5);
        // 결과서 미작성이므로 내용 필드는 null
        assertThat(result.synOpnn()).isNull();
    }

    // ───────────────────────────────────────────────────────
    // reviewResult / getMyReviewStatus
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("reviewResult: RESULT_REVIEW가 아니면 IllegalStateException을 던진다")
    void reviewResult_RESULT_REVIEW아님_예외발생() {
        Basctm council = mock(Basctm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);

        assertThatThrownBy(() -> resultService.reviewResult("ASCT-2026-0001", user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");
    }

    @Test
    @DisplayName("reviewResult: 협의회 평가위원이 아니면 SecurityException을 던진다")
    void reviewResult_평가위원아님_예외발생() {
        Basctm council = mock(Basctm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-2026-0001", "10001", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> resultService.reviewResult("ASCT-2026-0001", user))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("평가위원");
    }

    @Test
    @DisplayName("reviewResult: 간사는 결과서 검토 확인 대상이 아니다")
    void reviewResult_간사_예외발생() {
        Basctm council = mock(Basctm.class);
        Bcmmtm secretary = mock(Bcmmtm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-2026-0001", "10001", "N"))
                .willReturn(Optional.of(secretary));

        assertThatThrownBy(() -> resultService.reviewResult("ASCT-2026-0001", user))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("간사");
    }

    @Test
    @DisplayName("reviewResult: 일부 평가위원이 미확인 상태이면 상태 전이를 하지 않는다")
    void reviewResult_일부미확인_상태전이없음() {
        Basctm council = mock(Basctm.class);
        Bcmmtm currentMember = mock(Bcmmtm.class);
        Bcmmtm waitingMember = mock(Bcmmtm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(currentMember.getItPtlAsctMebTc()).willReturn("01");
        given(currentMember.getCnfmYn()).willReturn("Y");
        given(waitingMember.getItPtlAsctMebTc()).willReturn("02");
        given(waitingMember.getCnfmYn()).willReturn("N");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-2026-0001", "10001", "N"))
                .willReturn(Optional.of(currentMember));
        given(committeeRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(List.of(currentMember, waitingMember));

        resultService.reviewResult("ASCT-2026-0001", user);

        verify(currentMember).confirmReview();
        verify(councilService, never()).changeStatus("ASCT-2026-0001", "11");
    }

    @Test
    @DisplayName("reviewResult: 모든 평가위원이 확인 완료되면 FINAL_APPROVAL로 전이한다")
    void reviewResult_전원확인_FINAL_APPROVAL전이() {
        Basctm council = mock(Basctm.class);
        Bcmmtm mandMember = mock(Bcmmtm.class);
        Bcmmtm callMember = mock(Bcmmtm.class);
        Bcmmtm secretary = mock(Bcmmtm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(mandMember.getItPtlAsctMebTc()).willReturn("01");
        given(mandMember.getCnfmYn()).willReturn("Y");
        given(callMember.getItPtlAsctMebTc()).willReturn("02");
        given(callMember.getCnfmYn()).willReturn("Y");
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(secretary.getCnfmYn()).willReturn("N");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-2026-0001", "10001", "N"))
                .willReturn(Optional.of(mandMember));
        given(committeeRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(List.of(mandMember, callMember, secretary));

        resultService.reviewResult("ASCT-2026-0001", user);

        verify(councilService).changeStatus("ASCT-2026-0001", "11");
    }

    @Test
    @DisplayName("syncReviewStatus: RESULT_REVIEW가 아니면 전이하지 않고 false를 반환한다")
    void syncReviewStatus_RESULT_REVIEW아님_false반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("09");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);

        boolean result = resultService.syncReviewStatus("ASCT-2026-0001");

        assertThat(result).isFalse();
        verify(councilService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("syncReviewStatus: 평가위원이 없으면 전이하지 않고 false를 반환한다")
    void syncReviewStatus_평가위원없음_false반환() {
        Basctm council = mock(Basctm.class);
        Bcmmtm secretary = mock(Bcmmtm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(List.of(secretary));

        boolean result = resultService.syncReviewStatus("ASCT-2026-0001");

        assertThat(result).isFalse();
        verify(councilService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("syncReviewStatus: 일부 평가위원이 미확인 상태이면 false를 반환한다")
    void syncReviewStatus_일부미확인_false반환() {
        Basctm council = mock(Basctm.class);
        Bcmmtm confirmed = mock(Bcmmtm.class);
        Bcmmtm waiting = mock(Bcmmtm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(confirmed.getItPtlAsctMebTc()).willReturn("01");
        given(confirmed.getCnfmYn()).willReturn("Y");
        given(waiting.getItPtlAsctMebTc()).willReturn("02");
        given(waiting.getCnfmYn()).willReturn("N");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(List.of(confirmed, waiting));

        boolean result = resultService.syncReviewStatus("ASCT-2026-0001");

        assertThat(result).isFalse();
        verify(councilService, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("syncReviewStatus: 전체 평가위원 확인 완료이면 FINAL_APPROVAL로 전이하고 true를 반환한다")
    void syncReviewStatus_전원확인_true반환() {
        Basctm council = mock(Basctm.class);
        Bcmmtm mandMember = mock(Bcmmtm.class);
        Bcmmtm callMember = mock(Bcmmtm.class);
        Bcmmtm secretary = mock(Bcmmtm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("10");
        given(mandMember.getItPtlAsctMebTc()).willReturn("01");
        given(mandMember.getCnfmYn()).willReturn("Y");
        given(callMember.getItPtlAsctMebTc()).willReturn("02");
        given(callMember.getCnfmYn()).willReturn("Y");
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(councilService.findActiveCouncil("ASCT-2026-0001")).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndDelYn("ASCT-2026-0001", "N"))
                .willReturn(List.of(mandMember, callMember, secretary));

        boolean result = resultService.syncReviewStatus("ASCT-2026-0001");

        assertThat(result).isTrue();
        verify(councilService).changeStatus("ASCT-2026-0001", "11");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getMyReviewStatus: 본인 확인 여부를 반환하고 없으면 false를 반환한다")
    void getMyReviewStatus_확인여부반환() {
        Bcmmtm member = mock(Bcmmtm.class);
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "BBR001");
        given(member.getCnfmYn()).willReturn("Y");
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn("ASCT-2026-0001", "10001", "N"))
                .willReturn(Optional.of(member), Optional.empty());

        assertThat(resultService.getMyReviewStatus("ASCT-2026-0001", user)).isTrue();
        assertThat(resultService.getMyReviewStatus("ASCT-2026-0001", user)).isFalse();
    }
}
