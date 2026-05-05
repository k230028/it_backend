---
[ 백엔드 가이드 ]
본 파일은 IT Portal 백엔드의 SoT(Single Source of Truth)입니다.
기술 스택, 아키텍처, 보안 정책의 원본은 여기에 있습니다.
공통 규약(한글 주석, 문서 운영 등)은 루트 `../CLAUDE.md` 참조.
---

## 1. 개요
- 패키지 루트: `com.kdb.it`
- 프로젝트 개요/목적은 루트 `../CLAUDE.md` §1 참조.

## 2. 기술 스택
- Framework: Spring Boot 4.0.5
- Language: Java 25
- Build: Gradle (Groovy DSL, `build.gradle`)
- ORM: Spring Data JPA + QueryDSL 5.1.0
- Security: Spring Security + JWT (JJWT 0.13.0)
- Database: Oracle Database (XEPDB1 / 사용자: ITPAPP)
- API 문서화: SpringDoc OpenAPI 3.0.3 (Swagger UI)
- 유틸: Lombok, Jsoup 1.18.3

## 3. 주요 명령어
- `./gradlew build` — 빌드
- `./gradlew bootRun` — 개발 서버 실행
- `./gradlew test` — 테스트 실행
- `./gradlew clean test` — 전체 테스트 재검증
- `./gradlew clean build` — 클린 빌드
- Swagger UI: http://localhost:8080/swagger-ui/index.html

## 4. 아키텍처

### 4.1 레이어드 아키텍처
```
Controller Layer  →  Service Layer  →  Repository Layer  →  Oracle DB
(REST 엔드포인트)    (비즈니스 로직)    (JPA / QueryDSL)
```

### 4.2 디렉토리 구조 (개요)
```
src/main/java/com/kdb/it/
├── config/        - Spring Security, JPA Auditing, QueryDSL, Swagger, Web 설정
├── common/        - 공통 도메인
│   ├── admin/     - 시스템관리 (ROLE_ADMIN 전용)
│   ├── approval/  - 결재
│   ├── code/      - 공통코드
│   ├── iam/       - 사용자/조직/권한
│   ├── system/    - 인증·보안 (JwtUtil, JwtAuthenticationFilter)
│   └── util/      - 공통 유틸 (CookieUtil, HtmlSanitizer 등)
├── domain/        - 비즈니스 도메인
│   ├── budget/    - 예산 관리 (project, cost, document, plan, status, work)
│   ├── council/   - 정보화실무협의회
│   ├── log/       - 변경 로그 (BaseLogEntity, *L 엔티티)
│   ├── cdp/       - 경력개발
│   ├── audit/     - 감사/이력
│   └── entity/    - BaseEntity
├── exception/     - 전역 예외 핸들러
└── infra/         - 인프라 도메인 (ai, file)
src/main/resources/
└── application.properties
```

도메인별 엔티티 ↔ 테이블 매핑은 → [`docs/guides/data-model.md`](docs/guides/data-model.md) 참조.

## 5. 코딩 스타일 및 가이드라인

### 5.1 공통 원칙
- Lombok 활용: `@Getter`, `@RequiredArgsConstructor`, `@SuperBuilder`, `@NoArgsConstructor`
- 생성자 주입(`@RequiredArgsConstructor` + `private final`)
- 한글 주석 원칙은 루트 `../CLAUDE.md` §4.1 참조.

### 5.2 엔티티 설계
- 모든 업무 엔티티는 **`BaseEntity` 상속** (공통 컬럼: `DEL_YN`, `GUID`, `FST_ENR_DTM/USID`, `LST_CHG_DTM/USID`).
- 삭제는 항상 **Soft Delete**(`delete()` → `DEL_YN='Y'`). 물리 삭제 금지.

### 5.3 DTO 설계
- 관련 DTO는 **정적 중첩 클래스**로 한 파일에 묶음 (예: `AuthDto.LoginRequest`).
- Swagger 문서를 위해 `@Schema(name, description)` 필수.

### 5.4 Repository 패턴
- 기본 CRUD: `JpaRepository` 상속.
- 동적·복잡 쿼리: `RepositoryCustom` 인터페이스 + `RepositoryImpl` 구현(QueryDSL).
- 시퀀스 등 DB 종속 쿼리: `@Query(nativeQuery = true)`.

### 5.5 Service 트랜잭션
- 조회: `@Transactional(readOnly = true)` 필수.
- 쓰기: `@Transactional` (기본 readOnly=false).
- JPA Dirty Checking 활용. 불필요한 `save()` 호출 지양.

### 5.6 인증 및 보안 (전사 SoT)
- 인증 방식: **httpOnly 쿠키 기반 JWT**(Stateless).
- Access Token 유효시간: 15분 (`jwt.access-token-expiration=900000`).
- Refresh Token 유효시간: 7일 (`jwt.refresh-token-expiration=604800000`).
- 비밀번호 암호화: `CustomPasswordEncoder` (SHA-256 + Base64).
- Access/Refresh Token은 `CookieUtil`로 httpOnly 쿠키에 설정.
- 보호 API는 쿠키 자동 전송 기본. `JwtAuthenticationFilter`는 `Authorization: Bearer`를 폴백으로만 허용.
- 공개 엔드포인트: `/api/auth/login`, `/api/auth/signup`, `/api/auth/refresh`, `/swagger-ui/**`, `/v3/api-docs/**`.
- 관리자 전용: `/api/admin/**` — SecurityConfig URL 패턴 + `@PreAuthorize("hasRole('ADMIN')")` 이중 보호.
- RBAC 모델: 자격등급(`CauthI`) + 역할 매핑(`CroleI`).
  - `ITPAD001` = 시스템관리자
  - `ITPZZ001` = 일반사용자
  - `ITPZZ002` = 기획통할담당자
- CORS: `cors.allowed-origins=http://localhost:3000,http://localhost:3002` (개발 프론트).
- 운영: `app.cookie.secure=true` + HTTPS 필수.

### 5.7 채번/주요 비즈니스 제약
- 채번 규칙(관리번호 포맷)은 → [`docs/guides/data-model.md#3-채번-규칙`](docs/guides/data-model.md) 참조.
- 신청 상태가 **"결재중"** 또는 **"결재완료"**인 경우 연결된 프로젝트 수정/삭제 불가.
- 프로젝트 수정 시 품목(`Bitemm`) 동기화: 요청에 포함된 품목은 추가/수정, 누락된 기존 품목은 Soft Delete.
- 검토의견(`Brivgm`)은 문서에 종속. 삭제는 논리 삭제 우선 검토.
- 예산현황 조회는 `BudgetStatusQueryRepository` 집계 쿼리 기준. 화면 요구사항 변경 시 DTO·쿼리 동기 갱신.

### 5.8 환경 설정 키
- JWT: `jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`
- CORS: `cors.allowed-origins`
- 쿠키: `app.cookie.secure`
- 파일: `app.file.base-path=C:/data/files`, multipart 최대 파일 50MB / 요청 200MB
- Gemini: `gemini.api.key`, `gemini.api.base-url`, `gemini.api.model=gemini-2.5-flash`
- 서버 식별자: `app.server.instance-id=SVR1`

### 5.9 테스트 기준
- 기능 변경 후 최소 `./gradlew test` 실행.
- 인증/결재/파일/QueryDSL 집계/변경 로그 등 공통 영향 변경은 `./gradlew clean test`로 재검증.

## 6. API 응답 형식
| 코드 | 의미 |
|------|------|
| `200 OK` | 조회/수정 성공 |
| `201 Created` | 생성 성공 (Location 헤더 포함) |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 비즈니스 로직 오류 |
| `401 Unauthorized` | 인증 실패 (토큰 없음/만료) |
| `403 Forbidden` | 접근 권한 없음 |

## 7. 주석 작성 예시
JavaDoc 표준 양식과 코드 예제는 → [`docs/guides/comment-style.md`](docs/guides/comment-style.md) 참조.
