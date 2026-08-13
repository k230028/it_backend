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
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);

        assertThat(new EaiErrorResponseParser().parse(response, MS949))
                .contains("EAI 오류: SEEAI00006");
    }

    @Test
    @DisplayName("오류 플래그가 아니면 전문의 SEEAI 문자열을 오류로 오인하지 않는다")
    void parse_withoutErrorFlags_returnsEmpty() {
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);
        response[EaiStandardResponseFixture.rltTcOffset()] = '0';

        assertThat(new EaiErrorResponseParser().parse(response, MS949)).isEmpty();
    }

    @Test
    @DisplayName("null이거나 표준 헤더 길이에 못 미치는 응답은 오류로 해석하지 않는다")
    void parse_nullOrShortResponse_returnsEmpty() {
        EaiErrorResponseParser parser = new EaiErrorResponseParser();
        byte[] reference = EaiStandardResponseFixture.standardMessage();
        int msgIdctTc = EaiStandardResponseFixture.msgIdctTcOffset(reference, MS949);

        assertThat(parser.parse(null, MS949)).isEmpty();
        assertThat(parser.parse(new byte[0], MS949)).isEmpty();
        assertThat(parser.parse(" ".repeat(msgIdctTc).getBytes(MS949), MS949)).isEmpty();
    }

    @Test
    @DisplayName("메시지 구분 코드가 오류가 아니면 빈 값을 반환한다")
    void parse_withoutMessageIndicator_returnsEmpty() {
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);
        response[EaiStandardResponseFixture.msgIdctTcOffset(response, MS949)] = '0';

        assertThat(new EaiErrorResponseParser().parse(response, MS949)).isEmpty();
    }

    @Test
    @DisplayName("오류 플래그만 있고 SEEAI 코드가 없으면 빈 값을 반환한다")
    void parse_withoutErrorCode_returnsEmpty() {
        byte[] response = EaiStandardResponseFixture.flaggedResponse(MS949);

        assertThat(new EaiErrorResponseParser().parse(response, MS949)).isEmpty();
    }

    @Test
    @DisplayName("진단 요약은 판정 근거 위치의 값과 SEEAI 코드 유무만 남긴다")
    void diagnostics_summarizesDecisionBytes() {
        byte[] response = EaiStandardResponseFixture.standardMessage();

        String diagnostics = new EaiErrorResponseParser().diagnostics(response, MS949);

        assertThat(diagnostics)
                .contains("len=" + response.length)
                .contains("rltTc=0x20")
                .contains("msgIdctTc=0x20")
                .contains("seeai=없음");
    }

    @Test
    @DisplayName("진단 요약은 오류 응답의 플래그와 코드를 그대로 보여준다")
    void diagnostics_showsErrorFlagsAndCode() {
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);

        assertThat(new EaiErrorResponseParser().diagnostics(response, MS949))
                .contains("rltTc='2'")
                .contains("msgIdctTc='1'")
                .contains("seeai=SEEAI00006");
    }

    @Test
    @DisplayName("표준전문이 아닌 짧은 응답도 진단은 길이와 범위 초과를 알려준다")
    void diagnostics_handlesShortResponse() {
        EaiErrorResponseParser parser = new EaiErrorResponseParser();

        assertThat(parser.diagnostics(null, MS949)).contains("응답 없음");
        assertThat(parser.diagnostics(new byte[0], MS949))
                .contains("len=0")
                .contains("rltTc=범위밖")
                .contains("msgIdctTc=범위밖");
    }
}
