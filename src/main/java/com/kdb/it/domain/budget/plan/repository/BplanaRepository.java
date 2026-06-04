package com.kdb.it.domain.budget.plan.repository;

import com.kdb.it.domain.budget.plan.entity.Bplana;
import com.kdb.it.domain.budget.plan.entity.BplanaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 정보기술부문계획 관계(TPRMPP_BPLANA) JPA 리포지토리
 */
public interface BplanaRepository extends JpaRepository<Bplana, BplanaId> {

    /**
     * 요청문서번호(계획관리번호)와 삭제여부로 연결된 정보기술부문계획 관계 목록을 조회합니다.
     *
     * @param reqDocNo 요청문서번호 (= 계획관리번호 PLN_MNG_NO)
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 정보기술부문계획 관계 엔티티 목록
     */
    List<Bplana> findAllByReqDocNoAndDelYn(String reqDocNo, String delYn);

    /**
     * 요청문서번호 목록과 삭제여부로 연결된 정보기술부문계획 관계 목록을 일괄 조회합니다.
     *
     * @param reqDocNos 요청문서번호 목록 (= 계획관리번호 PLN_MNG_NO 목록)
     * @param delYn     삭제여부 ('N'=미삭제)
     * @return 정보기술부문계획 관계 엔티티 목록
     */
    List<Bplana> findAllByReqDocNoInAndDelYn(Collection<String> reqDocNos, String delYn);
}
