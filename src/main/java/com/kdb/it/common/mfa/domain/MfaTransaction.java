package com.kdb.it.common.mfa.domain;

import java.time.Instant;
import java.util.Objects;

/** 상태 전이를 새 인스턴스로 반환하는 불변 MFA 거래다. */
public final class MfaTransaction {

    private final String tokenHash;
    private final String eno;
    private final MfaPurpose purpose;
    private final MfaMethod method;
    private final Instant expiresAt;
    private final String providerChallengeHash;
    private final String svcTrId;
    private final String proofHash;
    private final MfaTransactionStatus status;
    private final Instant verifiedAt;
    private final int failureCount;

    private MfaTransaction(
            String tokenHash,
            String eno,
            MfaPurpose purpose,
            MfaMethod method,
            Instant expiresAt,
            String providerChallengeHash,
            String svcTrId,
            String proofHash,
            MfaTransactionStatus status,
            Instant verifiedAt,
            int failureCount) {
        Objects.requireNonNull(tokenHash, "토큰 해시는 필수입니다.");
        Objects.requireNonNull(eno, "사원번호는 필수입니다.");
        Objects.requireNonNull(purpose, "MFA 목적은 필수입니다.");
        Objects.requireNonNull(method, "MFA 수단은 필수입니다.");
        Objects.requireNonNull(expiresAt, "만료 시각은 필수입니다.");
        Objects.requireNonNull(status, "MFA 상태는 필수입니다.");
        if (failureCount < 0) {
            throw new IllegalArgumentException("실패 횟수는 음수일 수 없습니다.");
        }
        if (status == MfaTransactionStatus.VERIFIED && verifiedAt == null) {
            throw new IllegalArgumentException("검증 완료 거래에는 검증 시각이 필요합니다.");
        }
        if ((status == MfaTransactionStatus.PENDING || status == MfaTransactionStatus.LOCKED)
                && verifiedAt != null) {
            throw new IllegalArgumentException("대기 또는 잠금 거래에는 검증 시각이 있을 수 없습니다.");
        }
        this.tokenHash = tokenHash;
        this.eno = eno;
        this.purpose = purpose;
        this.method = method;
        this.expiresAt = expiresAt;
        this.providerChallengeHash = providerChallengeHash;
        this.svcTrId = svcTrId;
        this.proofHash = proofHash;
        this.status = status;
        this.verifiedAt = verifiedAt;
        this.failureCount = failureCount;
    }

    /** 대기 상태의 MFA 거래를 생성한다. */
    public static MfaTransaction pending(
            String tokenHash, String eno, MfaPurpose purpose, MfaMethod method, Instant expiresAt) {
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                null,
                null,
                null,
                MfaTransactionStatus.PENDING,
                null,
                0);
    }

    /**
     * 공급자 challenge 해시와 서비스 거래 식별자를 결속한 대기 상태 MFA 거래를 생성한다.
     *
     * @param svcTrId OnePass 서비스 거래 식별자(svcTrId). FIDO만 값이 있고 다른 수단은 null이다.
     */
    public static MfaTransaction pending(
            String tokenHash,
            String eno,
            MfaPurpose purpose,
            MfaMethod method,
            Instant expiresAt,
            String providerChallengeHash,
            String svcTrId) {
        Objects.requireNonNull(providerChallengeHash, "공급자 challenge 해시는 필수입니다.");
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                providerChallengeHash,
                svcTrId,
                null,
                MfaTransactionStatus.PENDING,
                null,
                0);
    }

    /** 만료되지 않은 대기 거래를 검증 완료 상태로 전이한다. */
    public MfaTransaction verify(Instant now) {
        if (isExpiredAt(now)) {
            return withStatus(MfaTransactionStatus.EXPIRED, verifiedAt, failureCount);
        }
        if (status != MfaTransactionStatus.PENDING) {
            return this;
        }
        return withStatus(MfaTransactionStatus.VERIFIED, now, failureCount);
    }

    /** 실패 횟수를 올리고 최대 횟수에 도달하면 거래를 잠근다. */
    public MfaTransaction fail(Instant now, int maxFailures) {
        if (maxFailures < 1) {
            throw new IllegalArgumentException("최대 실패 횟수는 1 이상이어야 합니다.");
        }
        if (isExpiredAt(now)) {
            return withStatus(MfaTransactionStatus.EXPIRED, verifiedAt, failureCount);
        }
        if (status != MfaTransactionStatus.PENDING) {
            return this;
        }
        int nextFailureCount = failureCount + 1;
        MfaTransactionStatus nextStatus =
                nextFailureCount >= maxFailures
                        ? MfaTransactionStatus.LOCKED
                        : MfaTransactionStatus.PENDING;
        return withStatus(nextStatus, null, nextFailureCount);
    }

    /** 지정 시각에 거래가 만료됐는지 확인한다. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private MfaTransaction withStatus(
            MfaTransactionStatus nextStatus, Instant nextVerifiedAt, int nextFailureCount) {
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                providerChallengeHash,
                svcTrId,
                proofHash,
                nextStatus,
                nextVerifiedAt,
                nextFailureCount);
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String eno() {
        return eno;
    }

    public MfaPurpose purpose() {
        return purpose;
    }

    public MfaMethod method() {
        return method;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    /** 공급자 challenge의 SHA-256 해시이며 원문은 저장하지 않는다. */
    public String providerChallengeHash() {
        return providerChallengeHash;
    }

    /** OnePass 서비스 거래 식별자(svcTrId) 원문이다. FIDO만 값이 있고 다른 수단은 null이다. */
    public String svcTrId() {
        return svcTrId;
    }

    /** 검증 후 발급한 1회용 증표의 SHA-256 해시이며 원문은 저장하지 않는다. */
    public String proofHash() {
        return proofHash;
    }

    /** 검증 완료 거래에만 1회용 증표 해시를 결속한다. */
    public MfaTransaction bindProofHash(String nextProofHash) {
        if (status != MfaTransactionStatus.VERIFIED || proofHash != null) {
            return this;
        }
        Objects.requireNonNull(nextProofHash, "MFA 증표 해시는 필수입니다.");
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                providerChallengeHash,
                svcTrId,
                nextProofHash,
                status,
                verifiedAt,
                failureCount);
    }

    /**
     * 영속 계층에 저장된 상태를 그대로 복원한다.
     *
     * <p>JPA 저장소의 엔티티→도메인 매핑 전용이다. 업무 흐름은 이 메서드가 아니라 {@link #pending}과 {@link #verify}·{@link
     * #fail}·{@link #bindProofHash} 등 전이 메서드를 사용해야 한다.
     *
     * @param tokenHash 거래 토큰 해시
     * @param eno 사원번호
     * @param purpose MFA 목적
     * @param method MFA 수단
     * @param expiresAt 만료 시각
     * @param providerChallengeHash 공급자 challenge 해시. 없으면 null
     * @param svcTrId OnePass 서비스 거래 식별자 원문. FIDO가 아니면 null
     * @param proofHash 증표 해시. 없으면 null
     * @param status 저장된 상태
     * @param verifiedAt 검증 시각. 없으면 null
     * @param failureCount 실패 횟수
     * @return 저장된 필드를 그대로 담은 거래
     */
    public static MfaTransaction restore(
            String tokenHash,
            String eno,
            MfaPurpose purpose,
            MfaMethod method,
            Instant expiresAt,
            String providerChallengeHash,
            String svcTrId,
            String proofHash,
            MfaTransactionStatus status,
            Instant verifiedAt,
            int failureCount) {
        return new MfaTransaction(
                tokenHash,
                eno,
                purpose,
                method,
                expiresAt,
                providerChallengeHash,
                svcTrId,
                proofHash,
                status,
                verifiedAt,
                failureCount);
    }

    public MfaTransactionStatus status() {
        return status;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public int failureCount() {
        return failureCount;
    }
}
