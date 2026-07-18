package com.kdb.it.common.approval.dto;

import com.kdb.it.common.util.NativeRowMapper;

/**
 * 본인 결재 대기 최근 3건 native 결과(4컬럼) DTO(#6).
 *
 * <p>컬럼: [0]=APF_DCM_NO(결재문서번호), [1]=DCD_REQ_TTL(제목), [2]=USR_NM(요청자명),
 * [3]=RQS_DT(YYYY-MM-DD 문자열).</p>
 *
 * @param apfDcmNo 결재문서번호
 * @param title    제목
 * @param usrNm    요청자명
 * @param rqsDt    신청일자(YYYY-MM-DD 문자열)
 */
public record PendingApprovalRow(String apfDcmNo, String title, String usrNm, String rqsDt) {
    /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
    private static final int EXPECTED_COLUMNS = 4;

    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(전부 문자열).
     *
     * @param r 4컬럼 native 결과 행
     * @return 매핑된 DTO
     * @throws IllegalStateException 컬럼 수가 4가 아니면(SQL/팩토리 불일치 조기 검출)
     */
    public static PendingApprovalRow fromRow(Object[] r) {
        if (r == null || r.length != EXPECTED_COLUMNS) {
            throw new IllegalStateException("컬럼 수 불일치: 기대=" + EXPECTED_COLUMNS + ", 실제=" + (r == null ? "null" : r.length));
        }
        return new PendingApprovalRow(
                NativeRowMapper.toStr(r[0]),
                NativeRowMapper.toStr(r[1]),
                NativeRowMapper.toStr(r[2]),
                NativeRowMapper.toStr(r[3]));
    }
}
