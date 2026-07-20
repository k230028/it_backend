package com.kdb.it.common.notification.controller;

import com.kdb.it.common.notification.dto.NotificationDto;
import com.kdb.it.common.notification.service.NotificationService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림 REST 컨트롤러.
 *
 * <p>모든 엔드포인트는 인증 필수이며 본인({@code currentUser.getEno()}) 데이터에만 접근 가능하다.</p>
 *
 * <p>경로: {@code /api/notifications}</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 API")
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 본인 알림 목록 페이지 조회.
     *
     * @param currentUser 인증된 사용자
     * @param unreadOnly true면 미읽음만 조회, null이면 전체 조회
     * @param page 0부터 시작하는 페이지 번호
     * @param size 페이지 크기, 최대 200
     * @return 본인 알림 페이지
     */
    @GetMapping
    @Operation(summary = "알림 목록 조회", description = "본인 알림 목록을 최신순으로 페이지 조회한다.")
    public ResponseEntity<Page<NotificationDto.Item>> list(
        @AuthenticationPrincipal CustomUserDetails currentUser,
        @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @Max(value = 200, message = "size는 최대 200까지 가능합니다.")
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Page<NotificationDto.Item> result = notificationService
            .listForCurrentUser(currentUser.getEno(), unreadOnly, PageRequest.of(page, size))
            .map(NotificationDto.Item::fromEntity);
        return ResponseEntity.ok(result);
    }

    /**
     * 본인 미읽음 카운트 — 헤더 배지용.
     *
     * @param currentUser 인증된 사용자
     * @return 본인 미읽음 알림 건수
     */
    @GetMapping("/unread-count")
    @Operation(summary = "미읽음 카운트", description = "AppHeader 뱃지용 본인 미읽음 알림 건수.")
    public ResponseEntity<NotificationDto.UnreadCount> unreadCount(
        @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        long count = notificationService.unreadCount(currentUser.getEno());
        return ResponseEntity.ok(NotificationDto.UnreadCount.builder().count(count).build());
    }

    /**
     * 단건 읽음 처리.
     *
     * @param currentUser 인증된 사용자
     * @param infmMsgNo 읽음 처리할 알림 메시지 번호
     * @return 본문 없는 HTTP 204 응답
     * @throws IllegalArgumentException 알림이 없거나 이미 삭제된 경우
     * @throws org.springframework.security.access.AccessDeniedException 본인 알림이 아닌 경우
     */
    @PatchMapping("/{infmMsgNo}/read")
    @Operation(summary = "단건 읽음 처리", description = "본인 알림 1건을 읽음으로 표시한다.")
    public ResponseEntity<Void> markRead(
        @AuthenticationPrincipal CustomUserDetails currentUser,
        @PathVariable("infmMsgNo") String infmMsgNo
    ) {
        notificationService.markRead(infmMsgNo, currentUser.getEno());
        return ResponseEntity.noContent().build();
    }

    /**
     * 본인 미읽음 일괄 읽음.
     *
     * @param currentUser 인증된 사용자
     * @return 읽음으로 변경된 알림 건수
     */
    @PatchMapping("/read-all")
    @Operation(summary = "일괄 읽음 처리", description = "본인 미읽음 알림을 모두 읽음으로 표시한다.")
    public ResponseEntity<NotificationDto.MarkAllReadResponse> markAllRead(
        @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        long updated = notificationService.markAllRead(currentUser.getEno());
        return ResponseEntity.ok(NotificationDto.MarkAllReadResponse.builder().updated(updated).build());
    }

    /**
     * 단건 논리 삭제.
     *
     * @param currentUser 인증된 사용자
     * @param infmMsgNo 삭제할 알림 메시지 번호
     * @return 본문 없는 HTTP 204 응답
     * @throws IllegalArgumentException 알림이 없거나 이미 삭제된 경우
     * @throws org.springframework.security.access.AccessDeniedException 본인 알림이 아닌 경우
     */
    @DeleteMapping("/{infmMsgNo}")
    @Operation(summary = "알림 삭제", description = "본인 알림 1건을 논리 삭제한다.")
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal CustomUserDetails currentUser,
        @PathVariable("infmMsgNo") String infmMsgNo
    ) {
        notificationService.softDelete(infmMsgNo, currentUser.getEno());
        return ResponseEntity.noContent().build();
    }
}
