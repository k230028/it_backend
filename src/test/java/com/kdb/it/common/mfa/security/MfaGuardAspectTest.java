package com.kdb.it.common.mfa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
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
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.domain.council.controller.CouncilLifecycleController;
import com.kdb.it.domain.council.controller.CouncilResultController;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 전자결재 MFA 공통 경계의 1회 소비와 엔드포인트 inventory를 검증한다. */
class MfaGuardAspectTest {

    private static final String PROOF_COOKIE = "mfa-proof";
    private static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001");

    private MfaService mfaService;
    private CookieUtil cookieUtil;
    private Runnable domainCommand;
    private ProceedingJoinPoint joinPoint;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MfaGuardAspect aspect;
    private MfaRequired approvalRequirement;

    @BeforeEach
    void setUp() throws Throwable {
        mfaService = mock(MfaService.class);
        cookieUtil = mock(CookieUtil.class);
        domainCommand = mock(Runnable.class);
        joinPoint = mock(ProceedingJoinPoint.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        aspect = new MfaGuardAspect(mfaService, cookieUtil);
        approvalRequirement = requirement("execute");
        given(cookieUtil.deleteMfaProofCookie())
                .willReturn(
                        ResponseCookie.from(PROOF_COOKIE, "")
                                .httpOnly(true)
                                .path("/")
                                .maxAge(0)
                                .sameSite("Lax")
                                .build());
        given(joinPoint.proceed())
                .willAnswer(
                        ignored -> {
                            domainCommand.run();
                            return null;
                        });
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        authenticate(USER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void guard_증표없음_명령미호출() {
        assertMfaError(
                () -> aspect.consumeProof(joinPoint, approvalRequirement),
                MfaErrorCode.MFA_REQUIRED);

        verify(mfaService, never()).consumeApprovalProof(USER, null);
        verify(domainCommand, never()).run();
        assertProofCookieDeleted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedProofs")
    void guard_거부된증표_명령미호출(String scenario, MfaErrorCode errorCode) {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "rejected-proof"));
        doThrow(new MfaException(errorCode))
                .when(mfaService)
                .consumeApprovalProof(USER, "rejected-proof");

        assertMfaError(() -> aspect.consumeProof(joinPoint, approvalRequirement), errorCode);

        verify(domainCommand, never()).run();
        assertProofCookieDeleted();
    }

    static Stream<Arguments> rejectedProofs() {
        return Stream.of(
                Arguments.of("만료된 증표", MfaErrorCode.MFA_EXPIRED),
                Arguments.of("사용자 불일치 또는 소비된 증표", MfaErrorCode.MFA_REQUIRED));
    }

    @Test
    void guard_유효증표_소비후명령실행() throws Throwable {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "valid-proof"));

        aspect.consumeProof(joinPoint, approvalRequirement);

        var ordered = org.mockito.Mockito.inOrder(mfaService, domainCommand);
        ordered.verify(mfaService).consumeApprovalProof(USER, "valid-proof");
        ordered.verify(domainCommand).run();
        assertProofCookieDeleted();
    }

    @Test
    void guard_도메인명령실패에도삭제쿠키유지() {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "valid-proof"));
        RuntimeException domainFailure = new RuntimeException("domain failure");
        doThrow(domainFailure).when(domainCommand).run();

        assertThatThrownBy(() -> aspect.consumeProof(joinPoint, approvalRequirement))
                .isSameAs(domainFailure);

        verify(mfaService).consumeApprovalProof(USER, "valid-proof");
        assertProofCookieDeleted();
    }

    @Test
    void guard_서블릿요청경계없음_거부() {
        RequestContextHolder.resetRequestAttributes();

        assertMfaError(
                () -> aspect.consumeProof(joinPoint, approvalRequirement),
                MfaErrorCode.MFA_REQUIRED);
        verify(mfaService, never()).consumeApprovalProof(USER, null);
    }

    @Test
    void guard_CustomUserDetails인증없음_거부() {
        SecurityContextHolder.clearContext();

        assertMfaError(
                () -> aspect.consumeProof(joinPoint, approvalRequirement),
                MfaErrorCode.MFA_REQUIRED);
        assertProofCookieDeleted();
    }

    @Test
    void guard_승인외목적_거부() throws NoSuchMethodException {
        assertMfaError(
                () -> aspect.consumeProof(joinPoint, requirement("loginExecute")),
                MfaErrorCode.MFA_REQUIRED);
        verify(mfaService, never()).consumeApprovalProof(USER, null);
    }

    @Test
    void guard_응답객체없어도증표소비후명령실행() throws Throwable {
        request.setCookies(new jakarta.servlet.http.Cookie(PROOF_COOKIE, "valid-proof"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        aspect.consumeProof(joinPoint, approvalRequirement);

        verify(mfaService).consumeApprovalProof(USER, "valid-proof");
        verify(domainCommand).run();
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
                endpoint(ApplicationController.class, "submit", true),
                endpoint(ApplicationController.class, "approve", true),
                endpoint(ApplicationController.class, "bulkApprove", true),
                endpoint(ApplicationController.class, "recall", true),
                endpoint(CouncilLifecycleController.class, "requestApproval", true),
                endpoint(CouncilLifecycleController.class, "decideSkipRequest", true),
                endpoint(CouncilResultController.class, "requestResultApproval", true),
                endpoint(ApplicationController.class, "getApplications", false),
                endpoint(ApplicationController.class, "getPendingCount", false),
                endpoint(ApplicationController.class, "getApplication", false),
                endpoint(ApplicationController.class, "getApfDtlCone", false),
                endpoint(ApplicationController.class, "bulkGetApplications", false),
                endpoint(ApplicationController.class, "getDashboard", false),
                endpoint(ApplicationController.class, "getApprovalBadgeCount", false),
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

    private static MfaRequired requirement(String methodName) throws NoSuchMethodException {
        return GuardedTarget.class.getDeclaredMethod(methodName).getAnnotation(MfaRequired.class);
    }

    private static void authenticate(CustomUserDetails user) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                user, null, user.getAuthorities()));
    }

    private static void assertMfaError(ThrowingCall call, MfaErrorCode expected) {
        assertThatThrownBy(call::invoke)
                .isInstanceOf(MfaException.class)
                .extracting(exception -> ((MfaException) exception).errorCode())
                .isEqualTo(expected);
    }

    private void assertProofCookieDeleted() {
        assertThat(response.getHeader("Set-Cookie"))
                .contains("mfa-proof=")
                .contains("Path=/")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void invoke() throws Throwable;
    }

    private static final class GuardedTarget {

        @MfaRequired(purpose = MfaPurpose.APPROVAL)
        void execute() {}

        @MfaRequired(purpose = MfaPurpose.LOGIN)
        void loginExecute() {}
    }
}
