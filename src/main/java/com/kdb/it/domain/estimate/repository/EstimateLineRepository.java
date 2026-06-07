package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.estimate.entity.Bestid;
import com.kdb.it.domain.estimate.entity.BestidId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소요예산 산정 상세(팀별 산정) Repository.
 *
 * <p>마스터(Bestim) 1건에 N행의 팀별·비목별 산정 명세(Bestid)를 관리합니다.</p>
 */
public interface EstimateLineRepository extends JpaRepository<Bestid, BestidId> {

    /**
     * 문서번호·버전·삭제여부로 산정 명세 행 목록을 조회합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param docVrsSno     문서버전일련번호
     * @param delYn         삭제여부 ("N")
     * @return 해당 마스터의 산정 명세 행 목록
     */
    List<Bestid> findByRqmBgReqDocNoAndDocVrsSnoAndDelYn(
            String rqmBgReqDocNo, Integer docVrsSno, String delYn);
}
