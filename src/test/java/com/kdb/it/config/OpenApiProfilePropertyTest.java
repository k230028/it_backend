package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/** 실제 프로파일 설정 파일의 OpenAPI 노출 정책을 검증한다. */
class OpenApiProfilePropertyTest {

    @Test
    @DisplayName("prod는 OpenAPI 명세와 Swagger UI를 모두 비활성화한다")
    void prod_disablesOpenApiDocsAndSwaggerUi() throws IOException {
        ResourcePropertySource prod = properties("application-prod.properties");

        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prod.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("local과 dev는 springdoc 기본 활성값을 사용한다")
    void localAndDev_useSpringdocDefaultEnabledValue() throws IOException {
        ResourcePropertySource localExt = properties("application-local-ext.properties");
        ResourcePropertySource localInt = properties("application-local-int.properties");
        ResourcePropertySource dev = properties("application-dev.properties");

        assertThat(localExt.getProperty("springdoc.api-docs.enabled")).isNull();
        assertThat(localInt.getProperty("springdoc.api-docs.enabled")).isNull();
        assertThat(dev.getProperty("springdoc.api-docs.enabled")).isNull();
    }

    private ResourcePropertySource properties(String fileName) throws IOException {
        return new ResourcePropertySource(fileName, new ClassPathResource(fileName));
    }
}
