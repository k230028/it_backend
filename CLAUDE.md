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
│   ├── board/     - 공통 게시판 (메타, 게시물, 댓글)
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

### 5.2 테이블 명칭
- TPRMPP_{1자리 구분값}{4자리 도메인}{1자리 용도}
- 1자리 구분값 : C (공통), B (비즈니스)
- 4자리 도메인 : 용도에 따라 지정 ex) BLBC
- 1자리 용도 : M (마스터), L (로그), H (이력)

### 5.2 엔티티 설계
- 모든 업무 엔티티는 **`BaseEntity` 상속** (공통 컬럼: `DEL_YN`, `GUID`, `FST_ENR_DTM/USID`, `LST_CHG_DTM/USID`).
- 삭제는 항상 **Soft Delete**(`delete()` → `DEL_YN='Y'`). 물리 삭제 금지.
- 엔티티 명칭은 `C:\it\META.md` 반드시 용어사전 기반으로 지정 (필수)
  (예. 삭제여부 : DEL_YN 등)
- comment 지정 필수
```java
    @Column(name = "ORC_TB_CD", length = 10, comment = "원본테이블코드")
    private String orcTbCd;
```
### 5.3 DTO 설계
- 관련 DTO는 **정적 중첩 클래스**로 한 파일에 묶음 (예: `AuthDto.LoginRequest`).
- Swagger 문서를 위해 `@Schema(name, description)` 필수.

### 5.4 Repository 패턴
- 기본 CRUD: `JpaRepository` 상속.
- 동적·복잡 쿼리: `RepositoryCustom` 인터페이스 + `RepositoryImpl` 구현(QueryDSL).
- 시퀀스 등 DB 종속 쿼리: `@Query(nativeQuery = true)`.
- 게시판 목록 검색처럼 공개 기간·권한·부서 조건이 함께 필요한 쿼리는 QueryDSL `BooleanBuilder`로 조립하고, 조건별 의도를 JavaDoc 또는 인접 주석으로 남깁니다.

### 5.5 Service 트랜잭션
- 조회: `@Transactional(readOnly = true)` 필수.
- 쓰기: `@Transactional` (기본 readOnly=false).
- **클래스 수준 `@Transactional(readOnly=true)` 적용 규칙**: 조회 메서드가 주인 서비스는 클래스 레벨에 `@Transactional(readOnly=true)` 적용. 쓰기 메서드는 반드시 `@Transactional` 또는 `@Transactional(readOnly=false)` 오버라이드 필수.
- JPA Dirty Checking 활용. 불필요한 `save()` 호출 지양.

### 5.5.1 공통코드(Ccodem) 캐시 관리
- `CodeService`: 클래스 레벨 `@Transactional(readOnly=true)` 적용.
- 캐시 전략: `@Cacheable('codesByCid', 'budgetPeriod')` — 정적 참조 데이터 캐시.
- 쓰기 메서드(생성/수정/삭제): `@CacheEvict(cacheNames = {"codesByCid", "budgetPeriod"}, allEntries = true)` 필수 — 두 캐시 무효화.
- 캐시 키 구성: `codesByCid`는 코드ID(cId) 기준, `budgetPeriod`는 회계연도 기준.

### 5.5.2 @Valid 검증 일관성
- 모든 **mutating 컨트롤러 엔드포인트** (POST/PUT/DELETE)는 요청 본문에 `@Valid` 필수.
- DTO 클래스에 `@Schema(name, description)` 추가 필수 (Swagger 문서화).
- 검증 실패 시 자동으로 400 Bad Request 응답.

### 5.6 인증 및 보안 (전사 SoT)
- 인증 방식: **httpOnly 쿠키 기반 JWT**(Stateless).
- Access Token 유효시간: 15분 (`jwt.access-token-validity=900000`).
- Refresh Token 유효시간: 7일 (`jwt.refresh-token-validity=604800000`).
- 비밀번호 암호화: `CustomPasswordEncoder` (SHA-256 + Base64).
- Access/Refresh Token은 `CookieUtil`로 httpOnly 쿠키에 설정.
- 보호 API는 쿠키 자동 전송 기본. `JwtAuthenticationFilter`는 `Authorization: Bearer`를 폴백으로만 허용.
- 공개 엔드포인트: `/api/auth/login`, `/api/auth/refresh`, `/swagger-ui/**`, `/v3/api-docs/**`.
- 회원가입 엔드포인트(`/api/auth/signup`)는 `SecurityConfig`에서 `hasRole("ADMIN")`로 보호합니다. 임직원 포털 특성상 자유 가입 API로 취급하지 않습니다.
- 관리자 전용: `/api/admin/**` — SecurityConfig URL 패턴 + `@PreAuthorize("hasRole('ADMIN')")` 이중 보호.
- **관리자 전용 도메인 API** (`/api/admin/**` 외 경로라도 관리자만 접근해야 하는 엔드포인트): 컨트롤러 **클래스 레벨**에 반드시 `@PreAuthorize("hasRole('ADMIN')")` 적용. SecurityConfig URL 패턴은 `/api/admin/**`에만 등록되므로 도메인 컨트롤러는 어노테이션으로 보호해야 함. 누락 시 인증된 모든 사용자가 API 직접 호출 가능.
  ```java
  // 관리자 전용 컨트롤러 — 클래스 레벨 적용 필수
  @RestController
  @RequestMapping("/api/plans")
  @RequiredArgsConstructor
  @PreAuthorize("hasRole('ADMIN')")   // ← 누락 금지
  public class PlanController { ... }
  ```
  현재 적용 대상: `PlanController`, `BudgetStatusController`, `BudgetWorkController`.
- **권한 검증 보강 현황** (코드 분석 기준, 2026-05-17): `FileController`는 단건 삭제 경로에 `FileOwnershipChecker.checkOwnership()`이 적용되어 있으나 다운로드·미리보기·조회·메타수정·원본 기준 일괄삭제 권한 검증은 후속 과제입니다. `GeminiController`는 `@PreAuthorize("hasRole('ADMIN')")`로 관리자 전용 처리합니다. `UserController`, `OrganizationController`, `ProjectController`, `ApplicationController`의 부서/소유권 정책은 업무 요건에 맞춰 별도 검토합니다.
- RBAC 모델: 자격등급(`CauthI`) + 역할 매핑(`CroleI`).
  - `ITPAD001` = 시스템관리자
  - `ITPZZ001` = 일반사용자
  - `ITPZZ002` = 기획통할담당자
- CORS: `cors.allowed-origins=http://localhost,http://localhost:3000,http://localhost:3002` (개발 프론트 및 E2E).
- 운영: `app.cookie.secure=true` + HTTPS 필수. `app.cookie.secure` 기본값이 `false`이므로 운영 프로파일에서 반드시 오버라이드해야 합니다.
- **`Authorization: Bearer` 헤더 폴백**: Swagger/Postman 편의를 위해 허용되어 있으나 운영 환경에서도 동작합니다. 운영 전환 전 비활성화 여부를 결정하고 이 문서에 명시합니다.
- **파일 업로드 확장자 검증**: `FileService.uploadFileInternal()` 진입 시점에 `FileValidator.validateExtension()`을 호출합니다.
- **로그인 Brute-force 보호**: `LoginAttemptService`가 사번 기준 5회 실패/10분 잠금을 적용합니다.
- Brute-force 판정은 인메모리 카운터가 아니라 `TPRMPP_CLOGNH`의 `LOGIN_FAILURE` 이력을 `LoginHistoryRepository.countByEnoAndLgnTpAndLgnDtmAfter()`로 집계합니다.
- **X-Forwarded-For 신뢰**: `AuthController.getClientIp()`가 헤더를 무조건 신뢰합니다. 운영 인프라(Nginx 등)에서 헤더를 덮어쓰도록 설정해야 IP 위조를 방지할 수 있습니다.
- **비밀값 기본값 금지**: `application.properties`의 `${VAR:default}` 형태 기본값은 환경변수 미설정 시 운영에 그대로 사용됩니다. `:default` 부분을 제거하고 구동 시 빈값이면 즉시 실패하도록 해야 합니다.
- **비밀번호 해시 규격(KDB 표준)**: `CustomPasswordEncoder`는 사내 SSO·통합인증 시스템과의 호환을 위해 KDB 표준 암호화 규격(SHA-256 + Base64, 고정 솔트 파라미터)을 적용합니다. 알고리즘·솔트 파라미터는 거버넌스 승인 없이 변경할 수 없으며, 차세대 인증체계 전환은 별도 트랙으로 관리합니다. 클래스에는 정책 예외 표시(`@SuppressWarnings` 4건 + `NOSONAR` 마커)가 부여되어 있으므로 자동화 보안 점검 결과에 재등재하지 않습니다.
- CORS: `cors.allowed-origins`는 `http://localhost,...` (개발값)이 기본입니다. 운영 배포 시 `https://it.kdb.co.kr` 등 실제 오리진으로 환경변수 오버라이드가 필수이며, 구동 시 검증 로직이 없으므로 배포 체크리스트에 포함해야 합니다.
- 운영 비밀값: `spring.datasource.password`, `jwt.secret`, `gemini.api.key`는 환경변수 또는 프로파일별 비공개 설정에서 주입합니다.

### 5.7 채번/주요 비즈니스 제약
- 채번 규칙(관리번호 포맷)은 → [`docs/guides/data-model.md#3-채번-규칙`](docs/guides/data-model.md) 참조.
- 신청 상태가 **"결재중"** 또는 **"결재완료"**인 경우 연결된 프로젝트 수정/삭제 불가.
- 프로젝트 수정 시 품목(`Bitemm`) 동기화: 요청에 포함된 품목은 추가/수정, 누락된 기존 품목은 Soft Delete.
- 검토의견(`Brivgm`)은 문서에 종속. 삭제는 논리 삭제 우선 검토.
- 예산현황 조회는 `BudgetStatusQueryRepository` 집계 쿼리 기준. 화면 요구사항 변경 시 DTO·쿼리 동기 갱신.

### 5.8 환경 설정 키
- JWT: `jwt.secret`, `jwt.access-token-validity`, `jwt.refresh-token-validity`
- CORS: `cors.allowed-origins`
- 쿠키: `app.cookie.secure`
- 파일: `app.file.base-path=C:/data/files`, multipart 최대 파일 50MB / 요청 200MB
- Gemini: `gemini.api.key`, `gemini.api.base-url`, `gemini.api.model=gemini-2.5-flash`
- 서버 식별자: `app.server.instance-id=SVR1`

### 5.9 테스트 기준
- 기능 변경 후 최소 `./gradlew test` 실행.
- 인증/결재/파일/QueryDSL 집계/변경 로그 등 공통 영향 변경은 `./gradlew clean test`로 재검증.

### 5.10 기동 시 환경변수 검증
- `EnvironmentValidator` (`common/system/EnvironmentValidator.java`): `@PostConstruct`에서 `spring.datasource.password`, `jwt.secret` 프로퍼티 해석 결과를 검사.
- 해석 결과가 빈값이면 `IllegalStateException`으로 즉시 구동 실패합니다. 다만 현재 `application.properties`에는 `DB_PASSWORD`, `JWT_SECRET` 기본값이 남아 있어 환경변수 미설정도 통과하므로 운영 프로파일에서는 기본값 제거가 필요합니다.
- 환경변수 추가 시 `EnvironmentValidator` 목록에도 함께 등록.

### 5.11 파일 보안
- `FileValidator` (`infra/file/FileValidator.java`): 허용 확장자 화이트리스트 검증 — `FileService.uploadFileInternal()` 진입 시점 호출.
- `FileOwnershipChecker` (`infra/file/FileOwnershipChecker.java`): 파일 소유자 및 도메인별 읽기 권한 검증. 현재 코드상 단건 삭제의 `checkOwnership()`은 적용되어 있으나, 다운로드/미리보기/목록/단건조회/메타수정/원본 기준 일괄삭제 경로의 읽기·쓰기 권한 검증은 후속 과제로 관리합니다.
- `/api/files/**`는 `SecurityConfig`에서 인증만 요구합니다. 새 파일 API를 추가할 때는 `flMngNo` 기반 조회/다운로드/미리보기에는 `FileOwnershipChecker.checkReadAccess()` 또는 `orcDtt`별 권한 검증을 반드시 연결합니다.
- `FileOwnershipChecker.checkReadAccess()`는 현재 `orcDtt="공통게시판"`만 게시판 권한 정책으로 특수 검증하고, 그 외 원본구분은 읽기를 허용합니다. 새 `orcDtt`를 도입할 때는 파일 권한 정책 등록 여부를 함께 결정합니다.
- 허용 확장자 변경 시 `FileValidator.ALLOWED_EXTENSIONS` 상수 수정.

### 5.11.1 Gemini AI 보안
- `GeminiController`는 `@PreAuthorize("hasRole('ADMIN')")`로 관리자 전용입니다.
- `GeminiService`는 파일 메타 조회, 파일 시스템 I/O, 외부 API 호출을 한 흐름에서 처리하지만 긴 외부 호출이 DB 트랜잭션을 점유하지 않도록 별도 `@Transactional` 경계를 두지 않습니다.
- 비관리자에게 Gemini 기능을 개방하기 전에는 첨부 `flMngNo`별 파일 접근 검증, 프롬프트 길이, 첨부 개수, 실제 파일 크기, 비용 상한을 먼저 구현합니다.
- `GeminiDto.Request` 검증 조건을 변경할 때는 `GeminiService.generate()`의 null/길이 처리와 함께 테스트를 갱신합니다.

### 5.12 로그인 Brute-force 보호
- `LoginAttemptService` (`common/iam/service/LoginAttemptService.java`): `TPRMPP_CLOGNH` 로그인 이력 기반 실패 횟수 집계.
- 임계값: 5회 실패 / 10분 잠금.
- `AuthService.login()`: 로그인 검증 전에 `checkLocked(eno)`를 호출하고, 실패 이력은 기존 로그인 이력 저장 흐름을 통해 남깁니다.
- **주의**: DB 이력 기준이므로 서버 재시작에는 유지되지만, IP·기기 기준 제한은 아직 없습니다.

### 5.12.1 감사 로그(BaseLogEntity) 패턴
- **로그 엔티티**: 23개 (*L 접미사, 예: `BprojmL`, `CcodemL`, `CapplmL`).
- **기본 구조**: `BaseLogEntity` 상속 — 기본 컬럼 자동 포함 (GUID, FST_ENR_DTM/USID, LST_CHG_DTM/USID).
- **로그 리스너**: `ChangeLogEntityListener` → JPA entity lifecycle 후킹 → `AuditLogPersister` → DB 저장.
- **저장 시점**: JPA `@PrePersist`/`@PreUpdate` 콜백 중 `ChangeLogEntityListener`가 `AuditLogPersister.persist()`를 직접 호출해 현재 flush 흐름에서 로그를 저장합니다. 로그 저장 실패는 catch 후 warn 처리하여 원본 작업 롤백을 피합니다.
- 로그 조회는 감사 도메인(`domain/audit`) 또는 분석용 view 사용.

### 5.12.2 이벤트 리스너(@EventListener vs @TransactionalEventListener)
- **`@EventListener`**: 발행자와 동일 트랜잭션에서 **동기 실행**. 리스너 실패 시 원본 트랜잭션 롤백.
  - 사용 예: `CouncilApprovalEventListener` — 결재 완료 이벤트 → 협의회 상태 자동 전이. 협의회 상태 변경 실패 시 결재 원본 작업도 함께 롤백.
- **`@TransactionalEventListener`**: 발행자 트랜잭션 커밋 **후** 비동기 실행. 리스너 실패 시 원본 무영향.
  - 사용 예: 알림 발송, 메일 전송, 별도 시스템 동기화 (부수 효과).
- 선택 기준: 상태 일관성이 필수 → `@EventListener`, 실패해도 괜찮은 부가 작업 → `@TransactionalEventListener`.

### 5.13 공통 게시판 패턴
- 백엔드 패키지: `common/board`.
- 주요 엔티티: `Cblbmm`(게시판 메타, `TPRMPP_CBLBMM`), `Cblbcm`(게시물, `TPRMPP_CBLBCM`), `Ccmmtm`(댓글, `TPRMPP_CCMMTM`).
- 주요 API:
  - `GET /api/boards/meta`, `GET /api/boards/meta/{blbMngNo}` — 인증 사용자 공통 게시판 메타 조회.
  - `/api/admin/boards/meta/**` — 관리자 전용 게시판 메타 CRUD. `AdminBoardMetaController` 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 필수.
  - `/api/boards/{blbMngNo}/posts/**` — 게시물 목록·상세·등록·수정·삭제·답변글.
  - `/api/boards/{blbMngNo}/posts/{nacMngNo}/comments/**` — 댓글·대댓글 CRUD.
- 게시물/댓글 본문은 저장 전 `HtmlSanitizer.sanitize()` 적용 필수.
- 게시판 권한은 메타의 `inqAthC`, `enrAthC`, `bbrLmtnUseYn`, `bbrLmtnC`와 서비스 계층 검증으로 판단합니다. 프론트 메뉴 숨김은 UX 보조일 뿐 최종 보안 경계가 아닙니다.
- 게시물/댓글 트리는 그룹번호·그룹순서·그룹레벨(`*_GRP_NO`, `*_GRP_SQN`, `*_GRP_LEV`)로 정렬합니다.

### 5.14 부서 필터링 패턴 (bbrC)
- 신규 목록 API는 `@RequestParam(required = false) String bbrC` 추가 필수.
- QueryDSL: `StringUtils.hasText(bbrC)` 체크 후 `builder.and(entity.bbrC.eq(bbrC))` 추가.
- `bbrC` null·빈 문자열이면 전체 조회 (관리자·SSO 미동기화 계정 모두 동일 경로).
- **하위 호환**: 기존 `bbrC` 없는 호출자는 전체 조회 그대로 동작. Breaking change 없음.
- TDD 의무: Repository/Service 변경 시 `bbrC` 지정 케이스와 null 케이스 모두 테스트 추가.
  ```java
  // RepositoryImpl 조건 패턴
  if (StringUtils.hasText(bbrC)) {
      builder.and(entity.bbrC.eq(bbrC));
  }
  ```

### 5.15 사전협의 검토자 API
- `ReviewerController`: `GET /api/reviews/{docMngNo}/reviewers` — 사전협의 문서 검토자 목록 반환.
- `ReviewerService.REVIEW_TEAM_MAP`: 팀코드 → 팀명 매핑 (`12004`=계약팀, `18001`=기획팀, `18010`=PMO팀, `18501`=개발/운영팀).
- 각 팀에서 첫 번째 사용자 1명만 포함. 팀원 없으면 해당 팀은 결과 제외.
- 검토 팀 추가·변경 시 `ReviewerService.REVIEW_TEAM_MAP`과 프론트엔드 `ReviewerTeam` 타입(`types/review.ts`) 동시 갱신.

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
