package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 메타 서비스
 *
 * <p>게시판 생성·수정·삭제는 관리자 전용. 목록 조회는 인증 사용자 전체.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardMetaService {

    private final BoardMetaRepository boardMetaRepository;

    /**
     * 사이드바용 게시판 목록 조회.
     *
     * @return 사용 중이고 삭제되지 않은 게시판을 표시순서 오름차순으로 정렬한 목록
     */
    public List<BoardMetaDto.Response> getAllActive() {
        return boardMetaRepository.findAllActiveOrderedRows().stream()
                .map(BoardMetaDto.Response::from)
                .toList();
    }

    /**
     * 게시판 단건 조회
     *
     * @param blbMngNo 게시판관리번호 (예: BLBM-0001)
     * @return 게시판 메타 응답 DTO
     * @throws NotFoundException 게시판이 사용 중이 아니거나 삭제된 경우
     */
    public BoardMetaDto.Response getOne(String blbMngNo) {
        return BoardMetaDto.Response.from(findUserActiveBoard(blbMngNo));
    }

    /**
     * 게시판 신규 등록 (관리자 전용)
     *
     * @param request 게시판 등록 요청 DTO
     * @return 생성된 게시판관리번호
     */
    @Transactional
    public String createBoard(BoardMetaDto.CreateRequest request) {
        Long seq = boardMetaRepository.getNextSequenceValue();
        String blbMngNo = String.format("BLBM-%04d", seq);

        Cblbmm entity =
                Cblbmm.builder().blbMngNo(blbMngNo).itPtlBlbTc(request.getItPtlBlbTc()).build();
        entity.update(request.toUpdateCommand());
        boardMetaRepository.save(entity);
        return blbMngNo;
    }

    /**
     * 게시판 수정 (관리자 전용)
     *
     * @param blbMngNo 게시판관리번호
     * @param request 수정 요청 DTO
     * @throws CustomGeneralException 게시판을 찾을 수 없는 경우
     */
    @Transactional
    public void updateBoard(String blbMngNo, BoardMetaDto.UpdateRequest request) {
        findManageableBoard(blbMngNo).update(request.toUpdateCommand());
    }

    /**
     * 게시판 삭제 — Soft Delete (관리자 전용)
     *
     * @param blbMngNo 게시판관리번호
     * @throws CustomGeneralException 게시판을 찾을 수 없는 경우
     */
    @Transactional
    public void deleteBoard(String blbMngNo) {
        findManageableBoard(blbMngNo).delete();
    }

    /**
     * 활성 게시판 엔티티 조회 — 내부 공통 헬퍼
     *
     * @param blbMngNo 게시판관리번호
     * @return Cblbmm 엔티티
     * @throws NotFoundException 사용 중이 아니거나 삭제된 게시판인 경우
     */
    Cblbmm findUserActiveBoard(String blbMngNo) {
        return boardMetaRepository
                .findByBlbMngNoAndUseYnAndDelYn(blbMngNo, "Y", "N")
                .orElseThrow(() -> new NotFoundException("게시판을 찾을 수 없습니다: " + blbMngNo));
    }

    Cblbmm findManageableBoard(String blbMngNo) {
        return boardMetaRepository
                .findByBlbMngNoAndDelYn(blbMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException("게시판을 찾을 수 없습니다: " + blbMngNo));
    }
}
