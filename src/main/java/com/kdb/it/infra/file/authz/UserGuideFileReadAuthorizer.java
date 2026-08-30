package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 사용자가이드 첨부 읽기 판정기 — 전사 공개(인증 사용자 전체).
 *
 * <p>사용자가이드는 헤더에서 전 직원이 내려받는 포털 매뉴얼이므로 인증된 사용자에게 읽기를 허용한다.
 * (default-deny의 명시적 예외)
 *
 * <p>이 선언이 {@link FileKindRegistry}가 종류 {@code 사용자가이드}를 아는 유일한 근거이기도 하다. 판정기를
 * 지우면 업로드 자체가 막힌다.
 */
@Component
public class UserGuideFileReadAuthorizer implements FileReadAuthorizer {

    /** 사용자가이드 파일 종류(APG_FL_KD_NM). */
    public static final String USER_GUIDE_KIND = "사용자가이드";

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(USER_GUIDE_KIND);
    }

    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return user != null; // 전사 공개: 인증 사용자 전체
    }
}
