package com.kdb.it.common.approval.itbudget.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetSnapshotV3Dto;
import com.kdb.it.common.approval.itbudget.exception.ItBudgetApprovalException;
import com.kdb.it.common.approval.itbudget.service.ItBudgetApprovalFacade;
import com.kdb.it.common.mfa.security.MfaGuardConfiguration;
import com.kdb.it.common.mfa.service.MfaService;
import com.kdb.it.common.system.security.*;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.common.util.CookieUtil;
import com.kdb.it.config.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ItBudgetApplicationController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    MfaGuardConfiguration.class,
    CookieUtil.class
})
class ItBudgetApplicationControllerTest {
    static final String URL = "/api/applications/it-budget/previews";
    static final String BODY =
            """
        {"approvers":[{"role":"TEAM_LEAD","eno":"A1"}],"documents":[{"clientDocumentKey":"one","sourceRefs":[{"kind":"PROJECT","id":"P1","revision":1,"order":1}]}]}
        """;
    static final CustomUserDetails USER =
            new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001");
    @Autowired MockMvc mvc;
    @MockitoBean ItBudgetApprovalFacade facade;
    @MockitoBean JwtUtil jwtUtil;
    @MockitoBean CustomUserDetailsService customUserDetailsService;
    @MockitoBean MfaService mfaService;

    static final String SUBMIT_URL = "/api/applications/it-budget/submissions";
    static final String SUBMIT_BODY =
            """
        {"previewDigest":"%s","previewToken":"signed","approvers":[{"role":"TEAM_LEAD","eno":"A1"},{"role":"DEPT_HEAD","eno":"A1"}],"documents":[{"clientDocumentKey":"one","payloadDigest":"%s","sources":[{"kind":"PROJECT","id":"P1","revision":1,"order":1,"sourceDigest":"%s","displayName":"사업"}]}]}
        """
                    .formatted("a".repeat(64), "b".repeat(64), "c".repeat(64));

    @Test
    void submissionRequiresApprovalMfaBeforeCallingFacade() throws Exception {
        mvc.perform(
                        post(SUBMIT_URL)
                                .with(user(USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SUBMIT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
        verifyNoInteractions(facade, mfaService);
    }

    @Test
    void submissionConsumesMfaOnceAndUsesRealPrincipal() throws Exception {
        when(facade.submit(eq(USER), any()))
                .thenReturn(new SubmissionResponse(List.of("APF-1", "APF-2")));
        mvc.perform(
                        post(SUBMIT_URL)
                                .with(user(USER))
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                CookieUtil.MFA_PROOF_COOKIE, "proof"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SUBMIT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationNumbers[0]").value("APF-1"))
                .andExpect(jsonPath("$.applicationNumbers[1]").value("APF-2"));
        var order = inOrder(mfaService, facade);
        order.verify(mfaService).consumeApprovalProof(USER, "proof");
        order.verify(facade).submit(eq(USER), any());
    }

    @Test
    void submissionBeanValidationAndAuthenticationRunBeforeMfa() throws Exception {
        mvc.perform(post(SUBMIT_URL).contentType(MediaType.APPLICATION_JSON).content(SUBMIT_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post(SUBMIT_URL)
                                .with(user(USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SUBMIT_BODY.replace("\"revision\":1", "\"revision\":0")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(facade, mfaService);
    }

    @Test
    void rejectedProofStopsBeforeSubmission() throws Exception {
        doThrow(
                        new com.kdb.it.common.mfa.exception.MfaException(
                                com.kdb.it.common.mfa.exception.MfaErrorCode.MFA_REQUIRED))
                .when(mfaService)
                .consumeApprovalProof(USER, "used-proof");
        mvc.perform(
                        post(SUBMIT_URL)
                                .with(user(USER))
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                CookieUtil.MFA_PROOF_COOKIE, "used-proof"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(SUBMIT_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(facade);
    }

    @Test
    void previewAcceptsCustomPrincipalWithoutMfaAndReturnsV3Contract() throws Exception {
        var snapshot =
                new ItBudgetSnapshotV3Dto.ItBudgetSnapshot(
                        new ItBudgetSnapshotV3Dto.Form("it-budget", 3),
                        new ItBudgetSnapshotV3Dto.Payload(
                                List.of(),
                                List.of(),
                                new ItBudgetSnapshotV3Dto.Summary("0.000", "0.000", "0.000"),
                                new ItBudgetSnapshotV3Dto.Ledger("IT_BUDGET_LEDGER_V1", List.of())),
                        null,
                        null);
        when(facade.preview(eq(USER), any()))
                .thenReturn(
                        new PreviewResponse(
                                "a".repeat(64),
                                "opaque",
                                Instant.parse("2026-09-06T06:00:00Z"),
                                List.of(
                                        new PreviewDocument(
                                                "one", snapshot, "b".repeat(64), List.of()))));
        mvc.perform(
                        post(URL)
                                .with(user(USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].snapshot.form.id").value("it-budget"))
                .andExpect(jsonPath("$.documents[0].snapshot.form.version").value(3))
                .andExpect(jsonPath("$.previewToken").value("opaque"));
        verify(facade).preview(eq(USER), any());
        verifyNoInteractions(mfaService);
    }

    @Test
    void anonymousIs401AndUnsupportedPrincipalIs403InsteadOf500() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        var generic =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "unsupported", "unused", List.of());
        mvc.perform(
                        post(URL)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors
                                                .authentication(generic))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(facade, mfaService);
    }

    @Test
    void beanValidationAndMalformedEnumAre400BeforeFacade() throws Exception {
        for (String body :
                List.of(
                        BODY.replace("TEAM_LEAD", "UNSUPPORTED"),
                        BODY.replace("\"revision\":1", "\"revision\":0"),
                        "{\"approvers\":[],\"documents\":[]}"))
            mvc.perform(
                            post(URL)
                                    .with(user(USER))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(facade, mfaService);
    }

    @Test
    void sourceMissingKeepsTyped404Envelope() throws Exception {
        when(facade.preview(any(), any()))
                .thenThrow(
                        new ItBudgetApprovalException(
                                HttpStatus.NOT_FOUND,
                                "IT_BUDGET_SOURCE_NOT_FOUND",
                                "신청 대상을 찾을 수 없습니다.",
                                List.of()));
        mvc.perform(
                        post(URL)
                                .with(user(USER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("IT_BUDGET_SOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.changedSources").doesNotExist());
    }
}
