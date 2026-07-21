package com.kdb.it.infra.eai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * UmsPayload 레코드 — null 보정 분기(compact constructor) 및 빌더/접근자 커버리지.
 *
 * <p>Branch=50%, Complexity=33.3% 미달 항목을 집중 보완합니다. umsBzDttId와 umsTrSno 두 필드가 null 보정 대상이고, 나머지 필드는
 * null 그대로 전달됩니다.
 */
@DisplayName("UmsPayload null 보정 및 빌더")
class UmsPayloadTest {

    // -----------------------------------------------------------------------
    // null 보정 분기: umsBzDttId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("umsBzDttId null 보정")
    class UmsBzDttIdDefault {

        @Test
        @DisplayName("umsBzDttId=null → \"\" (빈 문자열)")
        void null_umsBzDttId_becomesEmpty() {
            // Arrange / Act
            UmsPayload p = UmsPayload.builder().umsBzDttId(null).umsTrSno("1").build();
            // Assert
            assertThat(p.umsBzDttId()).isEqualTo("");
        }

        @Test
        @DisplayName("umsBzDttId 명시 지정 시 그대로 유지")
        void explicit_umsBzDttId_preserved() {
            UmsPayload p = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").build();
            assertThat(p.umsBzDttId()).isEqualTo("SMS2096");
        }
    }

    // -----------------------------------------------------------------------
    // null 보정 분기: umsTrSno
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("umsTrSno null 보정")
    class UmsTrSnoDefault {

        @Test
        @DisplayName("umsTrSno=null → \"\" (빈 문자열)")
        void null_umsTrSno_becomesEmpty() {
            UmsPayload p = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno(null).build();
            assertThat(p.umsTrSno()).isEqualTo("");
        }

        @Test
        @DisplayName("umsTrSno 명시 지정 시 그대로 유지")
        void explicit_umsTrSno_preserved() {
            UmsPayload p = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("42").build();
            assertThat(p.umsTrSno()).isEqualTo("42");
        }
    }

    // -----------------------------------------------------------------------
    // 보정 없는 필드 — null 허용
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("null 허용 필드는 null 그대로")
    class NullableFields {

        @Test
        @DisplayName("emplNum=null이면 null 그대로")
        void null_emplNum_staysNull() {
            UmsPayload p =
                    UmsPayload.builder().umsBzDttId("ALT0165").umsTrSno("1").emplNum(null).build();
            assertThat(p.emplNum()).isNull();
        }

        @Test
        @DisplayName("sendDt=null이면 null 그대로 (당일 발송 의미)")
        void null_sendDt_staysNull() {
            UmsPayload p =
                    UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").sendDt(null).build();
            assertThat(p.sendDt()).isNull();
        }

        @Test
        @DisplayName("sendTime=null이면 null 그대로 (즉시 발송 의미)")
        void null_sendTime_staysNull() {
            UmsPayload p =
                    UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").sendTime(null).build();
            assertThat(p.sendTime()).isNull();
        }

        @Test
        @DisplayName("umData1~7은 null 그대로 허용")
        void null_umData_staysNull() {
            UmsPayload p = UmsPayload.builder().umsBzDttId("EML0001").umsTrSno("1").build();
            assertThat(p.umData1()).isNull();
            assertThat(p.umData2()).isNull();
            assertThat(p.umData3()).isNull();
            assertThat(p.umData4()).isNull();
            assertThat(p.umData5()).isNull();
            assertThat(p.umData6()).isNull();
            assertThat(p.umData7()).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // 빌더 전체 필드 값 보존 (해피패스 — 모든 접근자 호출 포함)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("모든 필드를 명시 지정하면 그대로 보존")
    void allFields_preserved() {
        // Arrange
        UmsPayload p =
                UmsPayload.builder()
                        .umsBzDttId("SMS2096")
                        .umsTrSno("7")
                        .emplNum("K1234567")
                        .cstNm("홍길동")
                        .reqCh("01012345678")
                        .deptKey("182")
                        .deptNm("디지털금융부")
                        .sendDt("20260101")
                        .sendTime("090000")
                        .umData1("DATA1")
                        .umData2("DATA2")
                        .umData3("DATA3")
                        .umData4("DATA4")
                        .umData5("DATA5")
                        .umData6("DATA6")
                        .umData7("DATA7")
                        .build();

        // Assert — 모든 접근자 커버
        assertThat(p.umsBzDttId()).isEqualTo("SMS2096");
        assertThat(p.umsTrSno()).isEqualTo("7");
        assertThat(p.emplNum()).isEqualTo("K1234567");
        assertThat(p.cstNm()).isEqualTo("홍길동");
        assertThat(p.reqCh()).isEqualTo("01012345678");
        assertThat(p.deptKey()).isEqualTo("182");
        assertThat(p.deptNm()).isEqualTo("디지털금융부");
        assertThat(p.sendDt()).isEqualTo("20260101");
        assertThat(p.sendTime()).isEqualTo("090000");
        assertThat(p.umData1()).isEqualTo("DATA1");
        assertThat(p.umData2()).isEqualTo("DATA2");
        assertThat(p.umData3()).isEqualTo("DATA3");
        assertThat(p.umData4()).isEqualTo("DATA4");
        assertThat(p.umData5()).isEqualTo("DATA5");
        assertThat(p.umData6()).isEqualTo("DATA6");
        assertThat(p.umData7()).isEqualTo("DATA7");
    }

    // -----------------------------------------------------------------------
    // EaiPayload sealed 인터페이스 구현
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("UmsPayload는 EaiPayload 인터페이스를 구현")
    void implements_eaiPayload() {
        UmsPayload p = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").build();
        assertThat(p).isInstanceOf(EaiPayload.class);
    }

    // -----------------------------------------------------------------------
    // record 동등성
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("동일 필드 값이면 두 UmsPayload 인스턴스는 동등")
    void equality_sameFields() {
        UmsPayload a =
                UmsPayload.builder()
                        .umsBzDttId("SMS2096")
                        .umsTrSno("7")
                        .emplNum("K1234567")
                        .build();
        UmsPayload b =
                UmsPayload.builder()
                        .umsBzDttId("SMS2096")
                        .umsTrSno("7")
                        .emplNum("K1234567")
                        .build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("umsBzDttId가 다르면 불동등")
    void equality_differentUmsBzDttId() {
        UmsPayload a = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").build();
        UmsPayload b = UmsPayload.builder().umsBzDttId("ALT0165").umsTrSno("1").build();
        assertThat(a).isNotEqualTo(b);
    }
}
