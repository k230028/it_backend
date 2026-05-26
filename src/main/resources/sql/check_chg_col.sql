SET LINESIZE 200;
SET PAGESIZE 200;
SELECT table_name, column_name, data_type, data_length, nullable
FROM user_tab_columns
WHERE table_name LIKE 'TPRMPP_%L'
  AND (column_name = 'CHG_TC' OR column_name = 'CHG_TP')
ORDER BY table_name, column_name;
EXIT;
