package com.kdb.it.common.system.exception;

/**
 * Refresh Token 회전 직후 grace 기간 내 동시 재제출(다중 탭 동시 새로고침) 감지 마커 예외.
 *
 * <p>회전된(AVL_YN='N') 토큰이 회전 직후 grace 기간 안에 다시 제출되면 다중 탭에서 동시에 새로고침한 것으로 간주한다. 이 경우 토큰 패밀리는 폐기하지 않고
 * 이번 요청만 거부한다(공유 쿠키의 신규 토큰으로 사용자는 유지됨). 클라이언트는 잠시 후 재시도하면 된다.
 *
 * <p><b>롤백 보장</b>: {@link RuntimeException}(unchecked)이므로 {@link
 * com.kdb.it.common.system.service.RefreshTokenRotator#rotate(String)}의 {@code @Transactional}
 * 메서드에서 이 예외가 발생하면 Spring 기본 정책에 따라 별도 {@code rollbackFor} 지정 없이 트랜잭션이 롤백되고, 비관적 쓰기
 * 잠금(PESSIMISTIC_WRITE)이 즉시 해제된다.
 *
 * <p>패키지가 {@link com.kdb.it.common.system.service.RefreshTokenRotator}와 달라 {@code public}으로 선언한다 —
 * package-private로는 다른 패키지의 Rotator가 이 타입을 생성·전파할 수 없다.
 */
public class ConcurrentRefreshException extends RuntimeException {

    /**
     * 재시도 안내 메시지로 예외를 생성합니다.
     *
     * @param message 클라이언트/로그에 노출 가능한 재시도 안내 메시지
     */
    public ConcurrentRefreshException(String message) {
        super(message);
    }
}
