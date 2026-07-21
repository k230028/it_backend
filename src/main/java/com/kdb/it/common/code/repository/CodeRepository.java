package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.CcodemId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공통코드 데이터 접근 리포지토리
 *
 * <p>참고: 엔티티 필드 {@code cId}는 두 번째 문자가 대문자라 JavaBeans 규칙상 프로퍼티명이 {@code CId}로 해석되어 Spring Data 파생
 * 쿼리가 Hibernate 메타모델의 {@code cId} 속성을 찾지 못한다. 따라서 명시적 JPQL을 사용한다.
 */
public interface CodeRepository extends JpaRepository<Ccodem, CcodemId>, CodeRepositoryCustom {

    /** 복합키(cId, cdva, sttDt) + 삭제여부로 단건 조회 */
    @Query(
            "SELECT c FROM Ccodem c WHERE c.cId = :cId AND c.cdva = :cdva AND c.sttDt = :sttDt AND c.delYn = :delYn")
    Optional<Ccodem> findByCIdAndCdvaAndSttDtAndDelYn(
            @Param("cId") String cId,
            @Param("cdva") String cdva,
            @Param("sttDt") String sttDt,
            @Param("delYn") String delYn);

    /** 복합키 존재 여부 확인 (삭제여부 무관) */
    @Query(
            "SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Ccodem c WHERE c.cId = :cId AND c.cdva = :cdva AND c.sttDt = :sttDt")
    boolean existsByCIdAndCdvaAndSttDt(
            @Param("cId") String cId, @Param("cdva") String cdva, @Param("sttDt") String sttDt);
}
