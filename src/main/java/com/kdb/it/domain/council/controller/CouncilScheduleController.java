package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 일정 취합·확정 REST 컨트롤러 (M6)
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: {@code /{asctId}/schedule} 현황·본인 일정 조회, 제출, 대면 확정, 서면 확정.
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilScheduleController {

    /** 일정 서비스 */
    private final ScheduleService scheduleService;

    /** 협의회 기본 서비스 (일정 확정 시 상태 전이에 사용) */
    private final CouncilService councilService;

    /**
     * 일정 입력 현황 조회 (IT관리자)
     *
     * <p>전체 위원의 일정 응답 현황과 미응답 위원 수를 반환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 일정 현황
     */
    @Operation(summary = "일정 입력 현황 조회", description = "전체 위원의 일정 응답 현황을 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "조회 성공",
                        content =
                                @Content(
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CouncilDto.ScheduleStatusResponse
                                                                        .class))),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/schedule")
    public ResponseEntity<CouncilDto.ScheduleStatusResponse> getScheduleStatus(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(scheduleService.getScheduleStatus(asctId));
    }

    /**
     * 내 일정 조회 (평가위원 본인)
     *
     * <p>로그인한 평가위원이 제출한 일정 슬롯 목록을 반환합니다.
     *
     * @param asctId 협의회ID
     * @param userDetails 로그인한 평가위원
     * @return 본인이 제출한 일정 슬롯 목록
     */
    @Operation(summary = "내 일정 조회", description = "로그인한 평가위원 본인이 제출한 일정을 조회합니다.")
    @GetMapping("/{asctId}/schedule/my")
    public ResponseEntity<List<CouncilDto.ScheduleSlotResponse>> getMySchedule(
            @PathVariable("asctId") String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(scheduleService.getMySchedule(asctId, userDetails.getEno()));
    }

    /**
     * 일정 입력 (평가위원)
     *
     * <p>평가위원이 날짜×시간대별 가능 여부를 입력합니다. 허용 시간대: 10:00 / 14:00 / 15:00 / 16:00
     *
     * @param asctId 협의회ID
     * @param request 일정 입력 요청
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200
     */
    @Operation(summary = "일정 입력", description = "평가위원이 가능한 날짜/시간대를 입력합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "입력 성공"),
                @ApiResponse(responseCode = "400", description = "허용되지 않은 시간대", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/schedule")
    public ResponseEntity<Void> submitSchedule(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ScheduleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        scheduleService.submitSchedule(asctId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 일정 확정 (IT관리자)
     *
     * <p>최종 회의 일정을 확정합니다. BASCTM.CNRC_DT/TM/PLC를 업데이트하고 상태를 SCHEDULED로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 일정 확정 요청 (회의일자, 회의시간, 회의장소)
     * @return HTTP 200
     */
    @Operation(summary = "일정 확정", description = "최종 회의 일정을 확정하고 상태를 SCHEDULED로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "확정 성공"),
                @ApiResponse(responseCode = "400", description = "허용되지 않은 시간대", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/schedule/confirm")
    public ResponseEntity<Void> confirmSchedule(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.ScheduleConfirmRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        scheduleService.confirmSchedule(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 서면개최 확정 (IT관리자) (PRD_c_20260620 #1)
     *
     * <p>위원 전원이 대면을 희망하지 않을 때 서면개최로 확정합니다. 회의일자/시간/장소 없이 BASCTM.CSF_HELD_YN='N'으로 설정하고 상태를 개최준비(05)
     * → 진행중(07)으로 직접 전이합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200
     */
    @Operation(summary = "서면개최 확정", description = "위원 전원 미희망 시 서면개최로 확정하고 상태를 진행중으로 전이합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "서면개최 확정 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "개최준비(05) 상태가 아닌 경우",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/schedule/confirm-written")
    public ResponseEntity<Void> confirmWrittenMeeting(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        councilService.verifyCouncilManager(asctId, userDetails);
        scheduleService.confirmWrittenMeeting(asctId);
        return ResponseEntity.ok().build();
    }
}
