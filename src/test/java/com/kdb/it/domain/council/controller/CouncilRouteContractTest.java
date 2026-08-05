package com.kdb.it.domain.council.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.system.security.JwtUtil;
import com.kdb.it.common.system.service.CustomUserDetailsService;
import com.kdb.it.config.TestSecurityConfig;
import com.kdb.it.domain.council.service.CommitteeService;
import com.kdb.it.domain.council.service.CouncilApprovalService;
import com.kdb.it.domain.council.service.CouncilService;
import com.kdb.it.domain.council.service.CouncilSkipService;
import com.kdb.it.domain.council.service.EvaluationService;
import com.kdb.it.domain.council.service.FeasibilityService;
import com.kdb.it.domain.council.service.PlanEvaluationService;
import com.kdb.it.domain.council.service.ResultService;
import com.kdb.it.domain.council.service.ScheduleService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@code /api/council} 라우트 계약 characterization 테스트 — CQ-01 컨트롤러 분해 안전망.
 *
 * <p>컨트롤러를 7개로 분해하는 동안 URL이 하나도 바뀌지 않았음을 기계적으로 증명합니다. 분해 시 이 파일에서 바꿔도 되는 것은 {@code @WebMvcTest}의
 * 컨트롤러 목록과 {@code @MockitoBean} 목록뿐이며, {@link #EXPECTED_ROUTES}는 한 글자도 바꾸지 않습니다.
 *
 * <p>슬라이스에 등재된 컨트롤러의 매핑만 관측하므로 QnA 컨트롤러({@code /api/council/{asctId}/qna}, {@code /main-qna})의 라우트는
 * 이 목록에 포함되지 않습니다.
 */
@WebMvcTest(CouncilController.class)
@Import(TestSecurityConfig.class)
class CouncilRouteContractTest {

    /** 분해 전 실측한 42개 라우트. 사전순 정렬된 {@code "METHOD /path"} 문자열. */
    private static final List<String> EXPECTED_ROUTES =
            List.of(
                    "GET /api/council",
                    "GET /api/council/skip-requests",
                    "GET /api/council/{asctId}",
                    "GET /api/council/{asctId}/committee",
                    "GET /api/council/{asctId}/committee/default",
                    "GET /api/council/{asctId}/evaluation",
                    "GET /api/council/{asctId}/evaluation/my",
                    "GET /api/council/{asctId}/feasibility",
                    "GET /api/council/{asctId}/plan-evaluation",
                    "GET /api/council/{asctId}/plan-evaluation/my",
                    "GET /api/council/{asctId}/plan-evaluation/result-summary",
                    "GET /api/council/{asctId}/plan-targets",
                    "GET /api/council/{asctId}/result",
                    "GET /api/council/{asctId}/result/review/my",
                    "GET /api/council/{asctId}/schedule",
                    "GET /api/council/{asctId}/schedule/my",
                    "GET /api/council/{asctId}/skip-request",
                    "PATCH /api/council/{asctId}/approval",
                    "PATCH /api/council/{asctId}/complete",
                    "PATCH /api/council/{asctId}/skip",
                    "PATCH /api/council/{asctId}/start",
                    "PATCH /api/council/{asctId}/start-preparation",
                    "POST /api/council",
                    "POST /api/council/{asctId}/approval",
                    "POST /api/council/{asctId}/committee",
                    "POST /api/council/{asctId}/evaluation",
                    "POST /api/council/{asctId}/feasibility",
                    "POST /api/council/{asctId}/notify",
                    "POST /api/council/{asctId}/plan-evaluation",
                    "POST /api/council/{asctId}/result",
                    "POST /api/council/{asctId}/result/approval",
                    "POST /api/council/{asctId}/result/review",
                    "POST /api/council/{asctId}/result/review/sync",
                    "POST /api/council/{asctId}/schedule",
                    "POST /api/council/{asctId}/skip-request",
                    "POST /api/council/{asctId}/skip-request/decision",
                    "PUT /api/council/{asctId}/committee",
                    "PUT /api/council/{asctId}/feasibility",
                    "PUT /api/council/{asctId}/result",
                    "PUT /api/council/{asctId}/result/confirm",
                    "PUT /api/council/{asctId}/schedule/confirm",
                    "PUT /api/council/{asctId}/schedule/confirm-written");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean private CouncilService councilService;
    @MockitoBean private FeasibilityService feasibilityService;
    @MockitoBean private CouncilApprovalService councilApprovalService;
    @MockitoBean private CommitteeService committeeService;
    @MockitoBean private ScheduleService scheduleService;
    @MockitoBean private EvaluationService evaluationService;
    @MockitoBean private ResultService resultService;
    @MockitoBean private CouncilSkipService councilSkipService;
    @MockitoBean private PlanEvaluationService planEvaluationService;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("/api/council 라우트 42개가 정확히 일치한다 — 개수·경로·HTTP 메서드·중복 없음")
    void 라우트_계약이_유지된다() {
        assertThat(actualRoutes())
                .withFailMessage(
                        """
                        /api/council 라우트 계약이 깨졌습니다.

                        기대(%d개): %s

                        실제(%d개): %s

                        분해 작업이라면 라우트를 옮기다 누락·오타가 났거나 매핑이 중복됐습니다.
                        EXPECTED_ROUTES를 고치지 말고 컨트롤러를 고치십시오.
                        """
                                .formatted(
                                        EXPECTED_ROUTES.size(),
                                        EXPECTED_ROUTES,
                                        actualRoutes().size(),
                                        actualRoutes()))
                .isEqualTo(EXPECTED_ROUTES);
    }

    /**
     * 슬라이스에 등재된 컨트롤러에서 {@code /api/council}로 시작하는 (HTTP 메서드, 경로) 쌍을 모두 모아 사전순으로 정렬한다.
     *
     * <p>집합이 아니라 정렬된 리스트로 비교하므로 중복 매핑도 검출됩니다.
     */
    private List<String> actualRoutes() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(CouncilRouteContractTest::toRouteStrings)
                .sorted()
                .toList();
    }

    private static java.util.stream.Stream<String> toRouteStrings(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() == null) {
            throw new IllegalStateException("PathPatterns 기반 매핑이 아닙니다. Spring 설정을 확인하십시오: " + info);
        }
        return info.getPathPatternsCondition().getPatternValues().stream()
                .filter(pattern -> pattern.startsWith("/api/council"))
                .flatMap(
                        pattern ->
                                info.getMethodsCondition().getMethods().stream()
                                        .map(method -> method.name() + " " + pattern));
    }
}
