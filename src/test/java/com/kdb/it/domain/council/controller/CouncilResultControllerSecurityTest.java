package com.kdb.it.domain.council.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.ResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CouncilResultController 결과 검토 상태 동기화(syncReviewStatus) 관리자 권한 경계 테스트 (ERR-10 Phase C4-1).
 *
 * <p>{@code POST /api/council/{asctId}/result/review/sync}는 프론트 라우트 가드로만 보호되던 엔드포인트로, 인증만 되어 있으면 어떤
 * 사용자든 협의회 검토 상태를 임의로 전이시킬 수 있었습니다. 메서드 수준 {@code @PreAuthorize("hasRole('ADMIN')")}가 실제로 강제되는지
 * {@link EnableMethodSecurity}를 명시적으로 활성화한 WebMvcTest 슬라이스에서 ROLE_ADMIN/USER/Anonymous별 접근 제어를
 * 검증합니다. 프론트 가드는 UX 보조일 뿐이며 서버 권한 판단이 최종 경계입니다.
 *
 * <p>다른 협의회 엔드포인트(목록 조회, 신청 등)는 이 메서드 수준 변경의 영향을 받지 않으므로 별도로 검증하지 않습니다.
 */
@WebMvcTest(CouncilResultController.class)
@Import({
    TestSecurityConfig.class,
    CouncilResultControllerSecurityTest.MethodSecurityTestConfig.class
})
class CouncilResultControllerSecurityTest {

    /** WebMvcTest 슬라이스는 기본적으로 {@code @EnableMethodSecurity}를 로드하지 않으므로 별도로 활성화합니다. */
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CouncilService councilService;
    @MockitoBean private CouncilApprovalService councilApprovalService;
    @MockitoBean private ResultService resultService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    private static final String ASCT_ID = "ASCT-2026-0001";
    private static final String SYNC_URL = "/api/council/" + ASCT_ID + "/result/review/sync";

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN — 200 + boolean 반환")
    void admin_returns200() throws Exception {
        // Arrange
        given(resultService.syncReviewStatus(ASCT_ID)).willReturn(true);

        // Act & Assert
        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN — 이미 11/12/13으로 전이된 협의회는 200 + false (서비스 계약 유지, 4xx 아님)")
    void admin_alreadyTransitioned_returns200False() throws Exception {
        // Arrange: RESULT_REVIEW(10) 상태가 아니면 서비스는 예외 없이 false를 반환한다
        given(resultService.syncReviewStatus(ASCT_ID)).willReturn(false);

        // Act & Assert
        mockMvc.perform(post(SYNC_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자 — 403 차단, 서비스 미호출")
    void user_forbidden_serviceNotInvoked() throws Exception {
        // Act & Assert
        mockMvc.perform(post(SYNC_URL)).andExpect(status().isForbidden());

        // 권한 검증이 서비스 호출 전에 차단했는지 확인
        verify(resultService, never()).syncReviewStatus(anyString());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("미인증 — 401, 서비스 미호출")
    void anonymous_unauthorized_serviceNotInvoked() throws Exception {
        // Act & Assert
        mockMvc.perform(post(SYNC_URL)).andExpect(status().isUnauthorized());

        verify(resultService, never()).syncReviewStatus(anyString());
    }
}
