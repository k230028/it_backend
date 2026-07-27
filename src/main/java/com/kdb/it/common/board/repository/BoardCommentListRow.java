package com.kdb.it.common.board.repository;

import java.time.LocalDateTime;

/**
 * 게시판 댓글 목록 응답 전용 프로젝션 레코드
 *
 * <p>{@code BoardCommentDto.Response.from}이 실제로 소비하는 11개 필드만 담는다({@code Ccmmtm} 엔티티 고유 필드 7개 +
 * {@code BaseEntity} 상속 필드 중 응답이 사용하는 delYn·fstEnrUsid·fstEnrDtm·lstChgDtm 4개). QueryDSL {@code
 * Projections.constructor}는 위치 기반이므로 select 인자 순서가 이 레코드의 컴포넌트 순서와 정확히 일치해야 한다.
 *
 * @param cmmtMngNo 댓글관리번호 (컬럼 CMMT_SNO)
 * @param nacMngNo 게시물관리번호 (컬럼 NAC_NO)
 * @param cmmtCone 댓글내용 (컬럼 CMMT_CONE)
 * @param cmmtGrpNo 댓글그룹번호 (컬럼 CMMT_TGT_SNO)
 * @param cmmtGrpSqn 댓글그룹순서 (컬럼 CMMT_SQN_SNO)
 * @param cmmtGrpLev 댓글그룹레벨 (컬럼 CMMT_DEP_NBR)
 * @param hrkCmmtMngNo 상위댓글관리번호 (컬럼 HRK_CMMT_SNO)
 * @param delYn 삭제여부 (컬럼 DEL_YN, BaseEntity 상속)
 * @param fstEnrUsid 최초등록사용자ID (컬럼 FST_ENR_USID, BaseEntity 상속)
 * @param fstEnrDtm 최초등록일시 (컬럼 FST_ENR_DTM, BaseEntity 상속)
 * @param lstChgDtm 최종변경일시 (컬럼 LST_CHG_DTM, BaseEntity 상속)
 */
public record BoardCommentListRow(
        Long cmmtMngNo,
        String nacMngNo,
        String cmmtCone,
        Long cmmtGrpNo,
        Integer cmmtGrpSqn,
        Integer cmmtGrpLev,
        Long hrkCmmtMngNo,
        String delYn,
        String fstEnrUsid,
        LocalDateTime fstEnrDtm,
        LocalDateTime lstChgDtm) {}
