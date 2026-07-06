package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.log.entity.BaseLogEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AuditLogPersister 단위 테스트
 *
 * <p>변경 로그 INSERT 로직을 검증합니다.
 * EntityManager 는 Mockito mock 으로 주입하며,
 * SecurityContextHolder 를 직접 조작하여 인증 상태를 제어합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuditLogPersisterTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AuditLogPersister auditLogPersister;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(auditLogPersister, "entityManager", entityManager);
        SecurityContextHolder.clearContext();
    }

    // ---- 성공 케이스 ----

    /** 인증된 사용자 컨텍스트에서 persist() 호출 시 entityManager.persist() 가 1회 호출됩니다. */
    @Test
    @DisplayName("persist - 인증된 사용자 컨텍스트에서 로그 INSERT 성공")
    void persist_인증된사용자_로그INSERT성공() throws Exception {
        // Arrange
        Authentication auth = mock(Authentication.class);
        Mockito.when(auth.isAuthenticated()).thenReturn(true);
        Mockito.when(auth.getName()).thenReturn("EMP001");
        SecurityContext sc = mock(SecurityContext.class);
        Mockito.when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);
        SourceEntity source = new SourceEntity("홍길동");

        // Act
        auditLogPersister.persist(source, SampleLogEntity.class, "C");

        // Assert
        verify(entityManager, times(1)).persist(any(SampleLogEntity.class));
    }

    /** 미인증 상태에서 persist() 호출 시 chgUsid null 로 설정되고 persist() 는 정상 호출됩니다. */
    @Test
    @DisplayName("persist - 미인증 상태에서 chgUsid null 로 로그 INSERT 성공")
    void persist_미인증상태_chgUsidNull로INSERT성공() {
        // Arrange
        SecurityContextHolder.clearContext();
        SourceEntity source = new SourceEntity("테스트");

        // Act & Assert
        assertThatCode(() -> auditLogPersister.persist(source, SampleLogEntity.class, "U"))
                .doesNotThrowAnyException();
        verify(entityManager, times(1)).persist(any(SampleLogEntity.class));
    }

    /** 변경유형 D(논리삭제)로 persist() 호출 시 entityManager.persist() 가 정상 호출됩니다. */
    @Test
    @DisplayName("persist - 변경유형 D(논리삭제)로 로그 INSERT 성공")
    void persist_변경유형D_로그INSERT성공() throws Exception {
        // Arrange
        SourceEntity source = new SourceEntity("삭제대상");

        // Act
        auditLogPersister.persist(source, SampleLogEntity.class, "D");

        // Assert
        verify(entityManager, times(1)).persist(any(SampleLogEntity.class));
    }

    // ---- 실패 케이스 ----

    /**
     * 인스턴스화 불가 로그 엔티티 클래스 사용 시 RuntimeException 이 전파됩니다.
     * 입력값: 기본 생성자가 private 인 로그 엔티티 클래스
     * 실패 조건: getDeclaredConstructor().newInstance() 실패
     */
    @Test
    @DisplayName("persist - 인스턴스화 불가 로그 엔티티 클래스 사용 시 RuntimeException 전파")
    void persist_인스턴스화불가클래스_RuntimeException전파() {
        // Arrange
        SourceEntity source = new SourceEntity("테스트");

        // Act & Assert
        assertThatThrownBy(() ->
                auditLogPersister.persist(source, PrivateConstructorLogEntity.class, "C"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("변경 로그 INSERT 실패");
    }

    /**
     * entityManager.persist() 가 예외를 던지면 RuntimeException 으로 래핑되어 전파됩니다.
     * 입력값: PersistenceException 발생 상황
     * 실패 조건: DB 시퀀스 미생성 등 인프라 오류
     */
    @Test
    @DisplayName("persist - EntityManager 예외 발생 시 RuntimeException 으로 래핑하여 전파")
    void persist_EntityManager예외_RuntimeException래핑전파() {
        // Arrange
        doThrow(new jakarta.persistence.PersistenceException("시퀀스 미생성"))
                .when(entityManager).persist(any());
        SourceEntity source = new SourceEntity("테스트");

        // Act & Assert
        assertThatThrownBy(() ->
                auditLogPersister.persist(source, SampleLogEntity.class, "C"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("변경 로그 INSERT 실패");
    }

    /**
     * 성공 시 entityManager.persist() 만 호출됩니다.
     * merge() 나 다른 쓰기 메서드는 호출되지 않아야 합니다.
     */
    @Test
    @DisplayName("persist - 성공 시 entityManager.persist() 만 호출되어야 함 (merge 불가)")
    void persist_성공시_persist만호출() throws Exception {
        // Arrange
        SourceEntity source = new SourceEntity("테스트");

        // Act
        auditLogPersister.persist(source, SampleLogEntity.class, "C");

        // Assert
        verify(entityManager, times(1)).persist(any());
        verify(entityManager, never()).merge(any());
    }

    // ---- 엣지 케이스 ----

    /**
     * Authentication 이 존재하지만 isAuthenticated() 가 false 인 경우
     * chgUsid 가 null 로 처리됩니다.
     */
    @Test
    @DisplayName("persist - Authentication 존재하나 미인증 상태이면 chgUsid null 로 처리")
    void persist_Authentication존재미인증_chgUsidNull() {
        // Arrange
        Authentication auth = mock(Authentication.class);
        Mockito.when(auth.isAuthenticated()).thenReturn(false);
        SecurityContext sc = mock(SecurityContext.class);
        Mockito.when(sc.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(sc);
        SourceEntity source = new SourceEntity("테스트");

        // Act & Assert
        assertThatCode(() -> auditLogPersister.persist(source, SampleLogEntity.class, "U"))
                .doesNotThrowAnyException();
        verify(entityManager, times(1)).persist(any());
    }

    /** 원본 엔티티에 @Column 필드가 없더라도 persist() 는 정상 완료됩니다. */
    @Test
    @DisplayName("persist - 원본 엔티티에 @Column 필드 없어도 정상 저장")
    void persist_Column필드없는원본엔티티_정상저장() {
        // Arrange
        NoColumnEntity source = new NoColumnEntity();

        // Act & Assert
        assertThatCode(() -> auditLogPersister.persist(source, SampleLogEntity.class, "C"))
                .doesNotThrowAnyException();
        verify(entityManager, times(1)).persist(any());
    }

    /** IllegalAccessException 진단 로그 경로는 예외를 다시 던지지 않아 본 업무 흐름을 막지 않습니다. */
    @Test
    @DisplayName("logReflectionAccessFailure - IllegalAccessException 진단 경로는 예외를 던지지 않는다")
    void logReflectionAccessFailure_IllegalAccessException_예외없음() {
        // Arrange
        IllegalAccessException cause = new IllegalAccessException("접근 실패");

        // Act & Assert
        assertThatCode(() -> auditLogPersister.logReflectionAccessFailure(
                "read",
                SourceEntity.class,
                "name",
                cause
        )).doesNotThrowAnyException();
    }

    /** 연속으로 두 번 persist() 를 호출하면 entityManager.persist() 도 두 번 호출됩니다. */
    @Test
    @DisplayName("persist - 연속 두 번 호출 시 entityManager.persist() 두 번 호출")
    void persist_연속두번호출_persist두번호출() throws Exception {
        // Arrange
        SourceEntity s1 = new SourceEntity("엔티티1");
        SourceEntity s2 = new SourceEntity("엔티티2");

        // Act
        auditLogPersister.persist(s1, SampleLogEntity.class, "C");
        auditLogPersister.persist(s2, SampleLogEntity.class, "U");

        // Assert
        verify(entityManager, times(2)).persist(any());
    }

    // ---- 테스트 픽스처 ----

    /** @Column 필드를 가진 테스트용 소스 엔티티 */
    static class SourceEntity {
        @Column(name = "NAME")
        private String name;

        SourceEntity(String name) { this.name = name; }
    }

    /** @Column 필드가 없는 테스트용 소스 엔티티 */
    static class NoColumnEntity {
    }

    /** 테스트용 로그 엔티티 (BaseLogEntity 최소 구현체, public 기본 생성자 보유) */
    static class SampleLogEntity extends BaseLogEntity {
        @Column(name = "NAME")
        private String name;

        public SampleLogEntity() {}
    }

    /** 기본 생성자가 private 인 인스턴스화 불가 로그 엔티티 */
    static class PrivateConstructorLogEntity extends BaseLogEntity {
        private PrivateConstructorLogEntity() {
            throw new UnsupportedOperationException("인스턴스화 불가");
        }
    }
}
