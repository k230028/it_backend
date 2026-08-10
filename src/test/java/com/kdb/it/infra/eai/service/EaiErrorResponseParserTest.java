package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EaiErrorResponseParserTest {

    private static final Charset MS949 = Charset.forName("MS949");

    @Test
    @DisplayName("EAI 오류 플래그와 SEEAI 코드를 함께 만족하면 안전한 오류를 반환한다")
    void parse_validErrorResponse_returnsCode() {
        byte[] response = errorResponse("SEEAI00006");

        assertThat(new EaiErrorResponseParser().parse(response, MS949))
                .contains("EAI 오류: SEEAI00006");
    }

    @Test
    @DisplayName("오류 플래그가 아니면 전문의 SEEAI 문자열을 오류로 오인하지 않는다")
    void parse_withoutErrorFlags_returnsEmpty() {
        byte[] response = errorResponse("SEEAI00006");
        response[241] = '0';

        assertThat(new EaiErrorResponseParser().parse(response, MS949)).isEmpty();
    }

    private byte[] errorResponse(String errorCode) {
        byte[] response = " ".repeat(1100).getBytes(MS949);
        response[241] = '2';
        response[1013] = '1';
        byte[] code = errorCode.getBytes(MS949);
        System.arraycopy(code, 0, response, 1020, code.length);
        return response;
    }
}
