package com.kdb.it.infra.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.security.SimpleRequestCsrfFilter;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.migration.request.service.RequestFormSourceArchiveService;
import com.kdb.it.domain.migration.request.service.RequestFormSourceFileArchiver;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.authz.FileTargetWriteAuthorizerRegistry;
import com.kdb.it.infra.file.authz.RequestFormFileTargetWriteAuthorizer;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileController.class)
@Import({
    TestSecurityConfig.class,
    JacksonConfig.class,
    FileTargetWriteAuthorizerRegistry.class,
    RequestFormFileTargetWriteAuthorizer.class
})
class RequestFormFileControllerProtectionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FileService fileService;
    @MockitoBean private FileOwnershipChecker fileOwnershipChecker;
    @MockitoBean private RequestFormSourceArchiveService requestFormSourceArchiveService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private final CustomUserDetails admin =
            new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "D001");

    @Test
    @DisplayName("공식 반입 종류의 단건 업로드는 관리자도 generic API로 만들 수 없다")
    void singleUpload_rejectsOfficialSourceKind() throws Exception {
        mockMvc.perform(
                        multipart("/api/files")
                                .file(file("file"))
                                .file(textPart("flTpCone", "첨부파일"))
                                .file(textPart("pkColNm", RequestFormSourceFileArchiver.PK_COL_NM))
                                .file(textPart("pkCone", "APF-2026-00000001"))
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest")
                                .with(user(admin)))
                .andExpect(status().isForbidden());

        verify(fileService, never()).uploadFileAndGet(any(), any());
    }

    @Test
    @DisplayName("공식 반입 종류의 다건 업로드는 관리자도 generic API로 만들 수 없다")
    void bulkUpload_rejectsOfficialSourceKind() throws Exception {
        mockMvc.perform(
                        multipart("/api/files/bulk")
                                .file(file("files"))
                                .file(textPart("flTpCone", "첨부파일"))
                                .file(textPart("pkColNm", RequestFormSourceFileArchiver.PK_COL_NM))
                                .file(textPart("pkCone", "APF-2026-00000001"))
                                .header(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest")
                                .with(user(admin)))
                .andExpect(status().isForbidden());

        verify(fileService, never()).uploadFiles(any(), any());
    }

    @Test
    @DisplayName("일반 파일을 공식 반입 종류로 바꾸는 메타데이터 수정은 거부한다")
    void metadataUpdate_rejectsRetargetToOfficialSourceKind() throws Exception {
        FileDto.UpdateRequest request =
                FileDto.UpdateRequest.builder()
                        .pkColNm(RequestFormSourceFileArchiver.PK_COL_NM)
                        .pkCone("APF-2026-00000001")
                        .build();

        mockMvc.perform(
                        put("/api/files/FL-00000001")
                                .with(user(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(fileService, never()).updateFileMeta(any(), any());
    }

    private MockMultipartFile file(String partName) {
        return new MockMultipartFile(
                partName,
                "source.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1});
    }

    private MockMultipartFile textPart(String name, String value) {
        return new MockMultipartFile(
                name,
                "",
                MediaType.TEXT_PLAIN_VALUE,
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
