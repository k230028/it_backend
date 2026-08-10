package com.kdb.it.common.mfa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.controller.ApplicationController;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.exception.MfaErrorCode;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.controller.CouncilLifecycleController;
import com.kdb.it.domain.council.controller.CouncilResultController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

/** 전자결재 MFA 공통 경계의 1회 소비와 엔드포인트 inventory를 검증한다. */
class MfaGuardAspectTest {

    private static final String PROOF_COOKIE = "mfa-proof";
    private static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001");

    private MfaService mfaService;
    private Runnable domainCommand;
    private GuardedTarget target;
    private HandlerMethod handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MfaGuardAspect aspect;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        mfaService = mock(MfaService.class);
        domainCommand = mock(Runnable.class);
        target = new GuardedTarget(domainCommand);
        handler = new HandlerMethod(target, GuardedTarget.class.getDeclaredMethod("execute"));
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        aspect = new MfaGuardAspect(mfaService, false);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                USER, null, USER.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("MFA proof 쿠키가 없으면 증표 소비나 결재 명령을 실행하지 않고 쿠키를 삭제한다")
    void guard_증표없음_명령미호출() throws Exception {
        assertThatThrownBy(() -> aspect.preHandle(request, response, handler))
                .isInstanceOf(MfaException.class)
                .extracting(exception -> ((MfaException) exception).errorCode())
                .isEqualTo(MfaErrorCode.MFA_REQUIRED);

        verify(mfaService, never()).consumeApprovalProof(USER, null);
        verify(domainCommand, never()).run();
        assertProofCookieDeleted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedProofs")
    @DisplayName("거부된 MFA proof는 결재 명령을 실행하지 않는다")
    void guard_거부된증표_명령미호출(String scenario, MfaErrorCode errorCode) throws Exception {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "rejected-proof"));
        doThrow(new MfaException(errorCode))
                .when(mfaService)
                .consumeApprovalProof(USER, "rejected-proof");

        assertThatThrownBy(() -> aspect.preHandle(request, response, handler))
                .isInstanceOf(MfaException.class)
                .extracting(exception -> ((MfaException) exception).errorCode())
                .isEqualTo(errorCode);

        verify(domainCommand, never()).run();
        assertProofCookieDeleted();
    }

    static Stream<Arguments> rejectedProofs() {
        return Stream.of(
                Arguments.of("만료된 증표", MfaErrorCode.MFA_EXPIRED),
                Arguments.of("다른 사용자에게 귀속된 증표", MfaErrorCode.MFA_REQUIRED),
                Arguments.of("이미 소비된 증표", MfaErrorCode.MFA_REQUIRED));
    }

    @Test
    @DisplayName("유효한 MFA proof를 명령 전에 한 번 소비하고 성공 응답에서도 쿠키를 삭제한다")
    void guard_유효한증표_1회소비후명령실행() throws Throwable {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "valid-proof"));

        assertThat(aspect.preHandle(request, response, handler)).isTrue();
        assertProofCookieDeleted();
        target.execute();

        var ordered = org.mockito.Mockito.inOrder(mfaService, domainCommand);
        ordered.verify(mfaService).consumeApprovalProof(USER, "valid-proof");
        ordered.verify(domainCommand).run();
        verify(domainCommand).run();
        assertProofCookieDeleted();
    }

    @Test
    @DisplayName("유효한 증표 소비 뒤 도메인 명령이 실패해도 미리 설정된 삭제 쿠키를 유지한다")
    void guard_도메인명령실패_쿠키삭제() throws Exception {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "valid-proof"));
        RuntimeException domainFailure = new RuntimeException("domain failure");
        doThrow(domainFailure).when(domainCommand).run();

        assertThat(aspect.preHandle(request, response, handler)).isTrue();
        assertProofCookieDeleted();
        assertThatThrownBy(target::execute).isSameAs(domainFailure);

        verify(mfaService).consumeApprovalProof(USER, "valid-proof");
        assertProofCookieDeleted();
    }

    @ParameterizedTest(name = "{0}#{1} protected={2}")
    @MethodSource("endpointInventory")
    @DisplayName("전자결재 변경 경계의 보호·제외 inventory가 고정되어 있다")
    void endpointInventory_보호경계고정(
            Class<?> controllerType, String methodName, boolean protectedEndpoint) {
        Method method = uniqueMethod(controllerType, methodName);

        assertThat(method.isAnnotationPresent(MfaRequired.class)).isEqualTo(protectedEndpoint);
        if (protectedEndpoint) {
            assertThat(method.getAnnotation(MfaRequired.class).purpose())
                    .isEqualTo(MfaPurpose.APPROVAL);
        }
    }

    static Stream<Arguments> endpointInventory() {
        return Stream.of(
                // 보호: 공통 상신, 단건 승인·반려, 일괄 승인·반려, 회수
                endpoint(ApplicationController.class, "submit", true),
                endpoint(ApplicationController.class, "approve", true),
                endpoint(ApplicationController.class, "bulkApprove", true),
                endpoint(ApplicationController.class, "recall", true),
                // 보호: 도메인별 전자결재 상신
                endpoint(CouncilLifecycleController.class, "requestApproval", true),
                endpoint(CouncilLifecycleController.class, "decideSkipRequest", true),
                endpoint(CouncilResultController.class, "requestResultApproval", true),
                // 제외: 공통 조회와 POST 형식의 일괄 조회
                endpoint(ApplicationController.class, "getApplications", false),
                endpoint(ApplicationController.class, "getPendingCount", false),
                endpoint(ApplicationController.class, "getApplication", false),
                endpoint(ApplicationController.class, "getApfDtlCone", false),
                endpoint(ApplicationController.class, "bulkGetApplications", false),
                endpoint(ApplicationController.class, "getDashboard", false),
                endpoint(ApplicationController.class, "getApprovalBadgeCount", false),
                // 제외: 외부 전자결재 콜백과 전자결재 전 초안 저장
                endpoint(CouncilLifecycleController.class, "processApprovalCallback", false),
                endpoint(CouncilResultController.class, "saveResult", false),
                endpoint(CouncilResultController.class, "updateResult", false));
    }

    private static Arguments endpoint(
            Class<?> controllerType, String methodName, boolean protectedEndpoint) {
        return Arguments.of(controllerType, methodName, protectedEndpoint);
    }

    private static Method uniqueMethod(Class<?> controllerType, String methodName) {
        return Stream.of(controllerType.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private void assertProofCookieDeleted() {
        assertThat(response.getHeader("Set-Cookie"))
                .contains("mfa-proof=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    private static final class GuardedTarget {

        private final Runnable domainCommand;

        private GuardedTarget(Runnable domainCommand) {
            this.domainCommand = domainCommand;
        }

        @MfaRequired(purpose = MfaPurpose.APPROVAL)
        void execute() {
            domainCommand.run();
        }
    }
}
