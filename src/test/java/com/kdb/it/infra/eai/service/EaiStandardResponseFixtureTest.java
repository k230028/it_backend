package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 픽스처가 유도한 오프셋이 실제 표준전문 레이아웃과 맞는지 동결 골든 전문으로 검증한다.
 *
 * <p>이 검증이 없으면 파서와 픽스처가 같은 매직넘버를 공유해 오프셋이 틀려도 전 테스트가 통과한다.
 */
class EaiStandardResponseFixtureTest {

    private static final Charset MS949 = Charset.forName("MS949");

    /** 골든 전문 조립 시각(고정 Clock) — {@code EaiMessageBuilderTest}와 동일. */
    private static final String GOLDEN_REQ_DTM = "20260607093015123";

    @Test
    @DisplayName("골든 전문이 신고한 길이가 실제 바이트 길이와 일치한다")
    void goldenMessage_declaresOwnLength() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();

        int whole = Integer.parseInt(new String(golden, 0, 8, MS949).trim());

        assertThat(whole).isEqualTo(golden.length);
        assertThat(EaiStandardResponseFixture.headerLen(golden, MS949)).isLessThan(golden.length);
    }

    @Test
    @DisplayName("유도한 RLT_TC 위치는 요청일시 앵커로 확인된다")
    void rltTcOffset_isAnchoredByRequestDateTime() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();

        // RLT_TC 뒤 고정 길이 필드를 지나면 요청일시가 나와야 위치가 맞다.
        String reqDtm =
                new String(
                        golden,
                        EaiStandardResponseFixture.reqDtmOffset(),
                        EaiStandardResponseFixture.REQ_DTM_LEN,
                        MS949);

        assertThat(reqDtm).isEqualTo(GOLDEN_REQ_DTM);
        // 요청 전문의 결과구분코드는 공백이다(응답에서만 채워진다).
        assertThat((char) golden[EaiStandardResponseFixture.rltTcOffset()]).isEqualTo(' ');
    }

    @Test
    @DisplayName("유도한 MSG_IDCT_TC 위치는 헤더 끝의 출력매체건수로 확인된다")
    void msgIdctTcOffset_isAnchoredByHeaderTail() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();
        int headerLen = EaiStandardResponseFixture.headerLen(golden, MS949);
        int msgIdctTc = EaiStandardResponseFixture.msgIdctTcOffset(golden, MS949);

        // 헤더 마지막 3바이트는 출력매체건수("000")다.
        assertThat(
                        new String(
                                golden,
                                headerLen - EaiStandardResponseFixture.PRO_MDA_PART_LEN,
                                EaiStandardResponseFixture.PRO_MDA_PART_LEN,
                                MS949))
                .isEqualTo("000");
        // 메시지공통부 직전은 책임자승인공통부의 마지막 필드인 신분증관리건수("00")다.
        assertThat(new String(golden, msgIdctTc - 2, 2, MS949)).isEqualTo("00");
        // 요청 전문의 메시지표시방법구분코드는 공백이고, 뒤이어 오류발생전문항목 50바이트도 공백이다.
        assertThat(new String(golden, msgIdctTc, 51, MS949)).isBlank();
    }

    @Test
    @DisplayName("오류 응답 픽스처는 두 플래그와 SEEAI 코드를 모두 담는다")
    void errorResponse_carriesFlagsAndCode() {
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00006", MS949);

        assertThat((char) response[EaiStandardResponseFixture.rltTcOffset()]).isEqualTo('2');
        assertThat((char) response[EaiStandardResponseFixture.msgIdctTcOffset(response, MS949)])
                .isEqualTo('1');
        assertThat(new String(response, MS949)).contains("SEEAI00006");
    }

    @Test
    @DisplayName("플래그만 세운 응답에는 SEEAI 코드가 없다")
    void flaggedResponse_hasNoErrorCode() {
        byte[] response = EaiStandardResponseFixture.flaggedResponse(MS949);

        assertThat(new String(response, MS949)).doesNotContain("SEEAI");
    }
}
