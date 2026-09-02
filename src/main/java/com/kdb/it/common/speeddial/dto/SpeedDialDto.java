package com.kdb.it.common.speeddial.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** 스피드다이얼 전용 FAQ·Q&A API DTO입니다. */
public final class SpeedDialDto {

    private SpeedDialDto() {}

    @Schema(name = "SpeedDialFaqResponse", description = "스피드다이얼 FAQ 항목")
    public record FaqResponse(
            String postId, String title, String contentHtml, LocalDateTime createdAt) {}

    @Schema(name = "SpeedDialQnaCreateRequest", description = "스피드다이얼 문의 등록 요청")
    public record QnaCreateRequest(
            @NotBlank @Size(max = 90) String title,
            @NotBlank @Size(max = 200) String screenName,
            @NotBlank
                    @Size(max = 300)
                    @Pattern(regexp = "^/(?!/)(?!.*://).*", message = "화면 URL은 내부 경로만 입력할 수 있습니다.")
                    String screenUrl,
            @NotBlank @Pattern(regexp = "BUDGET|PROJECT|IMPROVEMENT|BUG|OTHER") String category,
            boolean privatePost,
            @NotBlank @Size(max = 4000) String content) {

        /** 기존 내부 호출이 문의 제목 없이도 컴파일되도록 화면명을 제목으로 보완합니다. */
        public QnaCreateRequest(
                String screenName, String screenUrl, String category, String content) {
            this(screenName, screenName, screenUrl, category, false, content);
        }
    }

    @Schema(name = "SpeedDialQnaCreateResponse", description = "스피드다이얼 문의 등록 결과")
    public record QnaCreateResponse(String postId) {}
}
