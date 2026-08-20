package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 배너 이미지 읽기 판정기 — 전사 공개(인증 사용자 전체).
 *
 * <p>배너는 /info 홈 캐러셀에 모든 사용자에게 노출되는 이미지이므로 인증된 사용자에게 읽기를 허용한다. (default-deny의 명시적 예외)
 */
@Component
public class BannerFileReadAuthorizer implements FileReadAuthorizer {

    /** 배너 파일 종류(PK_COL_NM). */
    public static final String BANNER_KIND = "배너";

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of(BANNER_KIND);
    }

    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return user != null; // 전사 공개: 인증 사용자 전체
    }
}
