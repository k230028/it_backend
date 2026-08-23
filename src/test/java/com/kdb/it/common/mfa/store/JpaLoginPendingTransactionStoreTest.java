package com.kdb.it.common.mfa.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.mfa.domain.LoginPendingTransaction;
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
 * JPA 로그인 대기 거래 저장소의 매핑·1회 소비 판정을 실 DB 없이 검증한다.
 *
 * <p>실제 Oracle 왕복과 동시성은 {@code JpaLoginPendingTransactionStoreIT}가 검증한다. 여기서는 저장소가 조건부 DELETE의 영향 행
 * 수와 사번 일치 여부로 결과를 판정하는 계약만 본다.
 */
@ExtendWith(MockitoExtension.class)
class JpaLoginPendingTransactionStoreTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Instant NOW = Instant.parse("2026-08-23T02:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(300);

    @Mock private LoginPendingTransactionJpaRepository repository;

    private JpaLoginPendingTransactionStore store;

    @BeforeEach
    void setUp() {
        store = new JpaLoginPendingTransactionStore(repository);
    }

    private static LocalDateTime toDtm(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZONE);
    }

    private static LoginPendingTransactionEntity entity(String tokenHash, String eno) {
        return LoginPendingTransactionEntity.create(tokenHash, eno, toDtm(EXPIRES));
    }

    @Test
    @DisplayName("저장은 도메인 값을 엔티티로 옮기고 만료 시각을 로컬 일시로 변환한다")
    void save_mapsDomainToEntity() {
        store.save(new LoginPendingTransaction("token-1", "E0001", EXPIRES));

        ArgumentCaptor<LoginPendingTransactionEntity> captor =
                ArgumentCaptor.forClass(LoginPendingTransactionEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTokenHash()).isEqualTo("token-1");
        assertThat(captor.getValue().getEno()).isEqualTo("E0001");
        assertThat(captor.getValue().getEndDtm()).isEqualTo(toDtm(EXPIRES));
    }

    @Test
    @DisplayName("활성 거래를 찾으면 도메인 객체로 되돌린다")
    void findByTokenHash_mapsToDomain() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entity("token-1", "E0001")));

        Optional<LoginPendingTransaction> found = store.findByTokenHash("token-1", NOW);

        assertThat(found).isPresent();
        assertThat(found.get().tokenHash()).isEqualTo("token-1");
        assertThat(found.get().eno()).isEqualTo("E0001");
        assertThat(found.get().expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("만료·미존재로 활성 거래가 없으면 빈 값을 반환한다")
    void findByTokenHash_emptyWhenAbsent() {
        given(repository.findActiveByTokenHash("token-x", toDtm(NOW))).willReturn(Optional.empty());

        assertThat(store.findByTokenHash("token-x", NOW)).isEmpty();
    }

    @Test
    @DisplayName("소비는 조건부 DELETE가 1행을 지운 경우에만 성공한다")
    void consumeOnce_succeedsWhenSingleRowDeleted() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entity("token-1", "E0001")));
        given(repository.consumeOnce("token-1", "E0001", toDtm(NOW))).willReturn(1);

        Optional<LoginPendingTransaction> consumed = store.consumeOnce("token-1", "E0001", NOW);

        assertThat(consumed).isPresent();
        assertThat(consumed.get().eno()).isEqualTo("E0001");
    }

    @Test
    @DisplayName("활성 거래가 없으면 DELETE를 시도하지 않는다")
    void consumeOnce_skipsDeleteWhenAbsent() {
        given(repository.findActiveByTokenHash("token-x", toDtm(NOW))).willReturn(Optional.empty());

        assertThat(store.consumeOnce("token-x", "E0001", NOW)).isEmpty();
        verify(repository, never()).consumeOnce(any(), any(), any());
    }

    @Test
    @DisplayName("사번이 다르면 DELETE를 시도하지 않고 거절한다")
    void consumeOnce_rejectsOtherOwner() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entity("token-1", "E0001")));

        assertThat(store.consumeOnce("token-1", "E9999", NOW)).isEmpty();
        verify(repository, never()).consumeOnce(any(), any(), any());
    }

    @Test
    @DisplayName("경합으로 다른 인스턴스가 먼저 지워 0행이면 실패로 판정한다")
    void consumeOnce_failsWhenNoRowDeleted() {
        given(repository.findActiveByTokenHash("token-1", toDtm(NOW)))
                .willReturn(Optional.of(entity("token-1", "E0001")));
        given(repository.consumeOnce(eq("token-1"), eq("E0001"), any())).willReturn(0);

        assertThat(store.consumeOnce("token-1", "E0001", NOW)).isEmpty();
    }
}
