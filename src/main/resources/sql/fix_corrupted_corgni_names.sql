-- ─────────────────────────────────────────────────────────────────────────
-- 깨진 TAAABB_CORGNI.BBR_NM 교정 (NLS_LANG=KOREAN_KOREA.AL32UTF8로 실행)
-- 원인: 과거 sqlplus 적재 시 NLS_LANG 미설정으로 한글이 mojibake 저장됨.
-- 본 스크립트는 깨진 행만 골라 정상 한글로 덮어씁니다.
-- ─────────────────────────────────────────────────────────────────────────
SET DEFINE OFF;

UPDATE TAAABB_CORGNI SET BBR_NM = '디지털금융부'   WHERE PRLM_OGZ_C_CONE = '182';
UPDATE TAAABB_CORGNI SET BBR_NM = '종합기획부'     WHERE PRLM_OGZ_C_CONE = '120';
UPDATE TAAABB_CORGNI SET BBR_NM = '정보보호기획부' WHERE PRLM_OGZ_C_CONE = '183';

COMMIT;
EXIT;
