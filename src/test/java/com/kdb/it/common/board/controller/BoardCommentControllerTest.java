package com.kdb.it.common.board.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.board.dto.BoardCommentDto;
import com.kdb.it.common.board.service.BoardCommentService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * BoardCommentController @WebMvcTest
 *
 * <p>댓글 등록 요청의 Bean Validation 동작을 검증합니다.</p>
 */
@WebMvcTest(BoardCommentController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class BoardCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BoardCommentService boardCommentService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts/{nacMngNo}/comments - 정상 요청 → 201")
    @WithMockUser(username = "10001")
    void create_정상요청_201() throws Exception {
        given(boardCommentService.createComment(anyString(), anyString(), any(), any())).willReturn(1L);

        var body = new BoardCommentDto.CreateRequest();
        body.setCmmtCone("정상 댓글");

        mockMvc.perform(post("/api/boards/BLB-1/posts/NAC-1/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts/{nacMngNo}/comments - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void create_필수필드누락_400() throws Exception {
        var body = new BoardCommentDto.CreateRequest();
        body.setCmmtCone(null);

        mockMvc.perform(post("/api/boards/BLB-1/posts/NAC-1/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
