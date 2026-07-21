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

        boolean createdByUser = Objects.equals(creatorEno, user.getEno());
        boolean sameDepartmentManager =
                user.isDeptManager()
                        && StringUtils.hasText(resourceBbrC)
                        && Objects.equals(resourceBbrC, user.getBbrC());
        if (user.isAdmin() || createdByUser || sameDepartmentManager) {
            return;
        }

        throw new AccessDeniedException("수정 권한이 없습니다.");
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
}
