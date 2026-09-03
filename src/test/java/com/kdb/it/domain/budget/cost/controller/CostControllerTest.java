package com.kdb.it.domain.budget.cost.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.cost.service.CostVersionService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CostController.class)
@Import({TestSecurityConfig.class, JacksonConfig.class})
class CostControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CostService costService;
    @MockitoBean private CostVersionService costVersionService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private UsernamePasswordAuthenticationToken adminAuthentication() {
        CustomUserDetails admin =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");
        return new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/cost - 비인증 → 401")
    void getCostList_비인증_401() throws Exception {
        mockMvc.perform(get("/api/cost")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/cost - 인증된 사용자 → 200 + 배열 반환")
    @WithMockUser(username = "10001")
    void getCostList_인증_200() throws Exception {
        given(costService.searchCostList(any(), any(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/cost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                // 페이지를 지정하지 않으면 총건수 헤더도, COUNT 쿼리도 없다
                .andExpect(header().doesNotExist("X-Total-Count"));
        org.mockito.Mockito.verify(costService, org.mockito.Mockito.never())
                .countCostList(any(), any());
    }

    @Test
    @DisplayName("GET /api/cost?page=1&size=50 - 페이지 조회는 X-Total-Count로 전체 건수를 알린다")
    @WithMockUser(username = "10001")
    void getCostList_페이지지정_총건수헤더() throws Exception {
        given(costService.searchCostList(any(), any(), any())).willReturn(List.of());
        given(costService.countCostList(any(), any())).willReturn(777L);

        mockMvc.perform(get("/api/cost").param("page", "1").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "777"));
    }

    @Test
    @DisplayName("GET /api/cost/{itMngcNo} - 인증된 사용자 → 200")
    @WithMockUser(username = "10001")
    void getCost_인증_200() throws Exception {
        given(costService.getCost(eq("COST_2026_0001"), any())).willReturn(new CostDto.Response());
        mockMvc.perform(get("/api/cost/COST_2026_0001")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/cost/{itMngcNo}?sno=2 - 지정한 개정본을 조회한다")
    void getCost_순번지정_200() throws Exception {
        given(costService.getCost(eq("COST_2026_0001"), eq(2), any()))
                .willReturn(new CostDto.Response());

        mockMvc.perform(
                        get("/api/cost/COST_2026_0001")
                                .param("sno", "2")
                                .with(authentication(adminAuthentication())))
                .andExpect(status().isOk());

        verify(costService).getCost(eq("COST_2026_0001"), eq(2), any());
    }

    @Test
    @DisplayName("GET /api/cost/{itMngcNo}/history - 각 순번의 상세를 반환한다")
    void getHistory_개정이력_200() throws Exception {
        given(costVersionService.findHistory("COST_2026_0001"))
                .willReturn(
                        List.of(
                                Bcostm.builder().costBgNo("COST_2026_0001").bgSno(1).build(),
                                Bcostm.builder().costBgNo("COST_2026_0001").bgSno(2).build()));
        given(costService.getCost(eq("COST_2026_0001"), eq(1), any()))
                .willReturn(new CostDto.Response());
        given(costService.getCost(eq("COST_2026_0001"), eq(2), any()))
                .willReturn(new CostDto.Response());

        mockMvc.perform(
                        get("/api/cost/COST_2026_0001/history")
                                .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/cost - 인증된 사용자 → 201 Created + Location")
    @WithMockUser(username = "10001")
    void createCost_인증_201() throws Exception {
        given(costService.createCost(any())).willReturn("COST_2026_0001");
        var body = new CostDto.CreateRequest();
        body.setCurC("KRW");

        mockMvc.perform(
                        post("/api/cost")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/cost/COST_2026_0001"));
    }

    @Test
    @DisplayName("POST /api/cost/{itMngcNo}/reapplications - 경로 관리번호로 재상신 초안을 생성한다")
    void createReapplication_경로변수해석_200() throws Exception {
        given(costService.getCost(eq("COST_2027_0001"), any())).willReturn(new CostDto.Response());
        given(costVersionService.createReapplication("COST_2027_0001"))
                .willReturn(new CostVersionService.CostVersion("COST_2027_0001", 2, "N"));

        mockMvc.perform(
                        post("/api/cost/COST_2027_0001/reapplications")
                                .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costBgNo").value("COST_2027_0001"))
                .andExpect(jsonPath("$.bgSno").value(2));
    }

    @Test
    @DisplayName("재상신 API는 컴파일러 옵션과 무관하게 경로 변수명을 명시한다")
    void createReapplication_경로변수명을_명시한다() throws Exception {
        var method =
                CostController.class.getDeclaredMethod(
                        "createReapplication", String.class, CustomUserDetails.class);
        var annotation =
                method.getParameters()[0].getAnnotation(
                        org.springframework.web.bind.annotation.PathVariable.class);

        org.assertj.core.api.Assertions.assertThat(annotation.value()).isEqualTo("itMngcNo");
    }

    @Test
    @DisplayName("POST /api/cost - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void createCost_필수필드누락_400() throws Exception {
        var body = new CostDto.CreateRequest();
        body.setCurC(null);

        mockMvc.perform(
                        post("/api/cost")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cost/{itMngcNo} - 인증된 사용자 → 200 OK")
    @WithMockUser(username = "10001")
    void updateCost_인증_200() throws Exception {
        given(costService.updateCost(anyString(), any())).willReturn("COST_2026_0001");
        var body = new CostDto.UpdateRequest();
        body.setCurC("KRW");

        mockMvc.perform(
                        put("/api/cost/COST_2026_0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/cost/{itMngcNo}?sno=2 - 지정한 개정본을 수정한다")
    void updateCost_순번지정_200() throws Exception {
        given(costService.updateCost(eq("COST_2026_0001"), eq(2), any()))
                .willReturn("COST_2026_0001");
        var body = new CostDto.UpdateRequest();

        mockMvc.perform(
                        put("/api/cost/COST_2026_0001")
                                .param("sno", "2")
                                .with(authentication(adminAuthentication()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(costService).updateCost(eq("COST_2026_0001"), eq(2), any());
    }

    @Test
    @DisplayName("PUT /api/cost/{itMngcNo} - 필수 필드 누락 → 400")
    @WithMockUser(username = "10001")
    void updateCost_필수필드누락_400() throws Exception {
        var body = new CostDto.UpdateRequest();
        body.setCurC(null);

        mockMvc.perform(
                        put("/api/cost/COST_2026_0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/cost/{itMngcNo} - 시스템관리자는 통화가 없어도 수정 요청을 전달한다")
    void updateCost_관리자_통화누락_200() throws Exception {
        given(costService.updateCost(anyString(), any())).willReturn("COST_2026_0001");
        var body = new CostDto.UpdateRequest();

        mockMvc.perform(
                        put("/api/cost/COST_2026_0001")
                                .with(authentication(adminAuthentication()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/cost/{itMngcNo} - 인증된 사용자 → 204 No Content")
    @WithMockUser(username = "10001")
    void deleteCost_인증_204() throws Exception {
        mockMvc.perform(delete("/api/cost/COST_2026_0001")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/cost/{itMngcNo}?sno=2 - 지정한 개정본을 삭제한다")
    void deleteCost_순번지정_204() throws Exception {
        mockMvc.perform(
                        delete("/api/cost/COST_2026_0001")
                                .param("sno", "2")
                                .with(authentication(adminAuthentication())))
                .andExpect(status().isNoContent());

        verify(costService).deleteCost("COST_2026_0001", 2);
    }

    @Test
    @DisplayName("POST /api/cost/bulk-get - 인증된 사용자 → 200 + items/failedIds 반환")
    @WithMockUser(username = "10001")
    void getCostsByIds_인증_200() throws Exception {
        given(costService.getCostsByIds(any(), any()))
                .willReturn(new CostDto.BulkResponse(List.of(), List.of()));
        mockMvc.perform(
                        post("/api/cost/bulk-get")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new CostDto.BulkGetRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.failedIds").isArray());
    }

    @Test
    @DisplayName("GET /api/cost/terminals/service-names - 단말기 종류를 서비스에 전달한다")
    void getTerminalServiceNames_종류지정_200() throws Exception {
        given(costService.getTerminalServiceNames("01")).willReturn(List.of("네트워크 서비스"));

        mockMvc.perform(
                        get("/api/cost/terminals/service-names")
                                .param("tmnClsfC", "01")
                                .with(authentication(adminAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("네트워크 서비스"));
    }
}
