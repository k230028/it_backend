package com.kdb.it.common.code.repository;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.entity.CcodemId;
import java.util.Collection;
import java.util.List;
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

    /**
     * 코드ID 집합에 속한 활성 공통코드를 일괄 조회합니다.
     *
     * @param cIds 조회할 코드ID 집합
     * @param delYn 삭제여부
     * @return 코드ID 집합에 속한 공통코드 목록
     */
    @Query("SELECT c FROM Ccodem c WHERE c.cId IN :cIds AND c.delYn = :delYn")
    List<Ccodem> findAllByCIdInAndDelYn(
            @Param("cIds") Collection<String> cIds, @Param("delYn") String delYn);

    /** 복합키 존재 여부 확인 (삭제여부 무관) */
    @Query(
            "SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Ccodem c WHERE c.cId = :cId AND c.cdva = :cdva AND c.sttDt = :sttDt")
    boolean existsByCIdAndCdvaAndSttDt(
            @Param("cId") String cId, @Param("cdva") String cdva, @Param("sttDt") String sttDt);

    /**
     * 코드ID에 속한 활성 공통코드 전체를 조회합니다.
     *
     * <p>이관 조회 인덱스({@code MigrationIoeCatalogReader})가 씁니다. 클래스 상단 참고와 같은 이유로 명시적 JPQL을 사용합니다.
     *
     * <p>결과는 화면 선택 상자의 후보 나열 순서가 되므로 {@code CodeRepositoryImpl.findByCIdWithValidDate}와 같은
     * 정렬(코드순서 오름차순, 미지정은 뒤 → 코드값 오름차순)을 적용해 순서를 고정합니다.
     *
     * @param cId 코드ID
     * @param delYn 삭제여부
     * @return 코드ID에 속한 공통코드 목록. 코드순서·코드값 오름차순
     */
    @Query(
            "SELECT c FROM Ccodem c WHERE c.cId = :cId AND c.delYn = :delYn ORDER BY c.cSqn ASC NULLS LAST, c.cdva ASC")
    List<Ccodem> findByCIdAndDelYn(@Param("cId") String cId, @Param("delYn") String delYn);
}
