package com.kdb.it.domain.budget.document.repository;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 가이드 문서(Bgdocm) 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 가이드 문서 테이블(TPRMPP_BGDOCM)의 기본 CRUD 기능을 제공합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴 적용: 조회 시 {@code delYn='N'} 조건을 사용합니다.
 * </p>
 */
public interface GuideDocRepository extends JpaRepository<Bgdocm, String> {

    /**
     * 문서관리번호와 삭제여부로 단건 조회
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 가이드 문서
     */
    Optional<Bgdocm> findByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * 삭제여부로 전체 목록 조회
     *
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 가이드 문서 목록
     */
    List<Bgdocm> findAllByDelYn(String delYn);

    /**
     * 문서관리번호와 삭제여부로 존재 여부 확인
     *
     * @param docMngNo 문서관리번호
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByDocMngNoAndDelYn(String docMngNo, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BGDOCM_1) 다음 값 조회
     *
     * <p>
     * 신규 가이드 문서 생성 시 문서관리번호 채번에 사용합니다.
     * 형식: {@code GDOC-{연도}-{4자리 시퀀스}} (예: {@code GDOC-2026-0001})
     * </p>
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_BGDOCM_1)의 다음 값
     */
    @Query(value = "SELECT SQ_TPRMPP_BGDOCM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
