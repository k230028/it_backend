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
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationImportService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MigrationController @WebMvcTest
 *
 * <p>이관 API의 권한(관리자 전용)·요청 검증·계약(상태 코드, 응답 필드)과, 확정 반영이 인증 사용자의 사번을 그대로 서비스에 넘기는지를 검증합니다. commit이
 * 사번을 결재 요청자·감사 주체로 기록하므로, {@link CustomUserDetails#getEno()} 값이 서비스 호출 인자와 일치해야 합니다.
 *
 * <p>{@code TestSecurityConfig}는 인증 여부만 걸러내고 {@code @PreAuthorize}는 별도 활성화가 필요하므로, {@code
 * RealtimeLogControllerTest}와 같은 방식으로 {@link MethodSecurityTestConfig}를 함께 임포트해 관리자 전용 접근 제어(403)를
 * 이 슬라이스에서도 재현합니다.
 */
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

    @MockitoBean private MigrationImportService migrationImportService;

    @MockitoBean private JwtUtil jwtUtil;

    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/admin/migration/imports/dry-run: 관리자가 아니면 403이다")
    void 비관리자는_거부된다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/imports/dry-run")
                                .with(generalUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dryRunRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/migration/imports/dry-run: 관리자는 사전검증 결과를 받는다")
    void 관리자는_사전검증을_수행한다() throws Exception {
        given(migrationImportService.dryRun(any()))
                .willReturn(
                        new MigrationDto.DryRunResponse(
                                List.of(),
                                new MigrationDto.Summary(3, 0, 1),
                                List.of(
                                        new MigrationDto.ColumnCatalog(
                                                SheetKind.CAPITAL_PROJECT,
                                                "devAmountIoeC",
                                                List.of(
                                                        new MigrationDto.Candidate(
                                                                "104", "개발비(감리/컨설팅)"))))));

        mockMvc.perform(
                        post("/api/admin/migration/imports/dry-run")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dryRunRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalRows").value(3))
                .andExpect(jsonPath("$.summary.blockerCount").value(0))
                // 미리보기가 진단 없이도 보정 드롭다운을 그릴 수 있도록 카탈로그를 함께 직렬화한다 (MIG-10)
                .andExpect(jsonPath("$.catalogs[0].sheet").value("CAPITAL_PROJECT"))
                .andExpect(jsonPath("$.catalogs[0].column").value("devAmountIoeC"))
                .andExpect(jsonPath("$.catalogs[0].candidates[0].code").value("104"));
    }

    @Test
    @DisplayName("POST /api/admin/migration/imports/dry-run: 시트가 비면 400이다")
    void 빈_시트목록은_400이다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/imports/dry-run")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sheets\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/migration/imports: 확정 반영은 201과 반영 건수를 돌려주고 인증 사용자의 사번을 그대로 넘긴다")
    void 확정반영은_201이고_사번을_그대로_넘긴다() throws Exception {
        given(migrationImportService.commit(any(), eq("999999")))
                .willReturn(
                        new MigrationDto.CommitResponse(
                                14, 4, 9, 23, 2, 1, "PLN-2026-0001", List.of("COST-2026-0001")));

        mockMvc.perform(
                        post("/api/admin/migration/imports")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(commitRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.costCount").value(14))
                .andExpect(jsonPath("$.skippedRateCount").value(2))
                .andExpect(jsonPath("$.skippedPlanCount").value(1))
                .andExpect(jsonPath("$.planReqDocNo").value("PLN-2026-0001"));
    }

    @Test
    @DisplayName("POST /api/admin/migration/imports: JSON이 아닌 Content-Type은 415다")
    void 잘못된_컨텐츠타입은_415다() throws Exception {
        // text/plain은 CORS 단순 요청 Content-Type이라 SimpleRequestCsrfFilter가 X-Requested-With
        // 헤더를 요구한다(§4.2). 그 검사를 통과시켜 415(consumes 불일치) 자체를 검증한다.
        mockMvc.perform(
                        post("/api/admin/migration/imports")
                                .with(adminUser())
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static MigrationDto.DryRunRequest dryRunRequest() {
        return new MigrationDto.DryRunRequest(
                List.of(
                        new MigrationDto.SheetPayload(
                                SheetKind.COST,
                                "2026",
                                List.of(
                                        new MigrationDto.NormalizedRow(
                                                2, Map.of("ioeName", "유지보수료"))))),
                List.of());
    }

    private static MigrationDto.CommitRequest commitRequest() {
        return new MigrationDto.CommitRequest(dryRunRequest().sheets(), List.of());
    }

    /** 사번 999999의 관리자 인증 principal. commit이 이 사번을 그대로 서비스에 넘기는지 검증하는 데 사용합니다. */
    private static RequestPostProcessor adminUser() {
        return user(new CustomUserDetails("999999", List.of(CustomUserDetails.ATH_ADMIN), "D001"));
    }

    /** 일반사용자 인증 principal. 관리자 전용 접근 제어(403)를 검증하는 데 사용합니다. */
    private static RequestPostProcessor generalUser() {
        return user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"));
    }
}
