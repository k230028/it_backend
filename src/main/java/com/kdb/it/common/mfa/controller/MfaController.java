package com.kdb.it.common.mfa.controller;

import com.kdb.it.common.mfa.dto.MfaDto;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인과 결재가 공유하는 MFA 거래 HTTP API다. */
@RestController
@RequestMapping("/api/mfa")
@Tag(name = "MFA", description = "다단계 인증 거래 API")
public class MfaController {

    public static final String LOGIN_PENDING_COOKIE = "mfa-login-pending";
    public static final String MFA_PROOF_COOKIE = "mfa-proof";

    private final MfaService mfaService;
    private final boolean secureCookie;

    public MfaController(
            MfaService mfaService, @Value("${app.cookie.secure:false}") boolean secureCookie) {
        this.mfaService = mfaService;
        this.secureCookie = secureCookie;
    }

    /**
     * MFA challenge를 시작한다.
     *
     * @param request 목적과 인증 수단
     * @param principal JWT 사용자 또는 익명 사용자
     * @param servletRequest 로그인 대기 쿠키를 읽을 현재 요청
     * @return MFA 거래와 공급자 표시 정보
     */
    @PostMapping(value = "/challenges", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MFA challenge 시작")
    public ResponseEntity<MfaDto.MfaChallengeResponse> startChallenge(
            @Valid @RequestBody MfaDto.MfaStartRequest request,
            @AuthenticationPrincipal Object principal,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(
                mfaService.startChallenge(
                        request, user(principal), cookie(servletRequest, LOGIN_PENDING_COOKIE)));
    }

    /**
     * MFA challenge를 검증하고 성공한 1회용 증표를 httpOnly 쿠키로 발급한다.
     *
     * @param challengeId 서버 MFA 거래 식별자
     * @param request 공급자 challenge 식별자와 검증 값
     * @param principal JWT 사용자 또는 익명 사용자
     * @param servletRequest 로그인 대기 쿠키를 읽을 현재 요청
     * @return 성공 여부와 서버 기준 남은 시간
     */
    @PostMapping(
            value = "/challenges/{challengeId}/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "MFA challenge 검증")
    public ResponseEntity<MfaDto.MfaVerifyResponse> verifyChallenge(
            @PathVariable("challengeId") UUID challengeId,
            @Valid @RequestBody MfaDto.MfaVerifyRequest request,
            @AuthenticationPrincipal Object principal,
            HttpServletRequest servletRequest) {
        MfaService.VerifiedChallenge completion =
                mfaService.verifyChallenge(
                        challengeId,
                        request,
                        user(principal),
                        cookie(servletRequest, LOGIN_PENDING_COOKIE));
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        proofCookie(completion.proof(), completion.response().remainingSeconds())
                                .toString())
                .body(completion.response());
    }

    /**
     * 사용자가 닫은 MFA 대화상자의 거래를 취소한다.
     *
     * @param challengeId 서버 MFA 거래 식별자
     * @param principal JWT 사용자 또는 익명 사용자
     * @param servletRequest 로그인 대기 쿠키를 읽을 현재 요청
     * @return 204 No Content
     */
    @DeleteMapping("/challenges/{challengeId}")
    @Operation(summary = "MFA challenge 취소")
    public ResponseEntity<Void> cancelChallenge(
            @PathVariable("challengeId") UUID challengeId,
            @AuthenticationPrincipal Object principal,
            HttpServletRequest servletRequest) {
        mfaService.cancelChallenge(
                challengeId, user(principal), cookie(servletRequest, LOGIN_PENDING_COOKIE));
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie proofCookie(String proof, long remainingSeconds) {
        return ResponseCookie.from(MFA_PROOF_COOKIE, proof)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(Duration.ofSeconds(remainingSeconds))
                .sameSite("Lax")
                .build();
    }

    private static Optional<CustomUserDetails> user(Object principal) {
        return principal instanceof CustomUserDetails user ? Optional.of(user) : Optional.empty();
    }

    private static String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
