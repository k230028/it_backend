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

        @Schema(description = "알림관리번호", example = "INF-2026-00000001")
        private String infMngNo;

        @Schema(description = "알림종류구분코드 (Ccodem cId='INF_TP' cdva, 예: '002'=결재요청)", example = "002")
        private String infTpC;

        @Schema(description = "알림 제목")
        private String infTtl;

        @Schema(description = "알림 내용 (미리보기)")
        private String infCone;

        @Schema(description = "클릭 시 이동할 앱 내부 경로")
        private String infLnkUrl;

        @Schema(description = "읽음여부 (Y/N)")
        private String rddYn;

        @Schema(description = "읽음일시")
        private LocalDateTime rddDtm;

        @Schema(description = "최초 등록 일시")
        private LocalDateTime fstEnrDtm;

        public static Item fromEntity(Cinfmm e) {
            return Item.builder()
                .infMngNo(e.getInfMngNo())
                .infTpC(e.getInfTpC())
                .infTtl(e.getInfTtl())
                .infCone(e.getInfCone())
                .infLnkUrl(e.getInfLnkUrl())
                .rddYn(e.getRddYn())
                .rddDtm(e.getRddDtm())
                .fstEnrDtm(e.getFstEnrDtm())
                .build();
        }
    }

    /** 미읽음 카운트 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "NotificationUnreadCount", description = "미읽음 알림 건수")
    public static class UnreadCount {

        @Schema(description = "미읽음 건수", example = "5")
        private long count;
    }

    /** 일괄 읽음 응답 DTO. */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "NotificationMarkAllReadResponse", description = "일괄 읽음 처리 응답")
    public static class MarkAllReadResponse {

        @Schema(description = "읽음으로 갱신된 건수", example = "5")
        private long updated;
    }
}
