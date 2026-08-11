package com.kdb.it.support;

import com.kdb.it.common.mfa.provider.MfaProviderRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@code test-it} 등 {@code MfaConfig}의 {@code @Profile}({@code local-ext}·{@code local-int}·{@code
 * dev}·{@code prod})에 속하지 않는 프로파일에서 전체 스프링 컨텍스트를 올리는 {@code @SpringBootTest} 기반 통합 테스트를 위한 빈 MFA
 * 공급자 레지스트리입니다.
 *
 * <p>{@code MfaConfig.mfaProviderRegistry}는 위 네 프로파일에서만 생성되므로, {@code test-it} 프로파일로 전체 컨텍스트를 올리는
 * 테스트는 {@code AuthService}·{@code MfaController} 등이 요구하는 {@link MfaProviderRegistry} 빈을 찾지 못해 컨텍스트
 * 로딩 자체가 실패합니다({@code NoSuchBeanDefinitionException}). {@link
 * MfaProviderRegistry#MfaProviderRegistry()}가 "프로파일별 단일 빈 계약을 유지하는 빈 레지스트리"로 이미 준비돼 있으므로, 이 지원 설정은
 * 그 빈 레지스트리를 실제 MFA 연동이 필요 없는 테스트 컨텍스트에 공급합니다. MFA 흐름 자체를 검증하는 테스트({@code MfaConfigurationTest} 등)는
 * 이 클래스를 쓰지 않습니다.
 *
 * <p>{@code @Import(MfaTestSupportConfig.class)}로 필요한 테스트에서만 선택적으로 가져다 씁니다.
 */
@TestConfiguration
public class MfaTestSupportConfig {

    /**
     * 활성 프로파일에서 실 {@code MfaProviderRegistry} 빈이 만들어지지 않았을 때만 빈 레지스트리를 보충합니다.
     *
     * @return 등록된 공급자가 없는 {@link MfaProviderRegistry}
     */
    @Bean
    @ConditionalOnMissingBean(MfaProviderRegistry.class)
    public MfaProviderRegistry mfaProviderRegistry() {
        return new MfaProviderRegistry();
    }
}
