package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.CcodemId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 공통코드 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA 기본 CRUD 및 커스텀 조회(QueryDSL) 메서드를 제공합니다.
 * </p>
 */
@Repository
public interface CodeRepository extends JpaRepository<Ccodem, CcodemId>, CodeRepositoryCustom {

    /**
     * 특정 복합키를 가진 논리적 삭제가 되지 않은 공통코드 단건 조회
     *
     * @param cdId  조회할 코드ID
     * @param sttDt 조회할 시작일자
     * @param delYn 삭제 여부 ('N')
     * @return 조회된 공통코드 엔티티
     */
    Optional<Ccodem> findByCdIdAndSttDtAndDelYn(String cdId, LocalDate sttDt, String delYn);

    /**
     * 특정 복합키의 존재 여부 확인 (삭제여부와 무관)
     */
    boolean existsByCdIdAndSttDt(String cdId, LocalDate sttDt);
}
