package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Baskpm;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.repository.BaskpmRepository;

/**
 * CouncilSkipService 단위 테스트 (PRD_c_20260620 #3)
 *
 * <p>협의회 타당성검토 생략 판정 워크플로우 서비스의 전체 퍼블릭 메서드,
 * 분기, 권한·상태 검증, 통보 이벤트 발행 흐름을 검증합니다.
 * Baskpm·Basctm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilSkipServiceTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 의존성 Mock
    // ─────────────────────────────────────────────────────────────────────────

    @Mock
    private BaskpmRepository baskpmRepository;

    @Mock
    private CouncilService councilService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CouncilSkipService councilSkipService;

    // ─────────────────────────────────────────────────────────────────────────
    // 테스트 픽스처 상수
    // ─────────────────────────────────────────────────────────────────────────

    private static final String ASCT_ID    = "ASCT-2026-0001";
    private static final String APF_MNG_NO = "APF-2026-00000001";

    // ─────────────────────────────────────────────────────────────────────────
    // 헬퍼 팩토리
    // ─────────────────────────────────────────────────────────────────────────

    /** 정보보호관리자(ITPAD002) CustomUserDetails 생성 */
    private CustomUserDetails infoSecAdminUser() {
        return new CustomUserDetails("E10001", List.of("ITPAD002"), "DEPT01");
    }

    /** IT관리자(ITPAD001) CustomUserDetails 생성 */
    private CustomUserDetails adminUser() {
        return new CustomUserDetails("E20001", List.of("ITPAD001"), "DEPT02");
    }

    /** 일반 사용자 CustomUserDetails 생성 */
    private CustomUserDetails normalUser() {
        return new CustomUserDetails("E30001", List.of("ITPZZ001"), "DEPT03");
    }

    /**
     * 정보보호시스템(04) + 결재완료(04) 협의회 목(mock) 생성.
     * 생략 판정 요청 등록이 가능한 '정상' 상태.
     */
    private Basctm validCouncil() {
        Basctm c = mock(Basctm.class);
        given(c.getItPtlAsctDbrTc()).willReturn("04");
        given(c.getItPtlAsctPrgStsTc()).willReturn("04");
        return c;
    }

    /**
     * 생략판정요청 엔티티 목(mock) 생성 — 미판정(cnfmDtm=null) 상태.
     *
     * @param rqsUsid 신청자 사번
     * @param omtYn   생략여부 (판정 전이면 null)
     */
    private Baskpm pendingBaskpm(String rqsUsid, String omtYn) {
        Baskpm b = mock(Baskpm.class);
        given(b.getRqsUsid()).willReturn(rqsUsid);
        given(b.getCnfmDtm()).willReturn(null);          // 미판정 상태
        given(b.getPrtyIvgOmtYn()).willReturn(omtYn);
        given(b.getItPtlAsctId()).willReturn(ASCT_ID);
        given(b.getPrtyIvgOmtRsnTc()).willReturn("01");
        given(b.getCgprOpnnCone()).willReturn("생략 사유");
        given(b.getFlMpnId()).willReturn("FL-0001");
        given(b.getRqsDtm()).willReturn(LocalDateTime.of(2026, 6, 20, 9, 0));
        given(b.getCgprRpdCone()).willReturn(null);
        given(b.getCnfmUsid()).willReturn(null);
        given(b.getApfMngNo()).willReturn(null);
        given(b.getDelYn()).willReturn("N");
        return b;
    }

    /**
     * 생략판정요청 엔티티 목(mock) 생성 — 판정 완료(cnfmDtm 설정) 상태.
     *
     * @param rqsUsid 신청자 사번
     * @param omtYn   생략여부 (Y/N)
     */
    private Baskpm decidedBaskpm(String rqsUsid, String omtYn) {
        Baskpm b = mock(Baskpm.class);
        given(b.getRqsUsid()).willReturn(rqsUsid);
        given(b.getCnfmDtm()).willReturn(LocalDateTime.of(2026, 6, 21, 10, 0)); // 판정 완료 상태
        given(b.getPrtyIvgOmtYn()).willReturn(omtYn);
        given(b.getItPtlAsctId()).willReturn(ASCT_ID);
        given(b.getPrtyIvgOmtRsnTc()).willReturn("02");
        given(b.getCgprOpnnCone()).willReturn("확인 완료");
        given(b.getFlMpnId()).willReturn("FL-0002");
        given(b.getRqsDtm()).willReturn(LocalDateTime.of(2026, 6, 20, 9, 0));
        given(b.getCgprRpdCone()).willReturn("판정 응답");
        given(b.getCnfmUsid()).willReturn("E20001");
        given(b.getApfMngNo()).willReturn(APF_MNG_NO);
        given(b.getDelYn()).willReturn("N");
        return b;
    }

    /** SkipRequestCreate 요청 레코드 생성 */
    private CouncilDto.SkipRequestCreate skipRequestCreate() {
        return new CouncilDto.SkipRequestCreate("01", "생략 요청 사유 설명", "FL-0001");
    }

    /** SkipDecisionRequest 요청 레코드 생성 (유효한 결재선 포함) */
    private CouncilDto.SkipDecisionRequest skipDecisionRequest(String omtYn) {
        return new CouncilDto.SkipDecisionRequest(omtYn, "확인 사유", List.of("E21001", "E21002"));
    }

    // =========================================================================
    // createSkipRequest
    // =========================================================================

    @Nested
    @DisplayName("createSkipRequest — 생략 판정 요청 등록")
    class CreateSkipRequestTests {

        @Test
        @DisplayName("정상 경로: 정보보호시스템(04)·결재완료(04) 협의회에 정보보호관리자가 요청하면 Baskpm을 persist한다")
        void createSkipRequest_정상_persist호출() {
            // Arrange
            Basctm council = validCouncil();
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

            CustomUserDetails user = infoSecAdminUser();

            // Act
            councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), user);

            // Assert: EntityManager.persist()로 Baskpm이 저장됨
            verify(entityManager).persist(any(Baskpm.class));
        }

        @Test
        @DisplayName("실패: 심의유형이 정보보호시스템(04)이 아니면 IllegalStateException을 던진다")
        void createSkipRequest_심의유형_비정보보호_IllegalStateException() {
            // Arrange: 심의유형 03 (정보보호시스템 아님)
            Basctm council = mock(Basctm.class);
            given(council.getItPtlAsctDbrTc()).willReturn("03");
            given(council.getItPtlAsctPrgStsTc()).willReturn("04");
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), infoSecAdminUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정보보호시스템(04)");
        }

        @Test
        @DisplayName("실패: 협의회 상태가 결재완료(04)가 아니면 IllegalStateException을 던진다")
        void createSkipRequest_상태_비결재완료_IllegalStateException() {
            // Arrange: 결재완료 아닌 상태(01)
            Basctm council = mock(Basctm.class);
            given(council.getItPtlAsctDbrTc()).willReturn("04");
            given(council.getItPtlAsctPrgStsTc()).willReturn("01");
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), infoSecAdminUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재완료(04)")
                .hasMessageContaining("01");  // 현재 상태 코드 포함 여부 확인
        }

        @Test
        @DisplayName("실패: 정보보호관리자(ITPAD002)가 아닌 일반 사용자는 AccessDeniedException을 던진다")
        void createSkipRequest_권한없음_AccessDeniedException() {
            // Arrange: 정상 협의회 상태지만 일반 사용자
            Basctm council = validCouncil();
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), normalUser()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ITPAD002");
        }

        @Test
        @DisplayName("실패: IT관리자(ITPAD001)도 생략 판정 요청 등록 권한이 없다 (ITPAD002 전용)")
        void createSkipRequest_IT관리자도_AccessDeniedException() {
            // Arrange: 정상 협의회 상태지만 IT관리자(isInfoSecAdmin=false)
            Basctm council = validCouncil();
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), adminUser()))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("실패: 이미 진행 중인 생략 판정 요청이 있으면 IllegalStateException을 던진다")
        void createSkipRequest_중복요청_IllegalStateException() {
            // Arrange: 기존 미삭제 요청 존재
            Basctm council = validCouncil();
            given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
            // Mockito 중첩 스터빙(UnfinishedStubbingException) 회피: 헬퍼 호출을 먼저 지역변수로 분리
            Baskpm existing = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(existing));

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.createSkipRequest(ASCT_ID, skipRequestCreate(), infoSecAdminUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 진행 중인");
        }
    }

    // =========================================================================
    // submitDecision
    // =========================================================================

    @Nested
    @DisplayName("submitDecision — 생략여부 판정 및 전자결재 상신")
    class SubmitDecisionTests {

        @Test
        @DisplayName("정상 경로(생략Y): IT관리자가 미판정 요청에 생략 판정 후 결재를 상신한다")
        void submitDecision_정상생략Y_결재상신() {
            // Arrange
            Baskpm baskpm = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));
            given(applicationService.submit(any(ApplicationDto.CreateRequest.class)))
                .willReturn(APF_MNG_NO);

            // Act
            councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("Y"), adminUser());

            // Assert
            verify(applicationService).submit(any(ApplicationDto.CreateRequest.class));
            verify(baskpm).submitForDecision("Y", "확인 사유", "E20001", APF_MNG_NO);
        }

        @Test
        @DisplayName("정상 경로(개최N): IT관리자가 미판정 요청에 개최 판정 후 결재를 상신한다")
        void submitDecision_정상개최N_결재상신() {
            // Arrange
            Baskpm baskpm = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));
            given(applicationService.submit(any(ApplicationDto.CreateRequest.class)))
                .willReturn(APF_MNG_NO);

            // Act
            councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("N"), adminUser());

            // Assert
            verify(baskpm).submitForDecision("N", "확인 사유", "E20001", APF_MNG_NO);
        }

        @Test
        @DisplayName("실패: IT관리자(ITPAD001)가 아닌 사용자는 AccessDeniedException을 던진다")
        void submitDecision_권한없음_AccessDeniedException() {
            // Act & Assert: repository 조회 전 권한 체크
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("Y"), normalUser()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ITPAD001");
        }

        @Test
        @DisplayName("실패: 정보보호관리자(ITPAD002)도 판정 권한이 없다 (ITPAD001 전용)")
        void submitDecision_정보보호관리자_AccessDeniedException() {
            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("N"), infoSecAdminUser()))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("실패: 생략 판정 요청이 존재하지 않으면 IllegalArgumentException을 던진다")
        void submitDecision_요청없음_IllegalArgumentException() {
            // Arrange: 미존재
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("Y"), adminUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ASCT_ID);
        }

        @Test
        @DisplayName("실패: 이미 판정·상신된 요청(cnfmDtm != null)이면 IllegalStateException을 던진다")
        void submitDecision_이미판정완료_IllegalStateException() {
            // Arrange: 판정 완료 상태
            Baskpm baskpm = decidedBaskpm("E10001", "Y");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, skipDecisionRequest("N"), adminUser()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 판정");
        }

        @Test
        @DisplayName("실패: 결재선이 빈 리스트이면 IllegalArgumentException을 던진다")
        void submitDecision_결재선빈리스트_IllegalArgumentException() {
            // Arrange: 미판정 요청은 있지만 결재선 없는 요청
            Baskpm baskpm = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            CouncilDto.SkipDecisionRequest emptyApprovers =
                new CouncilDto.SkipDecisionRequest("Y", "사유", List.of());

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, emptyApprovers, adminUser()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재선");
        }

        @Test
        @DisplayName("실패: 결재선이 null이면 IllegalArgumentException을 던진다")
        void submitDecision_결재선null_IllegalArgumentException() {
            // Arrange
            Baskpm baskpm = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            CouncilDto.SkipDecisionRequest nullApprovers =
                new CouncilDto.SkipDecisionRequest("Y", "사유", null);

            // Act & Assert
            assertThatThrownBy(() ->
                councilSkipService.submitDecision(ASCT_ID, nullApprovers, adminUser()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // handleApprovalCompleted
    // =========================================================================

    @Nested
    @DisplayName("handleApprovalCompleted — 전자결재 완료 콜백 처리")
    class HandleApprovalCompletedTests {

        @Test
        @DisplayName("콜백 대상 요청 없음: baskpm이 없으면 경고 로그만 남기고 조기 반환한다")
        void handleApprovalCompleted_요청없음_조기반환() {
            // Arrange: 대상 요청 없음
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

            // Act
            councilSkipService.handleApprovalCompleted(ASCT_ID, true);

            // Assert: 협의회 상태 변경·통보 미호출
            then(councilService).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("결재 반려(approved=false): 통보만 발행하고 협의회 상태 변경 없음")
        void handleApprovalCompleted_반려_통보만발행() {
            // Arrange
            Baskpm baskpm = pendingBaskpm("E10001", "Y");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            councilSkipService.handleApprovalCompleted(ASCT_ID, false);

            // Assert: 협의회 상태 변경 없음, 통보 이벤트 발행
            then(councilService).shouldHaveNoInteractions();
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("결재 완료 + 생략(Y): skipCouncil 호출 후 통보 발행")
        void handleApprovalCompleted_승인_생략Y_skipCouncil() {
            // Arrange: omtYn = "Y" (생략)
            Baskpm baskpm = pendingBaskpm("E10001", "Y");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            councilSkipService.handleApprovalCompleted(ASCT_ID, true);

            // Assert
            verify(councilService).skipCouncil(ASCT_ID);
            then(councilService).should(never()).startPreparation(any());
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("결재 완료 + 개최(N): startPreparation 호출 후 통보 발행")
        void handleApprovalCompleted_승인_개최N_startPreparation() {
            // Arrange: omtYn = "N" (개최)
            Baskpm baskpm = pendingBaskpm("E10001", "N");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            councilSkipService.handleApprovalCompleted(ASCT_ID, true);

            // Assert
            verify(councilService).startPreparation(ASCT_ID);
            then(councilService).should(never()).skipCouncil(any());
            verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("수신자 사번이 null이면 통보 이벤트를 발행하지 않는다")
        void handleApprovalCompleted_수신자null_통보미발행() {
            // Arrange: 수신자(rqsUsid) = null
            Baskpm baskpm = mock(Baskpm.class);
            given(baskpm.getRqsUsid()).willReturn(null);
            given(baskpm.getPrtyIvgOmtYn()).willReturn("Y");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act: 반려 시나리오
            councilSkipService.handleApprovalCompleted(ASCT_ID, false);

            // Assert: notify() 내부의 null 가드로 인해 publishEvent 미호출
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("수신자 사번이 공백 문자열이면 통보 이벤트를 발행하지 않는다")
        void handleApprovalCompleted_수신자공백_통보미발행() {
            // Arrange: 수신자(rqsUsid) = 공백 문자열
            Baskpm baskpm = mock(Baskpm.class);
            given(baskpm.getRqsUsid()).willReturn("   ");
            given(baskpm.getPrtyIvgOmtYn()).willReturn("N");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act: 반려 시나리오
            councilSkipService.handleApprovalCompleted(ASCT_ID, false);

            // Assert: isBlank() 처리로 publishEvent 미호출
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    // =========================================================================
    // getSkipRequest
    // =========================================================================

    @Nested
    @DisplayName("getSkipRequest — 단건 생략 판정 요청 조회")
    class GetSkipRequestTests {

        @Test
        @DisplayName("요청이 존재하면 SkipRequestResponse를 반환한다")
        void getSkipRequest_존재_응답반환() {
            // Arrange: 미판정 요청 존재
            Baskpm baskpm = pendingBaskpm("E10001", null);
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            CouncilDto.SkipRequestResponse result = councilSkipService.getSkipRequest(ASCT_ID);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.asctId()).isEqualTo(ASCT_ID);
            assertThat(result.rqsUsid()).isEqualTo("E10001");
            assertThat(result.decided()).isFalse(); // cnfmDtm=null → false
        }

        @Test
        @DisplayName("요청이 없으면 null을 반환한다")
        void getSkipRequest_없음_null반환() {
            // Arrange
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.empty());

            // Act
            CouncilDto.SkipRequestResponse result = councilSkipService.getSkipRequest(ASCT_ID);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("판정 완료(cnfmDtm != null) 요청이면 decided=true인 응답을 반환한다")
        void getSkipRequest_판정완료_decided_true() {
            // Arrange: 판정 완료 요청 (생략Y)
            Baskpm baskpm = decidedBaskpm("E10001", "Y");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            CouncilDto.SkipRequestResponse result = councilSkipService.getSkipRequest(ASCT_ID);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.decided()).isTrue();
            assertThat(result.omtYn()).isEqualTo("Y");
            assertThat(result.apfMngNo()).isEqualTo(APF_MNG_NO);
        }

        @Test
        @DisplayName("개최(N) 판정 완료 응답의 omtYn='N'을 확인한다")
        void getSkipRequest_개최판정완료_omtYnN() {
            // Arrange
            Baskpm baskpm = decidedBaskpm("E10001", "N");
            given(baskpmRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(Optional.of(baskpm));

            // Act
            CouncilDto.SkipRequestResponse result = councilSkipService.getSkipRequest(ASCT_ID);

            // Assert
            assertThat(result.omtYn()).isEqualTo("N");
            assertThat(result.decided()).isTrue();
        }
    }

    // =========================================================================
    // getActiveSkipRequests
    // =========================================================================

    @Nested
    @DisplayName("getActiveSkipRequests — 활성 생략 판정 요청 전체 조회")
    class GetActiveSkipRequestsTests {

        @Test
        @DisplayName("활성 요청이 있으면 delYn=N 필터링 후 목록을 반환한다")
        void getActiveSkipRequests_활성요청존재_목록반환() {
            // Arrange: delYn=N 요청 1건 + delYn=Y(삭제된) 요청 1건
            Baskpm active = pendingBaskpm("E10001", null);

            Baskpm deleted = mock(Baskpm.class);
            given(deleted.getDelYn()).willReturn("Y");

            given(baskpmRepository.findAll()).willReturn(List.of(active, deleted));

            // Act
            List<CouncilDto.SkipRequestResponse> result = councilSkipService.getActiveSkipRequests();

            // Assert: 삭제된 요청 제외, 활성 1건만
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rqsUsid()).isEqualTo("E10001");
        }

        @Test
        @DisplayName("모든 요청이 삭제되었으면 빈 목록을 반환한다")
        void getActiveSkipRequests_전체삭제_빈목록() {
            // Arrange
            Baskpm deleted = mock(Baskpm.class);
            given(deleted.getDelYn()).willReturn("Y");
            given(baskpmRepository.findAll()).willReturn(List.of(deleted));

            // Act
            List<CouncilDto.SkipRequestResponse> result = councilSkipService.getActiveSkipRequests();

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("저장된 요청이 없으면 빈 목록을 반환한다")
        void getActiveSkipRequests_요청없음_빈목록() {
            // Arrange
            given(baskpmRepository.findAll()).willReturn(List.of());

            // Act
            List<CouncilDto.SkipRequestResponse> result = councilSkipService.getActiveSkipRequests();

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("복수의 활성 요청을 모두 반환한다")
        void getActiveSkipRequests_복수활성요청_모두반환() {
            // Arrange: 활성 2건 (각각 다른 협의회ID)
            Baskpm b1 = pendingBaskpm("E10001", null);
            given(b1.getItPtlAsctId()).willReturn("ASCT-2026-0001");

            Baskpm b2 = pendingBaskpm("E10002", null);
            given(b2.getItPtlAsctId()).willReturn("ASCT-2026-0002");

            given(baskpmRepository.findAll()).willReturn(List.of(b1, b2));

            // Act
            List<CouncilDto.SkipRequestResponse> result = councilSkipService.getActiveSkipRequests();

            // Assert
            assertThat(result).hasSize(2);
        }
    }

    // =========================================================================
    // ORC_TB_CD 상수 검증
    // =========================================================================

    @Test
    @DisplayName("ORC_TB_CD 상수는 'BASKPM'이어야 한다 (전자결재 원본 연결 테이블코드)")
    void orcTbCd_상수값_BASKPM() {
        assertThat(CouncilSkipService.ORC_TB_CD).isEqualTo("BASKPM");
    }
}
