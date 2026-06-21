package com.kdb.it.common.system.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * 소유권/권한 공통 검증 유틸.
 *
 * <p>업무 도메인 쓰기 경로(수정·삭제·상태전이 등)에서 "본인 또는 관리자만 허용" 규칙을
 * 단일 지점으로 강제합니다. 클래스 레벨 {@code @PreAuthorize}가 없는 업무 컨트롤러는
 * 서비스 계층에서 본 유틸로 소유권을 검증해야 합니다(it_backend/CLAUDE.md §5.18 보안 규칙).</p>
 *
 * <p>실패 시 {@link AccessDeniedException}을 던지며, {@code GlobalExceptionHandler}가 403으로 매핑합니다.</p>
 */
public final class OwnershipVerifier {

    private OwnershipVerifier() {
    }

    /**
     * 대상 리소스의 소유자(최초등록자) 본인 또는 시스템관리자인지 검증합니다.
     *
     * @param ownerEno 리소스 소유자 사번(예: {@code FST_ENR_USID}). null이면 소유자 불일치로 간주.
     * @param user     현재 인증 사용자. null이면 거부.
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
}
