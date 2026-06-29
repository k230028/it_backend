---
[ 백엔드 가이드 ]
본 파일은 IT Portal 백엔드의 SoT(Single Source of Truth)입니다.
기술 스택, 아키텍처, 보안 정책의 원본은 여기에 있습니다.
공통 규약(한글 주석, 문서 운영 등)은 루트 `../CLAUDE.md` 참조.
---

## 1. 개요
- 패키지 루트: `com.kdb.it`
- 프로젝트 개요/목적은 루트 `../CLAUDE.md` §1 참조.
- 작업 워크플로우는 루트 `../CLAUDE.md` §5를 따릅니다. Superpowers를 기본으로 사용하고, ECC/gstack은 보조 도구로 사용합니다.

## 2. 기술 스택
- Framework: Spring Boot 4.1.0
- Language: Java 25
- Build: Gradle (Groovy DSL, `build.gradle`)
- ORM: Spring Data JPA + QueryDSL 5.1.0
- Security: Spring Security + JWT (JJWT 0.13.0)
- Database: Oracle Database — **전 환경(로컬/개발/운영) 공통 스키마 분리 구조**
  - 접속 계정은 `ITPAPP`, 테이블/시퀀스 소유 스키마는 **`ITPOWN`** — `ITPOWN.테이블명`으로 접근.
  - 베이스 `application.properties`의 `spring.datasource.hikari.connection-init-sql=ALTER SESSION SET CURRENT_SCHEMA=${DB_SCHEMA:ITPOWN}`이
    세션 스키마를 전환하므로 코드(엔티티/네이티브 쿼리)에 스키마 접두어를 하드코딩하지 않습니다.
    (hibernate `default_schema`는 네이티브 쿼리에 적용되지 않아 CURRENT_SCHEMA 방식을 사용.)
  - ITPAPP 계정에는 ITPOWN 객체에 대한 SELECT/INSERT/UPDATE/DELETE 및 시퀀스 SELECT 권한이 부여되어 있어야 합니다.
    (로컬 XE는 ITPAPP에 DBA 롤이 있어 별도 객체 권한 없이 동작.)
- API 문서화: SpringDoc OpenAPI 3.0.3 (Swagger UI)
- 유틸: Lombok, Jsoup 1.18.3

## 3. 주요 명령어
- `./gradlew build` — 빌드
- `./gradlew bootRun` — 개발 서버 실행
- `./gradlew test` — 테스트 실행
- `./gradlew clean test` — 전체 테스트 재검증
- `./gradlew clean build` — 클린 빌드
- Swagger UI: http://localhost:28080/swagger-ui/index.html

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
│   ├── admin/     - 시스템관리, 관리자 로그, 실시간 로그 모니터링(ROLE_ADMIN 전용)
│   ├── approval/  - 결재
│   ├── board/     - 공통 게시판 (메타, 게시물, 댓글)
│   ├── code/      - 공통코드
│   ├── iam/       - 사용자/조직/권한
│   ├── notification/ - 알림 (이벤트 발행 → 비동기 적재 → 채널별 디스패처)
│   ├── system/    - 인증·보안 (JwtUtil, JwtAuthenticationFilter), 환경검증, tiptap 변수
│   └── util/      - 공통 유틸 (CookieUtil, HtmlSanitizer, CustomPasswordEncoder 등)
├── domain/        - 비즈니스 도메인
│   ├── budget/    - 예산 관리 (it, project, cost, document, plan, status, work)
│   ├── estimate/  - 정보화사업 집행 ① 소요예산 산정 (Bestim 기본 + Besttm 팀별 상세)
│   ├── deliberation/ - 정보화사업 집행 ② 과업심의위원회 (Bdelim)
│   ├── contract/  - 정보화사업 집행 ③ 입찰/계약 (Bcontm)
│   ├── payment/   - 정보화사업 집행 ④ 대금지급 (Bpaymm 기본 + Bpaymt 회차별 상세)
│   ├── council/   - 정보화실무협의회
│   ├── log/       - 변경 로그 (BaseLogEntity, *L 엔티티, ChangeLogEntityListener)
│   ├── menu/      - DB 기반 메뉴 트리, 라우트 카탈로그, 권한 매핑
│   └── entity/    - BaseEntity
├── exception/     - 전역 예외 핸들러
└── infra/         - 인프라 도메인 (ai, eai, file)
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
- 4자리 도메인 : 용도에 따라 지정
  - 예: `BLBC` (사업), `CBLB` (게시판), `CINFM` (알림), `CLOGN` (로그인), `BBLG` (변경로그)
- 1자리 용도 : M (마스터), L (로그), H (이력)

### 5.2 엔티티 설계
- 모든 업무 엔티티는 **`BaseEntity` 상속** (공통 컬럼: `DEL_YN`, `GUID`, `FST_ENR_DTM/USID`, `LST_CHG_DTM/USID`).
- 감사 로그 필요 엔티티: 업무 엔티티는 **`BaseEntity` 상속 + `@LogTarget(entity = XxxL.class)` 어노테이션** 부착, 짝이 되는 **`*L` 로그 엔티티가 `BaseLogEntity` 상속**. (업무 엔티티 자체가 `BaseLogEntity`를 상속하지 않음에 주의.)
  - 현재 적용: 30쌍 (업무 엔티티 `@LogTarget` ↔ `*L` 로그 엔티티, 코드 분석 2026-06-24). 예: `Bprojm`↔`BprojmL`, `Bitemm`↔`BitemmL`, `Cblbcm`↔`CblbcmL`, `Capplm`↔`CapplmL`, `Ccodem`↔`CcodemL`, `Bestim`↔`BestimL`(사업집행 4단계 6쌍 신규).
  - 로그 생성 메커니즘: JPA `@PrePersist`/`@PreUpdate` → `ChangeLogEntityListener` → `AuditLogPersister.persist()`.
- 삭제는 항상 **Soft Delete**(`delete()` → `DEL_YN='Y'`). 물리 삭제 금지.
- 엔티티 명칭은 메타 문서 반드시 용어사전 기반으로 지정 (필수).
  - 예: 삭제여부=`DEL_YN`, 생성자사번=`FST_ENR_USID`, 변경자사번=`LST_CHG_USID`.
- `@Column` 주석(comment) 필수 지정.
- 복합 기본키: `@IdClass` 패턴 사용 (예: `CcodemId`, `BitemmId`, `BprojmId`, `CdecimId`).
```java
    @Column(name = "ORC_TB_CD", length = 10, comment = "원본테이블코드")
    private String orcTbCd;
```
#### 5.2.1 컬럼명/타입 변경 가이드
`spring.jpa.hibernate.ddl-auto=update`는 컬럼 RENAME과 타입 변경을 지원하지 않습니다.
엔티티에서 `@Column(name=...)`만 바꾸면 옛 컬럼이 그대로 남고 새 컬럼이 추가되며,
타입이 다르면 ALTER 자체가 실패합니다. 마이그레이션 SQL을 함께 작성합니다.

| 변경 유형 | 처리 패턴 |
|---|---|
| 컬럼명만 변경 | `ALTER TABLE T RENAME COLUMN OLD TO NEW;` |
| 타입 변경 (DATE → VARCHAR2 등) | 임시 컬럼 추가 → `UPDATE`로 변환 복사 → 옛 컬럼 `DROP` → 임시 컬럼 `RENAME` |
| PK 컬럼 타입 변경 | 단일 컬럼은 위 패턴, PK 제약 포함 시 `DROP TABLE` 후 ddl-auto 재생성 (테스트 환경) |
| NOT NULL 신규 추가 | 기존 데이터 백필 → `MODIFY ... NOT NULL` |

`DROP TABLE ... CASCADE CONSTRAINTS`는 관련 시퀀스를 함께 삭제하므로
`app_sequences_ddl.sql` + `audit_log_sequences_ddl.sql`을 재실행해 비즈니스/로그 시퀀스를 복구합니다.

참고 스크립트(`it_backend/src/main/resources/sql/`):
- `migrate_dt_to_varchar2.sql` — DATE → VARCHAR2(8) 변환 (DT 도메인)
- `backfill_chg_tc.sql` + `enforce_chg_tc_notnull.sql` — NOT NULL 신규 추가 (CHG_TC)
- `app_sequences_ddl.sql` — 비즈니스 채번 시퀀스 (S_ASCT/S_QTN/S_APF/S_APF_REL_SNO/S_FL)
- `audit_log_sequences_ddl.sql` — 로그 시퀀스 (S_{POSTFIX} 22개)

운영/개발 공통 변경은 `../it_database/migrations/`에 Flyway 스크립트를 추가합니다.
백엔드 Gradle `processResources`가 해당 V* 스크립트를 `classpath:db/migration`으로 포함합니다.
자동 적용은 `local-ext`/`local-int` 프로파일에서만 켜며, `dev`/`prod` DB는 DBA가 검토 후 수동 적용합니다.
적용된 스크립트는 체크섬 추적 대상이므로 수정하지 않고, 추가 변경은 항상 새 버전 스크립트로 작성합니다.

### 5.3 DTO 설계
- 관련 DTO는 **정적 중첩 클래스**로 한 파일에 묶음 (예: `AuthDto.LoginRequest`).
- Swagger 문서를 위해 `@Schema(name, description)` 필수.

### 5.4 Repository 패턴
- 기본 CRUD: `JpaRepository` 상속.
- 동적·복잡 쿼리: `RepositoryCustom` 인터페이스 + `RepositoryImpl` 구현(QueryDSL).
- 시퀀스 등 DB 종속 쿼리: `@Query(nativeQuery = true)`.
- 게시판 목록 검색처럼 공개 기간·권한·부서 조건이 함께 필요한 쿼리는 QueryDSL `BooleanBuilder`로 조립하고, 조건별 의도를 JavaDoc 또는 인접 주석으로 남깁니다.

#### 5.4.1 QueryDSL 작성 규칙
- `BooleanBuilder` 사용: 동적 조건 조립용.
- 조건 추가 패턴: `if (StringUtils.hasText(filterValue)) { builder.and(...); }`.
- 페이지네이션: `.offset()` 및 `.limit()`로 구현.
- 정렬: `.orderBy()`에 QueryDSL `OrderSpecifier` 사용.
- 에러 핸들링: 쿼리 오류 시 상위 계층으로 예외 전파 (Repository는 DB 예외 변환 X).
- 예시:
  ```java
  BooleanBuilder builder = new BooleanBuilder();
  
  // 선택적 조건들
  if (StringUtils.hasText(bbrC)) {
      builder.and(entity.bbrC.eq(bbrC));
  }
  if (status != null) {
      builder.and(entity.status.eq(status));
  }
  
  // 쿼리 실행
  List<Entity> results = queryFactory
      .selectFrom(entity)
      .where(builder)
      .orderBy(entity.createdAt.desc())
      .offset(offset)
      .limit(limit)
      .fetch();
  ```

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

### 5.5.3 `@RequestParam` / `@PathVariable` name 명시 필수 (반복 발생 함정)
**모든 `@RequestParam`, `@PathVariable`, `@RequestHeader`에 `name`(또는 `value`)을 명시합니다.**
Java 25 / Spring Boot 4 환경에서 컴파일러 `-parameters` 옵션이 누락되거나 Hibernate/Spring
proxy가 파라미터명을 reflection으로 못 읽으면 다음 예외가 모든 요청마다 발생합니다:

```
IllegalArgumentException: Name for argument of type [java.time.LocalDateTime] not specified,
  and parameter name information not available via reflection.
  Ensure that the compiler uses the '-parameters' flag.
```

WebMvcTest 환경에서는 잘 통과하는데 실제 `bootRun`에서만 깨지는 경우가 있어 발견이 늦습니다.

**필수 패턴:**
```java
public Snapshot get(
        @RequestParam(name = "since", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
        @RequestParam(name = "limit", defaultValue = "200") int limit,
        @RequestParam(name = "tables", required = false) String tables
) { ... }

public Detail get(@PathVariable(name = "id") Long id) { ... }
```

**금지:**
```java
public Snapshot get(
        @RequestParam(required = false) LocalDateTime since,   // ❌ name 누락
        @RequestParam(defaultValue = "200") int limit          // ❌ name 누락
) { ... }
```

### 5.5.4 네이티브 쿼리 결과 매핑 시 환경별 타입 차이 (Hibernate 6 / Oracle JDBC)
`createNativeQuery`의 `Object[]` 결과를 직접 캐스트하면 환경에 따라 ClassCastException이 발생합니다.

| 컬럼 타입 | Oracle JDBC가 반환하는 실제 타입 | 안전 변환 |
|-----------|--------------------------------|----------|
| `VARCHAR2(1)` | `Character` 또는 `String` | `v == null ? null : v.toString()` |
| `TIMESTAMP` | `java.sql.Timestamp` 또는 `LocalDateTime` (Hibernate 6+) | `instanceof` 분기로 변환 |
| `NUMBER` | `BigDecimal`, `Long`, `Integer` 등 | `((Number) v).longValue()` |

**필수 헬퍼 패턴 (`RealtimeLogRepository` 참고):**
```java
private static String toStr(Object v) {
    return v == null ? null : v.toString();
}
private static LocalDateTime toLdt(Object v) {
    if (v == null) return null;
    if (v instanceof LocalDateTime ldt) return ldt;
    if (v instanceof Timestamp ts) return ts.toLocalDateTime();
    throw new IllegalStateException("지원하지 않는 시각 타입: " + v.getClass());
}
```

직접 `(String) r[3]`, `(Timestamp) r[4]` 캐스트는 금지. QueryDSL/JPQL은 자동 매핑되므로 본 규칙은
네이티브 쿼리 한정.

### 5.5.6 RestClient/RestTemplate 응답을 Jackson 버전 특정 `JsonNode`로 받지 말 것 (Jackson 2/3 공존 함정)
**`RestClient`/`RestTemplate` 응답 본문은 `com.fasterxml.jackson.databind.JsonNode`(Jackson 2)나
`tools.jackson.databind.JsonNode`(Jackson 3) 같은 버전 특정 타입으로 받지 말고, `Map<String,Object>`
또는 전용 DTO로 역직렬화합니다.**

- **이유**: Spring Boot 4(Spring Framework 7) 클래스패스에는 Jackson 2(`jackson-databind-2.x`)와
  Jackson 3(`jackson-databind-3.x`, 패키지 `tools.jackson`)이 **공존**하며, 기본 JSON 메시지 컨버터는
  **Jackson 3**(`JacksonJsonHttpMessageConverter`)로 동작합니다. 이때 코드가 Jackson 2의 `JsonNode`로
  역직렬화를 요청하면 다음 예외가 발생합니다:
  ```
  HttpMessageConversionException: Type definition error:
    [simple type, class com.fasterxml.jackson.databind.JsonNode]
  ```
- **함정의 은닉성**: 외부망/CI에서는 외부 서버 미도달→타임아웃→폴백 경로라 컨버터가 실행되지 않아
  드러나지 않고, 서버가 실제 응답을 주는 **내부망 구동에서만** 재현됩니다. 또한 `RestClient`를 mock하는
  단위 테스트는 컨버터 경로를 건너뛰어 통과하므로 회귀로 잡히지 않습니다. 실제 컨버터를 거치는 통합
  테스트(로컬 `HttpServer` + 실제 RestClient, `SsoAgentClientHttpIntegrationTest` 참고)로 검증합니다.
- **올바른 패턴** (`SsoAgentClient` 참고): `Map<String,Object>`로 받고 키 접근:
  ```java
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
          new ParameterizedTypeReference<>() {};
  Map<String, Object> body = restClient.get().uri(url).retrieve().body(MAP_TYPE);
  ```
- 단순 import만 Jackson 3 `JsonNode`로 바꾸는 것은 비권장: Jackson 3는 `asText()`→`asString()` 등
  메서드/의미가 달라져 미묘한 회귀 위험이 있습니다. 버전 비의존(`Map`/DTO)이 표준입니다.
- 참고: `JacksonConfig`의 Jackson 2 `ObjectMapper` 빈은 정적 `RestClient.builder()`로 만든 클라이언트에
  주입되지 않습니다. 앱 JSON 설정을 따르려면 오토컨피그된 `RestClient.Builder`를 주입해야 하나, 위
  `Map` 패턴은 컨버터 종류와 무관하게 동작하므로 별도 설정이 필요 없습니다.

### 5.5.7 RestClient 쿼리 파라미터의 `+`는 UriComponentsBuilder가 인코딩하지 않음 (base64 깨짐 함정)
**base64/암호문처럼 `+`를 포함하는 값을 `RestClient`/`RestTemplate` 쿼리 파라미터로 보낼 때는
값을 직접 `URLEncoder.encode`로 인코딩한 뒤 `UriComponentsBuilder...build(true)`로 조립합니다.**

- **이유**: `UriComponentsBuilder.queryParam(...).build().encode()`는 `+`를 쿼리 컴포넌트에서 합법
  문자(RFC 3986 sub-delim)로 보아 **인코딩하지 않습니다**. 그 URL을 받은 서버가 쿼리를
  application/x-www-form-urlencoded 규칙으로 디코딩하면 `+`를 **공백**으로 바꿔 값이 깨집니다.
  SSO(ISign+) `/token/authorization`에서 base64 `secureToken`의 `+`가 공백이 되어 `resultCode 310001
  "토큰 복호화 실패"(Failed to decode the token)`가 발생한 사례가 있습니다(`SsoAgentClient`).
- **올바른 패턴**: `URLEncoder.encode(value, UTF_8)`로 `+`→`%2B`, `/`→`%2F`, `=`→`%3D` 인코딩 후
  `.build(true)`(이미 인코딩됨)로 조립. 구 JSP Web Agent의 `NameValuePair`(commons-httpclient)
  인코딩과 동일한 결과가 됩니다.
- 회귀: `SsoAgentClientTest.authorize_base64Token_percentEncoded`(URI 캡처),
  `SsoAgentClientHttpIntegrationTest`(실제 서버 폼디코딩 라운드트립).
- 참고: 응답 역직렬화 측 함정은 §5.5.6(Jackson 2/3)과 별개입니다(이건 요청 인코딩).

### 5.5.5 DB 기반 메뉴 트리 규칙
- 사용자 메뉴는 `MenuQueryService.getMenuTree()`가 `Cmenum`/`Cmenua`를 기준으로 권한 필터링하고, 프론트는 `useMenu()` 응답을 사이드바와 Breadcrumb의 단일 소스로 사용합니다.
  - `MenuDto.Node.athIds` 필드: 노드별 노출 권한ID 목록 (빈 목록=전체 공개). 사용자 트리도 노드별 `athIds`를 실어 사이드바/헤더가 관리자 전용 메뉴(왕관 아이콘)를 표시할 수 있도록 지원합니다. 관리 트리는 편집 폼 권한 복원용으로도 사용됩니다.
- 메뉴 유형은 `LNK`, `GRP`, `DYN`, `HED`를 허용합니다 (`AdminMenuService` 검증 기준). `HED`(헤더)는 트리 최상위 전용으로 상위 메뉴를 가질 수 없고, 비-HED는 반드시 상위 메뉴가 필요합니다. `LNK`는 `Cmenud` 라우트 카탈로그에 등록된 `srePth`가 필수이고, `GRP`/`DYN`/`HED`는 화면 경로를 가질 수 없습니다.
- 메뉴 깊이는 최대 3단입니다. 이동 시 `AdminMenuService.move()`가 `WHL_MNU_PTH`와 `MNU_DEP`를 하위 트리까지 재계산합니다.
- `DYN` 메뉴의 자식은 `MenuChildrenResolver` 구현체가 생성합니다. 현재 게시판 목록은 `BoardListMenuResolver`가 권한 필터링된 자식 노드를 제공합니다.
- 프론트 메뉴 숨김은 UX 보조입니다. 최종 보안 경계는 백엔드 API 권한(`SecurityConfig`, `@PreAuthorize`, 서비스 권한 검증)입니다.

### 5.6 인증 및 보안 (전사 SoT)

#### JWT 토큰 정책 (JwtUtil 코드 기준)
- 인증 방식: **httpOnly 쿠키 기반 JWT**(Stateless). DB 세션 없음.
- Access Token 유효시간: **15분** (`jwt.access-token-validity=900000` ms).
- Refresh Token 유효시간: **7일** (`jwt.refresh-token-validity=604800000` ms).
- JWT 서명 알고리즘: HMAC-SHA (JJWT `Keys.hmacShaKeyFor()`, 키 길이 최소 256비트 필수).
- JWT 클레임 구성 (`JwtUtil.generateAccessToken()`):
  - `sub` — 사번 (eno)
  - `athIds` — 자격등급 ID 목록 (JSON 배열, 예: `["ITPAD001"]`)
  - `bbrC` — 소속 부서코드
  - `iat` — 발급 시각, `exp` — 만료 시각
- Refresh Token은 `athIds`/`bbrC` 클레임 없이 `sub`+`iat`+`exp`만 포함.
- Refresh Token 갱신 시 최신 자격등급을 DB에서 재조회하여 Access Token 생성 (`AuthService.refreshAccessToken()`).
- **1인 1 Refresh Token 정책**: 로그인 시 기존 Refresh Token을 삭제 후 신규 저장 (`refreshTokenRepository.deleteByEno(eno)` → save).
- Refresh Token 검증은 3단계: JWT 서명 검증 → DB 존재 여부 → DB `endDtm` 만료 여부.
- **`athIds` 클레임 타입 불일치 시**: 빈 리스트 반환 → `CustomUserDetails`가 기본값 `ITPZZ001`(일반사용자) 적용. warn 로그 없음 — 권한 강등 탐지 어려움 (TASK.md 등록).

#### 쿠키 정책 (CookieUtil 코드 기준)
- Access/Refresh Token은 `CookieUtil`로 httpOnly 쿠키에 설정. JavaScript 접근 불가.
- Access Token 쿠키: `path="/"`, `maxAge=900초(15분)`, `sameSite=Lax`.
- Refresh Token 쿠키: `path="/api/auth"` (인증 경로 전송 제한), `maxAge=604800초(7일)`, `sameSite=Lax`.
- `it-portal-user` 쿠키: `httpOnly=false` — Nuxt 화면 인증 상태 복원용. 사번/이름/권한만 포함. JWT나 비밀값 삽입 금지.
- `app.cookie.secure` 기본값: `false`. 운영 프로파일에서 반드시 `true`로 오버라이드 필수.

#### 토큰 추출 우선순위 (JwtAuthenticationFilter 코드 기준)
1. `accessToken` httpOnly 쿠키 (브라우저 기본)
2. `Authorization: Bearer {token}` 헤더 (Swagger/Postman 폴백)

**`Authorization: Bearer` 헤더 폴백 주의**: Swagger/Postman 편의용이며 httpOnly 쿠키 전략을 우회할 수 있어 XSS 이후 2차 공격 경로가 됩니다. Bearer 헤더 폴백은 `app.auth.allow-bearer-header`(운영 기본 false)로 게이트 — 운영은 쿠키 전용, dev/swagger만 헤더 허용. 베이스 `application.properties`가 false, `dev`/`local-ext`/`local-int` 프로파일만 true이며 `prod`는 베이스 false를 상속합니다. 회귀: `JwtAuthenticationFilterTest`(`bearerIgnored_whenFlagFalse`/`bearerUsed_whenFlagTrue`).

#### @PreAuthorize 표준 패턴 (컨트롤러 코드 기준)
- `hasRole('ADMIN')` — 클래스 레벨 적용 필수. 메서드 레벨 개별 적용 금지 (누락 방지).
- `SecurityConfig`의 URL 패턴 보호는 `/api/admin/**`에만 적용됨. 도메인 컨트롤러는 어노테이션으로 이중 보호해야 함.

```java
// 관리자 전용 컨트롤러 — 클래스 레벨 적용 필수
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")   // ← 누락 금지
public class PlanController { ... }
```

#### IT부문 예산 API (ItBudgetController, domain/budget/it)
- URL: `/api/budget/it`
- 메서드: `GET /summary` (비목별 편성요청액·편성액), `GET /comparison` (전년도 대비 비교)
- **권한**: `@PreAuthorize("hasRole('ADMIN')")` — 클래스 레벨 적용, ADMIN 전용.

**현재 적용 대상** (클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")`, 코드 분석 2026-06-24 — 총 11개):
- `AdminController` (`common/admin`) — 시스템 관리
- `RealtimeLogController` (`common/admin/realtime`) — V_ITPAPP_LOG_FEED 기반 실시간 로그 모니터링
- `GeminiController` (`infra/ai`) — Gemini AI
- `AdminBoardMetaController` (`common/board`) — 게시판 메타 관리
- `BudgetStatusController` (`domain/budget/status`) — 예산 현황
- `BudgetWorkController` (`domain/budget/work`) — 예산 작업
- `PlanController` (`domain/budget/plan`) — 정보기술부문 계획
- `ItBudgetController` (`domain/budget/it`) — IT부문 예산 조회/비교
- `CouncilController` (`domain/council`) — 정보화실무협의회
- `AdminMenuController` (`domain/menu`) — 관리자 메뉴 관리
- `AdminRouteController` (`domain/menu`) — 라우트 카탈로그 관리

**SecurityConfig URL 패턴 보호 대상** (코드 분석 2026-06-05):
- `/api/admin/**` → `hasRole("ADMIN")` (`AdminController`, `AdminBoardMetaController`, `RealtimeLogController` 포함)
- `/api/auth/signup` → `hasRole("ADMIN")`
- `/api/plans/**` → `hasRole("ADMIN")` (`PlanController` 실제 경로 `/api/plans`와 정합 완료)

#### 부서 필터링(bbrC) 적용 규칙 (§5.14와 동일, 여기에 보안 관점 요약)
- `bbrC`는 JWT `athIds` 클레임의 소속 부서코드. Access Token 발급 시 DB에서 읽은 `user.getBbrC()`를 포함.
- 서비스 계층에서 `isAdmin()` 체크 후 관리자는 전체 조회, 일반 사용자는 `bbrC` 일치 항목만 반환. 프론트 필터링은 UX 보조일 뿐 최종 보안 경계가 아님.
- `bbrC` null인 경우 전체 조회. 관리자·SSO 미동기화 계정 동일 처리.

#### RBAC 모델 (CustomUserDetails 코드 기준)
| 자격등급 ID | Spring Security Role | 설명 |
|-----------|---------------------|------|
| `ITPAD001` | `ROLE_ADMIN` | 시스템관리자 — 전체 조회/수정/삭제, 관리자 메뉴 |
| `ITPZZ002` | `ROLE_DEPT_MANAGER` | 기획통할담당자 — 소속 부서 조회/수정/삭제 |
| `ITPZZ001` | `ROLE_USER` | 일반사용자 — 소속 부서 조회, 본인 작성 수정 (기본값) |

- 다중 자격등급 지원: 한 사용자가 여러 `athIds` 보유 가능. 모든 등급에 대응하는 `GrantedAuthority` 등록.
- `isDeptManager()` 편의 메서드: `ITPZZ002` 또는 `ITPAD001`이면 `true` (관리자 포함).
- `athIds` null/빈 리스트이면 `ITPZZ001` 자동 적용 (방어 기본값).

#### 공개/비공개 엔드포인트 (SecurityConfig 코드 기준)
- 인증 불필요: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/sso/complete`, `/sso/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`
- 인증 필요 + ADMIN 전용: `/api/admin/**`, `/api/auth/signup`, `/api/plans/**`
- 나머지: 인증 필요 (`anyRequest().authenticated()`)

#### 개발 전용 API 보안 주의사항 (DevAuthController, SsoController 코드 기준)
- **`DevAuthController`**: `app.dev.user-switch.enabled=true`(기본값)이면 비밀번호 없이 임의 사번으로 JWT 발급 가능. `matchIfMissing=true`이므로 설정 누락 시 자동 활성화됨. **운영 배포 전 `app.dev.user-switch.enabled=false` 설정 필수.**
- **`SsoController`**: `app.sso.allow-direct-eno=false`(기본값). `true`이면 GET 파라미터 `eno=`로 SSO 없이 JWT 발급 가능. 기본값 유지 필수.
- SSO 리다이렉트 URL은 `cors.allowed-origins` 화이트리스트로 오픈 리다이렉트 방지 (`SsoController.getAllowedOrigin()`).
- SSO 완료 후 세션 키(`ssoVerifiedEno`)는 사용 즉시 `session.removeAttribute()`로 삭제 (재사용 방지).
- **운영 안전장치**: `EnvironmentValidator`는 운영 프로파일에서 `app.sso.allow-direct-eno=true` 및 `app.dev.user-switch.enabled=true`를 기동 차단. (§5.10 참조)

#### 로그인 Brute-force 보호 (LoginAttemptService, AuthService 코드 기준)
- 임계값: **사번 기준 5회 실패 / 10분 잠금** (`LoginAttemptService.checkLocked(eno)`).
- 판정: `TPRMPP_CLOGNH` DB 이력 기반 (`LoginHistoryRepository.countByEnoAndLgnTpAndLgnDtmAfter()`). 서버 재시작에도 유지됨.
- 잠금 판정은 로그인 검증 전 선행 호출 (`AuthService.login()` 진입 시 checkLocked 먼저).
- **IP/기기 기준 잠금 없음** — Credential stuffing 방어 미적용. TASK.md 등록 대상.
- **로그인 에러 메시지**: "사용자를 찾을 수 없습니다"와 "비밀번호가 일치하지 않습니다"로 분리됨 — 사번 열거 가능. 개선 대상.

#### 보안 헤더 설정 (SecurityConfig.headers() 코드 기준)
- `X-Content-Type-Options: nosniff` — MIME 스니핑 방지
- `X-Frame-Options: DENY` — 클릭재킹 방지
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` — HSTS
- `Content-Security-Policy`: `default-src 'self'; script-src 'self' 'sha256-<auto>'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; object-src 'none'`
  - `script-src`의 sha256 해시는 SSO CS 모드 saveToken POST 브리지 페이지의 고정 인라인 스크립트(`SsoController.CS_MODE_SUBMIT_SCRIPT` = `document.forms[0].submit()`)만 허용합니다. 해시는 `SecurityConfig.cspHash()`가 그 상수에서 **런타임 계산**하므로 스크립트 변경 시 자동 정합되고, 그 외 인라인 스크립트는 계속 차단됩니다.
  - `style-src 'unsafe-inline'` 포함 — CSS injection 벡터 존재. 개선 대상.

#### CORS 설정 (SecurityConfig.corsConfigurationSource() 코드 기준)
- `cors.allowed-origins`는 **`app.frontend-url`로 폴백**합니다: `cors.allowed-origins=${CORS_ALLOWED_ORIGINS:${app.frontend-url}}`, `app.frontend-url=${APP_FRONTEND_URL:}`. 즉 **프론트 URL 환경변수(`APP_FRONTEND_URL`) 하나만 지정하면 SSO 복귀 대상과 CORS 허용 오리진이 함께** 맞춰집니다(매번 두 값을 따로 설정 불필요). 다중 오리진이 필요할 때만 `CORS_ALLOWED_ORIGINS`(콤마 구분)로 CORS만 오버라이드. 둘 다 미설정이면 빈 목록=전체 차단(WARN 로깅). 개발(`dev`/`local-ext`/`local-int`) 프로파일도 같은 폴백 구조(`cors.allowed-origins=${CORS_ALLOWED_ORIGINS:${APP_FRONTEND_URL:http://localhost,http://localhost:3000,http://localhost:3002}}`, `app.frontend-url=${APP_FRONTEND_URL:http://localhost:3000}`)라, 환경변수 미설정 시 localhost 기본값을 유지하되 `APP_FRONTEND_URL`을 주면 SSO 복귀·CORS가 함께 그 값으로 바뀝니다(내부망 IP 테스트 시 cors를 따로 수정할 필요 없음 — 과거엔 이 프로파일들이 cors를 하드코딩해 IP 테스트에서 CORS가 막혔음). 회귀: `FrontendUrlPropertyResolutionTest`.
- `allowCredentials=true`이므로 와일드카드(`*`) 불가 — 반드시 명시적 도메인 나열. split 후 공백 제거 + 빈 항목 필터링으로 `[""]` footgun 방지.
- `allowedHeaders`는 명시 목록(`Content-Type`, `Authorization`, `X-Requested-With`). `allowedMethods`는 GET/POST/PUT/DELETE/OPTIONS/PATCH. `exposedHeaders`는 `Location`.
- **`/sso/**` 는 위 SPA allowlist 예외** — `UrlBasedCorsConfigurationSource`에 `/sso/**`(허용 origin `*`, `allowCredentials=false`)를 `/**`보다 **먼저** 등록해 우선 매칭시킵니다. `/sso/**`는 SPA의 XHR이 아니라 ESSO(외부 인증서버)가 브라우저를 통해 교차 출처로 콜백/리다이렉트하는 **전체 페이지 내비게이션** 엔드포인트라, SPA allowlist로 게이트하면 ESSO origin(예: `http://intesso.kdb.co.kr:20080`)이나 IP 기반 접근의 Origin이 목록에 없어 CorsFilter가 **`Invalid CORS request`(403)** 로 콜백을 차단합니다. 회귀: `SecurityConfigCorsTest`.
- **내부망/IP 접근 배포 주의**: 백엔드를 `http://10.9.16.x:28080`처럼 IP로 접근하면 SPA의 `/api/**` XHR Origin이 localhost allowlist에 없어 거부됩니다. 해당 환경에서는 `cors.allowed-origins`에 **실제로 서비스되는 프론트 origin**(IP:포트 포함)을 추가해야 합니다. SSO 콜백(`/sso/**`)은 위 예외로 영향 없음.
- 운영 배포 시 `https://it.kdb.co.kr` 등 실제 오리진으로 환경변수 오버라이드 필수. 구동 시 CORS 오리진 운영값 검증 로직은 없으므로 배포 체크리스트에 포함 필수.

#### 비밀값 관리 (application.properties, EnvironmentValidator 코드 기준)
- 운영 비밀값: `spring.datasource.password`, `jwt.secret`, `gemini.api.key`는 환경변수 주입 필수.
- `EnvironmentValidator`는 `DB_PASSWORD`, `JWT_SECRET` 프로퍼티 해석 결과가 빈값이면 구동 차단.
- **현재 `application.properties`에 `${DB_PASSWORD:kdb1234!!}`, `${JWT_SECRET:...}` 기본값이 남아 있어 환경변수 미설정 시 기본값으로 통과됨. 운영 프로파일에서 기본값 제거 필수.**
- `gemini.api.key`는 `${GEMINI_API_KEY:}` (빈 기본값)이므로 `EnvironmentValidator` 검증 대상에 추가 권장.

#### X-Forwarded-For 헤더 신뢰 (ClientIpResolver.resolve() 코드 기준)
- `ClientIpResolver.resolve()`가 직접 peer(`request.getRemoteAddr()`)가 `app.trusted-proxies` allowlist에 포함될 때만 `X-Forwarded-For` 최좌측 IP를 채택하고, 그 외에는 `remoteAddr`를 사용합니다(`Proxy-Client-IP`/`WL-Proxy-Client-IP` 폴백 없음).
- 신뢰 프록시 미설정 시 헤더를 무시하므로 IP 위조가 차단됩니다. 운영 인프라(Nginx) 앞단 프록시 IP를 `app.trusted-proxies`에 등록해야 실 클라이언트 IP가 정확히 기록됩니다.

#### 비밀번호 해시 규격 (CustomPasswordEncoder 코드 기준)
- SHA-256 + Base64 (고정 빈 솔트). KDB 사내 SSO 표준 규격이므로 거버넌스 승인 없이 변경 불가.
- `@SuppressWarnings` 4건 + `NOSONAR` 마커로 자동화 보안 점검 정책 예외 처리됨. 보안 검토 결과에 재등재 금지.

#### 권한 검증 보강 현황 (코드 분석 2026-05-26)
- `FileController`: 읽기 경로(목록·단건조회·다운로드·미리보기)는 `FileOwnershipChecker.checkReadAccess()`/`canRead()`로 읽기 권한 검증, 쓰기 경로(메타수정·단건삭제)는 `FileOwnershipChecker.verifyWriteAccess()`(owner-or-admin, 403), 원본 기준 일괄삭제(`deleteFilesByOrc`)는 서비스 계층에서 owner-or-admin 검증 적용(2026-06-23 소유권 하드닝).
- `GeminiController`: `@PreAuthorize("hasRole('ADMIN')")` 관리자 전용.
- `UserController`, `OrganizationController`, `ProjectController`, `ApplicationController`: 부서/소유권 정책은 업무 요건에 맞춰 별도 검토.
- **파일 업로드 확장자 검증**: `FileService.uploadFileInternal()` 진입 시점에 `FileValidator.validateExtension()` 호출.

### 5.7 채번/주요 비즈니스 제약
- 채번 규칙(관리번호 포맷)은 → [`docs/guides/data-model.md#4-채번-규칙`](docs/guides/data-model.md) 참조.
- 신청 상태가 **"결재중"** 또는 **"결재완료"**인 경우 연결된 프로젝트 수정/삭제 불가.
- 프로젝트 수정 시 품목(`Bitemm`) 동기화: 요청에 포함된 품목은 추가/수정, 누락된 기존 품목은 Soft Delete.
- 검토의견(`Brivgm`)은 문서에 종속. 삭제는 논리 삭제 우선 검토.
- 예산현황 조회는 `BudgetStatusQueryRepository` 집계 쿼리 기준. 화면 요구사항 변경 시 DTO·쿼리 동기 갱신.

### 5.8 환경 설정 키
- JWT: `jwt.secret`, `jwt.access-token-validity`, `jwt.refresh-token-validity`
- 프론트/CORS: `app.frontend-url`(=`APP_FRONTEND_URL`)과 `cors.allowed-origins`(미지정 시 `app.frontend-url`로 폴백). **프론트 URL 환경변수 하나로 둘 다 정합**, CORS만 다중 오리진 필요 시 `CORS_ALLOWED_ORIGINS`로 오버라이드. (§5.6 CORS 참조)
- 쿠키: `app.cookie.secure`
- 파일: `app.file.base-path` — local `c:/itp_file` (base 기본값), dev·prod `${FILE_BASE_PATH:/dat/springitp}`. multipart 최대 파일 50MB / 요청 200MB
- Gemini: `gemini.api.key`, `gemini.api.base-url`, `gemini.api.model=gemini-2.5-flash`
- 서버 식별자: `app.server.instance-id=SVR1`
- Flyway: 베이스/dev/prod `spring.flyway.enabled=false`, `local-ext`/`local-int`만 `spring.flyway.enabled=true`; `spring.flyway.user=${FLYWAY_USER:...}`, `spring.flyway.password=${FLYWAY_PASSWORD:...}`, `spring.flyway.default-schema=${DB_SCHEMA:ITPOWN}`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=20260620.001`
  - **의존성(Spring Boot 4 필수)**: Flyway 오토컨피그는 `org.springframework.boot:spring-boot-starter-flyway`로 가져온다. Spring Boot 4.0+는 `FlywayAutoConfiguration`을 별도 모듈(`spring-boot-flyway`)로 분리했으므로 `flyway-core` 단독 의존이면 마이그레이션이 **조용히 실행되지 않는다**(빈 미생성·로그 0줄·히스토리 테이블 미생성). Oracle 방언은 `runtimeOnly 'org.flywaydb:flyway-database-oracle'` 유지.
  - **위치(로컬 실행 경로)**: 베이스는 `spring.flyway.locations=${FLYWAY_LOCATIONS:classpath:db/migration}`(Gradle `processResources`가 `it_database/migrations`를 복사). 단 VSCode/Eclipse 실행은 `bin/main`에서 구동되어 이 복사를 거치지 않으므로, `local-ext`/`local-int`는 `spring.flyway.locations=${FLYWAY_LOCATIONS:filesystem:../it_database/migrations}`로 소스 디렉토리를 직접 읽는다(작업 디렉토리는 VSCode·`gradlew bootRun` 모두 `it_backend`).

### 5.9 테스트 기준
- 기능 변경 후 최소 `./gradlew test` 실행.
- 인증/결재/파일/QueryDSL 집계/변경 로그 등 공통 영향 변경은 `./gradlew clean test`로 재검증.

### 5.10 기동 시 환경변수 검증
- `EnvironmentValidator` (`common/system/EnvironmentValidator.java`): `@PostConstruct`에서 `spring.datasource.password`, `jwt.secret` 프로퍼티 해석 결과를 검사.
- 해석 결과가 빈값이면 `IllegalStateException`으로 즉시 구동 실패.
- **현재 `application.properties`의 기본값(`kdb1234!!`, 기본 JWT 시크릿)으로 인해 환경변수 미설정 시 검증 통과 → 운영 프로파일에서 기본값 제거 필수.** (§5.6 비밀값 관리 참조)
- 환경변수 추가 시 `EnvironmentValidator` 목록에도 함께 등록.

### 5.11 파일 보안
- `FileValidator` (`infra/file/FileValidator.java`): 허용 확장자 화이트리스트 검증 — `FileService.uploadFileInternal()` 진입 시점 호출.
- `FileOwnershipChecker` (`infra/file/FileOwnershipChecker.java`): 파일 쓰기 권한 검증 `verifyWriteAccess(flMpnId, user)`(본인 또는 관리자, 실패 시 `AccessDeniedException`→403)와 읽기 권한 검증 `checkReadAccess(flMpnId, user)`(예외)·`canRead(file, user)`(목록 필터용 boolean)을 제공합니다. 메타수정·단건삭제는 `verifyWriteAccess`, 다운로드/미리보기/목록/단건조회는 `checkReadAccess`/`canRead`로 검증합니다. (기존 `checkOwnership(String,String)`은 `verifyWriteAccess`로 대체·제거됨, 2026-06-23.)
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
- 임계값: 사번 기준 5회 실패 / 10분 잠금. DB 이력 기준이므로 서버 재시작에도 유지.
- `AuthService.login()`: 로그인 검증 전에 `checkLocked(eno)` 선행 호출.
- **IP·기기 기준 잠금 없음** — Credential stuffing 방어 미적용 (§5.6 Brute-force 보호 참조).

### 5.12.1 감사 로그(BaseLogEntity) 패턴
- **로그 엔티티**: 30개 (*L 접미사, 예: `BprojmL`, `CcodemL`, `CapplmL`, `BestimL`, 코드 분석 2026-06-14). 짝이 되는 업무 엔티티는 `@LogTarget(entity = *L.class)`로 로그 대상을 지정.
- **기본 구조**: `*L` 로그 엔티티가 `BaseLogEntity` 상속 — 기본 컬럼 자동 포함 (GUID, FST_ENR_DTM/USID, LST_CHG_DTM/USID).
- **로그 리스너**: `ChangeLogEntityListener` → JPA entity lifecycle 후킹 → `AuditLogPersister` → DB 저장.
- **저장 시점**: JPA `@PrePersist`/`@PreUpdate` 콜백 중 `ChangeLogEntityListener`가 `AuditLogPersister.persist()`를 직접 호출해 현재 flush 흐름에서 로그를 저장합니다. 로그 저장 실패는 catch 후 `log.error`로 기록(감사 추적 유실은 비정상 상황 → 모니터링 알람 노출)하되 예외는 삼켜 원본 작업 롤백을 피합니다.
- 로그 조회는 `domain/log`의 `*L` 로그 엔티티 또는 분석용 view(`V_ITPAPP_LOG_FEED`, `common/admin`·`common/admin/realtime` 조회 서비스)를 사용.

#### 5.12.1.1 NOT NULL 기본값은 생성자/팩토리에서 설정 (감사 로그 스냅샷 타이밍 함정 — 필수 규칙)
**`@LogTarget` 엔티티에서 NOT NULL 컬럼의 기본값은 반드시 생성자·팩토리(`create()`)·빌더 시점에 채웁니다. 엔티티 자신의 `@PrePersist`에서만 기본값을 설정하면 안 됩니다.**

- **이유**: `BaseEntity`는 `@EntityListeners({AuditingEntityListener.class, ChangeLogEntityListener.class})`를 선언합니다. JPA 규약상 **엔티티 리스너 콜백은 엔티티 자신의 `@PrePersist`보다 먼저** 실행됩니다. 따라서 INSERT 시 `ChangeLogEntityListener`가 로그(`*L`)를 스냅샷하는 시점에는, 엔티티 자체 `@PrePersist`가 아직 실행되지 않아 해당 필드가 **null**입니다. 마스터 테이블 INSERT는 이후 `@PrePersist`가 값을 채워 정상이지만, 로그 테이블에는 null이 복사되어 `*L`의 NOT NULL 제약을 위반합니다(`ORA-01400`).
- **try/catch로도 안 잡힘**: `AuditLogPersister`의 `entityManager.persist(logEntity)`는 INSERT를 **예약만** 하며 실제 SQL은 커밋 시점 flush에서 실행됩니다. 그래서 `ChangeLogEntityListener.persistLog()`의 "로그 실패 무시" try/catch 바깥(커밋 flush)에서 제약 위반이 터져 **원본 업무 트랜잭션까지 롤백**됩니다.
- **안전한 필드(예외)**: BaseEntity 공통 필드(`delYn`/`guid`/`guidPrgSno`)는 `AuditLogPersister.applyBaseAuditDefaults()`가 스냅샷 직전에 보정하고, JPA Auditing 필드(`fstEnrDtm`/`fstEnrUsid`)는 리스너 선언 순서상 `AuditingEntityListener`가 먼저 채우므로 안전합니다. **엔티티 고유의 NOT NULL 기본값만** 본 규칙의 대상입니다.
- **올바른 패턴** (`Brivgm.create()` 참고):
  ```java
  public static Brivgm create(...) {
      Brivgm b = new Brivgm();
      // ...
      b.fsgYn = "N";   // ← 생성 시점에 설정. 스냅샷이 'N'을 복사하도록 보장.
      return b;
  }

  @PrePersist
  private void prePersistBrivgm() {
      if (this.fsgYn == null) this.fsgYn = "N";   // 방어적 폴백으로 유지
  }
  ```
- **체크리스트**: 새 `@LogTarget` 엔티티를 추가할 때, 짝이 되는 `*L` 로그 엔티티/테이블에 NOT NULL 컬럼이 있으면 그 값이 **영속화 직전이 아니라 객체 생성 시점**에 항상 채워지는지 확인합니다.
- 참고: `save()`가 ID 보유 엔티티에서 `merge()` 분기로 빠져 `DEL_YN=null` 등 기본값이 누락되는 별개의 회귀(PRD §15)도 있습니다. 신규 INSERT가 확실하면 `entityManager.persist()`로 직접 저장해 `@PrePersist` 발화를 보장합니다(`council` 서비스 참고).

### 5.12.2 이벤트 리스너(@EventListener vs @TransactionalEventListener)
- **`@EventListener`**: 발행자와 동일 트랜잭션에서 **동기 실행**. 리스너 실패 시 원본 트랜잭션 롤백.
  - 사용 예: `CouncilApprovalEventListener` — 결재 완료 이벤트 → 협의회 상태 자동 전이. 협의회 상태 변경 실패 시 결재 원본 작업도 함께 롤백.
- **`@TransactionalEventListener`**: 발행자 트랜잭션 커밋 **후** 비동기 실행. 리스너 실패 시 원본 무영향.
  - 사용 예: 알림 발송, 메일 전송, 별도 시스템 동기화 (부수 효과).
- 선택 기준: 상태 일관성이 필수 → `@EventListener`, 실패해도 괜찮은 부가 작업 → `@TransactionalEventListener`.

### 5.12.3 실시간 로그 모니터링 (최종 구현, 2026-06-05)
**컨트롤러 & API**
- `RealtimeLogController` (`common/admin/realtime`): `/api/admin/realtime-logs` 단일 GET 엔드포인트, 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 적용.
- 쿼리 파라미터:
  - `since` (LocalDateTime, 선택): 이 시각 이후 로그 조회. null이면 최신 `limit`건 스냅샷.
  - `cursorLogTbl`, `cursorLogSno` (복합 커서): since와 함께 사용하여 중복 조회 회피.
  - `limit` (기본값 200): 최대 200으로 제한.
  - `tables` (쉼표 구분): 허용 LOG_KEY 목록. null/공백이면 제약 없음.
  - `chgTypes` (쉼표 구분): C/U/D 부분집합만 허용. null/공백이면 제약 없음.
- 응답: `RealtimeLogDto.Snapshot` — 증분 로그 목록(`FeedRow[]`) + 최근 5분(`tableCountSince`) / 30분(`perMinuteBuckets`) 발생량 집계.

**Repository 구현**
- `RealtimeLogRepository`: `V_ITPAPP_LOG_FEED` View를 EntityManager 네이티브 쿼리로 조회.
  - **증분 조회 로직**: `findFeed(QueryCondition)` — since가 null이면 최신 limit건, 비-null이면 복합 커서 조건:
    ```
    CHG_DTM > since
    OR (CHG_DTM = since AND LOG_TBL > cursorLogTbl)
    OR (CHG_DTM = since AND LOG_TBL = cursorLogTbl AND LOG_HIS_TGR_SNO > cursorLogSno)
    ```
  - **네이티브 쿼리 결과 매핑** (§5.5.4 참조): 환경별 타입 차이 처리 헬퍼 함수 사용:
    - `toStr(Object)` — VARCHAR2(1) 컬럼이 Character/String 혼용으로 반환되는 경우 안전하게 String으로 변환.
    - `toLdt(Object)` — TIMESTAMP 컬럼이 LocalDateTime/Timestamp 둘 다 가능한 경우 LocalDateTime으로 변환.
  - **집계 쿼리**:
    - `countByTableSince(since)` → Map<LOG_KEY, 발생건수> (최근 5분).
    - `perMinuteSince(since30, serverTime)` → List<Long> (최근 30개 분 슬라이딩 윈도우, 0으로 채움).

**클라이언트 입력 검증 & 응답**
- `tables` → 쉼표로 구분하여 공백 trim 후, `AdminLogService.getTables()`의 허용 LOG_KEY 집합과 교집합.
- `chgTypes` → 쉼표로 구분하여 C/U/D만 필터링.
- `limit` → 200 이상이면 200으로 조정.
- 응답 컬럼: LOG_TBL, LOG_KEY, LOG_HIS_TGR_SNO, CHG_DTT_YN, CHG_DTM, CHG_USID, GUID, DEL_YN. BEFORE/AFTER 변경 본문 미포함.

### 5.13 공통 게시판 패턴
- 백엔드 패키지: `common/board`.
- 주요 엔티티: `Cblbmm`(게시판 메타, `TPRMPP_CBLBMM`), `Cblbcm`(게시물, `TPRMPP_CBLBCM`), `Ccmmtm`(댓글, `TPRMPP_CCMMTM`).
- 주요 API:
  - `GET /api/boards/meta`, `GET /api/boards/meta/{blbMngNo}` — 인증 사용자 공통 게시판 메타 조회.
  - `/api/admin/boards/meta/**` — 관리자 전용 게시판 메타 CRUD. `AdminBoardMetaController` 클래스 레벨 `@PreAuthorize("hasRole('ADMIN')")` 필수.
  - `/api/boards/{blbMngNo}/posts/**` — 게시물 목록·상세·등록·수정·삭제·답변글.
  - `/api/boards/{blbMngNo}/posts/{nacMngNo}/comments/**` — 댓글·대댓글 CRUD.
- 게시물/댓글 본문은 저장 전 `HtmlSanitizer.sanitize()` 적용 필수.
- 게시판 권한은 메타의 `inqAthC`, `enrAthC`와 서비스 계층 검증으로 판단합니다. 프론트 메뉴 숨김은 UX 보조일 뿐 최종 보안 경계가 아닙니다.
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

### 5.16 알림 시스템 (common/notification)

#### 목적 및 아키텍처
- **목적**: 앱 내 알림(인앱), 이메일, SMS, 알림톡 등 다중 채널 알림 발송 통합 관리.
- **패턴**: Event-driven 이벤트 발행 → 비동기 리스너 → DB 적재 → 채널별 디스패처 호출.

#### 알림 발송 흐름 (최종 구현, 2026-06-05)
1. 비즈니스 로직(결재, 게시판, 시스템 등)에서 `ApplicationEventPublisher.publishEvent(new NotificationEvent(...))` 호출.
2. `NotificationEventListener`가 `@TransactionalEventListener(phase=AFTER_COMMIT)` 콜백으로 `NotificationService.send(event)` 호출.
3. `NotificationService.send()` — **`@Transactional(propagation=Propagation.REQUIRES_NEW)` 반드시 필수**:
   - Spring 7.0+ 환경에서 AFTER_COMMIT 페이즈는 outer 트랜잭션 종료 후 non-transactional synchronization 컨텍스트에서 호출되므로, `Propagation.REQUIRED`만으로는 `TransactionRequiredException` 발생 가능. `REQUIRES_NEW`로 항상 독립된 새 트랜잭션을 강제 시작.
   - Recipient 검증: `event.recipientEno()` 비어있으면 warn 로그 후 발행 건너뜀 (null return).
   - 채번: `INF-{YYYY}-{NEXTVAL:08d}` (예: `INF-2026-00000001`).
   - 엔티티 빌드 후 `saveAndFlush()` — 즉시 INSERT 발행 (flush 스킵 회피, 실패 시 즉시 ORA 예외 진단 가능).
   - `NotificationDispatcher.dispatch(notification, event.sdPayload())` 호출 (부수 효과로 취급).

#### 알림 채번 규칙
- 형식: `INF-{YYYY}-{NEXTVAL:08d}`
  - 예: `INF-2026-00000001`, `INF-2026-00000002`
- 시퀀스는 `CINFMM` 테이블 스키마 기반 `NEXTVAL()` 호출로 생성 (Repository).

#### 알림 종류 상수 (`NotificationEvent.TYPE_*`, Ccodem cId='INF_TP')
```
01 — 시스템 알림 (TYPE_SYSTEM)
02 — 결재요청 (TYPE_APPROVAL_REQUEST)
03 — 결재결과 (TYPE_APPROVAL_RESULT)
04 — 게시물 멘션 (TYPE_MENTION_POST)
05 — 댓글 멘션 (TYPE_MENTION_COMMENT)
06 — 결재회수 (TYPE_APPROVAL_RECALLED)
```
호출자는 `NotificationEvent.TYPE_*` 상수 사용 권장 (오타 방지).

#### API 엔드포인트 (`/api/notifications`)
1. **`GET /api/notifications`** — 본인 알림 목록 페이지 조회.
   - 쿼리 파라미터: `unreadOnly` (true=미읽음만), `page` (기본값 0), `size` (기본값 20).
   - 응답: `Page<NotificationDto.Item>` (최신순 FST_ENR_DTM DESC).

2. **`GET /api/notifications/unread-count`** — 본인 미읽음 카운트.
   - 응답: `NotificationDto.UnreadCount { count: long }`.
   - 용도: AppHeader 배지 표시.

3. **`PATCH /api/notifications/{infMngNo}/read`** — 단건 읽음 처리.
   - 경로 파라미터: `infMngNo`.
   - 응답: 204 No Content.
   - 소유자 검증: 본인 알림만 가능 (AccessDeniedException 발생).

4. **`PATCH /api/notifications/read-all`** — 본인 미읽음 일괄 읽음.
   - 응답: `NotificationDto.MarkAllReadResponse { updated: long }`.

5. **`DELETE /api/notifications/{infMngNo}`** — 단건 Soft Delete.
   - 경로 파라미터: `infMngNo`.
   - 응답: 204 No Content.
   - 소유자 검증: 본인 알림만 가능.

모든 엔드포인트는 인증 필수 (`@AuthenticationPrincipal CustomUserDetails`).

#### 보안 — 소유권 검증
- 본인만 자신의 알림에 접근 가능 (조회/읽음/삭제).
- 타인 알림 조회/수정/삭제 시도 → `AccessDeniedException` 발생.
- `NotificationService.loadOwned(infMngNo, currentEno)` 내부 헬퍼로 검증.

#### 디스패처 패턴 (NotificationDispatcher SPI, 현재 구현 2026-06-05)
- **인터페이스**: `NotificationDispatcher.dispatch(Cinfmm notification, String eaiPayload)`.
  - 목적: 알림 엔티티 저장 후 채널별 발송 처리 분리 (부수 효과 SPI).
  
- **현재 구현**: `StubNotificationDispatcher` — INAPP(인앱) 채널만 처리.
  - `notification.markDispatched("01", sdPayload)` 호출 (`CHANNEL_INAPP="01"`, EAI_SD_TP_C='01' + EAI_SD_DTM=now).
  - 발송 실패 처리: 예외 발생 금지, warn 로그만 수행 (원본 알림 저장 작업 무영향 유지).
  
- **향후 확장 패턴** (TASK.md 등록):
  - 이메일, SMS, 카톡(알림톡) 어댑터 추가 시 채널별 구현체 분리 + 라우터 도입.
  - 각 구현체는 `NotificationDispatcher` 인터페이스 구현.
  - `dispatch()` 내에서 발송 실패는 예외 발생 금지, 로깅만 수행 (부수 효과로 취급).

### 5.17 Tiptap 변수 시스템 (common/system/tiptap)

#### 목적 및 설계
- **목적**: Tiptap 리치 에디터 문서에 변수 토큰 삽입 → 런타임에 실제 데이터값(예산액, 편성률)으로 해석 및 표시.
- **사용 사례**: 템플릿 문서(결재문, 제안문 등)에 `2026.itBudget.requestAmount` 같은 토큰 삽입 → 조회 시 실제 편성요청액 숫자로 치환 표시.

#### 토큰 형식 및 파서 (TiptapTokenParser)
**구조**: `<YEAR>.<CATEGORY>[.<PROJECT_CODE>].<ITEM>`

예시:
- `2026.itBudget.requestAmount` → 2026 전산예산 편성요청액.
- `2026.capBudget.allocatedAmount` → 2026 자본예산 편성액.
- `2026.proj.P001.allocationRate` → 2026 사업 P001의 편성률.

**정규식**:
- 비사업 (itBudget/capBudget/opex): `^(\d{4})\.(itBudget|capBudget|opex)\.(requestAmount|allocatedAmount|allocationRate)$`
- 사업 (proj): `^(\d{4})\.proj\.([A-Z0-9_-]+)\.(requestAmount|allocatedAmount|allocationRate)$`

**파서 결과** (ParseResult):
```java
record ParseResult(boolean valid, Integer year, Category category, 
                   String projectCode, String item)
```
- `valid=false` → 형식 오류.
- `projectCode` — 사업 카테고리일 때만 null 아님.

#### 변수 카탈로그 구조 (MetadataResponse)
```
categories: [
  {
    code: "IT_BUDGET",
    label: "전산예산",
    years: [2024, 2025, 2026, 2027, 2028],
    projects: null,           // 비사업 카테고리는 projects=null
    items: [
      { key: "requestAmount", label: "편성요청액" },
      { key: "allocatedAmount", label: "편성액" },
      { key: "allocationRate", label: "편성률" }
    ]
  },
  {
    code: "PROJ",
    label: "사업별",
    years: [2024, 2025, 2026, 2027, 2028],
    projects: [
      { code: "P001", name: "클라우드 전환" },
      ...
    ],
    items: [...]
  },
  ...
]
```
**카테고리**:
- `IT_BUDGET` — 전산예산.
- `CAP_BUDGET` — 자본예산.
- `OPEX` — 일반관리비.
- `PROJ` — 사업별 (project 목록 포함).

#### 해석 결과 상태 (ResolvedValue)
```java
record ResolvedValue(String value, String status)
```

상태 값:
- **`OK`** — 정상 해석, `value`에 포맷팅된 표시값 포함.
  - 예: `"900억원"`, `"85.3%"`.
- **`INVALID`** — 토큰 형식 오류 (정규식 불일치), `value=""`.
- **`MISSING`** — 해당 카테고리/사업/년도에 데이터 없음, `value=""`.
  - 또는 편성요청액이 0 또는 null (편성률 계산 불가).
- **`FORBIDDEN`** — 권한 없음 (향후 SecurityContext 기준 필터링), `value=""`.

#### 포맷팅 규칙
**금액 (formatAmount)**:
- 1억 이상: `"900억원"` (100_000_000 단위).
- 1만 이상: `"5000만원"` (10_000 단위).
- 그 외: `"123원"`.

**편성률 (formatRate)**:
- 계산: `편성액 / 편성요청액 × 100` (소수점 한 자리).
- 예: `"85.3%"`.
- null/0 조건 → `MISSING`.

#### API 엔드포인트 (`/api/tiptap-variables`, 현재 구현 2026-06-05)
1. **`GET /api/tiptap-variables/metadata`** — 변수 카탈로그 조회.
   - 응답: `TiptapVariableDto.MetadataResponse` (위 구조 참조).
   - 호출처: Tiptap 변수 드롭다운(UI) 초기화 시.
   - 인증 요구: Yes (인증된 모든 사용자).
   - 권한별 필터링: 현재 미구현. 프로젝트 카탈로그는 인증 사용자 공통 목록이며, 사용자별 프로젝트 목록 필터링은 TASK.md 후속 과제로 관리합니다.

2. **`POST /api/tiptap-variables/resolve`** — 토큰 배열 일괄 해석.
   - 요청 본문: `TiptapVariableDto.ResolveRequest { tokens: List<String> }`.
   - 토큰 개수 제약: `@Size(min=1, max=200)` (Bean Validation).
   - 응답: `TiptapVariableDto.ResolveResponse { results: Map<String, ResolvedValue> }`.
     - `results`는 LinkedHashMap (삽입 순서 유지) → 프론트 표시 순서 보장.
   - 응답 구조 예시: `{ "2026.itBudget.requestAmount": { "value": "900억원", "status": "OK" }, "2026.proj.P001.allocationRate": { "value": "85.3%", "status": "OK" }, ... }`.
   - 토큰 개수 = 0 → 빈 Map 반환 (API 호출 스킵 권장).
   - 인증 요구: Yes (인증된 모든 사용자).
   - 권한별 필터링: PROJ 토큰은 관리자·부서매니저만 해석 가능하며, 그 외 사용자는 `FORBIDDEN`을 반환합니다. 세부 부서-사업 매핑은 후속 과제입니다.

#### 데이터 쿼리 (BudgetStatusQueryRepository)
- **카테고리 집계**: `aggregateByCategory(year, category)` → `AggregatedAmount { requestSum, allocatedSum }`.
- **사업 집계**: `aggregateByProject(year, projectCode)` → `AggregatedAmount { requestSum, allocatedSum }`.
- null/결과 없음 → `ResolvedValue.missing()`.

#### 권한 필터링 (향후 Task)
- 현재: metadata 프로젝트 카탈로그는 모든 인증 사용자에게 공통 노출. resolve의 PROJ 토큰은 관리자·부서매니저만 허용.
- 향후: `SecurityContext` 기준 사용자 권한/부서별 카탈로그 및 사업별 해석 결과 필터링 (§4.5 Design Ref).

### 5.18 정보화사업 집행 4단계 (domain/estimate·deliberation·contract·payment)

정보화사업/전산업무비의 집행 절차를 4개 독립 도메인으로 구현합니다. 4개 도메인은 동일한 컨트롤러·상태·채번 패턴을 공유합니다.

| 단계 | 도메인 | 기본 테이블 | 상세 테이블 | API Prefix | 채번 | 상태(작성중→진행중→완료) | 대상구분(bgPrnTc) |
|------|--------|------------|------------|-----------|------|------|------|
| ① 소요예산 산정 | `domain/estimate` | `TPRMPP_BESTIM` | `TPRMPP_BESTTM`(팀별 산정) | `/api/project/estimates` | `REQ-{YYYY}-{4}` | 41→42→49 | 100(사업) 전용 |
| ② 과업심의위원회 | `domain/deliberation` | `TPRMPP_BDELIM` | — | `/api/project/deliberations` | `DLB-{YYYY}-{4}` | 51→52→59 | 100(사업)·200(전산업무비) |
| ③ 입찰/계약 | `domain/contract` | `TPRMPP_BCONTM` | — | `/api/project/contracts` | `CTR-{YYYY}-{4}` | 61→62→69 | 100·200 |
| ④ 대금지급 | `domain/payment` | `TPRMPP_BPAYMM` | `TPRMPP_BPAYTM`(회차별 지급) | `/api/project/payments` | `PAY-{YYYY}-{4}` | 71→72→79 | 100·200 |

**공통 컨트롤러 패턴** (각 도메인 `*Controller`):
- `GET /` 목록, `GET /{docNo}` 상세, `POST /` 생성, `PUT /{docNo}` 수정, `DELETE /{docNo}` 삭제(Soft Delete).
- `POST /{docNo}/status` 상태 전이, `PUT /{docNo}/{lines|contract|result|payments}` 단계별 상세 저장.
- **클래스 레벨 `@PreAuthorize` 없음** — `/api/admin/**`가 아니므로 SecurityConfig상 인증만 요구. 쓰기 주체·상태 전이·부서 권한은 **서비스 계층**에서 검증(관리자 전용 도메인 아님).
- mutating 엔드포인트는 `@Valid` 적용.

**공통 서비스 규칙**:
- 클래스 레벨 `@Transactional(readOnly=true)` + 쓰기 메서드 `@Transactional` 오버라이드.
- 상태 전이는 **인접 단계만 허용**(작성중↔진행중↔완료). 작성중 상태에서만 수정·삭제 가능.
- 채번: 연도별 시퀀스 `*-{연도}-{4자리}` (예: `REQ-2026-0001`). `nextDocSeq()`/시퀀스 NEXTVAL.
- 신규 생성 시 동일 대상에 **진행 중(작성중/진행중) 문서 중복 방지** 검증.
- 모든 엔티티는 `BaseEntity` 상속 + `@LogTarget`로 감사 로그 대상(§5.12.1).

**보안 규칙 (집행 4단계, 코드 분석 2026-06-23):**
- 쓰기 경로 소유권 검증은 공통 유틸 `OwnershipVerifier.verifyOwnerOrAdmin(ownerEno, user)`(`common/system/security`, 실패 시 `AccessDeniedException`→403)를 표준으로 사용합니다. 적용: 집행 4단계(update/delete/save*), 요구사항정의서(수정/삭제/새버전), 게시판 본인 게시물·댓글 수정·삭제. 파일은 메타수정·일괄삭제에 소유권 검증, 읽기 경로(목록/단건/다운로드/미리보기)에 `FileOwnershipChecker.checkReadAccess`/`canRead` 적용. 요구사항정의서 대시보드/배지의 `bbrC`는 비관리자에 한해 JWT 클레임으로 서버측 강제합니다.
- **집행 4단계 `changeStatus`(상태전이)는 ADMIN 전용입니다(2026-06-29 적용).** `OwnershipVerifier.verifyAdmin(user)`(실패 시 `AccessDeniedException`→403)로 검증하며, 소유자라도 ADMIN이 아니면 거부합니다. 인접 단계 전이 가드(`작성중↔진행중↔완료`)와 `bprojaSyncService.upsert`는 그대로 유지됩니다. update/delete/save*는 기존대로 소유자-or-ADMIN(`verifyOwnerOrAdmin`)을 유지합니다.
- 클래스 레벨 `@PreAuthorize`가 없는 업무 컨트롤러는 **서비스 계층에서 소유자/관리자 검증 필수**이며, 집행 4단계 update/delete/save*에 `verifyOwnerOrAdmin`, changeStatus에 `verifyAdmin`이 적용되어 있습니다.
- 부서(bbrC) 필터는 4개 도메인(`Estimate`/`Deliberation`/`Contract`/`Payment`) `RepositoryImpl` 목록 쿼리에 모두 적용됩니다(2026-06-28). 사업(`bgPrnTc='100'`)은 `Bprojm.svnDpmC`, 전산업무비(`'200'`)는 `Bcostm.costSvnDpmC`를 `cncdRfrNo` 키로 LEFT JOIN(최신버전 `lstYn='Y'`·미삭제) 후 `Expressions.anyOf(allOf(100,사업부서), allOf(200,전산부서))`로 분기 비교하며, 2중 JOIN 안전을 위해 `.distinct()` 가드를 둡니다. `changeStatus` 상태전이는 ADMIN 전용으로 적용되었습니다(2026-06-29, 위 보안 규칙 참조).
- 금융 금액 필드(`cttAmt`, `dfrAmt`)는 `@DecimalMin("0")`, YN 플래그는 `@Pattern(regexp="^[YN]$")` 적용 권장(현재 일부 누락).

### 5.19 EAI 발송 인프라 (infra/eai, KDB 표준전문)

- **목적**: KDB 사내 EAI 게이트웨이로 표준전문(UMS 알림톡/SMS, GWE 메일)을 발송. ePAMS(eHR) 전문 규격을 포팅.
- **구조**: `EaiService.sendEai(EaiRequest)`가 전문 조립(`EaiMessageBuilder`)과 전송(`RestClient`)을 연결. 페이로드는 **sealed 인터페이스 SPI**(`EaiPayload` → `UmsPayload`/`GwePayload`), 채널별 `EaiPayloadSection` 구현체가 개별부를 조립.
- **안전장치**:
  - `eai.enabled=false`(기본값)이면 전문을 빌드·로깅만 하고 HTTP 미호출 (개발/CI 안전).
  - 모든 실패는 예외를 전파하지 않고 `EaiResult`(success/failure/skip)로 표현 — **부수효과 원칙**.
  - 민감정보(휴대폰/OTP)는 전문 평문 로깅하지 않고 마스킹.
  - 시스템 식별자 IPP/PRM/PP는 프로퍼티로 확정. `IF_ID`(인터페이스ID)·UMS 템플릿은 운영팀 발급 대기 (TASK.md EAI 섹션).
- **현재 미연동**: `NotificationDispatcher` 실연동 어댑터로 `EaiService`를 연결하는 작업은 TASK.md 백로그. 현재 알림은 `StubNotificationDispatcher`(INAPP 전용)만 동작.
- 신규 시스템 연동 시: `EaiPayload`(record) + `EaiPayloadSection`(@Component) 1쌍 추가 패턴.

### 5.20 파일 로깅 (logback-spring.xml)
- 설정 파일: `src/main/resources/logback-spring.xml` (Spring Boot 자동 로드). 콘솔 + 파일 appender.
- **1개월 단위 롤오버**: `TimeBasedRollingPolicy` + `%d{yyyy-MM}` → 활성 파일 `it-backend.log`, 보관본 `it-backend.YYYY-MM.log`. `maxHistory=12`(12개월), `totalSizeCap=3GB`.
- 로그 경로(프로파일별): `local-ext`/`local-int`/미지정 → `c:/itp_log`, `dev`/`prod` → `/log/springitp`. 디렉터리는 logback이 자동 생성.
- 로그 레벨은 기존 `application*.properties`의 `logging.level.*`가 콘솔·파일 공통 제어.
- **콘솔 charset = `${stdout.encoding:-UTF-8}` (한글 콘솔 깨짐 방지)**: logback `ConsoleAppender`는 `stdout.encoding`(PrintStream)을 무시하고 자체 `<charset>`로 바이트를 직접 출력한다. 고정 `UTF-8`이면 Windows 한글 콘솔(MS949)에서 한글이 깨진다(예: `인증서버` → `?몄쬆?쒕쾭` = UTF-8 바이트를 cp949로 읽은 전형적 패턴). JDK 18+가 콘솔 코드페이지로 `stdout.encoding`을 설정하므로(Windows=MS949 / Linux=UTF-8), charset을 이 속성에 맞추면 프로파일 분기 없이 자동 정합된다. 속성 미해석 시 UTF-8 폴백. **파일 appender는 UTF-8 고정 유지**(도구가 UTF-8로 읽음). 회귀: `LogbackConsoleCharsetTest`.
- 프론트엔드(Nuxt CSR/정적 생성)는 서버 런타임이 없어 파일 로깅 비대상.

## 7. 주석 작성 예시
JavaDoc 표준 양식과 코드 예제는 → [`docs/guides/comment-style.md`](docs/guides/comment-style.md) 참조.
