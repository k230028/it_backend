package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.domain.bizplan.entity.Bbizgm;
import com.kdb.it.domain.bizplan.entity.BbizgmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업품목 기본(Bbizgm) Repository. */
public interface BbizgmRepository extends JpaRepository<Bbizgm, BbizgmId> {

    /** 사업계획의 품목 행 전체(soft-delete 포함, 병합용) — SNO 오름차순 */
    List<Bbizgm> findByAbusMngNoOrderBySnoAsc(String abusMngNo);
}
