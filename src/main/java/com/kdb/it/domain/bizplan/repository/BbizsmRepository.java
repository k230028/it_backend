package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.domain.bizplan.entity.Bbizsm;
import com.kdb.it.domain.bizplan.entity.BbizsmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업일정 기본(Bbizsm) Repository. */
public interface BbizsmRepository extends JpaRepository<Bbizsm, BbizsmId> {

    /** 사업계획의 일정 행 전체(soft-delete 포함, 병합용) — SNO 오름차순 */
    List<Bbizsm> findByAbusMngNoOrderBySnoAsc(String abusMngNo);
}
