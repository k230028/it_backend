package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.common.util.NativeRowMapper;
import java.time.LocalDate;

/**
 * 부서 기준 검토 중 최근 문서 3건 native 결과(5컬럼) DTO(#6).
 *
 * <p>컬럼: [0]=DOC_MNG_NO(문서관리번호), [1]=REQ_TTL(제목), [2]=USR_NM(작성자명), [3]=CREATED_AT(YYYY-MM-DD 문자열),
 * [4]=RVW_FSG_TLM_DT(검토완료기한 DATE).
 *
 * @param docMngNo 문서관리번호
 * @param reqTtl 제목
 * @param usrNm 작성자명
 * @param createdAt 등록일(YYYY-MM-DD 문자열)
 * @param fsgTlm 검토완료기한
 */
public record RecentReviewingRow(
        String docMngNo, String reqTtl, String usrNm, String createdAt, LocalDate fsgTlm) {
    /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
    private static final int EXPECTED_COLUMNS = 5;

    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다(#6).
     *
     * @param r 5컬럼 native 결과 행([4]는 DATE/Timestamp 혼용 → {@code toLd})
     * @return 매핑된 DTO
     * @throws IllegalStateException 컬럼 수가 5가 아니면(SQL/팩토리 불일치 조기 검출)
     */
    public static RecentReviewingRow fromRow(Object[] r) {
        if (r == null || r.length != EXPECTED_COLUMNS) {
            throw new IllegalStateException(
                    "컬럼 수 불일치: 기대=" + EXPECTED_COLUMNS + ", 실제=" + (r == null ? "null" : r.length));
        }
        return new RecentReviewingRow(
                NativeRowMapper.toStr(r[0]),
                NativeRowMapper.toStr(r[1]),
                NativeRowMapper.toStr(r[2]),
                NativeRowMapper.toStr(r[3]),
                NativeRowMapper.toLd(r[4]));
    }
}
