package com.kdb.it.common.board.controller;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 게시판 메타 조회 컨트롤러 (인증 사용자 공통)
 */
@RestController
@RequestMapping("/api/boards/meta")
@RequiredArgsConstructor
@Tag(name = "Board Meta", description = "게시판 메타 조회 API")
public class BoardMetaController {

    private final BoardMetaService boardMetaService;

    /**
     * 사이드바용 활성 게시판 목록 조회
     *
     * @return 활성 게시판 목록
     */
    @GetMapping
    @Operation(summary = "게시판 목록 조회 (사이드바용)")
    public ResponseEntity<List<BoardMetaDto.Response>> getAll() {
        return ResponseEntity.ok(boardMetaService.getAllActive());
    }

    /**
     * 게시판 단건 조회
     *
     * @param blbMngNo 게시판관리번호
     * @return 게시판 상세 정보
     */
    @GetMapping("/{blbMngNo}")
    @Operation(summary = "게시판 단건 조회")
    public ResponseEntity<BoardMetaDto.Response> getOne(@PathVariable("blbMngNo") String blbMngNo) {
        return ResponseEntity.ok(boardMetaService.getOne(blbMngNo));
    }
}
