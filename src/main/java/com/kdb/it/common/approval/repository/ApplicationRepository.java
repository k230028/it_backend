package com.kdb.it.common.approval.repository;

import com.kdb.it.common.approval.entity.Capplm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 신청서 마스터(Capplm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 신청서 마스터 테이블(TAAABB_CAPPLM)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link String} (apfMngNo: 신청서관리번호)</p>
 *
 * <p>기본 제공 메서드 ({@link JpaRepository} 상속):</p>
 * <ul>
 *   <li>{@code findById(apfMngNo)}: 신청서 관리번호로 단건 조회</li>
 *   <li>{@code findAll()}: 전체 신청서 조회</li>
 *   <li>{@code save(capplm)}: 신청서 저장 (신규 생성 및 수정)</li>
 *   <li>{@code deleteById(apfMngNo)}: 신청서 삭제</li>
 * </ul>
 */
public interface ApplicationRepository extends JpaRepository<Capplm, String> {

    /**
     * Oracle 시퀀스(S_APF) 다음 값 조회
     *
     * <p>신청서 생성 시 신청서관리번호(APF_MNG_NO) 채번에 사용합니다.
     * 형식: {@code APF_{연도}{String.format("%08d", seq)}}
     * 예: {@code APF_202600000001}</p>
     *
     * <p>Oracle DB 전용 Native Query입니다.</p>
     *
     * @return Oracle 시퀀스(S_APF)의 다음 값 (Long)
     */
    @Query(value = "SELECT S_APF.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextVal();

    /** 본인에게 온 결재 대기 건수 (APF_STS='결재중' AND 결재선 미처리) */
    @Query(value = """
        SELECT COUNT(*)
        FROM TAAABB_CAPPLM a
        JOIN TAAABB_CDECIM d ON a.APF_MNG_NO = d.DCD_MNG_NO
        WHERE a.APF_STS = '결재중'
          AND d.DCD_ENO = :eno
          AND d.DCD_DT IS NULL
        """, nativeQuery = true)
    int countPendingByEno(@Param("eno") String eno);

    /** 내가 기안한 진행 중 건수 */
    @Query(value = """
        SELECT COUNT(*)
        FROM TAAABB_CAPPLM a
        WHERE a.APF_STS = '결재중'
          AND a.RQS_ENO = :eno
        """, nativeQuery = true)
    int countInProgressByEno(@Param("eno") String eno);

    /** 이번달 부서 완료 건수 */
    @Query(value = """
        SELECT COUNT(*)
        FROM TAAABB_CAPPLM a
        JOIN TAAABB_CUSERI u ON a.RQS_ENO = u.ENO
        WHERE a.APF_STS = '결재완료'
          AND u.BBR_C = :bbrC
          AND a.RQS_DT >= TRUNC(SYSDATE, 'MM')
        """, nativeQuery = true)
    int countMonthlyCompletedByBbrC(@Param("bbrC") String bbrC);

    /** 내 반려 건수 */
    @Query(value = """
        SELECT COUNT(*)
        FROM TAAABB_CAPPLM a
        WHERE a.APF_STS = '반려'
          AND a.RQS_ENO = :eno
        """, nativeQuery = true)
    int countRejectedByEno(@Param("eno") String eno);

    /**
     * 부서 기준 최근 6개월 월별 결재 처리 건수
     * 반환 컬럼: [0]=MONTH(YYYY-MM), [1]=CNT
     */
    @Query(value = """
        SELECT TO_CHAR(a.RQS_DT, 'YYYY-MM') AS MONTH,
               COUNT(*) AS CNT
        FROM TAAABB_CAPPLM a
        JOIN TAAABB_CUSERI u ON a.RQS_ENO = u.ENO
        WHERE u.BBR_C = :bbrC
          AND a.RQS_DT >= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -5)
        GROUP BY TO_CHAR(a.RQS_DT, 'YYYY-MM')
        ORDER BY 1
        """, nativeQuery = true)
    List<Object[]> findMonthlyTrendByBbrC(@Param("bbrC") String bbrC);

    /**
     * 본인 결재 대기 최근 3건
     * 반환 컬럼: [0]=APF_MNG_NO, [1]=APF_NM, [2]=USR_NM, [3]=RQS_DT(YYYY-MM-DD)
     */
    @Query(value = """
        SELECT a.APF_MNG_NO, a.APF_NM, u.USR_NM,
               TO_CHAR(a.RQS_DT, 'YYYY-MM-DD') AS RQS_DT_STR
        FROM TAAABB_CAPPLM a
        JOIN TAAABB_CUSERI u ON a.RQS_ENO = u.ENO
        JOIN TAAABB_CDECIM d ON a.APF_MNG_NO = d.DCD_MNG_NO
        WHERE a.APF_STS = '결재중'
          AND d.DCD_ENO = :eno
          AND d.DCD_DT IS NULL
        ORDER BY a.RQS_DT DESC
        FETCH FIRST 3 ROWS ONLY
        """, nativeQuery = true)
    List<Object[]> findPendingListByEno(@Param("eno") String eno);
}
