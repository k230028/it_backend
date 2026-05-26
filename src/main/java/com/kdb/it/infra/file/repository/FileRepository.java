package com.kdb.it.infra.file.repository;

import com.kdb.it.infra.file.entity.Cfilem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 공통 첨부파일(Cfilem) 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 첨부파일 테이블(TPRMPP_CFILEM)의 기본 CRUD 기능을 제공합니다.
 * </p>
 *
 * <p>
 * Soft Delete 패턴 적용: 조회 시 {@code delYn='N'} 조건을 반드시 사용합니다.
 * </p>
 */
@Repository
public interface FileRepository extends JpaRepository<Cfilem, String> {

    /**
     * 파일관리번호와 삭제여부로 단건 조회
     *
     * @param flMngNo 파일관리번호 (예: FL_00000001)
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 메타데이터
     */
    Optional<Cfilem> findByFlMngNoAndDelYn(String flMngNo, String delYn);

    /**
     * 원본구분 + 원본PK값으로 파일 목록 조회
     *
     * <p>
     * 특정 도메인 레코드(예: 요구사항정의서 PRJ-2026-0001)에 연결된
     * 모든 첨부파일을 조회합니다.
     * </p>
     *
     * @param orcDtt  원본구분 (예: 요구사항정의서)
     * @param orcPkVl 원본PK값 (예: PRJ-2026-0001)
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByOrcDttAndOrcPkVlAndDelYn(String orcDtt, String orcPkVl, String delYn);

    /**
     * 원본구분으로 파일 목록 전체 조회
     *
     * <p>
     * 특정 도메인 종류(예: 요구사항정의서)에 속한 모든 파일을 조회합니다.
     * orcPkVl 미지정 시 사용합니다.
     * </p>
     *
     * @param orcDtt 원본구분
     * @param delYn  삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByOrcDttAndDelYn(String orcDtt, String delYn);

    /**
     * 원본구분 + 원본PK값 + 파일구분으로 파일 목록 조회
     *
     * <p>
     * 특정 레코드에서 이미지 또는 첨부파일만 필터링하여 조회합니다.
     * </p>
     *
     * @param orcDtt  원본구분
     * @param orcPkVl 원본PK값
     * @param flDtt   파일구분 ('이미지' 또는 '첨부파일')
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByOrcDttAndOrcPkVlAndFlDttAndDelYn(String orcDtt, String orcPkVl, String flDtt, String delYn);

    /**
     * Oracle 시퀀스(SEQ_CFILEM) 다음 값 조회
     *
     * <p>
     * 파일관리번호 채번에 사용합니다.
     * 형식: {@code FL_{8자리 시퀀스}} (예: {@code FL_00000001})
     * </p>
     *
     * @return Oracle 시퀀스(SEQ_CFILEM)의 다음 값
     */
    @Query(value = "SELECT SEQ_CFILEM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
