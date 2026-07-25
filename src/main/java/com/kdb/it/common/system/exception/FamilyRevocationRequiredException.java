package com.kdb.it.common.system.exception;

/**
 * Refresh Token 패밀리 폐기가 필요함을 알리는 마커 예외 (재사용 탐지 또는 만료).
 *
 * <p>{@link com.kdb.it.common.system.service.RefreshTokenRotator#rotate(String)}는 grace 기간을 벗어난 회전
 * 토큰 재제출(탈취 의심 재사용) 또는 만료된 토큰을 감지하면, 같은 트랜잭션 안에서 직접 삭제하지 않고 이 예외만 던진다. 삭제는 별도 {@code REQUIRES_NEW}
 * 트랜잭션인 {@link com.kdb.it.common.system.service.RefreshTokenRevoker#revokeByEno(String)}가, 이 트랜잭션이
 * 종료되어 비관적 쓰기 잠금이 풀린 뒤 수행한다({@link #getEno()}로 대상 사용자를 식별).
 *
 * <p><b>롤백 보장</b>: {@link RuntimeException}(unchecked)이므로 rotate()의 {@code @Transactional} 메서드에서 이
 * 예외가 발생하면 Spring 기본 정책에 따라 별도 {@code rollbackFor} 지정 없이 트랜잭션이 롤백된다.
 *
 * <p>패키지가 {@link com.kdb.it.common.system.service.RefreshTokenRotator}와 달라 {@code public}으로 선언한다 —
 * package-private로는 다른 패키지의 Rotator가 이 타입을 생성·전파할 수 없다.
 */
public class FamilyRevocationRequiredException extends RuntimeException {

    /** 패밀리 폐기가 필요해진 사유. */
    public enum Reason {
        /** grace 기간을 벗어난 회전 토큰 재제출 — 탈취 재사용 의심 */
        REUSED,
        /** DB 저장 만료일(END_DTM) 경과 */
        EXPIRED
    }

    private final Reason reason;
    private final String eno;

    /**
     * 폐기 사유와 대상 사용자 사번으로 예외를 생성합니다.
     *
     * @param reason 폐기 사유 ({@link Reason#REUSED} 또는 {@link Reason#EXPIRED})
     * @param eno 패밀리를 폐기할 토큰 소유자 사번
     */
    public FamilyRevocationRequiredException(Reason reason, String eno) {
        super("Refresh Token 패밀리 폐기가 필요합니다: reason=" + reason);
        this.reason = reason;
        this.eno = eno;
    }

    /**
     * 폐기 사유를 반환합니다.
     *
     * @return 폐기 사유
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * 패밀리를 폐기할 대상 사용자 사번을 반환합니다.
     *
     * @return 토큰 소유자 사번
     */
    public String getEno() {
        return eno;
    }
}
