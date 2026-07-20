package com.kdb.it.domain.budget.document.repository;

import com.kdb.it.common.util.LabeledCountRow;
import com.kdb.it.domain.budget.document.dto.RecentReviewingRow;
import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.entity.BrdocmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 요구사항 정의서(Brdocm) 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 요구사항 정의서 테이블(TPRMPP_BRDOCM)의 기본 CRUD 기능을 제공합니다.
 * </p>
 *
 * <p>
 * 테이블 PK는 (DOC_MNG_NO, DOC_VRS) 복합키이며, 복합키 클래스 {@link BrdocmId} 를 사용합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴 적용: 조회 시 {@code delYn='N'} 조건을 사용합니다.
 * </p>
 */
public interface ServiceRequestDocRepository extends JpaRepository<Brdocm, BrdocmId> {

    /**
     * 문서관리번호로 최신 버전 단건 조회 (일반 조회/수정/삭제의 기본 진입점)
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 최신 버전의 요구사항 정의서
     */
    Optional<Brdocm> findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(String docMngNo, String delYn);

    /**
     * 문서관리번호 + 특정 버전 단건 조회
     *
     * @param docMngNo 문서관리번호
     * @param docVrsSno 문서 버전
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 요구사항 정의서
     */
    Optional<Brdocm> findByDocMngNoAndDocVrsSnoAndDelYn(String docMngNo, BigDecimal docVrsSno, String delYn);

    /**
     * 문서관리번호로 전체 버전 히스토리 조회 (버전 내림차순)
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 전체 버전 목록 (버전 내림차순)
     */
    List<Brdocm> findAllByDocMngNoAndDelYnOrderByDocVrsSnoDesc(String docMngNo, String delYn);

    /**
     * 문서관리번호의 전체 버전 조회 (소프트 삭제 일괄 처리용, 순서 무관)
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 전체 버전 목록
     */
    List<Brdocm> findAllByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * 삭제여부로 전체 목록 조회
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 요구사항 정의서 목록
     */
    List<Brdocm> findAllByDelYn(String delYn);

    /**
     * 문서관리번호와 삭제여부로 존재 여부 확인
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * 문서관리번호 단위 최신 버전 목록 조회 (목록 화면용)
     *
     * <p>
     * 각 DOC_MNG_NO 그룹 내에서 가장 큰 DOC_VRS 행만 반환합니다.
     * 삭제되지 않은(DEL_YN='N') 레코드만 대상으로 하며,
     * 최초 등록일시(FST_ENR_DTM) 내림차순으로 정렬합니다.
     * </p>
     *
     * @return 문서별 최신 버전 목록
     */
    @Query(value = """
        SELECT * FROM TPRMPP_BRDOCM d
        WHERE d.DEL_YN = 'N'
          AND d.DOC_VRS_SNO = (
              SELECT MAX(d2.DOC_VRS_SNO) FROM TPRMPP_BRDOCM d2
              WHERE d2.DOC_MNG_NO = d.DOC_MNG_NO AND d2.DEL_YN = 'N'
          )
        ORDER BY d.FST_ENR_DTM DESC
        """, nativeQuery = true)
    List<Brdocm> findLatestVersionsAll();

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BRDOCM_1) 다음 값 조회
     *
     * <p>
     * 신규 요구사항 정의서 생성 시 문서관리번호 채번에 사용합니다.
     * 형식: {@code DOC-{연도}-{4자리 시퀀스}} (예: {@code DOC-2026-0001})
     * </p>
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_BRDOCM_1)의 다음 값
     */
    @Query(value = "SELECT SQ_TPRMPP_BRDOCM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /** 부서 기준 전체 미삭제 문서 수 (DOC_MNG_NO 기준 distinct) */
    @Query(value = """
        SELECT COUNT(DISTINCT b.DOC_MNG_NO)
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
        """, nativeQuery = true)
    int countTotalByBbrC(@Param("bbrC") String bbrC);

    /** 부서 기준 미해결 검토의견이 존재하는 문서 수 (검토 진행 중) */
    @Query(value = """
        SELECT COUNT(DISTINCT b.DOC_MNG_NO)
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        JOIN TPRMPP_BRIVGM r ON b.DOC_MNG_NO = r.DOC_MNG_NO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
          AND r.FSG_YN = 'N'
          AND r.DEL_YN = 'N'
        """, nativeQuery = true)
    int countReviewingByBbrC(@Param("bbrC") String bbrC);

    /** 부서 기준 협의 완료 문서 수 (검토의견 존재 AND 모두 해결) */
    @Query(value = """
        SELECT COUNT(DISTINCT b.DOC_MNG_NO)
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
          AND EXISTS (
              SELECT 1 FROM TPRMPP_BRIVGM r
              WHERE r.DOC_MNG_NO = b.DOC_MNG_NO AND r.DEL_YN = 'N'
          )
          AND NOT EXISTS (
              SELECT 1 FROM TPRMPP_BRIVGM r
              WHERE r.DOC_MNG_NO = b.DOC_MNG_NO AND r.DEL_YN = 'N' AND r.FSG_YN = 'N'
          )
        """, nativeQuery = true)
    int countCompletedByBbrC(@Param("bbrC") String bbrC);

    /** 부서 기준 완료기한 초과 문서 수 */
    @Query(value = """
        SELECT COUNT(DISTINCT b.DOC_MNG_NO)
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
          AND b.RVW_FSG_TLM_DT < TRUNC(SYSDATE)
        """, nativeQuery = true)
    int countOverdueByBbrC(@Param("bbrC") String bbrC);

    /**
     * 부서 기준 최근 6개월 월별 문서 등록 건수
     * 반환 컬럼: [0]=MONTH(YYYY-MM), [1]=CNT
     */
    @Query(value = """
        SELECT TO_CHAR(b.FST_ENR_DTM, 'YYYY-MM') AS MONTH,
               COUNT(DISTINCT b.DOC_MNG_NO) AS CNT
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
          AND b.FST_ENR_DTM >= ADD_MONTHS(TRUNC(SYSDATE, 'MM'), -5)
        GROUP BY TO_CHAR(b.FST_ENR_DTM, 'YYYY-MM')
        ORDER BY 1
        """, nativeQuery = true)
    java.util.List<Object[]> findMonthlyTrendByBbrC(@Param("bbrC") String bbrC);

    /**
     * 부서 기준 검토 중인 최근 문서 3건
     * 반환 컬럼: [0]=DOC_MNG_NO, [1]=REQ_NM, [2]=USR_NM, [3]=CREATED_AT(YYYY-MM-DD), [4]=FSG_TLM(DATE)
     */
    @Query(value = """
        SELECT DISTINCT b.DOC_MNG_NO, b.REQ_TTL, u.USR_NM,
               TO_CHAR(b.FST_ENR_DTM, 'YYYY-MM-DD') AS CREATED_AT,
               b.RVW_FSG_TLM_DT
        FROM TPRMPP_BRDOCM b
        JOIN TPRMPP_CUSERI u ON b.FST_ENR_USID = u.ENO
        JOIN TPRMPP_BRIVGM r ON b.DOC_MNG_NO = r.DOC_MNG_NO
        WHERE b.DEL_YN = 'N'
          AND u.BBR_C = :bbrC
          AND r.FSG_YN = 'N'
          AND r.DEL_YN = 'N'
        ORDER BY b.FST_ENR_DTM DESC
        FETCH FIRST 3 ROWS ONLY
        """, nativeQuery = true)
    java.util.List<Object[]> findRecentReviewingByBbrC(@Param("bbrC") String bbrC);

    /**
     * 부서 기준 최근 6개월 월별 문서 등록 건수를 DTO로 봉인 반환한다(#6).
     *
     * @param bbrC 소속부서코드
     * @return (월, 건수) DTO 목록
     */
    default List<LabeledCountRow> findMonthlyTrendRowsByBbrC(String bbrC) {
        return findMonthlyTrendByBbrC(bbrC).stream()
                .map(LabeledCountRow::fromRow)
                .toList();
    }

    /**
     * 부서 기준 검토 중인 최근 문서 3건을 DTO로 봉인 반환한다(#6).
     *
     * @param bbrC 소속부서코드
     * @return 검토 중 문서 DTO 목록
     */
    default List<RecentReviewingRow> findRecentReviewingRowsByBbrC(String bbrC) {
        return findRecentReviewingByBbrC(bbrC).stream()
                .map(RecentReviewingRow::fromRow)
                .toList();
    }
}
