package com.kdb.it.common.admin.waslog.client;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;

/** 다른 WAS 인스턴스의 내부 엔드포인트를 호출한다. */
public interface WasLogPeerClient {

    /**
     * 피어의 로그 스냅샷을 가져온다.
     *
     * @param baseUrl 피어 base URL(끝에 슬래시 없음)
     * @param instanceId 대상 인스턴스ID. 예외 메시지에만 쓴다
     * @param query 피어에 그대로 전달할 조회 조건
     * @return 피어가 돌려준 스냅샷. null을 반환하지 않는다
     * @throws WasLogPeerException 연결·타임아웃·4xx·5xx, 그리고 2xx인데 본문이 비어 있는 경우
     */
    WasLogDto.Snapshot fetchSnapshot(String baseUrl, String instanceId, WasLogDto.Query query);

    /**
     * 피어에 런타임 레벨 변경을 적용한다.
     *
     * @param baseUrl 피어 base URL(끝에 슬래시 없음)
     * @param request 적용할 로거·레벨·TTL
     * @return 피어가 적용한 오버라이드. null을 반환하지 않는다
     * @throws WasLogPeerException 연결·타임아웃·4xx·5xx, 그리고 2xx인데 본문이 비어 있는 경우
     */
    WasLogDto.LevelOverride applyLevel(String baseUrl, WasLogDto.LevelRequest request);
}
