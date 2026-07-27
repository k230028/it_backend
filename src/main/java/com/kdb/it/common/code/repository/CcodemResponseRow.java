package com.kdb.it.common.code.repository;

import java.time.LocalDateTime;

/**
 * 공통코드 REST 응답 전용 프로젝션 레코드
 *
 * <p>{@code CodeDto.Response.fromEntity}가 실제로 소비하는 18개 필드만 담는다({@code Ccodem} 엔티티의 {@code guid},
 * {@code guidPrgSno}는 제외). QueryDSL {@code Projections.constructor}는 위치 기반이므로 select 인자 순서가 이 레코드의
 * 컴포넌트 순서와 정확히 일치해야 한다.
 *
 * @param cId 코드ID
 * @param cdva 코드값
 * @param cdvaNm 코드값명
 * @param cNm 공통코드명 (구 CDVA)
 * @param cdvaDes 공통코드값약어명 (구 CDVA_DES)
 * @param cdvaDtl 공통코드값적요 (구 C_NM)
 * @param cdvaDtlC 공통코드값명 (구 CDVA_DTL_C)
 * @param cTp 공통코드인스턴스명 (구 C_TP)
 * @param cTpDes 공통코드인스턴스내용 (구 C_TP_DES)
 * @param hrkC 상위코드값ID
 * @param cSqn 코드순서일련번호
 * @param sttDt 시작일자 (YYYYMMDD)
 * @param endDt 종료일자 (YYYYMMDD)
 * @param delYn 삭제여부
 * @param fstEnrDtm 최초등록일시
 * @param fstEnrUsid 최초등록사용자ID
 * @param lstChgDtm 최종변경일시
 * @param lstChgUsid 최종변경사용자ID
 */
public record CcodemResponseRow(
        String cId,
        String cdva,
        String cdvaNm,
        String cNm,
        String cdvaDes,
        String cdvaDtl,
        String cdvaDtlC,
        String cTp,
        String cTpDes,
        String hrkC,
        Integer cSqn,
        String sttDt,
        String endDt,
        String delYn,
        LocalDateTime fstEnrDtm,
        String fstEnrUsid,
        LocalDateTime lstChgDtm,
        String lstChgUsid) {}
