# 편성요청서 반입 가이드

부점이 제출한 편성요청서 엑셀을 원장에 반영하는 `domain.migration.request` 서브시스템의 계약을 정리합니다.
전체 이관 화면(`/admin/migration`)의 편성률 반영은 `domain.migration`의 다른 경로이며 이 문서 범위가 아닙니다.

## 1. 경로와 권한

| 항목      | 값                                                             |
| --------- | -------------------------------------------------------------- |
| API       | `POST /api/admin/migration/requests/dry-run`, `POST /api/admin/migration/requests` |
| 권한      | `SecurityConfig`의 `/api/admin/**` 규칙 + 컨트롤러 `@PreAuthorize("hasRole('ADMIN')")` |
| 요청 형식 | `multipart/form-data` — `files`(파일 목록) + `manifest`(예산연도·파일별 부가 정보·보정값) |
| 화면      | `/admin/migration/requests`                                      |

두 엔드포인트는 같은 `importBatch`를 부르고 `dryRun` 플래그만 다릅니다.
**서버는 dry-run 결과를 보관하지 않습니다.** commit은 클라이언트가 보낸 값을 신뢰하지 않고 전부 다시 검증합니다.

## 2. 처리 흐름

```
RequestFormController
  └─ RequestFormImportService            배치 오케스트레이션 (트랜잭션 없음)
       ├─ WorkbookReader                 시트 분류
       ├─ FormSheetAdapter 구현체        셀 → 생성 요청 조립 + 해석 실패 진단
       │    ├─ CapitalProjectFormAdapter    정보화사업
       │    ├─ RecurringProjectFormAdapter  경상사업
       │    └─ GeneralExpenseFormAdapter    전산업무비(시트 ③)
       ├─ RequestFormValidator           횡단 검증 (필수값·물리 길이·자연키 중복)
       ├─ RequestFormFileImporter        원장 반영 (REQUIRES_NEW — 반영의 원자 단위)
       │    └─ MigrationApprovalStamper     이관 결재 표식
       └─ RequestFormSourceFileArchiver  원본 보관 (commit 경로에서만)
```

### 트랜잭션 경계

- **`RequestFormImportService`에는 트랜잭션을 걸지 않습니다.** 반영의 원자 단위는 `RequestFormFileImporter`의
  `REQUIRES_NEW`이며, 오케스트레이터에 트랜잭션을 걸면 바깥 롤백이 안쪽 커밋과 어긋나 "정상 파일만 반영"이 성립하지 않습니다.
- 조직·비목 인덱스(`OrgIdentityResolver.snapshot()`, `IoeHierarchyIndex.snapshot()`)는 배치 시작에 한 번 만들어
  모든 파일이 공유합니다. 파일마다 만들면 수백 회 전량 조회가 됩니다.
- **편성행(`BBUGTM`)은 만들지 않습니다.** `BudgetRateApplicationService.applyItemRates`가 연도 전량을 논리삭제한 뒤
  재삽입하는 구조라, 파일마다 부르면 앞서 반입한 편성행이 전부 사라집니다. 편성은 반입을 마친 뒤 예산작업 화면에서 한 번에 적용합니다.
- `RequestFormSourceFileArchiver`는 **예외를 밖으로 던지지 않습니다.** 원장 반영이 이미 커밋된 뒤에 실행되므로,
  보관 실패로 반입 전체를 실패로 돌리면 되돌릴 수 없는 원장이 남은 채 사용자에게 실패로 보입니다.

## 3. 진단 심각도

심각도는 `RequestFormDiagnosticCode` **enum 상수에 붙어 있습니다.** 호출부가 정하지 않습니다 — 호출부에서 정하게 하면
같은 코드가 파일마다 다른 심각도로 나가 사용자가 기준을 잡을 수 없습니다.

| 심각도    | 결과                | 코드                                                                                                                                    |
| --------- | ------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| `BLOCKER` | 그 파일을 반영하지 않음 | `FILE_UNREADABLE`, `ANCHOR_NOT_FOUND`, `ORG_UNRESOLVED`, `ORG_AMBIGUOUS`, `USER_UNRESOLVED`, `USER_AMBIGUOUS`, `CODE_UNRESOLVED`, `CODE_AMBIGUOUS`, `REQUIRED_MISSING`, `DUPLICATE_EXISTS`, `LENGTH_EXCEEDED` |
| `WARNING` | 반영함              | `SHEET_NOT_FOUND`, `CODE_DEFAULTED`, `UNIT_UNCERTAIN`, `AMOUNT_MISMATCH`, `OPTIONAL_MISSING`, `SUBSTITUTE_DROPPED`, `DATE_UNPARSEABLE`      |

파일 상태(`RequestFormDto.FileStatus`)는 넷입니다.

| 상태      | 뜻                                                       |
| --------- | -------------------------------------------------------- |
| `APPLIED` | 원장에 반영됨                                             |
| `BLOCKED` | BLOCKER 진단이 남아 반영하지 않음                          |
| `FAILED`  | 예상하지 못한 오류로 실패 (해당 파일만 롤백)               |
| `SKIPPED` | 반입 대상 시트가 없어 건너뜀 — **실패가 아님**             |

`SKIPPED`가 실패가 아닌 이유: 부점 폴더 아래에 팀·사업 폴더가 더 있는 구조에서는 편성요청서가 아닌 엑셀이 함께 올라오는 것이
정상입니다. 그것을 실패로 부르면 관리자가 고칠 수 없는 붉은 줄이 결과 표를 채웁니다. 차단 건수·반영 건수 어디에도 세지 않습니다.

### 코드를 고를 때 헷갈리는 짝

- `CODE_AMBIGUOUS`(BLOCKER)는 **아무 코드도 못 정한** 상태입니다. `CODE_DEFAULTED`(WARNING)는 **합리적인 기본값을
  정했지만** 다른 선택지가 있는 상태입니다. 둘을 같은 BLOCKER로 묶으면 기본값을 제시하는 의미가 사라져, 개발비·기타무형자산
  품목이 있는 파일이 사람이 매번 같은 값을 다시 고르기 전까지 전부 차단됩니다.
- 해석 실패 진단은 **어댑터**가 냅니다. 어댑터는 셀 좌표를 아는 대신 DB 상태나 배치 전체를 보지 못합니다. 필수값·물리 길이·
  자연키 중복처럼 조립 결과를 놓고 봐야 하는 것만 `RequestFormValidator`가 봅니다.
- 공란은 진단 대상이 아닙니다. `USER_UNRESOLVED`는 **값이 있는데** 해석에 실패한 경우입니다.

## 4. 공통코드 조회 기준

이관 조회는 `MigrationIoeCatalogReader`가 담당합니다. **조회 기준을 저장 경로와 맞추는 것이 이 리더의 규칙입니다.**

| 대상                            | 조회                                       | 유효일자 |
| ------------------------------- | ------------------------------------------ | -------- |
| 환율(`xcrByCurrency`)           | `findByCIdWithValidDate`                   | 본다     |
| 통화 후보(`currencyCandidates`) | `findByCIdWithValidDate`                   | 본다     |
| 그 밖의 코드·후보               | `findByCIdAndDelYn`                        | 보지 않음 |

- 환율과 통화 후보가 유효일자를 보는 이유: 저장 경로(`CostService.createCost` → `XcrLookupService.resolveXcr`)가
  `findByCIdAndCdvaWithValidDate`로 **유효일자 재조회**를 하고 없으면 `IllegalStateException`으로 롤백합니다.
  조회 기준이 어긋나면 유효기간이 닫힌 통화가 선택지에 떠서 사전검증은 통과하고 반영에서 그 파일만 실패합니다.
- 통화 후보에서 **환율값 파싱 가능 여부까지 거르지 않습니다.** `resolveXcr`이 `KRW`를 조회 없이 통과시키므로,
  거르면 가장 흔한 통화가 선택지에서 빠집니다.
- 그 밖의 코드가 유효일자를 보지 않는 이유: 저장 경로가 유효일자로 재조회하지 않아 기준을 맞출 상대가 없고,
  여기서만 좁히면 원장에 이미 쓰여 있는 값이 이관에서만 미해석으로 떨어집니다.

## 5. 원본 보관과 열람 권한

반입 원본은 전용 테이블 없이 공통첨부파일(`TPRMPP_CFILEM`)을 재사용합니다.
`RequestFormSourceFileArchiver.PK_COL_NM = "편성요청서반입"`이 이 파일 종류의 단일 출처입니다.

- **보관 단위**는 사업 보관 그룹과 파일 처리에서 확정한 **부서코드의 조합**입니다. 사업 폴더가 같아도 검증 코드가 다르거나,
  검증 코드가 같아도 사업 폴더가 다르면 파일과 신청서번호를 섞지 않습니다.
- **디스크 기록은 파일당 1회**이고, 두 번째 연결부터는 물리 경로를 공유하는 메타행만 추가합니다
  (`FileService.linkExistingFile`). 그래서 APPLIED 파일 1건이 신청서 수만큼 메타행을 만듭니다.
- 보관은 **commit 경로에서만** 실행합니다. dry-run은 원장을 만들지 않으므로 보관할 대상도 없습니다.
- `archiveOnly=true`인 항목은 원장 파싱 없이 같은 폴더의 반입 원본으로만 보관합니다.

### 열람 권한

`RequestFormFileReadAuthorizer`가 판정합니다. 파일의 부모(`PK_CONE`)는 반입받은 **신청서번호**이므로, 그 신청서가 가리키는
원장(`TPRMPP_CAPPLA` → `BPROJM`/`BCOSTM`)의 주관부서를 사용자 부서와 비교합니다.

- 관리자 → 허용
- 연결된 **활성** 원장의 주관부서가 사용자 부서와 같으면 허용
- 그 밖(미인증, 부모 없음, 원장 없음, 매핑·원장이 논리 삭제됨) → 거부

매핑과 원장 모두 `DEL_YN='N'`인 것만 봅니다. 권한 판정은 실패 시 거부(fail closed)여야 하므로, 논리 삭제나 재매핑 뒤에도
구 부서 사용자가 원본을 계속 열람하는 경로를 남기지 않습니다.

판정은 `(PK_COL_NM, PK_CONE, user)`의 순수 함수라는 `FileReadAuthorizer`의 불변식을 지킵니다 — 개별 파일의 다른 속성을
보지 않습니다. 업로드 저장경로 정화와 다운로드 경로 검증은 [파일 보안](../security/file-security.md)을 따릅니다.

## 6. 금액 단위

시트 ③(전산업무비)의 금액 기재 단위는 원/천원/백만원 중 하나입니다. 결정 순서는 다음과 같습니다.

1. 사용자가 manifest에 지정한 `generalExpenseUnit`
2. 시트가 명시한 단위 표기(`단위: 천원` 등)
3. 원화 행 금액 크기로 추정 → `UNIT_UNCERTAIN` 경고로 확인 요청

**전 행의 통화가 확정되었고 그중 원화가 하나도 없으면 경고를 내지 않습니다.** 이 배수는 원화 행에만 걸리고 외화 행은 통화 기본
단위 그대로 `FC_AMT`로 가므로, 원화 행이 없는 시트(국외 점포 실측)에서는 어떤 값을 골라도 결과가 같습니다.

반대로 **미해석 행이 하나라도 남아 있으면 경고를 냅니다.** 그 행은 사람이 통화를 고치면 원화가 될 수 있어 "원화 행 없음"이
성립하지 않습니다. 이 구분을 빼면 통화 칸이 빈 제출본에서 사전검증에 단위 확인이 뜨지 않고, 보정 후 반영에서 배수가 확인 없이
추정 적용됩니다 — 금액이 1,000배 틀린 채 원장에 들어갈 수 있습니다.

## 7. 국문·영문 대조표

해외점포는 같은 양식을 영문으로 번역해 제출합니다. 시트명은 국문 그대로라 시트 판별은 영향받지 않지만 라벨·비목명이 전부
영문이라 국문 기준 매칭이 빗나갑니다. `FormLexicon`이 대조표를 한곳에 모아 새 표기가 나타났을 때 상수 한 줄 추가로 끝나게 합니다.

**미식별 어휘를 추측해 매핑하지 않습니다.** 원문을 그대로 넘겨 미해석 진단(`CODE_UNRESOLVED`)이 나게 하고 미리보기에서
사람이 고르게 합니다. 현재 표는 런던지점 제출본 1건에서 뽑았으므로, 다른 해외점포(시드니·뉴욕·도쿄 등) 제출본이 들어오면
미해석으로 남은 어휘를 수집해 추가합니다.

## 관련 문서

- [파일 보안](../security/file-security.md) — 업로드·다운로드·부모키 검증
- [데이터 접근 범위](../security/data-scope.md) — 부서·작성자 필터
- [레이어와 패키지](../architecture/layering-and-packages.md)
- 잔여 과제는 `../../../../TASK.md`의 `MIG-*` 항목
