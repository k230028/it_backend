package com.kdb.it.infra.eai.service;

import java.nio.charset.Charset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** EAI HTTP 200 오류 응답의 표준 헤더와 메시지 코드를 해석합니다. */
final class EaiErrorResponseParser {

    private static final int RLT_TC_OFFSET = 241;
    private static final int MSG_IDCT_TC_OFFSET = 1013;
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
}
