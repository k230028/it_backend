package com.kdb.it;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 애플리케이션 통합 테스트 클래스
 *
 * <p>{@code @SpringBootTest}: 테스트 시 전체 Spring Application Context를 로드합니다.
 * 모든 빈(Bean) 설정, 자동 설정(Auto-configuration), 데이터베이스 연결 등이
 * 실제 환경과 동일하게 구성됩니다.</p>
 *
 * <p>테스트 목적:</p>
 * <ul>
 *   <li>애플리케이션 컨텍스트가 오류 없이 로드되는지 검증</li>
 *   <li>모든 빈 의존성 주입이 정상적으로 이루어지는지 확인</li>
 *   <li>DB 연결 설정, JPA 엔티티 매핑 등 기본 설정 유효성 검사</li>
 * </ul>
 *
 * <p>주의: {@code @SpringBootTest}는 전체 컨텍스트를 로드하므로
 * 실행 시간이 길 수 있습니다. 단위 테스트는 별도의 테스트 클래스로 분리하는 것이 좋습니다.</p>
 *
 * <p>테스트 실행: {@code ./mvnw test} 또는 IDE에서 직접 실행</p>
 */
// Oracle DB 접속이 필요한 전체 통합 테스트 — DB_PASSWORD 환경변수 설정 후 로컬에서만 실행
// CI 환경에서는 Testcontainers Oracle로 대체 예정 (TASK.md 참조)
@Disabled("Oracle 전체 통합 테스트: DB_PASSWORD 환경변수 설정 후 수동 실행")
@SpringBootTest
@ActiveProfiles("test")
class ItApplicationTests {

	/**
	 * Spring Application Context 로드 테스트
	 *
	 * <p>애플리케이션 컨텍스트가 정상적으로 시작되는지 확인합니다.
	 * 메서드 내부가 비어있어도 {@code @SpringBootTest}에 의해 컨텍스트 로드 자체가 테스트됩니다.</p>
	 *
	 * <p>컨텍스트 로드 실패 원인 예시:</p>
	 * <ul>
	 *   <li>필수 설정 값({@code application.properties}) 누락</li>
	 *   <li>빈 의존성 순환 참조</li>
	 *   <li>DB 연결 실패</li>
	 *   <li>JPA 엔티티 매핑 오류</li>
	 * </ul>
	 */
	@Test
	void contextLoads() {
		// 컨텍스트가 오류 없이 로드되면 테스트 통과
		// 별도의 검증 로직 없이 @SpringBootTest의 컨텍스트 로드 자체가 테스트 대상
	}

}
