# 컬럼명 충돌 매핑표 (거버넌스 승인본)

> 컬럼명 정합 리팩토링(`docs/superpowers/specs/2026-06-03-column-name-alignment-refactor-design.md`)의
> §2.4 충돌 조합 규칙 적용 결과. 승인일: 2026-06-03.

## 규칙

같은 물리 컬럼이 여러 엔티티에서 **서로 다른 도메인 의미**로 재사용될 때:
- 의미상 **주 소유 도메인**은 `lowerCamelCase(컬럼)` **원형** 유지.
- 나머지 **재사용 도메인**은 **도메인 접두사 + 컬럼 camel**.

같은 컬럼이 어디서나 **같은 의미**이면 충돌이 아니며 균일 camelCase를 쓴다.
마스터 엔티티와 그 `*L` 로그 미러는 항상 동일 필드명을 쓴다.

## A. 진짜 의미 충돌 (소유자=원형, 나머지=접두사)

| 물리 컬럼 | 주 소유(원형) | 재사용 도메인 → 목표명 |
|---|---|---|
| `BG_NO` | Bbugtm → `bgNo` (예산관리번호) | Bcostm → `costBgNo` (IT관리비코드), Btermm → `termBgNo` |
| `BG_SNO` | Bcostm → `bgSno` (예산일련번호) | Btermm → `termBgSno` |
| ~~`TOT_XP_AMT`~~ | Bplanm → `totXpAmt` (일반관리비, 단독 소유) | 2026-06-14 물리 분리로 충돌 해소: Bcostm → `AMT`(`costTotXpAmt`, 전산업무비예산금액). `*L` 미러 포함 |
| ~~`RQM_BG_AMT`~~ | Besttm·Bpovwm → `rqmBgAmt` (소요예산금액, 동일 의미) | 2026-06-04 Bprojm → `TOT_RQM_AMT`(`totRqmAmt`), Bbugtm → `BG_DUP_AMT`(`bgDupAmt`); 2026-06-14 Btermm → `AMT`(`termRqmBgAmt`, 단말기금액). 모두 `*L` 미러 포함 |
| `CPIT_BG_APV_AMT`→`TOT_CPIT_AMT` | Bplanm → `cpitBgApvAmt` (자본예산, 단독 소유) | 2026-06-14 메타 정합 리네임(총자본금액). 필드명 유지, `*L` 미러 포함 |
| `SVN_DPM_C` | Bprojm → `svnDpmC` (주관부서, 파일럿 완료) | Bcostm → `costSvnDpmC` (담당부서), Btermm → `termSvnDpmC` |
| `SVN_TEM_C` | Bcostm → `svnTemC` | Btermm → `termSvnTemC` |

코드모드 입력: `tools/colname-align/overrides.json` (비소유자 엔트리 + `*L` 미러).

## B. 동일 의미 재사용 (균일 camelCase, 접두사 없음)

같은 개념이므로 충돌 아님:
- 식별자: `ABUS_MNG_NO`(사업관리번호), `APF_DCM_NO`(결재문서번호), `ASCT_ID`(협의회ID),
  `DOC_MNG_NO`(문서관리번호), `NAC_NO`(게시물번호), `QTN_ID`/`QTN_CONE`/`QTN_ENO`/`REP_*`(질의응답),
  `SNO`(일련번호), `ENO`(사번), `MNU_ID`/`ATH_ID`/`BLB_ID` 등
- 공통 값: `BSE_YY`(기준연도), `ABUS_TC`(사업구분), `IOE_C`(비목코드),
  `CUR_C`/`XCR`/`XCR_BSE_DT`/`FC_AMT`/`DFR_CLE_C`(통화·환율·금액 공통), `CNCD_RFR_NO`(관련참조번호)
- 회계 공통(BaseEntity): `DEL_YN`, `GUID`, `FST_ENR_DTM`/`USID`, `LST_CHG_DTM`/`USID` — 이미 규칙 준수

## C. 마스터 / `*L` 미러

`Bprojm`↔`BprojmL`, `Bcostm`↔`BcostmL`, `Bbugtm`↔`BbugtL`(불규칙: m 탈락) 등
미러는 마스터와 **같은 필드명**을 쓴다. overrides.json에 미러 엔트리를 함께 기재한다.
