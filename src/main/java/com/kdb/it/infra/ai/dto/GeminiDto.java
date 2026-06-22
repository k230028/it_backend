package com.kdb.it.infra.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Gemini API 연동 관련 DTO 클래스 모음
 *
 * <p>
 * 프론트엔드 ↔ 백엔드 ↔ Gemini API 간 데이터 전달에 사용합니다.
 * </p>
 */
public class GeminiDto {

    /**
     * 프론트엔드 → 백엔드 요청 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GeminiRequest", description = "Gemini AI 요청")
    public static class Request {

        /** 사용자 프롬프트 */
        @Schema(description = "Gemini에게 전달할 프롬프트", example = "요구사항 정의서 작성을 도와줘")
        @NotBlank(message = "프롬프트는 필수입니다.")
        @Size(max = 10000, message = "프롬프트는 10000자를 초과할 수 없습니다.")
        private String prompt;

        /**
         * 시스템 지시문 (선택)
         * Gemini에게 역할이나 응답 방식을 사전에 지정할 때 사용합니다.
         */
        @Schema(description = "시스템 지시문 (선택)", example = "당신은 IT 프로젝트 요구사항 분석 전문가입니다.")
        @Size(max = 4000, message = "시스템 지시문은 4000자를 초과할 수 없습니다.")
        private String systemInstruction;

        /**
         * 첨부파일 관리번호 목록 (선택)
         *
         * <p>
         * TPRMPP_CFILEM에 저장된 파일의 관리번호를 전달하면
         * 해당 파일을 Base64로 인코딩하여 Gemini에 함께 전송합니다.
         * </p>
         *
         * <p>
         * Gemini 지원 파일 형식: 이미지(jpg/png/gif/webp), PDF
         * 미지원 형식(hwp, doc 등)은 무시됩니다.
         * 파일당 최대 20MB 제한.
         * </p>
         */
        @Schema(description = "첨부파일 매핑ID 목록 (선택, 최대 10개, 예: [\"FL_00000001\", \"FL_00000002\"])")
        @Size(max = 10, message = "첨부파일은 최대 10개까지 가능합니다.")
        private List<String> flMpnIds;
    }

    /**
     * 백엔드 → 프론트엔드 응답 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "GeminiResponse", description = "Gemini AI 응답")
    public static class Response {

        /** Gemini 응답 텍스트 */
        @Schema(description = "Gemini 응답 텍스트")
        private String text;

        /** 사용된 모델명 */
        @Schema(description = "사용된 Gemini 모델명")
        private String model;

        /**
         * 실제로 Gemini에 첨부된 파일 수
         * 0이면 첨부 파일 없이 텍스트만 전송된 것
         */
        @Schema(description = "Gemini에 실제 첨부된 파일 수", example = "2")
        private int attachedFileCount;

        /**
         * 건너뛴 파일 목록 (미지원 형식, 파일 미존재 등)
         * 프론트에서 사용자에게 안내하는 용도
         */
        @Schema(description = "첨부 실패한 파일 목록 (flMngNo: 사유)")
        private List<String> skippedFiles;
    }

    // ===== Gemini API 내부 요청/응답 구조 (직렬화용) =====

    /** Gemini API generateContent 요청 바디 */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeminiApiRequest {
        private List<Content> contents;
        private SystemInstruction systemInstruction;
        private GenerationConfig generationConfig;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemInstruction {
        private List<Part> parts;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        private String role;
        private List<Part> parts;
    }

    /**
     * Gemini API Part: 텍스트 또는 파일(inlineData) 중 하나를 담습니다.
     * null 필드는 JSON 직렬화에서 제외합니다.
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Part {
        /** 텍스트 파트 */
        private String text;

        /**
         * 파일 파트 (Base64 인코딩된 파일 데이터)
         * text와 inlineData 중 하나만 설정합니다.
         */
        private InlineData inlineData;
    }

    /**
     * Gemini API inlineData: Base64 인코딩 파일 데이터
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InlineData {
        /** MIME 타입 (예: image/jpeg, application/pdf) */
        private String mimeType;
        /** Base64 인코딩된 파일 바이너리 */
        private String data;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationConfig {
        private Double temperature;
        private Integer maxOutputTokens;
    }

    /** Gemini API generateContent 응답 바디 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiApiResponse {
        private List<Candidate> candidates;
        private UsageMetadata usageMetadata;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;
        private String finishReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageMetadata {
        private Integer promptTokenCount;
        private Integer candidatesTokenCount;
        private Integer totalTokenCount;
    }
}
