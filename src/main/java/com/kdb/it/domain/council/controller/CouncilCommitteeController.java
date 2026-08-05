package com.kdb.it.domain.council.controller;

import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CommitteeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 평가위원 선정 REST 컨트롤러 (M6)
 *
 * <p>기본 URL: {@code /api/council} — URL은 다른 협의회 컨트롤러와 공유하고 클래스만 책임별로 분리했습니다(CQ-01).
 *
 * <p>담당 범위: {@code /{asctId}/committee} 기본 후보 조회·목록 조회·저장·수정.
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilCommitteeController {

    /** 평가위원 서비스 */
    private final CommitteeService committeeService;

    /**
     * 심의유형별 당연위원 후보 조회 (IT관리자)
     *
     * <p>협의회 심의유형(dbrTc)을 기반으로 당연위원 대상 팀에서 위원 후보를 반환합니다. IT관리자가 평가위원 선정 화면에서 당연위원을 자동표출하는 데 사용합니다.
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 당연위원 후보 목록
     */
    @Operation(summary = "당연위원 후보 조회", description = "심의유형별 당연위원 대상 팀에서 위원 후보를 조회합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "조회 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/committee/default")
    public ResponseEntity<List<CouncilDto.CommitteeMemberResponse>> getDefaultCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(committeeService.getDefaultCommittee(asctId));
    }

    /**
     * 평가위원 목록 조회
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 위원유형별 목록
     */
    @Operation(summary = "평가위원 목록 조회", description = "협의회의 당연/소집/간사 위원 목록을 조회합니다.")
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
                                                                CouncilDto.CommitteeListResponse
                                                                        .class))),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}/committee")
    public ResponseEntity<CouncilDto.CommitteeListResponse> getCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(committeeService.getCommittee(asctId));
    }

    /**
     * 평가위원 선정 (신규 또는 수정)
     *
     * <p>IT관리자가 당연위원+소집위원+간사를 확정합니다. 기존 위원 전체 교체 방식으로 저장하고 협의회 상태를 PREPARING으로 전이합니다.
     *
     * @param asctId 협의회ID
     * @param request 위원 선정 요청
     * @return HTTP 200
     */
    @Operation(summary = "평가위원 선정", description = "당연/소집/간사 위원을 선정합니다. APPROVED 상태에서만 가능합니다.")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "선정 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PostMapping("/{asctId}/committee")
    public ResponseEntity<Void> saveCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.CommitteeRequest request) {
        committeeService.saveCommittee(asctId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 평가위원 수정 (전체 교체)
     *
     * <p>POST와 동일한 로직으로 전체 교체 저장합니다.
     *
     * @param asctId 협의회ID
     * @param request 위원 수정 요청
     * @return HTTP 200
     */
    @Operation(summary = "평가위원 수정", description = "평가위원을 수정합니다 (전체 교체).")
    @ApiResponses(
            value = {
                @ApiResponse(responseCode = "200", description = "수정 성공"),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @PutMapping("/{asctId}/committee")
    public ResponseEntity<Void> updateCommittee(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId,
            @Valid @RequestBody CouncilDto.CommitteeRequest request) {
        committeeService.saveCommittee(asctId, request);
        return ResponseEntity.ok().build();
    }
}
