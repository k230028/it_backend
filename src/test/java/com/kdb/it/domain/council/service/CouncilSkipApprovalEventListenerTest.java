package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

/**
 * CouncilSkipApprovalEventListener 단위 테스트.
 *
 * <p>결재 완료 이벤트 처리 메서드({@code handleApprovalCompleted})의 분기를 검증합니다.
 * {@link Cappla} 엔티티는 {@code Mockito.mock()}으로 생성합니다.
 * {@link CouncilSkipService}·{@link ApplicationMapRepository}는 {@code @Mock}으로 교체합니다.
 * Oracle DB 없이 실행됩니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilSkipApprovalEventListenerTest {

    @Mock
    private ApplicationMapRepository applicationMapRepository;

    @Mock
    private CouncilSkipService councilSkipService;

    @InjectMocks
    private CouncilSkipApprovalEventListener listener;

    /** 테스트용 신청서 식별번호 */
    private static final String APF_MNG_NO = "APF-2026-00000001";
    /** 테스트용 협의회 ID */
    private static final String ASCT_ID_A = "ASCT-2026-0001";
    /** 두 번째 협의회 ID (복수 링크 케이스용) */
    private static final String ASCT_ID_B = "ASCT-2026-0002";

    // ─────────────────────────────────────────────────────────────────
    // 케이스 1: BASKPM 연결 없음 → 조기 반환, 서비스 미호출
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: BASKPM 연결이 없으면 CouncilSkipService를 호출하지 않는다")
    void handleApprovalCompleted_연결없음_처리건너뜀() {
        // given: BASKPM 연결 없음
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of());

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when
        listener.handleApprovalCompleted(event);

        // then: CouncilSkipService 호출되지 않아야 한다
        verify(councilSkipService, never()).handleApprovalCompleted(any(), anyBoolean());
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 2: 결재완료 이벤트 → approved=true 로 콜백 호출
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 결재완료 이벤트 수신 시 approved=true로 콜백을 호출한다")
    void handleApprovalCompleted_결재완료_approved_true() {
        // given: BASKPM 연결 1건
        Cappla link = mock(Cappla.class);
        given(link.getPkColNm()).willReturn(ASCT_ID_A);
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when
        listener.handleApprovalCompleted(event);

        // then: approved=true 로 서비스 콜백 호출
        verify(councilSkipService).handleApprovalCompleted(eq(ASCT_ID_A), eq(true));
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 3: 반려 이벤트 → approved=false 로 콜백 호출
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 반려 이벤트 수신 시 approved=false로 콜백을 호출한다")
    void handleApprovalCompleted_반려_approved_false() {
        // given: BASKPM 연결 1건, 상태 '반려'
        Cappla link = mock(Cappla.class);
        given(link.getPkColNm()).willReturn(ASCT_ID_A);
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "반려");

        // when
        listener.handleApprovalCompleted(event);

        // then: approved=false 로 서비스 콜백 호출
        verify(councilSkipService).handleApprovalCompleted(eq(ASCT_ID_A), eq(false));
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 4: "결재완료"가 아닌 임의 상태 → approved=false
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: '결재완료' 이외의 상태는 approved=false로 처리된다")
    void handleApprovalCompleted_기타상태_approved_false() {
        // given: 상태값이 결재완료도 반려도 아닌 임의 문자열
        Cappla link = mock(Cappla.class);
        given(link.getPkColNm()).willReturn(ASCT_ID_A);
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "진행중");

        // when
        listener.handleApprovalCompleted(event);

        // then: "결재완료"가 아니므로 approved=false
        verify(councilSkipService).handleApprovalCompleted(eq(ASCT_ID_A), eq(false));
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 5: 복수 링크 → 각 asctId 개별 처리
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 복수 BASKPM 연결 시 각 asctId에 대해 콜백을 호출한다")
    void handleApprovalCompleted_복수링크_각각처리() {
        // given: BASKPM 연결 2건
        Cappla linkA = mock(Cappla.class);
        given(linkA.getPkColNm()).willReturn(ASCT_ID_A);
        Cappla linkB = mock(Cappla.class);
        given(linkB.getPkColNm()).willReturn(ASCT_ID_B);

        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(linkA, linkB));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when
        listener.handleApprovalCompleted(event);

        // then: 두 asctId 각각에 대해 approved=true 로 서비스 콜백 호출
        verify(councilSkipService, times(1)).handleApprovalCompleted(eq(ASCT_ID_A), eq(true));
        verify(councilSkipService, times(1)).handleApprovalCompleted(eq(ASCT_ID_B), eq(true));
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 6: 서비스 예외 발생 → 예외 재전파
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: 콜백 처리 중 예외가 발생하면 예외를 다시 던진다")
    void handleApprovalCompleted_콜백실패_예외전파() {
        // given: BASKPM 연결 1건, 콜백에서 예외 발생
        Cappla link = mock(Cappla.class);
        given(link.getPkColNm()).willReturn(ASCT_ID_A);
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(link));

        willThrow(new RuntimeException("생략 판정 처리 실패"))
                .given(councilSkipService).handleApprovalCompleted(any(), anyBoolean());

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, "결재완료");

        // when & then: 예외가 다시 전파되어야 한다
        assertThatThrownBy(() -> listener.handleApprovalCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("생략 판정 처리 실패");
    }

    // ─────────────────────────────────────────────────────────────────
    // 케이스 7: newStatus가 null → "결재완료"와 다르므로 approved=false
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleApprovalCompleted: newStatus가 null이면 approved=false로 처리한다")
    void handleApprovalCompleted_null상태_approved_false() {
        // given: 상태값 null (비정상 이벤트 방어)
        Cappla link = mock(Cappla.class);
        given(link.getPkColNm()).willReturn(ASCT_ID_A);
        given(applicationMapRepository.findByApfDcmNoAndFntTbNm(APF_MNG_NO, CouncilSkipService.ORC_TB_CD))
                .willReturn(List.of(link));

        ApprovalCompletedEvent event = new ApprovalCompletedEvent(APF_MNG_NO, null);

        // when
        listener.handleApprovalCompleted(event);

        // then: null은 "결재완료"가 아니므로 approved=false
        verify(councilSkipService).handleApprovalCompleted(eq(ASCT_ID_A), eq(false));
    }
}
