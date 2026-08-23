package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.mfa.domain.MfaMethod;
import com.kdb.it.common.mfa.domain.MfaPurpose;
import com.kdb.it.common.mfa.domain.MfaTransaction;
import com.kdb.it.common.mfa.domain.MfaTransactionStatus;
import com.kdb.it.common.mfa.store.MfaTransactionStore.ProofConsumption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * JPA MFA 거래 저장소의 코드 매핑과 조건부 UPDATE 판정을 실 DB 없이 검증한다.
 *
 * <p>실제 Oracle 왕복·동시성은 {@code JpaMfaTransactionStoreIT}가 검증한다. 여기서는 저장소가 영향 행 수 1건만 성공으로 보고,
 * 용도·수단·상태 코드값을 도메인 열거형과 정확히 맞바꾸는 계약만 본다.
 */
@ExtendWith(MockitoExtension.class)
class JpaMfaTransactionStoreTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Instant NOW = Instant.parse("2026-08-23T02:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(90);

    @Mock private MfaTransactionJpaRepository repository;

    private JpaMfaTransactionStore store;

    @BeforeEach
    void setUp() {
        store = new JpaMfaTransactionStore(repository);
    }

    private static LocalDateTime toDtm(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static MfaTransactionEntity.MfaTransactionEntityBuilder<?, ?> entityBuilder() {
        return MfaTransactionEntity.builder()
                .tokenHash("token-1")
                .eno("E0001")
                .purposeCode("10")
                .methodCode("20")
                .statusCode("10")
                .endDtm(toDtm(EXPIRES))
                .failureCount(0);
    }

    // -------------------------------------------------------------------------
    // save
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("저장은 용도·수단을 코드값으로 바꾸고 PENDING·실패 0으로 고정한다")
    void save_mapsDomainToEntity() {
        MfaTransaction transaction =
                MfaTransaction.pending(
                        "token-1",
                        "E0001",
                        MfaPurpose.APPROVAL,
                        MfaMethod.FIDO,
                        EXPIRES,
                        "challenge-hash",
                        "SVC-1");

        store.save(transaction);

        ArgumentCaptor<MfaTransactionEntity> captor =
                ArgumentCaptor.forClass(MfaTransactionEntity.class);
        verify(repository).save(captor.capture());
        MfaTransactionEntity saved = captor.getValue();
        assertThat(saved.getPurposeCode()).isEqualTo("20");
        assertThat(saved.getMethodCode()).isEqualTo("20");
        assertThat(saved.getStatusCode()).isEqualTo("10");
        assertThat(saved.getFailureCount()).isZero();
        assertThat(saved.getTryTokenHash()).isEqualTo("challenge-hash");
        assertThat(saved.getSvcTrNo()).isEqualTo("SVC-1");
        assertThat(saved.getEndDtm()).isEqualTo(toDtm(EXPIRES));
    }

    @Test
    @DisplayName("로그인 용도는 코드 10으로 저장한다")
    void save_mapsLoginPurpose() {
        store.save(
                MfaTransaction.pending(
                        "token-2", "E0001", MfaPurpose.LOGIN, MfaMethod.MOTP, EXPIRES));

        ArgumentCaptor<MfaTransactionEntity> captor =
                ArgumentCaptor.forClass(MfaTransactionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPurposeCode()).isEqualTo("10");
        assertThat(captor.getValue().getMethodCode()).isEqualTo("30");
    }

    @Test
    @DisplayName("지정맥 수단은 코드 10으로 저장한다")
    void save_mapsFingerVeinMethod() {
        store.save(
                MfaTransaction.pending(
                        "token-3", "E0001", MfaPurpose.LOGIN, MfaMethod.FINGER_VEIN, EXPIRES));

        ArgumentCaptor<MfaTransactionEntity> captor =
                ArgumentCaptor.forClass(MfaTransactionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMethodCode()).isEqualTo("10");
    }

    // -------------------------------------------------------------------------
    // findByTokenHash / toDomain
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("활성 거래는 코드값을 도메인 열거형으로 되돌려 매핑한다")
    void findByTokenHash_mapsCodesToDomain() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(
                        Optional.of(
                                entityBuilder()
                                        .statusCode("20")
                                        .vrfDtm(toDtm(NOW))
                                        .proofTokenHash("proof-hash")
                                        .tryTokenHash("challenge-hash")
                                        .svcTrNo("SVC-1")
                                        .failureCount(1)
                                        .build()));

        Optional<MfaTransaction> found = store.findByTokenHash("token-1", NOW);

        assertThat(found).isPresent();
        MfaTransaction tx = found.get();
        assertThat(tx.purpose()).isEqualTo(MfaPurpose.LOGIN);
        assertThat(tx.method()).isEqualTo(MfaMethod.FIDO);
        assertThat(tx.status()).isEqualTo(MfaTransactionStatus.VERIFIED);
        assertThat(tx.expiresAt()).isEqualTo(EXPIRES);
        assertThat(tx.verifiedAt()).isEqualTo(NOW);
        assertThat(tx.proofHash()).isEqualTo("proof-hash");
        assertThat(tx.providerChallengeHash()).isEqualTo("challenge-hash");
        assertThat(tx.svcTrId()).isEqualTo("SVC-1");
        assertThat(tx.failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("검증 시각이 비어 있으면 null로 매핑한다")
    void findByTokenHash_nullVerifiedAt() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entityBuilder().build()));

        assertThat(store.findByTokenHash("token-1", NOW).orElseThrow().verifiedAt()).isNull();
    }

    @Test
    @DisplayName("활성 거래가 없으면 빈 값을 반환한다")
    void findByTokenHash_emptyWhenAbsent() {
        given(repository.findActiveByTokenHash("token-x", toDtm(NOW))).willReturn(Optional.empty());

        assertThat(store.findByTokenHash("token-x", NOW)).isEmpty();
    }

    @Test
    @DisplayName("알 수 없는 수단 코드는 매핑하지 않고 실패로 드러낸다")
    void toDomain_rejectsUnknownMethodCode() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entityBuilder().methodCode("99").build()));

        assertThatThrownBy(() -> store.findByTokenHash("token-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("추가인증수단구분코드");
    }

    @Test
    @DisplayName("알 수 없는 상태 코드는 매핑하지 않고 실패로 드러낸다")
    void toDomain_rejectsUnknownStatusCode() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entityBuilder().statusCode("99").build()));

        assertThatThrownBy(() -> store.findByTokenHash("token-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("추가인증상태구분코드");
    }

    @Test
    @DisplayName("LOCKED·CANCELLED·CONSUMED 상태 코드를 각각 되돌린다")
    void toDomain_mapsRemainingStatusCodes() {
        given(repository.findActiveByTokenHash(eq("token-1"), any()))
                .willReturn(Optional.of(entityBuilder().statusCode("30").build()))
                .willReturn(Optional.of(entityBuilder().statusCode("40").build()))
                .willReturn(Optional.of(entityBuilder().statusCode("50").build()));

        assertThat(store.findByTokenHash("token-1", NOW).orElseThrow().status())
                .isEqualTo(MfaTransactionStatus.LOCKED);
        assertThat(store.findByTokenHash("token-1", NOW).orElseThrow().status())
                .isEqualTo(MfaTransactionStatus.CANCELLED);
        assertThat(store.findByTokenHash("token-1", NOW).orElseThrow().status())
                .isEqualTo(MfaTransactionStatus.CONSUMED);
    }

    // -------------------------------------------------------------------------
    // 조건부 UPDATE 전이
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("검증·증표 결속은 1행 갱신에만 성공하고 최신 행을 다시 조회한다")
    void verifyAndBindProof_succeedsOnSingleRowUpdate() {
        given(repository.verifyAndBindProof("token-1", "proof-hash", toDtm(NOW))).willReturn(1);
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(
                        Optional.of(
                                entityBuilder()
                                        .statusCode("20")
                                        .vrfDtm(toDtm(NOW))
                                        .proofTokenHash("proof-hash")
                                        .build()));

        assertThat(store.verifyAndBindProof("token-1", "proof-hash", NOW)).isPresent();
    }

    @Test
    @DisplayName("검증·증표 결속이 0행이면 재조회 없이 빈 값을 반환한다")
    void verifyAndBindProof_emptyOnNoRowUpdate() {
        given(repository.verifyAndBindProof("token-1", "proof-hash", toDtm(NOW))).willReturn(0);

        assertThat(store.verifyAndBindProof("token-1", "proof-hash", NOW)).isEmpty();
        verify(repository, never()).findActiveByTokenHash(any(), any());
    }

    @Test
    @DisplayName("실패 누적은 1행 갱신에만 성공한다")
    void fail_succeedsOnSingleRowUpdate() {
        given(repository.fail("token-1", toDtm(NOW), 5)).willReturn(1);
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entityBuilder().failureCount(1).build()));

        assertThat(store.fail("token-1", NOW, 5).orElseThrow().failureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 누적이 0행이면 재조회 없이 빈 값을 반환한다")
    void fail_emptyOnNoRowUpdate() {
        given(repository.fail("token-1", toDtm(NOW), 5)).willReturn(0);

        assertThat(store.fail("token-1", NOW, 5)).isEmpty();
        verify(repository, never()).findActiveByTokenHash(any(), any());
    }

    @Test
    @DisplayName("취소는 1행 갱신에만 성공한다")
    void delete_succeedsOnSingleRowUpdate() {
        given(repository.cancel("token-1", toDtm(NOW))).willReturn(1);
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entityBuilder().statusCode("40").build()));

        assertThat(store.delete("token-1", NOW).orElseThrow().status())
                .isEqualTo(MfaTransactionStatus.CANCELLED);
    }

    @Test
    @DisplayName("취소가 0행이면 재조회 없이 빈 값을 반환한다")
    void delete_emptyOnNoRowUpdate() {
        given(repository.cancel("token-1", toDtm(NOW))).willReturn(0);

        assertThat(store.delete("token-1", NOW)).isEmpty();
        verify(repository, never()).findActiveByTokenHash(any(), any());
    }

    // -------------------------------------------------------------------------
    // consumeVerifiedOnce
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("증표 소비가 1행이면 CONSUMED로 판정하고 추가 조회를 하지 않는다")
    void consumeVerifiedOnce_consumed() {
        given(repository.consumeVerifiedOnce("token-1", "E0001", "10", toDtm(NOW))).willReturn(1);

        assertThat(store.consumeVerifiedOnce("token-1", "E0001", MfaPurpose.LOGIN, NOW))
                .isEqualTo(ProofConsumption.CONSUMED);
        verify(repository, never()).findByProofTokenHash(any());
    }

    @Test
    @DisplayName("소비에 실패하고 만료된 행이 남아 있으면 EXPIRED로 구분한다")
    void consumeVerifiedOnce_expired() {
        given(repository.consumeVerifiedOnce("token-1", "E0001", "20", toDtm(NOW))).willReturn(0);
        given(repository.findByProofTokenHash("token-1"))
                .willReturn(
                        Optional.of(entityBuilder().endDtm(toDtm(NOW.minusSeconds(1))).build()));

        assertThat(store.consumeVerifiedOnce("token-1", "E0001", MfaPurpose.APPROVAL, NOW))
                .isEqualTo(ProofConsumption.EXPIRED);
    }

    @Test
    @DisplayName("소비에 실패했지만 아직 만료 전이면 REJECTED로 구분한다")
    void consumeVerifiedOnce_rejected() {
        given(repository.consumeVerifiedOnce("token-1", "E0001", "20", toDtm(NOW))).willReturn(0);
        given(repository.findByProofTokenHash("token-1"))
                .willReturn(Optional.of(entityBuilder().build()));

        assertThat(store.consumeVerifiedOnce("token-1", "E0001", MfaPurpose.APPROVAL, NOW))
                .isEqualTo(ProofConsumption.REJECTED);
    }

    @Test
    @DisplayName("증표 행 자체가 없으면 MISSING으로 구분한다")
    void consumeVerifiedOnce_missing() {
        given(repository.consumeVerifiedOnce("token-x", "E0001", "10", toDtm(NOW))).willReturn(0);
        given(repository.findByProofTokenHash("token-x")).willReturn(Optional.empty());

        assertThat(store.consumeVerifiedOnce("token-x", "E0001", MfaPurpose.LOGIN, NOW))
                .isEqualTo(ProofConsumption.MISSING);
    }

    // -------------------------------------------------------------------------
    // isExpired
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("만료 시각을 지났으면 만료로 본다")
    void isExpired_trueAfterEnd() {
        given(repository.findById("token-1"))
                .willReturn(Optional.of(entityBuilder().endDtm(toDtm(NOW)).build()));

        assertThat(store.isExpired("token-1", NOW)).isTrue();
    }

    @Test
    @DisplayName("만료 시각 이전이면 만료로 보지 않는다")
    void isExpired_falseBeforeEnd() {
        given(repository.findById("token-1")).willReturn(Optional.of(entityBuilder().build()));

        assertThat(store.isExpired("token-1", NOW)).isFalse();
    }

    @Test
    @DisplayName("행이 없으면 만료로 보지 않는다")
    void isExpired_falseWhenAbsent() {
        given(repository.findById("token-x")).willReturn(Optional.empty());

        assertThat(store.isExpired("token-x", NOW)).isFalse();
    }
}
