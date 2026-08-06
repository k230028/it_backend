package com.kdb.it.common.board.controller;

import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 게시물 CRUD + 답변글 컨트롤러 */
@RestController
@RequestMapping("/api/boards/{blbMngNo}/posts")
@RequiredArgsConstructor
@Tag(name = "Board Post", description = "게시물 API")
public class BoardPostController {

    private final BoardPostService boardPostService;

    /**
     * 게시물 목록 조회
     *
     * @param blbMngNo 게시판관리번호
     * @param cond 검색 조건
     * @param user 인증 사용자
     * @return 게시물 페이지
     */
    @GetMapping
    @Operation(summary = "게시물 목록 조회")
    public ResponseEntity<Page<BoardPostDto.ListItem>> searchPosts(
            @PathVariable("blbMngNo") String blbMngNo,
            @ParameterObject @ModelAttribute BoardPostDto.SearchCondition cond,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(boardPostService.searchPosts(blbMngNo, cond, user));
    }

    /**
     * 게시물 상세 조회
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @return 게시물 상세 DTO
     */
    @GetMapping("/{nacMngNo}")
    @Operation(summary = "게시물 상세 조회")
    public ResponseEntity<BoardPostDto.Detail> getDetail(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(boardPostService.getPostDetail(blbMngNo, nacMngNo, user));
    }

    /**
     * 게시물 조회수 증가
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     * @return 본문 없는 성공 응답
     */
    @PostMapping("/{nacMngNo}/views")
    @Operation(summary = "게시물 조회수 증가")
    public ResponseEntity<Void> incrementViewCount(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        boardPostService.incrementPostView(blbMngNo, nacMngNo, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시물 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param request 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 게시물관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "게시물 등록")
    public ResponseEntity<String> create(
            @PathVariable("blbMngNo") String blbMngNo,
            @Valid @RequestBody BoardPostDto.CreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        String nacMngNo = boardPostService.createPost(blbMngNo, request, user);
        return ResponseEntity.created(URI.create("/api/boards/" + blbMngNo + "/posts/" + nacMngNo))
                .body(nacMngNo);
    }

    /**
     * 게시물 수정
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param request 수정 요청 DTO
     * @param user 인증 사용자
     */
    @PutMapping("/{nacMngNo}")
    @Operation(summary = "게시물 수정")
    public ResponseEntity<Void> update(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @Valid @RequestBody BoardPostDto.UpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        boardPostService.updatePost(blbMngNo, nacMngNo, request, user);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시물 삭제 (Soft Delete)
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 게시물관리번호
     * @param user 인증 사용자
     */
    @DeleteMapping("/{nacMngNo}")
    @Operation(summary = "게시물 삭제 (Soft Delete)")
    public ResponseEntity<Void> delete(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @AuthenticationPrincipal CustomUserDetails user) {
        boardPostService.deletePost(blbMngNo, nacMngNo, user);
        return ResponseEntity.noContent().build();
    }

    /**
     * 답변글 등록
     *
     * @param blbMngNo 게시판관리번호
     * @param nacMngNo 부모 게시물관리번호
     * @param request 답변글 등록 요청 DTO
     * @param user 인증 사용자
     * @return 생성된 답변글관리번호 (Location 헤더 포함)
     */
    @PostMapping("/{nacMngNo}/replies")
    @Operation(summary = "답변글 등록")
    public ResponseEntity<String> createReply(
            @PathVariable("blbMngNo") String blbMngNo,
            @PathVariable("nacMngNo") String nacMngNo,
            @Valid @RequestBody BoardPostDto.ReplyCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        String replyId = boardPostService.createReply(blbMngNo, nacMngNo, request, user);
        return ResponseEntity.created(URI.create("/api/boards/" + blbMngNo + "/posts/" + replyId))
                .body(replyId);
    }
}
