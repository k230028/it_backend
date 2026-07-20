# 파일 부모 키 정규화 배포 기록 (SEC-05)

업무 파일(`TPRMPP_CFILEM`)의 뒤바뀐 레거시 부모 키(`PK_COL_NM` ↔ `PK_CONE`)를 정규화하는
Flyway migration `V20260719_001__NormalizeCfilemParentKeys.sql`의 배포 기록이다.
SEC-05 파일 읽기 인가 전환은 `PK_COL_NM`(종류) → authorizer, `PK_CONE`(부모 ID) → 부모 권한
재사용을 전제로 하므로, 인가 코드 배포 전에 이 데이터 정규화를 먼저 적용한다.

## 1. 배포 전 분류 (2026-07-19 로컬 Oracle, `DEL_YN='N'`)

`PK_CONE`가 6종 종류값이면 뒤바뀐 행, `PK_COL_NM`이 6종 종류값이면 정상 방향, 둘 다 아니면 격리로 분류한다.
(6종: 요구사항정의서, 가이드문서, 사업계획서, 타당성검토표, 협의회관련자료, 공통게시판)

| SHAPE | 건수 | 의미 |
| --- | --- | --- |
| `SWAPPED_RECOVERABLE` | 49 | `PK_CONE`에 종류값, `PK_COL_NM`에 부모 ID가 들어간 뒤바뀐 행 (정규화 대상) |
| `CANONICAL` | 1 | 이미 정상 방향(`PK_COL_NM`=종류, `PK_CONE`=부모 ID) |
| `QUARANTINED` | 7 | 부모 ID를 복구할 수 없는 행 (관리자 전용 격리) |
| **합계(활성)** | **57** | |

정규화 후 기대 상태: 정상 방향 50건(49 정규화 + 1 기존 정상) + 격리 7건.

분류 SQL:

```sql
SELECT CASE
         WHEN PK_CONE IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
              AND PK_COL_NM IS NOT NULL THEN 'SWAPPED_RECOVERABLE'
         WHEN PK_COL_NM IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
              AND PK_CONE IS NOT NULL THEN 'CANONICAL'
         ELSE 'QUARANTINED'
       END AS SHAPE, COUNT(*) AS CNT
FROM TPRMPP_CFILEM
WHERE DEL_YN = 'N'
GROUP BY CASE
           WHEN PK_CONE IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
                AND PK_COL_NM IS NOT NULL THEN 'SWAPPED_RECOVERABLE'
           WHEN PK_COL_NM IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
                AND PK_CONE IS NOT NULL THEN 'CANONICAL'
           ELSE 'QUARANTINED'
         END;
```

## 2. 정규화 migration (멱등)

`V20260719_001__NormalizeCfilemParentKeys.sql` 본문:

```sql
UPDATE TPRMPP_CFILEM
SET PK_COL_NM = PK_CONE,
    PK_CONE = PK_COL_NM
WHERE PK_CONE IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
  AND PK_COL_NM IS NOT NULL
  AND PK_COL_NM NOT IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판');
```

- Oracle은 같은 `SET` 절의 우변을 변경 전 행 기준으로 평가하므로 두 컬럼이 원자적으로 교환된다.
- 재실행 시 `PK_CONE`이 더 이상 종류값이 아니어서 0건 갱신된다(멱등). 격리 행은 조건에 걸리지 않아 건드리지 않는다.

## 3. 배포 후 게이트

```sql
-- (1) 반드시 0이어야 함
SELECT COUNT(*) AS RECOVERABLE_REMAINING
FROM TPRMPP_CFILEM
WHERE DEL_YN = 'N'
  AND PK_CONE IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판')
  AND PK_COL_NM IS NOT NULL;

-- (2) 격리 목록 (관리자 전용) — 현재 기준 7건 유지 확인
SELECT FL_MPN_ID, PK_COL_NM, PK_CONE
FROM TPRMPP_CFILEM
WHERE DEL_YN = 'N'
  AND (PK_COL_NM IS NULL OR PK_CONE IS NULL
       OR PK_COL_NM NOT IN ('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판'))
ORDER BY FL_MPN_ID;
```

`RECOVERABLE_REMAINING`이 0이 아니거나 격리 건수가 7이 아니면 인가 코드 배포를 중단하고 원인을 먼저 확정한다.

## 4. 격리 목록 (부모 ID 복구 불가, 관리자 전용)

파일 경로·본문은 기록하지 않고 식별 메타데이터만 남긴다. 모두 요구사항정의서 계열이며 부모 ID(`DOC_MNG_NO`)를
확정할 수 없는 상태다. 6건은 종류(`PK_COL_NM`)마저 비어 있고, 1건은 부모 ID(`PK_CONE`)가 비어 있다.

| FL_MPN_ID | PK_COL_NM | PK_CONE | 형태 |
| --- | --- | --- | --- |
| `FL_00000044` | (NULL) | `요구사항정의서` | 종류·부모 미상 (excalidraw scene 계열) |
| `FL_00000054` | (NULL) | `요구사항정의서` | 종류·부모 미상 |
| `FL_00000055` | (NULL) | `요구사항정의서` | 종류·부모 미상 |
| `FL_00000056` | (NULL) | `요구사항정의서` | 종류·부모 미상 |
| `FL_00000058` | (NULL) | `요구사항정의서` | 종류·부모 미상 |
| `FL_00000400` | (NULL) | `요구사항정의서` | 종류·부모 미상 |
| `FL_00000402` | `요구사항정의서` | (NULL) | 종류만 있고 부모 ID 없음 |

> 위 형태에서 `PK_CONE=요구사항정의서`(종류가 부모 자리)인 6건은 migration 조건의 `PK_COL_NM IS NOT NULL`을
> 만족하지 못해 정규화 대상에서 제외되고, `PK_CONE`가 NULL인 1건도 조건에 걸리지 않는다. 따라서 격리 7건은
> migration 실행 후에도 그대로 남는다.

## 5. 격리 행 수동 재연결 절차

격리 행은 자동 추정·삭제하지 않는다. 관리자가 다음 절차로만 재연결한다.

1. 관리자가 원 업무 화면(요구사항정의서 상세)에서 해당 첨부의 실제 부모 문서를 대조·확정한다.
2. 확정된 경우에만 기존 파일 메타 수정 API(`FileOwnershipChecker.verifyWriteAccess` 경로)로
   `PK_COL_NM`(종류)과 `PK_CONE`(부모 ID)를 표준 방향으로 채운다.
3. 변경 이력(감사 로그)을 남긴다. 부모를 확정하지 못한 행은 격리 상태를 유지하며 일반 사용자에게 노출하지 않는다.
4. 예상하지 못한 종류가 새로 나타나면 인가 코드(authorizer 레지스트리) 배포를 중단하고 규칙을 먼저 확정한다.

## 6. 운영(dev/prod) 적용

- 운영은 `spring.flyway.enabled=false`이므로 애플리케이션이 migration을 자동 적용하지 않는다.
- DBA가 승인된 DB 배포 절차에서 `V20260719_001__NormalizeCfilemParentKeys.sql`을 **먼저** 적용한 뒤
  인가 코드가 포함된 애플리케이션을 배포한다.
- 로컬(`local-int`/`local-ext`)만 기동 시 Flyway가 자동 적용한다.

## 7. 배포 현황

| 환경 | 상태 | 비고 |
| --- | --- | --- |
| 로컬(local-int) | **적용 완료 (2026-07-19)** | Flyway 5건 적용 성공(협의회/계획 배치 `20260718.005~008` + 본 `20260719.001`), 전부 `success=1`. 게이트 확인: `RECOVERABLE_REMAINING=0`, `CANONICAL=50`, 격리 7건(FL_00000044/54/55/56/58/400 + 402) 그대로 유지. |
| dev/prod | 미적용 | DBA가 승인된 DB 배포 절차에서 `V20260719_001__NormalizeCfilemParentKeys.sql`을 애플리케이션(인가 코드) 배포 **전에** 적용한다. |

> **선행 결함 해결**: 로컬 적용을 막던 `it_database`의 중복 버전 문제(`20260718.001`=`AddBprojDvmTemColumn`↔`UpdateCouncilDbrTcLabels`, `20260718.002`=`CorrectBtermmTeamCodeFromCuseri`↔`SeedPlanCouncilMemberType`)는 **미적용 협의회/계획 배치 4건**(`UpdateCouncilDbrTcLabels`·`SeedPlanCouncilMemberType`·`AddBasctmPlanRef`·`CreatePlanEvalTable`)을 순서를 보존하며 `20260718.005~008`로 개번해 해소했다(적용 완료된 `.001/.002`는 유지). dev/prod DBA도 동일하게 개번된 마이그레이션 세트를 적용한다.
