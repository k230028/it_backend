package com.kdb.it.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정 클래스
 *
 * <p>QueryDSL을 사용한 타입 안전(Type-Safe) 동적 쿼리 작성을 위한 {@link JPAQueryFactory} 빈을 등록합니다.
 *
 * <p>QueryDSL 사용 예시:
 *
 * <pre>{@code
 * @RequiredArgsConstructor
 * public class UserRepositoryImpl implements UserRepositoryCustom {
 *     private final JPAQueryFactory queryFactory;
 *
 *     public List<CuserI> searchByName(String name) {
 *         QCuserI cuserI = QCuserI.cuserI;
 *         return queryFactory.selectFrom(cuserI)
 *                 .where(cuserI.usrNm.contains(name))
 *                 .fetch();
 *     }
 * }
 * }</pre>
 */
@Configuration // Spring 설정 클래스로 등록
public class QuerydslConfig {

    /**
     * JPA EntityManager 주입
     *
     * <p>{@code @PersistenceContext}는 JPA 컨텍스트 범위에서 관리되는 {@link EntityManager}를 주입합니다. 일반
     * {@code @Autowired}와 달리 트랜잭션별로 EntityManager 프록시를 올바르게 처리합니다.
     */
    @PersistenceContext private EntityManager entityManager;

    /**
     * JPAQueryFactory 빈 등록
     *
     * <p>QueryDSL의 쿼리 빌더인 {@link JPAQueryFactory}를 Spring 빈으로 등록합니다. Repository 구현체에서
     * {@code @RequiredArgsConstructor} 또는 {@code @Autowired}로 주입하여 사용합니다.
     *
     * <p>스레드 안전성: {@link JPAQueryFactory}는 내부적으로 {@link EntityManager} 프록시를 사용하므로 싱글톤으로 등록해도 멀티스레드
     * 환경에서 안전합니다.
     *
     * @return {@link EntityManager}를 사용하는 {@link JPAQueryFactory} 인스턴스
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
