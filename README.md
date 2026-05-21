# 개발 노트 (Development Notes)

## 1. 프로젝트 개요

**IT Portal** 백엔드 API 서버. 약 3,000명의 임직원을 위한 정보화 예산, 사업, 인력 관리 시스템.

- **구현 기간**: 2026년 초
- **핵심 기능**:
  - 정보화 사업 CRUD 및 결재 프로세스
  - 예산(전산업무비) 관리
  - 정보화실무협의회 협의 및 평가
  - 요구사항 정의서 검토의견 관리
  - 공통 게시판(게시판 메타/게시물/댓글/답변글)
  - 변경 이력 추적(Audit Log)
  - 파일 업로드/다운로드
  - Gemini AI 텍스트 생성 보조
- **배포**: WAR 아티팩트로 Tomcat 기동

## 2. 기술 스택

| 구분 | 기술 | 버전 | 비고 |
|------|------|------|------|
| 언어 | Java | 25 | JDK 25 툴체인 |
| 프레임워크 | Spring Boot | 4.0.5 | Spring Framework 7.0, 경량화 설정 |
| ORM | Spring Data JPA + QueryDSL | 5.1.0 | 동적 쿼리/집계 쿼리용 QueryDSL, SQL Injection 대응(CVE) |
| DB | Oracle Database | XEPDB1 | 사용자: `ITPAPP` |
| 인증 | Spring Security + JWT | JJWT 0.13.0 | **httpOnly 쿠키 기반**, 15분 Access Token / 7일 Refresh Token |
| API 문서 | Springdoc OpenAPI | 3.0.3 | Swagger UI 자동 생성 (`/swagger-ui/index.html`) |
| 빌드 | Gradle (Groovy DSL) | - | `build.gradle` 관리, JaCoCo 70% 커버리지 목표 |
| 유틸 | Lombok, Jsoup | 1.18.3 | 보일러플레이트 제거, 서버 측 HTML XSS 방어 |
| 테스트 | JUnit 5, Mockito, AssertJ | - | 69개 테스트 파일 / 기존 결과 기준 787개 테스트 케이스 |

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

| 결정 | 내용 | 이유 |
|------|------|-----|
| **Soft Delete** | 물리 삭제 대신 `DEL_YN='Y'` 논리 삭제 사용 | 감사 추적(Audit Trail), 실수 복구 가능, 외래키 참조 무결성 유지 |
| **복합키 (`@IdClass`)** | `ProjectId`, `BcostmId`, `BitemmId`, `CdecimId` 등 복합 기본키 정의 | Oracle 테이블 스키마 설계를 JPA 엔티티에 1:1 매핑 |
| **JPA Auditing (`BaseEntity`)** | 모든 업무 엔티티 상속, `@CreatedDate/@LastModifiedDate` 자동 기록 | 누가 언제 생성/수정했는지 자동 추적 |
| **JWT httpOnly 쿠키** | Access Token(15분) + Refresh Token(7일), `CookieUtil`로 관리 | XSS 공격 방어(JavaScript 접근 불가), 자동 전송 편의성 |
| **비밀번호 인코딩** | SHA-256 + Base64 (`CustomPasswordEncoder`) | Oracle 레거시 시스템과의 호환성 |
| **HTML 새니타이징** | `HtmlSanitizer` (Jsoup 기반) 서버 측 XSS 방어 | 프론트엔드 DOMPurify와 이중 방어, 신뢰할 수 없는 사용자 입력 필터링 |
| **게시판 권한 정책** | `Cblbmm` 메타 + 서비스 계층 검증 | 조회/등록 권한, 부서 제한, 공개 기간을 백엔드에서 최종 판단 |
| **Oracle Native Query** | 시퀀스 채번 시 `@Query(nativeQuery=true)` 직접 조회 | 자동 생성 문자열(`BPROJM_0000...`) 포맷 구현 |
| **정적 중첩 DTO** | `AuthDto.LoginRequest`, `ProjectDto.CreateRequest` 등 한 파일 그룹화 | Swagger 문서 가독성, 관련 DTO 응집도 향상 |
| **전역 예외 처리** | `@RestControllerAdvice` 기반 `GlobalExceptionHandler` | 표준화된 오류 응답(`{ timestamp, status, message }`) |
| **Properties 기반 CORS** | `application.properties`의 `cors.allowed-origins` 환경변수 제어 | 운영 배포 시 도메인 재빌드 불필요 |
| **RBAC (자격등급 + 역할)** | `CauthI`(자격등급) + `CroleI`(역할 매핑) + `@PreAuthorize` | 유연한 권한 관리, 운영 중 권한 추가 가능 |
| **관리자 이중 보호** | SecurityConfig URL 패턴(`/api/admin/**`) + 컨트롤러 레벨 `@PreAuthorize("hasRole('ADMIN')")` | 깊이 있는 방어(Defense in Depth), 도메인 API도 명시적 보호 |
| **협의회 통합 컨트롤러** | `CouncilController` 1개 (34개 매핑 메서드) vs 서비스 8개 분리 | 협의회 업무의 통합 흐름 표현, 서비스 계층은 관심사 분리 |
| **변경 로그 (Audit)** | `@PrePersist/@PreUpdate` JPA 리스너로 자동 스냅샷 기록 | 누가 무엇을 언제 변경했는지 추적, 감사/규정 준수 대응 |

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
 ├── Brivgm      (검토의견)
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
│   ├── board/               # 공통 게시판 (BoardMeta/Post/Comment)
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
| 공통게시판 | `BoardMetaController`, `BoardPostController`, `BoardCommentController`, `AdminBoardMetaController` | `BoardMetaService`, `BoardPostService`, `BoardCommentService` | `BoardMetaRepository`, `BoardPostRepository`, `BoardCommentRepository` | `Cblbmm`, `Cblbcm`, `Ccmmtm` |
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
@Table(name = "TPRMPP_BPROJM")
public class Bprojm extends BaseEntity { ... }
```

**현재 로그 대상 엔티티 (23개)**

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
| `cblbcm` | `CblbcmL` | 게시물 |
| `cblbmm` | `CblbmmL` | 게시판 |
| `ccmmtm` | `CcmmtmL` | 게시판 댓글 |

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
- **테이블**: `TPRMPP_CLOGNH`

### 5.3 관리자 로그 조회 (`AdminLogService`) — ROLE_ADMIN 전용

변경 로그 20개 테이블을 관리자 화면에서 페이징·상세 조회합니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/admin/logs/tables` | 조회 가능한 로그 테이블 목록 |
| `GET /api/admin/logs/{key}` | 로그 목록 (페이징, 최대 500건/페이지) |
| `GET /api/admin/logs/{key}/{logSno}` | 로그 상세 (전체 스냅샷) |

사번 필드(`*USID`, `ENO` 등)는 자동으로 사용자명으로 변환하여 응답에 포함합니다.

---

## 6. 인증/인가 및 보안

### 6.1 JWT 인증 흐름

```
[로그인] POST /api/auth/login
  → 사번/비밀번호 검증 (SHA-256 + Base64)
  → TPRMPP_CLOGNH LOGIN_FAILURE 이력 기반 5회/10분 Brute-force 잠금 확인
  → Access Token(15분) + Refresh Token(7일) 발급
  → httpOnly 쿠키(Set-Cookie)로 토큰 전달
  → Clognh 테이블에 로그인 이력 기록

[API 요청] (모든 보호 엔드포인트)
  → JwtAuthenticationFilter
    - 쿠키에서 토큰 추출 (우선)
    - 또는 Authorization: Bearer {token} 폴백
  → JWT 검증 (서명, 만료시간)
  → claims(eno, athIds, bbrC) → CustomUserDetails 생성 (DB 재조회 없음)
  → SecurityContext에 인증 정보 설정
  → Controller 진입

[토큰 갱신] POST /api/auth/refresh
  → httpOnly 쿠키에서 Refresh Token 추출
  → DB 검증 (RefreshTokenRepository)
  → 새 Access Token 쿠키 발급 (유효시간 재설정)

[로그아웃] POST /api/auth/logout
  → RefreshTokenRepository에서 토큰 레코드 삭제
  → 쿠키 만료(maxAge=0)
  → Clognh 테이블에 로그아웃 이력 기록
```

### 6.2 인가 (Authorization) 모델

**RBAC (Role-Based Access Control)**
- **자격등급** (`CauthI` 엔티티): 시스템관리자(ITPAD001), 일반사용자(ITPZZ001), 기획담당(ITPZZ002)
- **역할 매핑** (`CroleI` 엔티티): 자격등급 → Spring Role 변환 (`ITPAD001` → `ROLE_ADMIN`)
- **@PreAuthorize**: 메서드/클래스 레벨에서 `hasRole('ADMIN')` 검사

**관리자 API 보호 규칙**
| 보호 범위 | 방식 | 예시 |
|-----------|------|-----|
| `/api/admin/**` | `SecurityConfig` URL 패턴 + `@PreAuthorize` | AdminController |
| `/api/plans/**` | `PlanController` 클래스 레벨 `@PreAuthorize` | SecurityConfig에는 구 경로 `/api/plan/**`가 남아 있어 정비 필요 |
| `/api/budget/status/**` | `@PreAuthorize` 컨트롤러 레벨만 | BudgetStatusController |
| `/api/budget/work/**` | `@PreAuthorize` 컨트롤러 레벨만 | BudgetWorkController |

> **주의**: SecurityConfig에 등록되지 않은 관리자 API는 반드시 컨트롤러 **클래스 레벨**에 `@PreAuthorize("hasRole('ADMIN')")` 적용. 누락 시 인증된 모든 사용자 접근 가능 → CLAUDE.md §5.6 참조

### 6.3 보안 조치

| 항목 | 기술 | 설명 |
|------|------|------|
| **CSRF 방어** | JWT Stateless | REST API는 CSRF 공격 대상이 아님, Spring Security CSRF 비활성화 |
| **XSS 방어** | httpOnly 쿠키 + HTML 새니타이징 | JavaScript 접근 차단, `HtmlSanitizer`(Jsoup)로 사용자 입력 필터링 |
| **SQL Injection** | 매개변수화 쿼리 | JPA `@Query`/QueryDSL, Native Query는 `@Query(nativeQuery=true)` + 파라미터 바인딩 |
| **HSTS** | SecurityConfig | `max-age=31536000`, `includeSubDomains=true` |
| **CSP** | SecurityConfig | `default-src 'self'`, `script-src 'self'`, XSS 2차 방어선 |
| **비밀번호 저장** | SHA-256 + Base64 | `CustomPasswordEncoder` |
| **환경 비밀값** | 환경변수 주입 | `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` (`DB_PASSWORD`, `JWT_SECRET`은 현재 개발 기본값이 남아 있어 운영 프로파일에서 제거 필요) |

## 7. 주요 API 엔드포인트

### 7.1 공개 엔드포인트 (인증 불필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/login` | 로그인 (사번/비밀번호) |
| POST | `/api/auth/refresh` | Access Token 갱신 (Refresh Token 기반) |
| POST | `/api/auth/sso/complete` | SSO 완료 콜백 (JWT 발급) |
| GET | `/swagger-ui/**` | Swagger UI 문서 |
| GET | `/v3/api-docs/**` | OpenAPI 명세 |

> **회원가입**: `/api/auth/signup` — 관리자 권한 필요 (임직원 포털 특성상 자유 가입 금지)

### 7.2 비즈니스 API (인증 필수)

| 도메인 | Method | Path | 설명 | 권한 |
|--------|--------|------|------|------|
| **정보화사업** | GET/POST | `/api/projects/**` | 사업 CRUD | 일반 |
| | | | 복합키: `prjYy`(연도) + `prjSn`(일련번호) | |
| **전산업무비** | GET/POST | `/api/cost/**` | 비용 항목 CRUD | 일반 |
| | | | 복합키: `costYy` + `costSn` | |
| **결재 신청서** | GET/POST/PUT | `/api/applications/**` | 신청/승인/반려 | 일반 |
| | | | 상태: 임시저장→제출→결재중→결재완료 | |
| **요구사항 정의서** | GET/POST | `/api/documents/**` | 문서 CRUD, 버전 관리 | 일반 |
| | GET/POST/DELETE | `/api/documents/{documentId}/review-comments/**` | 검토의견 추가/삭제 | 일반 |
| **가이드 문서** | GET/POST | `/api/guide-documents/**` | 가이드 CRUD | 일반 |
| **공통 게시판** | GET | `/api/boards/meta/**` | 게시판 메타 조회 | 일반 |
| | GET/POST/PUT/DELETE | `/api/boards/{blbMngNo}/posts/**` | 게시물/답변글 CRUD | 일반 |
| | GET/POST/PUT/DELETE | `/api/boards/{blbMngNo}/posts/{nacMngNo}/comments/**` | 댓글/대댓글 CRUD | 일반 |
| **첨부파일** | POST/GET | `/api/files/**` | 업로드(50MB)/다운로드/미리보기 | 일반 |
| | | | 파일명 생성: `{서버ID}_{UUID}_{원본확장자}` | |
| **협의회 관리** | GET/POST/PUT/PATCH | `/api/council/**` | 신청, 심의, 평가, 일정 (34개 매핑) | 일반 |
| | | | CouncilController 통합 (8개 서비스 분리) | |
| **Gemini AI** | POST | `/api/gemini/generate` | 텍스트 생성 (파일 첨부 가능) | **관리자** |
| **공통코드** | GET/POST/PUT/DELETE | `/api/ccodem/**` | 코드 조회 및 CRUD (캐싱) | 일반 |
| **사용자** | GET | `/api/users/**` | 사용자/조직 조회 | 일반 |
| **로그인 이력** | GET | `/api/login-history/**` | 본인 이력 조회 (최대 50건) | 일반 |
| **관리자** | GET/POST/PUT/DELETE | `/api/admin/**` | 시스템 설정, 로그 조회, 사용자/코드 관리 | **관리자** |
| **게시판 관리** | GET/POST/PUT/DELETE | `/api/admin/boards/meta/**` | 게시판 메타 생성/수정/삭제 | **관리자** |
| **계획 관리** | GET/POST | `/api/plans/**` | 정보기술부문 계획 CRUD | **관리자** |
| **예산현황** | GET | `/api/budget/status/**` | 집계 대시보드 (전체 예산 조회) | **관리자** |
| **예산작업** | GET/POST | `/api/budget/work/**` | 편성률 조회, Upsert, 결과 조회 | **관리자** |

> **Swagger UI**: http://localhost:8080/swagger-ui/index.html

## 8. 빌드 및 실행

```bash
# 1. QueryDSL Q클래스 생성 (필요시)
./gradlew compileJava

# 2. 전체 빌드 + 테스트 + JAR/WAR 생성
./gradlew build

# 3. 개발 서버 기동 (Hot Reload 지원)
./gradlew bootRun
#   → http://localhost:8080
#   → Swagger: http://localhost:8080/swagger-ui/index.html

# 4. 테스트 실행 (69개 테스트 파일 / 기존 결과 기준 787개 케이스)
./gradlew test

# 5. 테스트 커버리지 리포트 생성
./gradlew jacocoTestReport
#   → 리포트: build/reports/jacoco/test/html/index.html
#   → 목표: 70% 라인 커버리지 (JaCoCo 설정)

# 6. 클린 빌드 (의존성 재다운로드)
./gradlew clean build

# 7. WAR 아티팩트 배포 (운영)
#   빌드 결과: build/libs/it-0.0.1-SNAPSHOT.war
#   → Tomcat CATALINA_HOME/webapps 복사
#   → Tomcat 기동
```

## 9. 환경 설정

### 9.1 application.properties 주요 항목

| 속성 | 기본값 | 개발 | 운영 | 설명 |
|------|--------|------|------|------|
| `spring.datasource.url` | - | `jdbc:oracle:thin:@127.0.0.1:1521/XEPDB1` | 프로덕션 접속 정보 | Oracle 접속 URL |
| `spring.datasource.password` | `kdb1234!!` | 로컬값 | 환경변수 `DB_PASSWORD` | DB 비밀번호 (환경변수 우선, 운영 기본값 제거 필요) |
| `jwt.secret` | `kdb-it-secret-key...` | 로컬값 | 환경변수 `JWT_SECRET` (최소 256비트) | JWT 서명 비밀키 (운영 기본값 제거 필요) |
| `jwt.access-token-validity` | `900000` | - | - | Access Token 유효시간 (15분) |
| `jwt.refresh-token-validity` | `604800000` | - | - | Refresh Token 유효시간 (7일) |
| `app.cookie.secure` | `false` | 개발: false | 운영: true | 쿠키 Secure 플래그 (HTTPS 필수) |
| `cors.allowed-origins` | `http://localhost,...` | `http://localhost:3000,http://localhost:3002` | 실제 도메인 | CORS 허용 도메인 (쉼표 구분) |
| `app.server.instance-id` | `SVR1` | `SVR1` | `SVR1` 또는 `SVR2` | 멀티 서버 파일명 충돌 방지 |
| `app.file.base-path` | `C:/data/files` | 로컬 경로 | `/mnt/nas/files` (NAS 공유) | 파일 저장 디렉토리 |
| `spring.servlet.multipart.max-file-size` | `50MB` | - | - | 단일 파일 최대 크기 |
| `spring.servlet.multipart.max-request-size` | `200MB` | - | - | 다건 업로드 최대 크기 |
| `gemini.api.key` | - | 환경변수 `GEMINI_API_KEY` | 환경변수 | Google Gemini API 키 |
| `gemini.api.model` | `gemini-2.5-flash` | - | - | Gemini 모델 선택 |

### 9.2 보안 설정

| 항목 | 설정 | 비고 |
|------|------|------|
| **CSRF** | 비활성화 (`http.csrf(disable)`) | Stateless JWT REST API는 CSRF 불필요 |
| **CORS** | `corsConfigurationSource()` | `allowCredentials(true)`, 쿠키 자동 전송 |
| **세션** | STATELESS (`SessionCreationPolicy.STATELESS`) | JWT 토큰으로 상태 관리 |
| **HTTP 헤더** | 보안 헤더 자동 설정 | HSTS, CSP, X-Frame-Options, Content-Type-Options |
| **환경변수** | 비밀값은 환경변수에서 주입 | `application.properties`의 개발 기본값은 운영 프로파일에서 제거 |

### 9.3 로컬 개발 환경 설정

```bash
# Windows (PowerShell)
$env:DB_PASSWORD = "kdb1234!!"
$env:JWT_SECRET = "your-256-bit-secret-key-at-least-32-characters"
$env:GEMINI_API_KEY = "your-gemini-api-key"

# macOS/Linux (bash)
export DB_PASSWORD=kdb1234!!
export JWT_SECRET=your-256-bit-secret-key-at-least-32-characters
export GEMINI_API_KEY=your-gemini-api-key
```

### 9.4 운영 배포 설정

1. **application.properties** 운영값 적용
2. **환경변수** 주입:
   - `DB_PASSWORD`: 운영 DB 비밀번호
   - `JWT_SECRET`: 운영 JWT 키 (256비트 이상)
   - `GEMINI_API_KEY`: Gemini API 키
3. **Secure 플래그 활성화**: `app.cookie.secure=true`
4. **CORS 도메인 제한**: `cors.allowed-origins=https://itportal.kdb.com` (실제 도메인)
5. **파일 저장 경로**: NAS 공유 폴더 지정 (`/mnt/nas/files` 등)
6. **WAR 배포**: Tomcat CATALINA_HOME/webapps 디렉토리에 복사

## 10. 외부 연동

### 10.1 Gemini AI

- **API**: `POST /api/gemini/generate` (인증 필수)
- **기능**: 텍스트 생성, 첨부파일 inlineData 변환, 미지원/누락 파일 `skippedFiles` 응답
- **설정**: `gemini.api.key`, `gemini.api.model=gemini-2.5-flash`, `gemini.api.base-url`
- **구현**: `GeminiService`, `GeminiController`
- **보안**: API 키는 환경변수 `GEMINI_API_KEY`에서 주입

### 10.2 SSO (Single Sign-On) 에이전트

- **상태**: 선택적 (JSP 에이전트 라이브러리 libs/ 폴더에 복사 후 주석 해제)
- **엔드포인트**: `/api/auth/sso/complete` (JWT 발급)
- **구성**: `SsoWebConfig`, `SsoController`
- **기능**: 사내 SSO 시스템과 연동하여 JWT 토큰 발급

### 10.3 Oracle Database

- **버전**: XEPDB1 (Oracle Database 21c XE)
- **사용자**: `ITPAPP`
- **특성**: Soft Delete 패턴, 복합키 `@IdClass`, 시퀀스 기반 채번
- **변경 이력**: 자동 감사로그 (`*L` 테이블) 기록

---

## 11. 핵심 도메인 및 의존성

### 11.1 도메인 의존성 규칙

```
domain → common (O)
common → domain (X)

infra → common (O)
common → infra (X)

infra → domain (X, domain 기능 불필요)
```

### 11.2 도메인별 핵심 클래스

| 도메인 | Entity | Service | Repository | 설명 |
|--------|--------|---------|------------|------|
| **budget.project** | Bprojm, Bitemm | ProjectService | ProjectRepository(+Custom) | 정보화사업 및 비목 |
| **budget.cost** | Bcostm, Btermm | CostService | CostRepository(+Custom) | 전산관리비 및 단말기 |
| **budget.document** | Bgdocm, Brdocm, Brivgm | GuideDocService, ServiceRequestDocService, ReviewCommentService | GuideDocRepository, ServiceRequestDocRepository, BrivgmRepository | 가이드·요구사항·검토의견 |
| **budget.plan** | Bplanm, Bproja | PlanService | BplanmRepository, BprojaRepository | 정보기술부문 계획 |
| **budget.status** | - (집계) | BudgetStatusService | BudgetStatusQueryRepository | 예산현황 대시보드 |
| **budget.work** | Bbugtm | BudgetWorkService | BbugtmRepository(+Custom) | 예산 편성률 |
| **council** | Basctm, Bschdm, Bchklc, Bcmmtm, Bevalm, Bperfm, Bpovwm, Bpqnam, Brsltm | CouncilService(+7개 세부) | 9개 Repository | 정보화실무협의회 |
| **log** | BaseLogEntity, *L | - | EntityManager 직접 | 자동 감시로그 |
| **common.system** | CuserI, Crtokm, Clognh | AuthService, CustomUserDetailsService, LoginHistoryService | UserRepository, RefreshTokenRepository, LoginHistoryRepository | 인증 및 사용자 |
| **common.approval** | Capplm, Cappla, Cdecim | ApplicationService | ApplicationRepository(+Map, Approver) | 신청 및 결재 |
| **common.board** | Cblbmm, Cblbcm, Ccmmtm | BoardMetaService, BoardPostService, BoardCommentService | BoardMetaRepository, BoardPostRepository, BoardCommentRepository | 공통 게시판 |
| **common.iam** | CorgnI, CauthI, CroleI | UserService, OrganizationService | UserRepository, OrganizationRepository | 사용자/조직/권한 |
| **common.code** | Ccodem | CodeService | CodeRepository(+Custom) | 공통코드 (캐싱) |
| **common.admin** | - (기존 활용) | AdminService, AdminLogService | - | 시스템 관리 및 로그 |
| **infra.file** | Cfilem | FileService | FileRepository | 첨부파일 |
| **infra.ai** | - | GeminiService | FileRepository | Gemini 프록시 |

### 11.2.1 부서 필터링 패턴 (bbrC) — 재발 방지

신규 목록 API 추가 시 아래 패턴을 반드시 따릅니다.

| 계층 | 추가 내용 |
|------|----------|
| Controller | `@RequestParam(required = false) String bbrC` |
| Service | `getList(@Nullable String bbrC)` 시그니처 |
| RepositoryImpl | `if (StringUtils.hasText(bbrC)) builder.and(entity.bbrC.eq(bbrC))` |

- `bbrC` null·빈 문자열 → 전체 조회 (관리자 포함, 하위 호환 유지).
- TDD 의무: `bbrC` 지정·null 두 케이스 모두 JUnit 테스트 추가.

### 11.2.2 TDD 의무 범위

신규 Service / RepositoryImpl 로직은 **RED → GREEN → REFACTOR** 순서로 작성합니다.

```
RED   — 실패하는 JUnit 테스트 먼저 작성
GREEN — 테스트를 통과하는 최소 구현 작성
REFACTOR — 중복 제거, 가독성 개선 (테스트 통과 유지)
```

### 11.3 공통 의존성

| 패키지 | 목적 |
|--------|------|
| **config** | Spring Security, JPA, QueryDSL, Swagger, SSO, Jackson 설정 |
| **exception** | 전역 예외 처리 (`GlobalExceptionHandler`) |
| **common.util** | CookieUtil, HtmlSanitizer, CustomPasswordEncoder, CustomUserDetails |
| **common.system.security** | JwtUtil, JwtAuthenticationFilter |

---

## 12. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| **2026-05-19** | REVIEW 재점검 결과 반영: 로그인 이력 JavaDoc 위치, `Bcostm` 깨진 한글 주석, Gemini 트랜잭션 경계 설명, 게시판 QueryDSL 구현체 조회 의도 주석 보강 |
| **2026-05-14** | 공통 게시판 모듈(`common/board`)과 게시판 API, 감사로그 대상 23개, DB 로그인 이력 기반 Brute-force 설명을 문서에 반영 |
| **2026-05-10** | 로컬 개발 포트를 실제 설정 기준(백엔드 8080)으로 정정. 비밀값 기본값은 아직 `application.properties`에 남아 있어 운영 프로파일 제거 과제로 재분류 |
| **2026-05-09** | README.md 대폭 개선: 프로젝트 개요 강화, 설계 결정 이유 추가, 인증/보안 섹션 분리, API 엔드포인트 도메인별 정렬, 환경 설정 테이블화, 외부 연동 문서화, 도메인 의존성 규칙 명시 |
| 2026-05-09 | `PlanController`, `BudgetStatusController`, `BudgetWorkController`에 `@PreAuthorize("hasRole('ADMIN')")` 추가. API 엔드포인트 테이블 인증 컬럼 현행화. 관리자 도메인 API 보호 규칙 CLAUDE.md §5.6·README §6.2에 명문화 |
| 2026-04-30 | README 로그 체계 섹션 추가: 변경 로그(AuditLog), 로그인 이력, 관리자 로그 조회 구조 문서화 |
| 2026-04-29 | README 현행화: Spring Boot/JJWT/Springdoc 버전, 15분 Access Token, 예산현황·검토의견·변경로그 도메인, 테스트/환경 설정 반영 |
| 2026-04-10 | 전체 프로젝트 문서/주석 리프레시 (README/CLAUDE/TASK.md 최신화, AdminController JavaDoc 보강) |
| 2026-04-05 | 정보화실무협의회(council) 도메인 구현: CouncilController(현재 34개 매핑), 8개 서비스, 14개 엔티티, 9개 Repository |
| 2026-04-04 | 시스템관리(admin) 모듈 구현: AdminController/AdminService, @PreAuthorize ROLE_ADMIN 이중 보호 |
| 2026-04-04 | 예산작업(budget/work) 구현: BudgetWorkController(3 API), Bbugtm 엔티티, 편성률 Upsert |
| 2026-04-02 | 정보기술부문 계획(budget/plan) 구현: PlanController, Bplanm/Bproja 엔티티, JSON 스냅샷 저장 |
| 2026-03-30 | JPA 3.2 `@Table`, `@Column` 주석 표기 적용하여 문서화 자동화 및 스키마 직관성 강화 |
| 2026-03-26~27 | 도메인 기반 레이어드 아키텍처 리팩토링: flat 패키지 → common/budget/infra 3개 도메인 분리, 33개 클래스 리네이밍, 테스트 파일 도메인 패키지 이동 |
| 2026-03-25 | 전체 프로젝트 문서화 리프레시: 소스 코드 주석 전수 점검(81개 파일), README.md 최신화 |
| 2026-03-22 | Tiptap 에디터 호환성 수정, `HtmlSanitizer` 테이블 태그 허용 확대 |
| 2026-03-14 | 요구사항 정의서 테이블 포맷 보존 수정, TOC 스크롤 기능 |
| 2026-03-09 | httpOnly 쿠키 인증 전환 (`CookieUtil`, `JwtAuthenticationFilter` 쿠키 우선 추출) |
| 2026-03-08 | `GeminiService` 파일 첨부 지원, 파일 일괄 업로드 API 추가 |
| 2026-03-04 | `GlobalExceptionHandler` 추가, CORS properties 기반 전환, 로깅 표준화 |
| 2026-03-03 | Swagger 어노테이션 추가, JavaDoc 보강 |
| 2026-03-02 | 추진부서 필드 추가, 응답 필드 확대 |

---
