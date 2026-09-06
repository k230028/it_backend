package com.kdb.it.common.approval.itbudget.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.exception.DataCorruptionException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ItBudgetSnapshotObservationTest {
    @Test
    void downgradedV2EnvelopeStillCountsAsIntegrityFailure() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = context(registry)) {
            var root = v2();
            object(root, "/form").put("version", 1);
            assertThatThrownBy(
                            () ->
                                    context.getBean(ItBudgetSnapshotReader.class)
                                            .read(root.toString()))
                    .isInstanceOf(DataCorruptionException.class);
            assertThat(
                            registry.get("approval.it_budget.snapshot.integrity_failure")
                                    .counter()
                                    .count())
                    .isEqualTo(1);
            assertThat(registry.find("approval.it_budget.snapshot.legacy_read").counter()).isNull();
        }
    }

    @Test
    void countsOnlyBudgetLegacyReadsAndNoSuccessfulV2AsIntegrityFailure() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var context = context(registry)) {
            var reader = context.getBean(ItBudgetSnapshotReader.class);
            for (String form : new String[] {"it-budget", "IT_BUDGET"}) {
                assertThat(
                                reader.read("{\"form\":{\"id\":\"" + form + "\",\"version\":1}}")
                                        .version())
                        .isEqualTo(1);
            }
            reader.read("{\"form\":{\"id\":\"another\",\"version\":1}}");
            reader.read(v2().toString());
            assertThat(registry.get("approval.it_budget.snapshot.legacy_read").counter().count())
                    .isEqualTo(2);
            assertThat(registry.find("approval.it_budget.snapshot.integrity_failure").counter())
                    .isNull();
        }
    }

    @Test
    void v2HashAndShapeFailuresWarnOncePerReadWithoutIdentifiersOrRawValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ItBudgetSnapshotReader.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var registry = new SimpleMeterRegistry();
        try (var context = context(registry)) {
            var reader = context.getBean(ItBudgetSnapshotReader.class);
            var hash = v2();
            object(hash, "/payload/projects/0").put("name", "PRIVATE_JSON_VALUE");
            var shape = v2();
            object(shape, "/payload/projects/0").remove("name");
            for (var root : new com.fasterxml.jackson.databind.JsonNode[] {hash, shape}) {
                assertThatThrownBy(() -> reader.read(root.toString()))
                        .isInstanceOf(DataCorruptionException.class);
            }
            assertThat(
                            registry.get("approval.it_budget.snapshot.integrity_failure")
                                    .counter()
                                    .count())
                    .isEqualTo(2);
            assertThat(registry.getMeters())
                    .allSatisfy(m -> assertThat(m.getId().getTags()).isEmpty());
            assertThat(appender.list)
                    .hasSize(2)
                    .allSatisfy(
                            event -> {
                                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                                assertThat(event.getFormattedMessage())
                                        .contains("version=v2", "outcome=integrity_failure")
                                        .doesNotContain("PRIVATE_JSON_VALUE", "P1", "U1");
                                assertThat(event.getThrowableProxy()).isNull();
                            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void registryFailurePreservesLegacyReadAndOriginalCorruption() throws Exception {
        var registry = spy(new SimpleMeterRegistry());
        doThrow(new IllegalStateException("PRIVATE_REGISTRY_VALUE"))
                .when(registry)
                .counter(anyString(), any(String[].class));
        try (var context = context(registry)) {
            var reader = context.getBean(ItBudgetSnapshotReader.class);
            assertThat(reader.read("{\"form\":{\"id\":\"it-budget\",\"version\":1}}").version())
                    .isEqualTo(1);
            var root = v2();
            object(root, "/payload/projects/0").put("name", "changed");
            assertThatThrownBy(() -> reader.read(root.toString()))
                    .isInstanceOf(DataCorruptionException.class)
                    .hasMessageContaining("payloadDigest")
                    .hasNoCause();
        }
    }

    private AnnotationConfigApplicationContext context(MeterRegistry registry) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(ObjectMapper.class, () -> MAPPER);
        context.registerBean(
                Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator());
        context.registerBean(ItBudgetCanonicalJson.class, () -> new ItBudgetCanonicalJson(MAPPER));
        context.registerBean(MeterRegistry.class, () -> registry);
        context.register(ItBudgetSnapshotReader.class);
        context.refresh();
        return context;
    }
}
