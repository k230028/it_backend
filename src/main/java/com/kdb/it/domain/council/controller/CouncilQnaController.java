package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.QnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 정보화실무협의회 사전질의응답 REST 컨트롤러.
 *
 * <p>기본 URL은 {@code /api/council}이며, 협의회 개최 전 Q&A 엔드포인트만 담당합니다.</p>
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilQnaController {

    /** 사전질의응답 서비스 */
    private final QnaService qnaService;

    /**
     * 사전질의응답 목록 조회.
     *
     * <p>해당 협의회의 전체 질의응답 목록을 등록일시 오름차순으로 반환합니다.
     * 평가위원과 추진부서 담당자 모두 조회 가능합니다.</p>
     *
     * @param asctId 협의회ID
     * @return HTTP 200 + 질의응답 목록
     */
    @Operation(summary = "사전질의응답 목록 조회", description = "협의회의 사전질의응답 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @GetMapping("/{asctId}/qna")
    public ResponseEntity<List<CouncilDto.QnaResponse>> getQnaList(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(qnaService.getQnaList(asctId));
    }

    /**
     * 사전 질의 등록 (평가위원).
     *
     * <p>평가위원이 협의회 개최 전 사전 질의를 등록합니다.
     * QTN_ID는 자동 채번됩니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     질의 등록 요청
     * @param userDetails 로그인한 평가위원
     * @return HTTP 200 + 생성된 질의응답ID
     */
    @Operation(summary = "사전 질의 등록", description = "평가위원이 사전 질의를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 협의회", content = @Content)
    })
    @PostMapping("/{asctId}/qna")
    public ResponseEntity<String> createQna(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @RequestBody CouncilDto.QnaCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String qtnId = qnaService.createQna(asctId, request, userDetails);
        return ResponseEntity.ok(qtnId);
    }

    /**
     * 사전 질의 답변 (추진부서 담당자).
     *
     * <p>추진부서 담당자(ITPZZ001)가 평가위원의 사전 질의에 답변합니다.
     * 답변 후 REP_YN='Y'로 변경됩니다.</p>
     *
     * @param asctId      협의회ID
     * @param qtnId       질의응답ID
     * @param request     답변 요청
     * @param userDetails 로그인한 담당자
     * @return HTTP 200
     */
    @Operation(summary = "사전 질의 답변", description = "추진부서 담당자가 사전 질의에 답변합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "답변 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 질의응답", content = @Content)
    })
    @PutMapping("/{asctId}/qna/{qtnId}")
    public ResponseEntity<Void> replyQna(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Parameter(description = "질의응답ID", required = true, example = "QTN-ASCT-2026-0001-01")
            @PathVariable("qtnId") String qtnId,
            @RequestBody CouncilDto.QnaReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        qnaService.replyQna(asctId, qtnId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "사전질의 수정", description = "질의 등록자(또는 IT관리자)가 질의 내용을 수정합니다.")
    @PatchMapping("/{asctId}/qna/{qtnId}")
    public ResponseEntity<Void> updateQna(
            @Parameter(description = "협의회ID", required = true, example = "ASCT-2026-0001")
            @PathVariable("asctId") String asctId,
            @Parameter(description = "질의응답ID", required = true, example = "QTN-ASCT-2026-0001-01")
            @PathVariable("qtnId") String qtnId,
            @RequestBody CouncilDto.QnaUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        qnaService.updateQna(asctId, qtnId, request, userDetails);
        return ResponseEntity.ok().build();
    }
}
