package com.kdb.it.domain.council.repository;

import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.BcmmtmId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 평가위원(Bcmmtm) 리포지토리
 *
 * <p>DB 테이블: {@code TPRMPP_BCMMTM}
 *
 * <p>IT관리자가 심의유형에 따라 당연위원/소집위원/간사를 선정합니다.
 */
public interface CommitteeRepository extends JpaRepository<Bcmmtm, BcmmtmId> {

    /**
     * 협의회별 위원 목록 조회 (삭제되지 않은 항목)
     *
     * @param itPtlAsctId 협의회ID
     * @param delYn 삭제여부 ('N')
     * @return 해당 협의회의 평가위원 목록
     */
    List<Bcmmtm> findByItPtlAsctIdAndDelYn(String itPtlAsctId, String delYn);

    /**
     * 협의회별 특정 유형 위원 목록 조회
     *
     * @param itPtlAsctId 협의회ID
     * @param itPtlAsctMebTc 위원유형 (01/02/03/04)
     * @param delYn 삭제여부 ('N')
     * @return 해당 유형의 위원 목록
     */
    List<Bcmmtm> findByItPtlAsctIdAndItPtlAsctMebTcAndDelYn(
            String itPtlAsctId, String itPtlAsctMebTc, String delYn);

    /**
     * 특정 위원 단건 조회 (협의회ID + 사번)
     *
     * @param itPtlAsctId 협의회ID
     * @param eno 사번
     * @param delYn 삭제여부 ('N')
     * @return 위원 정보 (없으면 empty)
     */
    Optional<Bcmmtm> findByItPtlAsctIdAndEnoAndDelYn(String itPtlAsctId, String eno, String delYn);
}
