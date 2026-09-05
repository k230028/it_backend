package com.kdb.it.common.admin.metrics.service;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;
import java.time.Instant;

/** 현재 프로세스와 호스트의 자원 사용량을 한 번 읽는다. 구현은 플랫폼이 주지 않는 값을 {@code null}로 돌려준다. */
public interface ServerMetricsProbe {

    /**
     * 자원 사용량을 샘플링한다.
     *
     * @param at 샘플에 기록할 시각
     * @return 샘플. null을 반환하지 않는다
     */
    ServerMetricsDto.Sample sample(Instant at);
}
