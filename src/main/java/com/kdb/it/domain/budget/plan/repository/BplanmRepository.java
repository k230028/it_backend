package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bplanm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 정보기술부문계획(TAAABB_BPLANM) JPA 리포지토리
 */
public interface BplanmRepository extends JpaRepository<Bplanm, String> {

    /**
     * Oracle 시퀀스(SEQ_BPLANM)에서 다음 채번값을 조회합니다.
     * 계획관리번호 생성에 사용됩니다. (형식: PLN-{연도}-{seq:04d})
     *
     * @return 다음 시퀀스 값
     */
    @Query(nativeQuery = true, value = "SELECT SEQ_BPLANM.NEXTVAL FROM DUAL")
    Long getNextSequenceValue();

    /**
     * 삭제되지 않은 전체 계획 목록을 등록일시 내림차순으로 조회합니다.
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 계획 엔티티 목록
     */
    List<Bplanm> findAllByDelYnOrderByFstEnrDtmDesc(String delYn);

    /**
     * 계획관리번호와 삭제여부로 단건 조회합니다.
     *
     * @param plnMngNo 계획관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 계획 엔티티 (Optional)
     */
    Optional<Bplanm> findByPlnMngNoAndDelYn(String plnMngNo, String delYn);
}
