package com.kdb.it.exception;

/**
 * 예상된 로그인 거부(사용자 미존재, 비밀번호 불일치)를 나타내는 전용 예외.
 *
 * <p>{@code LoginAttemptService#checkLocked}는 커밋된 {@code CLOGNH} 실패 이력 건수로 잠금 여부를 판단합니다. 로그인 실패 시
 * {@code AuthService#login}이 실패 이력을 저장한 직후 이 예외를 던지면, {@code @Transactional(noRollbackFor =
 * LoginRejectedException.class)} 선언에 의해 트랜잭션이 롤백되지 않고 커밋되어 방금 저장한 실패 이력이 살아남습니다.
 *
 * <p>이 예외는 "예상된 로그인 거부"만을 나타냅니다. 잠금 예외({@code CustomGeneralException}), 이력 저장 중 발생한 DB 오류, 토큰 발급 등
 * 성공 경로 이후 발생하는 예기치 못한 오류는 이 타입으로 변환하지 않고 원래 예외 그대로 전파되어 트랜잭션이 정상적으로 롤백됩니다.
 */
public class LoginRejectedException extends RuntimeException {

    /**
     * 클라이언트 노출용 메시지로 예외를 생성합니다.
     *
     * @param message 로그인 거부 사유 메시지 (기존 HTTP 계약과 동일한 문구를 유지)
     */
    public LoginRejectedException(String message) {
        super(message);
    }
}
