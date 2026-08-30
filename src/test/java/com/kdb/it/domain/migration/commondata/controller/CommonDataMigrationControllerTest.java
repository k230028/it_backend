package com.kdb.it.domain.migration.commondata.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.util.Optional;
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

    /*
     * commit 경로는 dry-run과 달리 메뉴 시퀀스 동기화 결과를 응답에 합치므로,
     * 동기화 경고가 없을 때와 있을 때를 나눠 201 응답 본문까지 확인한다.
     */

    @Test
    void 관리자는_commit을호출하면_201과반영결과를받는다() throws Exception {
        given(migrationService.commit(any()))
                .willReturn(
                        new CommonDataMigrationDto.Response(
                                true,
                                List.of(
                                        new CommonDataMigrationDto.TableSummary(
                                                "TPRMPP_CMENUM", 1, 2, 0)),
                                List.of(),
                                List.of()));
        given(menuSequenceSynchronizer.advanceTo(any())).willReturn(Optional.empty());

        mockMvc.perform(
                        post("/api/admin/migration/common-data")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.committed").value(true))
                .andExpect(jsonPath("$.summaries[0].table").value("TPRMPP_CMENUM"))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    void 메뉴시퀀스_동기화경고는_commit응답의경고에더해진다() throws Exception {
        given(migrationService.commit(any()))
                .willReturn(
                        new CommonDataMigrationDto.Response(
                                true, List.of(), List.of("기존 경고"), List.of()));
        given(menuSequenceSynchronizer.advanceTo(any()))
                .willReturn(Optional.of("메뉴 시퀀스 동기화에 실패했습니다."));

        mockMvc.perform(
                        post("/api/admin/migration/common-data")
                                .with(adminUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warnings.length()").value(2))
                .andExpect(jsonPath("$.warnings[0]").value("기존 경고"))
                .andExpect(jsonPath("$.warnings[1]").value("메뉴 시퀀스 동기화에 실패했습니다."));
    }

    @Test
    void 비관리자는_commit도403이다() throws Exception {
        mockMvc.perform(
                        post("/api/admin/migration/common-data")
                                .with(generalUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(emptyRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비관리자는_export도403이다() throws Exception {
        mockMvc.perform(get("/api/admin/migration/common-data/export").with(generalUser()))
                .andExpect(status().isForbidden());
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
