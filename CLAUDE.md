# IT정보화포탈 백엔드 작업 계약

이 파일은 백엔드 변경 시 반드시 지킬 규칙만 정의합니다. 설치·실행·배포·문제 해결은 [README](README.md), 배경과 예제는 [가이드 인덱스](docs/guides/README.md)를 따릅니다. 상위 공통 규칙은 [루트 CLAUDE](../CLAUDE.md)를 함께 적용합니다.

## 1. 기술과 구조

- Java 25, Spring Boot 4, Gradle Wrapper, Oracle, Spring Data JPA·QueryDSL을 사용합니다.
- standalone `gradle`을 실행하지 말고 항상 `./gradlew`를 사용합니다.
- 호출 방향은 `controller → service → repository → entity`입니다. Controller는 검증·응답 변환, Service는 트랜잭션·권한·업무 흐름, Repository는 영속성만 담당합니다.
- 도메인 코드는 `com.kdb.it.domain.{domain}`, 공통 기능은 `common`, 외부 연동은 `infra`에 둡니다.
- 엔티티를 API로 직접 반환하지 않고 DTO로 변환합니다.
- 상세 레이어 규칙은 [레이어와 패키지](docs/guides/architecture/layering-and-packages.md)를 따릅니다.

## 2. Oracle과 영속성

- 접속 계정은 `ITPAPP`, 객체 소유자는 `ITPOWN`입니다. 세션의 `CURRENT_SCHEMA=ITPOWN`을 전제로 코드에 스키마 접두어를 쓰지 않습니다.
- 물리 모델은 `../it_database/migrations/`, ORM은 엔티티, 조회용 색인은 [데이터 모델 인덱스](docs/guides/persistence/data-model.md)가 SoT입니다.
- 엔티티·컬럼명은 `../meta/meta.txt`를 우선하고, 충돌 처리만 [컬럼 명명 가이드](docs/guides/persistence/column-naming.md)를 따릅니다.
- Oracle 빈 문자열은 NULL이므로 빈 문자열과 NULL을 다른 업무 상태로 설계하지 않습니다.
- ITPOWN의 문자셋은 `AL32UTF8`이며 `VARCHAR2(n BYTE)`에는 UTF-8 실제 바이트 길이로 저장 한도를 적용합니다. `@Size(max=n)`이나 `String.length()`만으로 BYTE 한도를 검증하지 말고 공통 `Utf8ByteLimit`을 재사용하며, 글자 수 업무 제한이 따로 있으면 두 조건을 각각 검증합니다.
- 목록·대시보드 조회는 전체 엔티티보다 필요한 컬럼만 반환하는 projection/read view/Row DTO를 우선 사용하고 Service에서 API DTO로 변환합니다.
- 네이티브 `Object[]` 결과는 중앙 `NativeRowMapper`와 전용 `fromRow` 팩토리로 변환하며, SELECT 컬럼 수·순서 불일치를 경계에서 검출합니다.
- 정보화사업 금액은 화면·서비스마다 다시 계산하지 않고 `ProjectAmountCalculator`가 산출한 스냅샷을 사용합니다. 저장 단위 반올림과 `NUMBER(18,3)` 범위 검증은 한 곳에 모아 적용하고, 통화별 환산 규칙과 컬럼별 원금 의미는 [데이터 모델의 정보화사업 금액 계약](docs/guides/persistence/data-model.md)과 [사업 집행 가이드](docs/guides/domains/project-execution.md)를 SoT로 따릅니다.
- 복합키는 기존 `@IdClass` 패턴을 유지하고 equals/hashCode 계약을 함께 검증합니다.
- 감사 필드와 NOT NULL 기본값은 [엔티티와 감사 로그](docs/guides/persistence/entities-and-audit.md)를 따릅니다.
- QueryDSL·네이티브 타입·Jackson 경계는 [QueryDSL과 Oracle](docs/guides/persistence/querydsl-and-oracle.md)을 따릅니다.

## 3. 마이그레이션

- DDL·시드·데이터 보정은 `../it_database/migrations/V{YYYYMMDD_NNN}__{CamelCaseDescription}.sql`로 추가합니다.
- 적용된 Flyway 스크립트는 수정하지 않습니다. 변경은 새 버전으로 추가합니다.
- 로컬 프로파일만 애플리케이션 Flyway를 사용하고 dev/prod는 DBA 적용을 전제로 합니다.
- 엔티티 변경에는 해당 마이그레이션과 스키마 검증을 함께 추가합니다.
- 상세 작성·복구 규칙은 [DB 마이그레이션 가이드](../it_database/docs/guides/migrations.md)와 [Flyway 운영](docs/guides/operations/flyway.md)을 따릅니다.

## 4. API와 서비스

- 요청은 Bean Validation으로 검증하고 공통 예외 응답 계약을 사용합니다.
- `@ModelAttribute`로 바인딩하는 DTO의 서버 전용 필드는 setter를 만들지 않고(`@Setter(AccessLevel.NONE)`) 의미를 드러내는 전용 메서드로만 켭니다. `@Schema(hidden = true)`는 OpenAPI 노출만 막고 쿼리 파라미터 바인딩은 막지 못합니다. setter 제거는 Lombok 어노테이션 정리로 조용히 되살아나므로, 해당 컨트롤러 전용 `@ControllerAdvice`의 `@InitBinder`에서 `setDisallowedFields`로 그 필드를 함께 차단합니다.
- 사용자·외부에서 온 HTML을 저장할 때는 서버가 `HtmlSanitizer`로 정화한 뒤 저장합니다. 프론트의 렌더 직전 정화는 이중 방어이지 서버 정화를 대신하지 않습니다.
- HTML 본문의 "비어 있음" 판정은 정화 **결과**로 다시 수행합니다. `@NotBlank`와 길이 검증은 정화 전 원문에 걸리므로 정화가 전부 제거한 본문을 통과시킵니다.
- 서버가 문자열 연결로 HTML(메일·알림 본문·게시글 머리말 등)을 조립할 때 삽입하는 사용자·외부 값은 `HtmlUtils.htmlEscape`로 이스케이프합니다. 이 경로는 `HtmlSanitizer`를 타지 않으므로 정화 규칙이 적용되지 않으며, 도메인마다 이스케이프 헬퍼를 새로 만들지 않습니다.
- 목록 API의 페이지 파라미터는 검색 조건 DTO에 섞지 않고 공통 `ListPageParams`를 별도 `@ModelAttribute`로 바인딩합니다. 미지정이면 상한까지 조회하고, 지정하면 그 구간만 조회하면서 조건에 맞는 전체 건수를 `X-Total-Count` 헤더로 함께 돌려줍니다. 본문 배열 계약은 그대로 두며, 잘못된 페이지 입력은 기본값으로 되돌리지 말고 예외로 구분합니다. 새 헤더를 응답에 실으면 `SecurityConfig`의 `setExposedHeaders`에 함께 등록합니다.
- 신청서 상태 전이·결재선 변경처럼 동시 실행이 서로를 덮어쓰는 명령은 원장 행을 `findByIdForUpdate`(`PESSIMISTIC_WRITE`)로 잠근 뒤 수행합니다.
- 조회는 읽기 전용 트랜잭션, 명령은 Service의 명시적 트랜잭션 경계에서 처리합니다.
- 조회 중심 Service는 클래스 수준 `@Transactional(readOnly = true)`를 기본으로 두고, 쓰기 메서드만 `@Transactional`로 명시적으로 오버라이드합니다.
- 목록 API는 필요한 필터와 정렬을 DB에 적용하고 메모리 전량 필터링을 만들지 않습니다.
- 목록 API는 전체 엔티티 fetch 대신 필요한 컬럼의 projection/read view/Row DTO를 우선하고, 명시적 안정 정렬과 페이지 크기 또는 상한을 DB 쿼리에 함께 둡니다.
- 여러 식별자를 조립하는 bulk 조회는 `IN` 배치 읽기와 bounded request size를 사용하며, 찾지 못한 식별자는 성공 데이터에 섞지 말고 호출자가 구분할 수 있게 보존합니다.
- DTO나 Controller 계약이 바뀌면 OpenAPI 계약 테스트와 프론트 `npm run codegen` 영향을 확인합니다.
- `@Schema(requiredProperties)`, nullable, enum 계약을 바꾸면 OpenAPI 계약 테스트와 프론트 `app/types/api.d.ts` 재생성을 같은 변경 묶음으로 완료합니다.
- 설정·이관 입력은 값이 없을 때만 업무상 기본값을 적용합니다. malformed 값은 absent/default로 폴백하지 말고 셀 진단, 경고 또는 차단으로 구분합니다.
- 업무 코드·상태·메뉴 경로 같은 식별자는 화면 표시명이 아니라 안정된 코드값을 사용합니다.

## 5. 인증·인가·데이터 범위

- Access/Refresh Token은 httpOnly 쿠키로만 전달합니다. Bearer 폴백은 명시적으로 허용된 개발·API 테스트 환경에서만 사용합니다.
- 서버 권한은 `SecurityConfig`, `@PreAuthorize`, Service 데이터 범위 검사로 강제합니다. 프론트 가드를 보안 경계로 간주하지 않습니다.
- `ROLE_INFOSEC_ADMIN`을 일반 `ROLE_ADMIN`과 합치지 않습니다. 협의회 심의유형 범위는 Service에서 검증합니다.
- `athIds`에서 역할로 변환하는 매핑은 명시적 allowlist로 유지하며, 미지원 자격등급을 관리자·특수 권한으로 매핑하지 않습니다. 새 역할은 매핑과 회귀 테스트를 함께 추가합니다.
- 부서·작성자·소유권 필터는 [데이터 접근 범위](docs/guides/security/data-scope.md)를 따릅니다.
- 파일 업로드·다운로드·연결 대상 검증은 [파일 보안](docs/guides/security/file-security.md)을 따르며 경로 문자열만으로 권한을 판단하지 않습니다.
- 첨부파일 종류(`APG_FL_KD_NM`)를 새로 쓰려면 읽기 또는 쓰기 판정기를 먼저 등록합니다. 알려진 종류 목록은 손으로 관리하지 않고 판정기가 선언한 종류의 합집합이므로, 판정기 없는 종류가 업로드 경로로 조용히 생기지 않습니다. 쓰기 판정기를 추가할 때는 범용 파일 API의 수정·삭제를 열어 둘지(`allowsGenericMutation`)를 기본값에 맡기지 말고 종류마다 명시적으로 판단합니다.
- 클라이언트 입력이 파일시스템 경로 세그먼트가 될 때는 허용문자 필터와 정규화 후 기준 디렉터리 포함 검증을 함께 겁니다. 읽기와 쓰기 양쪽에 같은 2단 검증을 적용합니다.
- 첨부 ZIP의 엔트리명은 저장 파일명·상대경로 원문을 그대로 쓰지 않고 경로 구분자·제어문자·상위 이동(`..`)을 제거하거나 거부한 뒤 `AttachmentArchiveSupport.uniqueEntryName`에 넘깁니다. 이 유틸은 중복만 해소하므로 정화 책임은 호출자에게 있으며, 여기서 지키는 대상은 서버가 아니라 ZIP을 푸는 수신자의 PC입니다.
- 관리자 전용 API는 `/api/admin/**` 경로에 두어 `SecurityConfig` URL 규칙을 받고 클래스 수준 `@PreAuthorize("hasRole('ADMIN')")`를 함께 답니다. 사용자 API와 경로를 공유해야 하면 관리자 메서드마다 `@PreAuthorize`를 명시하고 클래스 JavaDoc에 보호 범위를 적습니다.
- 클라이언트가 보낸 화면 경로·복귀 URL은 서버도 단일 `/`로 시작하는 내부 경로만 허용하고 `//`와 스킴(`://`) 포함 값을 거부합니다. 프론트의 같은 검증을 신뢰하지 않습니다.
- 목록·bulk·건수 조회는 상세와 같은 데이터 범위를 공유합니다. 부서 범위는 클라이언트가 보낸 조건이 아니라 인증 주체에서 유도해 덮어쓰며, 조건이 비어 있다는 이유로 전체 조회를 열지 않습니다. 상세만 보호하고 목록·bulk를 열어 두면 같은 응답 DTO가 우회 경로로 나갑니다.
- 인증 주체 판정은 fail-closed를 기본으로 합니다. principal이 기대한 타입이 아니면 권한 없음으로 처리하고, 권한 문자열만으로 관리자 여부를 단정하지 않습니다.
- 보안 목적이 아닌 식별자라도 난수는 `SecureRandom`으로만 만듭니다. 미지원 환경을 위한 약한 난수 폴백을 두지 말고 명시적으로 실패시킵니다.
- 수동 로그인과 사용자 전자결재 상태 변경은 사용자·용도에 귀속된 1회용 MFA 증표를 요구합니다. 조회·임시저장·SSO·개발 사용자 전환·외부 결재 콜백에는 적용하지 않습니다.
- MFA 거래 상태를 서비스나 공급자의 인스턴스 로컬 필드에 두지 않습니다. 저장과 상태 전이는 `MfaTransactionStore`·`LoginPendingTransactionStore` 구현에만 맡기고, 전이는 조건부 UPDATE의 영향 행 수로 판정해 다중 인스턴스에서도 증표가 한 번만 소비되게 합니다.
- MFA pending/proof는 httpOnly·운영 Secure·SameSite=Lax 쿠키로만 전달하고 응답 본문이나 JavaScript 상태에 넣지 않습니다. 쿠키 수명은 서버 거래의 잔여 TTL을 넘기지 않습니다.
- CSRF 토큰을 사용하지 않는 동안 unsafe 단순 Content-Type 요청은 `X-Requested-With` 헤더를 요구하며, 프론트 인증 fetch는 `credentials: 'include'`와 해당 헤더를 함께 보냅니다. 이 헤더 값 자체는 토큰으로 간주하지 않습니다.
- 운영 프로파일은 비밀값·Origin·프론트 URL을 fail-fast로 검사하고 모의 SSO, 직접 사번, 개발 사용자 전환, Bearer 폴백, 비보안 쿠키를 허용하지 않습니다.
- 전체 계약은 [인증과 인가](docs/guides/security/authentication-authorization.md)를 SoT로 사용합니다.

## 6. 이벤트와 외부 연동

- 트랜잭션 성공 뒤 실행해야 하는 알림·메일은 커밋 이후 이벤트로 처리하고 본 업무 트랜잭션과 실패 경계를 분리합니다.
- 알림 상태·재시도는 [알림 가이드](docs/guides/integrations/notifications.md), SSO는 [SSO](docs/guides/integrations/sso.md), EAI는 [EAI](docs/guides/integrations/eai.md)를 따릅니다.
- 외부 응답 문자열·HTML·파일명을 신뢰하지 말고 허용 목록, 정규화, 크기 제한과 안전한 오류 매핑을 적용합니다.
- EAI 전문 로그에는 개인정보·비밀값을 남기지 않고 승인된 필드만 마스킹 후 기록합니다.

## 7. 도메인 공통 규칙

- 게시판, 댓글, 첨부파일의 권한과 순서는 서버가 최종 검증합니다. 클라이언트가 보낸 작성자·부서·순서를 신뢰하지 않습니다.
- 정보화사업 집행 계약은 [사업 집행 가이드](docs/guides/domains/project-execution.md)를 따릅니다.
- 정보화사업과 경상사업은 같은 `BPROJM` 원장에서 `(ABUS_MNG_NO, SNO)`로 재상신 개정본을 식별하며 `ODN_YN='Y'`가 경상사업, NULL 또는 `N`이 정보화사업입니다. 전산업무비는 `BCOSTM`의 `(BG_NO, BG_SNO)`를 사용합니다. 결재완료 최종본만 다음 순번의 `LST_YN='N'` 초안으로 복제하고, 승인 완료 이벤트가 가리키는 정확한 순번만 원자적으로 `Y`로 전환합니다. 후속 업무 목록·집계·bulk 조회는 `LST_YN='Y'`만 소비하되 `apfSts=none` 미상신 작성 목록은 재상신 초안을 포함하고 순번을 반환합니다.
- Tiptap 변수 카탈로그와 해석 계약은 [Tiptap 변수](docs/guides/domains/tiptap-variables.md)를 따릅니다.
- 사업 입력 길라잡이의 대상 필드는 서버 고정 카탈로그가 SoT입니다. 길라잡이 ID는 사업 유형 접두사로 범위를 구분하고, 카탈로그에 없는 ID는 저장하지 않습니다. 사용자 조회 API는 본문이 등록된 항목만 돌려주고 카탈로그 전체 조회와 등록·삭제는 관리자 전용입니다.
- 메뉴명과 공통코드 표시명은 DB 번역 데이터가 SoT입니다. 분기와 저장에는 번역명이 아니라 코드값을 사용합니다.
- 전용 테이블 없이 `BGDOCM` 단일 문서로 운영하는 기능(공통 안내 팝업, 스피드다이얼 담당자 정보 등)은 `DOC_TTL_CONE` 고정 식별자와 그 식별자로 범위를 좁힌 조건부 유일 인덱스로 "활성 1건" 불변식을 지킵니다. 최초 생성은 동시 저장의 유일 제약 실패가 호출자 트랜잭션을 오염시키지 않도록 `REQUIRES_NEW`로 분리합니다. **`DOC_MNG_NO` 접두사는 기능마다 새로 정합니다** — 기존 접두사를 재사용하면 접두사만으로 필터링하는 다른 기능의 목록·상세에 그 문서가 섞여 노출됩니다.
- 전용 화면 없이 공통 게시판을 재사용하는 기능은 게시판을 이름이나 관리번호로 찾지 않고 `BoardTypeResolver.requireUniqueActiveBoard(유형코드)`로 해석합니다. 활성 게시판이 없거나 둘 이상이면 조용히 첫 건을 고르지 말고 실패시킵니다.
- 준비중 메뉴는 화면 경로를 사람이 입력받지 않고 `/preparing/{mnuId 소문자}`로 채번해 라우트 카탈로그에 함께 등록합니다. 준비중을 해제하거나 메뉴를 삭제할 때 회수하는 대상은 이 규칙으로 자동 생성한 경로뿐이며, 사람이 직접 등록한 준비중 경로는 다른 메뉴가 참조할 수 있으므로 회수하지 않습니다. 준비중 화면의 안내 문구는 그 카탈로그 행의 비고(RMK)를 사용자에게 그대로 보여주므로 비고에 내부 메모를 적지 않습니다.

## 8. 주석·로그·테스트

- 신규 주석은 한글로 작성하고 [주석 스타일](docs/guides/conventions/comment-style.md)을 따릅니다.
- 애플리케이션 로그와 실시간 로그의 책임은 [로깅](docs/guides/operations/logging.md), [실시간 로그](docs/guides/operations/realtime-logs.md)를 따릅니다.
- WAS 로그 링버퍼는 관리자 화면과 다운로드로 원문이 나가는 경로이므로 적재 전에 마스킹하고, 조회·레벨변경·다운로드는 예외 없이 관리자 감사 로그를 남깁니다. 클라이언트가 보낸 문자열을 로그에 남길 때는 개행·제어문자를 제거해 로그 줄 위조를 막습니다.
- 런타임 로그레벨 변경은 Actuator 엔드포인트를 열지 않고 `LoggingSystem` 빈으로만 처리하며, 항상 TTL·동시 건수 상한·로거 접두사 화이트리스트를 함께 적용합니다. 루트 로거 전체 변경은 허용하지 않습니다.
- 인스턴스 간 내부 전용 엔드포인트는 공유 비밀 헤더로만 인증하고, 비밀값이 비어 있으면 컨트롤러 빈 자체를 등록하지 않아 인증 없는 경로가 열리지 않게 합니다.
- 순수 Service·유틸은 JUnit 단위 테스트, MVC·보안 계약은 MockMvc/통합 테스트, 영속성 변경은 Oracle 호환 검증을 추가합니다.
- 의존성·보안·DB 변경은 관련 회귀 테스트와 문서 계약을 함께 갱신합니다.

변경 범위에 맞게 다음 명령을 실행합니다.

```powershell
./gradlew test
./gradlew check
./gradlew bootJar
```

특정 테스트는 `./gradlew test --tests '패키지.클래스명'`으로 먼저 확인하되 완료 전에는 영향 범위 전체를 실행합니다.

## 9. 문서 위치

- 설치·실행·환경·배포·문제 해결: [README](README.md)
- 상세 개발 가이드: [docs/guides/README.md](docs/guides/README.md)
- DBA 배포·복구 기록: `docs/operations/`
- 물리 마이그레이션: `../it_database/migrations/`
- 미구현·기술부채: `../TASK.md`
