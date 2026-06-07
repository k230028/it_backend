package com.kdb.it.domain.contract.repository;

import com.kdb.it.domain.contract.entity.Bcontm;
import com.kdb.it.domain.contract.entity.BcontmId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 입찰계약 기본 Repository. */
public interface ContractRepository extends JpaRepository<Bcontm, BcontmId>, ContractRepositoryCustom {

    /**
     * 문서관리번호로 최신 활성 버전을 조회한다.
     *
     * @param docMngNo 문서관리번호
     * @param lstYn    최종여부 (통상 "Y")
     * @param delYn    삭제여부 (통상 "N")
     * @return 일치하는 엔티티 Optional
     */
    Optional<Bcontm> findByDocMngNoAndLstYnAndDelYn(String docMngNo, String lstYn, String delYn);

    /**
     * 입찰계약 문서 채번 시퀀스 다음 값을 반환한다.
     *
     * @return SEQ_BCONTM.NEXTVAL
     */
    @Query(nativeQuery = true, value = "SELECT SEQ_BCONTM.NEXTVAL FROM DUAL")
    Long nextDocSeq();

    /**
     * 동일 대상에 처리 중인 입찰계약 문서가 이미 존재하는지 확인한다.
     *
     * @param bgPrnTc   예산성격구분코드
     * @param cncdRfrNo 관련참조번호
     * @param stsTc     체크할 상태코드 목록
     * @param delYn     삭제여부 (통상 "N")
     * @return 존재하면 true
     */
    boolean existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
            String bgPrnTc, String cncdRfrNo, java.util.Collection<String> stsTc, String delYn);
}
