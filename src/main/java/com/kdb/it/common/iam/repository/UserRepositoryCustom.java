package com.kdb.it.common.iam.repository;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import java.util.List;
import java.util.Optional;

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

    /**
     * 부서코드로 사용자 목록 응답 행을 조회합니다.
     *
     * @param bbrC 부서코드
     * @return 사용자 목록 응답 행
     */
    List<UserDto.ListRow> findListRowsByBbrC(String bbrC);

    /**
     * 사용자명으로 사용자 목록 응답 행을 부분 일치 검색합니다.
     *
     * @param name 검색할 사용자명
     * @return 사용자 목록 응답 행
     */
    List<UserDto.ListRow> searchListRowsByName(String name);

    /**
     * 사번으로 사용자 상세 응답 행을 조회합니다.
     *
     * @param eno 사번
     * @return 사용자 상세 응답 행
     */
    Optional<UserDto.DetailRow> findDetailRowByEno(String eno);

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
