package com.kdb.it.infra.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.kdb.it.infra.ai.dto.GeminiDto;
import com.kdb.it.infra.file.repository.FileRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * GeminiService 단위 테스트
 *
 * <p>Gemini AI 연동 서비스의 텍스트 생성·파일 첨부 건너뜀·API 오류 경로를 검증합니다. GeminiService는 @Value 파라미터와 내부 RestClient
 * 생성을 사용하므로 @InjectMocks 대신 직접 생성자를 호출하고, RestClient는 ReflectionTestUtils로 교체합니다. uri(String,
 * Object...) 바르그 호출을 우회하기 위해 RETURNS_SELF Answer를 사용합니다. 디스크 I/O가 포함된 파일 첨부 성공 경로는 단위 테스트 범위에서
 * 제외합니다. Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeminiServiceTest {

    @Mock private FileRepository fileRepository;

    private GeminiService geminiService;
    private RestClient mockRestClient;

    /** uri/contentType/body 중간 체인을 모두 자기 자신으로 반환하는 mock */
    private RestClient.RequestBodyUriSpec chainSpec;

    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        geminiService =
                new GeminiService("http://test-api", "test-key", "gemini-test", fileRepository);

        // RETURNS_SELF: uri(), contentType(), body() 등 모든 체인 호출이 chainSpec 자신을 반환
        // → uri(String, Object...) varargs 매칭 문제를 우회
        chainSpec = mock(RestClient.RequestBodyUriSpec.class, RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);
        mockRestClient = mock(RestClient.class);

        given(mockRestClient.post()).willReturn(chainSpec);
        given(chainSpec.retrieve()).willReturn(responseSpec);
        ReflectionTestUtils.setField(geminiService, "restClient", mockRestClient);
    }

    private void stubApiResponse(GeminiDto.GeminiApiResponse response) {
        given(responseSpec.body(GeminiDto.GeminiApiResponse.class)).willReturn(response);
    }

    private GeminiDto.GeminiApiResponse buildSuccessResponse(String text) {
        GeminiDto.Part part = GeminiDto.Part.builder().text(text).build();
        GeminiDto.Content content =
                GeminiDto.Content.builder().role("model").parts(List.of(part)).build();
        GeminiDto.Candidate candidate = new GeminiDto.Candidate(content, "STOP");
        return new GeminiDto.GeminiApiResponse(List.of(candidate), null);
    }

    // ───────────────────────────────────────────────────────
    // generate — 텍스트 응답
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: 첨부파일 없는 요청이면 Gemini 텍스트 응답 DTO를 반환한다")
    void generate_첨부파일없음_텍스트응답반환() {
        stubApiResponse(buildSuccessResponse("AI 응답입니다"));

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        GeminiDto.Response result = geminiService.generate(request);

        assertThat(result.getText()).isEqualTo("AI 응답입니다");
        assertThat(result.getModel()).isEqualTo("gemini-test");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
        assertThat(result.getSkippedFiles()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // generate — 파일 건너뜀
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: DB에 없는 파일관리번호는 skippedFiles에 포함되고 첨부 파일 수는 0이다")
    void generate_DB미존재파일_skippedFiles에포함() {
        given(fileRepository.findByFlMpnIdAndDelYn("FL_NOTEXIST", "N"))
                .willReturn(Optional.empty());
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder().prompt("테스트").flMpnIds(List.of("FL_NOTEXIST")).build();

        GeminiDto.Response result = geminiService.generate(request);

        assertThat(result.getSkippedFiles()).hasSize(1);
        assertThat(result.getSkippedFiles().get(0)).contains("FL_NOTEXIST");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    static Stream<Arguments> incompleteAttachmentMetadata() {
        return Stream.of(
                Arguments.of("저장 경로 null", true, null),
                Arguments.of("저장 경로 공백", true, " "),
                Arguments.of("물리 파일명 null", false, null),
                Arguments.of("물리 파일명 공백", false, " "));
    }

    @ParameterizedTest(name = "generate: {0}이면 파일을 건너뛴다")
    @MethodSource("incompleteAttachmentMetadata")
    void generate_저장경로또는물리파일명없음_메타데이터사유로skip(
            String caseName,
            boolean storagePathMissing,
            String missingValue,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        String flMpnId = "FL_00000030";
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm("첨부문서.pdf")
                        .flPysNm(storagePathMissing ? "server.pdf" : missingValue)
                        .flKpnPth(storagePathMissing ? missingValue : tempDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")).willReturn(Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Response result =
                geminiService.generate(
                        GeminiDto.Request.builder()
                                .prompt("분석")
                                .flMpnIds(List.of(flMpnId))
                                .build());

        assertThat(result.getAttachedFileCount()).isZero();
        assertThat(result.getSkippedFiles())
                .singleElement()
                .asString()
                .contains(flMpnId, "파일 메타데이터 불완전")
                .doesNotContain(tempDir.toString());
    }

    @Test
    @DisplayName("generate: 원본 파일명이 없으면 물리 파일명으로 MIME 타입을 판정해 첨부한다")
    void generate_원본파일명없음_물리파일명Mime폴백(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        String flMpnId = "FL_00000031";
        java.nio.file.Files.writeString(tempDir.resolve("server.png"), "PNG");
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm(null)
                        .flPysNm("server.png")
                        .flKpnPth(tempDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")).willReturn(Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Response result =
                geminiService.generate(
                        GeminiDto.Request.builder()
                                .prompt("분석")
                                .flMpnIds(List.of(flMpnId))
                                .build());

        assertThat(result.getAttachedFileCount()).isEqualTo(1);
        assertThat(result.getSkippedFiles()).isEmpty();
        ArgumentCaptor<GeminiDto.GeminiApiRequest> requestCaptor =
                ArgumentCaptor.forClass(GeminiDto.GeminiApiRequest.class);
        verify(chainSpec).body(requestCaptor.capture());
        assertThat(
                        requestCaptor
                                .getValue()
                                .getContents()
                                .get(0)
                                .getParts()
                                .get(0)
                                .getInlineData()
                                .getMimeType())
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("generate: 기존 한글 DB 경로로 이동된 영문 폴더의 파일을 첨부한다")
    void generate_기존한글DB경로_영문폴더파일첨부(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        String flMpnId = "FL_00000032";
        java.nio.file.Path legacyStorageDir = tempDir.resolve("요구사항정의서");
        java.nio.file.Path movedStorageDir = tempDir.resolve("requirement-documents");
        java.nio.file.Files.createDirectories(movedStorageDir);
        java.nio.file.Files.writeString(movedStorageDir.resolve("server.pdf"), "PDF");
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId(flMpnId)
                        .flNm("요구사항정의서.pdf")
                        .flPysNm("server.pdf")
                        .flKpnPth(legacyStorageDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")).willReturn(Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Response result =
                geminiService.generate(
                        GeminiDto.Request.builder()
                                .prompt("분석")
                                .flMpnIds(List.of(flMpnId))
                                .build());

        assertThat(result.getAttachedFileCount()).isEqualTo(1);
        assertThat(result.getSkippedFiles()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // generate — API 오류
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: Gemini API가 null을 반환하면 RuntimeException을 던진다")
    void generate_API응답null_RuntimeException발생() {
        stubApiResponse(null);

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API");
    }

    // ───────────────────────────────────────────────────────
    // generate — RestClientException (API 호출 실패)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: RestClientException 발생 시 원인 포함 RuntimeException을 던진다")
    void generate_RestClientException_RuntimeException발생() {
        // Arrange: API 호출 시 네트워크/서버 오류 모의
        given(responseSpec.body(GeminiDto.GeminiApiResponse.class))
                .willThrow(new RestClientException("Connection refused"));

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        // Act & Assert: 원본 예외가 cause로 감싸져 RuntimeException 재발생
        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API 호출 중 오류")
                .hasCauseInstanceOf(RestClientException.class);
    }

    // ───────────────────────────────────────────────────────
    // generate — 빈 candidates 배열 (응답 파싱 오류)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: candidates 배열이 비어있으면 RuntimeException을 던진다")
    void generate_빈candidates배열_RuntimeException발생() {
        // Arrange: candidates가 비어있는 응답 (정상 HTTP 응답이지만 내용 없음)
        GeminiDto.GeminiApiResponse emptyResponse =
                new GeminiDto.GeminiApiResponse(List.of(), null);
        stubApiResponse(emptyResponse);

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        // Act & Assert: 빈 응답 파싱 불가 → 예외 발생
        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("비어있습니다");
    }

    @Test
    @DisplayName("generate: candidate의 content.parts가 비어있으면 RuntimeException을 던진다")
    void generate_빈parts배열_RuntimeException발생() {
        // Arrange: parts 없는 content를 가진 candidate
        GeminiDto.Content emptyContent =
                GeminiDto.Content.builder().role("model").parts(List.of()).build();
        GeminiDto.Candidate candidate = new GeminiDto.Candidate(emptyContent, "STOP");
        GeminiDto.GeminiApiResponse response =
                new GeminiDto.GeminiApiResponse(List.of(candidate), null);
        stubApiResponse(response);

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        // Act & Assert: content.parts가 비어있어 파싱 불가 → 예외 발생
        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("파싱할 수 없습니다");
    }

    @Test
    @DisplayName("generate: candidate의 content가 null이면 RuntimeException을 던진다")
    void generate_content_null_RuntimeException발생() {
        // Arrange: content가 null인 candidate (API 오류 응답 — SAFETY 필터 등)
        GeminiDto.Candidate candidateNoContent = new GeminiDto.Candidate(null, "SAFETY");
        GeminiDto.GeminiApiResponse response =
                new GeminiDto.GeminiApiResponse(List.of(candidateNoContent), null);
        stubApiResponse(response);

        GeminiDto.Request request = GeminiDto.Request.builder().prompt("테스트 프롬프트").build();

        // Act & Assert: content==null 분기 → "파싱할 수 없습니다" 메시지와 함께 예외
        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("파싱할 수 없습니다");
    }

    // ───────────────────────────────────────────────────────
    // generate — systemInstruction 분기
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: systemInstruction이 있으면 결과 DTO에 응답 텍스트가 정상 포함된다")
    void generate_systemInstruction있음_텍스트응답반환() {
        // Arrange: 시스템 지시문 포함 요청 — buildApiRequest 내 if 분기 진입
        stubApiResponse(buildSuccessResponse("시스템 지시 포함 응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder()
                        .prompt("요구사항 분석")
                        .systemInstruction("당신은 IT 프로젝트 분석 전문가입니다.")
                        .build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: systemInstruction이 있어도 응답 텍스트는 동일하게 추출
        assertThat(result.getText()).isEqualTo("시스템 지시 포함 응답");
        assertThat(result.getModel()).isEqualTo("gemini-test");
    }

    // ───────────────────────────────────────────────────────
    // generate — flMpnIds 복수 파일 일부 미존재
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: 여러 파일관리번호 중 전부 DB 미존재 시 모두 skippedFiles에 포함된다")
    void generate_복수파일전부미존재_모두skip() {
        // Arrange: 두 파일 모두 DB에 없음
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000010", "N"))
                .willReturn(java.util.Optional.empty());
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000011", "N"))
                .willReturn(java.util.Optional.empty());
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder()
                        .prompt("분석")
                        .flMpnIds(List.of("FL_00000010", "FL_00000011"))
                        .build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: 두 파일 모두 skip, 첨부 파일 수 0
        assertThat(result.getSkippedFiles()).hasSize(2);
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // buildFilePartFromFlMngNo — 파일명에 점(.) 없음 → MIME null → skip
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: 파일명에 확장자(점) 없으면 skippedFiles에 포함된다")
    void generate_확장자없는파일명_skip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        // Arrange: 파일명에 점이 없어 detectMimeType이 null 반환 → skip 분기 진입
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId("FL_00000020")
                        .flNm("확장자없는파일명")
                        .flPysNm("SVR1_확장자없는파일명")
                        .flKpnPth(tempDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000020", "N"))
                .willReturn(java.util.Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder().prompt("테스트").flMpnIds(List.of("FL_00000020")).build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: 미지원 형식 → skip, 첨부 파일 수 0
        assertThat(result.getSkippedFiles()).hasSize(1);
        assertThat(result.getSkippedFiles().get(0)).contains("FL_00000020");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // buildFilePartFromFlMngNo — 미지원 MIME 타입(hwp) → skip
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: 미지원 확장자(hwp) 파일은 skippedFiles에 포함된다")
    void generate_미지원확장자hwp_skip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        // Arrange: .hwp는 SUPPORTED_MIME_TYPES에 없으므로 detectMimeType이 null 반환 → skip
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId("FL_00000021")
                        .flNm("문서.hwp")
                        .flPysNm("SVR1_문서.hwp")
                        .flKpnPth(tempDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000021", "N"))
                .willReturn(java.util.Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder().prompt("분석").flMpnIds(List.of("FL_00000021")).build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: hwp는 미지원 → skip
        assertThat(result.getSkippedFiles()).hasSize(1);
        assertThat(result.getSkippedFiles().get(0)).contains("FL_00000021");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // buildFilePartFromFlMngNo — 디스크에 파일 없음 → skip
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: DB에 경로가 있어도 디스크에 실제 파일이 없으면 skippedFiles에 포함된다")
    void generate_디스크파일없음_skip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        // Arrange: 지원 확장자(pdf)이지만 실제 디스크에는 파일 없음
        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId("FL_00000022")
                        .flNm("계획서.pdf")
                        .flPysNm("SVR1_계획서.pdf")
                        .flKpnPth(tempDir.toString()) // 디렉토리만 있고 파일 없음
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000022", "N"))
                .willReturn(java.util.Optional.of(filem));
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request =
                GeminiDto.Request.builder().prompt("검토").flMpnIds(List.of("FL_00000022")).build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: 디스크에 없음 → skip
        assertThat(result.getSkippedFiles()).hasSize(1);
        assertThat(result.getSkippedFiles().get(0)).contains("FL_00000022");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // buildFilePartFromFlMngNo — 성공 경로: 실제 pdf 파일 읽기 + Base64 첨부
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: 유효한 pdf 파일이 디스크에 있으면 첨부 파일 수가 1이다")
    void generate_유효pdf파일_첨부성공(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir)
            throws Exception {
        // Arrange: 실제 파일을 임시 디렉토리에 생성
        java.nio.file.Path pdfFile = tempDir.resolve("SVR1_계획서.pdf");
        java.nio.file.Files.write(
                pdfFile, "PDF content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        com.kdb.it.infra.file.entity.Cfilem filem =
                com.kdb.it.infra.file.entity.Cfilem.builder()
                        .flMpnId("FL_00000023")
                        .flNm("계획서.pdf")
                        .flPysNm("SVR1_계획서.pdf")
                        .flKpnPth(tempDir.toString())
                        .build();
        given(fileRepository.findByFlMpnIdAndDelYn("FL_00000023", "N"))
                .willReturn(java.util.Optional.of(filem));
        stubApiResponse(buildSuccessResponse("AI 분석 결과"));

        GeminiDto.Request request =
                GeminiDto.Request.builder().prompt("분석해줘").flMpnIds(List.of("FL_00000023")).build();

        // Act
        GeminiDto.Response result = geminiService.generate(request);

        // Assert: 파일 첨부 성공 → attachedFileCount = 1, skippedFiles 비어있음
        assertThat(result.getAttachedFileCount()).isEqualTo(1);
        assertThat(result.getSkippedFiles()).isEmpty();
        assertThat(result.getText()).isEqualTo("AI 분석 결과");
    }
}
