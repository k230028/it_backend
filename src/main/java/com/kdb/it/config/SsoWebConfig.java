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
