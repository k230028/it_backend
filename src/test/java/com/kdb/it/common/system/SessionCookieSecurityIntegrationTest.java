package com.kdb.it.common.system;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 프로파일의 실제 embedded servlet 세션 쿠키 보안 속성을 검증합니다. */
@SpringBootTest(
        classes = SessionCookieSecurityIntegrationTest.SessionCookieTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration,"
                    + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "spring.datasource.password=test-db-password",
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok",
            "gemini.api.key=test-gemini-key",
            "eai.enabled=false"
        })
@ActiveProfiles("prod")
class SessionCookieSecurityIntegrationTest {

    @LocalServerPort private int port;

    @Test
    @DisplayName("HTTP backend 응답의 JSESSIONID에도 Secure·HttpOnly·SameSite=Lax가 설정된다")
    void sessionCookie_overHttp_hasProductionSecurityAttributes() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/session-cookie-probe"))
                        .GET()
                        .build();

        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionCookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(value -> value.startsWith("JSESSIONID="))
                        .findFirst()
                        .orElseThrow();
        assertThat(sessionCookie)
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({EnvironmentValidator.class, SessionCookieProbeController.class})
    static class SessionCookieTestApplication {}

    @RestController
    static class SessionCookieProbeController {

        @GetMapping("/session-cookie-probe")
        String createSession(HttpServletRequest request) {
            request.getSession(true).setAttribute("sso-result", "verified");
            return "ok";
        }
    }
}
