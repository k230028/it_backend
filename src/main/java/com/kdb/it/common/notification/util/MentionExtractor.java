package com.kdb.it.common.notification.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 멘션 추출 유틸리티
 *
 * <p>
 * 게시물·댓글 본문(HTML, {@code HtmlSanitizer.sanitize()} 통과 결과)에서
 * {@code @사번} 형태의 멘션을 식별하여 수신자 사번 집합으로 반환한다.
 * </p>
 *
 * <p>
 * 정책:
 * </p>
 * <ul>
 *   <li>KDB 사번 형식 멘션만 지원: {@code @K} + 숫자 6~8자리 (예: {@code @K140024}).
 *       다른 영숫자 토큰(예: {@code @ADMIN001}, {@code @1234567})은 패턴 단계에서 제외된다.</li>
 *   <li>작성자 본인 멘션은 자동 제외</li>
 *   <li>중복 사번은 제거 (입력 순서 보존)</li>
 *   <li>HTML 태그 안의 영숫자 토큰은 속성 구분자(공백·따옴표 등)로 끊기므로 sanitize된 HTML에 직접 적용해도 안전</li>
 *   <li>본 클래스는 패턴 추출만 담당. 추출된 사번이 실제 {@code TPRMPP_CUSERI}에 존재하는지는
 *       호출자(예: BoardPostService.publishMentionNotifications)가 UserRepository로 별도 검증한다.</li>
 * </ul>
 */
public final class MentionExtractor {

    /**
     * 멘션 패턴: {@code @K + 숫자 6~8자리} (총 7~9자리).
     * 예: {@code @K140024}, {@code @K12345678}
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(K\\d{6,8})");

    private MentionExtractor() {}

    /**
     * 본문에서 멘션 사번을 추출한다.
     *
     * @param content   본문 텍스트 또는 HTML (null 허용)
     * @param authorEno 작성자 사번 (자기 멘션 제외용, null이면 모든 매치 포함)
     * @return 추출된 수신자 사번 집합 (입력 순서 보존). 추출 결과가 없으면 빈 집합 반환
     */
    public static Set<String> extractEnos(String content, String authorEno) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Matcher matcher = MENTION_PATTERN.matcher(content);
        Set<String> recipients = new LinkedHashSet<>();
        while (matcher.find()) {
            String eno = matcher.group(1);
            if (authorEno != null && eno.equals(authorEno)) {
                continue;
            }
            recipients.add(eno);
        }
        return recipients;
    }
}
