package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.estimate.entity.Besttm;
import com.kdb.it.domain.estimate.entity.BesttmId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소요예산 산정 상세(팀별 산정) Repository.
 *
 * <p>마스터(Bestim) 1건에 N행의 팀별·비목별 산정 명세(Besttm)를 관리합니다.
 */
public interface EstimateLineRepository extends JpaRepository<Besttm, BesttmId> {

    /**
     * 문서번호·버전·삭제여부로 산정 명세 행 목록을 조회합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param docVrsSno 문서버전일련번호
     * @param delYn 삭제여부 ("N")
     * @return 해당 마스터의 산정 명세 행 목록
     */
    List<Besttm> findByRqmBgReqDocNoAndDocVrsSnoAndDelYn(
            String rqmBgReqDocNo, Integer docVrsSno, String delYn);

    /**
     * 문서번호·버전으로 산정 명세 행 목록을 삭제여부와 무관하게 조회합니다.
     *
     * <p>명세 일괄 저장 시 soft-delete된 행까지 포함해 동일 복합키 충돌을 막고 재추가 시 복원(restore)할 수 있도록 합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param docVrsSno 문서버전일련번호
     * @return 해당 마스터의 모든 산정 명세 행 목록 (delYn='Y' 포함)
     */
    List<Besttm> findByRqmBgReqDocNoAndDocVrsSno(String rqmBgReqDocNo, Integer docVrsSno);
}
