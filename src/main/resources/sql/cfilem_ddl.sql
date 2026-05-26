-- ============================================================
-- TPRMPP_CFILEM (공통 첨부파일 관리) DDL
-- ============================================================
-- 테이블 생성
CREATE TABLE TPRMPP_CFILEM (
    FL_MNG_NO       VARCHAR2(32)    NOT NULL,   -- 파일관리번호 (PK, 형식: FL_{8자리 시퀀스})
    ORC_FL_NM       VARCHAR2(255)   NOT NULL,   -- 원본파일명
    SVR_FL_NM       VARCHAR2(100)   NOT NULL,   -- 서버파일명 ({서버ID}_{yyyyMMddHHmmss}_{UUID}.{ext})
    FL_KPN_PTH      VARCHAR2(255)   NOT NULL,   -- 파일저장경로
    FL_DTT          VARCHAR2(100)   NOT NULL,   -- 파일구분 ('이미지' 또는 '첨부파일')
    ORC_PK_VL       VARCHAR2(32),               -- 원본PK값 (연결된 도메인 레코드 기본키)
    ORC_DTT         VARCHAR2(100)   NOT NULL,   -- 원본구분 (예: 요구사항정의서, 정보화사업)
    DEL_YN          VARCHAR2(1)     DEFAULT 'N',-- 삭제여부 ('N'=미삭제, 'Y'=삭제)
    FST_ENR_DTM     DATE,                       -- 최초생성시간 (JPA Auditing 자동 기록)
    FST_ENR_USID    VARCHAR2(14),               -- 최초생성자 사번
    GUID            VARCHAR2(38),               -- UUID (자동 생성)
    GUID_PRG_SNO    NUMBER(4,0),                -- 일련번호2 (기본값 1)
    LST_CHG_DTM     DATE,                       -- 마지막수정시간
    LST_CHG_USID    VARCHAR2(14),               -- 마지막수정자 사번
    CONSTRAINT PK_TPRMPP_CFILEM PRIMARY KEY (FL_MNG_NO)
);

-- 코멘트
COMMENT ON TABLE  TPRMPP_CFILEM              IS '공통 첨부파일 관리';
COMMENT ON COLUMN TPRMPP_CFILEM.FL_MNG_NO    IS '파일관리번호';
COMMENT ON COLUMN TPRMPP_CFILEM.ORC_FL_NM    IS '원본파일명';
COMMENT ON COLUMN TPRMPP_CFILEM.SVR_FL_NM    IS '서버파일명';
COMMENT ON COLUMN TPRMPP_CFILEM.FL_KPN_PTH   IS '파일저장경로';
COMMENT ON COLUMN TPRMPP_CFILEM.FL_DTT       IS '파일구분';
COMMENT ON COLUMN TPRMPP_CFILEM.ORC_PK_VL    IS '원본PK값';
COMMENT ON COLUMN TPRMPP_CFILEM.ORC_DTT      IS '원본구분';
COMMENT ON COLUMN TPRMPP_CFILEM.DEL_YN       IS '삭제여부';
COMMENT ON COLUMN TPRMPP_CFILEM.FST_ENR_DTM  IS '최초생성시간';
COMMENT ON COLUMN TPRMPP_CFILEM.FST_ENR_USID IS '최초생성자';
COMMENT ON COLUMN TPRMPP_CFILEM.GUID         IS '일련번호';
COMMENT ON COLUMN TPRMPP_CFILEM.GUID_PRG_SNO IS '일련번호2';
COMMENT ON COLUMN TPRMPP_CFILEM.LST_CHG_DTM  IS '마지막수정시간';
COMMENT ON COLUMN TPRMPP_CFILEM.LST_CHG_USID IS '마지막수정자';

-- ============================================================
-- 시퀀스 생성 (파일관리번호 채번용)
-- 형식: FL_{8자리} → 최대 FL_99999999 (약 1억 건)
-- ============================================================
CREATE SEQUENCE S_FL
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- 조회 성능 인덱스
-- 원본구분 + 원본PK값 기준 조회 빈도가 높아 복합 인덱스 생성
CREATE INDEX IDX_CFILEM_ORC ON TPRMPP_CFILEM (ORC_DTT, ORC_PK_VL, DEL_YN);
