package com.kdb.it.common.mfa.provider;

import com.kdb.it.common.mfa.domain.MfaMethod;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 활성 프로파일에 대해 하나만 생성되는 MFA 공급자 레지스트리다. */
public final class MfaProviderRegistry {

    private final Map<MfaMethod, MfaProvider> providers;

    /** Task 2의 프로파일별 단일 빈 계약을 유지하는 빈 레지스트리를 생성한다. */
    public MfaProviderRegistry() {
        this(Map.of());
    }

    /**
     * 인증수단별 공급자 맵으로 레지스트리를 생성한다.
     *
     * @param providers 활성 프로파일에서 사용할 공급자
     */
    public MfaProviderRegistry(Map<MfaMethod, MfaProvider> providers) {
        EnumMap<MfaMethod, MfaProvider> copy = new EnumMap<>(MfaMethod.class);
        copy.putAll(providers);
        this.providers = Map.copyOf(copy);
    }

    /**
     * 선택한 인증수단의 challenge를 시작한다.
     *
     * @param method 사용자가 선택한 인증수단
     * @param context 서버가 소유한 MFA 거래 정보
     * @return 안전한 challenge 표시 정보
     * @throws IllegalArgumentException 활성화되지 않은 인증수단인 경우
     */
    public MfaChallengeData start(MfaMethod method, MfaStartContext context) {
        return provider(method).start(context);
    }

    /**
     * 선택한 인증수단의 명시적인 검증 요청을 처리한다.
     *
     * @param method 사용자가 선택한 인증수단
     * @param context challenge와 사용자가 제출한 검증 값
     * @return 검증 성공 여부
     * @throws IllegalArgumentException 활성화되지 않은 인증수단인 경우
     */
    public MfaVerificationResult verify(MfaMethod method, MfaVerifyContext context) {
        return provider(method).verify(context);
    }

    private MfaProvider provider(MfaMethod method) {
        Objects.requireNonNull(method, "MFA 인증수단은 필수입니다.");
        MfaProvider provider = providers.get(method);
        if (provider == null) {
            throw new IllegalArgumentException("활성화되지 않은 MFA 인증수단입니다.");
        }
        return provider;
    }
}
