package com.kdb.it.domain.council.dto;

import com.kdb.it.common.util.NativeRowMapper;
import java.time.LocalDate;

/**
 * 협의회 신청대상 목록 native 쿼리(18컬럼) 결과를 봉인하는 DTO.
 *
 * <p>{@code CouncilRepository.findProjectsForCouncilAll/ByDepartment}의 {@code Object[]}를 {@link
 * #fromRow(Object[])} 단일 팩토리로 매핑한다. SQL SELECT 컬럼 순서가 바뀌면 본 팩토리의 인덱스만 함께 고치면 되며, 캐스팅이 서비스 곳곳에 흩어지지
 * 않는다(§5.5.4).
 *
 * <p>컬럼 순서(0-based): abusMngNo, sno, abusNm, itPtlAsctId, itPtlAsctPrgStsTc, itPtlAsctDbrTc,
 * cnrcDt, cnrcSttTm, applied(NUMBER 0/1), prjYy, prjTp, svnDpm, rqmBgAmt(NULL·미사용), sttDt, endDt,
 * itDpm, abusCone, csfHeldYn.
 *
 * @param abusMngNo 사업관리번호
 * @param sno 사업 일련번호
 * @param abusNm 사업명
 * @param itPtlAsctId IT포탈 협의회 ID
 * @param itPtlAsctPrgStsTc IT포탈 협의회 진행상태구분코드
 * @param itPtlAsctDbrTc IT포탈 협의회 심의구분코드
 * @param cnrcDt 협의회 개최일자
 * @param cnrcSttTm 협의회 개최시작시각
 * @param applied 협의회 신청 여부
 * @param prjYy 프로젝트 연도
 * @param prjTp 프로젝트 유형
 * @param svnDpm 주관부서
 * @param sttDt 사업 시작일자
 * @param endDt 사업 종료일자
 * @param itDpm IT부서
 * @param abusCone 사업내용
 * @param csfHeldYn 자체협의회 개최여부
 */
public record CouncilProjectRow(
        String abusMngNo,
        Integer sno,
        String abusNm,
        String itPtlAsctId,
        String itPtlAsctPrgStsTc,
        String itPtlAsctDbrTc,
        LocalDate cnrcDt,
        String cnrcSttTm,
        boolean applied,
        String prjYy,
        String prjTp,
        String svnDpm,
        LocalDate sttDt,
        LocalDate endDt,
        String itDpm,
        String abusCone,
        String csfHeldYn) {
    /** 컬럼 수 가드: SELECT 절 길이가 바뀌면 즉시 드러나도록 한다. */
    private static final int EXPECTED_COLUMNS = 18;

    /**
     * native {@code Object[]} 1행을 DTO로 매핑한다.
     *
     * @param r 18컬럼 native 결과 행(위 컬럼 순서 고정)
     * @return 매핑된 DTO
     * @throws IllegalStateException 컬럼 수가 18이 아니면(SQL/팩토리 불일치 조기 검출)
     */
    public static CouncilProjectRow fromRow(Object[] r) {
        if (r == null || r.length != EXPECTED_COLUMNS) {
            throw new IllegalStateException(
                    "협의회 신청대상 행 컬럼 수 불일치: 기대="
                            + EXPECTED_COLUMNS
                            + ", 실제="
                            + (r == null ? "null" : r.length));
        }
        // 12번 컬럼은 SELECT의 NULL placeholder(rqmBgAmt 자리)다. 값이 들어오면 SELECT 컬럼 순서가
        // 바뀐 것이므로 조기에 드러내 오매핑을 방지한다.
        if (r[12] != null) {
            throw new IllegalStateException(
                    "12번 컬럼(rqmBgAmt 자리)은 NULL placeholder여야 합니다. SELECT 컬럼 순서 변경 의심: " + r[12]);
        }
        return new CouncilProjectRow(
                NativeRowMapper.toStr(r[0]), // abusMngNo
                r[1] == null ? null : ((Number) r[1]).intValue(), // sno
                NativeRowMapper.toStr(r[2]), // abusNm
                NativeRowMapper.toStr(r[3]), // itPtlAsctId
                NativeRowMapper.toStr(r[4]), // itPtlAsctPrgStsTc
                NativeRowMapper.toStr(r[5]), // itPtlAsctDbrTc
                NativeRowMapper.toLd(r[6]), // cnrcDt (DATE/String yyyyMMdd 혼용)
                NativeRowMapper.toStr(r[7]), // cnrcSttTm (VARCHAR2(1) 가능)
                NativeRowMapper.toInt(r[8], 0) == 1, // applied (NUMBER 0/1)
                NativeRowMapper.toStr(r[9]), // prjYy
                NativeRowMapper.toStr(r[10]), // prjTp
                NativeRowMapper.toStr(r[11]), // svnDpm
                // r[12] = rqmBgAmt(NULL) — 당해예산은 서비스가 품목 배치로 파생 산출하므로 미수용
                NativeRowMapper.toLd(r[13]), // sttDt
                NativeRowMapper.toLd(r[14]), // endDt
                NativeRowMapper.toStr(r[15]), // itDpm
                NativeRowMapper.toStr(r[16]), // abusCone
                NativeRowMapper.toStr(r[17]) // csfHeldYn (VARCHAR2(1))
                );
    }
}
