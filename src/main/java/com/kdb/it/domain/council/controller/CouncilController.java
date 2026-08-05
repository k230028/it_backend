package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.CouncilService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 기본 REST 컨트롤러
 *
 * <p>기본 URL: {@code /api/council}
 *
 * <p>담당 범위: 협의회 목록 조회, 신규 신청, 단건 상세 조회.
 *
 * <p>나머지 엔드포인트는 같은 {@code /api/council} URL을 공유하는 책임별 컨트롤러가 담당합니다(CQ-01 분해).
 *
 * <ul>
 *   <li>{@link CouncilFeasibilityController} — 타당성검토표
 *   <li>{@link CouncilLifecycleController} — 결재·상태 전이·생략요청
 *   <li>{@link CouncilCommitteeController} — 평가위원 선정
 *   <li>{@link CouncilScheduleController} — 일정 취합·확정
 *   <li>{@link CouncilEvaluationController} — 평가의견·계획협의회 적정/유보
 *   <li>{@link CouncilResultController} — 결과서·통보
 *   <li>{@link CouncilQnaController}, {@link CouncilMainQnaController} — 사전질의응답
 * </ul>
 *
 * <p>설계 참조: §2.5 API 설계
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilController {

    /** 협의회 기본 서비스 (목록/상태 관리) */
    private final CouncilService councilService;

    /**
     * 내 협의회 목록 조회
     *
     * <p>권한별 필터링:
     *
     * <ul>
     *   <li>일반사용자(ITPZZ001): 소속 부서 사업의 협의회
     *   <li>관리자(ITPAD001): 전체 협의회
     *   <li>평가위원: 배정된 협의회
     * </ul>
     *
     * @param userDetails 현재 로그인한 사용자 (JWT에서 자동 주입)
     * @return HTTP 200 + 협의회 목록
     */
    @Operation(summary = "협의회 목록 조회", description = "권한에 따라 내 부서/전체/배정된 협의회 목록을 조회합니다.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "조회 성공")})
    @GetMapping
    public ResponseEntity<List<CouncilDto.ListResponse>> getCouncilList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(councilService.getCouncilList(userDetails));
    }

    /**
     * 협의회 신규 신청
     *
     * <p>소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 신청합니다. 초기 상태 DRAFT로 생성됩니다.
     *
     * @param request 협의회 신청 정보 (프로젝트관리번호, 심의유형)
     * @param userDetails 신청자 정보
     * @return HTTP 200 + 생성된 협의회ID
     */
    @Operation(summary = "협의회 신청", description = "새로운 협의회를 신청합니다. 초기 상태는 DRAFT입니다.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "신청 성공",
                        content = @Content(schema = @Schema(implementation = String.class))),
                @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content)
            })
    @PostMapping
    public ResponseEntity<String> createCouncil(
            @Valid @RequestBody CouncilDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String asctId = councilService.createCouncil(request, userDetails);
        return ResponseEntity.created(java.net.URI.create("/api/council/" + asctId)).body(asctId);
    }

    /**
     * 협의회 단건 상세 조회
     *
     * <p>협의회 기본 정보를 반환합니다. 타당성검토표, 평가위원 등 상세 데이터는 별도 API로 조회합니다.
     *
     * @param asctId 협의회ID (예: ASCT-2026-0001)
     * @return HTTP 200 + 협의회 상세 정보, HTTP 404 존재하지 않는 경우
     */
    @Operation(summary = "협의회 단건 조회", description = "협의회ID로 기본 정보를 조회합니다.")
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
                                                                CouncilDto.DetailResponse.class))),
                @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
            })
    @GetMapping("/{asctId}")
    public ResponseEntity<CouncilDto.DetailResponse> getCouncil(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
                    @PathVariable("asctId")
                    String asctId) {
        return ResponseEntity.ok(councilService.getCouncil(asctId));
    }
}
