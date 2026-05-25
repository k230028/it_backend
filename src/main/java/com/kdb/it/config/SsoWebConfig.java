package com.kdb.it.config;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * SSO JSP 실행을 위한 내장 Tomcat 설정
 *
 * <p>bootRun과 IDE 실행 시 working directory가 달라 JSP 파일 탐색 경로가 다릅니다.</p>
 * <ul>
 *   <li>bootRun: working dir = {@code it_backend/} → {@code src/main/webapp}</li>
 *   <li>IDE devlocal: working dir = {@code it/} (repo root) → {@code it_backend/src/main/webapp}</li>
 * </ul>
 * <p>두 경로를 순서대로 탐색해 존재하는 디렉토리를 document root로 설정합니다.</p>
 */
@Configuration
public class SsoWebConfig {

    /**
     * 내장 Tomcat의 document root를 실행 환경에 맞게 설정합니다.
     *
     * <p>실행 환경별 working directory 차이로 JSP 탐색 경로가 달라지는 문제를 처리합니다.
     * 후보 경로를 순서대로 탐색하여 존재하는 첫 번째 디렉토리를 document root로 설정합니다.</p>
     *
     * <ul>
     *   <li>bootRun: working dir = {@code it_backend/} → {@code src/main/webapp} 사용</li>
     *   <li>IDE(devlocal): working dir = {@code it/} (repo root) → {@code it_backend/src/main/webapp} 사용</li>
     * </ul>
     *
     * <p>후보 경로 중 존재하는 디렉토리가 없으면 document root를 설정하지 않습니다
     * (Tomcat 기본값 사용).</p>
     *
     * @return 서블릿 웹서버 팩토리 커스터마이저 빈
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webappDocumentRoot() {
        return factory -> {
            for (String candidate : new String[]{"src/main/webapp", "it_backend/src/main/webapp"}) {
                File dir = new File(candidate);
                if (dir.exists() && dir.isDirectory()) {
                    factory.setDocumentRoot(dir);
                    return;
                }
            }
        };
    }
}
