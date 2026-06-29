package com.kdb.it.common.approval.dto;

import com.kdb.it.common.util.NativeRowMapper;

/**
 * 본인 결재 대기 최근 3건 native 결과(4컬럼) DTO(#6).
 *
 * <p>컬럼: [0]=APF_DCM_NO(결재문서번호), [1]=DCD_REQ_TTL(제목), [2]=USR_NM(요청자명),
 * [3]=RQS_DT(YYYY-MM-DD 문자열).</p>
 */
public record PendingApprovalRow(String apfDcmNo, String title, String usrNm, String rqsDt) {
    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(전부 문자열).
     *
     * @param r 4컬럼 native 결과 행
     * @return 매핑된 DTO
     */
    public static PendingApprovalRow fromRow(Object[] r) {
        return new PendingApprovalRow(
                NativeRowMapper.toStr(r[0]),
                NativeRowMapper.toStr(r[1]),
                NativeRowMapper.toStr(r[2]),
                NativeRowMapper.toStr(r[3]));
    }
}
