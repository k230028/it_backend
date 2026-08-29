package com.kdb.it.domain.migration.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.migration.terminal.TerminalBulkImportService;
import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** 금융정보단말기 일괄업로드 관리자 API 계약 테스트입니다. */
@WebMvcTest(MigrationController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    MigrationControllerTest.MethodSecurityTestConfig.class
})
class MigrationControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TerminalBulkImportService terminalBulkImportService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void 비관리자는_금융정보단말기_업로드를_호출할수없다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/terminals/dry-run")
                                .with(generalUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_금융정보단말기_사전검증결과를받는다() throws Exception {
        TerminalBulkImportDto.Group group =
                new TerminalBulkImportDto.Group(
                        "2025", "COST-2025-0001", true, 1, new BigDecimal("1200"), List.of(3));
        given(terminalBulkImportService.dryRun(any()))
                .willReturn(new TerminalBulkImportDto.Response(1, 1, 1, 0, List.of(group)));

        mockMvc.perform(
                        post("/api/admin/migration/terminals/dry-run")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.groups[0].bseYy").value("2025"))
                .andExpect(jsonPath("$.groups[0].createNew").value(true));
    }

    @Test
    void 확정반영은_관리자사번을_서비스에넘기고_201을반환한다() throws Exception {
        given(terminalBulkImportService.commit(any(), eq("999999")))
                .willReturn(new TerminalBulkImportDto.Response(1, 1, 1, 0, List.of()));

        mockMvc.perform(
                        post("/api/admin/migration/terminals")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createCount").value(1));
    }

    @Test
    void 기존_편성률_엔드포인트는_제거된다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/imports/dry-run")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isNotFound());
    }

    private static TerminalBulkImportDto.Request request() {
        return new TerminalBulkImportDto.Request(
                List.of(
                        new TerminalBulkImportDto.Row(
                                3,
                                null,
                                null,
                                "부서",
                                "팀",
                                "담당자",
                                "블룸버그",
                                "별도단말",
                                "리서치",
                                "서비스",
                                "KRW",
                                BigDecimal.ZERO,
                                new BigDecimal("100"),
                                new BigDecimal("1200"),
                                "신규",
                                null,
                                null,
                                null,
                                null,
                                "연간",
                                null)));
    }

    private static RequestPostProcessor adminUser() {
        return user(new CustomUserDetails("999999", List.of(CustomUserDetails.ATH_ADMIN), "D001"));
    }

    private static RequestPostProcessor generalUser() {
        return user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"));
    }
}
