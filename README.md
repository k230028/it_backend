# 개발 노트 (Development Notes)

## 1. 프로젝트 개요

IT Portal의 백엔드 API 서버로, 정보화 예산, 사업, 인력을 관리하는 시스템입니다.

## 2. 기술 스택

| 구분 | 기술 | 버전 | 비고 |
|------|------|------|------|
| 언어 | Java | 25 | - |
| 프레임워크 | Spring Boot | 4.0.5 | Spring Framework 7.0 기반 |
| DB | Oracle Database | - | Native Query(시퀀스 채번) 사용 |
| ORM | Spring Data JPA + QueryDSL | QueryDSL 5.1.0 | 동적 쿼리용 QueryDSL 병행 |
| 인증 | Spring Security + JWT | JJWT 0.13.0 | httpOnly 쿠키 방식 (XSS 방어) |
| API 문서 | Springdoc OpenAPI | 3.0.3 | Swagger UI 자동 생성 |
| 빌드 | Gradle (Groovy DSL) | - | `build.gradle` 기반 |
| 유틸 | Lombok, Jsoup | Jsoup 1.18.3 | 보일러플레이트 제거, HTML 새니타이징 |

## 3. 아키텍처

### 3.1 계층 구조 (Layered Architecture)

```
Controller → Service → Repository → DB (Oracle)
    ↓            ↓          ↓
   DTO        Entity     JPA/QueryDSL
```

- **Controller**: REST API 엔드포인트 정의, 요청/응답 처리
- **Service**: 비즈니스 로직, 트랜잭션 관리 (`@Transactional`)
- **Repository**: 데이터 접근 (JPA + QueryDSL 커스텀 구현)
- **DTO**: 계층 간 데이터 전달 (정적 중첩 클래스 패턴)

### 3.2 주요 설계 결정

| 결정 | 내용 |
|------|------|
| **Soft Delete** | 물리 삭제 대신 `DEL_YN='Y'` 논리 삭제 사용 |
| **복합키** | `@IdClass` 방식으로 복합 기본키 정의 (`ProjectId`, `BcostmId`, `BitemmId`, `CdecimId`) |
| **JPA Auditing** | `BaseEntity`에서 생성/수정 일시·사용자 자동 관리 |
| **JWT 인증** | httpOnly 쿠키 기반 Access Token(15분) + Refresh Token(7일), `CookieUtil`로 관리 |
| **비밀번호** | SHA-256 + Base64 인코딩 (`CustomPasswordEncoder`) |
| **HTML 새니타이징** | `HtmlSanitizer` (Jsoup 기반)으로 서버 측 XSS 방어, 프론트엔드 DOMPurify와 이중 방어 |
| **Oracle 시퀀스** | 관리번호 채번에 Native Query로 Oracle 시퀀스 사용 |
| **DTO 패턴** | 정적 중첩 클래스(Static Nested Class)로 관련 DTO 그룹화 |
| **전역 예외 처리** | `@RestControllerAdvice` 기반 `GlobalExceptionHandler`로 표준 오류 응답 |
| **CORS** | `application.properties`의 `cors.allowed-origins` 속성으로 환경별 제어 |
| **RBAC** | 자격등급(CauthI) + 역할(CroleI) 기반 접근 제어, `@PreAuthorize` 메서드 레벨 보호 |
| **관리자 이중 보호** | SecurityConfig URL 패턴 + `@PreAuthorize("hasRole('ADMIN')")` 컨트롤러 레벨 이중 적용 |
| **협의회 단일 컨트롤러** | 정보화실무협의회의 23개 엔드포인트를 `CouncilController` 하나에서 관리 (서비스는 8개로 분리) |

### 3.3 BaseEntity 상속 구조

```
BaseEntity (추상 클래스)
 ├── Bprojm      (정보화사업)
 ├── Bcostm      (전산관리비)
 ├── Bitemm      (품목)
 ├── Btermm      (단말기)
 ├── Bplanm      (정보기술부문 계획)
 ├── Bproja      (계획-사업 연결)
 ├── Bbugtm      (예산 작업/편성률)
 ├── Basctm      (협의회 심의과제)
 ├── Bchklc      (타당성 검토항목)
 ├── Bcmmtm      (평가위원)
 ├── Bevalm      (평가의견)
 ├── Bperfm      (성과지표)
 ├── Bpovwm      (사업개요)
 ├── Bpqnam      (사전질의응답)
 ├── Brsltm      (결과서)
 ├── Bschdm      (일정)
 ├── Capplm      (신청서 마스터)
 ├── Cappla      (신청서-원본 관계)
 ├── Cdecim      (결재 정보)
 ├── Ccodem      (공통코드)
 ├── CorgnI      (조직)
 ├── CuserI      (사용자)
 ├── Bgdocm      (가이드 문서)
 ├── Brdocm      (요구사항 정의서)
 ├── Cfilem      (첨부파일)
 ├── CauthI      (자격등급)
 ├── CroleI      (역할 매핑)
 ├── Clognh      (로그인 이력)
 └── Crtokm      (JWT Refresh Token)

BaseLogEntity (변경 로그 추상 클래스)
 └── BprojmL, BcostmL, BrdocmL 등 원본 엔티티별 로그 스냅샷
```

## 4. 모듈 관계

### 4.1 패키지 구조

2026-04-29 기준 도메인 기반 레이어드 아키텍처와 예산·결재·변경 로그 모듈 구조를 반영합니다.

```
com.kdb.it
├── config/                  # 전역 설정 (Security, JPA, Jackson, QueryDSL, Swagger, Web) — 6개
├── exception/               # 전역 예외 핸들러 + 커스텀 예외 — 2개
├── common/
│   ├── system/              # 인증·로그인 (AuthController, AuthService, JwtUtil, JwtAuthenticationFilter)
│   ├── iam/                 # 사용자·조직·권한 (UserController, OrganizationController, UserRepository)
│   ├── approval/            # 신청서·결재 (ApplicationController, ApplicationService, ApplicationMapRepository)
│   ├── admin/               # 시스템관리 (AdminController, AdminService — ROLE_ADMIN 전용)
│   ├── code/                # 공통 코드 (CodeController, CodeService, CodeRepository)
│   └── util/                # 공통 유틸 (CustomPasswordEncoder, CookieUtil, HtmlSanitizer)
├── domain/                  # 비즈니스 도메인 집합
│   ├── budget/              # 예산 관리
│   │   ├── project/         # 정보화사업 (ProjectController, ProjectService, Bprojm)
│   │   ├── cost/            # 전산업무비 (CostController, CostService, Bcostm, Btermm)
│   │   ├── document/        # 문서·검토의견 (GuideDocController, ServiceRequestDocController, ReviewCommentController)
│   │   ├── plan/            # 정보기술부문 계획 (PlanController, PlanService, Bplanm, Bproja)
│   │   ├── status/          # 예산현황 대시보드 (BudgetStatusController, BudgetStatusService)
│   │   └── work/            # 예산 작업 (BudgetWorkController, BudgetWorkService, Bbugtm)
│   ├── council/             # 정보화실무협의회 (CouncilController, 8개 서비스, 9개 Repository)
│   ├── log/                 # 변경 로그 (BaseLogEntity, *L 로그 엔티티, ChangeLogEntityListener)
│   ├── cdp/                 # 경력개발 (빈 디렉토리)
│   ├── audit/               # 감사/이력 (빈 디렉토리)
│   └── entity/              # BaseEntity
└── infra/
    ├── file/                # 파일 관리 (FileController, FileService, FileRepository, Cfilem)
    └── ai/                  # Gemini AI (GeminiController, GeminiService)
```

**의존성 규칙 (단방향)**
```
domain → common (O)   infra → common (O)
common → domain (X)   common → infra  (X)
```

### 4.2 도메인 모듈 관계

| 도메인 | Controller | Service | Repository | Entity |
|--------|-----------|---------|------------|--------|
| 정보화사업 | `ProjectController` | `ProjectService` | `ProjectRepository`, `ProjectItemRepository` | `Bprojm`, `Bitemm` |
| 전산업무비 | `CostController` | `CostService` | `CostRepository` + Custom | `Bcostm`, `Btermm` |
| 가이드문서 | `GuideDocController` | `GuideDocService` | `GuideDocRepository` | `Bgdocm` |
| 요구사항정의서 | `ServiceRequestDocController` | `ServiceRequestDocService` | `ServiceRequestDocRepository` | `Brdocm` |
| 검토의견 | `ReviewCommentController` | `ReviewCommentService` | `BrivgmRepository` | `Brivgm` |
| 정보기술부문계획 | `PlanController` | `PlanService` | `BplanmRepository`, `BprojaRepository` | `Bplanm`, `Bproja` |
| 예산현황 | `BudgetStatusController` | `BudgetStatusService` | `BudgetStatusQueryRepository` | - |
| 예산작업 | `BudgetWorkController` | `BudgetWorkService` | `BbugtmRepository` + Custom | `Bbugtm` |
| 정보화실무협의회 | `CouncilController` | `CouncilService` 외 7개 | `CouncilRepository` 외 8개 | `Basctm` 외 13개 |
| 신청서(결재) | `ApplicationController` | `ApplicationService` | `ApplicationRepository`, `ApplicationMapRepository`, `ApproverRepository` | `Capplm`, `Cappla`, `Cdecim` |
| 인증 | `AuthController` | `AuthService` | `UserRepository`, `RefreshTokenRepository`, `LoginHistoryRepository` | `CuserI`, `Crtokm`, `Clognh` |
| 공통코드 | `CodeController` | `CodeService` | `CodeRepository` + Custom | `Ccodem` |
| 시스템관리 | `AdminController` | `AdminService` | (기존 Repository 활용) | (기존 Entity 활용) |
| 사용자 | `UserController` | `UserService` | `UserRepository` | `CuserI` |
| 조직 | `OrganizationController` | `OrganizationService` | `OrganizationRepository` | `CorgnI` |
| 첨부파일 | `FileController` | `FileService` | `FileRepository` | `Cfilem` |
| Gemini AI | `GeminiController` | `GeminiService` | `FileRepository` (파일 첨부) | - |
| 로그인이력 | `LoginHistoryController` | `LoginHistoryService` | `LoginHistoryRepository` | `Clognh` |
| 변경로그 | - | `ChangeLogEntityListener`, `AuditLogPersister` | `EntityManager` 직접 저장 | `BaseLogEntity` 하위 `*L` 엔티티 |

## 5. 로그 체계

IT Portal의 로그는 **3가지 유형**으로 구성되며, 각각 다른 계층에서 처리됩니다.

### 5.1 변경 로그 (Audit Log) — 자동 기록

엔티티 CUD 이벤트를 JPA 리스너로 자동 캡처하여 `*L` 로그 테이블에 스냅샷을 남깁니다.

```
엔티티 저장/수정 (@PrePersist / @PreUpdate)
  → ChangeLogEntityListener         [JPA EntityListener, Spring 빈 아님]
  → AuditLogPersister.persist()     [Spring @Component, 현재 트랜잭션 내 EntityManager로 INSERT]
  → *L 로그 엔티티 (BaseLogEntity 상속)
```

**핵심 설계 포인트**

| 항목 | 내용 |
|------|------|
| 트리거 | `@PrePersist` / `@PreUpdate` (Post 콜백 대신 Pre 사용 → Hibernate ActionQueue ConcurrentModificationException 방지) |
| 이중 기록 방지 | `ThreadLocal<Set<Object>> inFlightEntities` (identity 비교)로 동일 flush 사이클 1회만 기록 |
| 실패 격리 | 로그 INSERT 실패 시 예외를 삼켜 본 업무 트랜잭션이 롤백되지 않도록 처리 (`log.warn` 출력) |
| PK 생성 | `AuditLogIdGenerator` → Oracle `S_{Postfix}.NEXTVAL` 조회 → `"{Postfix}_{22자리 0패딩}"` 형식 |
| 변경유형 | `C`(생성) / `U`(수정) / `D`(논리삭제, `DEL_YN='Y'` 판별) |
| 변경자 | `SecurityContext`에서 추출한 현재 사용자 사번 자동 기록 |

**`@LogTarget` 어노테이션으로 로그 대상 지정**

```java
@LogTarget(entity = BprojmL.class)
@Entity
@Table(name = "TAAABB_BPROJM")
public class Bprojm extends BaseEntity { ... }
```

**현재 로그 대상 엔티티 (20개)**

| 키 | 로그 엔티티 | 설명 |
|----|-----------|------|
| `basctm` | `BasctmL` | 정보화실무협의회 신청 |
| `bbugt` | `BbugtL` | 예산 편성 |
| `bchklc` | `BchklcL` | 체크리스트 |
| `bcmmtm` | `BcmmtmL` | 협의회 위원 |
| `bcostm` | `BcostmL` | 전산업무비 |
| `bevalm` | `BevalmL` | 평가 |
| `bgdocm` | `BgdocmL` | 가이드 문서 |
| `bitemm` | `BitemmL` | 사업 비목 |
| `bperfm` | `BperfmL` | 성과평가 |
| `bplanm` | `BplanmL` | 정보기술부문 계획 |
| `bpovwm` | `BpovwmL` | 관점/배점 |
| `bpqnam` | `BpqnamL` | 질의응답 |
| `bprojm` | `BprojmL` | 정보화사업 |
| `brdocm` | `BrdocmL` | 요구사항 문서 |
| `brivgm` | `BrivgmL` | 검토의견 |
| `brsltm` | `BrsltmL` | 심의결과 |
| `bschdm` | `BschdmL` | 협의회 일정 |
| `btermm` | `BtermmL` | 단말기 상세 |
| `capplm` | `CapplmL` | 전자결재 |
| `ccodem` | `CcodemL` | 공통코드 |

**`BaseLogEntity` 공통 필드**

| 컬럼 | 설명 |
|------|------|
| `LOG_SNO` | PK (`{Postfix}_{22자리 시퀀스}`, 예: `BPROJL_0000000000000000000001`) |
| `CHG_TP` | 변경유형 (`C`/`U`/`D`) |
| `CHG_DTM` | 변경일시 |
| `CHG_USID` | 변경자 사번 |
| `DEL_YN`, `GUID`, `FST_ENR_DTM` 등 | `BaseEntity` 스냅샷 필드 (리플렉션 복사) |

**새 엔티티에 변경 로그 추가하는 방법**

1. `BaseLogEntity`를 상속하는 `{엔티티명}L` 클래스 생성 (원본과 동일한 `@Column` 필드 복사)
2. Oracle에 `S_{테이블Postfix}` 시퀀스 생성
3. 원본 엔티티에 `@LogTarget(entity = {엔티티명}L.class)` 추가
4. `AdminLogService.buildDefinitions()`에 항목 추가 (관리자 화면 노출)

### 5.2 로그인 이력 (Login History) — 명시적 기록

인증 흐름 중 `AuthService`가 `Clognh` 엔티티에 직접 저장합니다. 변경 로그와 달리 AOP/리스너 없이 서비스 코드에서 명시적으로 기록합니다.

| 이력 유형 | 기록 시점 |
|----------|---------|
| `LOGIN_SUCCESS` | 로그인 성공 후 |
| `LOGIN_FAILURE` | 비밀번호 불일치 시 |
| `LOGOUT` | 로그아웃 처리 후 |

- **조회**: `LoginHistoryService` — 본인 이력 최대 50건(`getLoginHistory`) 또는 최근 10건(`getRecentLoginHistory`)
- **테이블**: `TAAABB_CLOGNH`

### 5.3 관리자 로그 조회 (`AdminLogService`) — ROLE_ADMIN 전용

변경 로그 20개 테이블을 관리자 화면에서 페이징·상세 조회합니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/admin/logs/tables` | 조회 가능한 로그 테이블 목록 |
| `GET /api/admin/logs/{key}` | 로그 목록 (페이징, 최대 500건/페이지) |
| `GET /api/admin/logs/{key}/{logSno}` | 로그 상세 (전체 스냅샷) |

사번 필드(`*USID`, `ENO` 등)는 자동으로 사용자명으로 변환하여 응답에 포함합니다.

---

## 6. 인증/인가 흐름

```
[로그인] POST /api/auth/login
  → 사번/비밀번호 검증 → Access Token + Refresh Token 발급
  → httpOnly 쿠키(Set-Cookie)로 토큰 전달 → 로그인 이력 기록

[API 요청] httpOnly 쿠키 자동 전송 (또는 Authorization: Bearer {token} 폴백)
  → JwtAuthenticationFilter → 쿠키/헤더에서 토큰 추출 → 검증
  → JWT claims(eno, athIds, bbrC)로 CustomUserDetails 생성 (DB 재조회 없음)
  → SecurityContext에 인증 정보 설정 → Controller 진입

[토큰 갱신] POST /api/auth/refresh
  → httpOnly 쿠키의 Refresh Token 검증 → 새 Access Token 쿠키 발급

[로그아웃] POST /api/auth/logout
  → DB에서 Refresh Token 삭제 → 쿠키 만료(maxAge=0) → 로그아웃 이력 기록
```

## 7. 주요 API 엔드포인트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/auth/login` | 로그인 | 불필요 |
| POST | `/api/auth/signup` | 회원가입 | 불필요 |
| POST | `/api/auth/refresh` | 토큰 갱신 | 불필요 |
| GET/POST | `/api/projects/**` | 정보화사업 CRUD | 필요 |
| GET/POST | `/api/costs/**` | 전산관리비 CRUD | 필요 |
| GET/POST | `/api/applications/**` | 신청서(결재) 관리 | 필요 |
| GET/POST | `/api/documents/**` | 요구사항 정의서 CRUD | 필요 |
| GET/POST | `/api/documents/{documentId}/review-comments/**` | 요구사항 정의서 검토의견 CRUD | 필요 |
| GET/POST | `/api/guide-documents/**` | 가이드 문서 CRUD | 필요 |
| GET/POST | `/api/plans/**` | 정보기술부문 계획 CRUD | 필요 |
| GET | `/api/budget/status/**` | 예산현황 집계·대시보드 조회 | 필요 |
| GET/POST | `/api/budget/work/**` | 예산 편성률 적용 (비목조회/적용/결과) | 필요 |
| GET/POST/PUT/PATCH | `/api/council/**` | 정보화실무협의회 (23개 엔드포인트) | 필요 |
| GET/POST/PUT/DELETE | `/api/admin/**` | 시스템 관리 (ROLE_ADMIN 전용) | 필요 (관리자) |
| GET/POST | `/api/files/**` | 첨부파일 업로드/다운로드/미리보기 | 필요 |
| POST | `/api/gemini/generate` | Gemini AI 프록시 | 필요 |
| GET | `/api/ccodem/**` | 공통코드 조회 | 필요 |
| GET | `/api/users/**` | 사용자 조회 | 필요 |
| GET | `/api/organizations` | 조직 목록 조회 | 필요 |
| GET | `/api/login-history/**` | 로그인 이력 조회 | 필요 |

> Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 8. 빌드 및 실행

```bash
# QueryDSL 어노테이션 프로세서 실행 (필요시)
./gradlew compileJava

# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트 (2026-04-29 기준 35개 테스트 파일 / 291개 케이스)
./gradlew test
```

## 9. 환경 설정

- `application.properties`: DB 접속 정보, JWT 비밀키/유효시간, CORS 도메인, 쿠키, 파일, Gemini 설정
- CORS: `cors.allowed-origins=http://localhost:3000,http://localhost:3002` 기준으로 로컬 프론트엔드 허용
- CSRF: Stateless JWT 방식으로 비활성화
- 쿠키: `app.cookie.secure` 속성으로 Secure 플래그 제어 (개발=false, 운영=true)
- JWT: `jwt.access-token-expiration=900000`(15분), `jwt.refresh-token-expiration=604800000`(7일)
- Gemini AI: `gemini.api.key`, `gemini.api.base-url`, `gemini.api.model=gemini-2.5-flash` 설정
- 파일 업로드: `app.file.base-path=C:/data/files`, multipart 최대 50MB/요청 200MB
- 서버 식별자: `app.server.instance-id=SVR1`로 파일 ID 등 서버 구분값 관리

## 10. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-04-30 | README 로그 체계 섹션 추가: 변경 로그(AuditLog), 로그인 이력, 관리자 로그 조회 구조 문서화 |
| 2026-04-29 | README 현행화: Spring Boot/JJWT/Springdoc 버전, 15분 Access Token, 예산현황·검토의견·변경로그 도메인, 테스트/환경 설정 반영 |
| 2026-04-10 | 전체 프로젝트 문서/주석 리프레시 (README/CLAUDE/TASK.md 최신화, AdminController JavaDoc 보강) |
| 2026-04-05 | 정보화실무협의회(council) 도메인 구현: CouncilController(23 엔드포인트), 8개 서비스, 14개 엔티티, 9개 Repository |
| 2026-04-04 | 시스템관리(admin) 모듈 구현: AdminController/AdminService, @PreAuthorize ROLE_ADMIN 이중 보호 |
| 2026-04-04 | 예산작업(budget/work) 구현: BudgetWorkController(3 API), Bbugtm 엔티티, 편성률 Upsert |
| 2026-04-02 | 정보기술부문 계획(budget/plan) 구현: PlanController, Bplanm/Bproja 엔티티, JSON 스냅샷 저장 |
| 2026-03-30 | JPA 3.2 `@Table`, `@Column` 주석 표기 적용하여 문서화 자동화 및 스키마 직관성 강화 |
| 2026-03-26~27 | **도메인 기반 레이어드 아키텍처 리팩토링**: flat 패키지 → common/budget/infra 3개 도메인 분리, 33개 클래스 리네이밍(CodeController, FileRepository 등), 테스트 파일 도메인 패키지 이동, Match Rate 100% |
| 2026-03-25 | 전체 프로젝트 문서화 리프레시: 소스 코드 주석 전수 점검(81개 파일), README.md 최신화 |
| 2026-03-22 | Tiptap 에디터 관련 수정, `HtmlSanitizer` 테이블 태그 허용 확대 |
| 2026-03-14 | 요구사항 정의서 테이블 포맷 보존 수정, TOC 스크롤 기능 |
| 2026-03-09 | httpOnly 쿠키 인증 전환 (`CookieUtil`, `JwtAuthenticationFilter` 쿠키 우선 추출) |
| 2026-03-08 | `GeminiService` 파일 첨부 지원, `CfilemController` 일괄 업로드 API 추가 |
| 2026-03-04 | `GlobalExceptionHandler` 신규 추가, CORS `properties` 기반 전환 |
| 2026-03-03 | `CcodemDto` Swagger 어노테이션 추가, `CcodemRepositoryImpl` JavaDoc 보강 |
| 2026-03-02 | `Bcostm` 추진부서(`PUL_DPM`) 필드 추가, `Project` 응답에 부서명/사용자명 포함 |
