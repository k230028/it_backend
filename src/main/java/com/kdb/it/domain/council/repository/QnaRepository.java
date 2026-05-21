package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bpqnam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 사전질의응답(Bpqnam) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BPQNAM}</p>
 *
 * <p>평가위원 질의 등록 및 추진부서 담당자 답변 관리.</p>
 */
public interface QnaRepository extends JpaRepository<Bpqnam, String> {

    /**
     * 협의회별 질의응답 목록 조회 (삭제되지 않은 항목, 등록일시 오름차순)
     *
     * @param asctId 협의회ID
     * @param delYn  삭제여부 ('N')
     * @return 해당 협의회의 질의응답 목록
     */
    List<Bpqnam> findByAsctIdAndDelYnOrderByFstEnrDtmAsc(String asctId, String delYn);

    /**
     * 미답변 질의 목록 조회
     *
     * <p>답변이 완료되지 않은 항목을 필터링합니다.</p>
     *
     * @param asctId 협의회ID
     * @param repYn  답변여부 ('N'=미답변)
     * @param delYn  삭제여부 ('N')
     * @return 미답변 질의 목록
     */
    List<Bpqnam> findByAsctIdAndRepYnAndDelYn(String asctId, String repYn, String delYn);

    /**
     * QTN_ID 채번 (협의회ID 기반 순번)
     *
     * <p>새로운 질의 등록 시 ID를 생성합니다.
     * 형식: {@code QTN-{asctId}-{2자리순번}} (예: QTN-ASCT-2026-0001-01)</p>
     *
     * @param asctId 협의회ID
     * @return 다음 순번 (기존 항목 없으면 1)
     */
    @Query(value = "SELECT NVL(COUNT(*), 0) + 1 FROM TPRMPP_BPQNAM WHERE ASCT_ID = :asctId",
           nativeQuery = true)
    Integer getNextQtnSeq(@Param("asctId") String asctId);
}
