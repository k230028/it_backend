package com.kdb.it.common.system.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CORS 단순 요청으로 도달 가능한 변경 요청을 차단하는 CSRF 보완 필터
 *
 * <p>이 애플리케이션은 Stateless JWT를 httpOnly 쿠키로 실어 보내며 CSRF 토큰을 사용하지 않습니다. 교차 사이트 요청은 쿠키의 {@code
 * SameSite=Lax}가 막고, 동일 사이트의 다른 오리진(형제 서브도메인 등)은 JSON 본문이 유발하는 preflight와 CORS 허용 목록이 막는 이중 구조입니다.
 *
 * <p>문제는 두 번째 층이 <b>"모든 변경 API가 JSON 본문을 쓴다"</b>는 전제에 의존한다는 점입니다. {@code
 * multipart/form-data}·{@code application/x-www-form-urlencoded}·{@code text/plain}은 CORS 안전 목록
 * Content-Type이라 preflight가 발생하지 않고, 따라서 브라우저가 요청 자체를 보내는 것을 CORS가 막지 못합니다. 파일 업로드처럼 multipart를 받는
 * 엔드포인트는 이 경로로 동일 사이트 다른 오리진에서 호출될 수 있습니다.
 *
 * <p>그래서 위 세 Content-Type의 변경 요청에만 {@link #REQUIRED_HEADER}를 요구합니다. 단순 요청은 커스텀 헤더를 붙일 수 없으므로 이 헤더가
 * 있다는 사실 자체가 preflight를 거쳤다는 뜻이고, preflight가 발생하면 기존 CORS 허용 목록이 다시 작동합니다. 값은 검사하지 않고 존재 여부만 확인합니다
 * — 값을 맞추는 것은 방어에 기여하지 않고 클라이언트만 제약합니다.
 *
 * <p>적용 범위를 좁게 둔 이유:
 *
 * <ul>
 *   <li>JSON 변경 요청은 이미 preflight 대상이므로 건드리지 않습니다.
 *   <li>Content-Type이 없는 요청은 통과시킵니다. 변경 엔드포인트는 {@code consumes}로 본문 형식을 못박고 있어 형식이 맞지 않으면 415로
 *       거부되므로, 이 필터가 중복해서 막을 필요가 없습니다.
 *   <li>{@code /sso/**}는 외부 ESSO가 전체 페이지 폼으로 POST하는 콜백 경로라 커스텀 헤더를 붙일 수 없어 제외합니다. 이 경로는 {@code
 *       allowCredentials=false}이고 SSO 검증 상태를 별도 세션으로 분리해 관리합니다.
 * </ul>
 *
 * @see com.kdb.it.config.SecurityConfig
 */
@Component
public class SimpleRequestCsrfFilter extends OncePerRequestFilter {

    /** 요구하는 커스텀 헤더. 단순 요청으로는 붙일 수 없어 preflight를 강제하는 역할만 한다. */
    public static final String REQUIRED_HEADER = "X-Requested-With";

    /** CORS 안전 목록 Content-Type — 이 세 종류만 preflight 없이 전송된다. */
    private static final Set<String> SIMPLE_CONTENT_TYPES =
            Set.of("multipart/form-data", "application/x-www-form-urlencoded", "text/plain");

    /** 본문을 수반하지 않는 안전 메서드. 상태를 바꾸지 않으므로 검사 대상이 아니다. */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /** 외부 ESSO 폼 콜백 경로 — 커스텀 헤더를 붙일 수 없어 제외한다. */
    private static final String SSO_PATH_PREFIX = "/sso/";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (검사대상이_아님(request) || request.getHeader(REQUIRED_HEADER) != null) {
            filterChain.doFilter(request, response);
            return;
        }

        response.sendError(
                HttpServletResponse.SC_FORBIDDEN,
                "Missing " + REQUIRED_HEADER + " header for simple-request content type");
    }

    /**
     * 이 요청이 CSRF 검사 대상에서 벗어나는지 판정합니다.
     *
     * @param request 검사할 요청
     * @return 안전 메서드, {@code /sso/**} 경로, 또는 단순 요청 Content-Type이 아닌 경우 {@code true}
     */
    private boolean 검사대상이_아님(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        if (request.getRequestURI().startsWith(SSO_PATH_PREFIX)) {
            return true;
        }
        return !단순요청_컨텐츠타입(request.getContentType());
    }

    /**
     * Content-Type이 CORS 안전 목록에 속하는지 판정합니다.
     *
     * @param contentType 원본 Content-Type 헤더 (파라미터 포함 가능, null 허용)
     * @return 안전 목록이면 {@code true}. null·빈 값은 {@code false}
     */
    private boolean 단순요청_컨텐츠타입(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        // "multipart/form-data; boundary=----x" 처럼 파라미터가 붙으므로 미디어 타입만 잘라 비교한다.
        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return SIMPLE_CONTENT_TYPES.contains(mediaType);
    }
}
