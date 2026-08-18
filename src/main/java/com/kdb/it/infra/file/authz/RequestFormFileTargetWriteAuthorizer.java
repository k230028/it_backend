package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.migration.request.service.RequestFormSourceFileArchiver;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 편성요청서 반입 원본은 generic 파일 API에서 새로 연결할 수 없도록 항상 거부합니다. */
@Component
public class RequestFormFileTargetWriteAuthorizer implements FileTargetWriteAuthorizer {

    /** 편성요청서 반입 원본 파일 종류만 담당합니다. */
    @Override
    public Set<String> supportedPkColNms() {
        return Set.of(RequestFormSourceFileArchiver.PK_COL_NM);
    }

    /** 공식 반입 원본의 기존 행도 importer 외 generic 경로에서는 변경할 수 없습니다. */
    @Override
    public boolean allowsGenericMutation() {
        return false;
    }

    /**
     * generic 파일 API의 대상 쓰기를 거부합니다.
     *
     * <p>공식 원본은 {@link RequestFormSourceFileArchiver}만 만들며, 이 내부 경로는 컨트롤러의 대상 쓰기 레지스트리를 거치지 않습니다.
     *
     * @param pkCone 부모 신청서번호
     * @param user 현재 사용자
     * @return 사용자와 부모에 관계없이 {@code false}
     */
    @Override
    public boolean canWrite(String pkCone, CustomUserDetails user) {
        return false;
    }
}
