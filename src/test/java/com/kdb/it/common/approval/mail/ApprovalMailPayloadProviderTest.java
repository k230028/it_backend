package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.entity.Capplm;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ApprovalMailPayloadProvider 단위 테스트.
 *
 * <p>이 제공자는 더 이상 {@code @Transactional}이 아니므로(로더가 그 경계를 대신 가짐), {@link
 * ApprovalMailPayloadProvider#render}가 {@link ApprovalMailDataLoader}의 예외를 스스로 삼켜 항상 정상 반환(null 또는
 * JSON)한다는 계약을 여기서 직접 검증할 수 있다. 로더가 실제로 예외를 전파한다는 반대쪽 계약은 {@link ApprovalMailDataLoaderTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalMailPayloadProviderTest {

    @Test
    void corruptSnapshotOmitsOnlyBusinessSummaryAndCountsExactlyOnceWithoutPrivateLogs()
            throws Exception {
        var mapper = com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.MAPPER;
        var reader =
                org.mockito.Mockito.spy(
                        com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.reader());
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var actualProvider =
                new ApprovalMailPayloadProvider(
                        approvalMailDataLoader, new ApprovalMailRenderer(mapper), reader, meters);
        ReflectionTestUtils.setField(actualProvider, "frontendUrl", "https://it.kdb.co.kr");
        given(approvalMailDataLoader.loadParties(any()))
                .willReturn(new ApprovalMailParties("신청자", "부서"));
        var root = com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.v2();
        root.set(
                "projects",
                mapper.readTree("[{\"abusNm\":\"PRIVATE_JSON_VALUE\",\"totRqmAmt\":987654}]"));
        Capplm application =
                Capplm.builder()
                        .apfMngNo("APF-2026-0001")
                        .dcdReqTtl("결재 신청")
                        .dcdReqDtm(LocalDate.of(2026, 9, 6))
                        .dcdReqInf(root.toString())
                        .build();
        var logger =
                (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(ApprovalMailPayloadProvider.class);
        var appender =
                new ch.qos.logback.core.read.ListAppender<
                        ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            String result = actualProvider.render(application);
            var payload =
                    mapper.readValue(
                            result, com.kdb.it.common.notification.dispatcher.MailPayload.class);
            assertThat(payload.html())
                    .contains("APF-2026-0001", "결재하러 가기")
                    .doesNotContain("PRIVATE_JSON_VALUE", "987,654", "사업 요약", "계약 요약");
            assertThat(
                            meters.get("approval.snapshot.mail.degraded")
                                    .tag("version", "v2")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            assertThat(meters.getMeters()).hasSize(1);
            verify(reader).read(root.toString());
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("APF-2026-0001")
                    .doesNotContain("PRIVATE_JSON_VALUE", "신청자", "부서");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void malformedJsonDegradesWithUnknownVersionAndNormalVersionsDoNotIncrement() throws Exception {
        var fixture = com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.MAPPER;
        var meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var actualProvider =
                new ApprovalMailPayloadProvider(
                        approvalMailDataLoader,
                        new ApprovalMailRenderer(fixture),
                        com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.reader(),
                        meters);
        ReflectionTestUtils.setField(actualProvider, "frontendUrl", "https://it.kdb.co.kr");
        given(approvalMailDataLoader.loadParties(any()))
                .willReturn(new ApprovalMailParties("신청자", "부서"));
        Capplm application =
                Capplm.builder()
                        .apfMngNo("APF-1")
                        .dcdReqTtl("결재 신청")
                        .dcdReqInf("{broken PRIVATE_JSON_VALUE")
                        .build();
        assertThat(actualProvider.render(application))
                .contains("APF-1")
                .doesNotContain("PRIVATE_JSON_VALUE");
        assertThat(
                        meters.get("approval.snapshot.mail.degraded")
                                .tag("version", "unknown")
                                .counter()
                                .count())
                .isEqualTo(1);
        application.updateDetailContent(
                com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.v2().toString());
        assertThat(actualProvider.render(application)).contains("사업 요약", "계약 요약");
        application.updateDetailContent("{\"projects\":[{\"abusNm\":\"기존사업\",\"totRqmAmt\":300}]}");
        assertThat(actualProvider.render(application)).contains("기존사업", "300 원");
        assertThat(meters.getMeters()).hasSize(1);
        assertThat(meters.get("approval.snapshot.mail.degraded").counter().count()).isEqualTo(1);
    }

    @Mock private ApprovalMailDataLoader approvalMailDataLoader;
    @Mock private ApprovalMailRenderer approvalMailRenderer;

    private ApprovalMailPayloadProvider provider;

    // capplm()이 채우는 필드는 ApprovalMailContextFactory.create가 직접 읽는 것만 남긴다.
    // dcdReqUsid·dcdReqBbrC는 신청자명·부서명 조회에 쓰이지만, 그 조회는 이제 별도 빈인
    // ApprovalMailDataLoader의 책임이라 이 테스트에서는 로더 자체를 모킹한다 — 여기서 스텁해도
    // 실제로 호출되지 않아 불필요한 스터빙 경고(UnnecessaryStubbingException)만 유발한다.
    private Capplm capplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn("APF-2026-00000001");
        given(capplm.getDcdReqTtl()).willReturn("테스트 신청서");
        given(capplm.getDcdReqDtm()).willReturn(LocalDate.of(2026, 8, 18));
        given(capplm.getDcdReqInf()).willReturn(null);
        return capplm;
    }

    @BeforeEach
    void setUp() {
        provider =
                new ApprovalMailPayloadProvider(
                        approvalMailDataLoader,
                        approvalMailRenderer,
                        com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.reader(),
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        ReflectionTestUtils.setField(provider, "frontendUrl", "https://it.kdb.co.kr");
    }

    @Test
    @DisplayName("정상 조회·렌더링이면 렌더러가 만든 JSON을 그대로 반환한다")
    void render_정상흐름_렌더러결과반환() {
        given(approvalMailDataLoader.loadParties(any()))
                .willReturn(new ApprovalMailParties("홍길동", "IT기획부"));
        given(approvalMailRenderer.renderPayloadJson(any(), any()))
                .willReturn("{\"subject\":\"제목\",\"html\":\"본문\"}");

        String result = provider.render(capplm());

        assertThat(result).isEqualTo("{\"subject\":\"제목\",\"html\":\"본문\"}");
    }

    @Test
    @DisplayName("렌더러가 null을 반환하면(예산 초과 등) 그대로 null을 돌려준다")
    void render_렌더러가null반환_null그대로전달() {
        given(approvalMailDataLoader.loadParties(any()))
                .willReturn(new ApprovalMailParties("홍길동", "IT기획부"));
        given(approvalMailRenderer.renderPayloadJson(any(), any())).willReturn(null);

        String result = provider.render(capplm());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("로더가 예외를 던져도 삼키고 null을 반환한다 — 호출자에 전파하지 않는다")
    void render_로더예외_예외전파없이null반환() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn("APF-2026-00000001");
        given(approvalMailDataLoader.loadParties(any()))
                .willThrow(new RuntimeException("DB 조회 실패(테스트)"));

        String result = provider.render(capplm);

        assertThat(result).isNull();
        verify(approvalMailRenderer, never()).renderPayloadJson(any(), any());
    }
}
