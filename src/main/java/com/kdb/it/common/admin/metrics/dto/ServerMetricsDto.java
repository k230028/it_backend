package com.kdb.it.common.admin.metrics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 관리자 대시보드 서버 자원 사용량 DTO. 수집 불가한 값은 0이나 -1로 위장하지 않고 {@code null}로 표면화한다.
 *
 * <p>내부 record는 OpenAPI에 단순 이름(Response 등)으로 노출돼 다른 DTO와 충돌하므로 {@code @Schema(name)}으로 고유 이름을 준다.
 */
public final class ServerMetricsDto {

    private ServerMetricsDto() {}

    /**
     * 한 시점의 서버 자원 샘플.
     *
     * @param at 샘플 시각
     * @param systemCpuPct 시스템 전체 CPU 사용률(0~100). 플랫폼이 제공하지 않으면 null
     * @param processCpuPct JVM 프로세스 CPU 사용률(0~100). 플랫폼이 제공하지 않으면 null
     * @param cpuCount JVM이 사용할 수 있는 논리 프로세서 수
     * @param load1m 1분 load average. Windows 등 미지원 플랫폼이면 null
     * @param memTotalBytes OS 물리 메모리 총량
     * @param memUsedBytes OS 물리 메모리 사용량(총량 - 여유량)
     * @param heapMaxBytes JVM 힙 최대치. JVM이 정하지 않았으면 null
     * @param heapUsedBytes JVM 힙 사용량
     * @param diskTotalBytes 애플리케이션 작업 경로가 속한 파티션 총량
     * @param diskFreeBytes 같은 파티션의 사용 가능 용량
     * @param liveThreads 라이브 스레드 수
     * @param uptimeSeconds JVM 가동 시간(초)
     * @param dbActive HikariCP 사용 중 커넥션 수. 풀 메트릭이 없으면 null
     * @param dbIdle HikariCP 유휴 커넥션 수. 풀 메트릭이 없으면 null
     * @param dbPending HikariCP 커넥션 대기 스레드 수. 풀 메트릭이 없으면 null
     * @param dbMax HikariCP 최대 풀 크기. 풀 메트릭이 없으면 null
     */
    @Schema(name = "ServerMetricsSample", description = "서버 자원 샘플")
    public record Sample(
            Instant at,
            Double systemCpuPct,
            Double processCpuPct,
            Integer cpuCount,
            Double load1m,
            Long memTotalBytes,
            Long memUsedBytes,
            Long heapMaxBytes,
            Long heapUsedBytes,
            Long diskTotalBytes,
            Long diskFreeBytes,
            Integer liveThreads,
            Long uptimeSeconds,
            Integer dbActive,
            Integer dbIdle,
            Integer dbPending,
            Integer dbMax) {

        /** 차트 시계열용 요약점으로 변환한다. */
        public Point toPoint() {
            return new Point(
                    at,
                    systemCpuPct,
                    processCpuPct,
                    percent(memUsedBytes, memTotalBytes),
                    percent(heapUsedBytes, heapMaxBytes),
                    load1m);
        }

        /** 분모가 없거나 0이면 null — 0%로 위장하지 않는다. */
        static Double percent(Long used, Long total) {
            if (used == null || total == null || total <= 0) return null;
            return Math.round(used * 1000.0 / total) / 10.0;
        }
    }

    /**
     * 차트 시계열 한 점.
     *
     * @param at 샘플 시각
     * @param systemCpuPct 시스템 CPU 사용률(0~100)
     * @param processCpuPct JVM 프로세스 CPU 사용률(0~100)
     * @param memUsedPct OS 물리 메모리 사용률(0~100)
     * @param heapUsedPct JVM 힙 사용률(0~100). 힙 최대치를 모르면 null
     * @param load1m 1분 load average. 미지원 플랫폼이면 null
     */
    @Schema(name = "ServerMetricsPoint", description = "서버 자원 시계열 점")
    public record Point(
            Instant at,
            Double systemCpuPct,
            Double processCpuPct,
            Double memUsedPct,
            Double heapUsedPct,
            Double load1m) {}

    /**
     * 인스턴스 하나의 최신 샘플과 이력.
     *
     * @param instanceId 인스턴스ID ({@code app.server.instance-id})
     * @param self 응답을 만든 인스턴스 자신이면 true
     * @param latest 최신 샘플. 피어 조회 실패면 null
     * @param history 오래된 순 시계열. 피어 조회 실패면 빈 목록
     * @param peerError 피어 조회 실패 사유. 성공이면 null
     */
    @Schema(name = "ServerMetricsInstance", description = "인스턴스별 서버 자원 사용량")
    public record InstanceMetrics(
            String instanceId,
            boolean self,
            Sample latest,
            List<Point> history,
            String peerError) {}

    /**
     * 대시보드 응답.
     *
     * @param serverTime 응답을 만든 인스턴스의 현재 시각
     * @param sampleIntervalSec 샘플링 주기(초)
     * @param historyMinutes 링버퍼 이력 길이(분)
     * @param instances 인스턴스ID 오름차순 목록. 자기 자신을 항상 포함한다
     */
    @Schema(name = "ServerMetricsResponse", description = "서버 자원 사용량 응답")
    public record Response(
            Instant serverTime,
            int sampleIntervalSec,
            int historyMinutes,
            List<InstanceMetrics> instances) {}
}
