package com.kdb.it.common.notification.service;

import com.kdb.it.common.notification.entity.Cinfmm;
import com.kdb.it.common.notification.event.NotificationEvent;
import com.kdb.it.common.notification.repository.CinfmmRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 알림을 발송 대기 상태로 독립 적재하는 서비스입니다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    /** {@code SD_DOC_CONE} 물리 컬럼 상한 — UTF-8 바이트 기준 4000바이트. */
    private static final int SD_DOC_CONE_MAX_BYTES = 4000;

    private final CinfmmRepository repository;

    /**
     * 알림 이벤트를 PENDING 행으로 즉시 커밋합니다.
     *
     * @param event 적재할 알림 이벤트
     * @return 생성된 알림 번호, 수신자가 비어 있으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CacheEvict(
            value = "notificationUnreadCount",
            key = "#event.recipientEno()",
            condition = "#event.recipientEno() != null")
    public String enqueue(NotificationEvent event) {
        if (event.recipientEno() == null || event.recipientEno().isBlank()) {
            return null;
        }
        String id =
                String.format("INF-%d-%08d", LocalDate.now().getYear(), repository.getNextVal());
        Cinfmm row =
                Cinfmm.builder()
                        .infmMsgNo(id)
                        .itPtlInfmSvcTc(event.itPtlInfmSvcTc())
                        .ttl(clamp(event.ttl(), 100))
                        .infmMsgCone(clamp(event.infmMsgCone(), 4000))
                        .infmRcdUrl(clamp(event.infmRcdUrl(), 300))
                        .rmsEno(event.recipientEno())
                        .inqYn("N")
                        .itPtlSdTc(event.itPtlSdTc())
                        .sdDocCone(safeSdPayload(event.sdPayload(), id))
                        .infmSdStsC(Cinfmm.DISPATCH_PENDING)
                        .reTryNot(0)
                        .build();
        repository.saveAndFlush(row);
        return id;
    }

    private static String clamp(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 발송 페이로드가 {@code SD_DOC_CONE} 컬럼 폭(UTF-8 4000바이트)을 넘으면 저장하지 않고 {@code null}로 접습니다.
     *
     * <p>페이로드는 {@code {"subject":...,"html":...}} 형태의 JSON 문자열이라, {@link #clamp}처럼 바이트 단위로 잘라내면 더
     * 이상 파싱할 수 없는 값이 됩니다. 자르는 대신 {@code null}로 저장하면 발송 계층이 기존 기본 본문으로 폴백하므로, 컬럼 폭 초과로 insert 자체가
     * {@code ORA-12899}로 실패해 알림 행이 통째로 유실되는 것보다 안전합니다.
     *
     * @param payload 발송 페이로드 JSON. {@code null}이면 그대로 {@code null}
     * @param infmMsgNo 로그 식별용 알림 번호
     * @return 예산 안이면 원본 그대로, 넘으면 {@code null}
     */
    private static String safeSdPayload(String payload, String infmMsgNo) {
        if (payload == null) {
            return null;
        }
        int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (bytes <= SD_DOC_CONE_MAX_BYTES) {
            return payload;
        }
        log.warn(
                "알림 발송 페이로드가 SD_DOC_CONE 컬럼 폭을 초과해 기본 본문으로 대체합니다: infmMsgNo={}, 크기={}바이트",
                infmMsgNo,
                bytes);
        return null;
    }
}
