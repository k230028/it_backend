package com.kdb.it.common.notification.dto;

import com.kdb.it.common.notification.entity.Cinfmm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 도메인 DTO 묶음 (정적 중첩).
 *
 * <p>요청/응답 모델을 한 파일에 모아 Swagger 스키마 등록과 임포트를 단순화한다.</p>
 */
public final class NotificationDto {

    private NotificationDto() {}

    /** 알림 단건 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "NotificationItem", description = "알림 단건")
    public static class Item {

        @Schema(description = "알림메시지번호", example = "INF-2026-00000001")
        private String infmMsgNo;

        @Schema(description = "알림서비스구분코드 (Ccodem cId='INFM_SVC' cdva, 예: '02'=결재요청)", example = "02")
        private String infmSvcTc;

        @Schema(description = "제목 (최대 100자)")
        private String ttl;

        @Schema(description = "알림메시지내용 (본문)")
        private String infmMsgCone;

        @Schema(description = "알림추천URL (클릭 시 이동할 앱 내부 경로)")
        private String infmRcdUrl;

        @Schema(description = "조회여부 (Y/N) — Y=읽음")
        private String inqYn;

        @Schema(description = "조회일시 (읽은 시각)")
        private LocalDateTime inqDtm;

        @Schema(description = "최초 등록 일시")
        private LocalDateTime fstEnrDtm;

        public static Item fromEntity(Cinfmm e) {
            return Item.builder()
                .infmMsgNo(e.getInfmMsgNo())
                .infmSvcTc(e.getInfmSvcTc())
                .ttl(e.getTtl())
                .infmMsgCone(e.getInfmMsgCone())
                .infmRcdUrl(e.getInfmRcdUrl())
                .inqYn(e.getInqYn())
                .inqDtm(e.getInqDtm())
                .fstEnrDtm(e.getFstEnrDtm())
                .build();
        }
    }

    /** 미읽음 카운트 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "NotificationUnreadCount", description = "미조회(미읽음) 알림 건수")
    public static class UnreadCount {

        @Schema(description = "미조회 건수", example = "5")
        private long count;
    }

    /** 일괄 읽음 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "NotificationMarkAllReadResponse", description = "일괄 조회(읽음) 처리 응답")
    public static class MarkAllReadResponse {

        @Schema(description = "조회로 갱신된 건수", example = "5")
        private long updated;
    }
}
