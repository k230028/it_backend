package com.kdb.it.common.admin.waslog.service;

import com.kdb.it.common.admin.waslog.dto.WasLogDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * WAS 로그 화면의 관리자 행위 감사 기록기.
 *
 * <p>전용 감사 테이블 대신 애플리케이션 로그로 남긴다 — 파일 appender가 12개월 보관하므로 추적 가능성이 확보되고, DDL과 DBA 절차가 필요 없다. DB 적재가
 * 필요해지면 별도 과제로 분리한다.
 */
@Component
@Slf4j
public class WasLogAuditLogger {

    /** 같은 행위자·인스턴스 조합의 조회를 다시 기록하기까지의 최소 간격(분). */
    private static final long THROTTLE_MINUTES = 10;

    /**
     * 추적 맵이 담을 수 있는 최대 키(행위자+인스턴스 조합) 개수.
     *
     * <p>{@code instanceId}는 {@link com.kdb.it.common.admin.waslog.service.WasLogService}가 검증하기 전
     * 값이라 관리자가 매번 다른 문자열을 보내면(오타·순번 스크립트 등) 맵이 프로세스 수명 동안 무한히 자랄 수 있다. 상한에 닿으면 맵을 비운다 — 최악의 결과는 그
     * 직후 같은 조합의 조회가 스로틀 없이 한 번 더 기록되는 것뿐이라 안전한 방향이다.
     */
    private static final int MAX_TRACKED_KEYS = 1000;

    private final Map<String, LocalDateTime> lastAccessLog = new ConcurrentHashMap<>();
    private final Clock clock;

    public WasLogAuditLogger(Clock clock) {
        this.clock = clock;
    }

    /**
     * 로그 조회 진입.
     *
     * <p>조회는 3초마다 폴링되므로 매 호출을 남기면 감사 기록이 정작 보려던 로그를 뒤덮는다. 그렇다고 클라이언트가 보낸 커서({@code afterSeq==0})로
     * first-call을 판정하면, 항상 0이 아닌 값을 보내는 호출자는 흔적을 하나도 남기지 않고 로그를 다 읽어갈 수 있다. 그래서 **서버가** 행위자+인스턴스별로
     * {@value #THROTTLE_MINUTES}분에 한 번만 기록한다 — 클라이언트가 회피할 수 없다.
     */
    public void logSnapshotAccess(String instanceId) {
        String actor = actor();
        if (!shouldLogAccess(actor, instanceId)) return;
        log.warn("[WAS로그감사] 조회 actor={} instance={}", sanitize(actor), sanitize(instanceId));
    }

    /** 런타임 레벨 변경. 드물고 상태를 바꾸므로 스로틀 없이 매번 남긴다. */
    public void logLevelChange(WasLogDto.LevelRequest request) {
        log.warn(
                "[WAS로그감사] 레벨변경 actor={} instance={} logger={} level={} ttl={}분",
                sanitize(actor()),
                sanitize(request.instanceId()),
                sanitize(request.logger()),
                sanitize(request.level()),
                request.ttlMinutes());
    }

    /** 로그 파일 다운로드. 스로틀 없이 매번 남긴다. */
    public void logDownload(String instanceId, int lineCount) {
        log.warn(
                "[WAS로그감사] 다운로드 actor={} instance={} lines={}",
                sanitize(actor()),
                sanitize(instanceId),
                lineCount);
    }

    /**
     * 피어 인스턴스가 내부 API(공유 비밀 인증)로 로컬 스냅샷을 조회했다.
     *
     * <p>{@code /internal/was-logs/**}는 사용자 JWT가 없어 {@link #actor()}가 의미 있는 값을 주지 못한다. 대신 호출자의 원격
     * 주소를 남겨 어느 피어(또는 위조 호출자)가 접근했는지 추적한다.
     *
     * <p>다른 인스턴스를 보는 관리자 화면이 3초마다 이 엔드포인트를 폴링하므로, {@link #logSnapshotAccess}와 같은 이유로 매 호출을 남기면 감사
     * 로그 자체가 RINGBUFFER를 채워 정작 보려던 로그를 밀어낸다. {@code endpoint+원격주소} 조합으로 같은 스로틀 맵을 재사용한다.
     */
    public void logInternalSnapshotAccess(String remoteAddr) {
        if (!shouldLog("internal-snapshot|" + remoteAddr)) return;
        log.warn("[WAS로그감사] 내부조회 remote={}", sanitize(remoteAddr));
    }

    /**
     * 피어 인스턴스가 내부 API로 로컬 로그레벨을 변경했다.
     *
     * <p>사용자가 직접 트리거하는 로컬 {@link #logLevelChange}와 달리, 이 경로는 유효한 공유 비밀을 가진(즉 침해됐거나 오동작하는) 피어가 반복
     * 호출할 수 있어 무제한으로 남기면 방어선이 없다. 같은 {@code endpoint+원격주소} 스로틀을 적용한다.
     */
    public void logInternalLevelChange(String remoteAddr, WasLogDto.LevelRequest request) {
        if (!shouldLog("internal-level|" + remoteAddr)) return;
        log.warn(
                "[WAS로그감사] 내부레벨변경 remote={} logger={} level={} ttl={}분",
                sanitize(remoteAddr),
                sanitize(request.logger()),
                sanitize(request.level()),
                request.ttlMinutes());
    }

    /**
     * 내부 API 공유 비밀 불일치로 요청이 거부됐다.
     *
     * <p>{@code /internal/was-logs/**}는 SecurityConfig에서 permitAll이고 컨트롤러가 직접 401을 만들어 반환하므로,
     * Spring Security의 인증 실패 엔트리포인트가 절대 개입하지 않는다 — 이 호출이 실패한 공유 비밀 시도의 유일한 기록이다.
     *
     * <p><b>스로틀 여부 판단:</b> 이 엔드포인트는 유효한 비밀 없이도 permitAll로 도달 가능하다. 거부 시도를 무제한으로 남기면, 포트에 닿을 수 있는
     * 누구든 잘못된 토큰으로 루프를 돌려 RINGBUFFER와 롤링 파일 appender를 동시에 무제한으로 채울 수 있다("반복된 거부 시도야말로 운영자가 봐야 할
     * 신호"라는 반론도 있지만, 그 신호를 위해 로그 저장소 자체를 공격 표면으로 내줄 수는 없다). 그래서 성공 경로와 동일한 스로틀 메커니즘(맵 상한·10분 창·주입된
     * Clock)을 재사용하되, 성공 키와는 다른 네임스페이스({@code internal-reject|엔드포인트|원격주소})를 써서 거부 스로틀이 성공 감사를 억제하지
     * 못하게 분리한다. 스로틀 창 안에서도 최초 1건은 반드시 기록되므로 공격이 시작됐다는 사실 자체는 여전히 관측 가능하다 — 다만 초당 수백 건이 아니라 원격 주소당
     * 10분에 한 줄로 상한을 둔다. {@code remoteAddr}는 헤더가 아니라 서블릿 컨테이너가 본 TCP 피어 주소이므로 공격자가 요청 필드처럼 자유롭게 조작할
     * 수 없고, 그래도 다수의 출발지 IP로 우회를 시도하면 {@link #MAX_TRACKED_KEYS} 상한이 메모리 증가를 막는다.
     */
    public void logInternalTokenRejected(String endpoint, String remoteAddr) {
        if (!shouldLog("internal-reject|" + endpoint + "|" + remoteAddr)) return;
        log.warn(
                "[WAS로그감사] 내부호출거부 endpoint={} remote={}", sanitize(endpoint), sanitize(remoteAddr));
    }

    /** 행위자+인스턴스별 스로틀 판정. 창을 벗어났으면 기록 시각을 갱신하고 true. */
    private boolean shouldLogAccess(String actor, String instanceId) {
        return shouldLog(actor + "|" + instanceId);
    }

    /**
     * 임의 키 기준 스로틀 판정. 창을 벗어났으면 기록 시각을 갱신하고 true.
     *
     * <p>{@link #logSnapshotAccess}(행위자+인스턴스)와 내부 API 감사(엔드포인트+원격주소, 성공/거부 별도 네임스페이스)가 같은 맵과 상한을
     * 공유한다 — 키 공간이 겹치지 않도록 각 호출부가 접두어로 네임스페이스를 구분한다.
     */
    private boolean shouldLog(String key) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusMinutes(THROTTLE_MINUTES);
        LocalDateTime previous = lastAccessLog.get(key);
        if (previous != null && previous.isAfter(cutoff)) return false;
        if (lastAccessLog.size() >= MAX_TRACKED_KEYS) lastAccessLog.clear();
        lastAccessLog.put(key, now);
        return true;
    }

    /** 테스트 검증용 — 현재 추적 중인 키 개수. */
    int trackedKeyCount() {
        return lastAccessLog.size();
    }

    /**
     * 감사 값 정화.
     *
     * <p>감사 기록은 이 기능의 유일한 보상 통제다. 값이 검증 전에 기록되는 경로가 있어, 개행이 들어가면 로그 파일에 가짜 감사 줄을 심을 수 있다. 개행·캐리지리턴을
     * 눈에 보이는 기호로 바꾼다.
     */
    private String sanitize(String value) {
        if (value == null) return null;
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
