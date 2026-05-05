-- ============================================================
-- 정보기술부문 계획 관련 DDL
-- ============================================================

-- 계획관리번호 채번 시퀀스 (PLN-{연도}-{4자리})
CREATE SEQUENCE S_PLN_MNG_NO
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- TAAABB_BPLANM: 정보기술부문계획 마스터
CREATE TABLE TAAABB_BPLANM (
    PLN_MNG_NO   VARCHAR2(32)   NOT NULL,   -- 계획관리번호 (PK)
    PLN_TP       VARCHAR2(16),              -- 계획구분 (신규, 조정)
    PLN_YY       VARCHAR2(4),              -- 대상년도 (YYYY)
    PLN_DTL_CONE CLOB,                     -- 계획세부내용 (JSON 스냅샷)
    TTL_BG       NUMBER(15, 2),            -- 총예산
    CPT_BG       NUMBER(15, 2),            -- 자본예산
    MNGC         NUMBER(15, 2),            -- 일반관리비
    DEL_YN       VARCHAR2(1)  DEFAULT 'N', -- 삭제여부
    FST_ENR_DTM  DATE,                     -- 최초생성시간
    FST_ENR_USID VARCHAR2(14),             -- 최초생성자
    GUID         VARCHAR2(38),             -- 일련번호
    GUID_PRG_SNO NUMBER(4, 0),             -- GUID진행일련번호
    LST_CHG_DTM  DATE,                     -- 마지막수정시간
    LST_CHG_USID VARCHAR2(14),             -- 마지막수정자
    CONSTRAINT PK_TAAABB_BPLANM PRIMARY KEY (PLN_MNG_NO)
);

-- TAAABB_BPROJA: 정보화사업 관계 (BPROJM ↔ BPLANM N:N 매핑)
CREATE TABLE TAAABB_BPROJA (
    PRJ_MNG_NO   VARCHAR2(32)   NOT NULL,  -- 프로젝트관리번호 (FK → TAAABB_BPROJM)
    BZ_MNG_NO    VARCHAR2(32)   NOT NULL,  -- 업무관리번호 (FK → TAAABB_BPLANM)
    DEL_YN       VARCHAR2(1)  DEFAULT 'N', -- 삭제여부
    FST_ENR_DTM  DATE,                     -- 최초생성시간
    FST_ENR_USID VARCHAR2(14),             -- 최초생성자
    GUID         VARCHAR2(38),             -- 일련번호
    GUID_PRG_SNO NUMBER(4, 0),             -- GUID진행일련번호
    LST_CHG_DTM  DATE,                     -- 마지막수정시간
    LST_CHG_USID VARCHAR2(14),             -- 마지막수정자
    CONSTRAINT PK_TAAABB_BPROJA PRIMARY KEY (PRJ_MNG_NO, BZ_MNG_NO)
);
