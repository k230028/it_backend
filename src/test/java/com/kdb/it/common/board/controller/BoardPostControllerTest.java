package com.kdb.it.common.board.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.board.dto.BoardPostDto;
import com.kdb.it.common.board.service.BoardPostService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BoardPostController @WebMvcTest
 *
 * <p>게시물 등록 요청의 제목(nacNm) Bean Validation 동작을 검증합니다.
 */
@WebMvcTest(BoardPostController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class BoardPostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BoardPostService boardPostService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String NAC_MNG_NO = "NAC-2026-0001";

    @Test
    @DisplayName("GET /api/boards/{blbMngNo}/posts/{nacMngNo}는 상세 조회만 수행한다")
    @WithMockUser(username = "10001", roles = "USER")
    void getDetail_doesNotIncrementViewCount() throws Exception {
        mockMvc.perform(get("/api/boards/BLB-1/posts/" + NAC_MNG_NO)).andExpect(status().isOk());

        verify(boardPostService, times(1)).getPostDetail(anyString(), anyString(), any());
        verify(boardPostService, never()).incrementPostView(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts/{nacMngNo}/views는 조회수를 한 번 증가시킨다")
    @WithMockUser(username = "10001", roles = "USER")
    void incrementViewCount_once_returns204() throws Exception {
        mockMvc.perform(post("/api/boards/BLB-1/posts/" + NAC_MNG_NO + "/views"))
                .andExpect(status().isNoContent());

        verify(boardPostService, times(1)).incrementPostView(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts - 정상 등록 → 201 + 생성된 게시물관리번호 반환")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createPost_정상_201() throws Exception {
        given(boardPostService.createPost(anyString(), any(), any())).willReturn(NAC_MNG_NO);

        var body = new BoardPostDto.CreateRequest();
        body.setNacNm("정상 제목"); // @NotBlank 충족
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(content().string(NAC_MNG_NO));
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts - 제목(nacNm) 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createPost_제목누락_400() throws Exception {
        var body = new BoardPostDto.CreateRequest();
        body.setNacNm(null); // @NotBlank 위반
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts - 본문 4000자 초과 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createPost_본문초과_400() throws Exception {
        var body = new BoardPostDto.CreateRequest();
        body.setNacNm("정상 제목");
        body.setNacCone("가".repeat(4001));
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/boards/{blbMngNo}/posts/{nacMngNo} - 제목(nacNm) 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updatePost_제목누락_400() throws Exception {
        var body = new BoardPostDto.UpdateRequest();
        body.setNacNm(null); // @NotBlank 위반
        mockMvc.perform(
                        put("/api/boards/BLB-1/posts/" + NAC_MNG_NO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/boards/{blbMngNo}/posts/{nacMngNo} - 본문 4000자 초과 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void updatePost_본문초과_400() throws Exception {
        var body = new BoardPostDto.UpdateRequest();
        body.setNacNm("정상 제목");
        body.setNacCone("나".repeat(4001));
        mockMvc.perform(
                        put("/api/boards/BLB-1/posts/" + NAC_MNG_NO)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts/{nacMngNo}/replies - 제목(nacNm) 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createReply_제목누락_400() throws Exception {
        var body = new BoardPostDto.ReplyCreateRequest();
        body.setNacNm(null); // @NotBlank 위반
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts/" + NAC_MNG_NO + "/replies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/boards/{blbMngNo}/posts/{nacMngNo}/replies - 본문 4000자 초과 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void createReply_본문초과_400() throws Exception {
        var body = new BoardPostDto.ReplyCreateRequest();
        body.setNacNm("정상 제목");
        body.setNacCone("다".repeat(4001));
        mockMvc.perform(
                        post("/api/boards/BLB-1/posts/" + NAC_MNG_NO + "/replies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
