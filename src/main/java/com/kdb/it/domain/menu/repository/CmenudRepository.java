package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CmenudRepository extends JpaRepository<Cmenud, String> {

    @Query("SELECT c FROM Cmenud c WHERE c.delYn = 'N' ORDER BY c.srePth")
    List<Cmenud> findAllActive();

    @Query("SELECT c FROM Cmenud c WHERE c.delYn = 'N' AND c.useYn = 'Y' ORDER BY c.srePth")
    List<Cmenud> findAllUsable();

    Optional<Cmenud> findBySrePthAndDelYn(String srePth, String delYn);
}
