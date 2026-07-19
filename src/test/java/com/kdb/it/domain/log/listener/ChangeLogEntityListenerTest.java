package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

import com.kdb.it.domain.log.annotation.LogTarget;
import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.Column;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ChangeLogEntityListener 단위 테스트
 *
 * <p>
 * JPA 엔티티 생명주기 콜백(@PrePersist, @PreUpdate, @PostUpdate)이
 * 올바른 변경유형(C/U/D)으로 로그를 저장하는지 검증합니다.
 * </p>
 *
 * <p>
 * ApplicationContextHolder 는 정적 메서드를 사용하므로 MockedStatic 으로 mock 처리합니다.
 * AuditLogPersister 는 Mockito mock 으로 주입합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeLogEntityListenerTest {

    @Mock
    private AuditLogPersister auditLogPersister;

    private ChangeLogEntityListener listener;
    private MockedStatic<ApplicationContextHolder> contextHolderMock;

    @BeforeEach
    void setUp() {
        listener = new ChangeLogEntityListener();
        contextHolderMock = mockStatic(ApplicationContextHolder.class, withSettings().lenient());
        contextHolderMock.when(() -> ApplicationContextHolder.getBean(AuditLogPersister.class))
                .thenReturn(auditLogPersister);
    }

    @AfterEach
    void tearDown() {
        contextHolderMock.close();
    }

    // ---- 성공 케이스 ----

    /**
     * @LogTarget 이 붙은 엔티티의 @PrePersist 이벤트 발생 시
     * AuditLogPersister.persist() 가 변경유형 'C' 로 호출되어야 합니다.
     */
    @Test
    @DisplayName("onPrePersist - @LogTarget 엔티티 생성 시 변경유형 C 로 로그 저장")
    void onPrePersist_LogTarget엔티티_변경유형C로저장() throws Exception {
        // Arrange
        SampleEntity entity = new SampleEntity();

        // Act
        listener.onPrePersist(entity);

        // Assert: 변경유형 C(Create)로 persist 호출
        verify(auditLogPersister, times(1))
                .persist(eq(entity), eq(SampleLogEntity.class), eq("C"));
    }

    /**
     * delYn 필드가 N 인 엔티티의 @PreUpdate 이벤트 발생 시
     * 변경유형 U 로 로그를 저장해야 합니다.
     */
    @Test
    @DisplayName("onPreUpdate - delYn=N 엔티티 수정 시 변경유형 U 로 로그 저장")
    void onPreUpdate_delYnN_변경유형U로저장() throws Exception {
        // Arrange
        SampleEntity entity = new SampleEntity();
        entity.delYn = "N";

        // Act
        listener.onPreUpdate(entity);

        // Assert: 변경유형 U(Update)로 persist 호출
        verify(auditLogPersister, times(1))
                .persist(eq(entity), eq(SampleLogEntity.class), eq("U"));
    }

    /**
     * delYn 필드가 Y 인 엔티티의 @PreUpdate 이벤트 발생 시
     * 변경유형 D(논리삭제)로 로그를 저장해야 합니다.
     */
    @Test
    @DisplayName("onPreUpdate - delYn=Y 엔티티 수정 시 변경유형 D 로 로그 저장")
    void onPreUpdate_delYnY_변경유형D로저장() throws Exception {
        // Arrange
        SampleEntity entity = new SampleEntity();
        entity.delYn = "Y";

        // Act
        listener.onPreUpdate(entity);

        // Assert: 변경유형 D(Delete)로 persist 호출
        verify(auditLogPersister, times(1))
                .persist(eq(entity), eq(SampleLogEntity.class), eq("D"));
    }

    // ---- 실패 케이스 ----

    /**
     * @LogTarget 어노테이션이 없는 엔티티의 @PrePersist 이벤트 발생 시
     * AuditLogPersister 가 호출되지 않아야 합니다.
     */
    @Test
    @DisplayName("onPrePersist - @LogTarget 없는 엔티티는 로그 저장하지 않음")
    void onPrePersist_LogTarget없는엔티티_로그저장안함() throws Exception {
        // Arrange
        NoLogTargetEntity entity = new NoLogTargetEntity();

        // Act
        listener.onPrePersist(entity);

        // Assert
        verify(auditLogPersister, never())
                .persist(any(), any(), any());
    }

    /**
     * @LogTarget 없는 엔티티의 @PreUpdate 이벤트 발생 시
     * AuditLogPersister 가 호출되지 않아야 합니다.
     */
    @Test
    @DisplayName("onPreUpdate - @LogTarget 없는 엔티티는 로그 저장하지 않음")
    void onPreUpdate_LogTarget없는엔티티_로그저장안함() throws Exception {
        // Arrange
        NoLogTargetEntity entity = new NoLogTargetEntity();

        // Act
        listener.onPreUpdate(entity);

        // Assert
        verify(auditLogPersister, never())
                .persist(any(), any(), any());
    }

    /**
     * AuditLogPersister 가 예외를 던지더라도 ChangeLogEntityListener 는
     * 예외를 삼켜서 본 업무 트랜잭션에 영향을 주지 않아야 합니다.
     */
    @Test
    @DisplayName("onPrePersist - AuditLogPersister 예외 발생 시 예외를 삼켜서 본 업무에 영향 없음")
    void onPrePersist_Persister예외_예외삼킴() throws Exception {
        // Arrange
        doThrow(new RuntimeException("DB 오류"))
                .when(auditLogPersister).persist(any(), any(), any());
        SampleEntity entity = new SampleEntity();

        // Act & Assert: 예외가 전파되지 않아야 함
        assertThatCode(() -> listener.onPrePersist(entity))
                .doesNotThrowAnyException();
    }

    // ---- 엣지 케이스 ----

    /**
     * 동일 flush 사이클에서 동일 엔티티 인스턴스에 대해 @PreUpdate 가 두 번 호출될 때
     * 두 번째 호출은 중복 방지를 위해 무시되어야 합니다.
     */
    @Test
    @DisplayName("onPreUpdate - 동일 엔티티 인스턴스 중복 호출 시 첫 번째만 로그 저장")
    void onPreUpdate_동일인스턴스중복호출_첫번째만저장() throws Exception {
        // Arrange
        SampleEntity entity = new SampleEntity();

        // Act: 동일 인스턴스에 두 번 호출
        listener.onPreUpdate(entity);
        listener.onPreUpdate(entity);

        // Assert: persist 는 1회만 호출되어야 함
        verify(auditLogPersister, times(1))
                .persist(any(), any(), any());
    }

    /**
     * @PostUpdate 이후 동일 엔티티에 대해 다시 @PreUpdate 가 호출되면
     * 다음 flush 사이클의 새 업데이트로 로그 저장이 가능해야 합니다.
     */
    @Test
    @DisplayName("onPostUpdate - PostUpdate 이후 동일 엔티티 재업데이트 시 로그 저장 가능")
    void onPostUpdate_PostUpdate이후재업데이트_로그저장가능() throws Exception {
        // Arrange
        SampleEntity entity = new SampleEntity();

        // Act: 1차 PreUpdate → PostUpdate(제거) → 2차 PreUpdate
        listener.onPreUpdate(entity);
        listener.onPostUpdate(entity);
        listener.onPreUpdate(entity);

        // Assert: persist 가 총 2회 호출되어야 함
        verify(auditLogPersister, times(2))
                .persist(any(), any(), any());
    }

    /**
     * ApplicationContextHolder 가 초기화되지 않아 예외가 발생하더라도
     * 본 업무 트랜잭션에 영향을 주지 않아야 합니다.
     */
    @Test
    @DisplayName("persistLog - ApplicationContextHolder 예외 발생 시 예외를 삼켜서 본 업무에 영향 없음")
    void persistLog_컨텍스트예외_예외삼킴() {
        // Arrange: getBean() 이 예외를 던지도록 재설정
        contextHolderMock.when(() -> ApplicationContextHolder.getBean(AuditLogPersister.class))
                .thenThrow(new IllegalStateException("ApplicationContext 초기화 안됨"));
        SampleEntity entity = new SampleEntity();

        // Act & Assert: 예외가 전파되지 않아야 함
        assertThatCode(() -> listener.onPrePersist(entity))
                .doesNotThrowAnyException();
    }

    // ---- ERR-06: 재진입 가드·예약 실패 ----

    /**
     * 감사 실패 처리 중이면(재진입) persister를 호출하지 않고 즉시 반환합니다.
     */
    @Test
    @DisplayName("persistLog - 감사 실패 처리 중이면 persister를 호출하지 않는다")
    void persistLog_감사실패처리중이면persister를호출하지않는다() {
        try (MockedStatic<AuditFailureRecorder> guard = mockStatic(AuditFailureRecorder.class)) {
            guard.when(AuditFailureRecorder::isHandlingFailure).thenReturn(true);
            listener.onPrePersist(new SampleEntity());
            verifyNoInteractions(auditLogPersister);
        }
    }

    /**
     * persister 빈 조회·예약 단계 실패는 recorder에 schedule 단계로 위임하고 예외를 삼킵니다.
     */
    @Test
    @DisplayName("persistLog - persister 조회 실패 시 recorder에 schedule로 기록하고 예외를 삼킨다")
    void persistLog_persister조회실패_recorder에schedule기록() {
        contextHolderMock.when(() -> ApplicationContextHolder.getBean(AuditLogPersister.class))
                .thenThrow(new IllegalStateException("컨텍스트 없음"));
        AuditFailureRecorder recorder = mock(AuditFailureRecorder.class);
        contextHolderMock.when(() -> ApplicationContextHolder.getBean(AuditFailureRecorder.class))
                .thenReturn(recorder);

        assertThatCode(() -> listener.onPrePersist(new SampleEntity())).doesNotThrowAnyException();
        verify(recorder).record(eq("SampleEntity"), anyString(), eq("C"), eq("schedule"), any());
    }

    // ---- 테스트 픽스처 ----

    /** @LogTarget 이 붙은 테스트용 소스 엔티티 */
    @LogTarget(entity = SampleLogEntity.class)
    static class SampleEntity {
        @Column(name = "DEL_YN")
        String delYn = "N";
    }

    /** @LogTarget 이 없는 테스트용 엔티티 (로그 제외 대상) */
    static class NoLogTargetEntity {
    }

    /** 테스트용 로그 엔티티 (BaseLogEntity 최소 구현체) */
    static class SampleLogEntity extends BaseLogEntity {
        public SampleLogEntity() {
        }
    }
}
