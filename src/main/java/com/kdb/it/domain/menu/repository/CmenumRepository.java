package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenum;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 메뉴 기본정보의 CRUD와 활성 메뉴 계층 조회를 담당하는 저장소입니다. */
public interface CmenumRepository extends JpaRepository<Cmenum, String>, CmenumRepositoryCustom {

    @Query("SELECT m FROM Cmenum m WHERE m.delYn = 'N'")
    List<Cmenum> findAllActive();

    Optional<Cmenum> findByMnuIdAndDelYn(String mnuId, String delYn);

    @Query("SELECT COUNT(m) FROM Cmenum m WHERE m.hrkMnuId = :mnuId AND m.delYn = 'N'")
    long countActiveChildren(@Param("mnuId") String mnuId);
}
