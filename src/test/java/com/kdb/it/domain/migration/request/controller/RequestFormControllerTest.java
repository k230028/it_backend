package com.kdb.it.domain.migration.request.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.RequestFormImportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * RequestFormController @WebMvcTest
 *
 * <p>편성요청서 반입 API의 권한(관리자 전용)·요청 검증·계약과, 반입이 인증 사용자의 사번을 그대로 서비스에 넘기는지를 검증합니다. 사번이 결재 요청자·감사 주체로
 * 기록되므로 {@link CustomUserDetails#getEno()} 값이 서비스 호출 인자와 일치해야 합니다.
 *
 * <p>{@code TestSecurityConfig}는 인증 여부만 걸러내고 {@code @PreAuthorize}는 별도 활성화가 필요하므로 {@code
 * MigrationControllerTest}와 같은 방식으로 {@link MethodSecurityTestConfig}를 함께 임포트합니다.
 */
@WebMvcTest(RequestFormController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    RequestFormControllerTest.MethodSecurityTestConfig.class
})
class RequestFormControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RequestFormImportService importService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/admin/migration/requests/dry-run: 관리자가 아니면 403이다")
    void 비관리자는_거부된다() throws Exception {
        mockMvc.perform(
                        multipart("/api/admin/migration/requests/dry-run")
                                .file(excelPart())
                                .file(manifestPart(validManifest()))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .with(generalUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/migration/requests/dry-run: 관리자는 사전검증 결과를 받는다")
    void 관리자는_사전검증을_수행한다() throws Exception {
        given(importService.importBatch(anyList(), any(), eq("999999"), eq(true)))
                .willReturn(response(true));

        mockMvc.perform(
                        multipart("/api/admin/migration/requests/dry-run")
                                .file(excelPart())
                                .file(manifestPart(validManifest()))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.summary.totalFiles").value(1))
                .andExpect(jsonPath("$.files[0].fileKey").value("자금운용실/요청서.xls"));
    }

    @Test
    @DisplayName("POST /api/admin/migration/requests: 반입은 인증 사용자의 사번을 그대로 넘긴다")
    void 반입은_사번을_그대로_넘긴다() throws Exception {
        given(importService.importBatch(anyList(), any(), eq("999999"), eq(false)))
                .willReturn(response(false));

        mockMvc.perform(
                        multipart("/api/admin/migration/requests")
                                .file(excelPart())
                                .file(manifestPart(validManifest()))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.files[0].created[0].key").value("COST-2026-0001"));
    }

    @Test
    @DisplayName("POST /api/admin/migration/requests: 예산연도가 4자리가 아니면 400이다")
    void 잘못된_예산연도는_400이다() throws Exception {
        RequestFormDto.ImportManifest invalid =
                new RequestFormDto.ImportManifest(
                        "26",
                        List.of(
                                new RequestFormDto.FileEntry(
                                        "자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, null)),
                        List.of());

        mockMvc.perform(
                        multipart("/api/admin/migration/requests")
                                .file(excelPart())
                                .file(manifestPart(invalid))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .with(adminUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/migration/requests: 파일 목록이 비면 400이다")
    void 빈_파일목록은_400이다() throws Exception {
        RequestFormDto.ImportManifest emptyEntries =
                new RequestFormDto.ImportManifest("2026", List.of(), List.of());

        mockMvc.perform(
                        multipart("/api/admin/migration/requests")
                                .file(excelPart())
                                .file(manifestPart(emptyEntries))
                                .header("X-Requested-With", "XMLHttpRequest")
                                .with(adminUser()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/admin/migration/requests: multipart가 아닌 Content-Type은 415다")
    void 잘못된_컨텐츠타입은_415다() throws Exception {
        // text/plain은 CORS 단순 요청 Content-Type이라 SimpleRequestCsrfFilter가 X-Requested-With
        // 헤더를 요구한다. 그 검사를 통과시켜 415(consumes 불일치) 자체를 검증한다.
        mockMvc.perform(
                        post("/api/admin/migration/requests")
                                .with(adminUser())
                                .header("X-Requested-With", "XMLHttpRequest")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private static MockMultipartFile excelPart() {
        return new MockMultipartFile(
                "files",
                "요청서.xls",
                "application/vnd.ms-excel",
                "dummy".getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile manifestPart(RequestFormDto.ImportManifest manifest)
            throws Exception {
        return new MockMultipartFile(
                "manifest",
                "manifest.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(manifest));
    }

    private static RequestFormDto.ImportManifest validManifest() {
        return new RequestFormDto.ImportManifest(
                "2026",
                List.of(
                        new RequestFormDto.FileEntry(
                                "자금운용실/요청서.xls", "자금운용실", null, AmountUnit.WON, "571")),
                List.of());
    }

    private static RequestFormDto.ImportResponse response(boolean dryRun) {
        return new RequestFormDto.ImportResponse(
                dryRun,
                new RequestFormDto.ImportSummary(1, 1, 0, new RequestFormDto.RecordCounts(0, 0, 1)),
                List.of(
                        new RequestFormDto.FileResult(
                                "자금운용실/요청서.xls",
                                "자금운용실",
                                RequestFormDto.FileStatus.APPLIED,
                                List.of(),
                                dryRun
                                        ? List.of()
                                        : List.of(
                                                new RequestFormDto.CreatedRecord(
                                                        "BCOSTM",
                                                        "COST-2026-0001",
                                                        "계약",
                                                        "APF-2026-00000001")),
                                new RequestFormDto.RecordCounts(0, 0, 1),
                                AmountUnit.WON)));
    }

    /** 사번 999999의 관리자 인증 principal. 반입이 이 사번을 그대로 서비스에 넘기는지 검증하는 데 사용합니다. */
    private static RequestPostProcessor adminUser() {
        return user(new CustomUserDetails("999999", List.of(CustomUserDetails.ATH_ADMIN), "D001"));
    }

    /** 일반사용자 인증 principal. 관리자 전용 접근 제어(403)를 검증하는 데 사용합니다. */
    private static RequestPostProcessor generalUser() {
        return user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"));
    }
}
