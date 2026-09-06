package com.kdb.it.common.admin.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class JvmServerMetricsProbeTest {

    private static final Instant AT = Instant.parse("2026-09-05T01:02:03Z");

    private final com.sun.management.OperatingSystemMXBean os =
            mock(com.sun.management.OperatingSystemMXBean.class);
    private final MemoryMXBean memory = mock(MemoryMXBean.class);
    private final ThreadMXBean threads = mock(ThreadMXBean.class);
    private final RuntimeMXBean runtime = mock(RuntimeMXBean.class);
    private final File diskRoot = mock(File.class);

    private JvmServerMetricsProbe probe(MeterRegistry registry) {
        return new JvmServerMetricsProbe(os, memory, threads, runtime, diskRoot, registry);
    }

    @Test
    @DisplayName("MXBean 값을 백분율·바이트·초 단위 샘플로 옮기고 HikariCP 게이지를 정수로 읽는다")
    void sample_정상값매핑() {
        given(os.getCpuLoad()).willReturn(0.2567);
        given(os.getProcessCpuLoad()).willReturn(0.05);
        given(os.getAvailableProcessors()).willReturn(8);
        given(os.getSystemLoadAverage()).willReturn(1.234);
        given(os.getTotalMemorySize()).willReturn(16_000L);
        given(os.getFreeMemorySize()).willReturn(4_000L);
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 300L, 300L, 1_000L));
        given(diskRoot.getTotalSpace()).willReturn(500L);
        given(diskRoot.getUsableSpace()).willReturn(125L);
        given(threads.getThreadCount()).willReturn(42);
        given(runtime.getUptime()).willReturn(90_500L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.gauge("hikaricp.connections.active", new AtomicInteger(3));
        registry.gauge("hikaricp.connections.idle", new AtomicInteger(7));
        registry.gauge("hikaricp.connections.pending", new AtomicInteger(0));
        registry.gauge("hikaricp.connections.max", new AtomicInteger(10));

        ServerMetricsDto.Sample sample = probe(registry).sample(AT);

        assertThat(sample.at()).isEqualTo(AT);
        assertThat(sample.systemCpuPct()).isEqualTo(25.7);
        assertThat(sample.processCpuPct()).isEqualTo(5.0);
        assertThat(sample.cpuCount()).isEqualTo(8);
        assertThat(sample.load1m()).isEqualTo(1.23);
        assertThat(sample.memTotalBytes()).isEqualTo(16_000L);
        assertThat(sample.memUsedBytes()).isEqualTo(12_000L);
        assertThat(sample.heapMaxBytes()).isEqualTo(1_000L);
        assertThat(sample.heapUsedBytes()).isEqualTo(300L);
        assertThat(sample.diskTotalBytes()).isEqualTo(500L);
        assertThat(sample.diskFreeBytes()).isEqualTo(125L);
        assertThat(sample.liveThreads()).isEqualTo(42);
        assertThat(sample.uptimeSeconds()).isEqualTo(90L);
        assertThat(sample.dbActive()).isEqualTo(3);
        assertThat(sample.dbIdle()).isEqualTo(7);
        assertThat(sample.dbPending()).isEqualTo(0);
        assertThat(sample.dbMax()).isEqualTo(10);
    }

    @Test
    @DisplayName("플랫폼이 주지 않는 값(음수)·힙 최대치 미정(-1)·풀 메트릭 없음은 0으로 위장하지 않고 null이다")
    void sample_미지원값은null() {
        given(os.getCpuLoad()).willReturn(-1.0);
        given(os.getProcessCpuLoad()).willReturn(-1.0);
        given(os.getAvailableProcessors()).willReturn(4);
        given(os.getSystemLoadAverage()).willReturn(-1.0);
        given(os.getTotalMemorySize()).willReturn(0L);
        given(os.getFreeMemorySize()).willReturn(0L);
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 300L, 300L, -1L));
        given(diskRoot.getTotalSpace()).willReturn(0L);
        given(diskRoot.getUsableSpace()).willReturn(0L);
        given(threads.getThreadCount()).willReturn(1);
        given(runtime.getUptime()).willReturn(0L);

        ServerMetricsDto.Sample sample = probe(null).sample(AT);

        assertThat(sample.systemCpuPct()).isNull();
        assertThat(sample.processCpuPct()).isNull();
        assertThat(sample.load1m()).isNull();
        assertThat(sample.memTotalBytes()).isNull();
        assertThat(sample.memUsedBytes()).isNull();
        assertThat(sample.heapMaxBytes()).isNull();
        assertThat(sample.heapUsedBytes()).isEqualTo(300L);
        assertThat(sample.diskTotalBytes()).isNull();
        assertThat(sample.diskFreeBytes()).isNull();
        assertThat(sample.dbActive()).isNull();
        assertThat(sample.dbMax()).isNull();
        assertThat(sample.toPoint().memUsedPct()).isNull();
        assertThat(sample.toPoint().heapUsedPct()).isNull();
    }

    @Test
    @DisplayName("MXBean이 NaN을 주면 0으로 위장하지 않고 null이다")
    void sample_NaN은null() {
        // 음수(미계산·미지원)와 달리 NaN은 산술 비교가 항상 false라, 음수 검사만으로는 걸러지지 않는다.
        given(os.getCpuLoad()).willReturn(Double.NaN);
        given(os.getProcessCpuLoad()).willReturn(Double.NaN);
        given(os.getSystemLoadAverage()).willReturn(Double.NaN);
        given(os.getAvailableProcessors()).willReturn(2);
        given(os.getTotalMemorySize()).willReturn(16_000L);
        given(os.getFreeMemorySize()).willReturn(-1L);
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 300L, 300L, 1_000L));

        ServerMetricsDto.Sample sample = probe(null).sample(AT);

        assertThat(sample.systemCpuPct()).isNull();
        assertThat(sample.processCpuPct()).isNull();
        assertThat(sample.load1m()).isNull();
        // 총량은 읽혔지만 여유량이 음수라 사용량을 계산할 수 없다. 총량만 남기고 사용량은 null이다.
        assertThat(sample.memTotalBytes()).isEqualTo(16_000L);
        assertThat(sample.memUsedBytes()).isNull();
    }

    @Test
    @DisplayName("HikariCP 게이지가 없거나 NaN·음수면 0으로 위장하지 않고 null이다")
    void sample_게이지값이상은null() {
        given(os.getAvailableProcessors()).willReturn(2);
        given(memory.getHeapMemoryUsage()).willReturn(new MemoryUsage(0, 300L, 300L, 1_000L));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Gauge.builder("hikaricp.connections.active", () -> Double.NaN).register(registry);
        Gauge.builder("hikaricp.connections.idle", () -> -1.0).register(registry);
        // pending·max는 등록하지 않아 게이지 자체가 없는 경우를 함께 검증한다.

        ServerMetricsDto.Sample sample = probe(registry).sample(AT);

        assertThat(sample.dbActive()).isNull();
        assertThat(sample.dbIdle()).isNull();
        assertThat(sample.dbPending()).isNull();
        assertThat(sample.dbMax()).isNull();
    }

    @Test
    @DisplayName("스프링 생성자는 MeterRegistry 빈이 없어도 실제 MXBean으로 샘플링한다")
    void sample_스프링생성자_레지스트리없음() {
        @SuppressWarnings("unchecked")
        ObjectProvider<MeterRegistry> registryProvider = mock(ObjectProvider.class);
        given(registryProvider.getIfAvailable()).willReturn(null);

        ServerMetricsDto.Sample sample = new JvmServerMetricsProbe(registryProvider).sample(AT);

        assertThat(sample.cpuCount()).isPositive();
        assertThat(sample.liveThreads()).isPositive();
        assertThat(sample.dbActive()).isNull();
    }

    @Test
    @DisplayName("실제 JVM MXBean으로도 예외 없이 샘플링되고 항상 있는 값이 채워진다")
    void sample_실제JVM() {
        JvmServerMetricsProbe real =
                new JvmServerMetricsProbe(
                        (com.sun.management.OperatingSystemMXBean)
                                ManagementFactory.getOperatingSystemMXBean(),
                        ManagementFactory.getMemoryMXBean(),
                        ManagementFactory.getThreadMXBean(),
                        ManagementFactory.getRuntimeMXBean(),
                        new File(".").getAbsoluteFile(),
                        null);

        ServerMetricsDto.Sample sample = real.sample(AT);

        assertThat(sample.cpuCount()).isPositive();
        assertThat(sample.heapUsedBytes()).isPositive();
        assertThat(sample.memTotalBytes()).isPositive();
        assertThat(sample.liveThreads()).isPositive();
        assertThat(sample.uptimeSeconds()).isNotNegative();
    }
}
