package com.kdb.it.infra.file.repository;

import com.kdb.it.infra.file.entity.Cfilem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 공통첨부파일기본(Cfilem) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 첨부파일 테이블(TPRMPP_CFILEM)의 기본 CRUD 기능을 제공합니다.
 *
 * <p>Soft Delete 패턴 적용: 조회 시 {@code delYn='N'} 조건을 반드시 사용합니다.
 */
public interface FileRepository extends JpaRepository<Cfilem, String> {

    /** 관리자 파일 목록 응답에 필요한 프로젝션. */
    interface AdminFileView {
        String getFlMpnId();

        String getFlNm();

        String getFlTpCone();

        String getApgFlKdNm();

        LocalDateTime getFstEnrDtm();

        String getFstEnrUsid();
    }

    /**
     * 삭제 여부로 관리자 파일 목록 프로젝션을 조회합니다.
     *
     * @param delYn 삭제 여부
     * @return 관리자 파일 목록 프로젝션
     */
    List<AdminFileView> findAdminFileViewsByDelYn(String delYn);

    /**
     * 파일매핑ID와 삭제여부로 단건 조회
     *
     * @param flMpnId 파일매핑ID (예: FL-00000001)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 메타데이터
     */
    Optional<Cfilem> findByFlMpnIdAndDelYn(String flMpnId, String delYn);

    /**
     * 첨부파일종류명 + 첨부파일연결콘텐츠명으로 파일 목록 조회
     *
     * <p>특정 도메인 레코드(예: 요구사항정의서 PRJ-2026-0001)에 연결된 모든 첨부파일을 조회합니다.
     *
     * @param apgFlKdNm 첨부파일종류명 (예: 요구사항정의서)
     * @param apgFlLnkCtzNm 첨부파일연결콘텐츠명 (예: PRJ-2026-0001)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(
            String apgFlKdNm, String apgFlLnkCtzNm, String delYn);

    /**
     * 첨부파일종류명과 여러 첨부파일연결콘텐츠명으로 파일 목록을 한 번에 조회합니다.
     *
     * @param apgFlKdNm 첨부파일종류명
     * @param apgFlLnkCtzNms 중복이 제거된 첨부파일연결콘텐츠명 집합
     * @param delYn 삭제여부
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByApgFlKdNmAndApgFlLnkCtzNmInAndDelYn(
            String apgFlKdNm, Set<String> apgFlLnkCtzNms, String delYn);

    /**
     * 첨부파일종류명과 첨부파일연결콘텐츠명에 연결된 파일 수를 삭제 여부별로 집계합니다.
     *
     * @param apgFlKdNm 첨부파일종류명
     * @param apgFlLnkCtzNm 첨부파일연결콘텐츠명
     * @param delYn 삭제 여부
     * @return 조건에 맞는 파일 수
     */
    long countByApgFlKdNmAndApgFlLnkCtzNmAndDelYn(
            String apgFlKdNm, String apgFlLnkCtzNm, String delYn);

    /**
     * 첨부파일종류명으로 파일 목록 전체 조회
     *
     * <p>특정 도메인 종류(예: 요구사항정의서)에 속한 모든 파일을 조회합니다. apgFlLnkCtzNm 미지정 시 사용합니다.
     *
     * @param apgFlKdNm 첨부파일종류명
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByApgFlKdNmAndDelYn(String apgFlKdNm, String delYn);

    /**
     * 첨부파일종류명 + 첨부파일연결콘텐츠명으로 삭제 여부와 무관하게 파일 목록을 조회합니다.
     *
     * <p>배너 관리 화면처럼 활성(DEL_YN='N')과 비활성(DEL_YN='Y')을 함께 보여줘야 하는 경우에만 사용합니다. 일반 조회는 반드시 delYn 조건이 있는
     * 메서드를 씁니다.
     *
     * @param apgFlKdNm 첨부파일종류명
     * @param apgFlLnkCtzNm 첨부파일연결콘텐츠명
     * @return 파일매핑ID 오름차순 파일 목록 (활성·비활성 포함)
     */
    List<Cfilem> findAllByApgFlKdNmAndApgFlLnkCtzNmOrderByFlMpnIdAsc(
            String apgFlKdNm, String apgFlLnkCtzNm);

    /**
     * 첨부파일종류명 + 첨부파일연결콘텐츠명 + 파일유형내용으로 파일 목록 조회
     *
     * <p>특정 레코드에서 이미지 또는 첨부파일만 필터링하여 조회합니다.
     *
     * @param apgFlKdNm 첨부파일종류명
     * @param apgFlLnkCtzNm 첨부파일연결콘텐츠명
     * @param flTpCone 파일유형내용 ('이미지' 또는 '첨부파일')
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 조건에 맞는 파일 목록
     */
    List<Cfilem> findAllByApgFlKdNmAndApgFlLnkCtzNmAndFlTpConeAndDelYn(
            String apgFlKdNm, String apgFlLnkCtzNm, String flTpCone, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_CFILEM_1) 다음 값 조회
     *
     * <p>파일매핑ID 채번에 사용합니다. 형식: {@code FL-{8자리 시퀀스}} (예: {@code FL-00000001})
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_CFILEM_1)의 다음 값
     */
    @Query(value = "SELECT SQ_TPRMPP_CFILEM_1.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
