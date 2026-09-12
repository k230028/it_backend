# QueryDSL과 Oracle 가이드

## QueryDSL

- 선택 조건은 `StringUtils.hasText` 등으로 유효성을 확인한 후 `BooleanBuilder`에 추가합니다.
- 페이지네이션은 `offset`, `limit`, 정렬은 명시적 `OrderSpecifier`를 사용합니다.
- 공개 기간·권한·부서 조건처럼 보안에 영향을 주는 조건은 의도를 JavaDoc 또는 인접 한글 주석으로 남깁니다.
- Repository에서 DB 예외를 비즈니스 예외로 임의 변환하지 않습니다.

## 네이티브 결과 타입

Oracle JDBC와 Hibernate 버전에 따라 같은 컬럼도 다른 Java 타입으로 반환될 수 있습니다.

| Oracle        | 가능한 타입                     | 변환               |
| ------------- | ------------------------------- | ------------------ |
| `VARCHAR2(1)` | `Character`, `String`           | `value.toString()` |
| `TIMESTAMP`   | `Timestamp`, `LocalDateTime`    | `instanceof` 분기  |
| `NUMBER`      | `BigDecimal`, `Long`, `Integer` | `Number` 메서드    |

직접 `(String) row[n]` 같은 캐스트를 반복하지 말고 공통 매퍼를 사용합니다.

## Jackson 2·3 공존

`RestClient` 응답은 Jackson 버전 특정 `JsonNode` 대신 전용 DTO 또는 `Map<String, Object>`로 받습니다. 실제 HTTP 메시지 컨버터를 지나는 통합 테스트로 확인합니다.

## 쿼리의 `+` 문자

Base64·암호문을 쿼리 파라미터로 보낼 때 `+`가 폼 디코딩 과정에서 공백으로 바뀔 수 있습니다. 값을 `URLEncoder.encode`로 먼저 인코딩하고 이미 인코딩된 URI로 조립합니다.

## 비관적 잠금과 잠금 대기

- 동시 실행이 서로를 덮어쓰는 명령은 `findByIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`)로 원장 행을 먼저 잠급니다. 잠금 조회에는 `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))`를 두어 무한 대기를 막습니다.
- 잠금 대기 초과는 JPA `LockTimeoutException`, Spring `CannotAcquireLockException`, Oracle `ORA-30006`·`ORA-00054`로 다르게 나타나므로 `LockTimeouts.isLockTimeout(Throwable)` 하나로 판정합니다. 예외 사슬 전체를 봅니다.
- 판정과 409 변환은 Repository가 아니라 Service의 동시성 가드(`CostConcurrencyGuard`·`ProjectConcurrencyGuard`·`ItBudgetApprovalFacade`)에서 수행합니다. 도메인마다 예외 분기를 다시 만들지 않습니다.
