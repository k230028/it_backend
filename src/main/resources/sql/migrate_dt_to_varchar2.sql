-- ============================================================
-- DT 도메인 컬럼: DATE → VARCHAR2(8) 마이그레이션
-- 대상 컬럼:
--   TAAABB_BASCTM.CNRC_DT, TAAABB_BASCTL.CNRC_DT
--   TAAABB_BSCHDM.DSD_DT,  TAAABB_BSCHDL.DSD_DT
--   TAAABB_BPERFM.MSM_STT_DT, TAAABB_BPERFL.MSM_STT_DT
--   TAAABB_BPERFM.MSM_END_DT, TAAABB_BPERFL.MSM_END_DT
-- 형식: 임시 컬럼 추가 → TO_CHAR(.., 'YYYYMMDD') 변환 → 원본 DROP → RENAME
-- BSCHDM.DSD_DT는 PK 일부지만 ALTER 시 데이터 손실 회피 위해 동일 방식 적용
-- ============================================================
SET DEFINE OFF;
SET SERVEROUTPUT ON;
WHENEVER SQLERROR CONTINUE;

DECLARE
    TYPE t_target IS RECORD (tbl VARCHAR2(64), col VARCHAR2(64));
    TYPE t_arr    IS TABLE OF t_target;
    targets t_arr := t_arr(
        t_target('TAAABB_BASCTM','CNRC_DT'),
        t_target('TAAABB_BASCTL','CNRC_DT'),
        t_target('TAAABB_BSCHDM','DSD_DT'),
        t_target('TAAABB_BSCHDL','DSD_DT'),
        t_target('TAAABB_BPERFM','MSM_STT_DT'),
        t_target('TAAABB_BPERFL','MSM_STT_DT'),
        t_target('TAAABB_BPERFM','MSM_END_DT'),
        t_target('TAAABB_BPERFL','MSM_END_DT')
    );
    v_type VARCHAR2(30);
BEGIN
    FOR i IN 1 .. targets.COUNT LOOP
        BEGIN
            -- 현재 타입 확인 (이미 VARCHAR2면 skip)
            SELECT DATA_TYPE INTO v_type
            FROM USER_TAB_COLUMNS
            WHERE TABLE_NAME = targets(i).tbl AND COLUMN_NAME = targets(i).col;

            IF v_type = 'VARCHAR2' THEN
                DBMS_OUTPUT.PUT_LINE('SKIP ' || targets(i).tbl || '.' || targets(i).col || ' (already VARCHAR2)');
                CONTINUE;
            END IF;

            -- 1) 임시 컬럼 추가
            EXECUTE IMMEDIATE 'ALTER TABLE ' || targets(i).tbl
                || ' ADD ' || targets(i).col || '_TMP VARCHAR2(8)';
            -- 2) 값 복사 (DATE → yyyyMMdd 8자리)
            EXECUTE IMMEDIATE 'UPDATE ' || targets(i).tbl
                || ' SET ' || targets(i).col || '_TMP = TO_CHAR(' || targets(i).col || ', ''YYYYMMDD'')';
            -- 3) 기존 DATE 컬럼 DROP
            EXECUTE IMMEDIATE 'ALTER TABLE ' || targets(i).tbl
                || ' DROP COLUMN ' || targets(i).col;
            -- 4) 임시 컬럼 RENAME
            EXECUTE IMMEDIATE 'ALTER TABLE ' || targets(i).tbl
                || ' RENAME COLUMN ' || targets(i).col || '_TMP TO ' || targets(i).col;
            DBMS_OUTPUT.PUT_LINE('Migrated ' || targets(i).tbl || '.' || targets(i).col);
        EXCEPTION WHEN OTHERS THEN
            DBMS_OUTPUT.PUT_LINE('FAIL ' || targets(i).tbl || '.' || targets(i).col || ': ' || SQLERRM);
        END;
    END LOOP;
END;
/
COMMIT;
EXIT;
