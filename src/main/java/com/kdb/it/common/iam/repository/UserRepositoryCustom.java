package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 사용자(CuserI) 커스텀 리포지토리 인터페이스
 *
 * <p>Spring Data JPA의 기본 메서드로 처리하기 어려운 복잡한 동적 쿼리를 위한 커스텀 인터페이스입니다. {@link UserRepositoryImpl}에서
 * QueryDSL로 구현됩니다.
 *
 * <p>사용 패턴: {@link UserRepository}가 이 인터페이스를 상속하므로, {@code userRepository.searchByName("홍길동")}과 같이
 * 직접 사용 가능합니다.
 *
 * <p>Spring Data JPA 커스텀 리포지토리 규칙:
 *
 * <ul>
 *   <li>인터페이스 이름이 자유롭지만, 구현 클래스 이름은 반드시 {@code [인터페이스명]Impl}이어야 함
 *   <li>구현 클래스({@link UserRepositoryImpl})에 {@code @Repository} 어노테이션 불필요
 * </ul>
 */
public interface UserRepositoryCustom {

    Page<UserRepository.AdminUserView> findAdminUserPage(String search, Pageable pageable);

    List<UserRepository.AdminUserView> findAdminUsersForExport(String search, Sort sort);

    /**
     * 부서코드로 사용자 목록 응답 행을 조회합니다.
     *
     * <p>결과는 K 행번 우선, 직위코드({@code PT_C}) 오름차순(단, {@code B6*} 그룹 안에서는 내림차순), 사용자명·사번 오름차순으로 정렬합니다.
     *
     * @param bbrC 부서코드
     * @param enoPrefix 행번({@code ENO}) 접두사 필터. null·공백이면 필터를 적용하지 않습니다.
     * @return 사용자 목록 응답 행
     */
    List<UserDto.ListRow> findListRowsByBbrC(String bbrC, String enoPrefix);

    /**
     * 전체 조직을 대상으로 사용자명·사번·직위명·팀명을 부분 일치(대소문자 무시) 검색합니다.
     *
     * <p>결과는 K 행번 우선, 직위코드({@code PT_C}) 오름차순(단, {@code B6*} 그룹 안에서는 내림차순), 사용자명·사번 오름차순으로 정렬하고
     * {@code limit}건까지만 반환합니다. 전체 조직이 대상이라 상한 없이 조회하면 응답이 과도하게 커질 수 있습니다. 상한 절단은 정렬 이후에 적용되므로 우선순위가
     * 높은 사용자가 먼저 남습니다.
     *
     * <p>{@code enoPrefix}는 상한 절단보다 먼저 DB에서 적용하므로 접두사에 맞는 사용자만 {@code limit}건을 채웁니다.
     *
     * @param keyword 검색어 (공백이 아닌 부분 일치 문자열)
     * @param enoPrefix 행번({@code ENO}) 접두사 필터. null·공백이면 필터를 적용하지 않습니다.
     * @param limit 최대 반환 건수 (1 이상)
     * @return 사용자 목록 응답 행 (최대 limit건)
     */
    List<UserDto.ListRow> searchListRowsByKeyword(String keyword, String enoPrefix, int limit);

    /**
     * 사번으로 사용자 상세 응답 행을 조회합니다.
     *
     * @param eno 사번
     * @return 사용자 상세 응답 행
     */
    Optional<UserDto.DetailRow> findDetailRowByEno(String eno);

    /**
     * 사번으로 활성 보유 자격등급명을 조회합니다.
     *
     * <p>역할 매핑과 자격등급 정의가 모두 사용 중이고 삭제되지 않은 행만 이름순으로 반환합니다.
     *
     * @param eno 사번
     * @return 활성 보유 자격등급명 목록
     */
    List<String> findActiveQualificationGradeNamesByEno(String eno);

    /**
     * 사용자명으로 사용자 검색 (부분 일치)
     *
     * <p>QueryDSL을 사용하여 {@code USR_NM} 컬럼에서 입력 문자열을 포함하는 사용자를 검색합니다 (LIKE '%name%' 검색).
     *
     * <p>구현: {@link UserRepositoryImpl#searchByName(String)}
     *
     * @param name 검색할 사용자명 (부분 일치, 예: "홍" → "홍길동", "홍철수" 등 반환)
     * @return 이름에 해당 문자열을 포함하는 사용자 목록
     */
    List<CuserI> searchByName(String name);
}
