package com.kdb.it.infra.file.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.infra.file.FileOwnershipChecker;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.service.FileService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

/**
 * FileController @WebMvcTest
 *
 * <p>공통 첨부파일 HTTP 응답 구조와 인증 동작을 검증합니다.</p>
 */
@WebMvcTest(FileController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FileService fileService;
    @MockitoBean
    private FileOwnershipChecker fileOwnershipChecker;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private static final String FL_MNG_NO = "FL_00000001";

    @Test
    @DisplayName("GET /api/files - 비인증 → 401")
    void getFiles_비인증_401() throws Exception {
        mockMvc.perform(get("/api/files").param("pkColNm", "요구사항정의서"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/files - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getFiles_인증_200() throws Exception {
        given(fileService.getFiles(any(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/files").param("pkColNm", "요구사항정의서"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/files/{flMngNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getFile_인증_200() throws Exception {
        given(fileService.getFile(FL_MNG_NO)).willReturn(new FileDto.Response());
        mockMvc.perform(get("/api/files/" + FL_MNG_NO))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/files (multipart) - 인증된 사용자 → 201 Created")
    @WithMockUser(username = "10001")
    void uploadFile_인증_201() throws Exception {
        given(fileService.uploadFileAndGet(any(), any())).willReturn(new FileDto.Response());

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf",
                MediaType.APPLICATION_PDF_VALUE, "pdf content".getBytes());
        MockMultipartFile flTpCone = new MockMultipartFile("flTpCone", "", MediaType.TEXT_PLAIN_VALUE,
                "첨부파일".getBytes());
        MockMultipartFile pkColNm = new MockMultipartFile("pkColNm", "", MediaType.TEXT_PLAIN_VALUE,
                "요구사항정의서".getBytes());

        mockMvc.perform(multipart("/api/files")
                .file(file)
                .file(flTpCone)
                .file(pkColNm))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/files/bulk (multipart) - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void uploadFiles_인증_200() throws Exception {
        given(fileService.uploadFiles(any(), any())).willReturn(new FileDto.BulkUploadResponse());

        MockMultipartFile file1 = new MockMultipartFile("files", "a.pdf",
                MediaType.APPLICATION_PDF_VALUE, "a".getBytes());
        MockMultipartFile flTpCone = new MockMultipartFile("flTpCone", "", MediaType.TEXT_PLAIN_VALUE,
                "첨부파일".getBytes());
        MockMultipartFile pkColNm = new MockMultipartFile("pkColNm", "", MediaType.TEXT_PLAIN_VALUE,
                "요구사항정의서".getBytes());

        mockMvc.perform(multipart("/api/files/bulk")
                .file(file1)
                .file(flTpCone)
                .file(pkColNm))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/files/{flMngNo} - 인증된 사용자 → 200")
    void updateFileMeta_인증_200() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        given(fileService.updateFileMeta(anyString(), any())).willReturn(FL_MNG_NO);
        mockMvc.perform(put("/api/files/" + FL_MNG_NO).with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FileDto.UpdateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/files/{flMngNo} - 소유권 검증을 호출한다")
    void updateMeta_callsOwnershipCheck() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        given(fileService.updateFileMeta(anyString(), any())).willReturn(FL_MNG_NO);

        mockMvc.perform(put("/api/files/" + FL_MNG_NO).with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FileDto.UpdateRequest())))
                .andExpect(status().isOk());

        verify(fileOwnershipChecker).verifyWriteAccess(FL_MNG_NO, userDetails);
    }

    @Test
    @DisplayName("PUT /api/files/{flMngNo} - 타인 파일 메타수정 시 소유권 위반 → 403")
    void updateMeta_deniedForOther() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        doThrow(new org.springframework.security.access.AccessDeniedException("본인 또는 관리자만 수행할 수 있습니다."))
                .when(fileOwnershipChecker).verifyWriteAccess(anyString(), any());

        mockMvc.perform(put("/api/files/" + FL_MNG_NO).with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FileDto.UpdateRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/files/{flMngNo} - 인증된 사용자 → 204 No Content")
    void deleteFile_인증_204() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        mockMvc.perform(delete("/api/files/" + FL_MNG_NO).with(user(userDetails)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/files/{flMngNo} - 타인 파일 삭제 시 소유권 위반 → 403")
    void deleteFile_deniedForOther() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        doThrow(new org.springframework.security.access.AccessDeniedException("본인 또는 관리자만 수행할 수 있습니다."))
                .when(fileOwnershipChecker).verifyWriteAccess(anyString(), any());

        mockMvc.perform(delete("/api/files/" + FL_MNG_NO).with(user(userDetails)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/files/bulk - 인증된 사용자 → 200")
    void deleteFilesByOrc_인증_200() throws Exception {
        CustomUserDetails userDetails = new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
        given(fileService.deleteFilesByOrc(anyString(), anyString(), any())).willReturn(3);
        mockMvc.perform(delete("/api/files/bulk").with(user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FileDto.BulkDeleteRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/files/{flMngNo}/download - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void downloadFile_인증_200() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("content".getBytes());
        given(fileService.downloadFile(FL_MNG_NO))
                .willReturn(new FileService.FileDownloadResult(resource, "test.pdf", "application/pdf"));

        mockMvc.perform(get("/api/files/" + FL_MNG_NO + "/download"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/files/{flMngNo}/preview - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void previewFile_인증_200() throws Exception {
        ByteArrayResource resource = new ByteArrayResource("imgdata".getBytes());
        given(fileService.downloadFile(FL_MNG_NO))
                .willReturn(new FileService.FileDownloadResult(resource, "photo.png", "image/png"));

        mockMvc.perform(get("/api/files/" + FL_MNG_NO + "/preview"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/files/{flMngNo}/preview - 비게시판 파일(읽기권한 통과) → 200")
    @WithMockUser(username = "10001")
    void previewFile_비게시판파일_읽기권한통과_200() throws Exception {
        // checkReadAccess는 비게시판 파일에서 예외 없이 통과(no-op)
        doNothing().when(fileOwnershipChecker).checkReadAccess(anyString(), any());

        ByteArrayResource resource = new ByteArrayResource("imgdata".getBytes());
        given(fileService.downloadFile(FL_MNG_NO))
                .willReturn(new FileService.FileDownloadResult(resource, "photo.png", "image/png"));

        mockMvc.perform(get("/api/files/" + FL_MNG_NO + "/preview"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/files/{flMngNo}/download - 비게시판 파일(읽기권한 통과) → 200")
    @WithMockUser(username = "10001")
    void downloadFile_비게시판파일_읽기권한통과_200() throws Exception {
        // checkReadAccess는 비게시판 파일에서 예외 없이 통과(no-op)
        doNothing().when(fileOwnershipChecker).checkReadAccess(anyString(), any());

        ByteArrayResource resource = new ByteArrayResource("content".getBytes());
        given(fileService.downloadFile(FL_MNG_NO))
                .willReturn(new FileService.FileDownloadResult(resource, "test.pdf", "application/pdf"));

        mockMvc.perform(get("/api/files/" + FL_MNG_NO + "/download"))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────
    // SEC-05 네 읽기 경로 인가 계약 (목록·메타·다운로드·미리보기)
    // 목록: 200 + 허용 파일만 / 단건 세 경로: 거부 403, 허용 200
    // ─────────────────────────────────────────

    /** 거부 대상 파일매핑ID — checkReadAccess가 AccessDeniedException을 던지도록 스텁하는 공통 값. */
    private static final String FL_DENIED = "FL-DENIED";
    /** 허용 대상 파일매핑ID. */
    private static final String FL_OK = "FL-OK";

    /** SEC-05 테스트용 인증 일반 사용자(관리자 아님). */
    private CustomUserDetails normalUser() {
        return new CustomUserDetails("10001", List.of("ITPZZ001"), "DEPT01");
    }

    @Test
    @DisplayName("GET /api/files - 인증 사용자를 fileService.getFiles에 그대로 전달하고 허용 파일만 반환")
    void getFiles_passesAuthenticatedUser_returnsAllowedOnly() throws Exception {
        CustomUserDetails userDetails = normalUser();
        // 서비스가 이미 읽기 권한으로 필터링한 "허용 파일만" 목록을 반환한다고 가정
        given(fileService.getFiles(any(), any())).willReturn(List.of(new FileDto.Response()));

        mockMvc.perform(get("/api/files").with(user(userDetails))
                .param("pkColNm", "요구사항정의서").param("pkCone", "DOC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        // 인증 사용자가 서비스로 정확히 전달되는지 ArgumentCaptor로 검증
        ArgumentCaptor<CustomUserDetails> userCaptor = ArgumentCaptor.forClass(CustomUserDetails.class);
        verify(fileService).getFiles(any(FileDto.SearchCondition.class), userCaptor.capture());
        assertThat(userCaptor.getValue()).isSameAs(userDetails);
    }

    @Test
    @DisplayName("GET /api/files - 목록 거부는 200 + 빈 배열(서비스가 걸러낸 결과)")
    void getFiles_denied_returnsEmptyArray() throws Exception {
        CustomUserDetails userDetails = normalUser();
        given(fileService.getFiles(any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/files").with(user(userDetails))
                .param("pkColNm", "요구사항정의서").param("pkCone", "DOC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId} - 읽기 권한 없음 → 403 (getFile 미호출)")
    void getFile_denied_403() throws Exception {
        CustomUserDetails userDetails = normalUser();
        doThrow(new org.springframework.security.access.AccessDeniedException("파일 읽기 권한이 없습니다."))
                .when(fileOwnershipChecker).checkReadAccess(FL_DENIED, userDetails);

        mockMvc.perform(get("/api/files/" + FL_DENIED).with(user(userDetails)))
                .andExpect(status().isForbidden());

        verify(fileService, never()).getFile(anyString());
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId} - 읽기 권한 허용 → 200 + 메타 DTO")
    void getFile_allowed_200() throws Exception {
        CustomUserDetails userDetails = normalUser();
        given(fileService.getFile(FL_OK)).willReturn(new FileDto.Response());

        mockMvc.perform(get("/api/files/" + FL_OK).with(user(userDetails)))
                .andExpect(status().isOk());

        verify(fileOwnershipChecker).checkReadAccess(FL_OK, userDetails);
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId}/download - 읽기 권한 없음 → 403 (downloadFile 미호출)")
    void downloadFile_denied_403() throws Exception {
        CustomUserDetails userDetails = normalUser();
        doThrow(new org.springframework.security.access.AccessDeniedException("파일 읽기 권한이 없습니다."))
                .when(fileOwnershipChecker).checkReadAccess(FL_DENIED, userDetails);

        mockMvc.perform(get("/api/files/" + FL_DENIED + "/download").with(user(userDetails)))
                .andExpect(status().isForbidden());

        // 권한 거부 시 실제 파일 로드는 절대 수행되지 않아야 한다
        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId}/download - 허용 → 200 + Content-Disposition: attachment")
    void downloadFile_allowed_attachment_200() throws Exception {
        CustomUserDetails userDetails = normalUser();
        ByteArrayResource resource = new ByteArrayResource("content".getBytes());
        given(fileService.downloadFile(FL_OK))
                .willReturn(new FileService.FileDownloadResult(resource, "test.pdf", "application/pdf"));

        mockMvc.perform(get("/api/files/" + FL_OK + "/download").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")));

        verify(fileOwnershipChecker).checkReadAccess(FL_OK, userDetails);
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId}/preview - 읽기 권한 없음 → 403 (downloadFile 미호출)")
    void previewFile_denied_403() throws Exception {
        CustomUserDetails userDetails = normalUser();
        doThrow(new org.springframework.security.access.AccessDeniedException("파일 읽기 권한이 없습니다."))
                .when(fileOwnershipChecker).checkReadAccess(FL_DENIED, userDetails);

        mockMvc.perform(get("/api/files/" + FL_DENIED + "/preview").with(user(userDetails)))
                .andExpect(status().isForbidden());

        verify(fileService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("GET /api/files/{flMpnId}/preview - 허용 → 200 + Content-Disposition: inline")
    void previewFile_allowed_inline_200() throws Exception {
        CustomUserDetails userDetails = normalUser();
        ByteArrayResource resource = new ByteArrayResource("imgdata".getBytes());
        given(fileService.downloadFile(FL_OK))
                .willReturn(new FileService.FileDownloadResult(resource, "photo.png", "image/png"));

        mockMvc.perform(get("/api/files/" + FL_OK + "/preview").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")));

        verify(fileOwnershipChecker).checkReadAccess(FL_OK, userDetails);
    }
}
