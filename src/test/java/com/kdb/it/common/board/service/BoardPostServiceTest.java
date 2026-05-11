package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BoardPostServiceTest {

    @Mock BoardMetaRepository metaRepository;
    @Mock BoardPostRepository postRepository;
    @InjectMocks BoardPostService service;

    private Cblbmm publicBoard;
    private Cblbmm adminOnlyBoard;
    private CustomUserDetails adminUser;
    private CustomUserDetails normalUser;

    @BeforeEach
    void setUp() {
        publicBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0001").blbNm("공지사항")
            .inqAthC("ALL").enrAthC("ROLE_ADMIN")
            .repUseYn("N").cmmtUseYn("N")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        adminOnlyBoard = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0099").blbNm("내부게시판")
            .inqAthC("ROLE_ADMIN").enrAthC("ROLE_ADMIN")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        adminUser  = new CustomUserDetails("ADMIN001", List.of("ITPAD001"), "10001");
        normalUser = new CustomUserDetails("USER001",  List.of("ITPZZ001"), "10002");
    }

    @Test
    @DisplayName("관리자가 아닌 사용자는 관리자 전용 게시판 목록을 조회할 수 없다")
    void searchPosts_nonAdminOnAdminBoard_throwsForbidden() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0099", "N"))
            .willReturn(Optional.of(adminOnlyBoard));

        assertThatThrownBy(() ->
            service.searchPosts("BLBM-2026-0099", new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(), normalUser)
        ).isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("공개 게시판 게시물 목록을 일반 사용자가 조회할 수 있다")
    void searchPosts_publicBoard_normalUser_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard));
        given(postRepository.searchPosts(any(), any(), anyBoolean(), any(), any()))
            .willReturn(List.of());

        var result = service.searchPosts(
            "BLBM-2026-0001",
            new com.kdb.it.common.board.dto.BoardPostDto.SearchCondition(),
            normalUser
        );
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("ROLE_ADMIN 등록 게시판에 일반 사용자가 게시물을 등록하면 예외가 발생한다")
    void createPost_noWritePermission_throwsForbidden() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(publicBoard)); // enrAthC = ROLE_ADMIN

        var req = new com.kdb.it.common.board.dto.BoardPostDto.CreateRequest();
        req.setNacNm("제목");

        assertThatThrownBy(() -> service.createPost("BLBM-2026-0001", req, normalUser))
            .isInstanceOf(CustomGeneralException.class);
    }
}
