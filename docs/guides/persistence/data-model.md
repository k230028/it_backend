# 데이터 모델 인덱스

물리 구조의 SoT는 `C:\it\it_database\migrations`, ORM 매핑의 SoT는 엔티티입니다. 이 문서는 도메인과 테이블을 빠르게 찾기 위한 인덱스입니다.

## 기본 명명

- 테이블 접두사: `TPRMPP_`
- 공통 영역: `C*`, 업무 영역: `B*`
- 마스터: `*M`, 로그: `*L`, 이력: `*H`, 관계: `*A`
- 엔티티와 대응 `*L` 로그 엔티티는 같은 업무 필드명을 사용합니다.

## 주요 매핑

| 도메인      | 주요 엔티티·테이블                                                                       |
| ----------- | ---------------------------------------------------------------------------------------- |
| 정보화사업  | `Bprojm/TPRMPP_BPROJM`, `Bproja/TPRMPP_BPROJA`, `Bitemm/TPRMPP_BITEMM`                   |
| 전산업무비  | `Bcostm/TPRMPP_BCOSTM`, `Btermm/TPRMPP_BTERMM`                                           |
| 계획·예산   | `Bplanm`, `Bplana`, `Bbugtm`                                                             |
| 문서·검토   | `Bgdocm`, `Brdocm`, `Brivgm`                                                             |
| 사업 집행   | `Bestim`, `Besttm`, `Bdelim`, `Bcontm`, `Bpaymm`, `Bpaymt`                               |
| 협의회      | `Basctm`, `Bcmmtm`, `Bevalm`, `Bperfm`, `Bpovwm`, `Bpqnam`, `Bmqnam`, `Brsltm`, `Bschdm` |
| 결재        | `Capplm`, `Cappla`, `Cdecim`                                                             |
| IAM         | `CuserI`, `CorgnI`, `CauthI`, `CroleI`, `Clognh`, `Crtokm`                               |
| 게시판      | `Cblbmm`, `Cblbcm`, `Ccmmtm`                                                             |
| 공통·인프라 | `Ccodem`, `Cfilem`, `Cinfmm`, `Cmenum`, `Cmenua`, `Cmenud`                               |
| 추가인증(MFA) | `MfaTransactionEntity/TPRMPP_CMFATM`, `LoginPendingTransactionEntity/TPRMPP_CMFADM`     |

정확한 물리 테이블명·컬럼·제약은 엔티티와 최신 마이그레이션을 함께 확인합니다.

## BPROJA 관계

`BPROJA`는 사업과 단계별 원본문서를 연결하고 단계 상태를 보관합니다. 사업 대표 상태는 유효 관계 행의 상태 집계로 계산합니다.

| 단계             | 원본     | 관계 키      |
| ---------------- | -------- | ------------ |
| 사전협의         | `BRDOCM` | 문서관리번호 |
| 예산편성         | `BBUGTM` | 예산번호     |
| 정보기술부문계획 | `BPLANM` | 요청문서번호 |
| 협의회           | `BASCTM` | 협의회 ID    |
| 소요예산         | `BESTIM` | 요청문서번호 |
| 과업심의         | `BDELIM` | 문서관리번호 |
| 입찰·계약        | `BCONTM` | 문서관리번호 |
| 대금지급         | `BPAYMM` | 문서관리번호 |

## 정보화사업 금액 계약

물리 구조는 `it_database/migrations/`, 계산·저장 동작은 정보화사업 서비스가 SoT이며, 아래 표는 `BITEMM`과 `BPROJM`의 금액 의미를 고정한다.

| 테이블 | 컬럼 | 통화 | 의미 |
| --- | --- | --- | --- |
| `TPRMPP_BITEMM` | `AMT` | KRW | 품목의 당해 원화 금액. 외화 품목도 환산된 당해 원화 금액을 저장한다. |
| `TPRMPP_BITEMM` | `FC_AMT` | 외화 | 외화 품목의 당해 원금. `CUR_C`가 `KRW`이거나 null인 품목에서는 null이어야 한다. |
| `TPRMPP_BITEMM` | `MPL_AMT` | 원금 통화 | 품목의 예정 원금. KRW/null 통화 품목은 원화 원금, 외화 품목은 외화 원금이다. |
| `TPRMPP_BPROJM` | `MPL_AMT` | KRW | 활성 품목 예정금액의 원화 환산 합계 스냅샷. |
| `TPRMPP_BPROJM` | `DFR_AMT` | KRW | 사업에 기록하는 지급 원화 금액. |
| `TPRMPP_BPROJM` | `TOT_RQM_AMT` | KRW | 당해 원화 금액, 예정 원화 금액, 지급 원화 금액을 합한 총소요금액 스냅샷. |

활성 품목은 `DEL_YN = 'N' AND LST_YN = 'Y'`로 한정한다. 예정 원화 금액은 KRW/null 통화에서 `NVL(MPL_AMT, 0)`, 외화에서 `ROUND(NVL(MPL_AMT, 0) * XCR, 3)`으로 계산한다. 외화 품목은 `FC_AMT`와 양수 `XCR`가 필요하다.

기존 업무 데이터는 이 계약으로 마이그레이션하거나 보정하지 않는다. 별도 운영 절차로 기존 데이터를 삭제한 뒤에 새로 저장하는 데이터부터 이 계약을 적용한다. 적용·공유된 과거 Flyway 스크립트는 불변이며, 물리 변경이 필요하면 새 마이그레이션으로만 추가한다.

## 공통 컬럼

`BaseEntity`는 삭제 여부, GUID, 최초 등록과 최종 변경 일시·사용자를 제공합니다. 물리 삭제 대신 Soft Delete를 사용합니다.

금액·상태처럼 계산으로 파생되는 API 필드는 테이블 컬럼으로 가정하지 않습니다. 제거·추가 이력은 마이그레이션과 Git 이력에서 확인합니다.
