package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.BcostmId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 전산관리비(Bcostm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 기본 CRUD 기능을 제공하며, 전산관리비 도메인에 특화된 조회 메서드를 추가로
 * 정의합니다.
 *
 * <p>복합키 타입: {@link BcostmId} (itMngcNo + itMngcSno)
 *
 * <p>일반 업무 조회는 {@code delYn='N'} 조건을 적용한다. 스냅샷 변경 감지용 버전 조회·잠금은 삭제 상태까지 읽는다.
 */
public interface CostRepository extends JpaRepository<Bcostm, BcostmId>, CostRepositoryCustom {

    /** 문서 전체 삭제·승격 전에 활성 개정본을 순번순으로 모두 잠그며 대기를 5초로 제한한다. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"))
    @Query("SELECT p FROM Bcostm p WHERE p.costBgNo = :id AND p.delYn = 'N' ORDER BY p.bgSno")
    List<Bcostm> findAllVersionsForUpdate(@Param("id") String id);

    /** 기존 대표행 선택 계약을 보존하며 활성 최종본 후보를 순번순으로 잠근다. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"))
    @Query(
            "SELECT c FROM Bcostm c WHERE c.costBgNo = :id AND c.delYn = 'N' AND c.lstYn = 'Y' ORDER BY c.bgSno")
    List<Bcostm> findCurrentVersionsForUpdate(@Param("id") String id);

    /** 삭제 상태까지 읽는 스냅샷용 후보 조회다. 빈 집합은 호출하지 않으며 정확한 ID·개정 쌍은 호출자가 필터한다. */
    @Query(
            "SELECT c FROM Bcostm c WHERE c.costBgNo IN :ids AND c.bgSno IN :revisions ORDER BY c.costBgNo, c.bgSno")
    List<Bcostm> findVersions(
            @Param("ids") Collection<String> ids,
            @Param("revisions") Collection<Integer> revisions);

    /** 최대 500개 참조의 삭제 상태까지 안정 순서로 잠그며 잠금 대기를 5초로 제한한다. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"))
    @Query(
            "SELECT c FROM Bcostm c WHERE c.costBgNo IN :ids AND c.bgSno IN :revisions ORDER BY c.costBgNo, c.bgSno")
    List<Bcostm> findVersionsForUpdate(
            @Param("ids") Collection<String> ids,
            @Param("revisions") Collection<Integer> revisions);

    /** 재상신 순번 채번 중 동일 예산의 현재 최종본을 잠급니다. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"))
    @Query(
            "SELECT c FROM Bcostm c WHERE c.costBgNo = :costBgNo AND c.lstYn = 'Y' AND c.delYn = 'N'")
    Optional<Bcostm> findCurrentVersionForUpdate(@Param("costBgNo") String costBgNo);

    /** 최종본 전환 전에 승인 대상 개정본을 잠급니다. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(
            @jakarta.persistence.QueryHint(
                    name = "jakarta.persistence.lock.timeout",
                    value = "5000"))
    @Query(
            "SELECT c FROM Bcostm c WHERE c.costBgNo = :costBgNo AND c.bgSno = :bgSno AND c.delYn = 'N'")
    Optional<Bcostm> findVersionForUpdate(
            @Param("costBgNo") String costBgNo, @Param("bgSno") Integer bgSno);

    /** 같은 예산번호의 이전 최종본을 해제합니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            "UPDATE Bcostm c SET c.lstYn = 'N' WHERE c.costBgNo = :costBgNo AND c.bgSno <> :bgSno AND c.delYn = 'N' AND c.lstYn = 'Y'")
    int clearCurrentVersion(@Param("costBgNo") String costBgNo, @Param("bgSno") Integer bgSno);

    /** 승인된 정확한 개정본을 최종본으로 지정합니다. */
    @Modifying(flushAutomatically = true)
    @Query(
            "UPDATE Bcostm c SET c.lstYn = 'Y' WHERE c.costBgNo = :costBgNo AND c.bgSno = :bgSno AND c.delYn = 'N'")
    int markVersionCurrent(@Param("costBgNo") String costBgNo, @Param("bgSno") Integer bgSno);

    /** 비용 대표행 선정과 계약명 표시에 필요한 필드만 읽는 프로젝션입니다. */
    interface CostRepresentativeView {
        String getCostBgNo();

        Integer getBgSno();

        String getLstYn();

        String getCttNm();
    }

    /**
     * 특정 전산관리비 단건 조회 (삭제되지 않은 항목)
     *
     * <p>관리번호 + 일련번호 + 삭제여부의 조합으로 단 하나의 레코드를 조회합니다.
     *
     * @param costBgNo 전산관리비 관리번호 (예: COST_2026_0001)
     * @param bgSno 전산관리비 일련번호 (예: 1)
     * @param delYn 삭제 여부 ('N'=미삭제, 'Y'=삭제)
     * @return 조건에 맞는 전산관리비 (없으면 {@link Optional#empty()})
     */
    Optional<Bcostm> findByCostBgNoAndBgSnoAndDelYn(String costBgNo, Integer bgSno, String delYn);

    /** 상세 이력 다이얼로그에 사용할 미삭제 개정본 목록입니다. */
    List<Bcostm> findByCostBgNoAndDelYnOrderByBgSnoAsc(String costBgNo, String delYn);

    /**
     * 전체 전산관리비 목록 조회 (삭제되지 않은 항목)
     *
     * <p>DEL_YN 조건으로 삭제된 항목을 제외한 모든 목록을 반환합니다.
     *
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 삭제되지 않은 최종 전산관리비 목록
     */
    default List<Bcostm> findAllByDelYn(String delYn) {
        return findAllByDelYnAndLstYn(delYn, "Y");
    }

    /** 일반 목록 화면에 노출할 미삭제 최종본만 조회합니다. */
    List<Bcostm> findAllByDelYnAndLstYn(String delYn, String lstYn);

    /**
     * 관리번호별 전산관리비 목록 조회 (삭제되지 않은 항목)
     *
     * <p>동일한 BG_NO를 가진 여러 일련번호(SNO) 레코드를 모두 조회합니다. 주로 수정·삭제 시 해당 관리번호의 모든 유효 레코드를 찾는 데 사용됩니다.
     *
     * @param costBgNo 전산관리비 관리번호 (예: COST_2026_0001)
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 해당 관리번호의 삭제되지 않은 최종 전산관리비 목록
     */
    default List<Bcostm> findByCostBgNoAndDelYn(String costBgNo, String delYn) {
        return findByCostBgNoAndDelYnAndLstYn(costBgNo, delYn, "Y");
    }

    /** 예산연도와 현재 유효 버전까지 확인하는 단건 조회입니다. */
    Optional<Bcostm> findByCostBgNoAndBseYyAndLstYnAndDelYn(
            String costBgNo, String bseYy, String lstYn, String delYn);

    /** 전년도 예산 금액과 통화를 관리번호 집합으로 한 번에 조회합니다. */
    List<Bcostm> findByCostBgNoInAndBseYyAndLstYnAndDelYn(
            java.util.Collection<String> costBgNos, String bseYy, String lstYn, String delYn);

    /** 금융정보단말기 일괄업로드 대상인지까지 확인하는 최신 전산업무비 조회입니다. */
    Optional<Bcostm> findByCostBgNoAndBseYyAndLstYnAndTmnYnAndDelYn(
            String costBgNo, String bseYy, String lstYn, String tmnYn, String delYn);

    /**
     * 전산업무비번호 집합 일괄 조회 (N+1 제거) — 계약명 매핑용
     *
     * <p>{@link #findByCostBgNoAndDelYn(String, String)}의 단건 조회를 집합으로 묶어 1회로 수행합니다. 동일한 {@code
     * DEL_YN} 필터를 유지하며, 호출부에서 costBgNo별 첫 행 채택 규칙(원본 로직과 동일)을 적용합니다.
     *
     * @param costBgNos 전산업무비 관리번호 집합
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 최종 전산관리비 목록
     */
    default List<Bcostm> findByCostBgNoInAndDelYn(
            java.util.Collection<String> costBgNos, String delYn) {
        return findByCostBgNoInAndDelYnAndLstYn(costBgNos, delYn, "Y");
    }

    /** 일반 일괄 조회에 노출할 미삭제 최종본만 조회합니다. */
    List<Bcostm> findByCostBgNoInAndDelYnAndLstYn(
            java.util.Collection<String> costBgNos, String delYn, String lstYn);

    /**
     * 비용관리번호 집합의 이력을 대표행 선정 전용 프로젝션으로 조회합니다.
     *
     * @param costBgNos 비용관리번호 집합
     * @param delYn 삭제여부
     * @return 비용 대표행 후보
     */
    List<CostRepresentativeView> findRepresentativeViewsByCostBgNoInAndDelYn(
            java.util.Collection<String> costBgNos, String delYn);

    /**
     * 관리번호별 전산관리비 최신 버전 목록 조회
     *
     * <p>동일 관리번호의 여러 버전 중 최신({@code LST_YN='Y'}) 레코드만 조회합니다. 예산 편성 작업 시 최신 버전 금액에만 편성률을 적용해야 하므로
     * 사용됩니다.
     *
     * @param costBgNo 전산관리비 관리번호
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @param lstYn 최종 여부 ('Y'=최신 버전)
     * @return 최신 버전의 전산관리비 목록
     */
    List<Bcostm> findByCostBgNoAndDelYnAndLstYn(String costBgNo, String delYn, String lstYn);

    /**
     * 관리번호·순번 집합으로 개정본을 일괄 조회합니다 (버전 지정 bulk 조회용).
     *
     * <p>튜플 IN을 쓸 수 없으므로 두 집합의 곱으로 넉넉히 읽고 호출부가 정확한 쌍만 채택합니다.
     *
     * @param costBgNos 전산업무비예산번호 집합
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @param bgSnos 개정 순번 집합
     * @return 조건에 맞는 개정본 목록
     */
    List<Bcostm> findByCostBgNoInAndDelYnAndBgSnoIn(
            Collection<String> costBgNos, String delYn, Collection<Integer> bgSnos);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BCOSTM_1) 다음 값 조회
     *
     * <p>새로운 전산관리비 생성 시 관리번호용 시퀀스 값을 채번합니다. Oracle DB 전용 Native Query입니다.
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    @Query(value = "SELECT SQ_TPRMPP_BCOSTM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 특정 관리번호 내 다음 일련번호(SNO) 계산
     *
     * <p>동일한 BG_NO에서 현재 최대 일련번호 + 1을 반환합니다. 새로운 버전의 레코드 저장 시 SNO를 채번하는 데 사용됩니다.
     *
     * <p>Oracle DB 전용 Native Query (NVL로 첫 번째 항목인 경우 1 반환)
     *
     * @param costBgNo 전산관리비 관리번호
     * @return 다음 일련번호 (기존 레코드가 없으면 1)
     */
    @Query(
            value = "SELECT NVL(MAX(BG_SNO), 0) + 1 FROM TPRMPP_BCOSTM WHERE BG_NO = :costBgNo",
            nativeQuery = true)
    Integer getNextSnoValue(@Param("costBgNo") String costBgNo);

    /**
     * 전산관리비 관리번호 존재 여부 확인 (과업심의 대상 유효성 검증용).
     *
     * <p>현재 유효 버전({@code lstYn='Y'}) + 미삭제({@code delYn='N'}) 조합으로 확인합니다.
     *
     * @param costBgNo 전산관리비 관리번호 (예: COST_2026_0001)
     * @param lstYn 최종여부 ('Y'=현재 유효)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 해당 조건의 레코드가 존재하면 true
     */
    boolean existsByCostBgNoAndLstYnAndDelYn(String costBgNo, String lstYn, String delYn);

    /**
     * 현재 최종본보다 뒤 순번의 미삭제 개정본이 있는지 확인합니다 — 미결 재상신 초안 판정용입니다.
     *
     * <p>{@code LST_YN='N'}만으로는 미결 초안을 가려낼 수 없습니다. 승격으로 강등된 과거 버전도 같은 값을 갖기 때문입니다.
     *
     * @param costBgNo 전산업무비예산번호
     * @param bgSno 현재 최종본의 개정 순번
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 최종본보다 뒤 순번의 개정본이 있으면 true
     */
    boolean existsByCostBgNoAndBgSnoGreaterThanAndDelYn(
            String costBgNo, Integer bgSno, String delYn);

    /**
     * 전산관리비 현재 유효 버전 단건 조회 (과업심의 대상명 해석용).
     *
     * <p>현재 유효 버전({@code lstYn='Y'}) + 미삭제({@code delYn='N'}) 조합으로 단 하나의 레코드를 조회합니다. 대상명은 {@code
     * Bcostm#getCttNm()} (계약명)으로 식별합니다.
     *
     * @param costBgNo 전산관리비 관리번호
     * @param lstYn 최종여부 ('Y'=현재 유효)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 전산관리비 (없으면 empty)
     */
    Optional<Bcostm> findByCostBgNoAndLstYnAndDelYn(String costBgNo, String lstYn, String delYn);

    /**
     * 예산연도의 최종·미삭제 전산업무비 전체를 조회합니다.
     *
     * <p>수기 엑셀 이관의 중복 판정과 편성률 재적용 대상 구성에 사용합니다. 연도 단위라 행 수가 제한적이므로 전량 로드가 타당합니다.
     *
     * @param bseYy 예산연도 (4자리)
     * @param lstYn 최종여부 ('Y')
     * @param delYn 삭제여부 ('N')
     * @return 해당 연도의 최종·미삭제 전산업무비 목록
     */
    List<Bcostm> findByBseYyAndLstYnAndDelYn(String bseYy, String lstYn, String delYn);
}
