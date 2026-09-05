package com.kdb.it.common.admin.metrics.service;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JMX MXBean과 Micrometer 레지스트리로 자원 사용량을 읽는 {@link ServerMetricsProbe}.
 *
 * <p>CPU·load·OS 메모리는 {@code com.sun.management.OperatingSystemMXBean}에서 읽는다. 이 빈은 값을 아직 계산하지 못했거나
 * 플랫폼이 지원하지 않으면 음수를 돌려주므로, 음수는 모두 {@code null}(수집 불가)로 바꾼다. Windows는 load average를 지원하지 않아 항상
 * null이고 운영 Linux에서는 정상 값이 실린다.
 */
@Component
public class JvmServerMetricsProbe implements ServerMetricsProbe {

    private final com.sun.management.OperatingSystemMXBean os;
    private final MemoryMXBean memory;
    private final ThreadMXBean threads;
    private final RuntimeMXBean runtime;
    private final File diskRoot;
    private final MeterRegistry registry;

    @Autowired
    public JvmServerMetricsProbe(ObjectProvider<MeterRegistry> registryProvider) {
        this(
                (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean(),
                ManagementFactory.getMemoryMXBean(),
                ManagementFactory.getThreadMXBean(),
                ManagementFactory.getRuntimeMXBean(),
                new File(".").getAbsoluteFile(),
                registryProvider.getIfAvailable());
    }

    JvmServerMetricsProbe(
            com.sun.management.OperatingSystemMXBean os,
            MemoryMXBean memory,
            ThreadMXBean threads,
            RuntimeMXBean runtime,
            File diskRoot,
            MeterRegistry registry) {
        this.os = os;
        this.memory = memory;
        this.threads = threads;
        this.runtime = runtime;
        this.diskRoot = diskRoot;
        this.registry = registry;
    }

    @Override
    public ServerMetricsDto.Sample sample(Instant at) {
        long memTotal = os.getTotalMemorySize();
        long memFree = os.getFreeMemorySize();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        long diskTotal = diskRoot.getTotalSpace();
        long diskFree = diskRoot.getUsableSpace();
        return new ServerMetricsDto.Sample(
                at,
                percentOrNull(os.getCpuLoad()),
                percentOrNull(os.getProcessCpuLoad()),
                os.getAvailableProcessors(),
                nonNegativeOrNull(os.getSystemLoadAverage()),
                positiveOrNull(memTotal),
                memTotal > 0 && memFree >= 0 ? memTotal - memFree : null,
                positiveOrNull(heap.getMax()),
                heap.getUsed(),
                positiveOrNull(diskTotal),
                diskTotal > 0 ? diskFree : null,
                threads.getThreadCount(),
                runtime.getUptime() / 1000,
                gaugeAsInt("hikaricp.connections.active"),
                gaugeAsInt("hikaricp.connections.idle"),
                gaugeAsInt("hikaricp.connections.pending"),
                gaugeAsInt("hikaricp.connections.max"));
    }

    /** 0.0~1.0 비율을 소수 첫째 자리 백분율로 바꾼다. 음수(미계산·미지원)는 null. */
    private static Double percentOrNull(double ratio) {
        if (ratio < 0 || Double.isNaN(ratio)) return null;
        return Math.round(ratio * 1000.0) / 10.0;
    }

    private static Double nonNegativeOrNull(double value) {
        if (value < 0 || Double.isNaN(value)) return null;
        return Math.round(value * 100.0) / 100.0;
    }

    private static Long positiveOrNull(long value) {
        return value > 0 ? value : null;
    }

    /** HikariCP 게이지를 정수로 읽는다. 레지스트리나 게이지가 없으면 null — 풀 메트릭 미등록을 0으로 위장하지 않는다. */
    private Integer gaugeAsInt(String name) {
        if (registry == null) return null;
        Gauge gauge = registry.find(name).gauge();
        if (gauge == null) return null;
        double value = gauge.value();
        if (Double.isNaN(value) || value < 0) return null;
        return (int) Math.round(value);
    }
}
