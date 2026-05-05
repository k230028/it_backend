package com.kdb.it.domain.log.listener;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * JPA EntityListener에서 Spring 빈에 접근하기 위한 정적 컨텍스트 홀더.
 *
 * <p>{@code ChangeLogEntityListener}는 JPA가 직접 인스턴스화하므로
 * Spring DI를 사용할 수 없다. 이 클래스를 통해 정적으로 {@code ApplicationContext}에
 * 접근하여 {@link AuditLogPersister} 등 Spring 빈을 조회한다.</p>
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    /**
     * Spring 빈 조회.
     *
     * @param beanClass 조회할 빈의 클래스
     * @param <T>       빈 타입
     * @return Spring 컨텍스트에서 조회한 빈 인스턴스
     */
    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext가 초기화되지 않았습니다.");
        }
        return context.getBean(beanClass);
    }

    /**
     * Spring ApplicationEvent 발행.
     *
     * <p>JPA EntityListener에서 직접 persist()를 호출하면 Hibernate ActionQueue
     * 이터레이션 도중 ConcurrentModificationException이 발생한다.
     * 이 메서드를 통해 이벤트를 발행하고, {@code @TransactionalEventListener(BEFORE_COMMIT)}이
     * flush 완료 후 안전한 시점에 로그 INSERT를 처리한다.</p>
     *
     * @param event 발행할 이벤트 객체
     */
    public static void publishEvent(Object event) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext가 초기화되지 않았습니다.");
        }
        context.publishEvent(event);
    }
}
