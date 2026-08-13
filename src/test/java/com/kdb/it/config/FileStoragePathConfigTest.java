package com.kdb.it.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

class FileStoragePathConfigTest {

    @Test
    @DisplayName("공통·개발·운영 프로파일의 첨부 기본 저장 경로는 /dat/springitp이다")
    void attachmentBasePath_usesSharedServerPath() throws IOException {
        assertThat(properties("application.properties").getProperty("app.file.base-path"))
                .isEqualTo("/dat/springitp");
        assertThat(properties("application-dev.properties").getProperty("app.file.base-path"))
                .isEqualTo("${FILE_BASE_PATH:/dat/springitp}");
        assertThat(properties("application-prod.properties").getProperty("app.file.base-path"))
                .isEqualTo("${FILE_BASE_PATH:/dat/springitp}");
    }

    private ResourcePropertySource properties(String name) throws IOException {
        return new ResourcePropertySource(new ClassPathResource(name));
    }
}
