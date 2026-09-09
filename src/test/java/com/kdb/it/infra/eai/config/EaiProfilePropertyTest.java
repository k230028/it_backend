package com.kdb.it.infra.eai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * 실제 프로파일 설정 파일의 EAI 시스템환경구분코드(SYS_ENV_TC) 정책을 검증한다.
 *
 * <p>운영 EAI 게이트웨이는 {@code SYS_ENV_TC='L'} 전문을 "시스템환경구분코드가 잘못 들어왔습니다"로 거부한다. 베이스 설정의 고정값 {@code L}이
 * 운영 프로파일에서 그대로 새어 나가지 않도록 prod 파일이 {@code P}를 기본값으로 재정의하는지 확인한다.
 */
class EaiProfilePropertyTest {

    @Test
    @DisplayName("prod는 EAI 시스템환경구분코드를 P로 재정의하고 환경변수로만 바꿀 수 있다")
    void prod_overridesSysEnvTcToProduction() throws IOException {
        ResourcePropertySource prod = properties("application-prod.properties");

        assertThat(prod.getProperty("eai.sys-env-tc")).isEqualTo("${EAI_SYS_ENV_TC:P}");
    }

    @Test
    @DisplayName("베이스 설정은 L을 기본값으로 두되 같은 환경변수로 재정의할 수 있다")
    void base_defaultsToLocalButAllowsEnvOverride() throws IOException {
        ResourcePropertySource base = properties("application.properties");

        assertThat(base.getProperty("eai.sys-env-tc")).isEqualTo("${EAI_SYS_ENV_TC:L}");
    }

    @Test
    @DisplayName("개발·로컬 프로파일은 운영 코드 P를 설정하지 않는다")
    void nonProdProfiles_doNotSetProductionCode() throws IOException {
        for (String fileName :
                new String[] {
                    "application-dev.properties",
                    "application-local-ext.properties",
                    "application-local-int.properties"
                }) {
            ResourcePropertySource profile = properties(fileName);
            assertThat(profile.getProperty("eai.sys-env-tc"))
                    .as(fileName)
                    .satisfiesAnyOf(
                            value -> assertThat(value).isNull(),
                            value -> assertThat(value).isNotEqualTo("P"));
        }
    }

    private ResourcePropertySource properties(String fileName) throws IOException {
        return new ResourcePropertySource(fileName, new ClassPathResource(fileName));
    }
}
