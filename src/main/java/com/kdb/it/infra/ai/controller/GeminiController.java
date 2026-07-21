package com.kdb.it.infra.ai.controller;

import com.kdb.it.infra.ai.dto.GeminiDto;
import com.kdb.it.infra.ai.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gemini AI API 프록시 컨트롤러
 *
 * <p>프론트엔드의 Gemini API 요청을 백엔드에서 중계(프록시)합니다. API 키는 서버에서 안전하게 관리됩니다.
 *
 * <p>기본 URL: {@code /api/gemini}
 *
 * <p>보안: JWT 토큰 인증 필요
 */
@RestController
@RequestMapping("/api/gemini")
@RequiredArgsConstructor
@Tag(name = "Gemini", description = "Gemini AI API")
@PreAuthorize("hasRole('ADMIN')")
public class GeminiController {

    /** Gemini API 연동 서비스 */
    private final GeminiService geminiService;

    /**
     * Gemini AI에 프롬프트를 전달하고 응답을 반환합니다.
     *
     * <p>요청 예시:
     *
     * <pre>{@code
     * POST /api/gemini/generate
     * {
     *   "prompt": "요구사항 정의서 작성을 도와줘",
     *   "systemInstruction": "당신은 IT 프로젝트 요구사항 분석 전문가입니다."
     * }
     * }</pre>
     *
     * @param request 프롬프트와 시스템 지시문이 담긴 요청 DTO
     * @return HTTP 200 + Gemini 응답 텍스트
     */
    @PostMapping("/generate")
    @Operation(
            summary = "Gemini AI 응답 생성",
            description =
                    "프롬프트를 Gemini API에 전달하고 AI 응답을 반환합니다. "
                            + "systemInstruction은 선택 사항으로, AI의 역할이나 응답 방식을 지정합니다.")
    public ResponseEntity<GeminiDto.Response> generate(
            @Valid @RequestBody GeminiDto.Request request) {
        GeminiDto.Response response = geminiService.generate(request);
        return ResponseEntity.ok(response);
    }
}
