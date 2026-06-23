package com.kdb.it.infra.file.repository;

import com.kdb.it.infra.file.entity.Cfilem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 공통첨부파일기본(Cfilem) 데이터 접근 리포지토리
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
public interface FileRepository extends JpaRepository<Cfilem, String> {

    /**
     * 파일매핑ID와 삭제여부로 단건 조회
     *
     * @param flMpnId 파일매핑ID (예: FL_00000001)
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 메타데이터
     */
    Optional<Cfilem> findByFlMpnIdAndDelYn(String flMpnId, String delYn);

    /**
     * 주식별자컬럼명 + 주식별자내용으로 파일 목록 조회
     *
     * <p>
     * 특정 도메인 레코드(예: 요구사항정의서 PRJ-2026-0001)에 연결된
     * 모든 첨부파일을 조회합니다.
     * </p>
     *
     * @param pkColNm 주식별자컬럼명 (예: 요구사항정의서)
     * @param pkCone  주식별자내용 (예: PRJ-2026-0001)
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByPkColNmAndPkConeAndDelYn(String pkColNm, String pkCone, String delYn);

    /**
     * 주식별자컬럼명으로 파일 목록 전체 조회
     *
     * <p>
     * 특정 도메인 종류(예: 요구사항정의서)에 속한 모든 파일을 조회합니다.
     * pkCone 미지정 시 사용합니다.
     * </p>
     *
     * @param pkColNm 주식별자컬럼명
     * @param delYn   삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByPkColNmAndDelYn(String pkColNm, String delYn);

    /**
     * 주식별자컬럼명 + 주식별자내용 + 파일유형내용으로 파일 목록 조회
     *
     * <p>
     * 특정 레코드에서 이미지 또는 첨부파일만 필터링하여 조회합니다.
     * </p>
     *
     * @param pkColNm  주식별자컬럼명
     * @param pkCone   주식별자내용
     * @param flTpCone 파일유형내용 ('이미지' 또는 '첨부파일')
     * @param delYn    삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByPkColNmAndPkConeAndFlTpConeAndDelYn(
            String pkColNm, String pkCone, String flTpCone, String delYn);

    /**
     * Oracle 시퀀스(SEQ_CFILEM) 다음 값 조회
     *
     * <p>
     * 파일매핑ID 채번에 사용합니다.
     * 형식: {@code FL_{8자리 시퀀스}} (예: {@code FL_00000001})
     * </p>
     *
     * @return Oracle 시퀀스(SEQ_CFILEM)의 다음 값
     */
    @Query(value = "SELECT SEQ_CFILEM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
