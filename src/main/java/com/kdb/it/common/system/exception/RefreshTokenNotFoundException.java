package com.kdb.it.common.system.exception;

/**
 * DB에 활성 Refresh Token이 없음을 알리는 마커 예외.
 *
 * <p>{@link com.kdb.it.common.system.service.RefreshTokenRotator#rotate(String)}가 조회값(HMAC-SHA256
 * HEX)으로 저장 행을 찾지 못하면 이 예외를 던진다. 원인은 서버 로그로만 구분하고 토큰 값은 남기지 않는다.
 *
 * <p>오케스트레이터(호출자, {@code AuthService})는 이 타입을 잡아 {@link
 * com.kdb.it.exception.InvalidRefreshTokenException}으로 변환해 재로그인을 유도해야 한다. 폐기할 패밀리 자체가 없으므로 {@link
 * com.kdb.it.common.system.service.RefreshTokenRevoker#revokeByEno(String)}는 호출하지 않는다.
 *
 * <p>패키지가 {@link com.kdb.it.common.system.service.RefreshTokenRotator}와 달라 {@code public}으로 선언한다 —
 * package-private로는 다른 패키지의 Rotator가 이 타입을 생성·전파할 수 없다.
 */
public class RefreshTokenNotFoundException extends RuntimeException {

    /** 고정 메시지("Refresh Token을 찾을 수 없습니다.")로 예외를 생성합니다. */
    public RefreshTokenNotFoundException() {
        super("Refresh Token을 찾을 수 없습니다.");
    }
}
