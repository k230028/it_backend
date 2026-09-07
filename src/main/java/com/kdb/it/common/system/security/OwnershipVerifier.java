package com.kdb.it.common.system.security;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * 소유권/권한 공통 검증 유틸.
 *
 * <p>업무 도메인 쓰기 경로(수정·삭제·상태전이 등)에서 "본인 또는 관리자만 허용" 규칙을 단일 지점으로 강제합니다. 클래스 레벨 {@code @PreAuthorize}가
 * 없는 업무 컨트롤러는 서비스 계층에서 본 유틸로 소유권을 검증해야 합니다.
 *
 * <p>실패 시 {@link AccessDeniedException}을 던지며, {@code GlobalExceptionHandler}가 403으로 매핑합니다.
 */
public final class OwnershipVerifier {

    private OwnershipVerifier() {}

    /**
     * 대상 리소스의 소유자(최초등록자) 본인 또는 시스템관리자인지 검증합니다.
     *
     * @param ownerEno 리소스 소유자 사번(예: {@code FST_ENR_USID}). null이면 소유자 불일치로 간주.
     * @param user 현재 인증 사용자. null이면 거부.
     * @throws AccessDeniedException 본인도 관리자도 아닌 경우(또는 user가 null)
     */
    public static void verifyOwnerOrAdmin(String ownerEno, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (user.isAdmin()) {
            return;
        }
        if (ownerEno != null && ownerEno.equals(user.getEno())) {
            return;
        }
        throw new AccessDeniedException("본인 또는 관리자만 수행할 수 있습니다.");
    }

    /**
     * 현재 인증 사용자가 대상 리소스를 수정할 수 있는지 검증합니다.
     *
     * @param creatorEno 리소스 최초 작성자 사번
     * @param resourceBbrC 리소스 소속 부서코드
     * @throws AccessDeniedException 인증 정보가 없거나 수정 권한이 없는 경우
     */
    public static void verifyModifiable(String creatorEno, String resourceBbrC) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            throw new AccessDeniedException("인증 정보가 없어 수정할 수 없습니다.");
        }

        if (canModify(creatorEno, resourceBbrC, user)) {
            return;
        }

        throw new AccessDeniedException("수정 권한이 없습니다.");
    }

    /**
     * 명시적으로 전달된 사용자 기준으로 수정 가능 여부를 판정합니다.
     *
     * <p>판정 규칙은 최초 작성자 본인, 같은 부서의 부서관리자, 시스템관리자입니다. 서비스·권한자처럼 SecurityContext 밖에서 같은 규칙이 필요할 때
     * 사용합니다.
     *
     * @param creatorEno 리소스 최초 작성자 사번
     * @param resourceBbrC 리소스 소속 부서코드
     * @param user 판정할 사용자
     * @return 수정 가능하면 {@code true}
     */
    public static boolean canModify(
            String creatorEno, String resourceBbrC, CustomUserDetails user) {
        if (user == null) {
            return false;
        }

        boolean createdByUser = Objects.equals(creatorEno, user.getEno());
        boolean sameDepartmentManager =
                user.isDeptManager()
                        && StringUtils.hasText(resourceBbrC)
                        && Objects.equals(resourceBbrC, user.getBbrC());
        return user.isAdmin() || createdByUser || sameDepartmentManager;
    }

    /**
     * 현재 인증 사용자가 대상 부서의 문서를 삭제할 수 있는지 검증합니다.
     *
     * <p>예산 원천 문서 삭제는 작성자 개인이 아니라 작성부서에 귀속되므로 같은 부서 사용자 또는 시스템관리자에게 허용합니다.
     *
     * @param resourceBbrC 삭제 대상 문서의 작성부서코드
     * @throws AccessDeniedException 인증 정보가 없거나 다른 부서의 일반 사용자인 경우
     */
    public static void verifySameDepartmentOrAdmin(String resourceBbrC) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            throw new AccessDeniedException("인증 정보가 없어 삭제할 수 없습니다.");
        }

        if (user.isAdmin()
                || (StringUtils.hasText(resourceBbrC)
                        && Objects.equals(resourceBbrC, user.getBbrC()))) {
            return;
        }

        throw new AccessDeniedException("같은 부서 사용자 또는 시스템관리자만 삭제할 수 있습니다.");
    }

    /**
     * 현재 인증 사용자가 시스템관리자인지 확인합니다.
     *
     * <p>거부가 아니라 <b>분기</b>가 필요한 규칙에서 사용합니다(예: 결재완료 문서를 관리자만 사후 정정할 수 있게 여는 예외). 접근 자체를 막아야 하면
     * {@link #verifyAdmin(CustomUserDetails)}를 사용합니다.
     *
     * @return 인증 사용자가 시스템관리자이면 true. 인증 정보가 없으면 false.
     */
    public static boolean isCurrentUserAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails user
                && user.isAdmin();
    }

    /**
     * 시스템관리자(ADMIN) 전용 작업 검증. ADMIN이 아니면 {@link AccessDeniedException}.
     *
     * @param user 현재 인증 사용자
     * @throws AccessDeniedException 인증 정보가 없거나 ADMIN이 아닌 경우
     */
    public static void verifyAdmin(CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (!user.isAdmin()) {
            throw new AccessDeniedException("관리자만 수행할 수 있습니다.");
        }
    }

    /**
     * 현재 인증 주체의 사번(로그인 ID)을 반환합니다.
     *
     * <p>{@link Authentication#getName()}을 그대로 쓰므로 principal이 {@link CustomUserDetails}가 아니어도(예:
     * 배치·이관 컨텍스트) 동작합니다. 작성완료 신청서 스탬프처럼 "인증된 이름을 그대로 저장"해야 하는 용도에 씁니다 — 소유권 판정처럼 {@link
     * CustomUserDetails}로 캐스팅해 역할을 확인해야 하는 경우는 이 메서드로 대체할 수 없습니다.
     *
     * @return 인증 주체 이름(사번). 인증 정보가 없으면 {@code null}
     */
    public static String currentEno() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
