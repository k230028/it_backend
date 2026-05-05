# 백엔드 데이터 모델

> IT Portal 백엔드 도메인 엔티티 ↔ Oracle 테이블 매핑.
> 이 문서는 `it_backend/CLAUDE.md`에서 분리된 참조 자료입니다. SoT는 실제 엔티티 클래스(`@Table` 주석)이며, 본 문서는 빠른 조회용 인덱스입니다.

## 1. 명명 규칙

- 테이블 prefix: `TAAABB_`
- 마스터 테이블: `*M` (예: `BPROJM`, `BCOSTM`)
- 로그 테이블: `*L` (예: `BPROJML`, `BCOSTML`) — `BaseLogEntity` 상속, `ChangeLogEntityListener`가 자동 기록
- 연결/매핑: `*A` (예: `CAPPLA`, `BPROJA`)
- 코드/공통: `C*` 접두 (예: `CCODEM`, `CFILEM`)

## 2. 도메인별 테이블 매핑

### 2.1 예산 (budget)

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| Bprojm  | TAAABB_BPROJM   | 정보화사업 마스터 |
| Bitemm  | TAAABB_BITEMM   | 프로젝트 품목     |
| Bcostm  | TAAABB_BCOSTM   | 전산관리비        |
| Btermm  | TAAABB_BTERMM   | 단말기            |
| Bplanm  | TAAABB_BPLANM   | 정보기술부문 계획 |
| Bproja  | TAAABB_BPROJA   | 계획-사업 연결    |
| Bbugtm  | TAAABB_BBUGTM   | 예산 편성률       |
| Bgdocm  | TAAABB_BGDOCM   | 가이드 문서       |
| Brdocm  | TAAABB_BRDOCM   | 요구사항 정의서   |
| Brivgm  | TAAABB_BRIVGM   | 요구사항 검토의견 |

### 2.2 협의회 (council)

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| Basctm  | TAAABB_BASCTM   | 협의회 심의과제   |
| Bchklc  | TAAABB_BCHKLC   | 타당성 검토항목   |
| Bcmmtm  | TAAABB_BCMMTM   | 평가위원          |
| Bevalm  | TAAABB_BEVALM   | 평가의견          |
| Bperfm  | TAAABB_BPERFM   | 성과지표          |
| Bpovwm  | TAAABB_BPOVWM   | 사업개요          |
| Bpqnam  | TAAABB_BPQNAM   | 사전질의응답      |
| Brsltm  | TAAABB_BRSLTM   | 결과서            |
| Bschdm  | TAAABB_BSCHDM   | 일정              |

### 2.3 IAM / 인증 / 결재

| 엔티티   | 테이블명         | 역할              |
|---------|-----------------|------------------|
| CuserI  | TAAABB_CUSERI   | 사용자/직원 정보  |
| CorgnI  | TAAABB_CORGNI   | 조직/부점 정보    |
| CauthI  | TAAABB_CAUTHI   | 자격등급          |
| CroleI  | TAAABB_CROLEI   | 역할 매핑         |
| Capplm  | TAAABB_CAPPLM   | 신청서 마스터     |
| Cappla  | TAAABB_CAPPLA   | 신청서-원본 연결  |
| Cdecim  | TAAABB_CDECIM   | 결재선 정보       |
| Clognh  | TAAABB_CLOGNH   | 로그인이력        |
| Crtokm  | TAAABB_CRTOKM   | 갱신토큰          |

### 2.4 공통 / 인프라

| 엔티티   | 테이블명         | 역할         |
|---------|-----------------|-------------|
| Ccodem  | TAAABB_CCODEM   | 코드 마스터  |
| Cfilem  | TAAABB_CFILEM   | 첨부파일     |

## 3. 채번 규칙

- 정보화사업 관리번호: `PRJ-{사업연도}-{4자리 시퀀스}` (예: `PRJ-2026-0001`)
- 신청서 관리번호: `APF_{연도}{8자리 시퀀스}`
- 시퀀스 값은 Oracle Native Query로 조회합니다.

## 4. 변경 로그(`*L`) 기록 원칙

- `BaseLogEntity` 상속 + `ChangeLogEntityListener` 자동 기록
- 원본 엔티티별 1:1 로그 엔티티(`BprojmL`, `BcostmL`, `BrdocmL` 등)
- 변경 이력 조회는 `LST_CHG_DTM` 기준 정렬

## 5. 공통 컬럼(`BaseEntity`)

모든 업무 엔티티가 상속:

- `DEL_YN` : 논리 삭제 여부 (`'N'` = 미삭제, `'Y'` = 삭제)
- `GUID` : UUID 자동 생성
- `FST_ENR_DTM` / `FST_ENR_USID` : 최초 등록 일시/사용자 (JPA Auditing)
- `LST_CHG_DTM` / `LST_CHG_USID` : 최종 변경 일시/사용자 (JPA Auditing)

물리 삭제 금지. 항상 `delete()` 메서드 호출(`DEL_YN='Y'`).
