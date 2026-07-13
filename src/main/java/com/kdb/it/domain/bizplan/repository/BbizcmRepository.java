package com.kdb.it.domain.bizplan.repository;

import com.kdb.it.domain.bizplan.entity.Bbizcm;
import com.kdb.it.domain.bizplan.entity.BbizcmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 사업계약 기본(Bbizcm) Repository. */
public interface BbizcmRepository extends JpaRepository<Bbizcm, BbizcmId> {

    /** 사업계획의 계약 행 전체(soft-delete 포함, 병합용) — SNO 오름차순 */
    List<Bbizcm> findByAbusMngNoOrderBySnoAsc(String abusMngNo);
}
