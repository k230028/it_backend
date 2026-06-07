package com.kdb.it.infra.eai.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EaiMessageBuilderTest {

    private static final Charset MS949 = Charset.forName("MS949");

    private static java.time.Clock fixedClock() {
        return java.time.Clock.fixed(
                java.time.LocalDateTime.of(2026, 6, 7, 9, 30, 15, 123_000_000)
                        .atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant(),
                java.time.ZoneId.of("Asia/Seoul"));
    }

    private static com.kdb.it.infra.eai.config.EaiProperties fixedProps() {
        return new com.kdb.it.infra.eai.config.EaiProperties(
                false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
    }

    private static HostAddressProvider fixedHost() {
        return new HostAddressProvider() {
            @Override public String ipAddress() { return "10.0.0.1"; }
            @Override public String macAddress() { return "001122334455"; }
        };
    }

    private static com.kdb.it.infra.eai.dto.EaiRequest fixedSmsRequest() {
        return com.kdb.it.infra.eai.dto.EaiRequest.builder()
                .system("UMS").ifId("IPPO00012345").umsBzDttId("SMS2096")
                .umsTrSno("7").emplNum("K1234567").cstNm("홍길동")
                .reqCh("01012345678").deptKey("182").deptNm("디지털금융부")
                .umData1("123456").build();
    }

    @Test
    @DisplayName("lpad: 숫자 타입은 '0', 그 외 타입은 공백으로 좌측 패딩")
    void lpad_padsLeft() {
        assertThat(EaiMessageBuilder.lpad(MS949, "N", 5, "42")).isEqualTo("00042");
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 5, "ab")).isEqualTo("   ab");
    }

    @Test
    @DisplayName("lpad: null/빈 문자열은 전체 패딩")
    void lpad_nullBecomesFullPad() {
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 3, null)).isEqualTo("   ");
        assertThat(EaiMessageBuilder.lpad(MS949, "N", 3, "")).isEqualTo("000");
    }

    @Test
    @DisplayName("lpad: MS949에서 한글 1자는 2바이트로 계산되어 패딩 폭이 줄어든다")
    void lpad_koreanIsTwoBytes() {
        assertThat(EaiMessageBuilder.lpad(MS949, "C", 4, "가")).isEqualTo("  가");
        assertThat("가".getBytes(MS949)).hasSize(2);
    }

    @Test
    @DisplayName("lpad: 내용 바이트가 offset을 초과하면 IndexOutOfBoundsException")
    void lpad_overflowThrows() {
        assertThatThrownBy(() -> EaiMessageBuilder.lpad(MS949, "C", 1, "abc"))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("build() 스모크")
    class BuildSmoke {

        private EaiMessageBuilder fixedBuilder() {
            java.time.Clock clock = java.time.Clock.fixed(
                    java.time.LocalDateTime.of(2026, 6, 7, 9, 30, 15, 123_000_000)
                            .atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant(),
                    java.time.ZoneId.of("Asia/Seoul"));
            com.kdb.it.infra.eai.config.EaiProperties props = new com.kdb.it.infra.eai.config.EaiProperties(
                    false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
            HostAddressProvider host = new HostAddressProvider() {
                @Override public String ipAddress() { return "10.0.0.1"; }
                @Override public String macAddress() { return "001122334455"; }
            };
            return new EaiMessageBuilder(props, clock, () -> "000000001", host);
        }

        private com.kdb.it.infra.eai.dto.EaiRequest umsRequest() {
            return com.kdb.it.infra.eai.dto.EaiRequest.builder()
                    .system("UMS").ifId("IPPO00012345").umsBzDttId("SMS2096")
                    .umsTrSno("7").emplNum("K1234567").cstNm("홍길동")
                    .reqCh("01012345678").deptKey("182").deptNm("디지털금융부")
                    .umData1("123456").build();
        }

        @Test
        @DisplayName("build()는 비어있지 않은 byte[]를 만들고 앞 24바이트가 길이필드(숫자)다")
        void build_producesLengthHeader() {
            byte[] msg = fixedBuilder().build(umsRequest());
            assertThat(msg).isNotEmpty();
            String head = new String(msg, 0, 24, MS949);
            assertThat(head).matches("\\d{24}");
            assertThat(msg).endsWith("@@".getBytes(MS949));
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("ePAMS 참조 동일성")
    class EpamsEquivalence {

        private EaiMessageBuilder actual() {
            return new EaiMessageBuilder(fixedProps(), fixedClock(), () -> "000000001", fixedHost());
        }

        private EpamsReferenceMessageBuilder reference() {
            return new EpamsReferenceMessageBuilder(fixedProps(), fixedClock(), () -> "000000001", fixedHost());
        }

        @Test
        @DisplayName("신규 빌더는 ePAMS 참조 조립과 바이트 단위로 동일하다 (SMS)")
        void byteForByte_sms() {
            byte[] ref = reference().buildUms(fixedSmsRequest());
            byte[] act = actual().build(fixedSmsRequest());

            assertThat(new String(act, 0, 24, MS949))
                    .as("길이필드(전체/헤더/출력매체)")
                    .isEqualTo(new String(ref, 0, 24, MS949));
            assertThat(act).as("전문 전체 byte[]").isEqualTo(ref);
        }

        @Test
        @DisplayName("알림톡(A) 템플릿도 참조와 동일하다")
        void byteForByte_alimtalk() {
            com.kdb.it.infra.eai.dto.EaiRequest alt = com.kdb.it.infra.eai.dto.EaiRequest.builder()
                    .system("UMS").ifId("IPPO00012345").umsBzDttId("ALT0165")
                    .umsTrSno("42").emplNum("K7654321").cstNm("김철수")
                    .reqCh("01099998888").deptKey("182").deptNm("디지털금융부")
                    .umData1("987654").build();
            assertThat(actual().build(alt)).isEqualTo(reference().buildUms(alt));
        }

        @Test
        @DisplayName("이메일(E) 템플릿도 참조와 동일하다 (umsSdChnTpC=M 분기)")
        void byteForByte_email() {
            com.kdb.it.infra.eai.dto.EaiRequest eml = com.kdb.it.infra.eai.dto.EaiRequest.builder()
                    .system("UMS").ifId("IPPO00012345").umsBzDttId("EML0001")
                    .umsTrSno("3").emplNum("K1112223").cstNm("이영희")
                    .reqCh("hong@kdb.co.kr").deptKey("182").deptNm("디지털금융부")
                    .umData1("본문내용").build();
            assertThat(actual().build(eml)).isEqualTo(reference().buildUms(eml));
        }
    }
}
