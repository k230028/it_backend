package com.kdb.it.domain.budget.project.repository;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 정보화사업(Bprojm) 데이터 접근 리포지토리
 *
 * <p>
 * Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 정보화사업 테이블(TAAABB_BPROJM)의 기본 CRUD 기능을 제공합니다.
 * </p>
 *
 * <p>
 * 기본키: {@link BprojmId} (복합키: prjMngNo + prjSno)
 * </p>
 *
 * <p>
 * Soft Delete 패턴 적용: 조회 시 항상 {@code delYn='N'} 조건을 사용합니다.
 * </p>
 */
@Repository // Spring 리포지토리 빈으로 등록
public interface ProjectRepository extends JpaRepository<Bprojm, BprojmId>, ProjectRepositoryCustom {

    /**
     * 프로젝트 관리번호와 삭제여부로 단건 조회
     *
     * <p>
     * 삭제여부로 특정 프로젝트를 조회합니다.
     * 프로젝트 상세 조회 시 사용합니다.
     * </p>
     *
     * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
     * @param delYn    삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 프로젝트 (없으면 {@link Optional#empty()})
     */
    Optional<Bprojm> findByPrjMngNoAndDelYn(String prjMngNo, String delYn);

    /**
     * 프로젝트 관리번호와 삭제여부로 존재 여부 확인
     *
     * <p>
     * 중복 생성 방지를 위한 효율적인 존재 여부 확인 메서드입니다.
     * {@code COUNT(*)} 쿼리를 사용하여 엔티티 전체를 로드하지 않습니다.
     * </p>
     *
     * @param prjMngNo 프로젝트 관리번호 (예: PRJ-2026-0001)
     * @param delYn    삭제 여부 ('N'=미삭제)
     * @return 존재하면 {@code true}
     */
    boolean existsByPrjMngNoAndDelYn(String prjMngNo, String delYn);

    /**
     * 전체 정보화사업 목록 조회 (삭제 여부 조건)
     *
     * <p>
     * DEL_YN 조건으로 삭제된 항목을 제외한 모든 프로젝트를 반환합니다.
     * </p>
     *
     * @param delYn 삭제 여부 ('N'=미삭제, 'Y'=삭제)
     * @return 조건에 맞는 정보화사업 목록
     */
    List<Bprojm> findAllByDelYn(String delYn);

    /**
     * 프로젝트 관리번호 목록 + 삭제여부 + 최종여부로 일괄 조회
     *
     * <p>
     * 계획 목록 화면에서 연결된 정보화사업의 요약 카운트(정보화사업/신규/계속)를
     * 산출하기 위해 사용합니다.
     * </p>
     *
     * @param prjMngNos 프로젝트 관리번호 목록
     * @param delYn     삭제 여부 ('N'=미삭제)
     * @param lstYn     최종 여부 ('Y'=최신 레코드)
     * @return 조건에 맞는 정보화사업 목록
     */
    List<Bprojm> findAllByPrjMngNoInAndDelYnAndLstYn(Collection<String> prjMngNos, String delYn, String lstYn);

    /**
     * 프로젝트 관리번호 목록 + 삭제여부로 일괄 조회 (lstYn 무관)
     *
     * <p>
     * 계획 목록 카운트 산출 시 lstYn='Y' 조건을 강제하지 않고
     * 동일 prjMngNo 의 어떤 스냅샷이든 가져오기 위해 사용합니다.
     * </p>
     *
     * @param prjMngNos 프로젝트 관리번호 목록
     * @param delYn     삭제 여부 ('N'=미삭제)
     * @return 조건에 맞는 정보화사업 목록 (동일 prjMngNo 의 여러 스냅샷이 포함될 수 있음)
     */
    List<Bprojm> findAllByPrjMngNoInAndDelYn(Collection<String> prjMngNos, String delYn);

    /**
     * Oracle 시퀀스(SEQ_BPROJM) 다음 값 조회
     *
     * <p>
     * 신규 프로젝트 생성 시 관리번호 채번에 사용합니다.
     * 형식: {@code PRJ-{사업연도}-{4자리 시퀀스}} (예: {@code PRJ-2026-0001})
     * </p>
     *
     * <p>
     * Oracle DB 전용 Native Query입니다.
     * </p>
     *
     * @return Oracle 시퀀스(SEQ_BPROJM)의 다음 값(Long)
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT SEQ_BPROJM.NEXTVAL FROM DUAL", nativeQuery = true)
    Long getNextSequenceValue();
}
