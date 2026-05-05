package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.council.dto.CouncilDto;

/**
 * CouncilApprovalEventListener 단위 테스트
 *
 * <p>
 * 결재 완료 이벤트 처리 메서드(handleApprovalCompleted)를 검증합니다.
 * Cappla 엔티티는 Mockito.mock()으로 생성합니다.
 * CouncilApprovalService·ApplicationMapRepository는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 * <p>커버리지 60% 달성을 위해 추가 (2026-04-29)</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilApprovalEventListenerTest {

    @Mock
    private ApplicationMapRepository applicationMapRepository;

    @Mock
    private CouncilApprovalService councilApprovalService;

    @InjectMocks
    private CouncilApprovalEventListener councilApprovalEventListener;

    private static final String APF_MNG_NO = "APF_202600000001";
    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // handleApprovalCompleted — 협의회 무관 신청서 (BASCTM 연결 없음)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: BASCTM 연결이 없으면 councilApprovalService를 호출하지 않는다")
    void handleApprovalCompleted_연결없음_처리건너뜀() {
        // given: BASCTM 연결 없음
        given(applicationMapRepository.findByApfMngNoAndOrcTbCd(APF_MNG_NO, "BASCTM"))
                .willReturn(List.of());

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when
        councilApprovalEventListener.handleApprovalCompleted(event);

        // then: councilApprovalService 호출되지 않아야 함
        verify(councilApprovalService, never())
                .processApprovalCallback(any(), any());
    }

    // ───────────────────────────────────────────────────────
    // handleApprovalCompleted — 결재완료 이벤트 → 협의회 상태 전이
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 결재완료 이벤트 수신 시 approved=true로 콜백을 호출한다")
    void handleApprovalCompleted_결재완료_approved_true콜백() {
        // given: BASCTM 연결 1건 설정
        Cappla link = mock(Cappla.class);
        given(link.getOrcPkVl()).willReturn(ASCT_ID);
        given(applicationMapRepository.findByApfMngNoAndOrcTbCd(APF_MNG_NO, "BASCTM"))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when
        councilApprovalEventListener.handleApprovalCompleted(event);

        // then: approved=true로 콜백 호출
        verify(councilApprovalService).processApprovalCallback(
                eq(ASCT_ID),
                eq(new CouncilDto.ApprovalCallbackRequest(true)));
    }

    @Test
    @DisplayName("handleApprovalCompleted: 반려 이벤트 수신 시 approved=false로 콜백을 호출한다")
    void handleApprovalCompleted_반려_approved_false콜백() {
        // given: BASCTM 연결 1건 설정
        Cappla link = mock(Cappla.class);
        given(link.getOrcPkVl()).willReturn(ASCT_ID);
        given(applicationMapRepository.findByApfMngNoAndOrcTbCd(APF_MNG_NO, "BASCTM"))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "반려");

        // when
        councilApprovalEventListener.handleApprovalCompleted(event);

        // then: approved=false로 콜백 호출
        verify(councilApprovalService).processApprovalCallback(
                eq(ASCT_ID),
                eq(new CouncilDto.ApprovalCallbackRequest(false)));
    }

    // ───────────────────────────────────────────────────────
    // handleApprovalCompleted — 콜백 실패 시 예외 전파
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 콜백 처리 중 예외가 발생하면 예외를 다시 던진다")
    void handleApprovalCompleted_콜백실패_예외전파() {
        // given: BASCTM 연결 1건, 콜백 시 예외 발생
        Cappla link = mock(Cappla.class);
        given(link.getOrcPkVl()).willReturn(ASCT_ID);
        given(applicationMapRepository.findByApfMngNoAndOrcTbCd(APF_MNG_NO, "BASCTM"))
                .willReturn(List.of(link));
        // void 메서드는 given(...).willThrow(...) 대신 willThrow(...).given(...) 형태로 스텁합니다.
        willThrow(new RuntimeException("상태 전이 실패"))
                .given(councilApprovalService).processApprovalCallback(any(), any());

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when & then: 예외가 다시 전파되어야 함
        assertThatThrownBy(
                () -> councilApprovalEventListener.handleApprovalCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("상태 전이 실패");
    }
}
