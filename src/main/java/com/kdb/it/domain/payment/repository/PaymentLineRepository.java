package com.kdb.it.domain.payment.repository;

import com.kdb.it.domain.payment.entity.Bpaymt;
import com.kdb.it.domain.payment.entity.BpaymtId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 대금지급 상세(회차별 지급) Repository.
 *
 * <p>마스터(Bpaymm) 1건에 N행의 회차별 지급 명세(Bpaymt)를 관리합니다.</p>
 */
public interface PaymentLineRepository extends JpaRepository<Bpaymt, BpaymtId> {

    /**
     * 문서번호·버전·삭제여부로 활성 지급 명세 스칼라 행을 조회합니다.
     *
     * @param docMngNo 문서관리번호
     * @param docVrsSno 문서버전일련번호
     * @param delYn 삭제여부
     * @return 지급 명세 스칼라 행 목록
     */
    @Query("""
            select new com.kdb.it.domain.payment.repository.PaymentLineView(
                p.dfrTod, p.dfrAmt, p.dfrDt, p.dfrMplDt, p.opnnCone)
            from Bpaymt p
            where p.docMngNo = :docMngNo
              and p.docVrsSno = :docVrsSno
              and p.delYn = :delYn
            """)
    List<PaymentLineView> findLineViewsByDocMngNoAndDocVrsSnoAndDelYn(
            @Param("docMngNo") String docMngNo,
            @Param("docVrsSno") Integer docVrsSno,
            @Param("delYn") String delYn);

    /**
     * 문서번호·버전으로 지급 명세 행 목록을 삭제여부와 무관하게 조회합니다.
     *
     * <p>명세 일괄 저장 시 soft-delete된 행까지 포함해 동일 복합키 충돌을 막고
     * 재추가 시 복원(restore)할 수 있도록 합니다.</p>
     *
     * @param docMngNo  문서관리번호
     * @param docVrsSno 문서버전일련번호
     * @return 해당 마스터의 모든 지급 명세 행 목록 (delYn='Y' 포함)
     */
    List<Bpaymt> findByDocMngNoAndDocVrsSno(String docMngNo, Integer docVrsSno);

    /**
     * 문서번호·버전·삭제여부로 현재 활성 지급 명세 행 목록을 조회합니다.
     *
     * <p>조회 화면 및 상세 응답 구성 시 사용합니다.</p>
     *
     * @param docMngNo  문서관리번호
     * @param docVrsSno 문서버전일련번호
     * @param delYn     삭제여부 ("N")
     * @return 해당 마스터의 활성 지급 명세 행 목록
     */
    List<Bpaymt> findByDocMngNoAndDocVrsSnoAndDelYn(String docMngNo, Integer docVrsSno, String delYn);
}
