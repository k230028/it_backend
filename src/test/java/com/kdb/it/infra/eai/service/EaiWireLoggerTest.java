package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 요청·응답 상세 덤프가 진단에 필요한 값만 남기고 개별부는 가리는지 검증한다. */
class EaiWireLoggerTest {

    private static final Charset MS949 = Charset.forName("MS949");

    private final EaiWireLogger wire = new EaiWireLogger(MS949);

    @Test
    @DisplayName("요청 덤프는 공통부를 필드명=값으로 펼친다")
    void requestDetail_expandsCommonParts() {
        byte[] message = EaiStandardResponseFixture.standardMessage();

        String detail = wire.requestDetail("GwePayload", message);

        assertThat(detail)
                .contains("[시스템공통부]")
                .contains("TGR_VRS_INF='1.0'")
                .contains("MLAN_TC='ko'")
                .contains("[거래공통부]")
                .contains("REQ_RPD_TC='Q'")
                .contains("IF_ID=")
                .contains("[메시지공통부]")
                .contains("MSG_IDCT_TC=");
    }

    @Test
    @DisplayName("요청 덤프는 개별부 값을 남기지 않고 길이만 남긴다")
    void requestDetail_masksIndividualPart() {
        byte[] header = EaiStandardResponseFixture.standardMessage();
        byte[] secret = "홍길동010-1234-5678기밀본문".getBytes(MS949);
        byte[] message = new byte[header.length + secret.length];
        System.arraycopy(header, 0, message, 0, header.length);
        System.arraycopy(secret, 0, message, header.length, secret.length);

        String detail = wire.requestDetail("GwePayload", message);

        int individual = message.length - EaiStandardLayout.HEADER_LEN;
        assertThat(detail)
                .contains("[개별부] GwePayload " + individual + "바이트 (값 미기록), 인코딩:")
                .doesNotContain("홍길동")
                .doesNotContain("010-1234-5678")
                .doesNotContain("기밀본문");
    }

    @Test
    @DisplayName("정상 한글 개별부는 비ASCII 바이트로 잡히고 물음표는 없다")
    void requestDetail_healthyKorean_countsNonAscii() {
        byte[] message = withIndividualPart("결재요청: 전산예산 신청서".getBytes(MS949));

        String detail = wire.requestDetail("GwePayload", message);

        assertThat(detail).contains("비ASCII=22바이트").contains("물음표(0x3F)=0개");
    }

    @Test
    @DisplayName("전문 조립 전에 깨진 문자열은 물음표 바이트로 드러난다")
    void requestDetail_corruptedKorean_countsQuestionMarks() {
        // MS949 가 표현하지 못하는 문자는 인코더가 '?'(0x3F)로 치환한다.
        byte[] broken = "결재요청".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String corrupted = new String(broken, MS949);
        byte[] message = withIndividualPart(corrupted.getBytes(MS949));

        String detail = wire.requestDetail("GwePayload", message);

        assertThat(detail).contains("물음표(0x3F)=");
        assertThat(detail).doesNotContain("물음표(0x3F)=0개");
    }

    /** 골든 헤더 뒤에 주어진 개별부를 붙인 요청 전문. */
    private static byte[] withIndividualPart(byte[] individual) {
        byte[] golden = EaiStandardResponseFixture.standardMessage();
        byte[] message = new byte[EaiStandardLayout.HEADER_LEN + individual.length];
        System.arraycopy(golden, 0, message, 0, EaiStandardLayout.HEADER_LEN);
        System.arraycopy(individual, 0, message, EaiStandardLayout.HEADER_LEN, individual.length);
        return message;
    }

    @Test
    @DisplayName("예비 필드는 잡음이라 덤프에서 제외한다")
    void requestDetail_skipsReservedFields() {
        String detail =
                wire.requestDetail("UmsPayload", EaiStandardResponseFixture.standardMessage());

        assertThat(detail).doesNotContain("SYS_CO_RSRV").doesNotContain("TR_CO_RSRV");
    }

    @Test
    @DisplayName("표준전문 응답은 판정 필드와 상태·헤더를 함께 남긴다")
    void responseDetail_standardMessage() {
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        String detail = wire.responseDetail(200, headers, response);

        assertThat(detail)
                .contains("status=200")
                .contains("len=" + response.length)
                .contains("contentType=application/octet-stream")
                .contains("RLT_TC='2'")
                .contains("MSG_IDCT_TC='1'");
    }

    @Test
    @DisplayName("HER_LEN이 기준보다 길면 그 차이를 메시지부로 보고 오류 문구를 남긴다")
    void responseDetail_messagePart_isRecorded() {
        String gatewayMessage = "SEEAI00007 등록되지 않은 인터페이스입니다";
        byte[] response = responseWithMessagePart(gatewayMessage);

        String detail = wire.responseDetail(200, null, response);

        assertThat(detail)
                .contains("[메시지부]")
                .contains("HER_LEN=" + (EaiStandardLayout.HEADER_LEN + 200))
                .contains("기준 " + EaiStandardLayout.HEADER_LEN)
                .contains(gatewayMessage)
                .contains("[개별부] 2바이트 (값 미기록)");
    }

    @Test
    @DisplayName("메시지부가 없는 응답은 기존대로 개별부 길이만 남긴다")
    void responseDetail_withoutMessagePart_keepsIndividualLength() {
        byte[] response = EaiStandardResponseFixture.standardMessage();

        String detail = wire.responseDetail(204, null, response);

        assertThat(detail)
                .doesNotContain("[메시지부]")
                .contains(
                        "[개별부] "
                                + (response.length - EaiStandardLayout.HEADER_LEN)
                                + "바이트 (값 미기록)");
    }

    /** 헤더 뒤에 메시지부 200바이트와 종료자 2바이트를 붙인 응답. HER_LEN도 함께 늘려 신고한다. */
    private static byte[] responseWithMessagePart(String gatewayMessage) {
        byte[] golden = EaiStandardResponseFixture.standardMessage();
        int headerLen = EaiStandardLayout.HEADER_LEN + 200;
        byte[] response = new byte[headerLen + 2];
        System.arraycopy(golden, 0, response, 0, EaiStandardLayout.HEADER_LEN);
        java.util.Arrays.fill(response, EaiStandardLayout.HEADER_LEN, headerLen, (byte) ' ');
        byte[] text = gatewayMessage.getBytes(MS949);
        System.arraycopy(text, 0, response, EaiStandardLayout.HEADER_LEN, text.length);
        System.arraycopy("@@".getBytes(MS949), 0, response, headerLen, 2);
        byte[] declared = "%08d".formatted(headerLen).getBytes(MS949);
        System.arraycopy(
                declared,
                0,
                response,
                EaiStandardLayout.field(EaiStandardLayout.SYSTEM_COMMON, "HER_LEN").offset(),
                declared.length);
        return response;
    }

    @Test
    @DisplayName("표준전문이 아닌 응답은 16진수와 텍스트 미리보기를 남긴다")
    void responseDetail_nonStandardBody_showsPreview() {
        byte[] body = "<html><title>403 Forbidden</title></html>".getBytes(MS949);

        String detail = wire.responseDetail(200, null, body);

        assertThat(detail)
                .contains("[비표준]")
                .contains("contentType=미상")
                .contains("3C 68 74 6D 6C") // "<html"
                .contains("403 Forbidden");
    }

    @Test
    @DisplayName("빈 응답과 없는 요청 전문도 덤프가 깨지지 않는다")
    void detail_handlesEmptyInput() {
        assertThat(wire.responseDetail(200, null, new byte[0])).contains("본문 없음");
        assertThat(wire.responseDetail(500, null, null)).contains("len=0바이트");
        assertThat(wire.requestDetail("GwePayload", null)).contains("요청 전문 없음");
    }
}
