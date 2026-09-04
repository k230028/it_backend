package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 다이어그램 파일 읽기 판정기 — 사용 화면과 부서에 관계없이 인증 사용자에게 공개합니다. */
@Component
public class DiagramFileReadAuthorizer implements FileReadAuthorizer {

    /** Excalidraw 장면과 장면 내부 이미지에 사용하는 공통 파일 종류입니다. */
    public static final String DIAGRAM_KIND = "다이어그램";

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(DIAGRAM_KIND);
    }

    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return user != null;
    }
}
