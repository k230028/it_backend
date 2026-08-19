package com.kdb.it.common.admin.waslog.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** WAS 로그 뷰어 요청·응답 계약. */
public final class WasLogDto {

    private WasLogDto() {}

    /**
     * 조회 조건.
     *
     * @param afterSeq 이 seq 초과분만 조회. 0이면 처음부터
     * @param limit 조회 상한. 0 이하면 200, 200 초과면 200으로 보정
     * @param levels 허용 레벨 집합. 비어 있으면 필터 없음
     * @param logger 로거명 접두사. null/공백이면 필터 없음
     * @param keyword 메시지·로거 부분일치. null/공백이면 필터 없음
     */
    public record Query(
            long afterSeq, int limit, Set<String> levels, String logger, String keyword) {}

    /**
     * 조회 응답.
     *
     * @param instanceId 실제로 응답한 인스턴스ID
     * @param bufferEpoch 버퍼 세대 식별자. 바뀌면 클라이언트는 커서를 버린다
     * @param entries seq 오름차순 로그
     * @param lastSeq 다음 요청에 쓸 커서
     * @param dropped 커서 이후 일부가 버퍼에서 밀려났으면 true
     * @param levelOverrides 해당 인스턴스에 적용 중인 런타임 레벨 변경
     * @param peerError 피어 위임 실패 사유. 성공이면 null
     */
    public record Snapshot(
            String instanceId,
            String bufferEpoch,
            List<WasLogEntry> entries,
            long lastSeq,
            boolean dropped,
            List<LevelOverride> levelOverrides,
            String peerError) {}

    /**
     * 런타임 레벨 변경 현황.
     *
     * @param logger 대상 로거명
     * @param level 적용된 레벨
     * @param previousLevel 변경 직전 레벨. 설정값이 없었으면 null
     * @param expiresAt 자동 원복 예정 시각
     */
    public record LevelOverride(
            String logger, String level, String previousLevel, LocalDateTime expiresAt) {}

    /**
     * 런타임 레벨 변경 요청.
     *
     * @param instanceId 대상 인스턴스ID
     * @param logger 화이트리스트 접두사에 속하는 로거명
     * @param level ERROR/WARN/INFO/DEBUG/TRACE
     * @param ttlMinutes 자동 원복까지 분. 1~120
     */
    public record LevelRequest(String instanceId, String logger, String level, int ttlMinutes) {}

    /**
     * 인스턴스 정보.
     *
     * @param id 인스턴스ID
     * @param self 이 응답을 만든 인스턴스인지
     * @param reachable 피어 URL이 설정되어 호출 가능한지
     */
    public record InstanceInfo(String id, boolean self, boolean reachable) {}
}
