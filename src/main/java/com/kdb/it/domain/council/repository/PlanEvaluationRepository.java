package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.entity.BplevmId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 정보기술부문계획 협의회 사업별 평가의견(Bplevm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BPLEVM}
 *
 * <p>평가위원이 계획에 포함된 각 정보화사업에 대해 남긴 적정/유보 및 사유를 관리합니다.
 */
public interface PlanEvaluationRepository extends JpaRepository<Bplevm, BplevmId> {

    /**
     * 협의회별 전체 사업 평가의견 조회 (삭제되지 않은 항목)
     *
     * <p>IT관리자가 사업별 판정 집계·결과서 작성 시 사용합니다.
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn 삭제여부 ('N')
     * @return 해당 협의회의 전체 사업별 평가의견 목록
     */
    List<Bplevm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);

    /**
     * 특정 위원의 사업별 평가의견 목록 조회
     *
     * @param itPtlAsctId 협의회ID
     * @param eno 사번
     * @param delYn 삭제여부 ('N')
     * @return 해당 위원이 남긴 사업별 평가의견 목록
     */
    List<Bplevm> findByItPtlAsctIdAndEnoAndDelYn(String itPtlAsctId, String eno, String delYn);

    /**
     * 특정 위원의 특정 사업 평가의견 단건 조회 (upsert 판정용)
     *
     * @param itPtlAsctId 협의회ID
     * @param eno 사번
     * @param abusMngNo 사업관리번호
     * @param delYn 삭제여부 ('N')
     * @return 평가의견 (없으면 empty)
     */
    Optional<Bplevm> findByItPtlAsctIdAndEnoAndAbusMngNoAndDelYn(
            String itPtlAsctId, String eno, String abusMngNo, String delYn);
}
