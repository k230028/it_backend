package com.kdb.it.common.admin.waslog.client;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;

/** 다른 WAS 인스턴스의 내부 엔드포인트를 호출한다. */
public interface WasLogPeerClient {

    /**
     * 피어의 로그 스냅샷을 가져온다.
     *
     * @param baseUrl 피어 base URL(끝에 슬래시 없음)
     * @param instanceId 대상 인스턴스ID. 응답 검증용
     * @throws WasLogPeerException 연결·타임아웃·5xx 등 모든 호출 실패
     */
    WasLogDto.Snapshot fetchSnapshot(String baseUrl, String instanceId, WasLogDto.Query query);

    /**
     * 피어에 런타임 레벨 변경을 적용한다.
     *
     * @throws WasLogPeerException 호출 실패
     */
    WasLogDto.LevelOverride applyLevel(String baseUrl, WasLogDto.LevelRequest request);
}
