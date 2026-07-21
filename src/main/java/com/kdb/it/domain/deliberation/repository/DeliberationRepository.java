package com.kdb.it.domain.deliberation.repository;

import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.entity.BdelimId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 과업심의 기본(마스터) Spring Data JPA 리포지토리.
 *
 * <p>기본 CRUD는 {@link JpaRepository}에서 제공하고,
 * 동적 목록 검색은 {@link DeliberationRepositoryCustom}에서 QueryDSL로 구현합니다.</p>
 */
public interface DeliberationRepository extends JpaRepository<Bdelim, BdelimId>, DeliberationRepositoryCustom {

    /**
     * 문서관리번호 + 최종여부 + 삭제여부로 현재 유효 버전 단건 조회.
     *
     * @param docMngNo 문서관리번호
     * @param lstYn    최종여부 ('Y'=현재 유효)
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 과업심의 (없으면 empty)
     */
    Optional<Bdelim> findByDocMngNoAndLstYnAndDelYn(String docMngNo, String lstYn, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BDELIM_1) 다음 값 조회.
     *
     * <p>신규 과업심의 문서관리번호 채번 시 사용합니다.</p>
     *
     * @return 시퀀스의 다음 값 (Long)
     */
    @Query(nativeQuery = true, value = "SELECT SQ_TPRMPP_BDELIM_1.NEXTVAL FROM DUAL")
    Long nextDocSeq();

    /**
     * 동일 대상에 진행 중인 심의 신청이 있는지 확인 (중복 신청 방지).
     *
     * <p>대상구분(ioeC) + 대상관리번호(cncdRfrNo) + 지정 상태 목록 + 미삭제 조건으로 존재 여부를 확인합니다.</p>
     *
     * @param ioeC   예산성격구분코드(대상구분)
     * @param cncdRfrNo 관련참조번호(대상관리번호)
     * @param stsTc     확인할 상태 코드 집합
     * @param delYn     삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 레코드가 하나라도 있으면 true
     */
    boolean existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
            String ioeC, String cncdRfrNo, java.util.Collection<String> stsTc, String delYn);
}
