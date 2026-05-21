package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bpovwm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 협의회 사업개요(Bpovwm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BPOVWM}</p>
 *
 * <p>BASCTM과 1:1 관계. 타당성검토표 Step 1 작성 시 함께 생성됩니다.</p>
 */
public interface ProjectOverviewRepository extends JpaRepository<Bpovwm, String> {

    /**
     * 협의회 사업개요 단건 조회 (삭제되지 않은 항목)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 사업개요 (없으면 empty)
     */
    Optional<Bpovwm> findByAsctIdAndDelYn(String asctId, String delYn);
}
