package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.entity.Ccmmtm;
import com.kdb.it.common.board.repository.BoardCommentRepository;
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class BoardCommentServiceTest {

    @Mock BoardMetaRepository    metaRepository;
    @Mock BoardPostRepository    postRepository;
    @Mock BoardCommentRepository commentRepository;
    @Mock BoardPostService        postService;

    @InjectMocks BoardCommentService service;

    private Cblbmm boardWithComment;
    private Cblbmm boardNoComment;
    private Cblbcm post;
    private CustomUserDetails adminUser;
    private CustomUserDetails normalUser;

    @BeforeEach
    void setUp() {
        boardWithComment = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0001").blbNm("자유게시판")
            .inqAthC("ALL").enrAthC("ALL")
            .repUseYn("N").cmmtUseYn("Y")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        boardNoComment = Cblbmm.builder()
            .blbMngNo("BLBM-2026-0002").blbNm("공지사항")
            .inqAthC("ALL").enrAthC("ROLE_ADMIN")
            .repUseYn("N").cmmtUseYn("N")
            .bbrLmtnUseYn("N").useYn("Y").delYn("N")
            .build();

        post = Cblbcm.builder()
            .nacMngNo("NAC-2026-0001")
            .blbMngNo("BLBM-2026-0001")
            .nacNm("테스트 게시물")
            .delYn("N")
            .build();

        adminUser  = new CustomUserDetails("ADMIN001", List.of("ITPAD001"), "10001");
        normalUser = new CustomUserDetails("USER001",  List.of("ITPZZ001"), "10002");
    }

    @Test
    @DisplayName("댓글 미지원 게시판에 댓글을 등록하면 예외가 발생한다")
    void createComment_boardNoComment_throws() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0002", "N"))
            .willReturn(Optional.of(boardNoComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(post));

        var request = new BoardCommentDto.CreateRequest("테스트 댓글 내용");

        assertThatThrownBy(() ->
            service.createComment("BLBM-2026-0002", "NAC-2026-0001", request, normalUser)
        )
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("댓글 기능을 지원하지 않습니다");
    }

    @Test
    @DisplayName("댓글 지원 게시판에 댓글을 등록하면 CMMT- 형식의 ID가 반환된다")
    void createComment_success() {
        given(metaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(boardWithComment));
        given(postRepository.findByNacMngNoAndDelYn("NAC-2026-0001", "N"))
            .willReturn(Optional.of(post));
        // verifyCanReadPost는 void 메서드 — 아무 동작 없이 통과
        willDoNothing().given(postService).verifyCanReadPost(any(), any(), any());
        given(commentRepository.getNextSequenceValue()).willReturn(1L);
        given(commentRepository.save(any(Ccmmtm.class))).willAnswer(inv -> inv.getArgument(0));

        var request = new BoardCommentDto.CreateRequest("테스트 댓글 내용");
        String result = service.createComment("BLBM-2026-0001", "NAC-2026-0001", request, normalUser);

        assertThat(result).startsWith("CMMT-");
    }

    @Test
    @DisplayName("본인 댓글이 아닌 댓글을 수정하려 하면 예외가 발생한다")
    void updateComment_notOwner_throws() throws Exception {
        // fstEnrUsid 는 BaseEntity의 @CreatedBy 필드이므로 리플렉션으로 설정
        Ccmmtm comment = Ccmmtm.builder()
            .cmmtMngNo("CMMT-2026-0001")
            .nacMngNo("NAC-2026-0001")
            .cmmtCone("원본 댓글")
            .sreYn("Y")
            .cmmtGrpNo("CMMT-2026-0001")
            .cmmtGrpSqn(0)
            .cmmtGrpLev(0)
            .delYn("N")
            .build();

        // BaseEntity.fstEnrUsid 를 리플렉션으로 "OTHER_USER" 로 설정
        Field fstEnrUsidField = comment.getClass().getSuperclass().getDeclaredField("fstEnrUsid");
        fstEnrUsidField.setAccessible(true);
        fstEnrUsidField.set(comment, "OTHER_USER");

        given(commentRepository.findByCmmtMngNoAndDelYn("CMMT-2026-0001", "N"))
            .willReturn(Optional.of(comment));

        var request = new BoardCommentDto.UpdateRequest("수정 내용");

        // normalUser(USER001)는 OTHER_USER가 작성한 댓글을 수정할 수 없다
        assertThatThrownBy(() ->
            service.updateComment("CMMT-2026-0001", request, normalUser)
        ).isInstanceOf(CustomGeneralException.class);
    }
}
