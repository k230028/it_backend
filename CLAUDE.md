# IT Portal 백엔드 작업 계약

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
- 조회는 읽기 전용 트랜잭션, 명령은 Service의 명시적 트랜잭션 경계에서 처리합니다.
- 목록 API는 필요한 필터와 정렬을 DB에 적용하고 메모리 전량 필터링을 만들지 않습니다.
- DTO나 Controller 계약이 바뀌면 OpenAPI 계약 테스트와 프론트 `npm run codegen` 영향을 확인합니다.
- 업무 코드·상태·메뉴 경로 같은 식별자는 화면 표시명이 아니라 안정된 코드값을 사용합니다.

## 5. 인증·인가·데이터 범위

- Access/Refresh Token은 httpOnly 쿠키로만 전달합니다. Bearer 폴백은 명시적으로 허용된 개발·API 테스트 환경에서만 사용합니다.
- 서버 권한은 `SecurityConfig`, `@PreAuthorize`, Service 데이터 범위 검사로 강제합니다. 프론트 가드를 보안 경계로 간주하지 않습니다.
- `ROLE_INFOSEC_ADMIN`을 일반 `ROLE_ADMIN`과 합치지 않습니다. 협의회 심의유형 범위는 Service에서 검증합니다.
- 부서·작성자·소유권 필터는 [데이터 접근 범위](docs/guides/security/data-scope.md)를 따릅니다.
- 파일 업로드·다운로드·부모키 검증은 [파일 보안](docs/guides/security/file-security.md)을 따르며 경로 문자열만으로 권한을 판단하지 않습니다.
- 수동 로그인과 사용자 전자결재 상태 변경은 사용자·용도에 귀속된 1회용 MFA 증표를 요구합니다. 조회·임시저장·SSO·개발 사용자 전환·외부 결재 콜백에는 적용하지 않습니다.
- MFA 거래 상태를 서비스나 공급자의 인스턴스 로컬 필드에 두지 않습니다. 저장과 상태 전이는 `MfaTransactionStore`·`LoginPendingTransactionStore` 구현에만 맡기고, 전이는 조건부 UPDATE의 영향 행 수로 판정해 다중 인스턴스에서도 증표가 한 번만 소비되게 합니다.
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
- Tiptap 변수 카탈로그와 해석 계약은 [Tiptap 변수](docs/guides/domains/tiptap-variables.md)를 따릅니다.
- 메뉴명과 공통코드 표시명은 DB 번역 데이터가 SoT입니다. 분기와 저장에는 번역명이 아니라 코드값을 사용합니다.
- 준비중 메뉴는 화면 경로를 사람이 입력받지 않고 `/preparing/{mnuId 소문자}`로 채번해 라우트 카탈로그에 함께 등록합니다. 준비중을 해제하거나 메뉴를 삭제할 때 회수하는 대상은 이 규칙으로 자동 생성한 경로뿐이며, 사람이 직접 등록한 준비중 경로는 다른 메뉴가 참조할 수 있으므로 회수하지 않습니다.

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
