package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.payment.entity.Bpaymm;
import com.kdb.it.domain.payment.entity.BpaymmId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 대금지급 마스터 Repository.
 *
 * <p>기본 CRUD는 {@link JpaRepository}가 제공하고,
 * 동적 목록 조회는 {@link PaymentRepositoryCustom}에 위임합니다.</p>
 */
public interface PaymentRepository extends JpaRepository<Bpaymm, BpaymmId>, PaymentRepositoryCustom {

    /**
     * 문서번호·최종여부·삭제여부로 현재 유효 마스터를 조회합니다.
     *
     * @param docMngNo 문서관리번호
     * @param lstYn    최종여부 ("Y")
     * @param delYn    삭제여부 ("N")
     * @return 현재 유효 마스터 (없으면 empty)
     */
    Optional<Bpaymm> findByDocMngNoAndLstYnAndDelYn(String docMngNo, String lstYn, String delYn);

    /**
     * 대금지급 문서관리번호 채번 — Oracle 시퀀스에서 다음 값을 조회합니다.
     *
     * @return 다음 시퀀스 값
     */
    @Query(nativeQuery = true, value = "SELECT SEQ_BPAYMM.NEXTVAL FROM DUAL")
    Long nextDocSeq();

    /**
     * 동일 대상·대상구분에 대해 지정 상태 중 하나인 미삭제 대금지급 문서가 이미 존재하는지 확인합니다.
     *
     * <p>중복 신청 방지: stsTc가 "81"(작성중) 또는 "85"(진행중)인 건이 있으면 신규 의뢰 불가.</p>
     *
     * @param ioeC   예산성격구분코드(대상구분)
     * @param cncdRfrNo 관련참조번호(대상관리번호)
     * @param stsTc     확인할 상태코드 컬렉션
     * @param delYn     삭제여부 ("N")
     * @return 존재하면 true
     */
    boolean existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
            String ioeC, String cncdRfrNo, java.util.Collection<String> stsTc, String delYn);
}
