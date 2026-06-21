package com.kdb.it.domain.budget.cost.repository;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.BcostmId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 전산관리비(Bcostm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 기본 CRUD 기능을 제공하며,
 * 전산관리비 도메인에 특화된 조회 메서드를 추가로 정의합니다.</p>
 *
 * <p>복합키 타입: {@link BcostmId} (itMngcNo + itMngcSno)</p>
 *
 * <p>Soft Delete 패턴 적용: 조회 시 항상 {@code delYn='N'} 조건을 사용합니다.</p>
 */
public interface CostRepository extends JpaRepository<Bcostm, BcostmId>, CostRepositoryCustom {

    /**
     * 특정 전산관리비 단건 조회 (삭제되지 않은 항목)
     *
     * <p>관리번호 + 일련번호 + 삭제여부의 조합으로 단 하나의 레코드를 조회합니다.</p>
     *
     * @param costBgNo  전산관리비 관리번호 (예: COST_2026_0001)
     * @param bgSno     전산관리비 일련번호 (예: 1)
     * @param delYn     삭제 여부 ('N'=미삭제, 'Y'=삭제)
     * @return 조건에 맞는 전산관리비 (없으면 {@link Optional#empty()})
     */
    Optional<Bcostm> findByCostBgNoAndBgSnoAndDelYn(String costBgNo, Integer bgSno, String delYn);

    /**
     * 전체 전산관리비 목록 조회 (삭제되지 않은 항목)
     *
     * <p>DEL_YN 조건으로 삭제된 항목을 제외한 모든 목록을 반환합니다.</p>
     *
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 삭제되지 않은 전산관리비 목록
     */
    List<Bcostm> findAllByDelYn(String delYn);

    /**
     * 관리번호별 전산관리비 목록 조회 (삭제되지 않은 항목)
     *
     * <p>동일한 BG_NO를 가진 여러 일련번호(SNO) 레코드를 모두 조회합니다.
     * 주로 수정·삭제 시 해당 관리번호의 모든 유효 레코드를 찾는 데 사용됩니다.</p>
     *
     * @param costBgNo 전산관리비 관리번호 (예: COST_2026_0001)
     * @param delYn    삭제 여부 ('N'=미삭제)
     * @return 해당 관리번호의 삭제되지 않은 전산관리비 목록
     */
    List<Bcostm> findByCostBgNoAndDelYn(String costBgNo, String delYn);

    /**
     * 관리번호별 전산관리비 최신 버전 목록 조회
     *
     * <p>동일 관리번호의 여러 버전 중 최신({@code LST_YN='Y'}) 레코드만 조회합니다.
     * 예산 편성 작업 시 최신 버전 금액에만 편성률을 적용해야 하므로 사용됩니다.</p>
     *
     * @param costBgNo 전산관리비 관리번호
     * @param delYn    삭제 여부 ('N'=미삭제)
     * @param lstYn    최종 여부 ('Y'=최신 버전)
     * @return 최신 버전의 전산관리비 목록
     */
    List<Bcostm> findByCostBgNoAndDelYnAndLstYn(String costBgNo, String delYn, String lstYn);

    /**
     * Oracle 시퀀스(SEQ_BCOSTM) 다음 값 조회
     *
     * <p>새로운 전산관리비 생성 시 관리번호용 시퀀스 값을 채번합니다.
     * Oracle DB 전용 Native Query입니다.</p>
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    @Query(value = "SELECT SEQ_BCOSTM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 특정 관리번호 내 다음 일련번호(SNO) 계산
     *
     * <p>동일한 BG_NO에서 현재 최대 일련번호 + 1을 반환합니다.
     * 새로운 버전의 레코드 저장 시 SNO를 채번하는 데 사용됩니다.</p>
     *
     * <p>Oracle DB 전용 Native Query (NVL로 첫 번째 항목인 경우 1 반환)</p>
     *
     * @param costBgNo 전산관리비 관리번호
     * @return 다음 일련번호 (기존 레코드가 없으면 1)
     */
    @Query(value = "SELECT NVL(MAX(BG_SNO), 0) + 1 FROM TPRMPP_BCOSTM WHERE BG_NO = :costBgNo", nativeQuery = true)
    Integer getNextSnoValue(@Param("costBgNo") String costBgNo);

    /**
     * 전산관리비 관리번호 존재 여부 확인 (과업심의 대상 유효성 검증용).
     *
     * <p>현재 유효 버전({@code lstYn='Y'}) + 미삭제({@code delYn='N'}) 조합으로 확인합니다.</p>
     *
     * @param costBgNo 전산관리비 관리번호 (예: COST_2026_0001)
     * @param lstYn    최종여부 ('Y'=현재 유효)
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 해당 조건의 레코드가 존재하면 true
     */
    boolean existsByCostBgNoAndLstYnAndDelYn(String costBgNo, String lstYn, String delYn);

    /**
     * 전산관리비 현재 유효 버전 단건 조회 (과업심의 대상명 해석용).
     *
     * <p>현재 유효 버전({@code lstYn='Y'}) + 미삭제({@code delYn='N'}) 조합으로 단 하나의 레코드를 조회합니다.
     * 대상명은 {@code Bcostm#getCttNm()} (계약명)으로 식별합니다.</p>
     *
     * @param costBgNo 전산관리비 관리번호
     * @param lstYn    최종여부 ('Y'=현재 유효)
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 전산관리비 (없으면 empty)
     */
    Optional<Bcostm> findByCostBgNoAndLstYnAndDelYn(String costBgNo, String lstYn, String delYn);
}
