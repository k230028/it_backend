package com.kdb.it.infra.eai.service;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.dto.UmsPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UmsPayloadSection 단위 테스트 — Complexity=64.7% 미달 항목 보완.
 *
 * <p>주요 커버리지 대상:
 * <ul>
 *   <li>{@code supports()} 분기: UmsPayload → true, GwePayload → false</li>
 *   <li>{@code systemCode()} 반환값</li>
 *   <li>{@code build()} 내부 분기:
 *       umsBzDttId 첫 글자 'E'(이메일) → umsTmeChnNo 및 umsSdChnTpC 분기</li>
 *   <li>{@code variableDataJson()} 분기: umData null/빈 스킵, 값 있음, 특수문자 이스케이프</li>
 *   <li>sendDt null/non-null 분기, sendTime null/non-null 분기</li>
 * </ul>
 * </p>
 */
@DisplayName("UmsPayloadSection 단위 테스트")
class UmsPayloadSectionTest {

    private static final Charset MS949 = Charset.forName("MS949");

    private UmsPayloadSection section;
    private EaiSectionContext ctx;

    @BeforeEach
    void setUp() {
        section = new UmsPayloadSection();
        LocalDateTime fixed = LocalDateTime.of(2026, 6, 7, 9, 30, 15, 0);
        UnaryOperator<String> dateFn = pattern ->
                DateTimeFormatter.ofPattern(pattern, Locale.ROOT).format(fixed);
        IntFunction<String> randomDigits = len -> "1".repeat(len);
        EaiProperties props = new EaiProperties(
                false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
        ctx = new EaiSectionContext(MS949, props, dateFn, randomDigits);
    }

    // -----------------------------------------------------------------------
    // supports() 분기
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("supports() 분기")
    class Supports {

        @Test
        @DisplayName("UmsPayload → true")
        void supports_UmsPayload_returnsTrue() {
            UmsPayload payload = UmsPayload.builder().umsBzDttId("SMS2096").umsTrSno("1").build();
            assertThat(section.supports(payload)).isTrue();
        }

        @Test
        @DisplayName("GwePayload → false")
        void supports_GwePayload_returnsFalse() {
            GwePayload payload = GwePayload.builder().msgGubun("3").recvIds("k001").build();
            assertThat(section.supports(payload)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // systemCode()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("systemCode()는 \"UMS\" 반환")
    void systemCode_returnsUms() {
        assertThat(section.systemCode()).isEqualTo("UMS");
    }

    // -----------------------------------------------------------------------
    // build() — umsBzDttId 분기
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("build() — umsBzDttId 첫 글자 분기")
    class UmsBzDttIdBranch {

        @Test
        @DisplayName("첫 글자 'S'(SMS)이면 umsTmeChnNo=1588-1500 포함")
        void sms_template_channel() {
            // Arrange
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("7")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").build();

            // Act
            String result = section.build(payload, ctx);

            // Assert
            assertThat(result).contains("1588-1500");
            assertThat(result).doesNotContain("hrd@kdb.co.kr");
        }

        @Test
        @DisplayName("첫 글자 'E'(이메일)이면 umsTmeChnNo=hrd@kdb.co.kr 포함")
        void email_template_channel() {
            // Arrange
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("EML0001").umsTrSno("3")
                    .emplNum("K1112223").cstNm("이영희").reqCh("hong@kdb.co.kr")
                    .deptKey("182").deptNm("디지털금융부").build();

            // Act
            String result = section.build(payload, ctx);

            // Assert — 이메일 채널 주소 포함
            assertThat(result).contains("hrd@kdb.co.kr");
        }

        @Test
        @DisplayName("첫 글자 'A'(알림톡)이면 umsTmeChnNo=1588-1500 포함")
        void alimtalk_template_channel() {
            // Arrange
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("ALT0165").umsTrSno("42")
                    .emplNum("K7654321").cstNm("김철수").reqCh("01099998888")
                    .deptKey("182").deptNm("디지털금융부").build();

            // Act
            String result = section.build(payload, ctx);

            // Assert
            assertThat(result).contains("1588-1500");
        }

        @Test
        @DisplayName("umsBzDttId 빈 문자열이면 예외 없이 결과 반환")
        void empty_umsBzDttId_noException() {
            // Arrange — umsBzDttId=""인 경우 umsSdChnTpC도 ""
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("").umsTrSno("1")
                    .emplNum("K1234567").cstNm("테스트").reqCh("01011112222")
                    .deptKey("000").deptNm("테스트부").build();

            // Act
            String result = section.build(payload, ctx);

            // Assert — 예외 없이 결과 반환
            assertThat(result).isNotEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // build() — sendDt / sendTime null 분기
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("build() — sendDt / sendTime null 분기")
    class SendDateBranch {

        @Test
        @DisplayName("sendDt=null이면 trDt(고정 2026-06-07)으로 대체")
        void null_sendDt_usesTrDt() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").sendDt(null).build();

            // sendDt=null이면 trDt로 채워짐 → "20260607" 포함 확인
            String result = section.build(payload, ctx);
            assertThat(result).contains("20260607");
        }

        @Test
        @DisplayName("sendDt 지정 시 해당 날짜를 사용")
        void explicit_sendDt_used() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").sendDt("20260101").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("20260101");
        }

        @Test
        @DisplayName("sendTime=null이면 빈 문자열로 대체 — 예외 없이 결과 반환")
        void null_sendTime_usesEmpty() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").sendTime(null).build();

            String result = section.build(payload, ctx);
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("sendTime 지정 시 해당 시각 사용")
        void explicit_sendTime_used() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").sendTime("093000").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("093000");
        }
    }

    // -----------------------------------------------------------------------
    // variableDataJson — umData null/빈/값 분기
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("variableDataJson() 분기")
    class VariableDataJson {

        @Test
        @DisplayName("umData 모두 null이면 entries가 빈 JSON")
        void all_null_umData_emptyEntries() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").build();

            String result = section.build(payload, ctx);
            // entries가 빈 JSON: {"type":"dataSet","entries":{}}
            assertThat(result).contains("{\"type\":\"dataSet\",\"entries\":{}}");
        }

        @Test
        @DisplayName("umData1만 있으면 UM_DATA_1 항목 포함")
        void single_umData1_includesEntry() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부").umData1("123456").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("\"UM_DATA_1\":\"123456\"");
        }

        @Test
        @DisplayName("복수 umData가 있으면 쉼표로 구분")
        void multiple_umData_commaSeparated() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("V1").umData2("V2").umData3("V3").build();

            String result = section.build(payload, ctx);
            assertThat(result)
                    .contains("\"UM_DATA_1\":\"V1\"")
                    .contains("\"UM_DATA_2\":\"V2\"")
                    .contains("\"UM_DATA_3\":\"V3\"");
        }

        @Test
        @DisplayName("빈 문자열 umData는 JSON에서 스킵")
        void empty_umData_skipped() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("").umData2("VALUE").build();

            String result = section.build(payload, ctx);
            // umData1은 빈 문자열이므로 스킵, umData2만 포함
            assertThat(result).doesNotContain("UM_DATA_1");
            assertThat(result).contains("\"UM_DATA_2\":\"VALUE\"");
        }

        @Test
        @DisplayName("umData 값에 따옴표가 있으면 이스케이프")
        void double_quote_escaped() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("say \"hello\"").build();

            String result = section.build(payload, ctx);
            // 따옴표가 \" 로 이스케이프되어야 함
            assertThat(result).contains("say \\\"hello\\\"");
        }

        @Test
        @DisplayName("umData 값에 개행/탭 특수문자가 있으면 이스케이프")
        void newline_tab_escaped() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("line1\nline2\ttab").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("\\n");
            assertThat(result).contains("\\t");
        }

        @Test
        @DisplayName("umData 값에 백슬래시가 있으면 이스케이프")
        void backslash_escaped() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("path\\to\\file").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("path\\\\to\\\\file");
        }

        @Test
        @DisplayName("umData 값에 캐리지리턴(\\r)이 있으면 이스케이프")
        void carriage_return_escaped() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData1("line\r").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("\\r");
        }

        @Test
        @DisplayName("umData7만 있어도 UM_DATA_7 항목 포함 (인덱스 경계 확인)")
        void only_umData7_includesEntry() {
            UmsPayload payload = UmsPayload.builder()
                    .umsBzDttId("SMS2096").umsTrSno("1")
                    .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                    .deptKey("182").deptNm("디지털금융부")
                    .umData7("LAST").build();

            String result = section.build(payload, ctx);
            assertThat(result).contains("\"UM_DATA_7\":\"LAST\"");
            assertThat(result).doesNotContain("UM_DATA_1");
        }
    }

    // -----------------------------------------------------------------------
    // build() — 출력 전문 구조 검증
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("build() 결과는 비어있지 않고 umsRetNo 형식을 포함")
    void build_result_notEmpty_andContainsRetNo() {
        UmsPayload payload = UmsPayload.builder()
                .umsBzDttId("SMS2096").umsTrSno("7")
                .emplNum("K1234567").cstNm("홍길동").reqCh("01012345678")
                .deptKey("182").deptNm("디지털금융부").umData1("123456").build();

        String result = section.build(payload, ctx);

        // UMS_RET_NO = umsBzDttId(7) + trDt(8) + umsTrSno(8자리)
        // SMS2096 + 20260607 + 00000007
        assertThat(result).isNotEmpty();
        assertThat(result).contains("SMS209620260607");
    }

    @Test
    @DisplayName("build() 결과는 고정 상수 CHN_REQ_TP_C=\"10\" 포함")
    void build_result_containsChnReqTpC() {
        UmsPayload payload = UmsPayload.builder()
                .umsBzDttId("ALT0165").umsTrSno("1")
                .emplNum("K7654321").cstNm("김철수").reqCh("01099998888")
                .deptKey("182").deptNm("디지털금융부").build();

        String result = section.build(payload, ctx);
        assertThat(result).contains("10");
    }
}
