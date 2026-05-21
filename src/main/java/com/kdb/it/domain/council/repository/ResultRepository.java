package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Brsltm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 협의회 결과서(Brsltm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BRSLTM}</p>
 *
 * <p>BASCTM과 1:1 관계. Step 3(결과서 작성) 단계에서 IT관리자가 작성합니다.</p>
 */
public interface ResultRepository extends JpaRepository<Brsltm, String> {

    /**
     * 협의회 결과서 단건 조회 (삭제되지 않은 항목)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 결과서 (없으면 empty — 아직 작성 전)
     */
    Optional<Brsltm> findByAsctIdAndDelYn(String asctId, String delYn);
}
