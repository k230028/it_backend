package com.kdb.it.common.mfa.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.dto.MfaDto;
import com.kdb.it.common.mfa.exception.MfaErrorCode;
import com.kdb.it.common.mfa.exception.MfaException;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** MFA 컨트롤러의 HTTP 계약과 익명 로그인 경로 보안 경계를 검증한다. */
@WebMvcTest(MfaController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class, GlobalExceptionHandler.class})
class MfaControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MfaService mfaService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void loginChallenge_익명요청은대기쿠키로시작할수있다() throws Exception {
        UUID challengeId = UUID.randomUUID();
        org.mockito.BDDMockito.given(mfaService.startChallenge(any(), any(), eq("pending-value")))
                .willReturn(
                        new MfaDto.MfaChallengeResponse(
                                challengeId, "provider-id", "display-data", 90));

        mockMvc.perform(
                        post("/api/mfa/challenges")
                                .cookie(
                                        new Cookie(
                                                MfaController.LOGIN_PENDING_COOKIE,
                                                "pending-value"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new MfaDto.MfaStartRequest(
                                                        MfaPurpose.LOGIN, MfaMethod.MOTP))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(challengeId.toString()))
                .andExpect(jsonPath("$.remainingSeconds").value(90));
    }

    @Test
    void verify_성공하면httpOnly일회용증표쿠키를발급한다() throws Exception {
        UUID challengeId = UUID.randomUUID();
        org.mockito.BDDMockito.given(
                        mfaService.verifyChallenge(
                                eq(challengeId), any(), any(), eq("pending-value")))
                .willReturn(new MfaDto.MfaVerifyResponse(true, 85));

        mockMvc.perform(
                        post("/api/mfa/challenges/{id}/verify", challengeId)
                                .cookie(
                                        new Cookie(
                                                MfaController.LOGIN_PENDING_COOKIE,
                                                "pending-value"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new MfaDto.MfaVerifyRequest(
                                                        "provider-id", "verification-value"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(
                        header().string(
                                        "Set-Cookie",
                                        org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString(
                                                        MfaController.MFA_PROOF_COOKIE
                                                                + "="
                                                                + challengeId),
                                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                                org.hamcrest.Matchers.containsString(
                                                        "SameSite=Lax"))));
    }

    @Test
    void cancel_소유권확인은서비스에위임하고204를반환한다() throws Exception {
        UUID challengeId = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/mfa/challenges/{id}", challengeId)
                                .cookie(
                                        new Cookie(
                                                MfaController.LOGIN_PENDING_COOKIE,
                                                "pending-value")))
                .andExpect(status().isNoContent());

        verify(mfaService).cancelChallenge(eq(challengeId), any(), eq("pending-value"));
    }

    @Test
    void approvalChallenge_익명요청은거부한다() throws Exception {
        org.mockito.BDDMockito.given(mfaService.startChallenge(any(), any(), any()))
                .willThrow(new MfaException(MfaErrorCode.MFA_REQUIRED));

        mockMvc.perform(
                        post("/api/mfa/challenges")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new MfaDto.MfaStartRequest(
                                                        MfaPurpose.APPROVAL, MfaMethod.FIDO))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
    }
}
