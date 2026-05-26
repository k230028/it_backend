-- ============================================================
-- BCMMTM DEL_YN NULL 복구 (PRD §15)
-- 원인: CommitteeService.saveCommittee 이전 구현이 JpaRepository.save() merge 분기로
--       detached entity의 delYn=null을 영속 객체에 복사 → DB에 DEL_YN=null로 저장됨
-- 조치: null 행을 'N'으로 일괄 복구
-- ============================================================

UPDATE TAAABB_BCMMTM SET DEL_YN = 'N' WHERE DEL_YN IS NULL;

COMMIT;

SELECT COUNT(*) AS NULL_REMAINING FROM TAAABB_BCMMTM WHERE DEL_YN IS NULL;

EXIT;
