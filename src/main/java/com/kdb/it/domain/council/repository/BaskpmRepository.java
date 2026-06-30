package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Baskpm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 협의회 타당성검토 생략판정요청(TPRMPP_BASKPM) 리포지토리. (PRD_c_20260620 #3)
 */
public interface BaskpmRepository extends JpaRepository<Baskpm, String> {

    /**
     * 협의회ID로 미삭제 생략판정요청 단건 조회.
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn       삭제여부('N')
     */
    Optional<Baskpm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);
}
