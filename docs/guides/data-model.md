# 백엔드 데이터 모델

> IT Portal 백엔드 도메인 엔티티 ↔ Oracle 테이블 매핑.
> 이 문서는 `it_backend/CLAUDE.md`에서 분리된 참조 자료입니다. SoT는 실제 엔티티 클래스(`@Table` 주석)이며, 본 문서는 빠른 조회용 인덱스입니다.

## 1. 명명 규칙

- 테이블 prefix: `TPRMPP_`
- 마스터 테이블: `*M` (예: `BPROJM`, `BCOSTM`)
- 로그 테이블: `*L` (예: `BPROJML`, `BCOSTML`) — `BaseLogEntity` 상속, `ChangeLogEntityListener`가 자동 기록
- 연결/매핑: `*A` (예: `CAPPLA`, `BPLANA`)
- 코드/공통: `C*` 접두 (예: `CCODEM`, `CFILEM`)

## 2. 도메인별 테이블 매핑

### 2.1 예산 (budget)

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| Bprojm  | TPRMPP_BPROJM   | 정보화사업 마스터 |
| Bproja  | TPRMPP_BPROJA   | 정보화사업관계 (사업↔단계문서 연결, 상태 정규화) |
| Bitemm  | TPRMPP_BITEMM   | 프로젝트 품목     |
| Bcostm  | TPRMPP_BCOSTM   | 전산관리비        |
| Btermm  | TPRMPP_BTERMM   | 단말기            |
| Bplanm  | TPRMPP_BPLANM   | 정보기술부문 계획 |
| Bplana  | TPRMPP_BPLANA   | 정보기술부문계획 관계 |
| Bbugtm  | TPRMPP_BBUGTM   | 예산 편성률       |
| Bgdocm  | TPRMPP_BGDOCM   | 가이드 문서       |
| Brdocm  | TPRMPP_BRDOCM   | 요구사항 정의서   |
| Brivgm  | TPRMPP_BRIVGM   | 요구사항 검토의견 |

### 2.2 협의회 (council)

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| Basctm  | TPRMPP_BASCTM   | 협의회 심의과제   |
| Bcmmtm  | TPRMPP_BCMMTM   | 평가위원          |
| Bevalm  | TPRMPP_BEVALM   | 평가의견          |
| Bperfm  | TPRMPP_BPERFM   | 성과지표          |
| Bpovwm  | TPRMPP_BPOVWM   | 사업개요          |
| Bpqnam  | TPRMPP_BPQNAM   | 사전질의응답      |
| Brsltm  | TPRMPP_BRSLTM   | 결과서            |
| Bschdm  | TPRMPP_BSCHDM   | 일정              |

### 2.3 IAM / 인증 / 결재

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| CuserI  | TPRMPP_CUSERI   | 사용자/직원 정보  |
| CorgnI  | TPRMPP_CORGNI   | 조직/부점 정보    |
| CauthI  | TPRMPP_CAUTHI   | 자격등급          |
| CroleI  | TPRMPP_CROLEI   | 역할 매핑         |
| Capplm  | TPRMPP_CAPPLM   | 신청서 마스터     |
| Cappla  | TPRMPP_CAPPLA   | 신청서-원본 연결  |
| Cdecim  | TPRMPP_CDECIM   | 결재선 정보       |
| Clognh  | TPRMPP_CLOGNH   | 로그인이력        |
| Crtokm  | TPRMPP_CRTOKM   | 갱신토큰          |

### 2.4 공통 / 인프라

| 엔티티   | 테이블명         | 역할         |
|---------|-----------------|-------------|
| Ccodem  | TPRMPP_CCODEM   | 코드 마스터  |
| Cfilem  | TPRMPP_CFILEM   | 첨부파일     |

## 3. 주요 테이블 컬럼 메모

### Bitemm / TPRMPP_BITEMM (프로젝트 품목)

업무상 자주 참조되는 컬럼:

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `ABUS_MNG_NO` | VARCHAR2(32) | 프로젝트 관리번호 (PK, FK → TPRMPP_BPROJM) |
| `IOE_C` | VARCHAR2(10) | 비목코드 (PK) |
| `AMT` | NUMBER(18,3) | 품목 금액 (당해 + 이월 합산) |
| `MPL_AMT` | NUMBER(18,3) | 예정금액 — 익년(예산연도+1) 이후로 이월 예정인 금액. 0 ≤ MPL_AMT ≤ AMT. |
| `DEL_YN` | VARCHAR2(1) | 논리 삭제 여부 |

`BitemmL` (TPRMPP_BITEML) 로그 엔티티에도 동일 컬럼이 미러됩니다.

### Bprojm / TPRMPP_BPROJM (정보화사업 마스터)

> **컬럼 삭제 이력 (2026-06-22)**: `TOT_RQM_AMT`(당해예산), `MPL_CPIT_AMT`(예정자본금액), `MPL_MNGC_AMT`(예정관리비금액) 3개 컬럼이 물리 테이블에서 제거되었습니다.
> 이 세 값은 이제 품목(`Bitemm`)의 `MPL_AMT`를 집계하여 파생합니다 (`ProjectBudgetSummaryService`):
> - `totRqmAmt` (당해예산) = max(0, ∑AMT − ∑MPL_AMT)
> - `mplCpitAmt` (예정자본금액) = ∑MPL_AMT (자본 비목, IOE_C 기준)
> - `mplMngcAmt` (예정관리비금액) = ∑MPL_AMT (관리비 비목, IOE_C 기준)
>
> `ProjectDto.Response`에는 세 필드가 그대로 노출되나, DB 컬럼이 아닌 파생 계산값입니다.
> `BprojmL` (TPRMPP_BPROJL) 로그 엔티티에서도 동일하게 제거되었습니다.
>
> **컬럼 삭제 이력 (2026-06-24)**: `IT_PTL_STS_TC`(IT포탈상태구분코드) 컬럼이 BPROJM 및 BPROJL에서 제거되었습니다.
> 프로젝트 대표상태는 `TPRMPP_BPROJA`(정보화사업관계)의 `MAX(IT_PTL_STS_TC)` 집계값으로 파생합니다.
> 상세 설계: `docs/superpowers/specs/2026-06-24-bproja-status-relation-design.md`

### Bproja / TPRMPP_BPROJA (정보화사업관계)

> 프로젝트(BPROJM) ↔ 단계별 원본문서를 연결하고 단계별 상태를 보관하는 관계 테이블.
> **프로젝트 대표상태 = 해당 사업의 BPROJA 행 중 `MAX(IT_PTL_STS_TC)`**.
> 상세 설계: `docs/superpowers/specs/2026-06-24-bproja-status-relation-design.md`

| 컬럼명 | 타입 | PK | NULL | Default | 설명 |
|--------|------|----|------|---------|------|
| `ABUS_MNG_NO` | VARCHAR2(30) | Y | N | | 사업관리번호 (FK → TPRMPP_BPROJM) |
| `CNCD_RFR_NO` | VARCHAR2(30) | Y | N | | 관련참조번호 — 단계 원본문서의 PK |
| `IT_PTL_STS_TC` | VARCHAR2(2) | | Y | | IT포탈상태구분코드 |
| `FST_ENR_USID` | VARCHAR2(14) | | N | '00000000000000' | 최초등록사용자ID |
| `FST_ENR_DTM` | DATE | | N | sysdate | 최초등록일시 |
| `DEL_YN` | VARCHAR2(1) | | N | 'N' | 삭제여부 |
| `GUID` | VARCHAR2(38) | | N | '0…0'(38자리) | GUID |
| `GUID_PRG_SNO` | NUMBER(4) | | N | 0 | GUID진행일련번호 |
| `LST_CHG_USID` | VARCHAR2(14) | | N | '00000000000000' | 최종변경사용자ID |
| `LST_CHG_DTM` | DATE | | N | sysdate | 최종변경일시 |

단계별 원본테이블 ↔ `CNCD_RFR_NO` 키 매핑:

| 단계 | 원본테이블 | CNCD_RFR_NO 키 |
|------|-----------|----------------|
| 사전협의 | TPRMPP_BRDOCM | DOC_MNG_NO |
| 예산편성 | TPRMPP_BBUGTM | BG_NO |
| 정보기술부문계획 | TPRMPP_BPLANM | REQ_DOC_NO |
| 타당성검토 | TPRMPP_BASCTM | IT_PTL_ASCT_ID |
| 소요예산 | TPRMPP_BESTIM | RQM_BG_REQ_DOC_NO |
| 과업심의 | TPRMPP_BDELIM | DOC_MNG_NO |
| 입찰계약 | TPRMPP_BCONTM | DOC_MNG_NO |
| 대금지급 | TPRMPP_BPAYMM | DOC_MNG_NO |

## 4. 채번 규칙

- 정보화사업 관리번호: `PRJ-{사업연도}-{4자리 시퀀스}` (예: `PRJ-2026-0001`)
- 신청서 관리번호: `APF-{연도}-{8자리 시퀀스}`
- 로그 일련번호:  BITEML-{22자리 시퀀스}`
- 시퀀스 값은 Oracle Native Query로 조회합니다.

## 5. 변경 로그(`*L`) 기록 원칙

- `BaseLogEntity` 상속 + `ChangeLogEntityListener` 자동 기록
- 원본 엔티티별 1:1 로그 엔티티(`BprojmL`, `BcostmL`, `BrdocmL` 등)
- 변경 이력 조회는 `LST_CHG_DTM` 기준 정렬

## 6. 공통 컬럼(`BaseEntity`)

모든 업무 엔티티가 상속:

- `DEL_YN` : 논리 삭제 여부 (`'N'` = 미삭제, `'Y'` = 삭제)
- `GUID` : UUID 자동 생성
- `FST_ENR_DTM` / `FST_ENR_USID` : 최초 등록 일시/사용자 (JPA Auditing)
- `LST_CHG_DTM` / `LST_CHG_USID` : 최종 변경 일시/사용자 (JPA Auditing)

물리 삭제 금지. 항상 `delete()` 메서드 호출(`DEL_YN='Y'`).
