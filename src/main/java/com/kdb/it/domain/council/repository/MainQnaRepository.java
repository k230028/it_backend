package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bmqnam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 본회의질의응답(Bmqnam) 리포지토리 (PRD §26)
 *
 * <p>DB 테이블: {@code TPRMPP_BMQNAM}</p>
 *
 * <p>IT관리자가 본회의 동안 오간 Q&A를 정리·관리합니다.
 * 사전질의응답({@link QnaRepository})과 동일 메서드 시그니처를 유지합니다.</p>
 */
public interface MainQnaRepository extends JpaRepository<Bmqnam, String> {

    /**
     * 협의회별 본회의 질의응답 목록 조회 (삭제되지 않은 항목, 등록일시 오름차순)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 본회의 질의응답 목록
     */
    List<Bmqnam> findByAsctIdAndDelYnOrderByFstEnrDtmAsc(String asctId, String delYn);

    /**
     * QTN_ID 채번 (협의회ID 기반 순번)
     *
     * <p>형식: {@code MQT-{asctId}-{2자리순번}} (예: MQT-ASCT-2026-0001-01)</p>
     *
     * @param asctId 협의회ID
     * @return 다음 순번 (기존 항목 없으면 1)
     */
    @Query(value = "SELECT NVL(COUNT(*), 0) + 1 FROM TPRMPP_BMQNAM WHERE ASCT_ID = :asctId",
           nativeQuery = true)
    Integer getNextQtnSeq(@Param("asctId") String asctId);
}
