package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.exception.NotFoundException;

/** 게시판·게시물의 공통 활성 조회와 안전한 문자열 변환을 제공합니다. */
final class BoardLookupSupport {

    private BoardLookupSupport() {}

    static Cblbmm findUserActiveBoard(BoardMetaRepository repository, String blbMngNo) {
        return repository
                .findByBlbMngNoAndUseYnAndDelYn(blbMngNo, "Y", "N")
                .orElseThrow(() -> new NotFoundException("게시판을 찾을 수 없습니다: " + blbMngNo));
    }

    static Cblbcm findPost(BoardPostRepository repository, String blbMngNo, String nacMngNo) {
        return repository
                .findByBlbMngNoAndNacMngNoAndDelYn(blbMngNo, nacMngNo, "N")
                .orElseThrow(() -> new NotFoundException("게시물을 찾을 수 없습니다: " + nacMngNo));
    }

    static Cblbcm findPostForUpdate(
            BoardPostRepository repository, String blbMngNo, String nacMngNo) {
        return repository
                .findByBlbMngNoAndNacMngNoAndDelYnForUpdate(blbMngNo, nacMngNo, "N")
                .orElseThrow(() -> new NotFoundException("게시물을 찾을 수 없습니다: " + nacMngNo));
    }

    static String safe(String value) {
        return value == null ? "" : value;
    }
}
