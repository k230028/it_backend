package com.kdb.it.common.board.controller;

import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.service.BoardCommentService;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.system.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BoardControllerUnitTest {

    private final CustomUserDetails user = new CustomUserDetails("USER001", List.of("ITPZZ001"), "D001");

    @Test
    @DisplayName("BoardMetaController는 목록과 단건 조회 결과를 반환한다")
    void boardMetaController_returnsResponses() {
        BoardMetaService service = mock(BoardMetaService.class);
        BoardMetaController controller = new BoardMetaController(service);
        BoardMetaDto.Response response = BoardMetaDto.Response.from(board("BLBM-2026-0001", "공지사항"));
        given(service.getAllActive()).willReturn(List.of(response));
        given(service.getOne("BLBM-2026-0001")).willReturn(response);

        var listResult = controller.getAll();
        var oneResult = controller.getOne("BLBM-2026-0001");

        assertThat(listResult.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResult.getBody()).containsExactly(response);
        assertThat(oneResult.getBody()).isEqualTo(response);
    }

    @Test
    @DisplayName("AdminBoardMetaController는 생성·수정·삭제 요청을 서비스에 위임한다")
    void adminBoardMetaController_delegatesCommands() {
        BoardMetaService service = mock(BoardMetaService.class);
        AdminBoardMetaController controller = new AdminBoardMetaController(service);
        BoardMetaDto.CreateRequest createRequest = new BoardMetaDto.CreateRequest();
        BoardMetaDto.UpdateRequest updateRequest = new BoardMetaDto.UpdateRequest();
        given(service.createBoard(createRequest)).willReturn("BLBM-2026-0001");

        var created = controller.create(createRequest);
        var updated = controller.update("BLBM-2026-0001", updateRequest);
        var deleted = controller.delete("BLBM-2026-0001");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/boards/meta/BLBM-2026-0001");
        assertThat(created.getBody()).isEqualTo("BLBM-2026-0001");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).updateBoard("BLBM-2026-0001", updateRequest);
        verify(service).deleteBoard("BLBM-2026-0001");
    }

    @Test
    @DisplayName("BoardPostController는 게시물 CRUD와 답변글 생성을 위임한다")
    void boardPostController_delegatesPostApis() {
        BoardPostService service = mock(BoardPostService.class);
        BoardPostController controller = new BoardPostController(service);
        BoardPostDto.SearchCondition condition = new BoardPostDto.SearchCondition();
        BoardPostDto.CreateRequest createRequest = new BoardPostDto.CreateRequest();
        BoardPostDto.UpdateRequest updateRequest = new BoardPostDto.UpdateRequest();
        BoardPostDto.ReplyCreateRequest replyRequest = new BoardPostDto.ReplyCreateRequest();
        BoardPostDto.ListItem listItem = new BoardPostDto.ListItem();
        BoardPostDto.Detail detail = new BoardPostDto.Detail();
        given(service.searchPosts("BLBM-2026-0001", condition, user)).willReturn(List.of(listItem));
        given(service.getPostDetail("BLBM-2026-0001", "NAC-1", user)).willReturn(detail);
        given(service.createPost("BLBM-2026-0001", createRequest, user)).willReturn("NAC-2");
        given(service.createReply("BLBM-2026-0001", "NAC-1", replyRequest, user)).willReturn("NAC-3");

        assertThat(controller.searchPosts("BLBM-2026-0001", condition, user).getBody()).containsExactly(listItem);
        assertThat(controller.getDetail("BLBM-2026-0001", "NAC-1", user).getBody()).isEqualTo(detail);
        var created = controller.create("BLBM-2026-0001", createRequest, user);
        var updated = controller.update("BLBM-2026-0001", "NAC-1", updateRequest, user);
        var deleted = controller.delete("BLBM-2026-0001", "NAC-1", user);
        var replied = controller.createReply("BLBM-2026-0001", "NAC-1", replyRequest, user);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/boards/BLBM-2026-0001/posts/NAC-2");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(replied.getBody()).isEqualTo("NAC-3");
        verify(service).updatePost("BLBM-2026-0001", "NAC-1", updateRequest, user);
        verify(service).deletePost("BLBM-2026-0001", "NAC-1", user);
    }

    @Test
    @DisplayName("BoardCommentController는 댓글 조회·등록·수정·삭제를 위임한다")
    void boardCommentController_delegatesCommentApis() {
        BoardCommentService service = mock(BoardCommentService.class);
        BoardCommentController controller = new BoardCommentController(service);
        BoardCommentDto.CreateRequest createRequest = new BoardCommentDto.CreateRequest("댓글");
        BoardCommentDto.UpdateRequest updateRequest = new BoardCommentDto.UpdateRequest("수정");
        BoardCommentDto.Response response = new BoardCommentDto.Response();
        given(service.getComments("BLBM-2026-0001", "NAC-1", user)).willReturn(List.of(response));
        given(service.createComment("BLBM-2026-0001", "NAC-1", createRequest, user)).willReturn("CMMT-1");
        given(service.createReply("BLBM-2026-0001", "NAC-1", "CMMT-1", createRequest, user)).willReturn("CMMT-2");

        assertThat(controller.getComments("BLBM-2026-0001", "NAC-1", user).getBody()).containsExactly(response);
        var created = controller.create("BLBM-2026-0001", "NAC-1", createRequest, user);
        var replied = controller.createReply("BLBM-2026-0001", "NAC-1", "CMMT-1", createRequest, user);
        var updated = controller.update("BLBM-2026-0001", "NAC-1", "CMMT-1", updateRequest, user);
        var deleted = controller.delete("BLBM-2026-0001", "NAC-1", "CMMT-1", user);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasToString("/api/boards/BLBM-2026-0001/posts/NAC-1/comments/CMMT-1");
        assertThat(replied.getBody()).isEqualTo("CMMT-2");
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).updateComment("CMMT-1", updateRequest, user);
        verify(service).deleteComment("CMMT-1", user);
    }

    private static com.kdb.it.common.board.entity.Cblbmm board(String id, String name) {
        return com.kdb.it.common.board.entity.Cblbmm.builder()
            .blbMngNo(id)
            .blbNm(name)
            .inqAthC("ALL")
            .enrAthC("ALL")
            .repUseYn("Y")
            .cmmtUseYn("Y")
            .useYn("Y")
            .delYn("N")
            .build();
    }
}
