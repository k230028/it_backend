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
     * 문서번호·버전으로 산정 명세 행 목록을 삭제여부와 무관하게, 개선의견일련번호 오름차순으로 조회합니다.
     *
     * <p>명세 일괄 저장 시 soft-delete된 행까지 포함해 동일 복합키 충돌을 막고 재추가 시 복원(restore)할 수 있도록 합니다.
     *
     * <p>운영 PK는 (문서번호+버전+개선의견일련번호) 3컬럼이라 (팀+비목) 업무 키의 유일성을 DB가 더 이상 보장하지
     * 않는다. 동일 키를 가진 물리 행이 2건 이상 존재할 때 {@code EstimateService.saveLines()}는 "먼저 나온
     * (=낮은 일련번호) 행을 대표로 남긴다"는 규칙으로 병합하므로, 이 조회가 명시적으로 오름차순 정렬을 보장하지
     * 않으면 그 규칙이 실제로 성립하지 않는다. 정렬 없는 파생 쿼리는 Oracle에서 행 순서를 보장하지 않는다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param docVrsSno 문서버전일련번호
     * @return 해당 마스터의 모든 산정 명세 행 목록, 개선의견일련번호 오름차순 (delYn='Y' 포함)
     */
    List<Besttm> findByRqmBgReqDocNoAndDocVrsSnoOrderByIpmOpnnSnoAsc(
            String rqmBgReqDocNo, Integer docVrsSno);
}
