-- TAAABB_* 잔존 객체 확인 (테이블/뷰/시퀀스)
SET LINESIZE 200;
SET PAGESIZE 200;
SET FEEDBACK ON;

PROMPT === TAAABB_* TABLES ===
SELECT table_name FROM user_tables WHERE table_name LIKE 'TAAABB%' ORDER BY table_name;

PROMPT === TAAABB_* VIEWS ===
SELECT view_name FROM user_views WHERE view_name LIKE 'TAAABB%' ORDER BY view_name;

PROMPT === TAAABB_* SEQUENCES ===
SELECT sequence_name FROM user_sequences WHERE sequence_name LIKE 'TAAABB%' ORDER BY sequence_name;

PROMPT === TPRMPP_* TABLES (already exists?) ===
SELECT table_name FROM user_tables WHERE table_name LIKE 'TPRMPP%' ORDER BY table_name;

EXIT;
