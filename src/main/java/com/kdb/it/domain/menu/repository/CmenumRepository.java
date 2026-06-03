package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CmenumRepository extends JpaRepository<Cmenum, String>, CmenumRepositoryCustom {

    @Query("SELECT m FROM Cmenum m WHERE m.delYn = 'N'")
    List<Cmenum> findAllActive();

    Optional<Cmenum> findByMnuIdAndDelYn(String mnuId, String delYn);

    @Query("SELECT COUNT(m) FROM Cmenum m WHERE m.hrkMnuId = :mnuId AND m.delYn = 'N'")
    long countActiveChildren(String mnuId);
}
