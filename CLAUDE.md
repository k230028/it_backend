---
[ 백엔드 필수 규칙 ]
이 문서는 코드 생성·수정 시 반드시 지킬 규칙만 정의합니다.
설치·실행·프로젝트 탐색은 README.md, 상세 설계는 docs/guides를 참조합니다.
공통 규약은 ../CLAUDE.md가 SoT입니다.
---

## 1. 기술과 구조

- Java 25, Spring Boot 4.1, Gradle, Spring Data JPA, QueryDSL, Oracle을 사용합니다.
- 패키지 루트는 `com.kdb.it`입니다.
- 요청 흐름은 Controller → Service → Repository 방향을 유지합니다.
- `domain`과 `infra`는 `common`을 사용할 수 있습니다. 결재 상태 동기화처럼 검증된 `common.approval → domain.budget.project` 예외는 허용하지만 신규 순환 의존은 만들지 않습니다.
- 생성자 주입과 Lombok `@RequiredArgsConstructor`를 사용합니다.

상세 구조는 [레이어와 패키지 가이드](docs/guides/architecture/layering-and-packages.md)를 참조합니다.

## 2. Oracle과 데이터 모델

- 접속 계정은 `ITPAPP`, 객체 소유 스키마는 `ITPOWN`입니다.
- `CURRENT_SCHEMA=ITPOWN`이 세션에 적용되므로 엔티티와 네이티브 쿼리에 `ITPOWN.` 접두사를 하드코딩하지 않습니다.
- 물리 구조의 SoT는 `../it_database/migrations`, ORM 매핑의 SoT는 엔티티, 사람이 보는 매핑 인덱스는 [data-model.md](docs/guides/persistence/data-model.md)입니다.
- 엔티티·컬럼명은 `C:\it\meta\meta.txt`의 메타 용어와 [컬럼 명명 가이드](docs/guides/persistence/column-naming.md)를 따릅니다.
- 시퀀스는 `SQ_{테이블명}_#`(예: `SQ_TPRMPP_CAUTHI_1`), PK 제약이 소유하지 않는 인덱스는 `IX_{테이블명}_##`(예: `IX_TPRMPP_CAUTHI_01`) 명명 규칙을 따릅니다. 감사로그 PK 채번(`AuditLogIdGenerator`)도 이 규칙으로 시퀀스명을 유도합니다.
- 시퀀스는 `NOCACHE`를 명시하고 MAXVALUE를 반드시 지정합니다. MAXVALUE 기준은 숫자 컬럼에 직접 저장하면 대상 컬럼 `NUMBER(p)`의 p자리, 문자열 식별번호를 만들면 채번 포맷의 zero-padding 폭입니다. 기준표의 SoT는 `../it_database/migrations/V20260730_003__NormalizeSequenceMaxValues.sql`입니다.
- CYCLE 여부는 **식별번호에 연도를 결합하는지**로 정합니다. 시퀀스는 연도 경계에서 초기화되지 않으므로(운영 소스에 초기화 코드가 없습니다), 연 발급량이 zero-padding 폭보다 작으면 순환 주기가 1년보다 길어져 재사용되는 일련번호가 **항상 다른 연도와 결합**합니다 — `COST-{yyyy}-%04d`처럼 연도를 붙이는 채번은 `CYCLE`을 허용합니다. 연도를 붙이지 않는 채번(`BLBM-%04d`)은 순환 즉시 같은 번호가 재발급되므로 `NOCYCLE`로 두어 상한 도달을 `ORA-08004`로 드러냅니다. **CYCLE의 성립 조건은 "어느 한 해에 zero-padding 폭을 넘게 발급하지 않는다"**이며, 이를 넘기면 같은 해 안에서 `(연도, 일련번호)` 쌍이 반복됩니다.
- Oracle `LPAD(seq, n, '0')`는 자릿수를 넘는 값을 잘라내 번호가 조용히 충돌하므로 채번에 쓰지 않습니다. 리포지토리는 시퀀스 원값만 반환하고 서비스가 Java `String.format("%0nd", ...)`로 조립합니다 — 자르지 않고 자릿수가 늘어납니다. 운영 소스의 SQL `LPAD` 채번은 2026-08-08 `BbugtmRepository`를 마지막으로 제거했습니다.
- 문자열 식별번호 채번은 접두어와 일련번호 사이 구분자로 `_`를 쓰지 않습니다. 구분자를 두면 `-`를 사용합니다(`FL-%08d`, `PRJ-%s-%04d`, `NAC-%d-%04d` 등. 메뉴 `MNU%07d`처럼 구분자 없는 형식도 있습니다). 채번 형식을 바꿔도 기존 행은 구 형식으로 남아 두 형식이 공존하므로, 식별번호는 정확히 일치 조회로만 사용하고 접두어를 파싱해 의미를 꺼내지 않습니다.
- 모든 업무 엔티티는 `BaseEntity`를 상속하고 물리 삭제 대신 `delete()`로 `DEL_YN='Y'`를 설정합니다.
- 감사 대상 업무 엔티티는 `@LogTarget`, 대응 로그 엔티티는 `BaseLogEntity`를 사용합니다.
- `@LogTarget` 엔티티의 고유 NOT NULL 기본값은 `@PrePersist`에만 의존하지 말고 생성자·팩토리에서 설정합니다.
- 복합키는 `@IdClass`, 모든 `@Column`에는 한글 `comment`를 지정합니다.
- 엔티티의 `@Id` 집합은 물리 PK의 **모든** 컬럼과 일치해야 합니다. 물리 PK 일부만 `@Id`로 매핑하면 서로 다른 행이 같은 JPA 식별자를 갖게 되어 1차 캐시에서 섞이므로 컬럼을 빼고 매핑하지 않습니다. `@IdClass`의 필드명·타입은 엔티티 필드와 같아야 하고 `JpaRepository`의 ID 타입도 그 `@IdClass`를 사용합니다. 매핑 정합은 `PhysicalCompositeIdMappingTest`가, 부분키가 같은 행이 실제로 분리되는지는 `PhysicalCompositeIdIsolationIt`가 검증합니다.

## 3. 마이그레이션

- 엔티티 컬럼 변경과 함께 새 Flyway 스크립트를 `../it_database/migrations`에 추가합니다.
- 파일명은 `V{YYYYMMDD_NNN}__{CamelCase설명}.sql` 형식을 사용합니다.
- 적용된 스크립트는 수정하지 않고 후속 변경은 새 버전으로 작성합니다.
- 컬럼 rename·타입 변경은 `ddl-auto=update`에 맡기지 않습니다.
- 로컬 프로파일만 Flyway 자동 적용을 허용하고 dev/prod는 DBA 검토 후 수동 적용합니다.
- `local-ext`/`local-int`는 IDE 기동과 `bootRun` 모두 `filesystem:../it_database/migrations`를 읽으므로 `it_database` 형제 디렉터리 구조를 유지합니다.
- Gradle `processResources`가 같은 디렉터리의 `V*.sql`을 WAR의 `db/migration`에 포함하므로 마이그레이션 경로나 리소스 태스크를 변경할 때 두 실행 경로를 함께 검증합니다.

상세는 [Flyway 운영 가이드](docs/guides/operations/flyway.md)를 따릅니다.

## 4. DTO·Controller·Repository·Service

- 관련 DTO는 정적 중첩 클래스로 묶고 Swagger `@Schema`를 작성합니다.
- 응답 DTO의 `@Schema`는 프론트 생성 타입(`it_frontend/app/types/api.d.ts`)의 SoT입니다. 모든 응답 속성에 `requiredMode = REQUIRED`를 지정하고, null이 올 수 있는 속성만 `nullable = true`를, 값 집합이 정해진 속성은 `allowableValues`를 함께 명시합니다. 이를 빠뜨리면 프론트가 실제로는 항상 오는 값을 optional로 다루거나 없는 값을 non-null로 다루게 됩니다. 계약은 `ApiResponseOpenApiContractTest`(도메인 전반)와 도메인별 `*OpenApiContractTest`가 고정하므로 응답 속성을 추가·삭제하면 해당 테스트를 함께 갱신합니다.
- 응답 직렬화에만 쓰는 조회는 엔티티 대신 필요한 컬럼만 담는 프로젝션(`record` 또는 인터페이스, 접미사 `*Row`·`*View`)으로 읽습니다. 쓰기 엔티티와 DDL은 그대로 두고 읽기 경로만 좁히며, 기존 엔티티 조회와 결과·정렬·null 계약이 같은지 `*ProjectionIt` Oracle 통합 테스트로 확인합니다.
- POST·PUT·PATCH 등 요청 본문을 받는 변경 API는 `@Valid`를 적용합니다.
- 모든 `@RequestParam`, `@PathVariable`, `@RequestHeader`에는 `name` 또는 `value`를 명시합니다.
- 기본 CRUD는 `JpaRepository`, 동적·복잡 쿼리는 `*RepositoryCustom` + `*RepositoryImpl` QueryDSL 패턴을 사용합니다.
- Repository는 DB 예외를 임의 변환하지 않고 상위 계층으로 전파합니다.
- 조회 서비스는 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`을 사용합니다.
- Dirty Checking이 가능한 변경에 불필요한 `save()`를 호출하지 않습니다.
- Spring Cache는 `CacheConfig`에 이름·TTL·최대 크기를 등록한 Caffeine 캐시를 사용하고, 기본 `CacheManager`는 `TransactionAwareCacheManagerProxy`를 유지하여 캐시 쓰기·무효화가 트랜잭션 커밋 이후 반영되도록 합니다.
- `@Cacheable` 원본을 변경하는 모든 쓰기 경로에는 영향 범위에 맞는 `@CacheEvict`를 적용합니다. 단일 키 변경은 같은 키를 제거하고 여러 키에 영향을 주면 `allEntries = true`를 사용합니다. TTL은 외부 변경이나 무효화 누락에 대한 안전망이며 정합성 보장의 주 수단으로 사용하지 않습니다.
- `@Cacheable`은 Spring 프록시를 통해 호출합니다. 같은 빈 내부 호출이 필요하면 캐시 조회 책임을 별도 빈으로 분리합니다.

Oracle/Jackson/URL 인코딩 함정은 [QueryDSL·Oracle 가이드](docs/guides/persistence/querydsl-and-oracle.md)를 확인합니다.

## 5. 인증·인가·보안

- 브라우저 인증은 httpOnly 쿠키 기반 Stateless JWT를 기본으로 합니다. Bearer 헤더 폴백은 `app.auth.allow-bearer-header`가 명시적으로 활성화된 개발·API 테스트 환경에서만 허용합니다.
- Access Token은 `Path=/`와 15분, Refresh Token은 `Path=/api/auth`와 7일 범위를 유지하며 두 쿠키 모두 SameSite=Lax를 적용합니다.
- Refresh Token은 사용자별 단일 패밀리로 관리하고 원문 대신 `ECY_RNW_PUB_TOK_CONE`에 소문자 SHA-256 HEX 조회값을 저장합니다. 갱신은 DB 쓰기 잠금 아래 구 토큰을 회전 상태로 남기고 같은 패밀리에 신규 토큰을 발급합니다. 회전 토큰 재사용 시 재로그인을 요구하며, 패밀리 DB 폐기는 삭제 트랜잭션이 실제 커밋되는 구현에서만 보장합니다.
- `EnvironmentValidator`는 전 프로파일에서 DB 비밀번호와 JWT 시크릿의 빈값을 차단합니다. `prod`에서는 Gemini 키, 활성 EAI URL, 비어 있거나 와일드카드인 CORS Origin, 빈 프론트 URL을 차단하고 SSO 직접 사번, 개발 사용자 전환, 모의 SSO, Bearer 폴백을 비활성화하며 보안 쿠키를 강제합니다. SSO 인증 성공 시 기존 세션 ID를 교체합니다.
- 매 요청의 권한·부서 범위는 Access Token의 `athIds`·`bbrC` 클레임 스냅샷으로 구성하며 DB를 재조회하지 않습니다. 최신 자격등급과 부서는 로그인·SSO 발급 및 Refresh 시 다시 조회해 새 Access Token에 반영합니다.
- Access 경로는 `tokenUse=access`를 검증하면서 용도 클레임이 없는 기존 Access Token을 만료까지 한시 허용하고, Refresh 경로는 `tokenUse=refresh`를 필수로 하여 레거시 토큰을 거부합니다.
- 프론트 라우트 가드와 메뉴 숨김은 UX 보조이며 서버가 최종 보안 경계입니다.
- 관리자 전용 컨트롤러는 클래스 수준 `@PreAuthorize("hasRole('ADMIN')")`를 적용합니다.
- 관리자 전용이 아닌 업무 컨트롤러는 서비스 계층에서 소유자·역할·업무 범위를 검증합니다.
- Cookie 기반 JWT는 Stateless여도 CSRF 검토 대상입니다. Access/Refresh/User/SSO 상태 쿠키 또는 `JSESSIONID`의 `SameSite=None` 전환, credentialed `/api/**`의 새 교차 사이트 Origin·와일드카드·패턴 허용, cross-site iframe/별도 사이트 SPA의 쿠키 API 호출, `/sso/**`의 `allowCredentials=true` 전환 중 하나라도 발생하면 같은 배포 단위에서 CSRF 토큰 또는 동등한 서버 검증 Origin/nonce 방어를 적용합니다. 모든 GET은 순수 조회로 유지하고 상태 변경 GET은 금지합니다.
- `SameSite=Lax`는 **교차 사이트**만 막고 동일 등록가능도메인의 다른 호스트(형제 서브도메인)는 동일 사이트로 취급합니다. 그 구간은 JSON 본문이 유발하는 preflight와 CORS 허용 목록이 막으므로, **변경 API가 JSON 본문을 쓴다는 전제**가 방어의 일부입니다. CORS 안전 목록 Content-Type(`multipart/form-data`, `application/x-www-form-urlencoded`, `text/plain`)은 preflight가 발생하지 않아 이 전제를 벗어납니다.
- 그래서 위 세 Content-Type의 변경 요청에는 `SimpleRequestCsrfFilter`가 `X-Requested-With` 헤더를 요구합니다. 단순 요청은 커스텀 헤더를 붙일 수 없어 헤더의 존재 자체가 preflight를 거쳤다는 증거이며, preflight가 발생하면 CORS 허용 목록이 다시 작동합니다. 파일 업로드처럼 multipart를 받는 엔드포인트를 추가할 때 별도 조치는 필요 없고, 호출하는 클라이언트가 이 헤더를 보내야 합니다(프론트는 `$apiFetch`가 자동 부착). `/sso/**`는 외부 ESSO의 전체 페이지 폼 콜백이라 제외 대상입니다.
- 변경 API에 `consumes`를 지정해 본문 형식을 못박습니다. 형식이 맞지 않는 요청은 415로 거부되므로, Content-Type이 없는 요청까지 위 필터가 중복해 막지 않습니다.
- 비밀값은 환경변수로 주입하고 운영 프로파일에서 개발용 폴백을 사용하지 않습니다.
- 사용자 HTML은 저장 전에 `HtmlSanitizer.sanitize()`를 적용합니다.
- 파일 쓰기·삭제는 업로더 또는 관리자만 허용합니다. 파일 읽기는 파일 종류(PK_COL_NM)별 authorizer가 부모 자원 권한을 재사용해 판정합니다(default-deny, 미등록 종류는 관리자만). 공통게시판=게시물 공개 여부, 요구사항정의서=관리자/작성자/주관부서, 협의회 연계(사업계획서·타당성검토표·협의회관련자료)=관리자/정보보안관리자/협의회 위원/관련부서, 가이드문서=인증 사용자 전체.
- 클라이언트 IP는 신뢰 프록시에서 온 경우에만 `X-Forwarded-For`를 사용합니다.
- `it-portal-user`의 사번·역할·부서 값은 변조 가능한 UX 상태로만 취급하고, API 권한과 데이터 범위는 JWT 기반 서버 검증으로 결정합니다.
- SSO JWT 발급은 외부 토큰 검증 결과를 서버 세션에 저장한 뒤 1회 소비하는 흐름으로 수행합니다. 직접 사번 전달은 운영에서 금지하고, 복귀 Origin은 CORS 허용 목록, 복귀 경로는 같은 사이트 상대 경로로 제한합니다.
- 수동 로그인과 사용자 전자결재 상태 변경은 MFA를 서버에서 강제합니다. 자격증명 검증(`/api/auth/login/start`)만으로는 JWT를 발급하지 않고, 결재 명령은 `@MfaRequired`가 붙은 메서드에서 `MfaGuardAspect`가 1회용 증표를 원자적으로 소비한 뒤에만 도메인 명령을 호출합니다. 조회·임시저장·외부 콜백은 대상이 아닙니다.
- MFA 증표는 사용자·용도에 귀속하며 명령 한 건에만 씁니다. 거래는 서버 메모리에만 두고 원문 대신 해시를 키로 사용하며, DB 테이블·JPA 엔티티·Flyway 스크립트를 추가하지 않습니다.
- `app.mfa.mock-enabled=true`는 `local-ext`에서만 허용하고 그 밖의 프로파일에서는 `MfaConfig`가 기동을 실패시킵니다. 운영에서 MFA를 우회하는 설정 경로를 만들지 않습니다.
- OTP, QR 원문, 외부 응답 전문, 증표 원문은 로그와 영속 저장소에 남기지 않습니다. 오류 응답에는 표준 코드(`MFA_REQUIRED`·`MFA_EXPIRED`·`MFA_FAILED`·`MFA_UNAVAILABLE`·`MFA_LOCKED`)와 정제된 메시지만 포함합니다.
- 외부 공급자 결과는 성공·실패 2값이 아니라 `MfaVerificationResult.Outcome`의 3값입니다. FIDO처럼 사용자가 다른 기기에서 승인하는 수단의 재조회는 `UNDECIDED`로 판정해 실패 횟수에 집계하지 않습니다. 이 구분이 없으면 정상 폴링이 허용 실패 횟수를 소진해 승인 전에 거래가 잠깁니다.
- `/sso/**`는 외부 ESSO의 전체 페이지 콜백 전용으로 Origin 패턴과 GET·POST·OPTIONS를 열되 `allowCredentials=false`를 유지합니다. `Path=/` Access 쿠키가 같은 사이트 요청에 포함될 수 있어도 SSO 검증 상태는 Secure·HttpOnly·SameSite=Lax인 `JSESSIONID` 서버 세션으로 분리하며, 이 예외를 `/api/**` 또는 쿠키 자격증명을 사용하는 XHR 경로로 확대하지 않습니다.

세부 정책은 [인증·인가 가이드](docs/guides/security/authentication-authorization.md), 데이터 범위는 [데이터 접근 범위 가이드](docs/guides/security/data-scope.md), 파일은 [파일 보안 가이드](docs/guides/security/file-security.md)를 따릅니다.

## 6. 부서·소유권 범위

- 클라이언트가 보낸 `bbrC`만 신뢰하지 않고 JWT 사용자 정보로 최종 범위를 결정합니다.
- 관리자는 전체 조회, 일반 사용자는 허용된 부서·소유 범위만 조회하도록 Service/Repository 조건을 연결합니다.
- `bbrC` 미동기화 계정의 처리 방식은 API별 업무 정책으로 명시하고 무조건 전체 조회로 확대하지 않습니다.
- 작성자 소속 스냅샷은 `AuthorOrgResolver`, 조직명 스냅샷은 `OrgNameResolver`를 사용합니다.
- 소유자 또는 관리자 검증은 `OwnershipVerifier` 등 공통 검증기를 사용합니다.

## 7. 이벤트·알림·외부 연동

- 원 트랜잭션과 반드시 함께 성공해야 하는 상태 동기화는 동기 `@EventListener`를 사용합니다.
- 알림·메일처럼 실패가 원 업무를 롤백하면 안 되는 부수효과는 `@TransactionalEventListener(AFTER_COMMIT)`를 사용합니다.
- AFTER_COMMIT 이후 outbox 적재와 채널 발송은 각각 `REQUIRES_NEW` 독립 트랜잭션으로 처리합니다. 적재 실패는 원 업무를 롤백하지 않으며 `notification.persist.failure` 메트릭으로 탐지합니다.
- 알림 발송 상태는 `Cinfmm.DISPATCH_*` 상수를 사용하고, 재시도 주기·배치 크기·최대 횟수는 `notification.retry.*` 설정으로 관리합니다. 서비스나 스케줄러에 별도 값을 중복 하드코딩하지 않습니다.
- 알림 종류와 채널은 `NotificationEvent.TYPE_*`, `NotificationDispatcherRouter.CHANNEL_*` 상수를 사용합니다.
- EAI 실패는 `EaiResult`로 표현하고 원 업무를 실패시키지 않으며 민감정보를 평문 로깅하지 않습니다. GWE의 `IF_ID`는 `eai.gwe.if-id`만 사용합니다.
- 외부 JSON 응답은 Jackson 버전 특정 `JsonNode`보다 전용 DTO 또는 `Map<String, Object>`로 받습니다.

상세는 [알림 가이드](docs/guides/integrations/notifications.md), [EAI 가이드](docs/guides/integrations/eai.md), [SSO 가이드](docs/guides/integrations/sso.md)를 따릅니다.

## 8. 도메인 공통 규칙

- 결재선의 `DCD_TP_C`는 현재 모든 행에 요청(`10`)을 기록하고 읽기 기능은 없습니다. 이 값을 조회·응답·분기에서 처음 소비할 때는 최종결재자를 포함한 행별 의미와 유효 코드셋을 먼저 확정하고 계약 테스트를 추가합니다.
- 공통 게시판 본문은 서버에서 정화하고 물리 컬럼 제한과 검색 최소 길이를 서버에서 검증합니다.
- 메뉴 아이콘은 `TPRMPP_CMENUM.IMK_NM`(이미지키명)에 아이콘 클래스 문자열로 저장하고 변경 스냅샷(`TPRMPP_CMENUL`)에도 같은 컬럼을 유지합니다. 이 값은 화면에서 class 속성으로 바인딩되므로 저장 시 `^[a-z0-9 -]{1,100}$`만 허용하고 공백뿐인 값은 null로 접습니다.
- `IMK_NM` 컬럼이 없는 환경(마이그레이션 미적용)에서도 **메뉴 조회는 동작해야 합니다**. `CmenumRepositoryImpl`이 데이터 사전으로 컬럼 존재를 판정해 성공한 값만 캐시하고(판정 자체가 실패하면 캐시하지 않되 60초 동안 재판정을 억제한 뒤 다음 호출에서 다시 판정합니다), 없으면 select 목록에서 그 컬럼을 빼 `MenuTreeRow` 프로젝션을 만듭니다. `MenuQueryService`는 그때만 `MenuIconDefaults`(고정 스냅샷)로 아이콘을 채우며, 컬럼이 있으면 DB의 `null`도 그대로 둡니다. 이 폴백은 **조회 전용**입니다 — 아이콘 저장과 변경로그(`TPRMPP_CMENUL.IMK_NM`), 그리고 `AdminRouteService`의 라우트 삭제 시 화면경로 중복 참조 검사(`AdminRouteService.java:107`, 엔티티 조회 `findAllActive()` 사용)는 컬럼이 있어야 동작하므로 DDL 반영을 대체하지 않습니다. 판정은 실제 성공한 값만 프로세스 수명 동안 캐시하므로, 가동 중인 시스템에 DDL을 반영한 뒤 아이콘 편집이 반영되려면 애플리케이션 재기동이 필요합니다.
- 게시판은 `/board/{게시판관리번호}` 화면경로를 가진 `PGE` 메뉴로 관리합니다. 저장과 사용자 메뉴 노출은 활성 게시판 목록으로 검증하며, 관리 트리에는 비활성 참조도 남깁니다. 경로 조립·판별은 `BoardScreenPath`, 저장 검증은 `AdminMenuService`, 사용자 메뉴 필터링은 `MenuQueryService`를 기준으로 합니다.
- `LNK` 메뉴는 경로 카탈로그에 등록된 안전한 `http(s)` 외부 URL만 참조하며 사용자 메뉴에서 새 창으로 엽니다. 경로 형식 판정은 `MenuPathPolicy`를 기준으로 합니다.
- 사업계획은 `BPLANA`에 포함된 최신·활성 사업만 대상으로 하며, 조회·생성·저장·완료는 사업 주관부서(`BPROJM.SVN_DPM_C`) 또는 관리자에게만 허용합니다.
- 사업계획 상태는 계획 마스터가 아니라 `BPROJA`의 `(ABUS_MNG_NO, 'BIZ-' + ABUS_MNG_NO)` 행에 기록합니다. 상태 전이는 작성중(21)에서 작성완료(29)로만 허용하되, 완료 후 내용 저장은 허용하고 상태 29를 유지합니다.
- 사업계획의 일정·품목·계약 목록은 `(ABUS_MNG_NO, SNO)` 기준으로 병합합니다. 같은 SNO의 삭제 행은 복원하고 요청에서 빠진 활성 행은 논리 삭제하며, 중복 SNO와 존재하지 않는 계약 SNO 참조를 저장 전에 거부합니다.
- 비목(IOE) 코드의 자본예산/일반관리비 판별과 중분류 그룹명 해석은 `IoeCategories`를 단일 기준으로 사용합니다. 예산작업·사업·정보기술부문 예산 조회가 같은 분류 집합(`CAPITAL_CTPS`)을 공유하도록 개별 서비스에 중복 정의하지 않습니다.
- 정보화사업 집행 4단계의 수정·삭제·상세 저장은 소유자 또는 관리자, 상태 전이는 관리자 권한을 검증합니다.
- 집행 문서 상태는 인접 단계만 전이하고 작성중 상태에서만 수정·삭제합니다.
- 알림 조회·읽음·삭제는 현재 사용자 소유권을 검증합니다.
- Tiptap 변수 해석은 잘못된 형식, 데이터 없음, 권한 없음 상태를 구분합니다.

상세는 [사업 집행 가이드](docs/guides/domains/project-execution.md)와 [Tiptap 변수 가이드](docs/guides/domains/tiptap-variables.md)를 참조합니다.

## 9. 테스트·주석·운영

- 기능 변경 후 `./gradlew test`, 인증·결재·파일·QueryDSL·감사로그 공통 변경은 `./gradlew clean test`를 실행합니다.
- `./gradlew test`는 Oracle 통합 태그를 제외하고 JaCoCo 보고서를 생성합니다. 병합 전 전체 품질 게이트는 Spotless와 JaCoCo 검증이 연결된 `./gradlew check`를 사용합니다.
- 로컬 Oracle 통합 테스트는 `@Tag("it")`와 `integrationTest` 태스크를 사용합니다.
- 신규 QueryDSL·JPQL·네이티브 조회를 추가할 때는 `AbstractOracleRepositoryTest` 기반 Oracle 통합 테스트로 결과 동등성, 정렬, null 계약을 함께 검증합니다.
- public API와 service 메서드 JavaDoc은 입력값·반환값·실패 조건을 한글로 기록합니다.
- Javadoc 기본 생성자 경고는 Jackson 역직렬화가 확인된 요청·입력 DTO에 한해 Javadoc을 단 명시적 no-arg 생성자 선언으로 해소합니다. 클래스 레벨 `@Builder`가 붙은 응답 DTO에는 builder용 전체 필드 생성자를 보존하기 위해 no-arg 생성자를 기계적으로 추가하지 않습니다. 전환하지 않은 대량 DTO의 기본 생성자 경고는 허용 잔여로 관리하며, 총량 기준선 수치는 CLAUDE.md가 아닌 `../TASK.md` 항목 메모 또는 `../README.md` 변경 이력에 기록합니다. 신규 코드에서 미분류 Javadoc 경고를 늘리지 않으며, 기존 허용 잔여는 기능 변경 시 의미 있는 공개 계약부터 점진 정리합니다.
- 설명 가치가 없는 단순 대입·게터에는 주석을 추가하지 않습니다.
- 로그에 비밀값·토큰·휴대폰·OTP를 기록하지 않습니다.
- 파일 로깅·롤오버 상세는 [로깅 가이드](docs/guides/operations/logging.md), 실시간 감사 피드는 [실시간 로그 가이드](docs/guides/operations/realtime-logs.md)를 따릅니다.
- 주석 예시는 [주석 스타일](docs/guides/conventions/comment-style.md)을 참조합니다.

## 10. 문서 위치

- 설치·실행·환경변수·배포: `README.md`
- 상세 개발 가이드: `docs/guides/README.md`
- 물리 DB 변경: `../it_database/migrations`
- 미구현·기술부채: `../TASK.md`
