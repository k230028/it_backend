package com.kdb.it.infra.eai.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GwePayload 레코드 — null 보정 분기(compact constructor) 및 빌더 접근자 커버리지.
 *
 * <p>Branch / Complexity 미달 항목을 집중 보완합니다.</p>
 */
@DisplayName("GwePayload null 보정 및 빌더")
class GwePayloadTest {

    // -----------------------------------------------------------------------
    // null 보정 분기: destGubun
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("destGubun 기본값 보정")
    class DestGubunDefault {

        @Test
        @DisplayName("destGubun=null → \"1\" 기본값")
        void null_destGubun_defaultsToOne() {
            // Arrange / Act
            GwePayload p = GwePayload.builder().msgGubun("1").recvIds("k001").destGubun(null).build();
            // Assert
            assertThat(p.destGubun()).isEqualTo("1");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("destGubun이 null/빈/공백이면 모두 \"1\"")
        void blank_destGubun_defaultsToOne(String value) {
            GwePayload p = GwePayload.builder().msgGubun("3").recvIds("k001").destGubun(value).build();
            assertThat(p.destGubun()).isEqualTo("1");
        }

        @Test
        @DisplayName("destGubun=\"2\"(부서)이면 그대로 유지")
        void explicit_destGubun_preserved() {
            GwePayload p = GwePayload.builder().msgGubun("1").recvIds("k001").destGubun("2").build();
            assertThat(p.destGubun()).isEqualTo("2");
        }
    }

    // -----------------------------------------------------------------------
    // null 보정 분기: sendId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("sendId 기본값 보정")
    class SendIdDefault {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("sendId가 null/빈/공백이면 \"systemalert\"")
        void blank_sendId_defaultsToSystemalert(String value) {
            GwePayload p = GwePayload.builder().msgGubun("3").recvIds("k001").sendId(value).build();
            assertThat(p.sendId()).isEqualTo("systemalert");
        }

        @Test
        @DisplayName("sendId 명시 지정 시 그대로 유지")
        void explicit_sendId_preserved() {
            GwePayload p = GwePayload.builder().msgGubun("3").recvIds("k001").sendId("k9999999").build();
            assertThat(p.sendId()).isEqualTo("k9999999");
        }
    }

    // -----------------------------------------------------------------------
    // null 보정 분기: sendName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("sendName 기본값 보정")
    class SendNameDefault {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("sendName이 null/빈/공백이면 \"관리자\"")
        void blank_sendName_defaultsToAdmin(String value) {
            GwePayload p = GwePayload.builder().msgGubun("3").recvIds("k001").sendName(value).build();
            assertThat(p.sendName()).isEqualTo("관리자");
        }

        @Test
        @DisplayName("sendName 명시 지정 시 그대로 유지")
        void explicit_sendName_preserved() {
            GwePayload p = GwePayload.builder().msgGubun("3").recvIds("k001").sendName("홍길동").build();
            assertThat(p.sendName()).isEqualTo("홍길동");
        }
    }

    // -----------------------------------------------------------------------
    // null 보정 분기: 단순 "" 기본값 필드들
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("나머지 null → 빈 문자열 보정")
    class SimpleNullFields {

        @Test
        @DisplayName("msgGubun=null → \"\"")
        void null_msgGubun_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.msgGubun()).isEqualTo("");
        }

        @Test
        @DisplayName("recvIds=null → \"\"")
        void null_recvIds_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.recvIds()).isEqualTo("");
        }

        @Test
        @DisplayName("subject=null → \"\"")
        void null_subject_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.subject()).isEqualTo("");
        }

        @Test
        @DisplayName("contents=null → \"\"")
        void null_contents_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.contents()).isEqualTo("");
        }

        @Test
        @DisplayName("url=null → \"\"")
        void null_url_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.url()).isEqualTo("");
        }

        @Test
        @DisplayName("ccRecvIds=null → \"\"")
        void null_ccRecvIds_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.ccRecvIds()).isEqualTo("");
        }

        @Test
        @DisplayName("bccRecvIds=null → \"\"")
        void null_bccRecvIds_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.bccRecvIds()).isEqualTo("");
        }

        @Test
        @DisplayName("attFlag=null → \"\"")
        void null_attFlag_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.attFlag()).isEqualTo("");
        }

        @Test
        @DisplayName("att=null → \"\"")
        void null_att_becomesEmpty() {
            GwePayload p = GwePayload.builder().build();
            assertThat(p.att()).isEqualTo("");
        }
    }

    // -----------------------------------------------------------------------
    // 빌더 전체 필드 값 보존 (해피패스)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("모든 필드를 명시 지정하면 보정 없이 그대로 보존")
    void allFields_preserved() {
        // Arrange
        GwePayload p = GwePayload.builder()
                .msgGubun("3")
                .recvIds("k0001,k0002")
                .subject("제목")
                .contents("<p>본문</p>")
                .destGubun("1")
                .url("http://it.kdb.co.kr")
                .ccRecvIds("k9999")
                .bccRecvIds("k8888")
                .attFlag("Y")
                .att("file.pdf")
                .sendId("k1234567")
                .sendName("홍길동")
                .build();

        // Assert
        assertThat(p.msgGubun()).isEqualTo("3");
        assertThat(p.recvIds()).isEqualTo("k0001,k0002");
        assertThat(p.subject()).isEqualTo("제목");
        assertThat(p.contents()).isEqualTo("<p>본문</p>");
        assertThat(p.destGubun()).isEqualTo("1");
        assertThat(p.url()).isEqualTo("http://it.kdb.co.kr");
        assertThat(p.ccRecvIds()).isEqualTo("k9999");
        assertThat(p.bccRecvIds()).isEqualTo("k8888");
        assertThat(p.attFlag()).isEqualTo("Y");
        assertThat(p.att()).isEqualTo("file.pdf");
        assertThat(p.sendId()).isEqualTo("k1234567");
        assertThat(p.sendName()).isEqualTo("홍길동");
    }

    // -----------------------------------------------------------------------
    // EaiPayload sealed 인터페이스 구현
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("GwePayload는 EaiPayload 인터페이스를 구현")
    void implements_eaiPayload() {
        GwePayload p = GwePayload.builder().msgGubun("1").recvIds("k001").build();
        assertThat(p).isInstanceOf(EaiPayload.class);
    }

    // -----------------------------------------------------------------------
    // record 동등성
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("동일 필드 값이면 두 GwePayload 인스턴스는 동등")
    void equality_sameFields() {
        GwePayload a = GwePayload.builder().msgGubun("3").recvIds("k001").subject("테스트").build();
        GwePayload b = GwePayload.builder().msgGubun("3").recvIds("k001").subject("테스트").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("필드가 다르면 두 GwePayload 인스턴스는 불동등")
    void equality_differentFields() {
        GwePayload a = GwePayload.builder().msgGubun("1").recvIds("k001").build();
        GwePayload b = GwePayload.builder().msgGubun("3").recvIds("k001").build();
        assertThat(a).isNotEqualTo(b);
    }
}
