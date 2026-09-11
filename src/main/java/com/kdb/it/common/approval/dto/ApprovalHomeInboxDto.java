package com.kdb.it.common.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * 전자결재 Home 결재함·기안함 목록 DTO 모음
 *
 * <p>전자결재 Home 화면은 인증 사용자 본인의 결재함(대기·완료)과 기안함(진행 중·완료·반려)만 상태별로 나누어 보여줍니다. 신청서 등록·결재 요청 DTO를 담는
 * {@link ApplicationDto}와 사용 시점·수명주기가 다르므로 별도 파일로 분리했습니다.
 *
 * <p>OpenAPI 스키마명은 {@code ApprovalHomeInboxItem}·{@code ApprovalHomeInboxResponse}로 고정되어 있어 클래스 위치를
 * 옮겨도 생성 타입은 바뀌지 않습니다.
 */
public final class ApprovalHomeInboxDto {

    private ApprovalHomeInboxDto() {}

    /** 전자결재 Home 목록의 신청서 요약 항목입니다. */
    @Schema(
            name = "ApprovalHomeInboxItem",
            description = "전자결재 Home 신청서 요약",
            requiredProperties = {
                "apfMngNo",
                "title",
                "requesterName",
                "requestedAt",
                "statusCode",
                "statusName",
                "actionable"
            })
    public record Item(
            @Schema(description = "신청서관리번호") String apfMngNo,
            @Schema(description = "신청서명") String title,
            @Schema(description = "신청내용(등록자결재요청내용). 상신 시 기록하지 않은 신청서는 null", nullable = true)
                    String requestNote,
            @Schema(description = "신청자명") String requesterName,
            @Schema(description = "신청일자") LocalDate requestedAt,
            @Schema(description = "결재상태 코드") String statusCode,
            @Schema(description = "결재상태명") String statusName,
            @Schema(description = "현재 사용자가 즉시 승인·반려할 수 있는지 여부") boolean actionable) {}

    /** 인증 사용자의 전자결재 Home 결재함·기안함 전체 목록입니다. */
    @Schema(
            name = "ApprovalHomeInboxResponse",
            description = "전자결재 Home 결재함·기안함 목록",
            requiredProperties = {
                "approvalPending",
                "approvalCompleted",
                "draftInProgress",
                "draftCompleted",
                "draftRejected"
            })
    public record Response(
            @Schema(description = "결재 대기 목록") List<Item> approvalPending,
            @Schema(description = "결재 완료 목록") List<Item> approvalCompleted,
            @Schema(description = "기안 진행 중 목록") List<Item> draftInProgress,
            @Schema(description = "기안 결재 완료 목록") List<Item> draftCompleted,
            @Schema(description = "기안 반려 목록") List<Item> draftRejected) {}
}
