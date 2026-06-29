package com.kdb.it.common.board.controller;

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
import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

/**
 * BoardPostController @WebMvcTest
 *
 * <p>게시물 등록 요청의 제목(nacNm) Bean Validation 동작을 검증합니다.</p>
 */
@WebMvcTest(BoardPostController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class BoardPostControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BoardPostService boardPostService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts - 제목(nacNm) 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createPost_제목누락_400() throws Exception {
        var body = new BoardPostDto.CreateRequest();
        body.setNacNm(null); // @NotBlank 위반
        mockMvc.perform(post("/api/boards/BLB-1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
