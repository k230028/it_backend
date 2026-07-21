package com.kdb.it.domain.council.controller;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.service.MainQnaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정보화실무협의회 본회의 질의응답 REST 컨트롤러.
 *
 * <p>기본 URL은 {@code /api/council}이며, 본회의 질의응답(PRD §26) 엔드포인트만 담당합니다.
 */
@RestController
@RequestMapping("/api/council")
@RequiredArgsConstructor
@Tag(name = "Council", description = "정보화실무협의회 관리 API")
public class CouncilMainQnaController {

    /** 본회의 질의응답 서비스 */
    private final MainQnaService mainQnaService;

    /**
     * 본회의 질의응답 목록 조회 (PRD §26)
     *
     * <p>평가위원은 평가의견 작성 시 참고용으로 사용합니다.
     *
     * @param asctId 협의회ID
     * @return 삭제되지 않은 본회의 질의응답 목록
     */
    @Operation(summary = "본회의 질의응답 목록", description = "협의회의 본회의 질의응답 목록을 반환합니다.")
    @GetMapping("/{asctId}/main-qna")
    public ResponseEntity<List<CouncilDto.QnaResponse>> getMainQnaList(
            @PathVariable("asctId") String asctId) {
        return ResponseEntity.ok(mainQnaService.getMainQnaList(asctId));
    }

    /**
     * 본회의 질의 등록 (IT관리자 전용, PRD §26)
     *
     * @param asctId 협의회ID
     * @param request 질의 등록 요청
     * @param userDetails 인증된 IT관리자
     * @return 생성된 질의응답ID
     */
    @Operation(summary = "본회의 질의 등록", description = "IT관리자가 본회의에서 나온 질의를 정리해 등록합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{asctId}/main-qna")
    public ResponseEntity<String> createMainQna(
            @PathVariable("asctId") String asctId,
            @RequestBody @Valid CouncilDto.QnaCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String qtnId = mainQnaService.createMainQna(asctId, request, userDetails);
        return ResponseEntity.ok(qtnId);
    }

    /**
     * 본회의 질의 수정 (IT관리자 전용, PRD §26)
     *
     * @param asctId 협의회ID
     * @param qtnId 질의응답ID
     * @param request 질의 수정 요청
     * @return 본문 없는 HTTP 200 응답
     * @throws IllegalArgumentException 협의회 또는 질의가 없는 경우
     */
    @Operation(summary = "본회의 질의 수정", description = "IT관리자가 본회의 질의 내용을 수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{asctId}/main-qna/{qtnId}")
    public ResponseEntity<Void> updateMainQna(
            @PathVariable("asctId") String asctId,
            @PathVariable("qtnId") String qtnId,
            @RequestBody @Valid CouncilDto.QnaUpdateRequest request) {
        mainQnaService.updateMainQna(asctId, qtnId, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 본회의 답변 등록/수정 (IT관리자 전용, PRD §26)
     *
     * @param asctId 협의회ID
     * @param qtnId 질의응답ID
     * @param request 답변 요청
     * @param userDetails 인증된 IT관리자
     * @return 본문 없는 HTTP 200 응답
     * @throws IllegalArgumentException 협의회 또는 질의가 없는 경우
     */
    @Operation(summary = "본회의 답변", description = "IT관리자가 본회의 질의에 대한 답변을 정리해 등록·수정합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{asctId}/main-qna/{qtnId}")
    public ResponseEntity<Void> replyMainQna(
            @PathVariable("asctId") String asctId,
            @PathVariable("qtnId") String qtnId,
            @RequestBody @Valid CouncilDto.QnaReplyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        mainQnaService.replyMainQna(asctId, qtnId, request, userDetails);
        return ResponseEntity.ok().build();
    }

    /**
     * 본회의 질의응답 논리 삭제 (IT관리자 전용, PRD §26)
     *
     * @param asctId 협의회ID
     * @param qtnId 질의응답ID
     * @return 본문 없는 HTTP 204 응답
     * @throws IllegalArgumentException 협의회 또는 질의가 없는 경우
     */
    @Operation(summary = "본회의 질의응답 삭제", description = "IT관리자가 본회의 Q&A 항목을 삭제(Soft)합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{asctId}/main-qna/{qtnId}")
    public ResponseEntity<Void> deleteMainQna(
            @PathVariable("asctId") String asctId, @PathVariable("qtnId") String qtnId) {
        mainQnaService.deleteMainQna(asctId, qtnId);
        return ResponseEntity.noContent().build();
    }
}
