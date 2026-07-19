package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 가이드문서 첨부 읽기 판정기 — 전사 공개(인증 사용자 전체).
 *
 * <p>가이드는 전 직원 참고 자료이므로 인증된 사용자에게 읽기를 허용한다.
 * (default-deny의 명시적 예외)</p>
 */
@Component
public class GuideDocFileReadAuthorizer implements FileReadAuthorizer {

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of("가이드문서");
    }

    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return user != null; // 전사 공개: 인증 사용자 전체
    }
}
