package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CuserI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자(CuserI) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}와 커스텀 리포지토리 {@link UserRepositoryCustom}을 동시에 상속하여 표준
 * CRUD + QueryDSL 기반의 동적 쿼리 기능을 제공합니다.
 *
 * <p>기본키 타입: {@link String} (eno: 사번)
 *
 * <p>EntityGraph 전략:
 *
 * <ul>
 *   <li>{@code @EntityGraph(attributePaths = "organization")}: LAZY 로딩으로 설정된 {@code
 *       organization}(CorgnI) 연관관계를 JOIN FETCH로 즉시 로딩하여 N+1 문제를 방지합니다.
 * </ul>
 */
public interface UserRepository extends JpaRepository<CuserI, String>, UserRepositoryCustom {

    /** 이름 응답에 필요한 사용자 프로젝션. */
    interface UserNameView {
        String getEno();

        String getUsrNm();
    }

    /** 검토의견 작성자 응답에 필요한 사용자 프로젝션입니다. */
    interface ReviewCommentAuthorView {
        String getEno();

        String getUsrNm();

        String getTemNm();
    }

    /** 사용자 조직코드 응답에 필요한 프로젝션. */
    interface UserOrgCodeView {
        String getEno();

        String getTemC();

        String getBbrC();
    }

    /** 관리자 사용자 목록 응답에 필요한 프로젝션. */
    interface AdminUserView {
        String getEno();

        String getUsrNm();

        String getPtCNm();

        String getTemC();

        String getTemNm();

        String getBbrC();

        String getEtrMilAddrNm();

        String getInleNo();

        String getCpnTpn();

        LocalDateTime getFstEnrDtm();

        LocalDateTime getLstChgDtm();
    }

    /** 협의회·검토자 팀 대표 응답에 필요한 프로젝션. */
    interface CommitteeUserRow {
        String getTemC();

        String getEno();

        String getUsrNm();

        String getBbrNm();

        String getPtCNm();
    }

    /** 협의회 위원 응답에 필요한 사용자 프로젝션. */
    interface CouncilMemberUserRow {
        String getEno();

        String getUsrNm();

        String getBbrNm();

        String getPtCNm();
    }

    /**
     * 사번 목록으로 사용자 이름 프로젝션을 조회합니다.
     *
     * @param enos 사번 목록
     * @return 사용자 이름 프로젝션 목록
     */
    List<UserNameView> findNameViewsByEnoIn(Collection<String> enos);

    /**
     * 사번 목록으로 검토의견 작성자의 이름과 팀명 프로젝션을 한 번에 조회합니다.
     *
     * @param enos 중복을 제거한 작성자 사번 목록
     * @return 사번별 작성자 이름과 팀명 프로젝션 목록
     */
    List<ReviewCommentAuthorView> findReviewCommentAuthorViewsByEnoIn(Collection<String> enos);

    /**
     * 사번으로 사용자 이름 프로젝션을 조회합니다.
     *
     * @param eno 사번
     * @return 사용자 이름 프로젝션
     */
    Optional<UserNameView> findNameViewByEno(String eno);

    /**
     * 사번 목록으로 사용자 조직코드 프로젝션을 조회합니다.
     *
     * @param enos 사번 목록
     * @return 사용자 조직코드 프로젝션 목록
     */
    List<UserOrgCodeView> findOrgCodeViewsByEnoIn(Collection<String> enos);

    /**
     * 삭제 여부로 관리자 사용자 목록 프로젝션을 조회합니다.
     *
     * @param delYn 삭제 여부
     * @return 관리자 사용자 목록 프로젝션
     */
    List<AdminUserView> findAdminUserViewsByDelYn(String delYn);

    /**
     * 팀코드 목록의 활성 사용자를 조직명과 함께 조회합니다.
     *
     * @param temCs 팀코드 목록
     * @param delYn 삭제 여부
     * @return 팀 대표 후보 사용자 프로젝션 목록
     */
    @Query(
            "SELECT u.temC AS temC, u.eno AS eno, u.usrNm AS usrNm, o.bbrNm AS bbrNm, u.ptCNm AS ptCNm "
                    + "FROM CuserI u LEFT JOIN CorgnI o ON o.prlmOgzCCone = u.bbrC "
                    + "WHERE u.temC IN :temCs AND u.delYn = :delYn")
    List<CommitteeUserRow> findCommitteeUserRowsByTemCInAndDelYn(
            @Param("temCs") Collection<String> temCs, @Param("delYn") String delYn);

    /**
     * 사번 목록의 협의회 위원 응답 정보를 조직명과 함께 조회합니다.
     *
     * @param enos 사번 목록
     * @return 협의회 위원 응답 사용자 프로젝션 목록
     */
    @Query(
            "SELECT u.eno AS eno, u.usrNm AS usrNm, o.bbrNm AS bbrNm, u.ptCNm AS ptCNm "
                    + "FROM CuserI u LEFT JOIN CorgnI o ON o.prlmOgzCCone = u.bbrC "
                    + "WHERE u.eno IN :enos")
    List<CouncilMemberUserRow> findCouncilMemberUserRowsByEnoIn(
            @Param("enos") Collection<String> enos);

    /**
     * 부서코드(BBR_C)로 사용자 목록 조회 (조직 정보 즉시 로딩)
     *
     * <p>{@code @EntityGraph}를 사용하여 {@code organization}(CorgnI) 연관관계를 JOIN FETCH로 한 번에 조회합니다.
     * 부점명(bbrNm) 표시에 활용됩니다.
     *
     * @param bbrC 부서코드 (최대 3자, CorgnI.prlmOgzCCone과 조인)
     * @return 해당 부서의 사용자 목록 (조직 정보 포함)
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "organization")
    java.util.List<CuserI> findByBbrC(String bbrC);

    /**
     * 사번(ENO)으로 사용자 단건 조회 (조직 정보 즉시 로딩)
     *
     * <p>{@code @EntityGraph}를 사용하여 {@code organization}(CorgnI) 연관관계를 JOIN FETCH로 함께 조회합니다. 로그인,
     * 사용자 상세 조회에 사용됩니다.
     *
     * @param eno 사번(행번)
     * @return 해당 사번의 사용자 (없으면 {@link Optional#empty()})
     */
    @org.springframework.data.jpa.repository.EntityGraph(
            attributePaths = {"organization", "organization.parentOrganization"})
    Optional<CuserI> findByEno(String eno);

    /**
     * 사번(ENO) 목록으로 사용자 다건 조회 — 이름 일괄 변환용(배치 조회)
     *
     * <p>ENO → 이름 변환 시 N+1 쿼리를 방지하기 위해 사용합니다. 조직 정보는 불필요하므로 EntityGraph 없이 기본 조회합니다.
     *
     * @param enos 조회할 사번 컬렉션
     * @return 해당 사번들의 사용자 목록
     */
    List<CuserI> findByEnoIn(Collection<String> enos);

    /**
     * 사번(ENO) 존재 여부 확인
     *
     * <p>회원가입 시 중복 사번 검사에 사용됩니다.
     *
     * @param eno 확인할 사번
     * @return 사번이 존재하면 true, 없으면 false
     */
    boolean existsByEno(String eno);

    /**
     * 전체 사용자 목록 조회 (조직 정보 즉시 로딩) — 개발 편의용
     *
     * <p>{@code DevAuthController.listUsers()}에서만 사용합니다. 운영 환경에서는 컨트롤러를 비활성화하므로 일반 API 흐름에서는 호출되지
     * 않습니다.
     *
     * @return 전체 사용자 목록 (조직 정보 포함)
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "organization")
    java.util.List<CuserI> findAllByOrderByUsrNmAsc();

    /**
     * 팀코드(TEM_C)로 사용자 목록 조회
     *
     * <p>협의회 당연위원 자동 매핑 시 심의유형별 고정 팀코드로 위원 후보를 조회합니다. (예: 예산팀=12004, PMO팀=18010, 디지털기획팀=18501 등)
     *
     * @param temC 팀코드 (5자리 이내)
     * @return 해당 팀의 사용자 목록
     */
    java.util.List<CuserI> findByTemC(String temC);

    /**
     * 팀코드(TEM_C) 목록으로 사용자 다건 조회 — 팀 대표자 선정용(배치 조회)
     *
     * <p>팀별 반복 조회로 인한 N+1 쿼리를 방지하기 위해 사용합니다. 조직 정보는 불필요하므로 EntityGraph 없이 기본 조회합니다.
     *
     * @param temCs 조회할 팀코드 컬렉션
     * @param delYn 삭제여부 ({@code N}=활성 사용자)
     * @return 해당 팀들의 활성 사용자 목록
     */
    java.util.List<CuserI> findByTemCInAndDelYn(Collection<String> temCs, String delYn);
}
