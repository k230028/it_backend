package com.kdb.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * IT Portal 백엔드 애플리케이션 메인 클래스
 *
 * <p>Spring Boot 애플리케이션의 진입점(Entry Point)으로,
 * JVM이 시작할 때 {@link #main(String[])} 메서드를 실행합니다.</p>
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>정보화사업(프로젝트) 관리 REST API</li>
 *   <li>전산관리비(비용) 관리 REST API</li>
 *   <li>신청서 및 결재 워크플로우 API</li>
 *   <li>사용자/조직 관리 API</li>
 *   <li>JWT 기반 인증/인가</li>
 * </ul>
 *
 * @author KDB IT Team
 * @version 1.0.0
 */
@SpringBootApplication // Spring Boot 자동 설정, 컴포넌트 스캔, 빈 등록을 일괄 활성화
public class ItApplication extends SpringBootServletInitializer {

	/**
	 * 외장 WAS 배포 시 서블릿 컨텍스트 초기화 (WAR 배포용)
	 * java -jar 실행 시에는 호출되지 않으며, 외장 Tomcat/WAS에 WAR 배포 시 사용됩니다.
	 */
	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(ItApplication.class);
	}

	/**
	 * 애플리케이션 시작 메서드
	 *
	 * <p>Spring Boot 컨텍스트를 초기화하고 내장 웹 서버(Tomcat)를 구동합니다.</p>
	 *
	 * @param args 커맨드라인 인수 (예: --server.port=8080)
	 */
	public static void main(String[] args) {
		SpringApplication.run(ItApplication.class, args);
	}

}
