package com.kdb.it.common.approval.repository;

import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.util.LabeledCountRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 신청서 마스터(Capplm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 신청서 마스터 테이블(TPRMPP_CAPPLM)에 대한 CRUD 기능을 제공합니다.
 *
 * <p>기본키 타입: {@link String} (apfMngNo: 신청서관리번호)
 *
 * <p>기본 제공 메서드 ({@link JpaRepository} 상속):
 *
 * <ul>
 *   <li>{@code findById(apfMngNo)}: 신청서 관리번호로 단건 조회
 *   <li>{@code findAll()}: 전체 신청서 조회
 *   <li>{@code save(capplm)}: 신청서 저장 (신규 생성 및 수정)
 *   <li>{@code deleteById(apfMngNo)}: 신청서 삭제
 * </ul>
 */
public interface ApplicationRepository extends JpaRepository<Capplm, String> {

    /** 프로젝트·관리비 응답 조립에 필요한 신청서 마스터 최소 필드입니다. */
    interface ApplicationSummaryView {
        String getApfMngNo();

        String getItPtlApfPrgStsC();

        String getDcdReqTtl();

        String getDcdReqUsid();

        LocalDate getDcdReqDtm();

        String getRgprDcdReqCone();
    }

    /**
     * 여러 신청서의 응답 조립용 요약 필드를 조회합니다.
     *
     * @param apfMngNos 신청서 관리번호 목록
     * @return 신청서 요약 view 목록
     */
    List<ApplicationSummaryView> findSummaryViewsByApfMngNoIn(Collection<String> apfMngNos);

    /** 신청서 조회 응답(ApplicationDto.Response, ApfDtlConeResponse) 조립에 필요한 신청서 마스터 8필드입니다. */
    interface ApplicationReadView {
        String getApfMngNo();

        String getItPtlApfPrgStsC();

        String getDcdReqTtl();

        String getDcdReqInf();

        String getDcdReqUsid();

        LocalDate getDcdReqDtm();

        String getRgprDcdReqCone();

        String getDcdReqBbrC();
    }

    /**
     * 전체 신청서를 응답 조립용 read view로 조회합니다.
     *
     * <p>{@code findAll()}과 동일하게 별도 정렬·삭제여부 필터를 적용하지 않습니다.
     *
     * @return 신청서 read view 목록 (정렬 없음)
     */
    List<ApplicationReadView> findAllProjectedBy();

    /**
     * 신청서 목록 API용 경량 read view를 안정된 순서와 상한으로 조회합니다.
     *
     * <p>정렬은 신청서식별번호 내림차순(최신 상신 우선)입니다. 상한(500건)에 걸려 잘리는 쪽이 항상 오래된 건이 되도록 하기 위한 것으로, 오름차순이면 최근 상신되어
     * 지금 결재해야 할 건이 목록에서 사라집니다.
     *
     * @return 신청서 read view 목록 (최신순, 최대 500건)
     */
    List<ApplicationReadView> findTop500ByOrderByApfMngNoDesc();

    /**
     * 특정 결재자가 지금 처리해야 할 신청서 식별번호를 최신순으로 조회합니다.
     *
     * <p>판정 조건은 사이드바 배지({@link #countPendingByEno})와 같습니다. 신청서가 결재중({@code IT_PTL_APF_PRG_STS_C =
     * '1'})이고, 그 결재선에 해당 결재자의 미처리({@code IT_PTL_DCD_STS_C = '1'}) 행이 있는 경우입니다. 동일 결재자가 1차·2차에 모두
     * 지정되면 결재선 행은 2건이지만 결재 행위는 1건이므로 JOIN 대신 EXISTS로 신청서당 한 행만 반환합니다.
     *
     * <p>목록 상한을 두지 않습니다. 본인 결재 대기 건수는 전체 신청서 수와 달리 유한하며, 상한을 두면 배지 건수와 목록 건수가 어긋납니다.
     *
     * @param eno 결재자 사번
     * @return 결재 대기 신청서 식별번호 목록 (최신순)
     */
    @Query(
            value =
                    """
        SELECT a.APF_DCM_NO
        FROM TPRMPP_CAPPLM a
        WHERE a.IT_PTL_APF_PRG_STS_C = '1'
          AND EXISTS (
            SELECT 1
            FROM TPRMPP_CDECIM d
            WHERE d.APF_DCM_NO = a.APF_DCM_NO
              AND d.DCR_ENO = :eno
              AND d.IT_PTL_DCD_STS_C = '1'
          )
        ORDER BY a.APF_DCM_NO DESC
        """,
            nativeQuery = true)
    List<String> findPendingApfMngNosByEno(@Param("eno") String eno);

    /** 여러 신청서를 응답 조립용 read view로 조회합니다. */
    List<ApplicationReadView> findReadViewsByApfMngNoIn(Collection<String> apfMngNos);

    /**
     * 신청관리번호로 단건 신청서를 응답 조립용 read view로 조회합니다.
     *
     * @param apfMngNo 신청서 관리번호
     * @return 해당 신청서 read view (없으면 {@link Optional#empty()})
     */
    Optional<ApplicationReadView> findReadViewByApfMngNo(String apfMngNo);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_CAPPLM_1) 다음 값 조회
     *
     * <p>신청서 생성 시 신청서관리번호(APF_MNG_NO) 채번에 사용합니다. 형식: {@code APF_{연도}{String.format("%08d", seq)}}
     * 예: {@code APF_202600000001}
     *
     * <p>Oracle DB 전용 Native Query입니다.
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_CAPPLM_1)의 다음 값 (Long)
     */
    @Query(value = "SELECT SQ_TPRMPP_CAPPLM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextVal();

    /**
     * 본인에게 온 결재 대기 건수 (APF_STS='결재중' AND 결재선 미처리)
     *
     * <p>신청서 단위 카운트입니다. 동일 신청서에서 같은 결재자가 1차·2차에 모두 지정된 경우 결재선(TPRMPP_CDECIM) 행은 2건이지만, "동일 결재자 연속
     * 등장 시 일괄 승인" 규칙에 따라 결재 행위는 1건이므로 신청서(APF_MNG_NO) 기준으로 DISTINCT 집계합니다.
     */
    @Query(
            value =
                    """
        SELECT COUNT(DISTINCT a.APF_DCM_NO)
        FROM TPRMPP_CAPPLM a
        JOIN TPRMPP_CDECIM d ON a.APF_DCM_NO = d.APF_DCM_NO
        WHERE a.IT_PTL_APF_PRG_STS_C = '1'
          AND d.DCR_ENO = :eno
          AND d.IT_PTL_DCD_STS_C = '1'
        """,
            nativeQuery = true)
    int countPendingByEno(@Param("eno") String eno);

    /** 내가 기안한 진행 중 건수 */
    @Query(
            value =
                    """
        SELECT COUNT(*)
        FROM TPRMPP_CAPPLM a
        WHERE a.IT_PTL_APF_PRG_STS_C = '1'
          AND a.DCD_REQ_USID = :eno
        """,
            nativeQuery = true)
    int countInProgressByEno(@Param("eno") String eno);

    /** 이번달 부서 완료 건수 */
    @Query(
            value =
                    """
        SELECT COUNT(*)
        FROM TPRMPP_CAPPLM a
        JOIN TPRMPP_CUSERI u ON a.DCD_REQ_USID = u.ENO
        WHERE a.IT_PTL_APF_PRG_STS_C = '2'
          AND u.BBR_C = :bbrC
          AND a.DCD_REQ_DTM >= TRUNC(SYSDATE, 'MM')
        """,
            nativeQuery = true)
    int countMonthlyCompletedByBbrC(@Param("bbrC") String bbrC);

    /** 내 반려 건수 */
    @Query(
            value =
                    """
        SELECT COUNT(*)
        FROM TPRMPP_CAPPLM a
        WHERE a.IT_PTL_APF_PRG_STS_C = '3'
          AND a.DCD_REQ_USID = :eno
        """,
            nativeQuery = true)
    int countRejectedByEno(@Param("eno") String eno);

    /** 부서 기준 최근 6개월 월별 결재 처리 건수 반환 컬럼: [0]=MONTH(YYYY-MM), [1]=CNT */
    @Query(
            value =
                    """
        SELECT TO_CHAR(a.DCD_REQ_DTM, 'YYYY-MM') AS MONTH,
               COUNT(*) AS CNT
        FROM TPRMPP_CAPPLM a
        JOIN TPRMPP_CUSERI u ON a.DCD_REQ_USID = u.ENO
        WHERE u.BBR_C = :bbrC
          AND a.DCD_REQ_DTM >= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -5)
        GROUP BY TO_CHAR(a.DCD_REQ_DTM, 'YYYY-MM')
        ORDER BY 1
        """,
            nativeQuery = true)
    List<Object[]> findMonthlyTrendByBbrC(@Param("bbrC") String bbrC);

    /**
     * 본인 결재 대기 최근 3건 반환 컬럼: [0]=APF_DCM_NO, [1]=DCD_REQ_TTL, [2]=USR_NM,
     * [3]=DCD_REQ_DTM(YYYY-MM-DD)
     */
    @Query(
            value =
                    """
        SELECT a.APF_DCM_NO, a.DCD_REQ_TTL, u.USR_NM,
               TO_CHAR(a.DCD_REQ_DTM, 'YYYY-MM-DD') AS RQS_DT_STR
        FROM TPRMPP_CAPPLM a
        JOIN TPRMPP_CUSERI u ON a.DCD_REQ_USID = u.ENO
        JOIN TPRMPP_CDECIM d ON a.APF_DCM_NO = d.APF_DCM_NO
        WHERE a.IT_PTL_APF_PRG_STS_C = '1'
          AND d.DCR_ENO = :eno
          AND d.IT_PTL_DCD_STS_C = '1'
        ORDER BY a.DCD_REQ_DTM DESC
        FETCH FIRST 3 ROWS ONLY
        """,
            nativeQuery = true)
    List<Object[]> findPendingListByEno(@Param("eno") String eno);

    /**
     * 부서 기준 최근 6개월 월별 결재 처리 건수를 DTO로 봉인 반환한다(#6).
     *
     * @param bbrC 소속부서코드
     * @return (월, 건수) DTO 목록
     */
    default List<LabeledCountRow> findMonthlyTrendRowsByBbrC(String bbrC) {
        return findMonthlyTrendByBbrC(bbrC).stream().map(LabeledCountRow::fromRow).toList();
    }

    /**
     * 본인 결재 대기 최근 3건을 DTO로 봉인 반환한다(#6).
     *
     * @param eno 사번
     * @return 결재 대기 DTO 목록
     */
    default List<PendingApprovalRow> findPendingRowsByEno(String eno) {
        return findPendingListByEno(eno).stream().map(PendingApprovalRow::fromRow).toList();
    }
}
