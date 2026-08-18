package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.infra.eai.service.EaiStandardLayout.Field;
import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 공통부 필드 표가 실제 전문과 맞는지 검증한다.
 *
 * <p>표의 누적 합을 다시 표로 검산하면 자기참조가 되므로, 위치는 <b>동결 골든 전문</b>과 {@link EaiStandardResponseFixture}가 독립적으로
 * 유도한 오프셋에 대조한다.
 */
class EaiStandardLayoutTest {

    private static final Charset MS949 = Charset.forName("MS949");

    @Test
    @DisplayName("공통부 필드 표의 길이 합이 전문 규격 길이와 같다")
    void sectionLengths_matchSpec() {
        assertThat(sum(EaiStandardLayout.SYSTEM_COMMON)).isEqualTo(180);
        assertThat(sum(EaiStandardLayout.TRANSACTION_COMMON)).isEqualTo(400);
        assertThat(sum(EaiStandardLayout.MESSAGE_COMMON)).isEqualTo(56);
    }

    @Test
    @DisplayName("헤더 길이가 골든 전문이 스스로 신고한 HER_LEN과 같다")
    void headerLen_matchesGoldenDeclaration() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();

        assertThat(EaiStandardLayout.HEADER_LEN)
                .isEqualTo(EaiStandardResponseFixture.headerLen(golden, MS949));
    }

    @Test
    @DisplayName("판정 필드 위치가 픽스처가 독립 유도한 오프셋과 일치한다")
    void decisionFieldOffsets_matchFixture() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();

        assertThat(EaiStandardLayout.field(EaiStandardLayout.TRANSACTION_COMMON, "RLT_TC").offset())
                .isEqualTo(EaiStandardResponseFixture.rltTcOffset());
        assertThat(
                        EaiStandardLayout.field(EaiStandardLayout.TRANSACTION_COMMON, "REQ_DTM")
                                .offset())
                .isEqualTo(EaiStandardResponseFixture.reqDtmOffset());
        assertThat(
                        EaiStandardLayout.field(EaiStandardLayout.MESSAGE_COMMON, "MSG_IDCT_TC")
                                .offset())
                .isEqualTo(EaiStandardResponseFixture.msgIdctTcOffset(golden, MS949));
    }

    @Test
    @DisplayName("골든 전문에서 읽은 필드 값이 전문 규격의 고정값과 같다")
    void fieldValues_readFromGolden() {
        byte[] golden = EaiStandardResponseFixture.standardMessage();

        assertThat(read(EaiStandardLayout.SYSTEM_COMMON, "TGR_VRS_INF", golden)).isEqualTo("1.0");
        assertThat(read(EaiStandardLayout.SYSTEM_COMMON, "MLAN_TC", golden)).isEqualTo("ko");
        assertThat(read(EaiStandardLayout.TRANSACTION_COMMON, "REQ_RPD_TC", golden)).isEqualTo("Q");
        assertThat(read(EaiStandardLayout.TRANSACTION_COMMON, "CHN_TP_C", golden)).isEqualTo("TR");
        assertThat(read(EaiStandardLayout.TRANSACTION_COMMON, "IF_ID", golden)).isNotBlank();
    }

    @Test
    @DisplayName("전문이 짧으면 필드 읽기는 빈 값을 반환한다")
    void read_shortMessage_returnsEmpty() {
        Field ifId = EaiStandardLayout.field(EaiStandardLayout.TRANSACTION_COMMON, "IF_ID");

        assertThat(ifId.read(new byte[10], MS949)).isEmpty();
        assertThat(ifId.fitsIn(new byte[10])).isFalse();
    }

    private static int sum(List<Field> section) {
        return section.stream().mapToInt(Field::length).sum();
    }

    private static String read(List<Field> section, String name, byte[] message) {
        return EaiStandardLayout.field(section, name).read(message, MS949).orElseThrow();
    }
}
