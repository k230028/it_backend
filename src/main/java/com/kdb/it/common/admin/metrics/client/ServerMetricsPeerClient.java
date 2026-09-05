package com.kdb.it.common.admin.metrics.client;

import com.kdb.it.common.admin.metrics.dto.ServerMetricsDto;

/** 다른 WAS 인스턴스의 서버 자원 사용량 내부 엔드포인트를 호출한다. */
public interface ServerMetricsPeerClient {

    /**
     * 피어의 최신 샘플과 이력을 가져온다.
     *
     * @param baseUrl 피어 base URL(끝에 슬래시 없음)
     * @param instanceId 대상 인스턴스ID. 응답의 인스턴스ID와 대조한다
     * @return 피어가 돌려준 인스턴스 지표. null을 반환하지 않는다
     * @throws ServerMetricsPeerException 연결·타임아웃·4xx·5xx, 본문 없음, 인스턴스ID 불일치
     */
    ServerMetricsDto.InstanceMetrics fetch(String baseUrl, String instanceId);
}
