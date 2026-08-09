package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 정보화사업(Bprojm) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여 정보화사업 테이블(TPRMPP_BPROJM)의 기본 CRUD 기능을 제공합니다.
 *
 * <p>기본키: {@link BprojmId} (복합키: prjMngNo + prjSno)
 *
 * <p>Soft Delete 패턴 적용: 조회 시 항상 {@code delYn='N'} 조건을 사용합니다.
 */
public interface ProjectRepository
        extends JpaRepository<Bprojm, BprojmId>, ProjectRepositoryCustom {

    /** 소요예산 상세의 사업명 표시에 필요한 단건 프로젝션입니다. */
    interface ProjectNameView {
        String getAbusMngNo();

        String getAbusNm();
    }

    /** 사업명 배치 조회용 최소 필드. */
    interface ProjectKeyView {
        String getAbusMngNo();

        String getAbusNm();
    }

    /**
     * 프로젝트 관리번호와 삭제여부로 단건 조회
     *
     * <p>삭제여부로 특정 프로젝트를 조회합니다. 프로젝트 상세 조회 시 사용합니다.
     *
     * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 프로젝트 (없으면 {@link Optional#empty()})
     */
    Optional<Bprojm> findByAbusMngNoAndDelYn(String prjMngNo, String delYn);

    /**
     * 사업관리번호 집합 일괄 조회 (N+1 제거) — 사업명 매핑용
     *
     * <p>{@link #findByAbusMngNoAndDelYn(String, String)}의 단건 조회를 집합으로 묶어 1회로 수행합니다. 동일한 {@code
     * DEL_YN} 필터를 유지하며, 호출부에서 abusMngNo별 첫 행 채택 규칙(원본 로직과 동일)을 적용합니다. (LST_YN 조건 없는 별도 메서드)
     *
     * @param abusMngNos 사업관리번호 집합
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 사업 목록
     */
    List<Bprojm> findByAbusMngNoInAndDelYn(Collection<String> abusMngNos, String delYn);

    /**
     * 사업관리번호·최종여부·삭제여부로 현재 버전(최신 스냅샷) 사업 단건 조회
     *
     * <p>동일 {@code abusMngNo}의 여러 버전 중 {@code lstYn='Y'} 한 건만 반환하여 복수 결과로 인한 예외를 방지합니다. 소요예산 산정
     * 상세에서 사업명 표기에 사용합니다.
     *
     * @param abusMngNo 사업관리번호 (예: PRJ-2026-0001)
     * @param lstYn 최종여부 ('Y'=최신 레코드)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 현재 버전 사업 (없으면 {@link Optional#empty()})
     */
    Optional<Bprojm> findByAbusMngNoAndLstYnAndDelYn(String abusMngNo, String lstYn, String delYn);

    /**
     * 현재 유효 사업의 관리번호와 사업명만 조회합니다.
     *
     * @param abusMngNo 사업관리번호
     * @param lstYn 최종여부
     * @param delYn 삭제여부
     * @return 사업명 프로젝션
     */
    Optional<ProjectNameView> findNameViewByAbusMngNoAndLstYnAndDelYn(
            String abusMngNo, String lstYn, String delYn);

    /** 현재 유효 사업의 관리번호와 사업명을 배치 조회합니다. */
    List<ProjectKeyView> findKeyViewsByAbusMngNoInAndLstYnAndDelYn(
            Collection<String> abusMngNos, String lstYn, String delYn);

    /**
     * 프로젝트 관리번호와 삭제여부로 존재 여부 확인
     *
     * <p>중복 생성 방지를 위한 효율적인 존재 여부 확인 메서드입니다. {@code COUNT(*)} 쿼리를 사용하여 엔티티 전체를 로드하지 않습니다.
     *
     * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByAbusMngNoAndDelYn(String prjMngNo, String delYn);

    /**
     * 전체 정보화사업 목록 조회 (삭제 여부 조건)
     *
     * <p>DEL_YN 조건으로 삭제된 항목을 제외한 모든 프로젝트를 반환합니다.
     *
     * @param delYn 삭제 여부 ('N'=미삭제, 'Y'=삭제)
     * @return 조건에 맞는 정보화사업 목록
     */
    List<Bprojm> findAllByDelYn(String delYn);

    /**
     * 프로젝트 관리번호 목록 + 삭제여부 + 최종여부로 일괄 조회
     *
     * <p>계획 목록 화면에서 연결된 정보화사업의 요약 카운트(정보화사업/신규/계속)를 산출하기 위해 사용합니다.
     *
     * @param prjMngNos 프로젝트 관리번호 목록
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @param lstYn 최종 여부 ('Y'=최신 레코드)
     * @return 조건에 맞는 정보화사업 목록
     */
    List<Bprojm> findAllByAbusMngNoInAndDelYnAndLstYn(
            Collection<String> prjMngNos, String delYn, String lstYn);

    /**
     * 프로젝트 관리번호 목록 + 삭제여부로 일괄 조회 (lstYn 무관)
     *
     * <p>계획 목록 카운트 산출 시 lstYn='Y' 조건을 강제하지 않고 동일 prjMngNo 의 어떤 스냅샷이든 가져오기 위해 사용합니다.
     *
     * @param prjMngNos 프로젝트 관리번호 목록
     * @param delYn 삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 정보화사업 목록 (동일 prjMngNo 의 여러 스냅샷이 포함될 수 있음)
     */
    List<Bprojm> findAllByAbusMngNoInAndDelYn(Collection<String> prjMngNos, String delYn);

    /**
     * Oracle 시퀀스(SQ_TPRMPP_BPROJM_1) 다음 값 조회
     *
     * <p>신규 프로젝트 생성 시 관리번호 채번에 사용합니다. 형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})
     *
     * <p>Oracle DB 전용 Native Query입니다.
     *
     * @return Oracle 시퀀스(SQ_TPRMPP_BPROJM_1)의 다음 값(Long)
     */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT SQ_TPRMPP_BPROJM_1.NEXTVAL FROM DUAL",
            nativeQuery = true)
    Long getNextSequenceValue();

    /**
     * 사업계획서 사업일정(TPRMPP_BBIZSM) 기준 사업별 일정 범위 일괄 조회
     *
     * <p>대시보드 '사업별 진행현황' 간트 막대를 예산 일정이 아닌 사업계획서 일정 기준으로 표시하기 위해, 활성 사업일정 행에서 사업(ABUS_MNG_NO)별 최소
     * 시작일({@code MIN(STT_DT)})과 최대 종료일({@code MAX(END_DT)})을 집계합니다.
     *
     * <p>날짜는 {@code YYYYMMDD}(VARCHAR2(8)) 문자열이라 문자열 MIN/MAX가 곧 최소/최대 날짜와 일치하며, NULL 날짜는 집계에서 자동
     * 제외됩니다. 사업계획 일정 행이 없는 사업은 결과에 포함되지 않습니다(호출부에서 예산 일정으로 폴백).
     *
     * <p>도메인 순환 의존(budget.project ↔ bizplan)을 피하기 위해 bizplan 엔티티를 참조하지 않고 테이블명을 직접 지정하는 Oracle 전용
     * Native Query로 조회합니다.
     *
     * @param abusMngNos 사업관리번호 목록 (비어있으면 호출하지 않음)
     * @return {@code Object[]{ABUS_MNG_NO, MIN(STT_DT), MAX(END_DT)}} 행 목록 (일정 있는 사업만)
     */
    @Query(
            value =
                    """
            SELECT ABUS_MNG_NO, MIN(STT_DT) AS MIN_STT_DT, MAX(END_DT) AS MAX_END_DT
              FROM TPRMPP_BBIZSM
             WHERE ABUS_MNG_NO IN (:abusMngNos)
               AND DEL_YN = 'N'
             GROUP BY ABUS_MNG_NO
            """,
            nativeQuery = true)
    List<Object[]> findBizplanScheduleRange(
            @org.springframework.data.repository.query.Param("abusMngNos")
                    Collection<String> abusMngNos);

    /**
     * 사업관리번호·최종여부·삭제여부로 사업 존재 여부 확인 (소요예산 산정 신청 유효성 검증용)
     *
     * <p>소요예산 산정 신규 신청 시 대상 사업이 실제로 존재하는지 확인합니다.
     *
     * @param abusMngNo 사업관리번호 (예: PRJ-2026-0001)
     * @param lstYn 최종여부 ('Y'=최신 레코드)
     * @param delYn 삭제여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByAbusMngNoAndLstYnAndDelYn(String abusMngNo, String lstYn, String delYn);

    /**
     * 활성 정보화사업의 경량 참조 목록 조회 (Tiptap 변수 카탈로그용)
     *
     * <p>동일 prjMngNo 의 여러 버전 중 최신({@code LST_YN='Y'}) 레코드만 반환하여 드롭다운에서 중복 사업이 나타나지 않도록 합니다.
     *
     * @return 활성 사업의 (관리번호, 사업명) 참조 목록 (사업명 오름차순)
     */
    @Query(
            """
            SELECT new com.kdb.it.domain.budget.project.entity.Bprojm$Ref(p.abusMngNo, p.abusNm)
              FROM Bprojm p
             WHERE p.delYn = 'N'
               AND p.lstYn = 'Y'
             ORDER BY p.abusNm ASC
            """)
    List<Bprojm.Ref> findActiveProjectRefs();

    /**
     * 부서(주관부서코드) 기준 활성 사업 참조 목록 (Tiptap 카탈로그 권한 필터용)
     *
     * <p>{@link #findActiveProjectRefs()}와 동일하게 최신({@code LST_YN='Y'}) 미삭제 사업만 반환하되, 주관부서코드({@code
     * SVN_DPM_C})가 일치하는 사업으로 한정합니다. 일반 사용자(비관리자·비부서매니저)의 변수 카탈로그 부서 필터에 사용합니다.
     *
     * @param svnDpmC 주관부서코드 (JWT bbrC와 동일 도메인)
     * @return 해당 부서의 활성 사업 (관리번호, 사업명) 참조 목록 (사업명 오름차순)
     */
    @Query(
            """
            SELECT new com.kdb.it.domain.budget.project.entity.Bprojm$Ref(p.abusMngNo, p.abusNm)
              FROM Bprojm p
             WHERE p.delYn = 'N'
               AND p.lstYn = 'Y'
               AND p.svnDpmC = :svnDpmC
             ORDER BY p.abusNm ASC
            """)
    List<Bprojm.Ref> findActiveProjectRefsByDept(String svnDpmC);
}
