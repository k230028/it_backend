package com.kdb.it.common.board.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;

@WebMvcTest(AdminBoardMetaController.class)
@Import({ TestSecurityConfig.class, JacksonConfig.class })
class AdminBoardMetaControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BoardMetaService boardMetaService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /api/admin/boards/meta - 정상 요청 → 201")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_정상요청_201() throws Exception {
        given(boardMetaService.createBoard(any())).willReturn("BLBM-0001");

        var body = new BoardMetaDto.CreateRequest();
        body.setBlbNm("공지사항");
        body.setBlbTp("001");

        mockMvc.perform(post("/api/admin/boards/meta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/admin/boards/meta - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void create_필수필드누락_400() throws Exception {
        var body = new BoardMetaDto.CreateRequest();
        body.setBlbNm(null);
        body.setBlbTp("001");

        mockMvc.perform(post("/api/admin/boards/meta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/admin/boards/meta/{blbMngNo} - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001", roles = "ADMIN")
    void update_필수필드누락_400() throws Exception {
        var body = new BoardMetaDto.UpdateRequest();
        body.setBlbNm(null);

        mockMvc.perform(put("/api/admin/boards/meta/BLBM-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
