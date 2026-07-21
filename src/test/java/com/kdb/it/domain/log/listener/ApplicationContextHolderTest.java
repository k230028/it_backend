package com.kdb.it.domain.log.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ApplicationContextHolder 단위 테스트
 *
 * <p>JPA EntityListener 가 Spring 빈을 정적으로 조회하는 홀더의 초기화 전/후 동작을 검증합니다. 정적 컨텍스트 필드는 다른 테스트에 영향을 주지 않도록
 * 각 테스트 종료 시 원래 값으로 복원합니다.
 */
class ApplicationContextHolderTest {

    /** 테스트 진입 시점의 정적 컨텍스트 원본 (테스트 후 복원용) */
    private ApplicationContext originalContext;

    @BeforeEach
    void setUp() {
        originalContext =
                (ApplicationContext)
                        ReflectionTestUtils.getField(ApplicationContextHolder.class, "context");
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(ApplicationContextHolder.class, "context", originalContext);
    }

    // ---- 성공 케이스 ----

    /** setApplicationContext 로 컨텍스트 주입 후 getBean 이 해당 컨텍스트의 빈을 반환합니다. */
    @Test
    @DisplayName("getBean - 컨텍스트 초기화 후 빈 조회 성공")
    void getBean_컨텍스트초기화후_빈조회성공() {
        // Arrange
        ApplicationContext context = mock(ApplicationContext.class);
        AuditLogPersister persister = mock(AuditLogPersister.class);
        when(context.getBean(AuditLogPersister.class)).thenReturn(persister);
        ApplicationContextHolder holder = new ApplicationContextHolder();

        // Act
        holder.setApplicationContext(context);
        AuditLogPersister result = ApplicationContextHolder.getBean(AuditLogPersister.class);

        // Assert
        assertThat(result).isSameAs(persister);
    }

    /** setApplicationContext 를 다시 호출하면 최신 컨텍스트가 빈 조회에 사용됩니다. */
    @Test
    @DisplayName("setApplicationContext - 재호출 시 최신 컨텍스트로 교체")
    void setApplicationContext_재호출시_최신컨텍스트로교체() {
        // Arrange
        ApplicationContext first = mock(ApplicationContext.class);
        ApplicationContext second = mock(ApplicationContext.class);
        AuditLogPersister fromSecond = mock(AuditLogPersister.class);
        when(second.getBean(AuditLogPersister.class)).thenReturn(fromSecond);
        ApplicationContextHolder holder = new ApplicationContextHolder();

        // Act
        holder.setApplicationContext(first);
        holder.setApplicationContext(second);
        AuditLogPersister result = ApplicationContextHolder.getBean(AuditLogPersister.class);

        // Assert
        assertThat(result).isSameAs(fromSecond);
    }

    // ---- 실패 케이스 ----

    /** 컨텍스트 미초기화 상태에서 getBean 호출 시 IllegalStateException 이 발생합니다. */
    @Test
    @DisplayName("getBean - 컨텍스트 미초기화 시 IllegalStateException 발생")
    void getBean_컨텍스트미초기화시_예외발생() {
        // Arrange
        ReflectionTestUtils.setField(ApplicationContextHolder.class, "context", null);

        // Act & Assert
        assertThatThrownBy(() -> ApplicationContextHolder.getBean(AuditLogPersister.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("초기화되지 않았습니다");
    }
}
