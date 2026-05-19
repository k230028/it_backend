package com.kdb.it.common.notification.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MentionExtractor 단위 테스트.
 *
 * <p>정책 검증:</p>
 * <ul>
 *   <li>{@code @4~14자리 숫자} 패턴 추출</li>
 *   <li>작성자 본인 사번은 제외</li>
 *   <li>중복 사번 제거 (LinkedHashSet으로 순서 보존)</li>
 *   <li>null/빈 입력은 빈 집합 반환</li>
 * </ul>
 */
class MentionExtractorTest {

    @Nested
    @DisplayName("기본 추출 동작")
    class BasicExtraction {

        @Test
        @DisplayName("@사번 패턴이 1건 있으면 1건 추출한다")
        void extractsSingleMention() {
            Set<String> result = MentionExtractor.extractEnos("@1234567 검토 부탁드립니다", null);
            assertThat(result).containsExactly("1234567");
        }

        @Test
        @DisplayName("영문 prefix 사번(K140024 등) 멘션도 추출한다")
        void extractsAlphanumericMention() {
            Set<String> result = MentionExtractor.extractEnos("33\n@K140024", null);
            assertThat(result).containsExactly("K140024");
        }

        @Test
        @DisplayName("자격등급 형식(ADMIN001) 멘션도 추출한다")
        void extractsRoleStyleMention() {
            Set<String> result = MentionExtractor.extractEnos("@ADMIN001 확인 바랍니다", null);
            assertThat(result).containsExactly("ADMIN001");
        }

        @Test
        @DisplayName("여러 멘션은 입력 순서대로 모두 추출한다")
        void extractsMultipleMentionsInOrder() {
            Set<String> result = MentionExtractor.extractEnos(
                "@K140024 그리고 @K140025 검토 부탁드립니다",
                null
            );
            assertThat(result).containsExactly("K140024", "K140025");
        }

        @Test
        @DisplayName("동일 사번이 반복되면 1건으로 중복 제거된다")
        void deduplicatesRepeatedEno() {
            Set<String> result = MentionExtractor.extractEnos(
                "@K140024 다시 한 번 @K140024",
                null
            );
            assertThat(result).containsExactly("K140024");
        }

        @Test
        @DisplayName("@뒤에 4자리 미만 토큰은 매칭되지 않는다")
        void rejectsTooShortToken() {
            assertThat(MentionExtractor.extractEnos("@123 안녕", null)).isEmpty();
            assertThat(MentionExtractor.extractEnos("@K12 안녕", null)).isEmpty();
        }

        @Test
        @DisplayName("HTML 태그가 섞여 있어도 본문의 @사번을 추출한다")
        void extractsFromHtmlContent() {
            String html = "<p>안녕하세요 <strong>@K140024</strong> 검토 부탁드립니다.</p>";
            Set<String> result = MentionExtractor.extractEnos(html, null);
            assertThat(result).containsExactly("K140024");
        }
    }

    @Nested
    @DisplayName("자기 멘션 제외")
    class SelfMentionExclusion {

        @Test
        @DisplayName("작성자 본인 사번은 결과에서 제외된다")
        void excludesAuthor() {
            Set<String> result = MentionExtractor.extractEnos(
                "@K140025 작성자입니다 @K140024",
                "K140025"
            );
            assertThat(result).containsExactly("K140024");
        }

        @Test
        @DisplayName("authorEno가 null이면 모든 매치를 포함한다")
        void includesAllWhenAuthorIsNull() {
            Set<String> result = MentionExtractor.extractEnos("@K140024 @K140025", null);
            assertThat(result).containsExactly("K140024", "K140025");
        }
    }

    @Nested
    @DisplayName("경계 케이스")
    class EdgeCases {

        @Test
        @DisplayName("null 입력은 빈 집합을 반환한다")
        void nullInputReturnsEmpty() {
            assertThat(MentionExtractor.extractEnos(null, null)).isEmpty();
        }

        @Test
        @DisplayName("빈 문자열은 빈 집합을 반환한다")
        void emptyInputReturnsEmpty() {
            assertThat(MentionExtractor.extractEnos("", null)).isEmpty();
            assertThat(MentionExtractor.extractEnos("   ", null)).isEmpty();
        }

        @Test
        @DisplayName("멘션이 없는 텍스트는 빈 집합을 반환한다")
        void noMentionReturnsEmpty() {
            assertThat(MentionExtractor.extractEnos("일반 텍스트입니다.", null)).isEmpty();
        }
    }
}
