package com.kdb.it.common.mfa.provider;

/** MFA 수단별 외부 인증 시작과 검증을 수행하는 공급자 계약이다. */
public interface MfaProvider {

    /**
     * 외부 인증을 시작하고 화면에 표시 가능한 안전한 challenge 정보만 반환한다.
     *
     * @param context MFA 거래의 소유자, 목적 및 만료 시각
     * @return challenge 식별자, 선택 QR 데이터 및 만료 시각
     * @throws IllegalStateException 외부 공급자 통신 또는 응답이 유효하지 않은 경우
     */
    MfaChallengeData start(MfaStartContext context);

    /**
     * 현재 challenge의 명시적인 사용자 검증 요청을 외부 공급자에 전달한다.
     *
     * @param context MFA 거래의 소유자, 목적 및 만료 시각
     * @return 성공 여부만 포함한 검증 결과
     */
    MfaVerificationResult verify(MfaVerifyContext context);
}
