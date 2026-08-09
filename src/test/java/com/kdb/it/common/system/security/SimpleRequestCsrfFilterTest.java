package com.kdb.it.common.system.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link SimpleRequestCsrfFilter} 단위 테스트
 *
 * <p>CORS 단순 요청(preflight가 발생하지 않는 요청)으로 도달 가능한 변경 요청을 차단하는지 검증합니다. 단순 요청은 Content-Type이 {@code
 * multipart/form-data}·{@code application/x-www-form-urlencoded}·{@code text/plain}인 경우에만 성립하므로, 그
 * 세 종류의 변경 요청에만 커스텀 헤더를 요구합니다.
 */
class SimpleRequestCsrfFilterTest {

    private final SimpleRequestCsrfFilter filter = new SimpleRequestCsrfFilter();

    /** 필터를 1회 실행하고 체인 통과 여부를 반환합니다. */
    private boolean 통과여부(MockHttpServletRequest request, MockHttpServletResponse response)
            throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() != null;
    }

    private MockHttpServletRequest 요청(String method, String uri, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (contentType != null) {
            request.setContentType(contentType);
        }
        return request;
    }

    @Nested
    @DisplayName("단순 요청 Content-Type의 변경 요청")
    class 단순요청차단 {

        @Test
        @DisplayName("multipart 업로드에 커스텀 헤더가 없으면 403으로 차단한다")
        void multipart_헤더없음_차단() throws Exception {
            MockHttpServletRequest request =
                    요청("POST", "/api/files", "multipart/form-data; boundary=----x");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(통과여부(request, response)).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        @DisplayName("form-urlencoded 변경 요청에 커스텀 헤더가 없으면 403으로 차단한다")
        void formUrlencoded_헤더없음_차단() throws Exception {
            MockHttpServletRequest request =
                    요청("POST", "/api/files", "application/x-www-form-urlencoded");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(통과여부(request, response)).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        @DisplayName("text/plain 변경 요청에 커스텀 헤더가 없으면 403으로 차단한다")
        void textPlain_헤더없음_차단() throws Exception {
            MockHttpServletRequest request = 요청("PUT", "/api/files/FL-1", "text/plain");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(통과여부(request, response)).isFalse();
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }

        @Test
        @DisplayName("커스텀 헤더가 있으면 통과시킨다 — 이 헤더는 단순 요청으로 붙일 수 없다")
        void multipart_헤더있음_통과() throws Exception {
            MockHttpServletRequest request =
                    요청("POST", "/api/files", "multipart/form-data; boundary=----x");
            request.addHeader(SimpleRequestCsrfFilter.REQUIRED_HEADER, "XMLHttpRequest");
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThat(통과여부(request, response)).isTrue();
        }
    }

    @Nested
    @DisplayName("차단 대상이 아닌 요청")
    class 통과대상 {

        @Test
        @DisplayName("JSON 변경 요청은 preflight가 이미 보호하므로 통과시킨다")
        void json_통과() throws Exception {
            MockHttpServletRequest request = 요청("POST", "/api/projects", "application/json");
            assertThat(통과여부(request, new MockHttpServletResponse())).isTrue();
        }

        @Test
        @DisplayName("GET 등 안전 메서드는 Content-Type과 무관하게 통과시킨다")
        void 안전메서드_통과() throws Exception {
            MockHttpServletRequest request = 요청("GET", "/api/files", "text/plain");
            assertThat(통과여부(request, new MockHttpServletResponse())).isTrue();
        }

        @Test
        @DisplayName("Content-Type이 없으면 통과시킨다 — consumes 제약이 대신 거부한다")
        void 컨텐츠타입없음_통과() throws Exception {
            MockHttpServletRequest request = 요청("POST", "/api/auth/logout", null);
            assertThat(통과여부(request, new MockHttpServletResponse())).isTrue();
        }

        @Test
        @DisplayName("/sso/** 는 외부 ESSO의 전체 페이지 폼 콜백이므로 제외한다")
        void sso_통과() throws Exception {
            MockHttpServletRequest request =
                    요청("POST", "/sso/logout", "application/x-www-form-urlencoded");
            assertThat(통과여부(request, new MockHttpServletResponse())).isTrue();
        }
    }
}
