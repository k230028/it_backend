package com.kdb.it.domain.budget.common.security;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;

/** 예산 상세·이력·재신청의 부서 데이터 범위를 검증합니다. */
public final class BudgetDetailAccessVerifier {

    private static final Set<String> IT_ORGANIZATION_CODES =
            Set.of("013", "180", "181", "182", "183", "185");

    private BudgetDetailAccessVerifier() {}

    /**
     * 대상 예산을 인증 사용자가 읽을 수 있는지 검증합니다.
     *
     * <p>시스템관리자, IT 조직 사용자, 대상 주관부서와 동일한 사용자를 허용합니다. 그 밖의 사용자와 인증 정보가 없는 호출은 거부합니다.
     *
     * @param resourceDepartmentCode 대상 예산의 주관부서 코드
     * @param actor 인증된 사용자
     * @throws AccessDeniedException 접근 범위를 충족하지 못한 경우
     */
    public static void verifyReadable(String resourceDepartmentCode, CustomUserDetails actor) {
        if (!isReadable(resourceDepartmentCode, actor)) {
            throw new AccessDeniedException("예산 상세 조회 권한이 없습니다.");
        }
    }

    /** 대상 부서가 인증 사용자의 예산 조회 범위에 포함되는지 반환합니다. */
    public static boolean isReadable(String resourceDepartmentCode, CustomUserDetails actor) {
        if (actor == null) {
            return false;
        }
        // Set.of(...)는 불변 집합이라 contains(null)이 NullPointerException을 던진다.
        // SSO 미동기화 등으로 부점코드가 비어 있는 계정은 500이 아니라 권한 없음으로 처리한다.
        String actorDepartmentCode = actor.getBbrC();
        if (actor.isAdmin()
                || (actorDepartmentCode != null
                        && IT_ORGANIZATION_CODES.contains(actorDepartmentCode))) {
            return true;
        }
        if (StringUtils.hasText(resourceDepartmentCode)
                && resourceDepartmentCode.equals(actor.getBbrC())) {
            return true;
        }
        return false;
    }

    /** 목록과 건수 조회에서 전체 부서 범위를 사용할 수 있는지 반환합니다. */
    public static boolean canReadAllDepartments(CustomUserDetails actor) {
        if (actor == null) {
            return false;
        }
        String actorDepartmentCode = actor.getBbrC();
        return actor.isAdmin()
                || (actorDepartmentCode != null && IT_ORGANIZATION_CODES.contains(actorDepartmentCode));
    }
}
