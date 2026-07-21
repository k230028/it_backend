package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.entity.CroleIId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 역할관리(TPRMPP_CROLEI) JPA 리포지토리
 *
 * <p>사용자(ENO)와 자격등급(ATH_ID) 매핑 데이터를 조회합니다. 한 사용자가 여러 자격등급을 가질 수 있으므로 List를 반환합니다.
 */
public interface RoleRepository extends JpaRepository<CroleI, CroleIId> {

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
