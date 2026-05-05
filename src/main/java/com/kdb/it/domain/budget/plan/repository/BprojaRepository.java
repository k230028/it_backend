package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bproja;
import com.kdb.it.domain.budget.plan.entity.BprojaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 정보화사업 관계(TAAABB_BPROJA) JPA 리포지토리
 */
public interface BprojaRepository extends JpaRepository<Bproja, BprojaId> {

    /**
     * 업무관리번호(계획관리번호)와 삭제여부로 연결된 정보화사업 관계 목록을 조회합니다.
     *
     * @param bzMngNo 업무관리번호 (= 계획관리번호 PLN_MNG_NO)
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 정보화사업 관계 엔티티 목록
     */
    List<Bproja> findAllByBzMngNoAndDelYn(String bzMngNo, String delYn);
}
