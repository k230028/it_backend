package com.kdb.it.common.admin.waslog.appender;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 링버퍼에 담기 전에 로그 본문에서 민감값을 가립니다.
 *
 * <p><b>왜 여기인가</b>: {@code /admin/was-logs}는 링버퍼 원문을 그대로 화면과 다운로드로 내보냅니다. 다운로드 파일이 개인 PC로 나가면
 * ADMIN 권한과 감사 로그 외에 통제 수단이 없습니다. 적재 경로가 {@link RingBufferAppender#append}
 * 한 곳뿐이라 여기서 가리면 화면·다운로드가 함께 덮입니다(BE-55).
 *
 * <p><b>파일 로그는 가리지 않습니다.</b> 이 마스킹은 링버퍼에 담기는 사본에만 적용되고, 파일 appender가 쓰는 원문은 그대로입니다. 서버에 남는
 * 로그로 장애를 조사하는 경로를 막지 않으려는 의도적인 범위입니다.
 *
 * <p><b>대상을 좁게 잡았습니다</b> — 토큰류와 주민등록번호 둘뿐입니다. 과하게 가리면 장애 조사가 불가능해집니다. 특히 <b>사번은 가리지
 * 않습니다</b>: 어느 사용자의 요청에서 난 오류인지가 추적의 출발점이기 때문입니다.
 */
final class WasLogMasker {

    /** 가린 자리에 남기는 표시. 무엇이 지워졌는지 알 수 있게 종류를 함께 적는다. */
    private static final String TOKEN_MARK = "[TOKEN]";

    private static final String RRN_MARK = "[RRN]";

    /**
     * JWT — {@code header.payload.signature} 형태의 base64url 3분절.
     *
     * <p>각 분절 길이 하한을 두어 점으로 이어진 평범한 식별자(`a.b.c`)를 잡지 않게 합니다.
     */
    private static final Pattern JWT =
            Pattern.compile("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");

    /** {@code Authorization: Bearer xxx} 형태의 토큰. 스킴은 남기고 값만 가린다. */
    private static final Pattern BEARER =
            Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]{8,}");

    /**
     * 주민등록번호 — {@code YYMMDD-Nxxxxxx}.
     *
     * <p>뒷자리 첫 숫자는 1~4(내국인)·5~8(외국인)만 받아 전화번호·사업자번호 같은 다른 13자리 조합을 덜 잡습니다.
     */
    private static final Pattern RRN =
            Pattern.compile("\\b[0-9]{6}[-\\s]?[1-8][0-9]{6}\\b");

    private WasLogMasker() {}

    /**
     * 로그 한 줄에서 민감값을 가립니다.
     *
     * <p>대부분의 줄에는 가릴 것이 없으므로, 정규식을 돌리기 전에 값싼 사전 검사로 걸러 냅니다 — 이 메서드는 <b>모든 로그 이벤트</b>가 지나는
     * 자리라 평시 비용이 그대로 처리량이 됩니다.
     *
     * @param value 원본 문자열. {@code null}이면 그대로 {@code null}
     * @return 민감값을 표시로 바꾼 문자열. 바꿀 것이 없으면 원본 그대로
     */
    static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String masked = value;
        // 점 없이는 JWT가 성립하지 않고, 숫자 없이는 주민번호가 성립하지 않는다.
        if (masked.indexOf('.') >= 0) {
            masked = JWT.matcher(masked).replaceAll(TOKEN_MARK);
        }
        if (containsIgnoreCase(masked, "bearer") || containsIgnoreCase(masked, "basic")) {
            masked = BEARER.matcher(masked).replaceAll(matcher -> matcher.group(1) + " " + TOKEN_MARK);
        }
        if (hasDigitRun(masked)) {
            masked = RRN.matcher(masked).replaceAll(RRN_MARK);
        }
        return masked;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value.regionMatches(true, 0, needle, 0, needle.length())
                || indexOfIgnoreCase(value, needle) >= 0;
    }

    private static int indexOfIgnoreCase(String value, String needle) {
        int limit = value.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) return i;
        }
        return -1;
    }

    /** 숫자가 6자 이상 이어지는 구간이 있는지. 주민번호 정규식을 돌릴 가치가 있는지 값싸게 본다. */
    private static boolean hasDigitRun(String value) {
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                if (++run >= 6) return true;
            } else {
                run = 0;
            }
        }
        return false;
    }

    /** {@link Matcher}를 직접 노출하지 않기 위한 표시 상수 접근자. 테스트가 쓴다. */
    static String tokenMark() {
        return TOKEN_MARK;
    }

    static String rrnMark() {
        return RRN_MARK;
    }
}
