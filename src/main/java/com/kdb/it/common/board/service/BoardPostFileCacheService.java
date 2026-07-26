package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 게시물의 활성 첨부파일 수 캐시를 갱신합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardPostFileCacheService {

    private static final String BOARD_FILE_TYPE = "공통게시판";

    private final BoardPostRepository boardPostRepository;
    private final FileRepository fileRepository;

    /**
     * 활성 첨부파일 수를 게시물 캐시에 반영합니다.
     *
     * @param nacMngNo 게시물 관리번호
     * @param activeFileCount 삭제되지 않은 활성 첨부파일 수
     * @throws CustomGeneralException 게시물이 존재하지 않거나 파일 수가 음수인 경우
     */
    @Transactional
    public void sync(String nacMngNo, int activeFileCount) {
        if (activeFileCount < 0) {
            throw new CustomGeneralException("활성 첨부파일 수는 0 이상이어야 합니다.");
        }
        Cblbcm post =
                boardPostRepository
                        .findByNacMngNoAndDelYn(nacMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 게시물입니다. 게시물관리번호: " + nacMngNo));
        post.updateFileCache(activeFileCount > 0, activeFileCount);
    }

    /**
     * 게시물 행을 잠근 뒤 활성 첨부파일 수를 다시 조회하여 캐시에 반영합니다.
     *
     * @param nacMngNo 게시물 관리번호
     * @throws CustomGeneralException 게시물이 존재하지 않는 경우
     */
    @Transactional
    public void syncFromActiveFiles(String nacMngNo) {
        Cblbcm post =
                boardPostRepository
                        .findByNacMngNoAndDelYnForUpdate(nacMngNo, "N")
                        .orElseThrow(
                                () ->
                                        new CustomGeneralException(
                                                "존재하지 않는 게시물입니다. 게시물관리번호: " + nacMngNo));
        long activeFileCount =
                fileRepository.countByPkColNmAndPkConeAndDelYn(BOARD_FILE_TYPE, nacMngNo, "N");
        post.updateFileCache(activeFileCount > 0, Math.toIntExact(activeFileCount));
    }
}
