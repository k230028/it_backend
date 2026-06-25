package com.kdb.it.domain.estimate.repository;

import com.kdb.it.domain.estimate.entity.Bestim;
import com.kdb.it.domain.estimate.entity.BestimId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 소요예산 산정 마스터 Repository.
 *
 * <p>기본 CRUD는 {@link JpaRepository}가 제공하고,
 * 동적 목록 조회는 {@link EstimateRepositoryCustom}에 위임합니다.</p>
 */
public interface EstimateRepository extends JpaRepository<Bestim, BestimId>, EstimateRepositoryCustom {

    /**
     * 문서번호·최종여부·삭제여부로 현재 유효 마스터를 조회합니다.
     *
     * @param rqmBgReqDocNo 소요예산요청문서번호
     * @param lstYn         최종여부 ("Y")
     * @param delYn         삭제여부 ("N")
     * @return 현재 유효 마스터 (없으면 empty)
     */
    Optional<Bestim> findByRqmBgReqDocNoAndLstYnAndDelYn(String rqmBgReqDocNo, String lstYn, String delYn);

    /**
     * 소요예산요청문서번호 채번 — Oracle 시퀀스에서 다음 값을 조회합니다.
     *
     * @return 다음 시퀀스 값
     */
    @Query(nativeQuery = true, value = "SELECT SEQ_BESTIM.NEXTVAL FROM DUAL")
    Long nextDocSeq();

    /**
     * 동일 사업·대상구분에 대해 지정 상태 중 하나인 미삭제 산정 문서가 이미 존재하는지 확인합니다.
     *
     * <p>중복 신청 방지: stsTc가 "51"(작성중) 또는 "55"(진행중)인 건이 있으면 신규 신청 불가.</p>
     *
     * @param bgPrnTc   예산성격구분코드
     * @param cncdRfrNo 관련참조번호(사업관리번호)
     * @param stsTc     확인할 상태코드 컬렉션
     * @param delYn     삭제여부 ("N")
     * @return 존재하면 true
     */
    boolean existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
            String bgPrnTc, String cncdRfrNo, Collection<String> stsTc, String delYn);
}
