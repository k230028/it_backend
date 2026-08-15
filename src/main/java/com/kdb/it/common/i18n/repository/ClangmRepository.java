package com.kdb.it.common.i18n.repository;

import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.entity.ClangmId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 언어별구분코드마스터 저장소입니다. */
public interface ClangmRepository extends JpaRepository<Clangm, ClangmId> {

    /** 지정 언어와 대상 키에 속한 활성 번역을 일괄 조회합니다. */
    @Query(
            "SELECT c FROM Clangm c WHERE c.dttNm = :target AND c.dttLanC = :language "
                    + "AND c.tcIdCone IN :targetKeys AND c.delYn = 'N'")
    List<Clangm> findActiveByTargetAndLanguageAndKeys(
            @Param("target") String target,
            @Param("language") String language,
            @Param("targetKeys") Collection<String> targetKeys);

    /** 대상 키의 삭제 행을 포함한 모든 번역을 조회합니다. */
    @Query("SELECT c FROM Clangm c WHERE c.dttNm = :target AND c.tcIdCone = :targetKey")
    List<Clangm> findByDttNmAndTcIdCone(
            @Param("target") String target, @Param("targetKey") String targetKey);

    /** 대상 키에 번역 행이 하나라도 존재하는지 확인합니다. */
    @Query(
            "SELECT CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Clangm c "
                    + "WHERE c.dttNm = :target AND c.tcIdCone = :targetKey")
    boolean existsByDttNmAndTcIdCone(
            @Param("target") String target, @Param("targetKey") String targetKey);
}
