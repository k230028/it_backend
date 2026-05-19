-- ============================================================
-- 애플리케이션 운영 시퀀스 통합 DDL
-- ============================================================
-- 대상: ddl-auto가 자동 생성하지 못하는 비즈니스 채번 시퀀스
--   1) S_ASCT          — 협의회 ID (ASCT-{YYYY}-{NNNN})
--   2) S_QTN           — 사전질의 ID
--   3) S_APF           — 결재신청서 ID (APF-{YYYY}-{NNNN})
--   4) S_APF_REL_SNO   — 결재 첨부행 순번
--   5) S_FL            — 첨부파일관리번호 (FL_{NNNNNNNN})
--
-- 별도 스크립트:
--   - audit_log_sequences_ddl.sql : *L 로그 테이블용 S_{POSTFIX} 22개
--
-- 실행 시점:
--   - DROP TABLE ... CASCADE CONSTRAINTS 직후 (시퀀스가 함께 사라짐)
--   - 신규 DB 셋업 시 audit_log_sequences_ddl.sql과 함께 실행
--
-- 멱등성: CREATE만 시도하므로 이미 존재하면 ORA-00955 발생 → 무시.
--         START WITH는 빈 테이블 기준 1. 데이터 보존 환경에서는
--         MAX(...) 기반 재계산이 필요하므로 별도 운영 절차 참조.
-- ============================================================
SET DEFINE OFF;
WHENEVER SQLERROR CONTINUE;

CREATE SEQUENCE S_ASCT        START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE S_QTN         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE S_APF         START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE S_APF_REL_SNO START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;
CREATE SEQUENCE S_FL          START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

COMMIT;
EXIT;
