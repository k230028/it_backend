package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.domain.bizplan.entity.Bbizpm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업계획 기본(Bbizpm) Repository. 목록 조회는 {@link BizplanRepositoryCustom}에 위임. */
public interface BizplanRepository extends JpaRepository<Bbizpm, String>, BizplanRepositoryCustom {

    /** 사업관리번호로 미삭제 사업계획 단건 조회 */
    Optional<Bbizpm> findByAbusMngNoAndDelYn(String abusMngNo, String delYn);
}
