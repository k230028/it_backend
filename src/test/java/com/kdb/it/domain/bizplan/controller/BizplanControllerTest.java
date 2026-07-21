package com.kdb.it.domain.bizplan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.bizplan.service.BizplanService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BizplanController @WebMvcTest
 *
 * <p>사업계획 API의 인증/인가 동작과 HTTP 응답 구조·상태코드 매핑을 검증합니다. 서비스 계층은 {@link MockitoBean}으로 대체하며, 인증은 {@link
 * WithMockUser}로 시뮬레이션합니다. 권한/유효성 예외의 HTTP 상태코드 매핑은 {@code GlobalExceptionHandler}가 담당합니다.
 */
@WebMvcTest(BizplanController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class BizplanControllerTest {

    private static final String PRJ = "PRJ-2026-0001";
    private static final String BASE = "/api/project/bizplans";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private BizplanService bizplanService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    /** 목록 응답 1건 */
    private BizplanDto.ListItem listItem() {
        return new BizplanDto.ListItem(
                PRJ,
                "차세대 시스템 구축",
                "18001",
                "정보기술부",
                "2026",
                new BigDecimal("1650000"),
                "21",
                LocalDateTime.of(2026, 3, 1, 10, 0));
    }

    /** 상세 응답 (자식 목록은 비어 있음) */
    private BizplanDto.Detail detail() {
        return new BizplanDto.Detail(
                PRJ,
                "차세대 시스템 구축",
                "BG-2026-0001",
                new BigDecimal("1650000"),
                "01",
                "<p>보고서</p>",
                "21",
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    @DisplayName("GET /api/project/bizplans - 인증된 사용자 → 200 + 목록 반환")
    @WithMockUser(username = "10001")
    void list_인증된사용자_200반환() throws Exception {
        given(bizplanService.list(any())).willReturn(List.of(listItem()));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abusMngNo").value(PRJ))
                .andExpect(jsonPath("$[0].stsTc").value("21"));
    }

    @Test
    @DisplayName("GET /api/project/bizplans - 비인증 요청 → 401 반환")
    void list_비인증_401반환() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/project/bizplans/{abusMngNo} - 진입(create-or-get) → 200 + 상세")
    @WithMockUser(username = "10001")
    void enter_인증된사용자_200반환() throws Exception {
        given(bizplanService.getOrCreate(eq(PRJ), any())).willReturn(detail());

        mockMvc.perform(post(BASE + "/" + PRJ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abusMngNo").value(PRJ))
                .andExpect(jsonPath("$.bgNo").value("BG-2026-0001"));
    }

    @Test
    @DisplayName("POST /api/project/bizplans/{abusMngNo} - 주관부서/관리자 아님 → 403 반환")
    @WithMockUser(username = "10001")
    void enter_권한없음_403반환() throws Exception {
        given(bizplanService.getOrCreate(eq(PRJ), any()))
                .willThrow(new AccessDeniedException("사업 주관부서 또는 관리자만 수행할 수 있습니다."));

        mockMvc.perform(post(BASE + "/" + PRJ)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/project/bizplans/{abusMngNo} - 상세 재조회 → 200 + 상세")
    @WithMockUser(username = "10001")
    void get_인증된사용자_200반환() throws Exception {
        given(bizplanService.get(eq(PRJ), any())).willReturn(detail());

        mockMvc.perform(get(BASE + "/" + PRJ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abusMngNo").value(PRJ));
    }

    @Test
    @DisplayName("GET /api/project/bizplans/{abusMngNo} - 대상 없음 → 400 반환")
    @WithMockUser(username = "10001")
    void get_대상없음_400반환() throws Exception {
        given(bizplanService.get(eq(PRJ), any()))
                .willThrow(new IllegalArgumentException("사업계획이 아직 생성되지 않았습니다: " + PRJ));

        mockMvc.perform(get(BASE + "/" + PRJ)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/project/bizplans/{abusMngNo} - 전체 저장 → 200 + 서비스 호출")
    @WithMockUser(username = "10001")
    void save_유효요청_200반환() throws Exception {
        BizplanDto.SaveRequest req =
                new BizplanDto.SaveRequest("<p>보고서</p>", "01", List.of(), List.of(), List.of());

        mockMvc.perform(
                        put(BASE + "/" + PRJ)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(bizplanService).save(eq(PRJ), any(BizplanDto.SaveRequest.class), any());
    }

    @Test
    @DisplayName("PUT /api/project/bizplans/{abusMngNo} - schedules 누락(null) → 400 검증 오류")
    @WithMockUser(username = "10001")
    void save_필수목록누락_400반환() throws Exception {
        // schedules/items/contracts 는 @NotNull — 하나라도 null이면 검증 실패
        String invalidBody =
                "{\"redtConeInf\":\"<p>x</p>\",\"itPtlEdrtTc\":\"01\","
                        + "\"items\":[],\"contracts\":[]}";

        mockMvc.perform(
                        put(BASE + "/" + PRJ)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/project/bizplans/{abusMngNo}/status - 완료(29) 전이 → 200 + 서비스 호출")
    @WithMockUser(username = "10001")
    void changeStatus_유효요청_200반환() throws Exception {
        BizplanDto.StatusRequest req = new BizplanDto.StatusRequest("29");

        mockMvc.perform(
                        post(BASE + "/" + PRJ + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(bizplanService).complete(eq(PRJ), any(BizplanDto.StatusRequest.class), any());
    }

    @Test
    @DisplayName("POST /api/project/bizplans/{abusMngNo}/status - 허용되지 않은 전이 → 400 반환")
    @WithMockUser(username = "10001")
    void changeStatus_잘못된전이_400반환() throws Exception {
        doThrow(new IllegalStateException("작성중(21) 상태에서만 완료할 수 있습니다."))
                .when(bizplanService)
                .complete(eq(PRJ), any(BizplanDto.StatusRequest.class), any());

        BizplanDto.StatusRequest req = new BizplanDto.StatusRequest("29");
        mockMvc.perform(
                        post(BASE + "/" + PRJ + "/status")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
