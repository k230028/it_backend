# Flyway 운영 가이드

## 위치와 이름

마이그레이션은 `C:\it\it_database\migrations`에 둡니다.

```text
V{YYYYMMDD_NNN}__{CamelCase설명}.sql
```

DDL과 필요한 데이터 백필을 한 변경 단위로 구성하고 재실행 가능성을 고려합니다. 성공 적용된 파일은 체크섬 대상이므로 수정하지 않습니다.

## 적용 정책

- Gradle `processResources`가 마이그레이션을 classpath에 포함합니다.
- `local-ext`, `local-int`만 자동 적용합니다.
- dev/prod는 DBA 검토 후 수동 적용합니다.
- 애플리케이션 계정과 DDL 계정이 다르면 `FLYWAY_USER`, `FLYWAY_PASSWORD`를 사용합니다.
- 빈 스키마와 기존 baseline 스키마의 적용 경로를 각각 검증합니다.

## 컬럼 변경

- rename은 명시적 `ALTER TABLE ... RENAME COLUMN`을 사용합니다.
- 타입 변경은 임시 컬럼, 데이터 변환, 기존 컬럼 제거, rename 순서로 안전하게 수행합니다.
- NOT NULL 추가는 기존 데이터 백필 후 제약을 적용합니다.
- 엔티티 변경, 로그 미러, 인덱스·시퀀스와 테스트 픽스처를 함께 확인합니다.
