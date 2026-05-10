-- ============================================================
-- 정보화실무협의회 공통코드 (TAAABB_CCODEM) 초기 데이터
-- 대상: ASCT_STS(협의회상태 13건), DBR_TP(심의유형 5건),
--        VLR_TP(평가자유형 3건), CKG_ITM(점검항목 6건)
-- MERGE INTO — 멱등성 보장 (재실행 가능)
-- ============================================================

-- ------------------------------------------------------------
-- 1. 협의회상태 (ASCT_STS) — 13건
-- ------------------------------------------------------------
MERGE INTO TAAABB_CCODEM t
USING (
    SELECT 'ASCT-STS-001' AS C_ID, '작성 중'           AS C_NM, 'DRAFT'                    AS CDVA, '작성 중'           AS C_DES,  1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'ASCT-STS-002',          '작성 완료',          'SUBMITTED',                        '작성 완료',           2 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-003',          '결재 대기',          'APPROVAL_PENDING',                 '결재 대기',           3 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-004',          '결재 완료',          'APPROVED',                         '결재 완료',           4 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-005',          '개최 준비',          'PREPARING',                        '개최 준비',           5 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-006',          '일정 확정',          'SCHEDULED',                        '일정 확정',           6 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-007',          '협의회 진행 중',     'IN_PROGRESS',                      '협의회 진행 중',      7 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-008',          '평가의견 작성 중',   'EVALUATING',                       '평가의견 작성 중',    8 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-009',          '결과서 작성 중',     'RESULT_WRITING',                   '결과서 작성 중',      9 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-010',          '결과서 검토 중',     'RESULT_REVIEW',                    '결과서 검토 중',     10 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-011',          '결과보고 결재 중',   'FINAL_APPROVAL',                   '결과보고 결재 중',   11 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-012',          '완료',               'COMPLETED',                        '완료',               12 FROM DUAL UNION ALL
    SELECT 'ASCT-STS-013',          '결과보고 결재 중',   'RESULT_APPROVAL_PENDING',          NULL,                 13 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.C_NM         = s.C_NM,
        t.CDVA         = s.CDVA,
        t.C_DES        = s.C_DES,
        t.C_SQN        = s.C_SQN,
        t.CTT_TP       = 'ASCT_STS',
        t.CTT_TP_DES   = '협의회상태',
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, C_NM, CDVA, C_DES, CTT_TP, CTT_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.C_NM, s.CDVA, s.C_DES, 'ASCT_STS', '협의회상태', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 2. 심의유형 (DBR_TP) — 5건
-- ------------------------------------------------------------
MERGE INTO TAAABB_CCODEM t
USING (
    SELECT 'DBR-TP-001' AS C_ID, '중기계획 수립'               AS C_NM, 'MID_PLAN'  AS CDVA, '중기계획 수립'               AS C_DES, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'DBR-TP-002',          'IT계획 수립',                  'IT_PLAN',   'IT계획 수립',                  2 FROM DUAL UNION ALL
    SELECT 'DBR-TP-003',          '정보시스템 사업 타당성 검토',  'INFO_SYS',  '정보시스템 사업 타당성 검토',  3 FROM DUAL UNION ALL
    SELECT 'DBR-TP-004',          '정보보안 심의',                'INFO_SEC',  '정보보안 심의',                4 FROM DUAL UNION ALL
    SELECT 'DBR-TP-005',          '기타',                         'ETC',       '기타',                         5 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.C_NM         = s.C_NM,
        t.CDVA         = s.CDVA,
        t.C_DES        = s.C_DES,
        t.C_SQN        = s.C_SQN,
        t.CTT_TP       = 'DBR_TP',
        t.CTT_TP_DES   = '심의유형',
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, C_NM, CDVA, C_DES, CTT_TP, CTT_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.C_NM, s.CDVA, s.C_DES, 'DBR_TP', '심의유형', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 3. 평가자유형 (VLR_TP) — 3건
-- ------------------------------------------------------------
MERGE INTO TAAABB_CCODEM t
USING (
    SELECT 'VLR-TP-001' AS C_ID, '당연위원' AS C_NM, 'MAND' AS CDVA, '당연위원' AS C_DES, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'VLR-TP-002',          '소집위원',          'CALL',          '소집위원',          2 FROM DUAL UNION ALL
    SELECT 'VLR-TP-003',          '간사',              'SECR',          '간사',              3 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.C_NM         = s.C_NM,
        t.CDVA         = s.CDVA,
        t.C_DES        = s.C_DES,
        t.C_SQN        = s.C_SQN,
        t.CTT_TP       = 'VLR_TP',
        t.CTT_TP_DES   = '평가자유형',
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, C_NM, CDVA, C_DES, CTT_TP, CTT_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.C_NM, s.CDVA, s.C_DES, 'VLR_TP', '평가자유형', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

-- ------------------------------------------------------------
-- 4. 점검항목 (CKG_ITM) — 6건
-- ------------------------------------------------------------
MERGE INTO TAAABB_CCODEM t
USING (
    SELECT 'CKG-ITM-001' AS C_ID, '경영전략 부합성'   AS C_NM, 'MGMT_STR' AS CDVA, '경영전략 부합성'   AS C_DES, 1 AS C_SQN FROM DUAL UNION ALL
    SELECT 'CKG-ITM-002',          '재무적 효과',        'FIN_EFC',          '재무적 효과',        2 FROM DUAL UNION ALL
    SELECT 'CKG-ITM-003',          '리스크 영향도',      'RISK_IMP',         '리스크 영향도',      3 FROM DUAL UNION ALL
    SELECT 'CKG-ITM-004',          '평판 영향도',        'REP_IMP',          '평판 영향도',        4 FROM DUAL UNION ALL
    SELECT 'CKG-ITM-005',          '중복 시스템 여부',   'DUP_SYS',          '중복 시스템 여부',   5 FROM DUAL UNION ALL
    SELECT 'CKG-ITM-006',          '기타',               'ETC',              '기타',               6 FROM DUAL
) s ON (t.C_ID = s.C_ID AND t.STT_DT = TO_DATE('2026-04-12', 'YYYY-MM-DD'))
WHEN MATCHED THEN
    UPDATE SET
        t.C_NM         = s.C_NM,
        t.CDVA         = s.CDVA,
        t.C_DES        = s.C_DES,
        t.C_SQN        = s.C_SQN,
        t.CTT_TP       = 'CKG_ITM',
        t.CTT_TP_DES   = '점검항목',
        t.LST_CHG_DTM  = SYSDATE,
        t.LST_CHG_USID = 'SYSTEM'
WHEN NOT MATCHED THEN
    INSERT (C_ID, C_NM, CDVA, C_DES, CTT_TP, CTT_TP_DES, C_SQN,
            STT_DT, END_DT,
            DEL_YN, FST_ENR_DTM, FST_ENR_USID, LST_CHG_DTM, LST_CHG_USID,
            GUID, GUID_PRG_SNO)
    VALUES (s.C_ID, s.C_NM, s.CDVA, s.C_DES, 'CKG_ITM', '점검항목', s.C_SQN,
            TO_DATE('2026-04-12', 'YYYY-MM-DD'), TO_DATE('9999-12-31', 'YYYY-MM-DD'),
            'N', SYSDATE, 'SYSTEM', SYSDATE, 'SYSTEM',
            RAWTOHEX(SYS_GUID()), 1);

COMMIT;
