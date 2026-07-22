# IT Portal 백엔드

정보화 예산, 사업, 문서 검토, 결재, 게시판, 알림과 관리자 기능을 제공하는 Spring Boot API 서버입니다.

## 기술 스택

- Java 25 / Spring Boot 4.1
- Gradle / Spring Data JPA / QueryDSL
- Oracle Database
- Spring Security / JWT
- JUnit 5 / Mockito / AssertJ

정확한 버전은 `build.gradle`과 Gradle lock·의존성 결과를 기준으로 확인합니다.

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

`local-ext`는 외부망 개발용 프로파일로 모의 SSO, 로컬 HTTP 쿠키, Swagger의 Bearer 인증 폴백과 Flyway 자동 적용을 활성화합니다. 내부 ESSO에 연결할 수 있는 환경에서는 `local-int`를 사용합니다. 두 로컬 프로파일은 개발 전용 DB·JWT 기본값을 제공하며, 환경변수를 지정하면 해당 값이 우선합니다.

| 프로파일    | 용도             | SSO         | Flyway | 쿠키 / Bearer 헤더       |
| ----------- | ---------------- | ----------- | ------ | ------------------------ |
| `local-ext` | 외부망 로컬 개발 | 모의 SSO    | 자동   | Secure 해제 / 허용       |
| `local-int` | 내부망 로컬 개발 | ESSO 실연동 | 자동   | Secure 해제 / 허용       |
| `dev`       | 개발 서버        | ESSO 실연동 | 비활성 | Secure 해제 / 허용       |
| `prod`      | 운영 서버        | ESSO 실연동 | 비활성 | Secure 적용 / 허용 안 함 |

## 주요 명령어

| 명령                                       | 용도                      |
| ------------------------------------------ | ------------------------- |
| `./gradlew bootRun`                        | 개발 서버 실행            |
| `./gradlew test`                           | 기본 단위·슬라이스 테스트 |
| `./gradlew clean test`                     | 전체 재검증               |
| `./gradlew integrationTest`                | 로컬 Oracle 통합 테스트   |
| `./gradlew build`                          | 테스트와 WAR 빌드         |
| `./gradlew jacocoTestReport`               | 커버리지 보고서 생성      |
| `./gradlew jacocoTestCoverageVerification` | 설정된 커버리지 기준 검증 |

Gradle Wrapper는 9.2.1을 사용합니다. 일반 의존성은 `C:\maven-repo` → 접속 가능한 내부 Nexus → Maven Central 순서로 탐색하며, 플러그인은 내부 Nexus → 로컬 저장소 → Gradle Plugin Portal/Maven Central 순서로 해석합니다. 폐쇄망에서는 Wrapper 배포본과 필요한 Maven 아티팩트를 `C:\maven-repo`에 먼저 반입합니다.

## 프로젝트 구조

```text
src/main/java/com/kdb/it/
├── config/       Security, JPA, QueryDSL, Swagger 설정
├── common/       인증, IAM, 결재, 게시판, 코드, 알림, 관리자 공통 기능
├── domain/       예산, 협의회, 사업 집행, 메뉴, 감사 도메인
├── exception/    전역 예외 처리
└── infra/        파일, AI, EAI 외부 연동
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

Controller는 엔티티 대신 DTO로 HTTP 계약을 노출하고, 변경 요청은 `@Valid`로 검증합니다. 서비스는 JWT 인증 주체를 기준으로 역할·부서·소유권을 재검증하며, 조회와 쓰기 트랜잭션을 구분합니다. 처리 중 발생한 업무·검증 예외는 `GlobalExceptionHandler`가 `timestamp`, `status`, `message`를 가진 JSON 오류 응답으로 변환합니다.

| 영역                                                                          | 주요 책임                                              | 연결되는 영역                                                        |
| ----------------------------------------------------------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------- |
| `common.system`, `common.iam`                                                 | JWT 인증, Refresh Token, 로그인 이력, 사용자·조직·권한 | 전체 API의 인증 주체와 부서 범위를 제공                              |
| `common.approval`                                                             | 신청서, 결재선, 승인·반려·회수                         | 사업·협의회 상태 동기화와 알림 이벤트 발행                           |
| `common.notification`                                                         | 인앱 알림 저장, 소유권 검증, 채널 라우팅               | 결재·게시판 이벤트와 `infra.eai` 연결                                |
| `domain.budget`                                                               | 정보화사업, 비용, 계획, 문서 검토, 예산 현황·작업      | 협의회와 사업 집행의 기준 사업 데이터를 제공                         |
| `domain.bizplan`                                                              | 정보기술부문 계획에 포함된 사업의 사업계획             | `budget.plan`, `budget.project`의 계획 관계·사업·품목·단계 상태 사용 |
| `domain.council`                                                              | 정보화실무협의회 일정·평가·질의·결과                   | 결재 완료 이벤트를 같은 트랜잭션에서 상태에 반영                     |
| `domain.estimate`, `domain.deliberation`, `domain.contract`, `domain.payment` | 사업 집행의 소요예산·심의·계약·지급 단계               | 정보화사업을 기준으로 단계별 문서와 상태를 관리                      |
| `domain.log`                                                                  | 업무 엔티티 변경 스냅샷                                | `@LogTarget`이 지정된 엔티티의 생성·수정·논리삭제를 기록             |
| `infra.file`, `infra.eai`, `infra.ai`                                         | 파일 저장, 표준전문 외부 전송, Gemini 연동             | 공통·도메인 서비스가 외부 자원을 사용할 때 호출                      |

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
- 업무 엔티티는 `BaseEntity`의 논리삭제, GUID, 등록·변경 감사 필드를 공유합니다. 복합키 테이블은 `@IdClass`로 기존 Oracle 물리 모델을 매핑합니다.
- `@LogTarget` 엔티티는 대응하는 `BaseLogEntity` 하위 로그 엔티티에 생성·수정·논리삭제 스냅샷을 남깁니다.
- 단순 CRUD는 `JpaRepository`를 사용하고 동적 검색·집계·다중 조인은 `*RepositoryCustom`과 `*RepositoryImpl`의 QueryDSL 구현으로 분리합니다.
- 공통코드, 메뉴 권한, 알림 미읽음 수, Tiptap 메타데이터는 Caffeine 캐시를 사용합니다. 캐시 쓰기는 트랜잭션 완료와 연동하고, 원본 변경 서비스가 `@CacheEvict`로 즉시 무효화하며 TTL은 누락에 대한 안전망으로 사용합니다.
- 물리 스키마 변경의 기준은 `C:\it\it_database\migrations`이며, 엔티티 매핑과 마이그레이션을 함께 검토합니다. 상세 매핑은 [데이터 모델 인덱스](docs/guides/persistence/data-model.md)를 확인합니다.

## 환경 설정

주요 운영 값은 환경변수로 주입합니다.

| 환경변수               | 용도                                   |
| ---------------------- | -------------------------------------- |
| `DB_PASSWORD`          | 애플리케이션 DB 비밀번호               |
| `JWT_SECRET`           | JWT 서명 키                            |
| `APP_FRONTEND_URL`     | SSO 복귀 URL과 기본 CORS Origin        |
| `CORS_ALLOWED_ORIGINS` | 다중 CORS Origin이 필요할 때 별도 지정 |
| `FILE_BASE_PATH`       | 첨부파일 저장 경로                     |
| `GEMINI_API_KEY`       | Gemini 기능을 사용할 때만 지정         |
| `DB_SCHEMA`            | 기본값 `ITPOWN`                        |

운영에서는 개발·로컬 프로파일의 기본값을 사용하지 않습니다. `EnvironmentValidator`가 필수 비밀값 누락을 검사합니다.

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
- [루트 개발 안내](../README.md)
- [기술부채와 후속 과제](../TASK.md)
