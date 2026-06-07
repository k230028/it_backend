package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.GwePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

class GwePayloadSectionTest {

    private static final Charset MS949 = Charset.forName("MS949");

    private EaiSectionContext fixedCtx() {
        LocalDateTime fixed = LocalDateTime.of(2026, 6, 7, 9, 30, 15, 123_000_000);
        UnaryOperator<String> dateFn = pattern ->
                DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(fixed);
        IntFunction<String> randomDigits = len -> "1".repeat(len);
        EaiProperties props = new EaiProperties(false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
        return new EaiSectionContext(MS949, props, dateFn, randomDigits);
    }

    @Test
    @DisplayName("GwePayloadSection은 ePAMS getParamGWE 참조 전사와 바이트 동일 (메일)")
    void byteForByte_mail() {
        GwePayload mail = GwePayload.builder()
                .msgGubun("3").recvIds("k0001,k0002").subject("제목입니다")
                .contents("<p>본문</p>").ccRecvIds("k9999").attFlag("Y").att("file.pdf").build();
        EaiSectionContext ctx = fixedCtx();
        String actual = new GwePayloadSection().build(mail, ctx);
        String reference = new EpamsGweReferenceBuilder(ctx).build(mail);
        assertThat(actual.getBytes(MS949)).isEqualTo(reference.getBytes(MS949));
    }

    @Test
    @DisplayName("메신저(1) 케이스도 참조와 동일")
    void byteForByte_messenger() {
        GwePayload msgr = GwePayload.builder()
                .msgGubun("1").recvIds("k0001").subject("알림").contents("내용").url("http://it.kdb.co.kr/x").build();
        EaiSectionContext ctx = fixedCtx();
        assertThat(new GwePayloadSection().build(msgr, ctx).getBytes(MS949))
                .isEqualTo(new EpamsGweReferenceBuilder(ctx).build(msgr).getBytes(MS949));
    }

    @Test
    @DisplayName("systemCode는 GWE, GwePayload만 supports")
    void metadata() {
        GwePayloadSection s = new GwePayloadSection();
        assertThat(s.systemCode()).isEqualTo("GWE");
        assertThat(s.supports(GwePayload.builder().msgGubun("3").recvIds("k1").build())).isTrue();
        assertThat(s.supports(com.kdb.it.infra.eai.dto.UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").build())).isFalse();
    }
}
