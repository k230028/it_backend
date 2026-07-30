package com.kdb.it.common.board.repository;

/**
 * 게시판 메타 목록 응답 전용 프로젝션 레코드
 *
 * <p>{@code BoardMetaDto.Response.from}이 실제로 소비하는 10개 필드만 담는다({@code Cblbmm} 엔티티가 상속하는 {@code
 * BaseEntity} 필드는 응답에 사용되지 않으므로 제외). QueryDSL {@code Projections.constructor}는 위치 기반이므로 select 인자
 * 순서가 이 레코드의 컴포넌트 순서와 정확히 일치해야 한다.
 *
 * @param blbMngNo 게시판관리번호 (컬럼 BLB_ID)
 * @param blbNm 게시판명 (컬럼 BLB_NM)
 * @param itPtlBlbTc 게시판구분코드 (컬럼 IT_PTL_BLB_TC)
 * @param repUseYn 답변사용여부 (컬럼 REP_FNC_USE_YN)
 * @param cmmtUseYn 댓글사용여부 (컬럼 CMMT_USE_YN)
 * @param flEsnYn 첨부필수여부 (컬럼 APG_FL_USE_YN)
 * @param hedTagUseYn 머리말태그사용여부 (컬럼 HED_TAG_USE_YN)
 * @param sreSqnNo 화면순서번호 (컬럼 SRE_SQN_SNO)
 * @param useYn 사용여부 (컬럼 USE_YN)
 * @param rmk 비고 (컬럼 RMK)
 */
public record BoardMetaListRow(
        String blbMngNo,
        String blbNm,
        String itPtlBlbTc,
        String repUseYn,
        String cmmtUseYn,
        String flEsnYn,
        String hedTagUseYn,
        Integer sreSqnNo,
        String useYn,
        String rmk) {}
