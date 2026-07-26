package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.infra.file.repository.FileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BoardPostFileCacheServiceTest {

    private final BoardPostRepository boardPostRepository = mock(BoardPostRepository.class);
    private final FileRepository fileRepository = mock(FileRepository.class);
    private final BoardPostFileCacheService fileCacheService =
            new BoardPostFileCacheService(boardPostRepository, fileRepository);

    @Test
    @DisplayName("활성 첨부 수에 따라 게시물 파일 캐시를 갱신한다")
    void sync_활성첨부수_게시물캐시갱신() {
        Cblbcm post =
                Cblbcm.builder()
                        .nacMngNo("NAC-001")
                        .blbMngNo("BLB-001")
                        .nacNm("게시물")
                        .nacInqNbr(0)
                        .flNbr(0)
                        .flApgYn("N")
                        .ancYn("N")
                        .nacUnqId("NAC-001")
                        .nacGrpSqn(0)
                        .nacGrpLev(0)
                        .delYn("N")
                        .build();
        given(boardPostRepository.findByNacMngNoAndDelYn("NAC-001", "N"))
                .willReturn(Optional.of(post));

        fileCacheService.sync("NAC-001", 2);

        assertThat(post.getFlApgYn()).isEqualTo("Y");
        assertThat(post.getFlNbr()).isEqualTo(2);

        fileCacheService.sync("NAC-001", 0);

        assertThat(post.getFlApgYn()).isEqualTo("N");
        assertThat(post.getFlNbr()).isZero();
    }

    @Test
    @DisplayName("부모 행을 잠근 뒤 실제 활성 첨부 수를 집계해 게시물 캐시를 갱신한다")
    void syncFromActiveFiles_부모잠금후집계_게시물캐시갱신() {
        Cblbcm post =
                Cblbcm.builder()
                        .nacMngNo("NAC-001")
                        .blbMngNo("BLB-001")
                        .nacNm("게시물")
                        .nacInqNbr(0)
                        .flNbr(0)
                        .flApgYn("N")
                        .ancYn("N")
                        .nacUnqId("NAC-001")
                        .nacGrpSqn(0)
                        .nacGrpLev(0)
                        .delYn("N")
                        .build();
        given(boardPostRepository.findByNacMngNoAndDelYnForUpdate("NAC-001", "N"))
                .willReturn(Optional.of(post));
        given(fileRepository.countByPkColNmAndPkConeAndDelYn("공통게시판", "NAC-001", "N"))
                .willReturn(2L);

        fileCacheService.syncFromActiveFiles("NAC-001");

        assertThat(post.getFlApgYn()).isEqualTo("Y");
        assertThat(post.getFlNbr()).isEqualTo(2);
    }
}
