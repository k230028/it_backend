package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.common.util.NativeRowMapper;

import java.time.LocalDate;

/**
 * 부서 기준 검토 중 최근 문서 3건 native 결과(5컬럼) DTO(#6).
 *
 * <p>컬럼: [0]=DOC_MNG_NO(문서관리번호), [1]=REQ_TTL(제목), [2]=USR_NM(작성자명),
 * [3]=CREATED_AT(YYYY-MM-DD 문자열), [4]=RVW_FSG_TLM_DT(검토완료기한 DATE).</p>
 */
public record RecentReviewingRow(
        String docMngNo,
        String reqTtl,
        String usrNm,
        String createdAt,
        LocalDate fsgTlm) {
    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(#6).
     *
     * @param r 5컬럼 native 결과 행([4]는 DATE/Timestamp 혼용 → {@code toLd})
     * @return 매핑된 DTO
     */
    public static RecentReviewingRow fromRow(Object[] r) {
        return new RecentReviewingRow(
                NativeRowMapper.toStr(r[0]),
                NativeRowMapper.toStr(r[1]),
                NativeRowMapper.toStr(r[2]),
                NativeRowMapper.toStr(r[3]),
                NativeRowMapper.toLd(r[4]));
    }
}
