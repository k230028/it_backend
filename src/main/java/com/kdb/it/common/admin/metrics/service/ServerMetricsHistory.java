package com.kdb.it.common.admin.metrics.service;

import com.kdb.it.common.admin.metrics.config.ServerMetricsProperties;
import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 인스턴스 로컬 샘플 링버퍼. 용량을 넘기면 가장 오래된 샘플을 버린다.
 *
 * <p>인스턴스마다 자기 버퍼만 갖는다. 다른 인스턴스의 이력은 피어 내부 API로 가져와 응답에서 합친다.
 */
@Component
public class ServerMetricsHistory {

    private final int capacity;
    private final ArrayDeque<ServerMetricsDto.Sample> samples;

    @Autowired
    public ServerMetricsHistory(ServerMetricsProperties properties) {
        this(properties.historyCapacity());
    }

    ServerMetricsHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("링버퍼 용량은 1 이상이어야 합니다: " + capacity);
        }
        this.capacity = capacity;
        this.samples = new ArrayDeque<>(capacity);
    }

    public int capacity() {
        return capacity;
    }

    /** 샘플을 추가하고 용량을 넘긴 가장 오래된 샘플을 버린다. */
    public synchronized void record(ServerMetricsDto.Sample sample) {
        if (sample == null) throw new IllegalArgumentException("샘플이 없습니다.");
        if (samples.size() >= capacity) samples.pollFirst();
        samples.addLast(sample);
    }

    /** 가장 최근 샘플. 아직 수집 전이면 비어 있다. */
    public synchronized Optional<ServerMetricsDto.Sample> latest() {
        return Optional.ofNullable(samples.peekLast());
    }

    /** 오래된 순 시계열 점 목록의 복사본. */
    public synchronized List<ServerMetricsDto.Point> points() {
        List<ServerMetricsDto.Point> result = new ArrayList<>(samples.size());
        for (ServerMetricsDto.Sample sample : samples) result.add(sample.toPoint());
        return result;
    }
}
