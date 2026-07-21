package com.kdb.it.support;

import com.kdb.it.config.QuerydslConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 로컬 Oracle 기반 @DataJpaTest 공통 베이스.
 *
 * <p>이미 가동 중인 로컬 Oracle(ITPAPP@127.0.0.1:11521/XEPDB1, CURRENT_SCHEMA=ITPOWN)에 연결한다. {@code
 * ddl-auto=none}으로 실 스키마를 변경하지 않고, {@code @DataJpaTest}의 기본 트랜잭션 롤백으로 데이터 오염을 막는다. QueryDSL 리포지토리
 * 구현체 검증을 위해 {@link QuerydslConfig}를 함께 임포트한다.
 *
 * <p>로컬 전용: {@code @Tag("it")}로 CI 기본 test 게이트에서 제외되며, 로컬 Oracle이 꺼져 있으면 {@code
 * OracleAvailableCondition}(컨텍스트 로드 전 평가)이 전체 테스트를 스킵한다.
 */
@Tag("it")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
public abstract class AbstractOracleRepositoryTest {}
