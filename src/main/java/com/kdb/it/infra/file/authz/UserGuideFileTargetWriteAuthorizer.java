package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 사용자가이드 첨부 대상 쓰기 판정기 — 관리자만 허용합니다.
 *
 * <p>사용자가이드는 도메인 부모 레코드가 없고 {@code APG_FL_LNK_CTZ_NM}이 노출 위치({@code HEADER} 고정)를 담는다. 따라서 부모 존재 검사
 * 대신 위치 값이 비어 있지 않은지와 관리자 여부만 판정한다.
 *
 * <p>{@link #allowsGenericMutation()}을 {@code false}로 두어 범용 {@code /api/files} PUT·DELETE가 사용자가이드
 * 행을 변경하지 못하게 막는다. 사용자가이드 관리 창구는 {@code /admin/user-guides}({@code UserGuideService}) 하나다.
 */
@Component
public class UserGuideFileTargetWriteAuthorizer implements FileTargetWriteAuthorizer {

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(UserGuideFileReadAuthorizer.USER_GUIDE_KIND);
    }

    /** 사용자가이드는 전용 API({@code UserGuideService})로만 수정·삭제한다. */
    @Override
    public boolean allowsGenericMutation() {
        return false;
    }

    /**
     * 사용자가이드 노출 위치에 파일을 붙일 수 있는지 판정합니다.
     *
     * @param apgFlLnkCtzNm 사용자가이드 노출 위치 (예: {@code HEADER})
     * @param user 현재 사용자
     * @return 위치 값이 있고 관리자이면 {@code true}
     */
    @Override
    public boolean canWrite(String apgFlLnkCtzNm, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(apgFlLnkCtzNm)) {
            return false;
        }
        return user.isAdmin();
    }
}
