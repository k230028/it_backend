package com.kdb.it.domain.migration.commondata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.migration.commondata.CommonDataExportService;
import com.kdb.it.domain.migration.commondata.CommonDataMigrationService;
import com.kdb.it.domain.migration.commondata.MenuSequenceSynchronizer;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
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

/** 공통 데이터 이관(개발→운영) 관리자 API 보안 계약 테스트입니다. */
@WebMvcTest(CommonDataMigrationController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    CommonDataMigrationControllerTest.MethodSecurityTestConfig.class
})
class CommonDataMigrationControllerTest {

    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private CommonDataExportService exportService;
    @MockitoBean private CommonDataMigrationService migrationService;
    @MockitoBean private MenuSequenceSynchronizer menuSequenceSynchronizer;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    void 관리자는_dryRun을호출할수있다() throws Exception {
        given(migrationService.dryRun(any()))
                .willReturn(
                        new CommonDataMigrationDto.Response(
                                false, List.of(), List.of(), List.of()));

        mockMvc.perform(
                        post("/api/admin/migration/common-data/dry-run")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void 비관리자는_403이다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/common-data/dry-run")
                                .with(generalUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자는_export를호출할수있다() throws Exception {
        given(exportService.export())
                .willReturn(
                        new CommonDataMigrationDto.ExportResponse(
                                List.of(), List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/admin/migration/common-data/export").with(adminUser()))
                .andExpect(status().isOk());
    }

    /** 시트 5개 전부 빈 배열인 업로드 요청입니다. 빈 배열도 유효한 입력입니다. */
    private static CommonDataMigrationDto.Request emptyRequest() {
        return new CommonDataMigrationDto.Request(
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static RequestPostProcessor adminUser() {
        return user(new CustomUserDetails("999999", List.of(CustomUserDetails.ATH_ADMIN), "D001"));
    }

    private static RequestPostProcessor generalUser() {
        return user(new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "D001"));
    }
}
