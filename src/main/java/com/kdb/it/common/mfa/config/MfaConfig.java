package com.kdb.it.common.mfa.config;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.provider.FidoMfaProvider;
import com.kdb.it.common.mfa.provider.FingerVeinMfaProvider;
import com.kdb.it.common.mfa.provider.MfaProvider;
import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import com.kdb.it.common.mfa.provider.MockMfaProvider;
import com.kdb.it.common.mfa.provider.MotpMfaProvider;
import com.kdb.it.common.mfa.provider.OnePassClient;
import java.util.Map;
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
     * @param clock 지정맥 해시의 년월일을 만들 서버 시계
     * @return 현재 프로파일에 사용할 MFA 공급자 레지스트리
     * @throws IllegalStateException local-ext 외 프로파일에서 mock MFA가 활성화되었거나 실제 연동 endpoint 또는 지정맥 고정키가
     *     없을 때
     */
    @Bean
    @Profile({"local-ext", "local-int", "dev", "prod"})
    public MfaProviderRegistry mfaProviderRegistry(
            MfaProperties properties, Environment environment, java.time.Clock clock) {
        if (properties.mockEnabled() && !isOnlyLocalExtProfile(environment)) {
            throw new IllegalStateException("MFA 보안 위반: app.mfa.mock-enabled는 local-ext에서만 허용됩니다.");
        }
        if (!properties.mockEnabled() && properties.endpoint().isBlank()) {
            throw new IllegalStateException(
                    "app.mfa.endpoint is required when mock MFA is disabled");
        }
        if (!properties.mockEnabled() && properties.fingerVeinFixedKey().isBlank()) {
            throw new IllegalStateException(
                    "app.mfa.finger-vein-fixed-key is required when mock MFA is disabled");
        }
        if (properties.mockEnabled()) {
            MfaProvider mockProvider = new MockMfaProvider();
            return new MfaProviderRegistry(
                    Map.of(
                            MfaMethod.FINGER_VEIN, mockProvider,
                            MfaMethod.FIDO, mockProvider,
                            MfaMethod.MOTP, mockProvider));
        }
        OnePassClient onePassClient = new OnePassClient(properties);
        return new MfaProviderRegistry(
                Map.of(
                        MfaMethod.FINGER_VEIN,
                        new FingerVeinMfaProvider(properties.fingerVeinFixedKey(), clock),
                        MfaMethod.FIDO,
                        new FidoMfaProvider(onePassClient),
                        MfaMethod.MOTP,
                        new MotpMfaProvider(onePassClient)));
    }

    private boolean isOnlyLocalExtProfile(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && "local-ext".equalsIgnoreCase(activeProfiles[0]);
    }
}
