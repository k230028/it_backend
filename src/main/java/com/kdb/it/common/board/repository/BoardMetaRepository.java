package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

/** 게시판 메타 리포지토리 */
public interface BoardMetaRepository
        extends JpaRepository<Cblbmm, String>, BoardMetaRepositoryCustom {

    Optional<Cblbmm> findByBlbMngNoAndDelYn(String blbMngNo, String delYn);

    /** 게시판 메타 채번 시퀀스 — BLBM-{YYYY}-{0001} 형식 */
    @Query(value = "SELECT SQ_BLBMNGNO.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
