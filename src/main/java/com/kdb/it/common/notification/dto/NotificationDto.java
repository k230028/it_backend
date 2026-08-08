package com.kdb.it.common.notification.dto;

import com.kdb.it.common.notification.entity.Cinfmm;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 도메인 DTO 묶음 (정적 중첩).
 *
 * <p>요청/응답 모델을 한 파일에 모아 Swagger 스키마 등록과 임포트를 단순화한다.
 */
public final class NotificationDto {

    private NotificationDto() {}

    /** 알림 단건 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "NotificationItem",
            description = "알림 단건",
            requiredProperties = {
                "infmMsgNo",
                "itPtlInfmSvcTc",
                "ttl",
                "infmMsgCone",
                "infmRcdUrl",
                "inqYn",
                "inqDtm",
                "fstEnrDtm"
            })
    public static class Item {

        @Schema(description = "알림메시지번호", example = "INF-2026-00000001")
        private String infmMsgNo;

        @Schema(
                description = "알림서비스구분코드 (Ccodem cId='INFM_SVC' cdva, 예: '02'=결재요청)",
                example = "02",
                allowableValues = {"01", "02", "03", "04", "05", "06"})
        private String itPtlInfmSvcTc;

        @Schema(description = "제목 (최대 100자)", nullable = true)
        private String ttl;

        @Schema(description = "알림메시지내용 (본문)", nullable = true)
        private String infmMsgCone;

        @Schema(description = "알림추천URL (클릭 시 이동할 앱 내부 경로)", nullable = true)
        private String infmRcdUrl;

        @Schema(
                description = "조회여부 (Y/N) — Y=읽음",
                allowableValues = {"Y", "N"})
        private String inqYn;

        @Schema(description = "조회일시 (읽은 시각)", nullable = true)
        private LocalDateTime inqDtm;

        @Schema(description = "최초 등록 일시")
        private LocalDateTime fstEnrDtm;

        public static Item fromEntity(Cinfmm e) {
            return Item.builder()
                    .infmMsgNo(e.getInfmMsgNo())
                    .itPtlInfmSvcTc(e.getItPtlInfmSvcTc())
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
    @Schema(
            name = "NotificationUnreadCount",
            description = "미조회(미읽음) 알림 건수",
            requiredProperties = "count")
    public static class UnreadCount {

        @Schema(description = "미조회 건수", example = "5")
        private long count;
    }

    /** 일괄 읽음 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(
            name = "NotificationMarkAllReadResponse",
            description = "일괄 조회(읽음) 처리 응답",
            requiredProperties = "updated")
    public static class MarkAllReadResponse {

        @Schema(description = "조회로 갱신된 건수", example = "5")
        private long updated;
    }
}
