# 엔티티와 감사 로그

## 업무 엔티티

- `BaseEntity`를 상속합니다.
- `@Column`에 메타 용어 기반 이름, 길이와 한글 comment를 지정합니다. CLOB 컬럼은 `@Lob`으로 선언하고 길이를 지정하지 않습니다.
- 서비스 검증을 거치지 않고 직접 생성·복제되는 스냅샷 문자열(담당자·부서명 등)은 엔티티 `@PrePersist`/`@PreUpdate`에서 `Utf8ByteLimit`으로 BYTE 한도를 한 번 더 검증합니다(`Bcostm`·`Bprojm` 참고). 초과는 `IllegalArgumentException`으로 실패시키고 잘라 저장하지 않습니다.
- 삭제는 `delete()`로 `DEL_YN='Y'`를 설정합니다.
- 복합키는 `@IdClass`를 사용합니다.

## 감사 로그

감사 대상 업무 엔티티에 `@LogTarget(entity = XxxL.class)`를 지정하고, 대응 로그 엔티티는 `BaseLogEntity`를 상속합니다.

```text
업무 엔티티 변경
→ ChangeLogEntityListener
→ AuditLogPersister
→ 대응 *L 엔티티 저장
```

업무 엔티티가 `BaseLogEntity`를 직접 상속하지 않습니다. 마스터와 로그 미러는 같은 업무 필드명을 유지합니다.

## NOT NULL 기본값 함정

엔티티 리스너가 엔티티 자체 `@PrePersist`보다 먼저 감사 스냅샷을 만들 수 있습니다. 고유 NOT NULL 필드의 기본값을 `@PrePersist`에서만 채우면 로그 엔티티에 null이 복사될 수 있습니다.

```java
public static Entity create(...) {
    Entity entity = new Entity();
    entity.useYn = "N";
    return entity;
}
```

`@PrePersist`는 방어적 폴백으로 둘 수 있지만 객체 생성 시점에 유효한 값을 보장합니다.

새 감사 대상을 추가할 때는 업무 엔티티, 로그 엔티티, 로그 테이블·시퀀스, 관리자 로그 정의와 회귀 테스트를 함께 확인합니다.
