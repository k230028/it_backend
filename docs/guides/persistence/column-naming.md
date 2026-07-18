# 컬럼 명명 가이드

엔티티·컬럼명은 `C:\it\meta\meta.txt`의 메타 용어를 우선합니다.

## 충돌 규칙

같은 물리 컬럼이 도메인마다 다른 의미이면 주 소유 도메인은 원형 camelCase를 사용하고 다른 도메인은 의미 접두사를 붙입니다.

| 물리 컬럼   | 주 소유          | 재사용 예                                  |
| ----------- | ---------------- | ------------------------------------------ |
| `BG_NO`     | `Bbugtm.bgNo`    | `Bcostm.costBgNo`, `Btermm.termBgNo`       |
| `BG_SNO`    | `Bcostm.bgSno`   | `Btermm.termBgSno`                         |
| `SVN_DPM_C` | `Bprojm.svnDpmC` | `Bcostm.costSvnDpmC`, `Btermm.termSvnDpmC` |
| `SVN_TEM_C` | `Bcostm.svnTemC` | `Btermm.termSvnTemC`                       |

같은 의미로 재사용되는 식별자·공통 값은 동일 camelCase를 사용합니다. 마스터와 `*L` 로그 미러도 같은 필드명을 사용합니다.

자동 정렬 도구의 예외 입력은 `tools/colname-align/overrides.json`에서 관리합니다. 과거 rename 과정은 이 문서에 누적하지 않고 마이그레이션과 Git 이력으로 확인합니다.
