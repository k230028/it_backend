package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.BitemmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 정보화사업 품목(Bitemm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 기본 CRUD 기능을 제공하며, 품목 도메인에 특화된 조회 메서드를 추가로 정의합니다.
 *
 * <p>복합키 타입: {@link BitemmId} (gclMngNo + gclSno)
 *
 * <p>프로젝트와의 연관: {@code prjMngNo} + {@code prjSno}로 특정 프로젝트의 품목을 조회합니다.
 */
public interface ProjectItemRepository extends JpaRepository<Bitemm, BitemmId> {

    /** 이전 개정본에 속한 현재 품목을 비최종 상태로 전환합니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            """
            UPDATE Bitemm i
               SET i.lstYn = 'N'
             WHERE i.abusMngNo = :abusMngNo
               AND i.fntTbCrySno <> :sno
               AND i.delYn = 'N'
               AND i.lstYn = 'Y'
            """)
    int clearCurrentVersionItems(@Param("abusMngNo") String abusMngNo, @Param("sno") Integer sno);

    /** 승인된 개정본에 속한 품목을 현재 품목으로 전환합니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            """
            UPDATE Bitemm i
               SET i.lstYn = 'Y'
             WHERE i.abusMngNo = :abusMngNo
               AND i.fntTbCrySno = :sno
               AND i.delYn = 'N'
            """)
    int markVersionItemsCurrent(@Param("abusMngNo") String abusMngNo, @Param("sno") Integer sno);

    /** 사업별 예산 합산에 필요한 품목 필드만 읽는 프로젝션입니다. */
    interface ProjectItemBudgetView {
        String getGclMngNo();

        String getAbusMngNo();

        String getIoeC();

        java.math.BigDecimal getAmt();

        java.math.BigDecimal getMplAmt();

        String getCurC();

        java.math.BigDecimal getXcr();
    }

    /**
     * 프로젝트 관리번호와 순번으로 품목 목록 조회 (삭제 여부 무관)
     *
     * <p>특정 프로젝트에 속한 모든 품목을 조회합니다. 프로젝트 삭제 시 관련 품목을 일괄 Soft Delete 처리하는 데 사용됩니다.
     *
     * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
     * @param prjSno 프로젝트 순번
     * @return 해당 프로젝트의 모든 품목 목록 (삭제된 항목 포함)
     */
    List<Bitemm> findByAbusMngNoAndFntTbCrySno(String prjMngNo, Integer prjSno);

    /** 부모 사업 순번에 속한 미삭제 품목을 품목 최종여부와 무관하게 조회합니다. */
    List<Bitemm> findAllByAbusMngNoAndFntTbCrySnoAndDelYn(
            String prjMngNo, Integer prjSno, String delYn);

    /**
     * 프로젝트 관리번호, 순번, 삭제여부로 품목 목록 조회
     *
     * <p>특정 프로젝트의 유효한(미삭제) 품목만 조회합니다. 프로젝트 상세 조회 및 품목 동기화(CUD) 처리에 사용됩니다.
     *
     * @param prjMngNo 프로젝트 관리번호
     * @param prjSno 프로젝트 순번
     * @param delYn 삭제 여부 ('N'=미삭제, 'Y'=삭제)
     * @return 조건에 맞는 품목 엔티티 목록
     */
    default List<Bitemm> findByAbusMngNoAndFntTbCrySnoAndDelYn(
            String prjMngNo, Integer prjSno, String delYn) {
        return findAllByAbusMngNoAndFntTbCrySnoAndDelYn(prjMngNo, prjSno, delYn);
    }

    List<Bitemm> findByAbusMngNoAndFntTbCrySnoAndDelYnAndLstYn(
            String prjMngNo, Integer prjSno, String delYn, String lstYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BITEMM_1) 다음 값 조회
     *
     * <p>신규 품목 생성 시 품목관리번호(GCL_MNG_NO) 채번에 사용합니다. Oracle DB 전용 Native Query입니다.
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    /**
     * 품목관리번호로 품목 조회 (삭제되지 않은 항목)
     *
     * <p>BBUGTM에서 ORC_PK_VL(gclMngNo)로 원본 품목을 역추적하여 소속 프로젝트(prjMngNo)를 확인하는 데 사용됩니다.
     *
     * @param gclMngNo 품목관리번호
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 해당 품목 목록
     */
    List<Bitemm> findByGclMngNoAndDelYn(String gclMngNo, String delYn);

    /**
     * 품목관리번호 집합 일괄 조회 (N+1 제거)
     *
     * <p>{@link #findByGclMngNoAndDelYn(String, String)}의 단건 조회를 집합으로 묶어 1회로 수행합니다. 동일한 {@code
     * DEL_YN} 필터를 유지하며, 호출부에서 gclMngNo별 첫 행 채택 규칙(원본 로직과 동일)을 적용합니다.
     *
     * @param gclMngNos 품목관리번호 집합
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 품목 목록
     */
    List<Bitemm> findByGclMngNoInAndDelYn(java.util.Collection<String> gclMngNos, String delYn);

    /**
     * 프로젝트 관리번호와 삭제여부로 품목 목록 조회 (순번 무관)
     *
     * <p>사업별 편성률 적용(REQ-2) 시 해당 사업의 모든 유효 품목을 조회하는 데 사용됩니다.
     *
     * @param prjMngNo 프로젝트 관리번호
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 해당 프로젝트의 유효 품목 목록
     */
    default List<Bitemm> findByAbusMngNoAndDelYn(String prjMngNo, String delYn) {
        return findByAbusMngNoAndDelYnAndLstYn(prjMngNo, delYn, "Y");
    }

    /**
     * 프로젝트 관리번호 집합과 삭제여부로 품목 일괄 조회 (목록 파생 합산용, N+1 제거)
     *
     * <p>{@code enrichProjectListBatch}에서 목록 파생 예산 3종 합산 시 N+1 쿼리를 제거하기 위해 대상 프로젝트들의 활성 품목을 1회 배치로
     * 조회합니다.
     *
     * @param prjMngNos 프로젝트 관리번호 집합
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 품목 목록
     */
    default List<Bitemm> findByAbusMngNoInAndDelYn(
            java.util.Collection<String> prjMngNos, String delYn) {
        return findAllByAbusMngNoInAndDelYn(prjMngNos, delYn);
    }

    /** 사업 개정 순번별 조립을 위해 관리번호 집합의 미삭제 품목을 최종여부와 무관하게 조회합니다. */
    List<Bitemm> findAllByAbusMngNoInAndDelYn(
            java.util.Collection<String> prjMngNos, String delYn);

    List<Bitemm> findByAbusMngNoInAndDelYnAndLstYn(
            java.util.Collection<String> prjMngNos, String delYn, String lstYn);

    /**
     * 사업관리번호 집합의 활성 품목을 예산 합산 전용 프로젝션으로 조회합니다.
     *
     * @param abusMngNos 사업관리번호 집합
     * @param delYn 삭제여부
     * @return 예산 합산용 품목 행
     */
    default List<ProjectItemBudgetView> findBudgetViewsByAbusMngNoInAndDelYn(
            java.util.Collection<String> abusMngNos, String delYn) {
        return findBudgetViewsByAbusMngNoInAndDelYnAndLstYn(abusMngNos, delYn, "Y");
    }

    List<ProjectItemBudgetView> findBudgetViewsByAbusMngNoInAndDelYnAndLstYn(
            java.util.Collection<String> abusMngNos, String delYn, String lstYn);

    /**
     * 프로젝트 관리번호의 최신 버전 품목 목록 조회
     *
     * <p>동일 프로젝트의 여러 버전(PRJ_SNO) 중 최신 버전({@code LST_YN='Y'}) 품목만 조회합니다. 예산 편성 작업 시 구버전 품목이 BBUGTM에
     * 중복 합산되지 않도록 최신 버전만 필터링합니다.
     *
     * @param prjMngNo 프로젝트 관리번호
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @param lstYn 최종 여부 ('Y'=최신 버전)
     * @return 최신 버전 유효 품목 목록
     */
    List<Bitemm> findByAbusMngNoAndDelYnAndLstYn(String prjMngNo, String delYn, String lstYn);

    @org.springframework.data.jpa.repository.Query(
            value = "SELECT SQ_TPRMPP_BITEMM_1.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 사업에 정보보호(보안시스템운용) 소요자원이 하나라도 있는지 확인
     *
     * <p>협의회 신청 시 심의유형 04(정보보호시스템) 노출 조건 판정에 사용합니다. 활성(미삭제) 품목 중 {@code SECT_SYS_UTZ_YN='Y'}가 하나라도
     * 있으면 true.
     *
     * @param abusMngNo 사업관리번호
     * @param sectSysUtzYn 정보보호여부 ('Y')
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 정보보호 품목 존재 여부
     */
    default boolean existsByAbusMngNoAndSectSysUtzYnAndDelYn(
            String abusMngNo, String sectSysUtzYn, String delYn) {
        return existsByAbusMngNoAndSectSysUtzYnAndDelYnAndLstYn(
                abusMngNo, sectSysUtzYn, delYn, "Y");
    }

    boolean existsByAbusMngNoAndSectSysUtzYnAndDelYnAndLstYn(
            String abusMngNo, String sectSysUtzYn, String delYn, String lstYn);
}
