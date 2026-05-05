package com.kdb.it.infra.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.kdb.it.infra.ai.dto.GeminiDto;
import com.kdb.it.infra.file.repository.FileRepository;

/**
 * GeminiService 단위 테스트
 *
 * <p>
 * Gemini AI 연동 서비스의 텍스트 생성·파일 첨부 건너뜀·API 오류 경로를 검증합니다.
 * GeminiService는 @Value 파라미터와 내부 RestClient 생성을 사용하므로
 * @InjectMocks 대신 직접 생성자를 호출하고, RestClient는 ReflectionTestUtils로 교체합니다.
 * uri(String, Object...) 바르그 호출을 우회하기 위해 RETURNS_SELF Answer를 사용합니다.
 * 디스크 I/O가 포함된 파일 첨부 성공 경로는 단위 테스트 범위에서 제외합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeminiServiceTest {

    @Mock
    private FileRepository fileRepository;

    private GeminiService geminiService;
    private RestClient mockRestClient;

    /** uri/contentType/body 중간 체인을 모두 자기 자신으로 반환하는 mock */
    private RestClient.RequestBodyUriSpec chainSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        geminiService = new GeminiService("http://test-api", "test-key", "gemini-test", fileRepository);

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
        GeminiDto.Content content = GeminiDto.Content.builder()
                .role("model")
                .parts(List.of(part))
                .build();
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

        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("테스트 프롬프트")
                .build();

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
        given(fileRepository.findByFlMngNoAndDelYn("FL_NOTEXIST", "N")).willReturn(Optional.empty());
        stubApiResponse(buildSuccessResponse("응답"));

        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("테스트")
                .flMngNos(List.of("FL_NOTEXIST"))
                .build();

        GeminiDto.Response result = geminiService.generate(request);

        assertThat(result.getSkippedFiles()).hasSize(1);
        assertThat(result.getSkippedFiles().get(0)).contains("FL_NOTEXIST");
        assertThat(result.getAttachedFileCount()).isEqualTo(0);
    }

    // ───────────────────────────────────────────────────────
    // generate — API 오류
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generate: Gemini API가 null을 반환하면 RuntimeException을 던진다")
    void generate_API응답null_RuntimeException발생() {
        stubApiResponse(null);

        GeminiDto.Request request = GeminiDto.Request.builder()
                .prompt("테스트 프롬프트")
                .build();

        assertThatThrownBy(() -> geminiService.generate(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Gemini API");
    }
}
