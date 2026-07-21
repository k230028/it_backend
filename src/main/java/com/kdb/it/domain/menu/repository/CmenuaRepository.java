package com.kdb.it.domain.menu.repository;

import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.CmenuaId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 메뉴와 권한의 활성 매핑 및 삭제 이력을 조회하는 저장소입니다. */
public interface CmenuaRepository extends JpaRepository<Cmenua, CmenuaId> {

    @Query("SELECT a FROM Cmenua a WHERE a.delYn = 'N'")
    List<Cmenua> findAllActive();

    @Query("SELECT a FROM Cmenua a WHERE a.mnuId = :mnuId AND a.delYn = 'N'")
    List<Cmenua> findActiveByMnuId(@Param("mnuId") String mnuId);

    /** 삭제분 포함 전체 매핑. 권한 재조정 시 동일 PK 행을 복원·재사용하기 위해 사용한다. */
    @Query("SELECT a FROM Cmenua a WHERE a.mnuId = :mnuId")
    List<Cmenua> findByMnuId(@Param("mnuId") String mnuId);
}
