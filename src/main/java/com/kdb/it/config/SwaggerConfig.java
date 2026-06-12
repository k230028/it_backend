package com.kdb.it.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger(OpenAPI 3.0) 문서화 설정 클래스
 *
 * <p>
 * springdoc-openapi 라이브러리를 사용하여 REST API 명세서를 자동으로 생성합니다.
 * </p>
 *
 * <p>
 * Swagger UI 접근 URL:
 * </p>
 * <ul>
 * <li>Swagger UI: {@code http://localhost:28080/swagger-ui/index.html}</li>
 * <li>API 명세 JSON: {@code http://localhost:28080/v3/api-docs}</li>
 * </ul>
 *
 * <p>
 * 인증 없이 접근 가능 (SecurityConfig에서 permitAll 처리됨)
 * </p>
 */
@Configuration // Spring 설정 클래스로 등록
public class SwaggerConfig {

    /**
     * OpenAPI 명세서 메타데이터 및 전역 보안 설정 빈 등록
     *
     * <p>
     * Swagger UI 상단에 표시되는 API 제목, 설명, 버전 정보를 설정하고,
     * JWT 토큰(Bearer Auth)을 전역적으로 사용할 수 있도록 Security 설정을 추가합니다.
     * </p>
     *
     * @return {@link OpenAPI} 인스턴스 (메타데이터 및 인증 설정 포함)
     */
    @Bean
    public OpenAPI openAPI() {
        String jwtSchemeName = "BearerAuth";

        // 전역 보안 요구사항 설정 (모든 API 요청에 jwtSchemeName 적용)
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        // 보안 스키마 정의 (HTTP Bearer 토큰 방식)
        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("발급받은 Access Token을 입력하세요. (Bearer는 생략)"));

        return new OpenAPI()
                .info(new Info()
                        .title("IT Portal API") // Swagger UI 제목
                        .description("IT Portal 프로젝트 API 명세서") // Swagger UI 설명
                        .version("1.0.0")) // API 버전
                .addSecurityItem(securityRequirement) // 모든 경로에 보안 요구사항 적용
                .components(components); // 보안 스키마 컴포넌트에 등록
    }
}
