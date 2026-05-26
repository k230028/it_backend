-- ============================================================
-- 정보화실무협의회 공통코드 (TPRMPP_CCODEM) 초기 데이터
-- 대상: ASCT_STS_C(협의회상태 13건), DBR_TC(심의유형 5건),
--        VLR_TC(평가자유형 3건), CKG_ITM_C(점검항목 6건)
--
-- [컬럼 역할]
--   C_ID     = 코드 타입  (예: ASCT_STS_C)
--   CDVA     = 코드 식별자 — 숫자 3자리 (예: 001, 013)
--   CDVA_NM  = 한글 표시명 (예: 작성 중)  ← 프론트 표시 기준
--   C_NM     = 타입 설명   (예: 협의회상태)
--   CDVA_DTL = CDVA_NM 복사
--   CDVA_DES = C_NM 복사
-- MERGE INTO — 멱등성 보장 (재실행 가능)
-- ============================================================

-- ------------------------------------------------------------
-- 0. 구 시맨틱 CDVA 행 정리 (숫자 체계 전환 전 잔여 데이터)
-- ------------------------------------------------------------
DELETE FROM TPRMPP_CCODEM
WHERE C_ID IN ('ASCT_STS', 'ASCT_STS_C')
  AND CDVA = 'RESULT_APPROVAL_PENDING'
  AND STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD');

-- ------------------------------------------------------------
-- 0-2. 구 C_ID 행 정리 (접미사 표준화 전환: _C/_TC)
--     - 기존 데이터 잔존 시 신규 C_ID와 충돌하지 않도록 삭제
-- ------------------------------------------------------------
DELETE FROM TPRMPP_CCODEM WHERE C_ID = 'ASCT_STS';
DELETE FROM TPRMPP_CCODEM WHERE C_ID = 'DBR_TP';
DELETE FROM TPRMPP_CCODEM WHERE C_ID = 'VLR_TP';
DELETE FROM TPRMPP_CCODEM WHERE C_ID = 'CKG_ITM';

-- ------------------------------------------------------------
-- 1. 협의회상태 (ASCT_STS_C) — 13건
--    011: 결재 요청 가능 (전원 확인 완료, IT관리자 결재 요청 전)
--    012: 결과보고 결재 중 (결재 진행 중) — PRD §31 라벨 스왑
--    013: 완료                          — PRD §31 라벨 스왑
-- ------------------------------------------------------------
MERGE INTO TPRMPP_CCODEM t
USING (
    SELECT 'ASCT_STS_C' AS C_ID, '001' AS CDVA, '작성 중'           AS CDVA_NM,  1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '002',          '작성 완료',                     2 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '003',          '결재 대기',                     3 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '004',          '결재 완료',                     4 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '005',          '개최 준비',                     5 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '006',          '일정 확정',                     6 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '007',          '협의회 진행 중',                7 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '008',          '평가의견 작성 중',              8 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '009',          '결과서 작성 중',                9 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '010',          '결과서 검토 중',               10 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '011',          '결재 요청 가능',               11 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '012',          '결과보고 결재 중',             12 FROM DUAL UNION ALL
    SELECT 'ASCT_STS_C',          '013',          '완료',                         13 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.CDVA = s.CDVA AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.CDVA_NM      = s.CDVA_NM,
        t.CDVA_DTL     = s.CDVA_NM,
        t.C_NM         = '협의회상태',
        t.CDVA_DES     = '협의회상태',
        t.C_TP         = 'ASCT_STS_C',
        t.C_TP_DES     = '협의회상태',
        t.C_SQN        = s.C_SQN,
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, CDVA, CDVA_NM, CDVA_DTL, C_NM, CDVA_DES, C_TP, C_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.CDVA, s.CDVA_NM, s.CDVA_NM, '협의회상태', '협의회상태', 'ASCT_STS_C', '협의회상태', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 2. 심의유형 (DBR_TC) — 5건
-- ------------------------------------------------------------
MERGE INTO TPRMPP_CCODEM t
USING (
    SELECT 'DBR_TC' AS C_ID, '001' AS CDVA, '중장기 계획'                  AS CDVA_NM, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'DBR_TC',          '002',          '정보부문기술계획',              2 FROM DUAL UNION ALL
    SELECT 'DBR_TC',          '003',          '정보시스템 사업',               3 FROM DUAL UNION ALL
    SELECT 'DBR_TC',          '004',          '정보보호시스템 사업',           4 FROM DUAL UNION ALL
    SELECT 'DBR_TC',          '005',          '기타',                         5 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.CDVA = s.CDVA AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.CDVA_NM      = s.CDVA_NM,
        t.CDVA_DTL     = s.CDVA_NM,
        t.C_NM         = '심의유형',
        t.CDVA_DES     = '심의유형',
        t.C_TP         = 'DBR_TC',
        t.C_TP_DES     = '심의유형',
        t.C_SQN        = s.C_SQN,
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, CDVA, CDVA_NM, CDVA_DTL, C_NM, CDVA_DES, C_TP, C_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.CDVA, s.CDVA_NM, s.CDVA_NM, '심의유형', '심의유형', 'DBR_TC', '심의유형', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 3. 평가자유형 (VLR_TC) — 3건
-- ------------------------------------------------------------
MERGE INTO TPRMPP_CCODEM t
USING (
    SELECT 'VLR_TC' AS C_ID, '001' AS CDVA, '당연위원' AS CDVA_NM, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'VLR_TC',          '002',          '소집위원',          2 FROM DUAL UNION ALL
    SELECT 'VLR_TC',          '003',          '간사',              3 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.CDVA = s.CDVA AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.CDVA_NM      = s.CDVA_NM,
        t.CDVA_DTL     = s.CDVA_NM,
        t.C_NM         = '평가자유형',
        t.CDVA_DES     = '평가자유형',
        t.C_TP         = 'VLR_TC',
        t.C_TP_DES     = '평가자유형',
        t.C_SQN        = s.C_SQN,
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, CDVA, CDVA_NM, CDVA_DTL, C_NM, CDVA_DES, C_TP, C_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.CDVA, s.CDVA_NM, s.CDVA_NM, '평가자유형', '평가자유형', 'VLR_TC', '평가자유형', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 4. 점검항목 (CKG_ITM_C) — 6건
-- ------------------------------------------------------------
MERGE INTO TPRMPP_CCODEM t
USING (
    SELECT 'CKG_ITM_C' AS C_ID, '001' AS CDVA, '경영전략 부합성'   AS CDVA_NM, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'CKG_ITM_C',          '002',          '재무적 효과',        2 FROM DUAL UNION ALL
    SELECT 'CKG_ITM_C',          '003',          '리스크 영향도',      3 FROM DUAL UNION ALL
    SELECT 'CKG_ITM_C',          '004',          '평판 영향도',        4 FROM DUAL UNION ALL
    SELECT 'CKG_ITM_C',          '005',          '중복 시스템 여부',   5 FROM DUAL UNION ALL
    SELECT 'CKG_ITM_C',          '006',          '기타',               6 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.CDVA = s.CDVA AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.CDVA_NM      = s.CDVA_NM,
        t.CDVA_DTL     = s.CDVA_NM,
        t.C_NM         = '점검항목',
        t.CDVA_DES     = '점검항목',
        t.C_TP         = 'CKG_ITM_C',
        t.C_TP_DES     = '점검항목',
        t.C_SQN        = s.C_SQN,
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, CDVA, CDVA_NM, CDVA_DTL, C_NM, CDVA_DES, C_TP, C_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.CDVA, s.CDVA_NM, s.CDVA_NM, '점검항목', '점검항목', 'CKG_ITM_C', '점검항목', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 5. 저장구분코드 (KPN_TC) — 2건  (Bpovwm.KPN_TC 신규 도메인)
-- ------------------------------------------------------------
MERGE INTO TPRMPP_CCODEM t
USING (
    SELECT 'KPN_TC' AS C_ID, '001' AS CDVA, '임시저장' AS CDVA_NM, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'KPN_TC',          '002',          '저장',              2 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.CDVA = s.CDVA AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.CDVA_NM      = s.CDVA_NM,
        t.CDVA_DTL     = s.CDVA_NM,
        t.C_NM         = '저장구분코드',
        t.CDVA_DES     = '저장구분코드',
        t.C_TP         = 'KPN_TC',
        t.C_TP_DES     = '저장구분코드',
        t.C_SQN        = s.C_SQN,
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, CDVA, CDVA_NM, CDVA_DTL, C_NM, CDVA_DES, C_TP, C_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.CDVA, s.CDVA_NM, s.CDVA_NM, '저장구분코드', '저장구분코드', 'KPN_TC', '저장구분코드', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

COMMIT;
