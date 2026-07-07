package com.kdb.it.common.board.controller;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * 게시판 메타 관리 컨트롤러 — 관리자 전용
 *
 * <p>{@code @PreAuthorize} 클래스 레벨 적용 필수 (CLAUDE.md §5.6)</p>
 */
@RestController
@RequestMapping("/api/admin/boards/meta")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Board Meta", description = "게시판 메타 관리 API (관리자 전용)")
public class AdminBoardMetaController {

    private final BoardMetaService boardMetaService;

    /**
     * 게시판 신규 등록
     *
     * @param request 게시판 등록 요청 DTO
     * @return 생성된 게시판관리번호 (Location 헤더 포함)
     */
    @PostMapping
    @Operation(summary = "게시판 등록")
    public ResponseEntity<String> create(@Valid @RequestBody BoardMetaDto.CreateRequest request) {
        String blbMngNo = boardMetaService.createBoard(request);
        return ResponseEntity.created(URI.create("/api/boards/meta/" + blbMngNo)).body(blbMngNo);
    }

    /**
     * 게시판 수정
     *
     * @param blbMngNo 게시판관리번호
     * @param request  수정 요청 DTO
     */
    @PutMapping("/{blbMngNo}")
    @Operation(summary = "게시판 수정")
    public ResponseEntity<Void> update(
            @PathVariable("blbMngNo") String blbMngNo,
            @RequestBody BoardMetaDto.UpdateRequest request) {
        boardMetaService.updateBoard(blbMngNo, request);
        return ResponseEntity.ok().build();
    }

    /**
     * 게시판 삭제 (Soft Delete)
     *
     * @param blbMngNo 게시판관리번호
     */
    @DeleteMapping("/{blbMngNo}")
    @Operation(summary = "게시판 삭제 (Soft Delete)")
    public ResponseEntity<Void> delete(@PathVariable("blbMngNo") String blbMngNo) {
        boardMetaService.deleteBoard(blbMngNo);
        return ResponseEntity.noContent().build();
    }
}
