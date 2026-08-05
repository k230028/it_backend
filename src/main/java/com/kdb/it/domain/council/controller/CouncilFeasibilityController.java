package com.kdb.it.domain.council.controller;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.FeasibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 타당성검토표 REST 컨트롤러 (M4)
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: {@code /{asctId}/feasibility} 조회·저장·수정.
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilFeasibilityController {

    /** 타당성검토표 서비스 */
    private final FeasibilityService feasibilityService;

    /**
     * 타당성검토표 조회
     *
     * <p>사업개요 + 타당성 자체점검(6개) + 성과지표 목록을 통합 반환합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 타당성검토표 전체 데이터
     */
    @Operation(summary = "타당성검토표 조회", description = "사업개요, 자체점검, 성과지표를 통합 조회합니다.")
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
                                                                CouncilDto.FeasibilityResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "협의회 또는 타당성검토표 없음",
                        content = @Content)
            })
    @GetMapping("/{asctId}/feasibility")
    public ResponseEntity<CouncilDto.FeasibilityResponse> getFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(feasibilityService.getFeasibility(asctId));
    }

    /**
     * 타당성검토표 신규 저장 (임시저장 / 작성완료)
     *
     * <p>kpnTc=001: 임시저장, 상태 DRAFT 유지
     *
     * <p>kpnTc=002: 작성완료, 첨부파일 필수, 상태 SUBMITTED 전이
     *
     * @param asctId 협의회ID
     * @param request 타당성검토표 저장 요청
     * @return HTTP 200
     */
    @Operation(summary = "타당성검토표 저장", description = "임시저장(TEMP) 또는 작성완료(COMPLETE)로 저장합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "저장 성공"),
                @ApiResponse(
                        responseCode = "400",
                        description = "잘못된 요청 (작성완료 시 첨부파일 없음 등)",
                        content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/feasibility")
    public ResponseEntity<Void> saveFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.FeasibilityRequest request) {
        feasibilityService.saveFeasibility(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 타당성검토표 수정 (임시저장 / 작성완료)
     *
     * <p>POST와 동일한 로직으로 upsert 처리합니다 (기존 데이터 있으면 update).
     *
     * @param asctId 협의회ID
     * @param request 타당성검토표 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "타당성검토표 수정", description = "기존 타당성검토표를 수정합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "수정 성공"),
                @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/feasibility")
    public ResponseEntity<Void> updateFeasibility(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.FeasibilityRequest request) {
        feasibilityService.saveFeasibility(asctId, request);
        return ResponseEntity.ok().build();
    }
}
