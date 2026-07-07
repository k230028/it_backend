package com.kdb.it.common.board.controller;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.service.BoardCommentService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * 게시판 댓글 컨트롤러
 */
@RestController
@RequestMapping("/api/boards/{blbMngNo}/posts/{nacMngNo}/comments")
@RequiredArgsConstructor
@Tag(name = "Board Comment", description = "댓글 API")
public class BoardCommentController {

    private final BoardCommentService boardCommentService;

    /**
     * 댓글 목록 조회 (트리 정렬)
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user     인증 사용자
     * @return 트리 정렬된 댓글 목록
     */
    @GetMapping
    @Operation(summary = "댓글 목록 조회 (트리 정렬)")
    public ResponseEntity<List<BoardCommentDto.Response>> getComments(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(boardCommentService.getComments(blbMngNo, nacMngNo, user));
    }

    /**
     * 댓글 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request  댓글 등록 요청 DTO
     * @param user     인증 사용자
     * @return 생성된 댓글관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "댓글 등록")
    public ResponseEntity<Long> create(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @Valid @RequestBody BoardCommentDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long cmmtMngNo = boardCommentService.createComment(blbMngNo, nacMngNo, request, user);
        return ResponseEntity.created(
            URI.create("/api/boards/" + blbMngNo + "/posts/" + nacMngNo + "/comments/" + cmmtMngNo)
        ).body(cmmtMngNo);
    }

    /**
     * 대댓글 등록
     *
     * @param blbMngNo    게시판관리번호
     * @param nacMngNo    게시물관리번호
     * @param cmmtMngNo   부모 댓글관리번호
     * @param request     댓글 등록 요청 DTO
     * @param user        인증 사용자
     * @return 생성된 대댓글관리번호 (Location 헤더 포함)
     */
    @PostMapping("/{cmmtMngNo}/replies")
    @Operation(summary = "대댓글 등록")
    public ResponseEntity<Long> createReply(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @PathVariable("cmmtMngNo") Long cmmtMngNo,
            @RequestBody BoardCommentDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long replyId = boardCommentService.createReply(blbMngNo, nacMngNo, cmmtMngNo, request, user);
        return ResponseEntity.created(
            URI.create("/api/boards/" + blbMngNo + "/posts/" + nacMngNo + "/comments/" + replyId)
        ).body(replyId);
    }

    /**
     * 댓글 수정
     *
     * @param blbMngNo  게시판관리번호
     * @param nacMngNo  게시물관리번호
     * @param cmmtMngNo 댓글관리번호
     * @param request   수정 요청 DTO
     * @param user      인증 사용자
     */
    @PutMapping("/{cmmtMngNo}")
    @Operation(summary = "댓글 수정")
    public ResponseEntity<Void> update(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @PathVariable("cmmtMngNo") Long cmmtMngNo,
            @RequestBody BoardCommentDto.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        boardCommentService.updateComment(cmmtMngNo, request, user);
        return ResponseEntity.ok().build();
    }

    /**
     * 댓글 삭제 (Soft Delete)
     *
     * @param blbMngNo  게시판관리번호
     * @param nacMngNo  게시물관리번호
     * @param cmmtMngNo 댓글관리번호
     * @param user      인증 사용자
     */
    @DeleteMapping("/{cmmtMngNo}")
    @Operation(summary = "댓글 삭제 (Soft Delete)")
    public ResponseEntity<Void> delete(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @PathVariable("cmmtMngNo") Long cmmtMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        boardCommentService.deleteComment(cmmtMngNo, user);
        return ResponseEntity.noContent().build();
    }
}
