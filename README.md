# 개발 노트 (Development Notes)

## 1. 프로젝트 개요

**IT Portal** 백엔드 API 서버. 약 3,000명의 임직원을 위한 정보화 예산, 사업, 인력 관리 시스템.

- **구현 기간**: 2026년 초
- **핵심 기능**:
  - 정보화 사업 CRUD 및 결재 프로세스
  - 예산(전산업무비, IT부문 예산) 관리
  - 정보화실무협의회 협의 및 평가
  - 요구사항 정의서 검토의견 관리
  - 공통 게시판(게시판 메타/게시물/댓글/답변글)
  - 변경 이력 추적(Audit Log)
  - 파일 업로드/다운로드
  - Tiptap 에디터 변수 토큰 시스템
  - 실시간 알림 (인앱, Phase 2 예정: 이메일/SMS/알림톡)
  - 실시간 로그 모니터링(관리자용)
  - DB 기반 메뉴 트리 및 라우트 카탈로그 관리
  - Gemini AI 텍스트 생성 보조
- **배포**: WAR 아티팩트로 Tomcat 기동
- **소스 코드**: 347개 메인 Java 파일, 116개 테스트 파일, 77개 JPA 엔티티(`@Entity` 기준)

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
| 테스트 | JUnit 5, Mockito, AssertJ | - | 116개 테스트 파일 |

## 2.5 빠른 시작 (Quick Start)

### 환경 준비

```bash
# 1. 로컬 Oracle DB 접속 확인
.\it_database\connect-db.ps1

# 2. 환경변수 설정 (Windows PowerShell)
$env:DB_PASSWORD = "your-db-password"
$env:JWT_SECRET = "your-jwt-secret-key"
$env:GEMINI_API_KEY = "your-gemini-api-key"  # 필요시

# 3. 빌드 및 실행
cd it_backend
./gradlew clean build
./gradlew bootRun
#   → http://localhost:8080
#   → Swagger: http://localhost:8080/swagger-ui/index.html
```

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 테스트 클래스
./gradlew test --tests "com.kdb.it.domain.budget.project.service.ProjectServiceTest"

# 커버리지 리포트
./gradlew jacocoTestReport
# 리포트 확인: build/reports/jacoco/test/html/index.html
```

### IDE 설정 (IntelliJ IDEA 권장)

1. QueryDSL Q클래스 자동 생성 설정:
   - Build, Execution, Deployment → Compiler → Annotation Processors
   - Enable annotation processing 체크
2. Lombok 플러그인 설치 (IntelliJ Lombok 플러그인)
3. 파일 인코딩: File → Settings → Editor → File Encodings → UTF-8

---

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
| **복합키 (`@IdClass`)** | `BprojmId`, `BcostmId`, `BitemmId`, `CdecimId` 등 복합 기본키 정의 (총 29개 `@IdClass`) | Oracle 테이블 스키마 설계를 JPA 엔티티에 1:1 매핑 |
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
| **협의회 통합 컨트롤러** | `CouncilController` 1개 (39개 매핑 메서드) vs 서비스 9개 분리 | 협의회 업무의 통합 흐름 표현, 서비스 계층은 관심사 분리 |
| **변경 로그 (Audit)** | `@PrePersist/@PreUpdate` JPA 리스너로 자동 스냅샷 기록 | 누가 무엇을 언제 변경했는지 추적, 감사/규정 준수 대응 |

### 3.3 BaseEntity 상속 구조

```
BaseEntity (추상 클래스)
 ├── Bprojm      (정보화사업)
 ├── Bcostm      (전산관리비)
 ├── Bitemm      (품목)
 ├── Btermm      (단말기)
 ├── Bplanm      (정보기술부문 계획)
 ├── Bplana      (정보기술부문계획 관계)
 ├── Bbugtm      (예산 작업/편성률)
 ├── Basctm      (협의회 심의과제)
 ├── Bcmmtm      (평가위원)
 ├── Bevalm      (평가의견)
 ├── Bperfm      (성과지표)
 ├── Bpovwm      (사업개요)
 ├── Bpqnam      (사전질의응답)
 ├── Bmqnam      (본회의질의응답)
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
 ├── Cblbmm      (게시판 메타)
 ├── Cblbcm      (게시물)
 ├── Ccmmtm      (게시판 댓글)
 ├── Cinfmm      (알림)
 ├── Cmenum      (메뉴 마스터)
 ├── Cmenua      (메뉴-자격등급 매핑)
 ├── Cmenud      (라우트 카탈로그)
 ├── CauthI      (자격등급)
 ├── CroleI      (역할 매핑)
 ├── Clognh      (로그인 이력)
 └── Crtokm      (JWT Refresh Token)

BaseLogEntity (변경 로그 추상 클래스)
 └── BprojmL, BcostmL, BrdocmL 등 원본 엔티티별 로그 스냅샷
```

## 4. 모듈 관계

### 4.1 패키지 구조

2026-06-14 기준 도메인 기반 레이어드 아키텍처와 예산·결재·변경 로그·메뉴·사업집행 4단계 모듈 구조를 반영합니다.

```
com.kdb.it
├── config/                  # 전역 설정 (Security, JPA Auditing, Jackson, QueryDSL, Swagger, SSO Web, Clock) — 7개
├── exception/               # 전역 예외 핸들러 + 커스텀 예외 — 2개
├── common/
│   ├── system/              # 인증·로그인 (AuthController, AuthService, JwtUtil, JwtAuthenticationFilter)
│   ├── iam/                 # 사용자·조직·권한 (UserController, OrganizationController, UserRepository)
│   ├── approval/            # 신청서·결재 (ApplicationController, ApplicationService, ApplicationMapRepository)
│   ├── admin/               # 시스템관리, 관리자 로그 (AdminController, AdminLogService)
│   │   └── realtime/        # 실시간 로그 모니터링(ROLE_ADMIN 전용): RealtimeLogController, V_ITPAPP_LOG_FEED 기반
│   ├── notification/        # 알림 시스템 (NotificationService, NotificationDispatcher, Cinfmm 엔티티)
│   ├── board/               # 공통 게시판 (BoardMeta/Post/Comment)
│   ├── code/                # 공통 코드 (CodeController, CodeService, CodeRepository)
│   └── util/                # 공통 유틸 (CustomPasswordEncoder, CookieUtil, HtmlSanitizer)
├── domain/                  # 비즈니스 도메인 집합
│   ├── budget/              # 예산 관리
│   │   ├── project/         # 정보화사업 (ProjectController, ProjectService, Bprojm)
│   │   ├── cost/            # 전산업무비 (CostController, CostService, Bcostm, Btermm)
│   │   ├── document/        # 문서·검토의견 (GuideDocController, ServiceRequestDocController, ReviewCommentController)
│   │   ├── plan/            # 정보기술부문 계획 (PlanController, PlanService, Bplanm, Bplana)
│   │   ├── status/          # 예산현황 대시보드 (BudgetStatusController, BudgetStatusService)
│   │   ├── work/            # 예산 작업 (BudgetWorkController, BudgetWorkService, Bbugtm)
│   │   └── it/              # IT부문 예산 (ItBudgetController, ItBudgetService)
│   ├── estimate/            # 사업집행① 소요예산 산정 (EstimateController, Bestim + Besttm 팀별 상세)
│   ├── deliberation/        # 사업집행② 과업심의위원회 (DeliberationController, Bdelim)
│   ├── contract/            # 사업집행③ 입찰/계약 (ContractController, Bcontm)
│   ├── payment/             # 사업집행④ 대금지급 (PaymentController, Bpaymm + Bpaymt 회차별 상세)
│   ├── council/             # 정보화실무협의회 (CouncilController, 9개 서비스, 10개 Repository)
│   ├── log/                 # 변경 로그 (BaseLogEntity, *L 로그 엔티티, ChangeLogEntityListener)
│   ├── menu/                # DB 기반 메뉴 트리·라우트 카탈로그 (MenuQueryController, AdminMenuController, AdminRouteController, Cmenum/Cmenua/Cmenud)
│   └── entity/              # BaseEntity
└── infra/
    ├── file/                # 파일 관리 (FileController, FileService, FileRepository, Cfilem)
    ├── eai/                 # KDB 표준전문 EAI 발송 (EaiService, sealed EaiPayload SPI: UMS/GWE) — 현재 미연동(eai.enabled=false)
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
| 사전협의 검토자 | `ReviewerController` | `ReviewerService` | - | - |
| 정보기술부문계획 | `PlanController` | `PlanService` | `BplanmRepository`, `BplanaRepository` | `Bplanm`, `Bplana` |
| 예산현황 | `BudgetStatusController` | `BudgetStatusService` | `BudgetStatusQueryRepository` | - |
| IT부문 예산 | `ItBudgetController` | `ItBudgetService` | `ItBudgetQueryRepository` + Custom | - |
| 사업집행① 소요예산 산정 | `EstimateController` | `EstimateService` | `EstimateRepository`(+Custom), `EstimateLineRepository` | `Bestim`, `Besttm` |
| 사업집행② 과업심의 | `DeliberationController` | `DeliberationService` | `DeliberationRepository`(+Custom) | `Bdelim` |
| 사업집행③ 입찰/계약 | `ContractController` | `ContractService` | `ContractRepository`(+Custom) | `Bcontm` |
| 사업집행④ 대금지급 | `PaymentController` | `PaymentService` | `PaymentRepository`(+Custom), `PaymentLineRepository` | `Bpaymm`, `Bpaymt` |
| 예산작업 | `BudgetWorkController` | `BudgetWorkService` | `BbugtmRepository` + Custom | `Bbugtm` |
| 정보화실무협의회 | `CouncilController` | `CouncilService` 외 8개 | `CouncilRepository` 외 9개 | `Basctm` 외 9개 |
| 신청서(결재) | `ApplicationController` | `ApplicationService` | `ApplicationRepository`, `ApplicationMapRepository`, `ApproverRepository` | `Capplm`, `Cappla`, `Cdecim` |
| 공통게시판 | `BoardMetaController`, `BoardPostController`, `BoardCommentController`, `AdminBoardMetaController` | `BoardMetaService`, `BoardPostService`, `BoardCommentService` | `BoardMetaRepository`, `BoardPostRepository`, `BoardCommentRepository` | `Cblbmm`, `Cblbcm`, `Ccmmtm` |
| 알림 | `NotificationController` | `NotificationService` | `CinfmmRepository` + Custom | `Cinfmm` |
| Tiptap 변수 | `TiptapVariableController` | `TiptapVariableService` | - | - |
| 실시간 로그 | `RealtimeLogController` | `RealtimeLogService` | `RealtimeLogRepository` | - (V_ITPAPP_LOG_FEED View 기반) |
| 인증 | `AuthController` | `AuthService` | `UserRepository`, `RefreshTokenRepository`, `LoginHistoryRepository` | `CuserI`, `Crtokm`, `Clognh` |
| 공통코드 | `CodeController` | `CodeService` | `CodeRepository` + Custom | `Ccodem` |
| 시스템관리 | `AdminController` | `AdminService` | (기존 Repository 활용) | (기존 Entity 활용) |
| 메뉴 조회 | `MenuQueryController` | `MenuQueryService`, `BoardListMenuResolver` | `CmenumRepository`(+Custom), `CmenuaRepository` | `Cmenum`, `Cmenua` |
| 관리자 메뉴/라우트 | `AdminMenuController`, `AdminRouteController` | `AdminMenuService`, `AdminRouteService` | `CmenumRepository`, `CmenuaRepository`, `CmenudRepository` | `Cmenum`, `Cmenua`, `Cmenud` |
| 사용자 | `UserController` | `UserService` | `UserRepository` | `CuserI` |
| 조직 | `OrganizationController` | `OrganizationService` | `OrganizationRepository` | `CorgnI` |
| 첨부파일 | `FileController` | `FileService` | `FileRepository` | `Cfilem` |
| Gemini AI | `GeminiController` | `GeminiService` | `FileRepository` (파일 첨부) | - |
| 로그인이력 | `LoginHistoryController` | `LoginHistoryService` | `LoginHistoryRepository` | `Clognh` |
| 변경로그 | - | `ChangeLogEntityListener`, `AuditLogPersister` | `EntityManager` 직접 저장 | `BaseLogEntity` 하위 `*L` 엔티티 |

## 5. 실시간 로그 모니터링

### 5.0 실시간 로그 (Realtime Log) — ROLE_ADMIN 전용

**`common/admin/realtime` 패키지는 `V_ITPAPP_LOG_FEED` 통합 View를 기반으로 관리자용 변경 로그 스냅샷을 제공합니다.**

#### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/admin/realtime-logs` | 실시간 변경 로그 조회 (커서 기반 페이징) |

#### 요청 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `since` | LocalDateTime | null | 조회 시작 시각 (ISO-8601, 예: `2026-06-01T10:30:00`) |
| `cursorLogTbl` | String | null | 커서 시작 로그 테이블 KEY (마지막 행 기준, 페이지네이션용) |
| `cursorLogSno` | String | null | 커서 시작 로그 일련번호 (마지막 행 기준, 페이지네이션용) |
| `limit` | int | 100 | 한 번에 반환할 행 수 (1~200 범위로 서비스 계층에서 보정) |
| `tables` | String | null | 조회할 로그 테이블 KEY 쉼표 구분 (예: `bprojl,bcostml`), `AdminLogService.getTables()`의 허용 KEY만 사용 |
| `chgTypes` | String | null | 변경 유형 필터 쉼표 구분 (허용값: `C`/`U`/`D`만) |

#### 응답 구조

```json
{
  "rows": [
    {
      "logTbl": "BPROJL",
      "logSno": "BPROJL_0000000000000000000001",
      "chgTp": "C",
      "chgDtm": "2026-06-01T10:30:45",
      "chgUsid": "S12345",
      "... 표준 로그 컬럼": "..."
    }
  ],
  "serverTime": "2026-06-01T10:31:00",
  "tableCounts": {
    "BPROJL": 45,
    "BCOSTML": 12,
    ...
  },
  "perMinute": [
    { "minute": "2026-06-01T10:30:00", "count": 15 },
    { "minute": "2026-06-01T10:31:00", "count": 8 }
  ]
}
```

#### 주요 특징

- **V_ITPAPP_LOG_FEED 기반**: 모든 로그 테이블(`*L`)을 통합한 Oracle View 조회. 표준화된 컬럼만 반환.
- **표준 로그 컬럼**: `LOG_TBL`, `LOG_SNO`, `CHG_TP`, `CHG_DTM`, `CHG_USID`, `DEL_YN`, `GUID`, `FST_ENR_DTM/USID`, `LST_CHG_DTM/USID` 등.
- **BEFORE/AFTER 변경 본문 제외**: 실시간 스냅샷이므로 상세 비교는 `/api/admin/logs/{key}/{logSno}` 상세 조회 엔드포인트 사용.
- **커서 기반 페이징**: `cursorLogTbl` + `cursorLogSno`로 "마지막 행 다음부터" 조회 → 무한 스크롤 가능.
- **테이블 필터링**: `tables` 파라미터로 특정 로그만 조회 (예: `bprojl,bcostml`). 허용 목록 외 key는 서비스 계층에서 거부.
- **변경 유형 필터**: `chgTypes=C,U` → 생성/수정만, `chgTypes=D` → 삭제만.
- **집계 정보**: 최근 5분, 최근 30분의 분단위 변경 수(perMinute) 제공.
- **권한**: `@PreAuthorize("hasRole('ADMIN')")` 클래스 레벨 적용.

---

## 6. 알림 및 Tiptap 변수 시스템

### 6.0 알림 시스템 (Notification)

**결재요청, 게시판 멘션, 시스템 알림 등을 사용자에게 실시간으로 전달하는 모듈**

#### 엔티티 구조 (common/notification)

- **Cinfmm** (`TPRMPP_CINFMM`): 알림 마스터 — 1행 = 1수신자
  - `infMngNo` (PK): 형식 `INF-{YYYY}-{8자리 시퀀스}` (예: `INF-2026-00000001`)
  - `infTpC`: 알림종류구분코드 (Ccodem cId=CINF_TP)
    - `001` = 시스템 알림
    - `002` = 결재요청 알림
    - `003` = 결재결과 알림
    - `004` = 게시물 멘션 알림
    - `005` = 댓글 멘션 알림
  - `rcvUsid`: 수신자 사번 (1행 = 1수신자)
  - `rddYn` / `rddDtm`: 읽음여부 및 읽음일시
  - `eaiSdTpC` / `eaiSdDtm` / `eaiSdCone`: EAI 발송 채널·일시·페이로드 (Phase 2에서 EMAIL/SMS/TALK 활성화)

#### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/notifications` | 본인 알림 목록 페이지 조회 (unreadOnly 필터 가능) |
| GET | `/api/notifications/unread-count` | 본인 미읽음 카운트 (헤더 뱃지용) |
| PATCH | `/api/notifications/{infMngNo}/read` | 단건 읽음 처리 |
| PATCH | `/api/notifications/read-all` | 본인 미읽음 일괄 읽음 |
| DELETE | `/api/notifications/{infMngNo}` | 단건 Soft Delete |

모든 엔드포인트는 인증 필수이며, 호출자 본인 데이터에만 접근 가능합니다.

#### 발송 흐름

```
1. 결재/게시판/시스템 도메인에서 NotificationEvent 발행
   → ApplicationEventPublisher.publishEvent(new NotificationEvent(...))

2. NotificationEventListener (Spring @TransactionalEventListener(AFTER_COMMIT))
   → 발행자 트랜잭션 커밋 후 비동기 호출
   → NotificationService.send(event)

3. NotificationService.send()
   → 채번(INF-{YYYY}-{8자리})
   → Cinfmm 엔티티 빌드
   → saveAndFlush() — 즉시 INSERT
   → NotificationDispatcher.dispatch() — EAI 메타 기록

4. NotificationDispatcher (전략 인터페이스)
   → 현 Phase: INAPP만 처리 (EAI_SD_TP_C='001')
   → Phase 2: EMAIL/SMS/TALK 채널 추가
```

#### 주요 특징

- **@TransactionalEventListener(AFTER_COMMIT)**: 알림 발행이 원본 트랜잭션을 차단하지 않음
- **Propagation.REQUIRES_NEW**: 이벤트 리스너 내 새 트랜잭션 강제 시작 (Spring 7.0 호환성)
- **Soft Delete**: 논리 삭제로 이력 추적
- **소유자 검증**: 모든 조회/수정/삭제는 `rcvUsid==currentEno` 검증
- **채번**: 시퀀스 기반 연도별 자동 생성

#### NotificationDispatcher 패턴

현재 `StubNotificationDispatcher` 구현체가 기본 처리합니다. Phase 2에서는 다음을 추가합니다:

```java
public interface NotificationDispatcher {
    void dispatch(Cinfmm notification, String eaiPayload);
}

// Phase 1 (현재): 인앱만
public class StubNotificationDispatcher implements NotificationDispatcher {
    public void dispatch(Cinfmm notification, String eaiPayload) {
        notification.markDispatched("001", null); // EAI_SD_TP='001' = INAPP 채널 코드
    }
}

// Phase 2 (예정):
public class MultiChannelDispatcher implements NotificationDispatcher {
    void dispatch(Cinfmm notification, String eaiPayload) {
        // EMAIL/SMS/TALK 어댑터 호출
    }
}
```

---

### 6.1 Tiptap 변수 시스템 (common/system/tiptap)

**Tiptap 에디터 문서에 동적 변수를 삽입 및 해석하는 모듈**

#### 목적

요구사항 정의서, 계획, 가이드 등 편집 가능 문서에 "편성요청액", "편성액", "편성률" 등 예산 변수를 삽입 가능하게 합니다.

#### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/tiptap-variables/metadata` | 변수 카탈로그 조회 (카테고리·연도·사업·항목 목록) |
| POST | `/api/tiptap-variables/resolve` | 토큰 배열 해석 (표시값 및 상태 반환) |

#### 카탈로그 구조

`/metadata` 응답:

```json
{
  "categories": [
    {
      "code": "IT_BUDGET",
      "label": "전산예산",
      "years": [2024, 2025, 2026, 2027, 2028],
      "projects": null,
      "items": [
        { "code": "requestAmount", "label": "편성요청액" },
        { "code": "allocatedAmount", "label": "편성액" },
        { "code": "allocationRate", "label": "편성률" }
      ]
    },
    {
      "code": "PROJ",
      "label": "사업별",
      "years": [2024, 2025, 2026, 2027, 2028],
      "projects": [
        { "code": "PRJ-2026-0001", "name": "시스템 개선" },
        ...
      ],
      "items": [...]
    },
    ...
  ]
}
```

#### 토큰 형식 및 해석

**토큰 형식**: `<YEAR>.<CATEGORY>[.<PROJECT_CODE>].<ITEM>` (점 구분, 카테고리는 camelCase)

- 비사업 카테고리: `{연도}.{itBudget|capBudget|opex}.{항목}`
- 사업 카테고리: `{연도}.proj.{사업코드}.{항목}`

예시 요청 (`/resolve`):

```json
{
  "tokens": [
    "2026.itBudget.requestAmount",
    "2026.proj.PRJ-2026-0001.allocatedAmount",
    "2026.capBudget.allocationRate"
  ]
}
```

**응답**:

```json
{
  "results": {
    "2026.itBudget.requestAmount": {
      "status": "OK",
      "value": "500억원"
    },
    "2026.proj.PRJ-2026-0001.allocatedAmount": {
      "status": "MISSING",
      "value": null
    },
    "2026.capBudget.allocationRate": {
      "status": "OK",
      "value": "85.3%"
    }
  }
}
```

#### 해석 상태

| 상태 | 의미 |
|------|------|
| `OK` | 성공적으로 해석됨 (value 포함) |
| `INVALID` | 토큰 형식 불일치 또는 카테고리/항목 미존재 |
| `MISSING` | 카테고리/연도/사업은 존재하나 해당 금액 데이터 없음 |

#### 구현 상세

**TiptapTokenParser**
- 토큰 정규식 검증
- 카테고리·연도·항목·사업코드 추출
- ParseResult 반환 (valid 플래그 포함)

**TiptapVariableService**
- 클래스 레벨 `@Transactional(readOnly=true)` 적용
- `getMetadata()`: ProjectRepository(활성 사업만) + 현재 연도±2 범위
- `resolve(List<String> tokens)`: 1~200개 토큰 (DTO 검증)
  - 각 토큰마다 `resolveOne()` 호출
  - BudgetStatusQueryRepository.aggregateByCategory() 또는 aggregateByProject() 호출
  - 금액 포맷팅: 억원/만원/원 자동 변환
  - 편성률: (편성액 / 편성요청액 × 100) 소수점 한 자리

**금액 포맷팅 규칙**

| 범위 | 포맷 |
|------|------|
| ≥ 1억 원 | `{n}억원` (예: 90000000000 → "900억원") |
| 1만 원 ~ 1억 원 미만 | `{n}만원` |
| < 1만 원 | `{n}원` |

#### 권한 및 필터링

- 현 Phase: 권한 필터링 없음 (모든 인증 사용자 동일 카탈로그)
- Phase 2: SecurityContext 기반 부서/권한별 사업 필터링 (후속 Task)

---

## 7. 로그 체계

IT Portal의 로그는 **3가지 유형**으로 구성되며, 각각 다른 계층에서 처리됩니다.

### 7.1 변경 로그 (Audit Log) — 자동 기록

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
| 실패 격리 | `persist()` 호출 자체의 예외는 삼켜 본 업무 트랜잭션 롤백을 방지 (`log.warn`). **단, `entityManager.persist()`는 INSERT를 예약만 하므로 제약 위반 등 flush 시점 오류는 이 try/catch로 잡히지 않고 커밋 시 원본 트랜잭션을 롤백시킴**(아래 ⚠️ 규칙 참조) |
| PK 생성 | `AuditLogIdGenerator` → Oracle `S_{Postfix}.NEXTVAL` 조회 → `"{Postfix}_{22자리 0패딩}"` 형식 |
| 변경유형 | `C`(생성) / `U`(수정) / `D`(논리삭제, `DEL_YN='Y'` 판별) |
| 변경자 | `SecurityContext`에서 추출한 현재 사용자 사번 자동 기록 |

> **⚠️ 필수 규칙 — NOT NULL 기본값은 생성자/팩토리에서 설정 (스냅샷 타이밍 함정)**
>
> `@LogTarget` 엔티티에서 NOT NULL 컬럼의 기본값은 **생성자·팩토리(`create()`)·빌더 시점**에 채웁니다. 엔티티 자신의 `@PrePersist`에서만 설정하면 안 됩니다.
>
> JPA 규약상 엔티티 리스너(`ChangeLogEntityListener`)는 엔티티 자신의 `@PrePersist`보다 **먼저** 실행됩니다. 그래서 로그(`*L`)를 스냅샷하는 시점에 엔티티 고유 기본값이 아직 null이면, 마스터 INSERT는 정상이어도 로그 테이블에 null이 복사되어 `*L`의 NOT NULL 제약을 위반합니다(`ORA-01400`). 위 "실패 격리"의 try/catch는 flush 시점 오류를 잡지 못하므로 **원본 트랜잭션까지 롤백**됩니다.
>
> BaseEntity 공통 필드(`delYn`/`guid`/`guidPrgSno`)와 JPA Auditing 필드(`fstEnrDtm`/`fstEnrUsid`)는 각각 `AuditLogPersister.applyBaseAuditDefaults()`와 `AuditingEntityListener`(리스너 순서상 먼저)가 처리하므로 안전합니다. **엔티티 고유의 NOT NULL 기본값만** 본 규칙 대상입니다. 사례: `Brivgm.fsgYn`(검토의견 완료여부)를 `create()`에서 `"N"`으로 설정. 상세는 `CLAUDE.md` §5.12.1.1 참조.

**`@LogTarget` 어노테이션으로 로그 대상 지정**

```java
@LogTarget(entity = BprojmL.class)
@Entity
@Table(name = "TPRMPP_BPROJM")
public class Bprojm extends BaseEntity { ... }
```

**현재 로그 대상 엔티티 (`@LogTarget` 기준 30개)**

| 키 | 로그 엔티티 | 설명 |
|----|-----------|------|
| `basctm` | `BasctmL` | 정보화실무협의회 신청 |
| `bbugt` | `BbugtL` | 예산 편성 |
| `bcmmtm` | `BcmmtmL` | 협의회 위원 |
| `bcostm` | `BcostmL` | 전산업무비 |
| `bevalm` | `BevalmL` | 평가 |
| `bgdocm` | `BgdocmL` | 가이드 문서 |
| `bitemm` | `BitemmL` | 사업 비목 |
| `bperfm` | `BperfmL` | 성과평가 |
| `bplanm` | `BplanmL` | 정보기술부문 계획 |
| `bpovwm` | `BpovwmL` | 관점/배점 |
| `bpqnam` | `BpqnamL` | 사전질의응답 |
| `bmqnam` | `BmqnamL` | 본회의질의응답 |
| `bprojm` | `BprojmL` | 정보화사업 |
| `brdocm` | `BrdocmL` | 요구사항 문서 |
| `brivgm` | `BrivgmL` | 검토의견 |
| `brsltm` | `BrsltmL` | 심의결과 |
| `bschdm` | `BschdmL` | 협의회 일정 |
| `btermm` | `BtermmL` | 단말기 상세 |
| `bestim` | `BestimL` | 사업집행① 소요예산 산정 기본 |
| `besttm` | `BesttmL` | 사업집행① 소요예산 산정 팀별 상세 |
| `bdelim` | `BdelimL` | 사업집행② 과업심의 |
| `bcontm` | `BcontmL` | 사업집행③ 입찰/계약 |
| `bpaymm` | `BpaymmL` | 사업집행④ 대금지급 기본 |
| `bpaymt` | `BpaymtL` | 사업집행④ 대금지급 회차별 상세 |
| `capplm` | `CapplmL` | 전자결재 |
| `ccodem` | `CcodemL` | 공통코드 |
| `cblbcm` | `CblbcmL` | 게시물 |
| `cblbmm` | `CblbmmL` | 게시판 |
| `ccmmtm` | `CcmmtmL` | 게시판 댓글 |
| `cmenum` | `CmenumL` | 공통메뉴 |

> **참고**: 위 30개 엔티티는 `@LogTarget`으로 변경 로그가 자동 기록됩니다. 다만 관리자 로그 조회 화면(`AdminLogService.buildDefinitions()`, §7.3)에 등록된 항목은 **19개**입니다. 사업집행 4단계(bestim/besttm/bdelim/bcontm/bpaymm/bpaymt)는 자동 기록되나 관리자 조회 정의 미등록(후속 과제). 게시판 로그(`cblbcm`/`cblbmm`/`ccmmtm`), `bmqnam`, `cmenum`은 자동 기록은 되지만 아직 관리자 조회 정의에 추가되지 않았습니다(후속 과제).

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

### 7.2 로그인 이력 (Login History) — 명시적 기록

인증 흐름 중 `AuthService`가 `Clognh` 엔티티에 직접 저장합니다. 변경 로그와 달리 AOP/리스너 없이 서비스 코드에서 명시적으로 기록합니다.

| 이력 유형 | 기록 시점 |
|----------|---------|
| `LOGIN_SUCCESS` | 로그인 성공 후 |
| `LOGIN_FAILURE` | 비밀번호 불일치 시 |
| `LOGOUT` | 로그아웃 처리 후 |

- **조회**: `LoginHistoryService` — 본인 이력 최대 50건(`getLoginHistory`) 또는 최근 10건(`getRecentLoginHistory`)
- **테이블**: `TPRMPP_CLOGNH`

### 7.3 관리자 로그 조회 (`AdminLogService`) — ROLE_ADMIN 전용

변경 로그 20개 테이블을 관리자 화면에서 페이징·상세 조회합니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/admin/logs/tables` | 조회 가능한 로그 테이블 목록 |
| `GET /api/admin/logs/{key}` | 로그 목록 (페이징, 최대 500건/페이지) |
| `GET /api/admin/logs/{key}/{logSno}` | 로그 상세 (전체 스냅샷) |

사번 필드(`*USID`, `ENO` 등)는 자동으로 사용자명으로 변환하여 응답에 포함합니다.

---

## 8. 인증/인가 및 보안

### 8.1 JWT 인증 흐름

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

### 8.2 인가 (Authorization) 모델

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

### 8.3 보안 조치

| 항목 | 기술 | 설명 |
|------|------|------|
| **CSRF 방어** | JWT Stateless | REST API는 CSRF 공격 대상이 아님, Spring Security CSRF 비활성화 |
| **XSS 방어** | httpOnly 쿠키 + HTML 새니타이징 | JavaScript 접근 차단, `HtmlSanitizer`(Jsoup)로 사용자 입력 필터링 |
| **SQL Injection** | 매개변수화 쿼리 | JPA `@Query`/QueryDSL, Native Query는 `@Query(nativeQuery=true)` + 파라미터 바인딩 |
| **HSTS** | SecurityConfig | `max-age=31536000`, `includeSubDomains=true` |
| **CSP** | SecurityConfig | `default-src 'self'`, `script-src 'self'`, XSS 2차 방어선 |
| **비밀번호 저장** | SHA-256 + Base64 | `CustomPasswordEncoder` |
| **환경 비밀값** | 환경변수 주입 | `DB_PASSWORD`, `JWT_SECRET`, `GEMINI_API_KEY` (`DB_PASSWORD`, `JWT_SECRET`은 현재 개발 기본값이 남아 있어 운영 프로파일에서 제거 필요) |

## 9. 주요 API 엔드포인트

### 9.1 공개 엔드포인트 (인증 불필요)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/login` | 로그인 (사번/비밀번호) |
| POST | `/api/auth/refresh` | Access Token 갱신 (Refresh Token 기반) |
| POST | `/api/auth/sso/complete` | SSO 완료 콜백 (JWT 발급) |
| GET | `/swagger-ui/**` | Swagger UI 문서 |
| GET | `/v3/api-docs/**` | OpenAPI 명세 |

> **회원가입**: `/api/auth/signup` — 관리자 권한 필요 (임직원 포털 특성상 자유 가입 금지)
>
> **개발 전용**: `/api/auth/dev/**` — 개발자 사용자 전환 API (app.dev.user-switch.enabled=true 시에만 활성화, 운영 배포 전 반드시 비활성화)

### 9.2 비즈니스 API (인증 필수)

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
| **협의회 관리** | GET/POST/PUT/PATCH | `/api/council/**` | 신청, 심의, 평가, 일정 (39개 매핑) | 일반 |
| | | | CouncilController 통합 (9개 서비스 분리) | |
| **Gemini AI** | POST | `/api/gemini/generate` | 텍스트 생성 (파일 첨부 가능) | **관리자** |
| **알림** | GET/PATCH/DELETE | `/api/notifications/**` | 알림 목록/읽음/삭제 (본인 데이터만) | 일반 |
| **Tiptap 변수** | GET/POST | `/api/tiptap-variables/**` | 변수 카탈로그, 토큰 해석 | 일반 |
| **메뉴 조회** | GET | `/api/menus` | 사용자 자격등급 기준 사이드바·Breadcrumb 메뉴 트리 | 일반 |
| **공통코드** | GET/POST/PUT/DELETE | `/api/ccodem/**` | 코드 조회 및 CRUD (캐싱) | 일반 |
| **사용자** | GET | `/api/users/**` | 사용자/조직 조회 | 일반 |
| **로그인 이력** | GET | `/api/login-history/**` | 본인 이력 조회 (최대 50건) | 일반 |
| **관리자** | GET/POST/PUT/DELETE | `/api/admin/**` | 시스템 설정, 로그 조회, 사용자/코드 관리 | **관리자** |
| **관리자 메뉴** | GET/POST/PUT/PATCH/DELETE | `/api/admin/menus/**`, `/api/admin/routes/**` | 메뉴 트리, 권한 매핑, 라우트 카탈로그 관리 | **관리자** |
| **실시간 로그** | GET | `/api/admin/realtime-logs` | 통합 변경 로그 스냅샷, 최근 5분/30분 집계 | **관리자** |
| **게시판 관리** | GET/POST/PUT/DELETE | `/api/admin/boards/meta/**` | 게시판 메타 생성/수정/삭제 | **관리자** |
| **계획 관리** | GET/POST | `/api/plans/**` | 정보기술부문 계획 CRUD | **관리자** |
| **예산현황** | GET | `/api/budget/status/**` | 집계 대시보드 (전체 예산 조회) | **관리자** |
| **IT부문 예산** | GET | `/api/budget/it/**` | 비목별 IT/정보보호 예산 요약, 전년 대비 비교 | **관리자** |
| **예산작업** | GET/POST | `/api/budget/work/**` | 편성률 조회, Upsert, 결과 조회 | **관리자** |
| **사전협의 검토자** | GET | `/api/reviews/{docMngNo}/reviewers` | 사전협의 문서별 검토자 목록 조회 | 일반 |
| **사업집행① 소요예산** | GET/POST/PUT/DELETE | `/api/project/estimates/**` | 소요예산 산정 CRUD, 상태전이, 팀별 라인 저장 (상태 41→42→49) | 일반 |
| **사업집행② 과업심의** | GET/POST/PUT/DELETE | `/api/project/deliberations/**` | 과업심의 CRUD, 상태전이, 심의결과 저장 (상태 51→52→59) | 일반 |
| **사업집행③ 입찰/계약** | GET/POST/PUT/DELETE | `/api/project/contracts/**` | 입찰/계약 CRUD, 상태전이, 계약정보 저장 (상태 61→62→69) | 일반 |
| **사업집행④ 대금지급** | GET/POST/PUT/DELETE | `/api/project/payments/**` | 대금지급 CRUD, 상태전이, 회차별 지급 저장 (상태 71→72→79) | 일반 |

> 사업집행 4단계(`/api/project/**`)는 클래스 레벨 `@PreAuthorize` 없이 인증만 요구하며, 쓰기 주체·상태 전이·부서 권한은 서비스 계층에서 검증합니다. 대상구분(`bgPrnTc`)은 100(정보화사업)·200(전산업무비)이며 소요예산 산정은 100 전용입니다.

> **Swagger UI**: http://localhost:8080/swagger-ui/index.html

## 10. 빌드 및 실행

```bash
# 1. QueryDSL Q클래스 생성 (필요시)
./gradlew compileJava

# 2. 전체 빌드 + 테스트 + JAR/WAR 생성
./gradlew build

# 3. 개발 서버 기동 (Hot Reload 지원)
./gradlew bootRun
#   → http://localhost:8080
#   → Swagger: http://localhost:8080/swagger-ui/index.html

# 4. 테스트 실행 (84개 테스트 파일)
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

### 10.1 폐쇄망 빌드 (오프라인 미러)

인터넷이 차단된 폐쇄망에서는 외부망에서 수집한 의존성 묶음(`C:\maven-repo`)을
**로컬 파일 저장소(file:// URL)**로 사용해 빌드합니다. Nexus 업로드가 정책상
불가하고, Gradle 캐시(`.gradle`) 통째 복사도 저장소 선언 불일치로 동작하지
않으므로 본 방식이 표준입니다.

#### 반입 패키지 생성 (외부망 PC)

```powershell
cd it_backend

# 1. 격리된 GRADLE_USER_HOME으로 클린 빌드 — 필요한 의존성 전부 수집
$env:GRADLE_USER_HOME = 'C:\gradle-mirror'
.\gradlew --no-daemon clean build

# 2. Gradle 캐시를 Maven2 레이아웃 저장소로 변환
.\make-local-maven-repo.ps1 -CacheDir 'C:\gradle-mirror\caches\modules-2\files-2.1' -OutDir 'C:\maven-repo'

# 3. Gradle 배포판 zip 포함 (wrapper가 압축 해제 후 zip을 삭제하므로 별도 다운로드)
Invoke-WebRequest -Uri 'https://downloads.gradle.org/distributions/gradle-9.2.1-bin.zip' -OutFile 'C:\maven-repo\gradle-9.2.1-bin.zip'
```

`C:\maven-repo` 폴더 전체(의존성 + Gradle zip, 약 250MB)를 폐쇄망 PC의
**동일 경로 `C:\maven-repo`**로 복사합니다.

#### 폐쇄망 PC 설정

아래 3개 파일의 `[폐쇄망]` 주석 블록을 해제합니다 (모두 file:// URL 기본):

| 파일 | 변경 내용 |
|------|----------|
| `settings.gradle` | `pluginManagement` 블록 해제 → 플러그인을 `file:///C:/maven-repo`에서 해석 |
| `build.gradle` | `mavenCentral()` 주석 처리 + `maven { url = 'file:///C:/maven-repo' }` 해제 |
| `gradle/wrapper/gradle-wrapper.properties` | 기존 `distributionUrl` 주석 처리 + `file:///c:/maven-repo/gradle-9.2.1-bin.zip` 해제 |

file 프로토콜이므로 `allowInsecureProtocol` 설정은 불필요합니다.
이후 `./gradlew build`로 일반 빌드와 동일하게 사용합니다.

#### 내부 Nexus 활용 — 커버리지 점검 및 부분 다운로드

내부 Nexus(`http://10.6.65.151:20080/repository/maven-releases/`)에 의존성이
일부만 있는 경우, 빌드는 첫 누락에서 중단되므로 `check-repo-coverage.ps1`로
전체 커버리지를 먼저 점검합니다. 빌드와 달리 끝까지 돌며 누락 목록을 남깁니다.

```powershell
# 점검만 (존재/누락/커버리지 요약 + repo-missing-files.txt 생성)
.\check-repo-coverage.ps1 -ManifestFile .\maven-repo-manifest-pom-only.txt

# 점검 + 존재하는 파일을 C:\maven-repo로 다운로드 (재실행 시 받은 파일은 건너뜀)
.\check-repo-coverage.ps1 -ManifestFile .\maven-repo-manifest-pom-only.txt -DownloadDir 'C:\maven-repo'

# Nexus가 익명 조회를 막은 경우: -Username / -Password 추가
```

- 기준 목록: `maven-repo-manifest-pom-only.txt` (583건, `.module` 제외) —
  외부망 미러 생성 시 함께 갱신.
- 누락분은 `repo-missing-files.txt`를 그대로 **반입 신청 목록**으로 사용합니다.
  타입은 확장자 그대로: `.jar` → jar, `.pom` → pom (BOM·부모 POM·플러그인
  마커도 모두 pom).
- 운영 흐름: ① Nexus에서 받을 수 있는 만큼 `C:\maven-repo` 채움 → ② 누락분
  반입 신청 → ③ 도착 파일을 같은 경로 구조로 배치 → ④ 빌드.

#### Gradle Module Metadata(.module) — 반입 불필요

`.module`은 Gradle 전용 JSON 메타데이터(variant 정보)로, Maven 표준 타입
(jar/pom/war/aar/ear)이 아니라 반입 신청이 불가합니다. 저장소 주석 블록에
포함된 아래 설정이 Gradle의 `.module` 요청 자체를 차단하므로 **반입하지 않아도
됩니다** (주석 해제 시 이 블록 누락 금지 — 누락하면 `.module` 404로 빌드 중단):

```groovy
metadataSources {
    mavenPom()
    artifact()
    ignoreGradleMetadataRedirection()   // POM의 .module 리다이렉트 마커 무시
}
```

POM-only 해석으로 전체 빌드가 통과함은 빈 캐시 + file:// 저장소 시뮬레이션으로
검증 완료(2026-06-12). `.module` 보유 아티팩트 132개 전부 `.pom`을 함께
보유함을 확인했습니다.

#### 의존성 추가/변경 시 미러 갱신

build.gradle 의존성이 바뀌면 외부망에서 위 1·2단계를 재실행한 뒤
갱신된 `C:\maven-repo`를 다시 반입합니다 (`C:\gradle-mirror`는 증분
재사용되므로 유지 권장). manifest도 함께 재생성합니다:

```powershell
Get-ChildItem C:\maven-repo -File -Recurse |
  ForEach-Object { $_.FullName.Substring('C:\maven-repo\'.Length) -replace '\\','/' } |
  Where-Object { $_ -notlike 'gradle-*' } |
  Out-File maven-repo-manifest.txt -Encoding utf8
Get-Content maven-repo-manifest.txt | Where-Object { $_ -notlike '*.module' } |
  Out-File maven-repo-manifest-pom-only.txt -Encoding utf8
```

#### 동작 원리 및 주의사항

- Gradle은 플러그인 ID(`org.springframework.boot`)를 **마커 POM**
  (`org.springframework.boot.gradle.plugin-4.0.5.pom`)으로 먼저 조회한 뒤 실제
  구현 JAR를 받습니다. 미러에 마커 POM이 없으면 "Plugin was not found" 오류가
  발생합니다 (변환 스크립트가 자동 포함).
- `querydsl-jpa`/`querydsl-apt`는 `jakarta` classifier 파일
  (`querydsl-jpa-5.1.0-jakarta.jar`)을 사용합니다.
- `.gradle` 캐시 통째 복사가 실패하는 이유: 캐시 메타데이터가 "어느 저장소
  선언에서 받았는지"에 바인딩되어, 외부망(`mavenCentral()`)과 폐쇄망(file/Nexus)
  선언이 다르면 캐시를 재사용하지 못하고 네트워크 조회를 시도합니다.
- Nexus를 사용할 수 있게 되면(프록시 그룹 저장소 구성 시) 각 파일의 "방식 A"
  주석(Nexus URL + `allowInsecureProtocol`)으로 전환하면 됩니다.
  이때도 `metadataSources` 블록은 유지합니다.

#### 관련 스크립트/파일

| 파일 | 용도 |
|------|------|
| `make-local-maven-repo.ps1` | Gradle 캐시 → Maven2 레이아웃 미러 변환 (외부망) |
| `check-repo-coverage.ps1` | 내부 저장소 커버리지 점검 + 존재 파일 다운로드 (폐쇄망) |
| `maven-repo-manifest.txt` | 필요 파일 전체 목록 (715건, `.module` 포함) |
| `maven-repo-manifest-pom-only.txt` | 점검·반입 기준 목록 (583건, `.module` 제외) |

## 11. 환경 설정

### 11.1 application.properties 주요 항목

| 속성 | 기본값 | 개발 | 운영 | 설명 |
|------|--------|------|------|------|
| `spring.datasource.url` | - | `jdbc:oracle:thin:@127.0.0.1:11521/XEPDB1` | 프로덕션 접속 정보 | Oracle 접속 URL |
| `spring.datasource.hikari.connection-init-sql` | `ALTER SESSION SET CURRENT_SCHEMA=${DB_SCHEMA:ITPOWN}` | 동일 (베이스 공통) | 동일 (베이스 공통) | 스키마 전환 — 전 환경 공통 (접속 ITPAPP → 객체 소유 ITPOWN) |
| `spring.datasource.password` | `${DB_PASSWORD:kdb1234!!}` | 환경변수 또는 기본값 `kdb1234!!` | 환경변수 `DB_PASSWORD` | DB 비밀번호 (환경변수 우선, 운영 기본값 제거 필요) |
| `jwt.secret` | `${JWT_SECRET:kdb-it-secret-key-...256-bits}` | 환경변수 또는 내장 기본 시크릿 | 환경변수 `JWT_SECRET` (최소 256비트) | JWT 서명 비밀키 (운영 기본값 제거 필요) |
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

### 11.2 보안 설정

| 항목 | 설정 | 비고 |
|------|------|------|
| **CSRF** | 비활성화 (`http.csrf(disable)`) | Stateless JWT REST API는 CSRF 불필요 |
| **CORS** | `corsConfigurationSource()` | `allowCredentials(true)`, 쿠키 자동 전송 |
| **세션** | STATELESS (`SessionCreationPolicy.STATELESS`) | JWT 토큰으로 상태 관리 |
| **HTTP 헤더** | 보안 헤더 자동 설정 | HSTS, CSP, X-Frame-Options, Content-Type-Options |
| **환경변수** | 비밀값은 환경변수에서 주입 | `application.properties`의 개발 기본값은 운영 프로파일에서 제거 |

### 11.3 로컬 개발 환경 설정

```bash
# Windows (PowerShell)
$env:DB_PASSWORD = "your-db-password"
$env:JWT_SECRET = "your-jwt-secret-key"
$env:GEMINI_API_KEY = "your-gemini-api-key"

# macOS/Linux (bash)
export DB_PASSWORD=your-db-password
export JWT_SECRET=your-jwt-secret-key
export GEMINI_API_KEY=your-gemini-api-key
```

### 11.4 운영 배포 설정

1. **application.properties** 운영값 적용
2. **환경변수** 주입:
   - `DB_PASSWORD`: 운영 DB 비밀번호
   - `JWT_SECRET`: 운영 JWT 키 (256비트 이상)
   - `GEMINI_API_KEY`: Gemini API 키
3. **Secure 플래그 활성화**: `app.cookie.secure=true`
4. **CORS 도메인 제한**: `cors.allowed-origins=https://itportal.kdb.com` (실제 도메인)
5. **파일 저장 경로**: NAS 공유 폴더 지정 (`/mnt/nas/files` 등)
6. **WAR 배포**: Tomcat CATALINA_HOME/webapps 디렉토리에 복사

## 12. 외부 연동

### 12.1 Gemini AI

- **API**: `POST /api/gemini/generate` (인증 필수)
- **기능**: 텍스트 생성, 첨부파일 inlineData 변환, 미지원/누락 파일 `skippedFiles` 응답
- **설정**: `gemini.api.key`, `gemini.api.model=gemini-2.5-flash`, `gemini.api.base-url`
- **구현**: `GeminiService`, `GeminiController`
- **보안**: API 키는 환경변수 `GEMINI_API_KEY`에서 주입

### 12.2 SSO (Single Sign-On) 에이전트

- **상태**: 선택적 (JSP 에이전트 라이브러리 libs/ 폴더에 복사 후 주석 해제)
- **엔드포인트**: `/api/auth/sso/complete` (JWT 발급)
- **구성**: `SsoWebConfig`, `SsoController`
- **기능**: 사내 SSO 시스템과 연동하여 JWT 토큰 발급

### 12.3 Oracle Database

- **버전**: XEPDB1 (Oracle Database 21c XE)
- **사용자**: `ITPAPP`
- **특성**: Soft Delete 패턴, 복합키 `@IdClass`, 시퀀스 기반 채번
- **변경 이력**: 자동 감사로그 (`*L` 테이블) 기록

---

## 13. 핵심 도메인 및 의존성

### 13.1 도메인 의존성 규칙

```
domain → common (O)
common → domain (X)

infra → common (O)
common → infra (X)

infra → domain (X, domain 기능 불필요)
```

### 13.2 도메인별 핵심 클래스

| 도메인 | Entity | Service | Repository | 설명 |
|--------|--------|---------|------------|------|
| **budget.project** | Bprojm, Bitemm | ProjectService | ProjectRepository(+Custom) | 정보화사업 및 비목 |
| **budget.cost** | Bcostm, Btermm | CostService | CostRepository(+Custom) | 전산관리비 및 단말기 |
| **budget.document** | Bgdocm, Brdocm, Brivgm | GuideDocService, ServiceRequestDocService, ReviewCommentService | GuideDocRepository, ServiceRequestDocRepository, BrivgmRepository | 가이드·요구사항·검토의견 |
| **budget.plan** | Bplanm, Bplana | PlanService | BplanmRepository, BplanaRepository | 정보기술부문 계획 |
| **budget.status** | - (집계) | BudgetStatusService | BudgetStatusQueryRepository | 예산현황 대시보드 |
| **budget.work** | Bbugtm | BudgetWorkService | BbugtmRepository(+Custom) | 예산 편성률 |
| **council** | Basctm, Bschdm, Bcmmtm, Bevalm, Bperfm, Bpovwm, Bpqnam, Bmqnam, Brsltm | CouncilService(+8개 세부) | 9개 Repository | 정보화실무협의회 |
| **log** | BaseLogEntity, *L | - | EntityManager 직접 | 자동 감시로그 |
| **common.system** | CuserI, Crtokm, Clognh | AuthService, CustomUserDetailsService, LoginHistoryService | UserRepository, RefreshTokenRepository, LoginHistoryRepository | 인증 및 사용자 |
| **common.approval** | Capplm, Cappla, Cdecim | ApplicationService | ApplicationRepository(+Map, Approver) | 신청 및 결재 |
| **common.board** | Cblbmm, Cblbcm, Ccmmtm | BoardMetaService, BoardPostService, BoardCommentService | BoardMetaRepository, BoardPostRepository, BoardCommentRepository | 공통 게시판 |
| **common.iam** | CorgnI, CauthI, CroleI | UserService, OrganizationService | UserRepository, OrganizationRepository | 사용자/조직/권한 |
| **common.code** | Ccodem | CodeService | CodeRepository(+Custom) | 공통코드 (캐싱) |
| **common.admin** | - (기존 활용) | AdminService, AdminLogService | - | 시스템 관리 및 로그 |
| **infra.file** | Cfilem | FileService | FileRepository | 첨부파일 |
| **infra.ai** | - | GeminiService | FileRepository | Gemini 프록시 |

### 13.2.1 부서 필터링 패턴 (bbrC) — 재발 방지

신규 목록 API 추가 시 아래 패턴을 반드시 따릅니다.

| 계층 | 추가 내용 |
|------|----------|
| Controller | `@RequestParam(required = false) String bbrC` |
| Service | `getList(@Nullable String bbrC)` 시그니처 |
| RepositoryImpl | `if (StringUtils.hasText(bbrC)) builder.and(entity.bbrC.eq(bbrC))` |

- `bbrC` null·빈 문자열 → 전체 조회 (관리자 포함, 하위 호환 유지).
- TDD 의무: `bbrC` 지정·null 두 케이스 모두 JUnit 테스트 추가.

### 13.2.2 TDD 의무 범위

신규 Service / RepositoryImpl 로직은 **RED → GREEN → REFACTOR** 순서로 작성합니다.

```
RED   — 실패하는 JUnit 테스트 먼저 작성
GREEN — 테스트를 통과하는 최소 구현 작성
REFACTOR — 중복 제거, 가독성 개선 (테스트 통과 유지)
```

### 13.3 공통 의존성

| 패키지 | 목적 |
|--------|------|
| **config** | Spring Security, JPA Auditing, QueryDSL, Swagger, SSO Web, Jackson, Clock 설정 (7개) |
| **exception** | 전역 예외 처리 (`GlobalExceptionHandler`) |
| **common.util** | CookieUtil, HtmlSanitizer, CustomPasswordEncoder, CustomUserDetails |
| **common.system.security** | JwtUtil, JwtAuthenticationFilter |

---

## 14. 개발자 가이드

### 14.1 신규 기능 구현 패턴

#### 목록 API에 부서 필터링 추가
신규 목록 조회 API는 반드시 부서코드(`bbrC`) 필터링을 지원해야 합니다.

```
1. Controller: @RequestParam(required = false) String bbrC 추가
2. Service: getList(@Nullable String bbrC) 시그니처 변경
3. RepositoryImpl: if (StringUtils.hasText(bbrC)) builder.and(entity.bbrC.eq(bbrC))
4. Test: bbrC 지정/null 두 케이스 모두 테스트
```

#### 관리자 전용 컨트롤러 작성
관리자만 접근 가능한 도메인 API는 **클래스 레벨 `@PreAuthorize` 필수**:

```java
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")  // SecurityConfig URL 패턴 외 도메인 컨트롤러는 반드시 명시
public class PlanController { ... }
```

#### 알림 발송
결재/게시판 완료 후 사용자에게 알림:

```java
applicationEventPublisher.publishEvent(
    new NotificationEvent(
        Cinfmm.newInAppNotification(
            recipient.getEno(),
            "002",  // NotificationEvent.TYPE_APPROVAL_REQUEST
            applId
        )
    )
);
// NotificationEventListener(AFTER_COMMIT)가 자동으로 처리
```

#### JPA Auditing 로그 자동 기록
새 엔티티에 변경 로그 추가:

```java
@LogTarget(entity = BnewentL.class)  // 로그 대상 등록
@Entity
public class Bnewent extends BaseEntity { ... }
```

그 후:
1. `BaseLogEntity` 상속하는 `BnewentL` 생성
2. Oracle 시퀀스 `S_BNEWENT` 생성
3. `AdminLogService.buildDefinitions()`에 항목 추가

### 14.2 테스트 작성 의무

| 대상 | 테스트 케이스 | 필수 |
|------|-------------|------|
| 신규 Service 메서드 | RED→GREEN→REFACTOR | O |
| 신규 RepositoryImpl | 정상/null/부서필터 케이스 | O |
| 신규 Controller 엔드포인트 | MockMvc + 권한 테스트 | O |
| @Valid 검증 | 유효/무효 요청 | O |
| QueryDSL 집계 쿼리 | 결과 정확도 | O |

### 14.3 보안 체크리스트

신규 API 또는 수정 후:

- [ ] 인증 필수 엔드포인트는 SecurityConfig 또는 `@PreAuthorize` 보호
- [ ] `@Valid` 요청 검증 적용
- [ ] SQL Injection: 모든 동적 쿼리는 QueryDSL 또는 파라미터 바인딩
- [ ] XSS: 사용자 HTML 입력은 `HtmlSanitizer.sanitize()` 적용
- [ ] 파일 업로드: `FileValidator.validateExtension()` 호출
- [ ] 소유권 검증: 파일/알림/게시물은 `FileOwnershipChecker` 또는 권한 검증
- [ ] 부서 필터링: 목록 API는 `bbrC` 지원

---

## 15. 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| **2026-06-09** | README.md 코드 대조 현행화: (1) 소스 통계 정정(메인 Java 291→350, 테스트 96→115, @Entity 64→79), (2) 정보화사업 집행 4단계 도메인 신규 반영 — `domain/estimate`(소요예산 산정, `/api/project/estimates`, Bestim+Besttm), `domain/deliberation`(과업심의, `/api/project/deliberations`, Bdelim), `domain/contract`(입찰/계약, `/api/project/contracts`, Bcontm), `domain/payment`(대금지급, `/api/project/payments`, Bpaymm+Bpaymt) — 패키지 구조·모듈 관계표·API 엔드포인트표에 추가(상태머신 41~79, 인증만 요구·서비스 계층 권한 검증), (3) `infra/eai`(KDB 표준전문 EAI 발송, sealed EaiPayload SPI: UMS/GWE, eai.enabled=false 미연동) 인프라 모듈 반영 |
| **2026-06-05** | README.md 코드 대조 현행화: (1) 소스 통계 정정(@Entity 63→64), (2) `Bmqnam`(본회의질의응답) 엔티티·`@LogTarget` 반영 — 로그 대상 23→25개(관리자 조회 정의는 20개 유지), (3) 협의회 통계 정정(매핑 34→39, 서비스 8→9, Repository 9→10), (4) `config` 7개(ClockConfig 포함) 및 `domain/menu`·`budget/it` 패키지 명시, 미사용 `cdp`/`audit` 빈 디렉토리 표기 제거, (5) `application.properties` 실제 기본값(`DB_PASSWORD`/`JWT_SECRET`) 반영 |
| **2026-06-05** | README.md 현행화: 소스 통계(291개 메인 Java, 96개 테스트)와 DB 기반 메뉴 모듈(`domain/menu`, `/api/menus`, `/api/admin/menus`, `/api/admin/routes`) 반영 |
| **2026-06-01** | README.md 현행화: (1) 소스 통계 확정(271개 메인 Java, 92개 테스트, 63개 @Entity), (2) 실시간 로그 모니터링 섹션 신규 추가(§5, `common/admin/realtime`, RealtimeLogController, V_ITPAPP_LOG_FEED View, 커서 페이징, 테이블·변경유형 필터, 집계 정보), (3) 알림·Tiptap 변수 섹션을 §6으로 이동, (4) 로그 체계 섹션을 §7으로 이동, (5) 모듈 패키지 구조에 `common/notification`, `common/admin/realtime` 명시 |
| **2026-05-29** | README.md 현행화: 소스 코드 통계 정정(266 Java 파일, 84 테스트, 59 엔티티), IT부문 예산(`ItBudgetController`/`ItBudgetService`) 도메인 추가, 사전협의 검토자(`ReviewerController`) API 추가 |
| **2026-05-26** | README.md 전체 분석 및 업데이트: 소스 코드 통계(257 Java 파일, 84 테스트, 61 엔티티) 추가, 개발자 가이드 섹션(신규 기능 패턴, 테스트 의무, 보안 체크리스트) 신규 작성, 28개 컨트롤러 API 현행화 |
| **2026-05-22** | 알림 시스템(Notification) 및 Tiptap 변수 시스템 문서화: `common/notification` 모듈(Cinfmm, NotificationService, NotificationDispatcher, @TransactionalEventListener 패턴), `common/system/tiptap` 모듈(TiptapVariableService, TiptapVariableController, 토큰 형식, 금액 포맷팅) 상세 기술 |
| **2026-05-19** | REVIEW 재점검 결과 반영: 로그인 이력 JavaDoc 위치, `Bcostm` 깨진 한글 주석, Gemini 트랜잭션 경계 설명, 게시판 QueryDSL 구현체 조회 의도 주석 보강 |
| **2026-05-14** | 공통 게시판 모듈(`common/board`)과 게시판 API, 감사로그 대상 23개, DB 로그인 이력 기반 Brute-force 설명을 문서에 반영 |
| **2026-05-10** | 로컬 개발 포트를 실제 설정 기준(백엔드 8080)으로 정정. 비밀값 기본값은 아직 `application.properties`에 남아 있어 운영 프로파일 제거 과제로 재분류 |
| **2026-05-09** | README.md 대폭 개선: 프로젝트 개요 강화, 설계 결정 이유 추가, 인증/보안 섹션 분리, API 엔드포인트 도메인별 정렬, 환경 설정 테이블화, 외부 연동 문서화, 도메인 의존성 규칙 명시 |
| 2026-05-09 | `PlanController`, `BudgetStatusController`, `BudgetWorkController`에 `@PreAuthorize("hasRole('ADMIN')")` 추가. API 엔드포인트 테이블 인증 컬럼 현행화. 관리자 도메인 API 보호 규칙 CLAUDE.md §5.6·README §6.2에 명문화 |
| 2026-04-30 | README 로그 체계 섹션 추가: 변경 로그(AuditLog), 로그인 이력, 관리자 로그 조회 구조 문서화 |
| 2026-04-29 | README 현행화: Spring Boot/JJWT/Springdoc 버전, 15분 Access Token, 예산현황·검토의견·변경로그 도메인, 테스트/환경 설정 반영 |
| 2026-04-10 | 전체 프로젝트 문서/주석 리프레시 (README/CLAUDE/TASK.md 최신화, AdminController JavaDoc 보강) |
| 2026-04-05 | 정보화실무협의회(council) 도메인 구현: CouncilController(현재 39개 매핑), 9개 서비스, 10개 엔티티, 10개 Repository |
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
