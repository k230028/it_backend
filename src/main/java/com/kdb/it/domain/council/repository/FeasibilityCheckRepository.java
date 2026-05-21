package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bchklc;
import com.kdb.it.domain.council.entity.BchklcId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 타당성 자체점검(Bchklc) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BCHKLC}</p>
 *
 * <p>협의회 1건당 6개 고정 항목 (CCODEM CKG_ITM 기준)이 관리됩니다.</p>
 */
public interface FeasibilityCheckRepository extends JpaRepository<Bchklc, BchklcId> {

    /**
     * 협의회별 자체점검 항목 목록 조회 (삭제되지 않은 항목)
     *
     * <p>타당성검토표 조회 시 6개 항목을 한 번에 로드합니다.</p>
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 자체점검 항목 목록 (최대 6개)
     */
    List<Bchklc> findByAsctIdAndDelYn(String asctId, String delYn);

    /**
     * 자체점검 단건 조회 (협의회ID + 점검항목코드)
     *
     * @param asctId   협의회ID
     * @param ckgItmC  점검항목코드
     * @param delYn    삭제여부 ('N')
     * @return 점검 항목 (없으면 empty)
     */
    Optional<Bchklc> findByAsctIdAndCkgItmCAndDelYn(String asctId, String ckgItmC, String delYn);
}
