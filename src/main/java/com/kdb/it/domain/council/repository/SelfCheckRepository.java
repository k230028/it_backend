package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bchklm;
import com.kdb.it.domain.council.entity.BchklmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 타당성 자체점검(TPRMPP_BCHKLM) 리포지토리.
 *
 * <p>협의회별 6개 점검항목 자체점검 행을 조회/저장한다.
 */
public interface SelfCheckRepository extends JpaRepository<Bchklm, BchklmId> {

    /**
     * 협의회의 자체점검 항목 전체 조회.
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn 삭제여부 ('N')
     * @return 자체점검 항목 목록 (정렬은 서비스/프론트에서 처리)
     */
    List<Bchklm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);
}
