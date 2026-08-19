package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.entity.CroleIId;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 역할관리(TPRMPP_CROLEI) JPA 리포지토리
 *
 * <p>사용자(ENO)와 자격등급(ATH_ID) 매핑 데이터를 조회합니다. 한 사용자가 여러 자격등급을 가질 수 있으므로 List를 반환합니다.
 */
public interface RoleRepository extends JpaRepository<CroleI, CroleIId> {

    /** 삭제되지 않은 역할을 역할ID·사번·사용자명으로 검색해 페이지 단위로 조회합니다. */
    @Query(
            value =
                    "SELECT r FROM CroleI r LEFT JOIN CuserI u ON u.eno = r.id.eno "
                            + "WHERE r.delYn = 'N' AND (:search IS NULL OR "
                            + "LOWER(r.id.athId) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                            + "LOWER(r.id.eno) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                            + "LOWER(u.usrNm) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))))",
            countQuery =
                    "SELECT COUNT(r) FROM CroleI r LEFT JOIN CuserI u ON u.eno = r.id.eno "
                            + "WHERE r.delYn = 'N' AND (:search IS NULL OR "
                            + "LOWER(r.id.athId) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                            + "LOWER(r.id.eno) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                            + "LOWER(u.usrNm) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))))")
    Page<CroleI> findAdminRolePage(@Param("search") String search, Pageable pageable);

    /** 엑셀 내보내기를 위해 현재 검색 조건의 역할 전체를 조회합니다. */
    @Query(
            "SELECT r FROM CroleI r LEFT JOIN CuserI u ON u.eno = r.id.eno "
                    + "WHERE r.delYn = 'N' AND (:search IS NULL OR "
                    + "LOWER(r.id.athId) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                    + "LOWER(r.id.eno) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))) OR "
                    + "LOWER(u.usrNm) LIKE LOWER(CONCAT('%', CONCAT(:search, '%'))))")
    List<CroleI> findAdminRolesForExport(@Param("search") String search, Sort sort);

    /**
     * 사번으로 유효한 자격등급 전체 조회 (다중 자격등급 지원)
     *
     * <p>USE_YN='Y', DEL_YN='N' 조건을 만족하는 모든 자격등급 행을 반환합니다. 자격등급이 없는 사용자는 빈 리스트를 반환하며, 호출부에서
     * 기본값(ITPZZ001)을 적용합니다.
     *
     * @param eno 조회할 사원번호
     * @param useYn 사용여부 ('Y' 전달)
     * @param delYn 삭제여부 ('N' 전달)
     * @return 해당 사용자의 모든 활성 자격등급 목록 (없으면 빈 리스트)
     */
    List<CroleI> findAllByIdEnoAndUseYnAndDelYn(String eno, String useYn, String delYn);
}
