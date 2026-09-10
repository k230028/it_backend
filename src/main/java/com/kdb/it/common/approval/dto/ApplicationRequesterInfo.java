package com.kdb.it.common.approval.dto;

/**
 * 신청서 응답이 신청자(기안자) 축으로 채우는 값들입니다.
 *
 * <p>표시 정보(성명·직위명·부서명)는 사용자·조직 조회에서, {@code decisionOpinion}은 결재선(TPRMPP_CDECIM)의 기안자 요청 행(순번 0)
 * 결재자의견내용에서 옵니다. 기안자 요청 행을 남기지 않는 신청서(전산예산 v2 이전 방식)는 {@code decisionOpinion}이 null이며, 이는 "의견 없음"을
 * 뜻합니다 — 신청의견(RGPR_DCD_REQ_CONE)으로 대체하지 않습니다.
 *
 * @param usrNm 신청자 성명 (미등록 사번이면 null)
 * @param ptCNm 신청자 직위명 (미등록 사번이면 null)
 * @param bbrNm 신청부서명 (조직 미등재면 null)
 * @param decisionOpinion 기안자 결재의견 (기안자 요청 행이 없으면 null)
 */
public record ApplicationRequesterInfo(
        String usrNm, String ptCNm, String bbrNm, String decisionOpinion) {}
