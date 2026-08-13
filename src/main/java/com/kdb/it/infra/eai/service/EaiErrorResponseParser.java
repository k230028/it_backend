package com.kdb.it.infra.eai.service;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** EAI HTTP 200 오류 응답의 표준 헤더와 메시지 코드를 해석합니다. */
final class EaiErrorResponseParser {

    /**
     * 결과구분코드(RLT_TC) 위치 — 시스템공통부 180 + 거래공통부 내 41(거래ID 10 + 수신시스템코드 3 + 화면ID 10 + 연계화면ID 10 +
     * 화면제어구분 1 + 요청응답구분 1 + 세부유형구분 1 + 채널유형 2 + 메시지채널 2 + 동기처리구분 1).
     */
    private static final int RLT_TC_OFFSET = 221;

    /** 메시지표시방법구분코드(MSG_IDCT_TC) 위치 — 시스템공통부 180 + 거래공통부 400 + 채널공통부 400 + 책임자승인공통부 63. */
    private static final int MSG_IDCT_TC_OFFSET = 1043;

    private static final Pattern ERROR_CODE = Pattern.compile("SEEAI\\d{5}");

    Optional<String> parse(byte[] response, Charset charset) {
        if (response == null || response.length <= MSG_IDCT_TC_OFFSET) {
            return Optional.empty();
        }
        if (response[RLT_TC_OFFSET] != '2' || response[MSG_IDCT_TC_OFFSET] != '1') {
            return Optional.empty();
        }

        Matcher matcher = ERROR_CODE.matcher(new String(response, charset));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of("EAI 오류: " + matcher.group());
    }

    /**
     * 오류로 해석하지 못한 응답의 진단 요약을 만듭니다.
     *
     * <p>전문 전체 대신 판정 근거가 되는 두 위치의 값과 SEEAI 코드 유무만 남겨, 응답이 표준전문이 아닌지(길이 부족) 플래그 값이 다른지 구분할 수 있게 합니다.
     * 개별부(수신자·본문)는 포함하지 않습니다.
     *
     * @param response 게이트웨이 응답 바이트. null 허용
     * @param charset 전문 문자셋
     * @return 로그 전용 요약 문자열
     */
    String diagnostics(byte[] response, Charset charset) {
        if (response == null) {
            return "응답 없음";
        }
        Matcher matcher = ERROR_CODE.matcher(new String(response, charset));
        return "len=%d, rltTc=%s, msgIdctTc=%s, seeai=%s"
                .formatted(
                        response.length,
                        describeByte(response, RLT_TC_OFFSET),
                        describeByte(response, MSG_IDCT_TC_OFFSET),
                        matcher.find() ? matcher.group() : "없음");
    }

    /** 판정 바이트를 로그 안전한 형태로 표기 — 출력 가능한 ASCII만 그대로 두고 나머지는 16진수로 남긴다. */
    private static String describeByte(byte[] response, int offset) {
        if (offset >= response.length) {
            return "범위밖";
        }
        byte value = response[offset];
        boolean printable = value > 0x20 && value < 0x7F;
        return printable ? "'" + (char) value + "'" : "0x%02X".formatted(value);
    }
}
