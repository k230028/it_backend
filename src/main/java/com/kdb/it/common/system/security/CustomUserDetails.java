package com.kdb.it.common.system.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security UserDetails 확장 구현체
 *
 * <p>JWT 클레임에서 추출한 eno, athIds(복수), bbrC를 담아 SecurityContext에 저장합니다. {@link
 * JwtAuthenticationFilter}에서 매 요청마다 생성되며, DB 재조회 없이 JWT 클레임 데이터를 직접 활용합니다.
 *
 * <p>다중 자격등급 지원: 한 사용자가 여러 자격등급(athIds)을 가질 수 있으며, 모든 자격등급에 대응하는 {@link GrantedAuthority}를 등록하여 최상위
 * 권한으로 접근 범위를 결정합니다.
 *
 * <p>권한 우선순위 (높은 순): ITPAD001(ROLE_ADMIN) &gt; ITPZZ002(ROLE_DEPT_MANAGER) &gt; ITPZZ001(ROLE_USER)
 */
@Getter
public class CustomUserDetails implements UserDetails {

    // -------------------------------------------------------------------------
    // 자격등급 ID 상수
    // -------------------------------------------------------------------------

    /** 시스템관리자: 전체 조회/수정/삭제, 관리자 메뉴 접근 */
    public static final String ATH_ADMIN = "ITPAD001";

    /** 정보보호관리자: 정보보호기획팀 — 정보보호시스템 사업(dbrTc=04) 협의회 개최준비 권한 (PRD_c_20260620 #3) */
    public static final String ATH_INFOSEC_ADMIN = "ITPAD002";

    /** 기획통할담당자: 소속 부서 조회/수정/삭제 */
    public static final String ATH_DEPT_MGR = "ITPZZ002";

    /** 일반사용자: 소속 부서 조회, 본인 작성 수정 (기본값) */
    public static final String ATH_USER = "ITPZZ001";

    // Spring Security Role 상수 (Spring Security Role은 ROLE_ 접두사 필요)
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_INFOSEC_ADMIN = "ROLE_INFOSEC_ADMIN";
    private static final String ROLE_DEPT_MANAGER = "ROLE_DEPT_MANAGER";
    private static final String ROLE_USER = "ROLE_USER";

    // -------------------------------------------------------------------------
    // 필드
    // -------------------------------------------------------------------------

    /** 사번: 사용자 고유 식별자 */
    private final String eno;

    /** 자격등급 ID 목록: 보유한 모든 자격등급 (다중 가능, 기본값 [ITPZZ001]) */
    private final List<String> athIds;

    /** 소속 부서코드: 권한 범위 결정에 사용 */
    private final String bbrC;

    /** Spring Security 권한 목록: 모든 자격등급에 대응하는 Role 등록 */
    private final Collection<? extends GrantedAuthority> authorities;

    // -------------------------------------------------------------------------
    // 생성자
    // -------------------------------------------------------------------------

    /**
     * CustomUserDetails 생성자
     *
     * <p>JWT 클레임에서 추출한 값으로 생성합니다. athIds가 null 또는 빈 리스트이면 일반사용자(ITPZZ001) 기본값을 적용합니다.
     *
     * @param eno 사번
     * @param athIds 자격등급 ID 목록 (null/빈 리스트이면 ITPZZ001 기본값)
     * @param bbrC 소속 부서코드
     */
    public CustomUserDetails(String eno, List<String> athIds, String bbrC) {
        this.eno = eno;
        this.bbrC = bbrC;

        // null/빈 리스트 방어: 미등록 사용자에게 일반사용자 기본값 적용
        this.athIds =
                (athIds != null && !athIds.isEmpty()) ? List.copyOf(athIds) : List.of(ATH_USER);

        // 모든 자격등급에 대응하는 Role을 authorities에 등록 (중복 제거)
        this.authorities =
                this.athIds.stream()
                        .map(id -> new SimpleGrantedAuthority(mapRole(id)))
                        .distinct()
                        .collect(Collectors.toUnmodifiableList());
    }

    // -------------------------------------------------------------------------
    // 권한 확인 편의 메서드
    // -------------------------------------------------------------------------

    /**
     * 시스템관리자 여부 athIds에 ITPAD001이 포함된 경우 true
     *
     * @return 시스템관리자이면 true
     */
    public boolean isAdmin() {
        return athIds.contains(ATH_ADMIN);
    }

    /**
     * 정보보호관리자 여부 (PRD_c_20260620 #3) athIds에 ITPAD002가 포함된 경우 true
     *
     * @return 정보보호관리자이면 true
     */
    public boolean isInfoSecAdmin() {
        return athIds.contains(ATH_INFOSEC_ADMIN);
    }

    /**
     * 협의회 개최준비 관리자 여부 (PRD_c_20260620 #3)
     *
     * <p>IT관리자(ITPAD001) 또는 정보보호관리자(ITPAD002)이면 true. 협의회 개최준비/위원선정 등 관리 액션의 공통 권한 판정에 사용합니다. 단,
     * 심의유형(dbrTc)별 가능 범위 제한은 서비스 계층에서 별도 적용합니다.
     *
     * @return 협의회 관리 권한이 있으면 true
     */
    public boolean isCouncilManager() {
        return isAdmin() || isInfoSecAdmin();
    }

    /**
     * 부서 관리 권한 여부
     *
     * <p>ITPZZ002(기획통할담당자) 또는 ITPAD001(시스템관리자) 포함 시 true 관리자는 부서 관리 권한도 포함합니다.
     *
     * @return 부서 관리 권한이 있으면 true
     */
    public boolean isDeptManager() {
        return athIds.contains(ATH_DEPT_MGR) || isAdmin();
    }

    /**
     * 특정 자격등급 보유 여부 확인
     *
     * @param athId 확인할 자격등급 ID
     * @return 해당 자격등급을 보유하면 true
     */
    public boolean hasAthId(String athId) {
        return athIds.contains(athId);
    }

    // -------------------------------------------------------------------------
    // UserDetails 인터페이스 필수 구현 (Stateless JWT 방식)
    // -------------------------------------------------------------------------

    @Override
    public String getUsername() {
        return eno;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    // -------------------------------------------------------------------------
    // 내부 헬퍼
    // -------------------------------------------------------------------------

    /**
     * 자격등급 ID → Spring Security Role 변환
     *
     * @param athId 자격등급 ID
     * @return Spring Security Role 문자열 (ROLE_ 접두사 포함)
     */
    private String mapRole(String athId) {
        return switch (athId) {
            case ATH_ADMIN -> ROLE_ADMIN;
            case ATH_INFOSEC_ADMIN -> ROLE_INFOSEC_ADMIN;
            case ATH_DEPT_MGR -> ROLE_DEPT_MANAGER;
            default -> ROLE_USER;
        };
    }
}
