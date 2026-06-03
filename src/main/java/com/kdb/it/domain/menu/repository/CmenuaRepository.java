package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.CmenuaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CmenuaRepository extends JpaRepository<Cmenua, CmenuaId> {

    @Query("SELECT a FROM Cmenua a WHERE a.delYn = 'N'")
    List<Cmenua> findAllActive();

    @Query("SELECT a FROM Cmenua a WHERE a.mnuId = :mnuId AND a.delYn = 'N'")
    List<Cmenua> findActiveByMnuId(String mnuId);
}
