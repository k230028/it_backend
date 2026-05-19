-- ============================================================
-- 모든 *L (로그) 테이블의 CHG_TC NULL 행을 'U'로 백필
-- BaseLogEntity.CHG_TC NOT NULL 회복 전 데이터 정리
-- ============================================================
SET DEFINE OFF;
SET SERVEROUTPUT ON;
WHENEVER SQLERROR CONTINUE;

DECLARE
    v_cnt NUMBER;
BEGIN
    FOR r IN (
        SELECT tc.TABLE_NAME
        FROM USER_TAB_COLUMNS tc
        WHERE tc.COLUMN_NAME = 'CHG_TC'
    ) LOOP
        BEGIN
            EXECUTE IMMEDIATE 'UPDATE ' || r.TABLE_NAME || ' SET CHG_TC = ''U'' WHERE CHG_TC IS NULL';
            v_cnt := SQL%ROWCOUNT;
            DBMS_OUTPUT.PUT_LINE('Backfilled ' || v_cnt || ' rows in ' || r.TABLE_NAME);
        EXCEPTION WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('FAIL ' || r.TABLE_NAME || ': ' || SQLERRM);
        END;
    END LOOP;
END;
/
COMMIT;
EXIT;
