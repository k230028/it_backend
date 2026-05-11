package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.CcodemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 공통코드 데이터 접근 리포지토리
 */
@Repository
public interface CodeRepository extends JpaRepository<Ccodem, CcodemId>, CodeRepositoryCustom {

    /**
     * 복합키(cId, cdva, sttDt) + 삭제여부로 단건 조회
     */
    Optional<Ccodem> findByCIdAndCdvaAndSttDtAndDelYn(String cId, String cdva, LocalDate sttDt, String delYn);

    /**
     * 복합키 존재 여부 확인 (삭제여부 무관)
     */
    boolean existsByCIdAndCdvaAndSttDt(String cId, String cdva, LocalDate sttDt);
}
