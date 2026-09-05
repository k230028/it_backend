package com.kdb.it.common.approval.itbudget.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.*;
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

    @Test
    void previewAcceptsCustomPrincipalWithoutMfaAndReturnsV2Contract() throws Exception {
        var snapshot =
                new ItBudgetSnapshot(
                        new Form("it-budget", 2),
                        new Payload(List.of(), List.of(), new Summary("0.000", "0.000", "0.000")),
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
                .andExpect(jsonPath("$.documents[0].snapshot.form.version").value(2))
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
