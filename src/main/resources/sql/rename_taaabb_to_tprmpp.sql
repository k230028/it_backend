-- ============================================================================
-- TAAABB_* → TPRMPP_* 스키마 prefix 일괄 RENAME (PRD_c_20260526 §1)
-- ============================================================================
-- 운영 환경에서는 단 1회만 실행해야 합니다.
-- FK / 인덱스 / 제약조건은 Oracle이 자동으로 따라옵니다.
-- ============================================================================

SET SERVEROUTPUT ON SIZE UNLIMITED;
SET LINESIZE 200;
SET PAGESIZE 200;

DECLARE
  v_old VARCHAR2(128);
  v_new VARCHAR2(128);
  v_ok  NUMBER := 0;
  v_err NUMBER := 0;
BEGIN
  FOR r IN (
    SELECT table_name
    FROM   user_tables
    WHERE  table_name LIKE 'TAAABB\_%' ESCAPE '\'
    ORDER  BY table_name
  ) LOOP
    v_old := r.table_name;
    v_new := 'TPRMPP_' || SUBSTR(v_old, 8);
    BEGIN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || v_old || ' RENAME TO ' || v_new;
      DBMS_OUTPUT.PUT_LINE('OK   : ' || RPAD(v_old, 24) || ' -> ' || v_new);
      v_ok := v_ok + 1;
    EXCEPTION WHEN OTHERS THEN
      DBMS_OUTPUT.PUT_LINE('FAIL : ' || v_old || ' / ' || SQLERRM);
      v_err := v_err + 1;
    END;
  END LOOP;

  DBMS_OUTPUT.PUT_LINE('--------------------------------------------------');
  DBMS_OUTPUT.PUT_LINE('TOTAL OK   : ' || v_ok);
  DBMS_OUTPUT.PUT_LINE('TOTAL FAIL : ' || v_err);
END;
/

COMMIT;

PROMPT === Verification: remaining TAAABB_* ===
SELECT COUNT(*) AS remaining_taaabb FROM user_tables WHERE table_name LIKE 'TAAABB%';

PROMPT === Verification: TPRMPP_* count ===
SELECT COUNT(*) AS tprmpp_count FROM user_tables WHERE table_name LIKE 'TPRMPP%';

EXIT;
