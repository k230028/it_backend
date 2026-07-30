package com.kdb.it.common.board.repository;

import com.kdb.it.common.board.entity.Cblbmm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 게시판 메타 리포지토리 */
public interface BoardMetaRepository
        extends JpaRepository<Cblbmm, String>, BoardMetaRepositoryCustom {

    Optional<Cblbmm> findByBlbMngNoAndDelYn(String blbMngNo, String delYn);

    Optional<Cblbmm> findByBlbMngNoAndUseYnAndDelYn(String blbMngNo, String useYn, String delYn);

    /** 게시판 메타 채번 시퀀스 — BLBM-{0001} 형식 */
    @Query(value = "SELECT SQ_TPRMPP_CBLBMM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
