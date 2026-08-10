package com.kdb.it.common.mfa.config;

import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** 지원 프로파일별 MFA 공급자 레지스트리를 안전하게 조립한다. */
@Configuration
@EnableConfigurationProperties(MfaProperties.class)
public class MfaConfig {

    /**
     * 현재 프로파일에 맞는 단일 MFA 공급자 레지스트리를 생성한다.
     *
     * @param properties MFA 통신 및 거래 제한 설정
     * @param environment 활성 Spring 프로파일
     * @return 현재 프로파일에 사용할 MFA 공급자 레지스트리
     * @throws IllegalStateException local-ext 외 프로파일에서 mock MFA가 활성화되었거나 실제 연동 endpoint가 없을 때
     */
    @Bean
    @Profile({"local-ext", "local-int", "dev", "prod"})
    public MfaProviderRegistry mfaProviderRegistry(MfaProperties properties, Environment environment) {
        if (properties.mockEnabled() && !isOnlyLocalExtProfile(environment)) {
            throw new IllegalStateException("MFA 보안 위반: app.mfa.mock-enabled는 local-ext에서만 허용됩니다.");
        }
        if (!properties.mockEnabled() && properties.endpoint().isBlank()) {
            throw new IllegalStateException("app.mfa.endpoint is required when mock MFA is disabled");
        }
        return new MfaProviderRegistry();
    }

    private boolean isOnlyLocalExtProfile(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && "local-ext".equalsIgnoreCase(activeProfiles[0]);
    }
}
