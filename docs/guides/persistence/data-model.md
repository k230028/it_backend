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

## 공통 컬럼

`BaseEntity`는 삭제 여부, GUID, 최초 등록과 최종 변경 일시·사용자를 제공합니다. 물리 삭제 대신 Soft Delete를 사용합니다.

금액·상태처럼 계산으로 파생되는 API 필드는 테이블 컬럼으로 가정하지 않습니다. 제거·추가 이력은 마이그레이션과 Git 이력에서 확인합니다.
