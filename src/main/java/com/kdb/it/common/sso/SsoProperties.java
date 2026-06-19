package com.kdb.it.common.sso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KDB ESSO(ISign+) Web Agent 연동 설정.
 *
 * <p>JSP Web Agent(별첨1 SA-WEB)의 {@code agentInfo.jsp}/{@code config.properties} 값을
 * Spring 설정으로 옮긴 것입니다. {@code sso.*} 프리픽스로 바인딩됩니다.</p>
 *
 * <p>망 분리: PC(브라우저) → ESSO 통신용 URL({@code browserBaseUrl})과 업무서버(AP) → ESSO
 * 통신용 URL({@code hostBaseUrl})이 다를 수 있어 분리합니다(인터넷망/스마트워크망 구성 대응).</p>
 *
 * @param mockEnabled    모의 모드 여부. true이면 ESSO 통신 없이 {@code mockEno}로 로그인합니다
 *                       (SSO와 통신 불가한 외부망 개발용). 운영 프로파일은 반드시 false.
 * @param mockEno        모의 모드에서 사용할 사번. 기본값 {@code K140024}.
 * @param browserBaseUrl PC(브라우저) → ESSO 기준 URL (예: {@code https://dintesso.kdb.co.kr:20443}).
 * @param hostBaseUrl    업무서버(AP) → ESSO 기준 URL. 망 분리 시 browserBaseUrl과 다를 수 있음.
 * @param agentId        업무 시스템 고유 번호(SSO 관리자 발급).
 * @param requestData    인증 결과에서 추출할 사용자 데이터 키. 기본값 {@code id}(사번). 복수 키는 쉼표 구분.
 * @param connectTimeout ESSO 통신 연결 타임아웃(ms). 기본 5000.
 * @param readTimeout    ESSO 통신 읽기 타임아웃(ms). 기본 5000.
 */
@ConfigurationProperties(prefix = "sso")
public record SsoProperties(
        boolean mockEnabled,
        String mockEno,
        String browserBaseUrl,
        String hostBaseUrl,
        String agentId,
        String requestData,
        int connectTimeout,
        int readTimeout
) {

    /**
     * 누락값에 안전한 기본을 채웁니다. URL/식별자는 빈 문자열로 정규화해 NPE를 방지합니다.
     */
    public SsoProperties {
        if (mockEno == null || mockEno.isBlank()) mockEno = "K140024";
        if (requestData == null || requestData.isBlank()) requestData = "id";
        if (connectTimeout <= 0) connectTimeout = 5000;
        if (readTimeout <= 0) readTimeout = 5000;
        if (browserBaseUrl == null) browserBaseUrl = "";
        if (hostBaseUrl == null) hostBaseUrl = "";
        if (agentId == null) agentId = "";
    }

    /** 인증서버 통신 점검 URL (AP → ESSO). */
    public String checkServerUrl() {
        return hostBaseUrl + "/openapi/checkserver";
    }

    /** 토큰 검증 및 사용자 정보 요청 URL (AP → ESSO). */
    public String tokenAuthorizationUrl() {
        return hostBaseUrl + "/token/authorization";
    }

    /** ESSO 로그인 페이지 (PC → ESSO). */
    public String loginPageUrl() {
        return browserBaseUrl + "/login.html";
    }

    /** ESSO 통합 로그아웃 페이지 (PC → ESSO). */
    public String logoutPageUrl() {
        return browserBaseUrl + "/logout.html";
    }

    /** CS 모드 토큰 저장 페이지 (PC → ESSO). */
    public String saveTokenUrl() {
        return browserBaseUrl + "/token/saveToken.html";
    }
}
