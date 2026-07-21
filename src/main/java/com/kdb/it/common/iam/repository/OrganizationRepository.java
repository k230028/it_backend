package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.entity.CorgnI;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 조직(부점) 정보(CorgnI) 데이터 접근 리포지토리
 *
 * <p>Spring Data JPA의 {@link JpaRepository}를 상속하여
 * 조직 테이블(TPRMPP_CORGNI)에 대한 CRUD 기능을 제공합니다.</p>
 *
 * <p>기본키 타입: {@link String} (prlmOgzCCone: 조직코드)</p>
 *
 * <p>기본 제공 메서드 ({@link JpaRepository} 상속):</p>
 * <ul>
 *   <li>{@code findById(orgCode)}: 조직코드로 단건 조회</li>
 *   <li>{@code findAll()}: 전체 조직 목록 조회</li>
 * </ul>
 *
 * <p>현재는 커스텀 메서드 없이 기본 기능만 사용합니다.
 * 조직 정보는 읽기 전용으로 주로 활용됩니다.</p>
 */
public interface OrganizationRepository extends JpaRepository<CorgnI, String> {

    /** 조직명 응답에 필요한 조직코드와 이름 프로젝션. */
    interface OrganizationNameView {
        String getPrlmOgzCCone();
        String getBbrNm();
    }

    /**
     * 조직코드 목록으로 조직명 프로젝션을 조회합니다.
     *
     * @param prlmOgzCCones 조직코드 목록
     * @return 조직명 프로젝션 목록
     */
    List<OrganizationNameView> findNameViewsByPrlmOgzCConeIn(Collection<String> prlmOgzCCones);

    /**
     * 조직코드로 조직명 프로젝션을 조회합니다.
     *
     * @param prlmOgzCCone 조직코드
     * @return 조직명 프로젝션
     */
    Optional<OrganizationNameView> findNameViewByPrlmOgzCCone(String prlmOgzCCone);
}
