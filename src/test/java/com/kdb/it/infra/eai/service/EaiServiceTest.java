package com.kdb.it.infra.eai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kdb.it.infra.eai.config.EaiProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EaiServiceTest {

    private static final Charset MS949 = Charset.forName("MS949");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Clock fixedClock() {
        return Clock.fixed(
                LocalDateTime.of(2026, 6, 7, 9, 30, 15, 123_000_000).atZone(KST).toInstant(), KST);
    }

    private HostAddressProvider host() {
        return new HostAddressProvider() {
            @Override
            public String ipAddress() {
                return "10.0.0.1";
            }

            @Override
            public String macAddress() {
                return "001122334455";
            }
        };
    }

    private EaiRequest req() {
        return EaiRequest.ums(
                "IPPO00012345",
                com.kdb.it.infra.eai.dto.UmsPayload.builder()
                        .umsBzDttId("SMS2096")
                        .umsTrSno("7")
                        .emplNum("K1234567")
                        .cstNm("홍길동")
                        .reqCh("01012345678")
                        .deptKey("182")
                        .deptNm("디지털금융부")
                        .umData1("123456")
                        .build());
    }

    private EaiService service(EaiProperties props, RestClient client) {
        return new EaiService(
                props,
                client,
                fixedClock(),
                () -> "000000001",
                host(),
                len -> "1".repeat(len),
                java.util.List.of(new UmsPayloadSection(), new GwePayloadSection()));
    }

    @Test
    @DisplayName("enabled=false면 HTTP 미호출, EaiResult.skip 반환")
    void disabled_skipsHttp() {
        EaiProperties props =
                new EaiProperties(false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.invalid").build();
        EaiResult r = service(props, client).sendEai(req());
        assertThat(r.skipped()).isTrue();
        assertThat(r.success()).isFalse();
    }

    @Test
    @DisplayName("enabled=true면 octet-stream으로 전문 전송, 성공 시 success")
    void enabled_sendsOctetStream() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://eai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://eai.test/eai"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        RestClient client = builder.build();

        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        EaiResult r = service(props, client).sendEai(req());

        server.verify();
        assertThat(r.success()).isTrue();
        assertThat(r.responseRaw()).isEmpty();
    }

    @Test
    @DisplayName("전송 실패(5xx)면 예외 전파 없이 EaiResult.failure")
    void serverError_returnsFailure() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://eai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://eai.test/eai")).andRespond(withServerError());
        RestClient client = builder.build();

        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        EaiResult r = service(props, client).sendEai(req());

        assertThat(r.success()).isFalse();
        assertThat(r.skipped()).isFalse();
        assertThat(r.errorMessage()).isNotBlank();
    }

    @Test
    @DisplayName("전문 빌드 실패(필드 초과)면 EaiResult.failure")
    void buildOverflow_returnsFailure() {
        EaiRequest bad =
                EaiRequest.ums(
                        "IPPO00012345",
                        com.kdb.it.infra.eai.dto.UmsPayload.builder()
                                .umsBzDttId("SMS2096")
                                .umsTrSno("7")
                                .emplNum("K12345678")
                                .cstNm("홍길동")
                                .reqCh("01012345678")
                                .deptKey("182")
                                .deptNm("디지털금융부")
                                .umData1("123456")
                                .build());
        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.test").build();
        EaiResult r = service(props, client).sendEai(bad);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("전문");
    }

    @Test
    @DisplayName("umsTrSno가 빈 문자열이면 parseInt 실패 → EaiResult.failure")
    void umsTrSnoBlank_returnsFailure() {
        EaiRequest bad =
                EaiRequest.ums(
                        "IPPO00012345",
                        com.kdb.it.infra.eai.dto.UmsPayload.builder()
                                .umsBzDttId("SMS2096")
                                .umsTrSno("")
                                .emplNum("K1234567")
                                .cstNm("홍길동")
                                .reqCh("01012345678")
                                .deptKey("182")
                                .deptNm("디지털금융부")
                                .umData1("123456")
                                .build());
        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.test").build();
        EaiResult r = service(props, client).sendEai(bad);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("전문");
    }

    @Test
    @DisplayName("umsTrSno가 비숫자면 parseInt 실패 → EaiResult.failure")
    void umsTrSnoNonNumeric_returnsFailure() {
        EaiRequest bad =
                EaiRequest.ums(
                        "IPPO00012345",
                        com.kdb.it.infra.eai.dto.UmsPayload.builder()
                                .umsBzDttId("SMS2096")
                                .umsTrSno("abc")
                                .emplNum("K1234567")
                                .cstNm("홍길동")
                                .reqCh("01012345678")
                                .deptKey("182")
                                .deptNm("디지털금융부")
                                .umData1("123456")
                                .build());
        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.test").build();
        EaiResult r = service(props, client).sendEai(bad);
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("전문");
    }

    @Test
    @DisplayName("지원 섹션 없으면 EaiResult.failure (예외 전파 없음)")
    void noSection_returnsFailure() {
        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.test").build();
        EaiService svc =
                new EaiService(
                        props,
                        client,
                        fixedClock(),
                        () -> "000000001",
                        host(),
                        len -> "1".repeat(len),
                        java.util.List.of()); // 빈 섹션 레지스트리
        EaiResult r = svc.sendEai(req());
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("지원하지 않는");
    }

    @Test
    @DisplayName("GWE 메일 발송 — octet-stream 전송 성공")
    void gwe_mail_sends() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://eai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://eai.test/eai"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        RestClient client = builder.build();
        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        EaiRequest gwe =
                EaiRequest.gwe(
                        "IPPG00000001",
                        com.kdb.it.infra.eai.dto.GwePayload.builder()
                                .msgGubun("3")
                                .recvIds("k0001,k0002")
                                .subject("공지")
                                .contents("<p>본문</p>")
                                .build());
        EaiResult r = service(props, client).sendEai(gwe);
        server.verify();
        assertThat(r.success()).isTrue();
    }

    @Test
    @DisplayName("HTTP 200 빈 응답은 EAI 오류 전문 파싱 실패로 처리한다")
    void http200EmptyBody_returnsFailure() {
        // Arrange — 서버가 빈 응답 바디를 반환하는 시나리오 (body="" 는 null 분기 또는 빈 배열 분기)
        RestClient.Builder builder = RestClient.builder().baseUrl("http://eai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://eai.test/eai"))
                .andExpect(method(POST))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));
        RestClient client = builder.build();

        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");
        EaiResult r = service(props, client).sendEai(req());

        server.verify();
        assertThat(r.success()).isFalse();
        assertThat(r.errorMessage()).contains("파싱 실패");
    }

    @Test
    @DisplayName("HTTP 200 EAI 오류 전문은 SEEAI 코드를 포함한 실패로 처리한다")
    void http200ErrorMessage_returnsFailureWithErrorCode() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://eai.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] response = EaiStandardResponseFixture.errorResponse("SEEAI00001", MS949);
        server.expect(requestTo("http://eai.test/eai"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_OCTET_STREAM));

        EaiProperties props =
                new EaiProperties(
                        true,
                        "http://eai.test/eai",
                        "MS949",
                        3000,
                        3000,
                        "L",
                        "IPP",
                        "IPP",
                        "PRM",
                        "PP");

        EaiResult result = service(props, builder.build()).sendEai(req());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("SEEAI00001");
    }

    @Test
    @DisplayName("enabled=false이고 GWE 요청도 skip 반환 (마스킹 미리보기 경로)")
    void disabled_gwe_skipsHttp() {
        // Arrange
        EaiProperties props =
                new EaiProperties(false, "", "MS949", 3000, 3000, "L", "IPP", "IPP", "PRM", "PP");
        RestClient client = RestClient.builder().baseUrl("http://eai.invalid").build();
        com.kdb.it.infra.eai.dto.EaiRequest gwe =
                com.kdb.it.infra.eai.dto.EaiRequest.gwe(
                        "IPPG00000001",
                        com.kdb.it.infra.eai.dto.GwePayload.builder()
                                .msgGubun("1")
                                .recvIds("k0001")
                                .subject("알림")
                                .contents("내용")
                                .build());

        // Act
        EaiResult r = service(props, client).sendEai(gwe);

        // Assert — GWE도 enabled=false면 skip
        assertThat(r.skipped()).isTrue();
        assertThat(r.success()).isFalse();
    }
}
