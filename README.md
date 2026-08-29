# IT Portal 백엔드

정보화 예산, 사업, 문서 검토, 결재, 게시판, 알림과 관리자 기능을 제공하는 Spring Boot API 서버입니다.

## 기술 스택

- Java 25 / Spring Boot 4.1
- Gradle / Spring Data JPA / QueryDSL
- Oracle Database
- Spring Security / JWT
- JUnit 5 / Mockito / AssertJ

정확한 버전은 `build.gradle`과 Gradle lock·의존성 결과를 기준으로 확인합니다.

## 사전 준비

- JDK 25를 설치하고 `JAVA_HOME`과 `java -version`이 같은 JDK를 가리키는지 확인합니다. 별도 Gradle 설치는 필요하지 않으며 저장소의 Wrapper를 사용합니다. PATH에 다른 Gradle 설치본이 있어도 `it_backend`에서 `gradle ...`을 직접 실행하지 않습니다 — standalone Gradle의 `wrapper` 태스크가 `gradle-wrapper.properties`를 자기 버전으로 덮어써 IDE 임포트가 `Can't use Java 25.0.2 and Gradle 8.9`로 깨집니다(원복 절차는 루트 `README.md`의 「IDE 설정(VS Code)」).
- 로컬 Oracle(`127.0.0.1:11521/XEPDB1`)과 접속 계정 `ITPAPP`을 준비합니다. 객체는 `ITPOWN` 스키마에 있으며 커넥션 생성 시 `CURRENT_SCHEMA=ITPOWN`이 적용됩니다.
- `it_backend`와 `it_database`를 `C:\it` 아래 형제 디렉터리로 둡니다. 로컬 프로파일의 Flyway는 `../it_database/migrations`를 직접 읽고, Gradle `processResources`도 같은 경로의 `V*.sql`을 빌드 리소스에 포함합니다.
- 폐쇄망에서는 `C:\maven-repo`에 Gradle 9.2.1 배포본과 필요한 Maven 아티팩트가 반입되어 있어야 합니다. `gradle/wrapper/gradle-wrapper.properties`에서 온라인 `distributionUrl`을 주석 처리하고 안내된 로컬 `file:///c:/maven-repo/gradle-9.2.1-bin.zip` 항목을 활성화합니다.

## 빠른 시작

```powershell
cd C:\it\it_backend
$env:SPRING_PROFILES_ACTIVE = "local-ext"
./gradlew bootRun
```

- API: http://localhost:28080
- Swagger UI: http://localhost:28080/swagger-ui/index.html
- Health: http://localhost:28080/actuator/health
- Oracle: `ITPAPP@127.0.0.1:11521/XEPDB1`, CURRENT_SCHEMA=`ITPOWN`

Swagger UI와 API 명세는 `local-ext`·`local-int`·`dev`에서만 활성화하며, `prod`에서는 둘 다 비활성화합니다.

`local-ext`는 외부망 개발용 프로파일로 모의 SSO, 로컬 HTTP 쿠키, Swagger의 Bearer 인증 폴백과 Flyway 자동 적용을 활성화합니다. 내부 ESSO에 연결할 수 있는 환경에서는 `local-int`를 사용합니다. 두 로컬 프로파일은 개발 전용 DB·JWT 기본값을 제공하며, 환경변수를 지정하면 해당 값이 우선합니다.

| 프로파일    | 용도             | SSO         | Flyway | 쿠키 / Bearer 헤더       |
| ----------- | ---------------- | ----------- | ------ | ------------------------ |
| `local-ext` | 외부망 로컬 개발 | 모의 SSO    | 자동   | Secure 해제 / 허용       |
| `local-int` | 내부망 로컬 개발 | ESSO 실연동 | 자동   | Secure 해제 / 허용       |
| `dev`       | 개발 서버        | ESSO 실연동 | 비활성 | Secure 해제 / 허용       |
| `prod`      | 운영 서버        | ESSO 실연동 | 비활성 | Secure 적용 / 허용 안 함 |

프로파일을 지정하지 않으면 공통 설정의 보수적 기본값이 적용됩니다. 이 경우 Flyway와 개발 사용자 전환·Bearer 폴백은 꺼지고, `DB_PASSWORD`·`JWT_SECRET`이 필요하며, 프론트 URL과 CORS 허용 Origin의 기본값은 비어 있습니다. 일반 로컬 개발은 프로파일 없는 기동보다 `local-ext` 또는 `local-int`를 사용합니다.

## 주요 명령어

| 명령                                       | 용도                      |
| ------------------------------------------ | ------------------------- |
| `./gradlew bootRun`                        | 개발 서버 실행            |
| `./gradlew test`                           | 기본 단위·슬라이스 테스트 |
| `./gradlew clean test`                     | 전체 재검증               |
| `./gradlew integrationTest`                | 로컬 Oracle 통합 테스트   |
| `./gradlew spotlessCheck`                  | Java 포맷 검사            |
| `./gradlew spotlessApply`                  | Java 포맷 자동 적용        |
| `./gradlew check`                          | 포맷·테스트·커버리지 게이트 |
| `./gradlew build`                          | 품질 게이트와 WAR 빌드     |
| `./gradlew jacocoTestReport`               | 커버리지 보고서 생성      |
| `./gradlew jacocoTestCoverageVerification` | 설정된 커버리지 기준 검증 |

`test`는 `@Tag("it")` 통합 테스트를 제외하고 종료 후 JaCoCo 보고서를 생성합니다. `check`와 `build`는 `spotlessCheck`와 JaCoCo 검증까지 실행하므로 단순 테스트보다 강한 게이트이며, 현재 전체·클래스별 라인과 클래스별 분기·복잡도 커버리지 기준은 `build.gradle`의 70% 설정을 따릅니다.

Gradle Wrapper는 9.2.1을 사용합니다. 일반 의존성은 `C:\maven-repo` → 접속 가능한 내부 Nexus → Maven Central 순서로 탐색하며, 플러그인은 내부 Nexus → 로컬 저장소 → Gradle Plugin Portal/Maven Central 순서로 해석합니다. 폐쇄망에서는 Wrapper 배포본과 필요한 Maven 아티팩트를 `C:\maven-repo`에 먼저 반입하고 `gradle-wrapper.properties`의 로컬 `distributionUrl`을 활성화합니다.

로컬 폴더 저장소는 **폴더 존재가 아니라 jar 보유 여부**로 등록합니다(`build.gradle`·`settings.gradle`의 `localMavenRepoUsable`). 백신 정책 등으로 jar만 지워지면 POM과 `.module`은 남기 때문에, Gradle이 이 저장소로 모듈을 확정한 뒤 아티팩트를 찾지 못해 실패하고 뒤 순위 저장소로 폴백하지 않습니다. jar이 하나도 없으면 경고를 남기고 저장소를 아예 등록하지 않아 Nexus·Maven Central로 자동 전환됩니다. 폐쇄망 빌드가 갑자기 외부망 저장소를 찾는다면 이 경고 로그부터 확인합니다.

## 프로젝트 구조

```text
it_backend/
├── src/main/java/com/kdb/it/
│   ├── config/       Security, JPA, QueryDSL, Swagger 설정
│   ├── common/       인증, SSO, IAM, MFA, 결재, 게시판, 코드, 다국어, 알림, 관리자 공통 기능
│   ├── domain/       예산, 사업계획, 협의회, 사업 집행, 메뉴, 배너, 이관(편성요청서 반입), 감사 도메인
│   ├── exception/    전역 예외 처리
│   └── infra/        파일, AI, EAI 외부 연동
├── src/main/resources/
│   └── application*.properties   공통·프로파일별 설정
├── src/test/java/                단위·슬라이스·Oracle 통합 테스트
├── docs/guides/                  아키텍처·보안·DB·연동 상세 가이드
├── build.gradle                  의존성·품질 게이트·Flyway 리소스 구성
└── gradle/wrapper/               Gradle 9.2.1 Wrapper
```

기본 호출 방향은 Controller → Service → Repository → Oracle입니다. 자세한 패키지 책임은 [아키텍처 가이드](docs/guides/architecture/layering-and-packages.md)를 확인합니다.

## 아키텍처와 모듈 흐름

백엔드는 도메인별로 Controller·Service·Repository·Entity·DTO를 묶는 레이어드 구조입니다. `domain`과 `infra`는 인증·결재·알림 같은 `common` 기능을 사용하며, 결재 결과를 정보화사업 단계에 반영하는 검증된 흐름에서는 `common.approval`이 `domain.budget.project.BprojaSyncService`를 호출합니다. 신규 의존은 이 예외를 일반화하지 말고 순환 참조 여부를 먼저 확인합니다.

```text
HTTP 요청
  → SecurityFilterChain(CORS·공개/관리자 URL 경계)
  → JwtAuthenticationFilter(Access Token 쿠키 검증)
  → Controller(DTO 변환·Bean Validation)
  → Service(권한·업무 규칙·트랜잭션)
  → JpaRepository 또는 QueryDSL 커스텀 Repository
  → Oracle
```

Controller는 엔티티 대신 DTO로 HTTP 계약을 노출하고, 변경 요청은 `@Valid`로 검증합니다. 응답 DTO의 `@Schema`는 Swagger 문서용 장식이 아니라 프론트가 소비하는 계약입니다 — 프론트가 `/v3/api-docs`에서 TypeScript 타입을 생성하므로 여기의 `requiredMode`·`nullable`·`allowableValues`가 곧 프론트 타입이 되며, 그 계약은 `ApiResponseOpenApiContractTest`와 도메인별 `*OpenApiContractTest`가 고정합니다. 서비스는 JWT 인증 주체를 기준으로 역할·부서·소유권을 재검증하며, 조회와 쓰기 트랜잭션을 구분합니다. 처리 중 발생한 업무·검증 예외는 `GlobalExceptionHandler`가 `timestamp`, `status`, `message`를 가진 JSON 오류 응답으로 변환합니다.

| 영역                                                                          | 주요 책임                                              | 연결되는 영역                                                        |
| ----------------------------------------------------------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------- |
| `common.system`, `common.iam`                                                 | JWT 인증, Refresh Token, 로그인 이력, 사용자·조직·권한 | 전체 API의 인증 주체와 부서 범위를 제공                              |
| `common.sso`                                                                  | ESSO 연동, SSO 상태 보관, 인증 완료 복귀                | 검증한 외부 인증 결과를 `common.system`의 JWT 발급 흐름으로 전달     |
| `common.approval`                                                             | 신청서, 결재선, 승인·반려·회수                         | 사업·협의회 상태 동기화와 알림 이벤트 발행                           |
| `common.board`, `common.code`, `common.admin`                                 | 공통 게시판·코드와 관리자 운영 API. `common.admin.realtime`은 감사·실시간 로그, `common.admin.waslog`는 인메모리 링버퍼 기반 WAS 로그 조회·런타임 레벨 변경 | 파일·메뉴·사용자·감사로그 등 공통 관리 기능을 조합                   |
| `common.mfa`                                                                  | 추가 인증 거래 발급·검증·소비와 공유 저장소            | 수동 로그인과 전자결재 명령의 증표를 `common.system`·`common.approval`에 제공 |
| `common.i18n`                                                                 | 메뉴명·공통코드 표시명 번역과 변경 이력                | 메뉴·코드 조회 응답의 표시명을 언어별로 제공                         |
| `common.notification`                                                         | 인앱 알림 저장, 소유권 검증, 채널 라우팅               | 결재·게시판 이벤트와 `infra.eai` 연결                                |
| `domain.budget`                                                               | 정보화사업, 비용, 계획, 문서 검토, 예산 현황·작업. `budget.document.formguide`는 사업 입력 길라잡이를 서버 고정 카탈로그 기준으로 등록·조회 | 협의회와 사업 집행의 기준 사업 데이터를 제공                         |
| `domain.bizplan`                                                              | 정보기술부문 계획에 포함된 사업의 사업계획             | `budget.plan`, `budget.project`의 계획 관계·사업·품목·단계 상태 사용 |
| `domain.council`                                                              | 정보화실무협의회 일정·평가·질의·결과                   | 결재 완료 이벤트를 같은 트랜잭션에서 상태에 반영                     |
| `domain.estimate`, `domain.deliberation`, `domain.contract`, `domain.payment` | 사업 집행의 소요예산·심의·계약·지급 단계               | 정보화사업을 기준으로 단계별 문서와 상태를 관리                      |
| `domain.menu`                                                                 | 사용자 메뉴 조회와 관리자 메뉴·라우트 관리             | 인증 주체의 권한에 맞는 프론트 메뉴 구성을 제공                      |
| `domain.banner`                                                               | `/info` 홈 배너 등록·노출·활성 전환                    | 전용 테이블 없이 `infra.file`의 공통첨부파일을 규약(`APG_FL_KD_NM='배너'`)으로 재사용 |
| `domain.log`                                                                  | 업무 엔티티 변경 스냅샷                                | `@LogTarget`이 지정된 엔티티의 생성·수정·논리삭제를 기록             |
| `domain.migration`                                                            | 수기 엑셀(편성요청서) 반입 — 검증·진단, 원장 생성, 결재완료 표식, 원본 파일 보관 | `budget`의 원장(`BPROJM`·`BCOSTM`), `common.approval` 신청서, `infra.file` 첨부에 연결 |
| `infra.file`, `infra.eai`, `infra.ai`                                         | 파일 저장, 표준전문 외부 전송, Gemini 연동. `infra.file.authz`는 첨부파일 종류별 읽기·쓰기 판정기를 등록해 부모 자원 권한으로 접근을 판정 | 공통·도메인 서비스가 외부 자원을 사용할 때 호출                      |

## 사업계획(`bizplan`) 흐름

사업계획 API는 `/api/project/bizplans` 아래에 있으며, 정보기술부문 계획 관계인 `BPLANA`에 포함된 최신 유효 사업만 대상으로 합니다. 관리자는 전체 목록을 보고 일반 사용자는 JWT의 부서 코드와 사업 주관부서가 일치하는 항목만 조회·저장·완료할 수 있습니다.

1. 최초 진입 `POST /{abusMngNo}`은 `BBIZPM`을 멱등하게 생성하고 `BPROJA`의 `BIZ-{사업관리번호}` 단계 상태를 작성중 `21`로 맞춥니다.
2. 최초 생성 시 사업명과 예산번호를 기존 사업 데이터에서 연결하고, 저장 이력이 없는 품목과 일정은 `BITEMM`, `BPROJM`에서 초기화합니다.
3. 전체 저장 `PUT /{abusMngNo}`은 보고서·전결권과 일정(`BBIZSM`)·품목(`BBIZGM`)·계약(`BBIZCM`)을 일련번호 기준으로 병합합니다. 요청에서 빠진 활성 자식 행은 물리 삭제하지 않고 논리 삭제합니다.
4. 완료 `POST /{abusMngNo}/status`는 작성중 `21`에서 작성완료 `29`로만 전이합니다. 완료 후에도 내용 저장은 허용하며 완료 상태는 유지합니다.

사업계획의 상태를 마스터 `BBIZPM`에 중복 저장하지 않고 공통 사업 단계 테이블 `BPROJA`에서 관리하는 것이 핵심 설계입니다. 목록 조회는 `BPLANA`를 기준으로 사업·계획·상태·조직을 QueryDSL로 조합하므로, 사업계획이 아직 생성되지 않은 대상도 함께 반환합니다.

## 인증과 비동기 부수효과

- 로그인과 토큰 갱신을 제외한 API는 기본적으로 인증이 필요합니다. `JwtAuthenticationFilter`가 Access Token 쿠키를 검증하고 `CustomUserDetails`를 보안 컨텍스트에 넣습니다.
- Access Token과 Refresh Token은 httpOnly 쿠키로 전달하며 서버 세션은 만들지 않습니다. 관리자 API는 URL 규칙과 `@PreAuthorize`를 함께 사용하고, 일반 업무 API는 서비스에서 소유자·부서 범위를 추가로 검증합니다.
- 결재 상태처럼 원 업무와 반드시 함께 반영되어야 하는 변경은 동기 `@EventListener`로 같은 트랜잭션에서 처리합니다.
- 알림은 `@TransactionalEventListener(AFTER_COMMIT)`에서 처리하고 저장이 필요하면 `REQUIRES_NEW` 트랜잭션을 사용합니다. 따라서 알림 실패가 이미 성공한 원 업무를 롤백하지 않습니다.
- 알림 채널 라우터는 기본 인앱 채널과 EAI 그룹웨어 채널을 구분합니다. 외부 전송은 `EaiResult`의 성공·스킵·실패로 표현하며 예외나 실패를 원 알림 저장 흐름으로 전파하지 않습니다.

## 데이터 설계 결정

- 접속 계정 `ITPAPP`과 객체 소유 스키마 `ITPOWN`을 분리하고, 커넥션 생성 시 `CURRENT_SCHEMA`를 설정합니다. 엔티티와 쿼리에는 스키마 접두어를 하드코딩하지 않습니다.
- 업무 엔티티는 `BaseEntity`의 논리삭제, GUID, 등록·변경 감사 필드를 공유합니다. 복합키 테이블은 `@IdClass`로 기존 Oracle 물리 모델을 매핑하며, 물리 PK의 모든 컬럼을 `@Id`로 매핑합니다. 일부만 매핑하면 서로 다른 행이 같은 JPA 식별자를 갖게 되므로, 이 정합은 `PhysicalCompositeIdMappingTest`와 `PhysicalCompositeIdIsolationIt`가 고정합니다.
- 응답 직렬화에만 쓰이는 조회는 엔티티 대신 필요한 컬럼만 담는 프로젝션(`*Row` record 또는 `*View` 인터페이스)으로 읽습니다. 쓰기 엔티티와 DDL은 그대로 두고 읽기 경로만 좁히는 방식이며, 엔티티 조회와의 결과·정렬·null 동등성은 `*ProjectionIt` Oracle 통합 테스트가 확인합니다.
- `@LogTarget` 엔티티는 대응하는 `BaseLogEntity` 하위 로그 엔티티에 생성·수정·논리삭제 스냅샷을 남깁니다.
- 단순 CRUD는 `JpaRepository`를 사용하고 동적 검색·집계·다중 조인은 `*RepositoryCustom`과 `*RepositoryImpl`의 QueryDSL 구현으로 분리합니다.
- 공통코드, 메뉴 권한, 알림 미읽음 수, Tiptap 메타데이터는 Caffeine 캐시를 사용합니다. 캐시 쓰기는 트랜잭션 완료와 연동하고, 원본 변경 서비스가 `@CacheEvict`로 즉시 무효화하며 TTL은 누락에 대한 안전망으로 사용합니다.
- 정보화사업 금액은 화면·조회마다 다시 더하지 않고 활성 품목과 지급금액으로 한 번 계산해 사업 스냅샷에 기록합니다. 저장 단위 반올림과 `NUMBER(18,3)` 범위 검증을 한 곳에 모아, 외화 환산이 끼어드는 경로에서도 컬럼별 통화 의미가 갈리지 않게 합니다. 계약은 [사업 집행 가이드](docs/guides/domains/project-execution.md)와 [데이터 모델 인덱스](docs/guides/persistence/data-model.md)가 SoT입니다.
- 물리 스키마 변경의 기준은 `C:\it\it_database\migrations`이며, 엔티티 매핑과 마이그레이션을 함께 검토합니다. 상세 매핑은 [데이터 모델 인덱스](docs/guides/persistence/data-model.md)를 확인합니다.

## 환경 설정

주요 운영 값은 환경변수로 주입합니다.

| 환경변수                | 용도                                                                 |
| ----------------------- | -------------------------------------------------------------------- |
| `DB_URL`                | Oracle JDBC URL. 공통 로컬 기본값은 `127.0.0.1:11521/XEPDB1`        |
| `DB_USERNAME`           | DB 접속 계정. 기본값은 `ITPAPP`                                     |
| `DB_PASSWORD`           | 애플리케이션 DB 비밀번호                                             |
| `DB_SCHEMA`             | 객체 소유 스키마. 기본값은 `ITPOWN`                                 |
| `JWT_SECRET`            | JWT 서명 키                                                          |
| `APP_FRONTEND_URL`      | SSO 기본 복귀 URL과 기본 CORS Origin                                 |
| `CORS_ALLOWED_ORIGINS`  | 다중 CORS Origin이 필요할 때 콤마 구분으로 별도 지정                 |
| `APP_TRUSTED_PROXIES`   | `X-Forwarded-For`를 신뢰할 프록시 IP 목록                            |
| `SSO_BROWSER_BASE_URL`  | 브라우저가 접근하는 ESSO 기준 URL                                   |
| `SSO_HOST_BASE_URL`     | 백엔드가 접근하는 ESSO 기준 URL                                     |
| `SSO_AGENT_ID`          | ESSO가 발급한 업무 시스템 식별 번호                                 |
| `FILE_BASE_PATH`        | 첨부파일 저장 경로                                                   |
| `SERVER_INSTANCE_ID`    | 인스턴스 ID. 멀티 서버 파일명 충돌 방지와 WAS 로그 인스턴스 식별에 함께 사용하므로 서버마다 서로 달라야 합니다 |
| `WAS_LOG_INTERNAL_SECRET` | WAS 로그 피어 내부 API(`/internal/was-logs/**`) 공유 비밀값. 비어 있으면 내부 컨트롤러 자체가 등록되지 않습니다 |
| `WAS_LOG_PEER_SVR1`·`WAS_LOG_PEER_SVR2` | 인스턴스별 내부 호출 base URL. 지정하지 않으면 해당 인스턴스를 조회 대상에서 뺍니다 |
| `GEMINI_API_KEY`        | Gemini API 키. `prod`에서는 기동 시 필수 검증                        |
| `EAI_ENABLED`           | EAI 전송 활성화 여부. 공통 기본값 `false`, `prod` 기본값 `true`     |
| `EAI_URL`               | EAI 전송 URL. `prod`에서 EAI가 활성화되면 기동 시 필수 검증         |
| `EAI_GWE_IF_ID`         | 그룹웨어 EAI 인터페이스 ID                                          |
| `MFA_SITE_ID`           | OnePass 기관 식별자. 기본값 `SIT01KDBBANK00000000`                  |
| `MFA_SVC_ID`            | OnePass 서비스 식별자. 기본값 `SVC12SIT01KDBBANK000`                |
| `MFA_FINGER_VEIN_FIXED_KEY` | 지정맥 해시 검증용 고정키. 모의 공급자를 끈 프로파일에서 필수 |
| `MFA_FIDO_REJECTED_STATUSES` | OnePass 규격에서 사용자 거부로 확정된 FIDO 상태값. 쉼표로 구분하며 기본값은 비어 있음 |
| `JAVA_HOME`             | JDK25 설치 경로(C:\Program Files\Java\jdk-25.0.2)                 |

운영에서는 개발·로컬 프로파일의 기본값을 사용하지 않습니다. `EnvironmentValidator`는 모든 프로파일에서 DB 비밀번호와 JWT 시크릿의 빈값을 차단하고, `prod`에서는 Gemini 키, 활성 EAI URL, 프론트 URL, 명시적 CORS Origin과 운영 보안 토글을 추가로 검증합니다.

### 내부망 MFA (지정맥·FIDO·mOTP)

수동 로그인과 사용자 전자결재 명령은 MFA를 통과해야 진행됩니다. 설정은 `app.mfa.*` 구성 속성으로 묶여 있습니다.

| 속성                     | 기본값   | 설명                                                             |
| ------------------------ | -------- | ---------------------------------------------------------------- |
| `app.mfa.endpoint`       | 프로파일별 | OnePass 연동 URL. 공통 기본값은 비어 있고 프로파일에서 지정합니다 |
| `app.mfa.site-id`        | `SIT01KDBBANK00000000` | `MFA_SITE_ID`로 재정의                             |
| `app.mfa.svc-id`         | `SVC12SIT01KDBBANK000` | `MFA_SVC_ID`로 재정의                              |
| `app.mfa.connect-timeout`| `5s`     | OnePass 연결 타임아웃                                             |
| `app.mfa.read-timeout`   | `5s`     | OnePass 응답 타임아웃                                             |
| `app.mfa.challenge-ttl`  | `90s`    | MFA 거래 유효 시간                                                |
| `app.mfa.max-failures`   | `5`      | 한 거래의 허용 실패 횟수. 초과 시 `MFA_LOCKED`                    |
| `app.mfa.mock-enabled`   | `false`  | 모의 공급자 사용 여부. **`local-ext`에서만 `true`를 허용**        |
| `app.mfa.fido-rejected-statuses` | 빈 집합 | 사용자 거부로 확정된 `trStatus` 허용 목록. `MFA_FIDO_REJECTED_STATUSES`로 재정의 |
| `app.mfa.store`          | `jpa`    | `jpa`(기본)는 MFA·로그인대기 거래를 Oracle(`TPRMPP_CMFATM`/`TPRMPP_CMFADM`)에 저장해 다중 인스턴스를 지원합니다. `memory`는 인스턴스 내부에만 보관하며 단일 인스턴스 전용이고 재시작 시 유실됩니다. `spring.jpa.hibernate.ddl-auto=none`이라 기동 시 스키마를 검증하지 않으므로, `app.mfa.store=jpa`(기본값)로 기동하기 전에 반드시 마이그레이션 `V20260820_002__CreateMfaTransactionTables.sql`을 적용해야 합니다. 적용하지 않으면 기동은 정상적으로 끝나고 최초 로그인 시도에서야 Oracle "table or view does not exist" 오류로 실패합니다 |
| `app.mfa.cleanup.fixed-delay-ms` | `300000` | 만료 후 10분 유예가 지난 MFA·로그인대기 행을 Oracle에서 물리 삭제하는 배치 주기(ms). 순수한 용량 관리 설정이며 정합성에는 영향이 없습니다(모든 조회·쓰기 경로가 만료 행을 이미 자체적으로 제외합니다) |

프로파일별 동작:

| 프로파일    | MFA 동작                  | OnePass URL                                                  |
| ----------- | ------------------------- | ------------------------------------------------------------ |
| `local-ext` | 대화상자 확인 시 모의 성공 | 호출하지 않음                                                |
| `local-int` | 실제 연동                 | `https://dopsap.kdb.co.kr:20443/interfBiz/processRequest.do` |
| `dev`       | 실제 연동                 | `https://dopsap.kdb.co.kr:20443/interfBiz/processRequest.do` |
| `prod`      | 실제 연동                 | `https://opsap.kdb.co.kr:20443/interfBiz/processRequest.do`  |

`MfaConfig`는 `local-ext` 외의 프로파일에서 `app.mfa.mock-enabled=true`이면 기동을 실패시키고, 모의 공급자를 끈 상태에서 endpoint가 비어 있어도 기동을 실패시킵니다. 운영에서 MFA를 우회하는 설정 경로는 없습니다.

운영 전제와 한계:

- **BioAgent 전제** — 지정맥은 사용자 PC에서 BioAgent가 실행 중이어야 합니다. 브라우저가 `ws://127.0.0.1:8089/bio`로 연결하며, 미실행 시 화면이 실행 안내를 표시합니다. 서버는 에이전트 설치 여부를 알 수 없습니다.
- **지정맥 해시 검증** — `mfa.md`의 「지정맥인증 연계 보안방안」대로 서버가 결과를 독립 검증합니다. `FingerVeinMfaProvider`가 거래마다 6자리 랜덤키를 발급해 challenge 응답에 싣고, 화면이 그 키로 BioAgent를 호출하면 에이전트가 `년월일 + 사번 + 랜덤키 + 검증값(SUCC|FAIL) + 고정키`를 SHA-256으로 3회 해시한 값을 돌려줍니다. 서버는 성공 검증값으로 같은 해시를 만들어 상수 시간 비교하므로 **클라이언트가 보낸 결과 코드(`FE00`)를 신뢰하지 않습니다**. 랜덤키는 검증 성공·실패와 무관하게 1회 사용 후 폐기해 재전송을 막습니다.
- **지정맥 고정키** — `MFA_FINGER_VEIN_FIXED_KEY` 환경변수로 주입합니다. 비밀값이라 프로파일 파일에 기본값을 두지 않으며, 모의 공급자를 끈 프로파일에서 값이 비어 있으면 `MfaConfig`가 기동을 실패시킵니다.
- **년월일 기준** — 해시의 년월일은 서버 시계(`Clock` 빈)를 씁니다. 자정 경계에 에이전트와 서버의 날짜가 갈리면 검증이 실패할 수 있습니다.
- **FIDO 재조회** — 사용자가 휴대폰에서 승인할 때까지 화면이 결과를 반복 조회합니다. `trStatus=1`은 성공이고, `app.mfa.fido-rejected-statuses`에 등록한 값은 즉시 실패로 분리합니다. 그 밖의 값은 미결정(UNDECIDED)으로 보아 실패 횟수에 집계하지 않습니다. 현재 반입 규격에는 사용자 거부 상태값이 없으므로 기본 목록은 비어 있으며, 공급자가 값을 확정한 뒤에만 운영 환경변수로 등록합니다.
- **다중 인스턴스와 재시작** — 기본값 `app.mfa.store=jpa`에서 MFA 거래와 로그인대기 거래는 Oracle 공유 테이블에 있으므로 인스턴스가 여러 대여도, 인스턴스 하나가 재시작해도 진행 중 거래가 유지됩니다. 상태 전이는 전부 조건부 UPDATE의 영향 행 수로 판정하므로 두 인스턴스가 같은 거래를 동시에 다뤄도 증표가 두 번 소비되지 않습니다. `app.mfa.store=memory`로 바꾼 경우에만 거래가 인스턴스 메모리에 남아 재시작 시 폐기되고 사용자가 MFA를 다시 수행합니다.

### 실시간 WAS 로그 (`/admin/was-logs`)

관리자가 서버 재기동이나 파일 접근 없이 애플리케이션 로그를 보고 로그 레벨을 임시로 올릴 수 있는 기능입니다. 설정은 `app.was-log.*`로 묶여 있습니다.

| 속성                         | 기본값  | 설명                                                                                              |
| ---------------------------- | ------- | ------------------------------------------------------------------------------------------------- |
| `app.was-log.buffer-capacity` | `2000`  | 인메모리 링버퍼 용량(줄). `logback-spring.xml`이 `<springProperty>`로 같은 값을 읽어 appender에 주입하므로 이 프로퍼티가 단일 출처입니다 |
| `app.was-log.peers.{인스턴스ID}` | 비어 있음 | 인스턴스ID → 내부 호출 base URL. `WAS_LOG_PEER_SVR1`·`WAS_LOG_PEER_SVR2`로 주입합니다              |
| `app.was-log.internal-secret` | 비어 있음 | 피어 내부 API 공유 비밀값                                                                          |
| `app.was-log.connect-timeout-ms`·`read-timeout-ms` | `1000`·`3000` | 피어 호출 타임아웃(ms)                                                          |
| `app.was-log.restore-scan-ms` | `30000` | 만료된 로그레벨 오버라이드를 되돌리는 스캔 주기(ms)                                                 |

설계상 알아 둘 점:

- **저장은 프로세스 메모리뿐입니다.** `RingBufferAppender`가 링버퍼에 적재하므로 재기동 이전 로그는 조회할 수 없고, 용량을 넘으면 오래된 줄부터 버립니다. 버려진 줄이 있으면 응답이 `dropped`로 알려 화면이 배너로 표면화합니다. 장기 보관이 필요한 로그는 기존 파일 appender가 계속 담당합니다.
- **다중 인스턴스는 피어 팜아웃으로 처리합니다.** 자기 인스턴스가 아닌 대상을 조회하면 `/internal/was-logs/**`로 위임합니다. 이 경로는 `SecurityConfig`에서 `permitAll`이고 공유 비밀 헤더 `X-Internal-Token`이 유일한 관문이므로, 비밀값이 비면 컨트롤러 빈 자체를 등록하지 않아 인증 없는 로그 엔드포인트가 열리는 경로를 구조적으로 없앱니다. 운영 배포 시 방화벽에서 이 경로를 사내 서버 대역으로 제한하고 피어 URL은 HTTPS를 사용합니다.
- **로그레벨 변경은 항상 TTL을 가집니다.** Actuator 엔드포인트를 열지 않고 `LoggingSystem` 빈만 사용하며, 최대 120분·동시 50건 상한과 `com.kdb.it`·`org.springframework`·`org.hibernate` 접두사 화이트리스트를 적용합니다. 루트 로거 전체 변경은 허용하지 않습니다. 만료되면 스케줄러가 직전 레벨로 되돌리고 재기동 시에는 설정 파일 레벨로 자연 복원됩니다.
- **조회·레벨변경·다운로드는 모두 관리자 감사 로그를 남깁니다.** 로그 본문을 마스킹하지 않는 대신 누가 언제 무엇을 봤는지 남기는 것이 보상 통제이며, 내부 API는 공유 비밀 불일치로 거부한 시도도 원격 주소와 함께 기록합니다.

### 내부망 IP로 접속할 때 (CORS)

`cors.allowed-origins`는 **명시한 Origin만** 허용합니다(와일드카드 기본값 없음). `local-int` 기본값은 `http://localhost`, `http://localhost:3000`, `http://localhost:3002`뿐이므로, 프론트를 `http://<내부IP>:3000`처럼 IP로 접속하면 모든 `/api/**` 요청이 CORS 단계에서 차단됩니다.

```powershell
# 백엔드 기동 전 (IP 접속 + localhost 병행 허용)
setx APP_FRONTEND_URL "http://10.9.16.109:3000"
setx CORS_ALLOWED_ORIGINS "http://10.9.16.109:3000,http://localhost:3000"
```

- `APP_FRONTEND_URL`은 SSO 복귀 대상과 기본 CORS Origin을 함께 정합니다. 여러 Origin이 필요할 때만 `CORS_ALLOWED_ORIGINS`로 CORS만 따로 지정합니다.
- 프론트도 같은 호스트를 보도록 `NUXT_PUBLIC_API_BASE=http://<내부IP>:28080`을 맞춰야 합니다. 쿠키는 포트가 아니라 호스트 기준이라 페이지와 API 호스트가 다르면 인증 쿠키가 실리지 않습니다.
- `setx`는 새 프로세스부터 적용되므로 IDE·터미널을 재시작해야 합니다.

확인 방법 — 허용 Origin이면 `401`과 함께 `Access-Control-Allow-Origin`이 오고, 비허용이면 헤더 없는 `403`이 옵니다.

```powershell
curl -i -H "Origin: http://10.9.16.109:3000" http://127.0.0.1:28080/api/menus
```

### 리버스 프록시(same-origin) 배포 시

프론트 정적 빌드(`npm run generate`)를 nginx·WebTobe로 서빙하고 `/api/`·`/sso/`를 백엔드로 프록시하는 구조에서는 API 호출이 same-origin이라 CORS가 발생하지 않습니다. 다만 두 가지는 여전히 백엔드 설정에 의존합니다.

- **SSO 복귀 origin 검증**: SSO 완료 후 복귀 주소는 `cors.allowed-origins` 목록으로 검증합니다(`SsoController.resolveFrontendBaseUrl`). 프론트 접속 origin(로컬 nginx `http://localhost`, 운영 프론트 URL)이 목록에 없으면 SSO 성공 후 `app.frontend-url` 기본값으로 되돌아갑니다.
- **프로파일·환경변수 우선순위**: `local-ext`/`local-int` 기본값에는 `http://localhost`가 포함되어 있지만, 프로파일 없이 기동하면 허용 목록이 비어 모든 교차 출처가 차단되고, `APP_FRONTEND_URL`·`CORS_ALLOWED_ORIGINS` 환경변수가 등록되어 있으면 기본 목록을 통째로 덮어씁니다. 로컬 nginx 검증 시에는 프로파일을 지정해 기동하거나 `CORS_ALLOWED_ORIGINS`에 `http://localhost`를 직접 포함시킵니다.
- **Origin 문자열 형식**: 허용 여부는 문자열을 정확히 비교하므로 `scheme://host[:port]`만 등록합니다. 경로나 끝 슬래시가 붙은 URL은 브라우저 Origin과 일치하지 않습니다.

```powershell
# 로컬 nginx(http://localhost) 검증용 기동 예시
$env:SPRING_PROFILES_ACTIVE = "local-ext"
./gradlew bootRun
```

## 데이터베이스 변경

DDL과 데이터 마이그레이션은 `C:\it\it_database\migrations`에 새 Flyway 스크립트로 추가합니다.

```text
V20260715_001__DescribeChange.sql
```

로컬 프로파일은 자동 적용할 수 있지만 dev/prod는 DBA 검토 후 수동 적용합니다. 상세는 [Flyway 가이드](docs/guides/operations/flyway.md)를 따릅니다.

## 테스트

일반 변경:

```powershell
./gradlew test
```

인증·결재·파일·QueryDSL·감사로그처럼 공통 영향이 큰 변경:

```powershell
./gradlew clean test
```

로컬 Oracle을 사용하는 테스트는 기본 테스트에서 제외되며 `integrationTest`로 실행합니다.

Controller 계약은 MockMvc 슬라이스 테스트, 서비스 규칙은 Mockito 기반 단위 테스트, 실제 Oracle 매핑과 QueryDSL은 `@Tag("it")` 통합 테스트로 분리합니다. 기본 `test`는 통합 태그를 제외하고 종료 후 JaCoCo HTML/XML 보고서를 생성합니다. 커버리지 기준 자체를 게이트로 확인할 때는 `jacocoTestCoverageVerification`을 별도로 실행합니다.

## 빌드와 배포

```powershell
./gradlew clean build
```

`war` 플러그인과 `SpringBootServletInitializer`를 함께 사용하므로 생성된 실행 가능 WAR은 `java -jar`로 구동하거나 외장 서블릿 컨테이너에 배포할 수 있습니다. 운영 배포 전 DB·JWT·프론트 URL·CORS·파일 경로·쿠키 Secure 설정을 확인합니다. `EnvironmentValidator`는 운영 프로파일에서 필수 비밀값, CORS 와일드카드와 개발용 인증 토글을 검사하고 안전하지 않으면 기동을 중단합니다.

## 보안 참고

- 인증 토큰은 httpOnly 쿠키로 전달합니다.
- 쿠키 기반 JWT는 Stateless여도 CSRF 검토 대상입니다.
- CORS는 허용 Origin을 제한하고 `allowCredentials=true`와 와일드카드를 함께 사용하지 않습니다.
- 관리자 화면 숨김은 보안 경계가 아니며 API 권한 검증이 필요합니다.
- 첨부파일 접근은 경로나 파일명이 아니라 첨부파일 종류에 등록된 판정기가 부모 자원 권한으로 판정합니다. 알려진 종류 목록은 손으로 관리하지 않고 판정기가 선언한 종류의 합집합이라, 판정기 없는 종류를 클라이언트가 지어내 업로드하는 경로가 구조적으로 막힙니다.

세부 정책의 SoT는 [CLAUDE.md](CLAUDE.md)와 [인증·인가 가이드](docs/guides/security/authentication-authorization.md)입니다.

## 일반적인 개발 흐름

1. 대상 도메인의 Controller·Service·Repository와 기존 테스트를 확인합니다.
2. 실패 테스트 또는 검증 시나리오를 먼저 추가합니다.
3. 엔티티 변경이면 새 Flyway 스크립트를 작성합니다.
4. 권한·부서·소유권 범위를 서비스 계층에서 확인합니다.
5. 관련 테스트와 정적 빌드를 실행합니다.

## 문서

- [필수 백엔드 규칙](CLAUDE.md)
- [상세 개발 가이드](docs/guides/README.md)
- [데이터 모델 인덱스](docs/guides/persistence/data-model.md)
- [DB 마이그레이션 작성 가이드](../it_database/docs/guides/migrations.md)
- `docs/operations/`: 백엔드 배포·복구 기록
- [루트 개발 안내](../README.md)
- [기술부채와 후속 과제](../TASK.md)
