package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 신규 첨부 대상 종류별 쓰기 판정기를 수집하는 레지스트리입니다.
 *
 * <p>등록된 종류는 부모 존재와 작성 권한을 강제합니다. 아직 부모 규칙이 연결되지 않은 레거시 종류는 기존 업로드 동작을 보존합니다.
 */
@Component
public class FileTargetWriteAuthorizerRegistry {

    private final Map<String, FileTargetWriteAuthorizer> byKind;

    public FileTargetWriteAuthorizerRegistry(List<FileTargetWriteAuthorizer> authorizers) {
        Map<String, FileTargetWriteAuthorizer> map = new HashMap<>();
        for (FileTargetWriteAuthorizer authorizer : authorizers) {
            for (String kind : authorizer.supportedPkColNms()) {
                FileTargetWriteAuthorizer previous = map.put(kind, authorizer);
                if (previous != null) {
                    throw new IllegalStateException("첨부 대상 쓰기 authorizer 중복 등록: " + kind);
                }
            }
        }
        this.byKind = Map.copyOf(map);
    }

    /**
     * 등록된 종류의 첨부 대상 쓰기 권한을 검증합니다.
     *
     * @param pkColNm 파일 종류
     * @param pkCone 부모 식별자
     * @param user 현재 사용자
     * @throws AccessDeniedException 등록 종류의 부모가 없거나 쓰기 권한이 없는 경우
     */
    public void verifyTargetWriteAccess(String pkColNm, String pkCone, CustomUserDetails user) {
        FileTargetWriteAuthorizer authorizer = pkColNm == null ? null : byKind.get(pkColNm);
        if (authorizer != null && !authorizer.canWrite(pkCone, user)) {
            throw new AccessDeniedException("첨부 대상 쓰기 권한이 없습니다.");
        }
    }

    /**
     * 파일 종류가 generic 수정·삭제를 허용하는지 검증합니다.
     *
     * @param pkColNm 현재 또는 변경 후 파일 종류
     * @throws AccessDeniedException 전용 writer만 관리할 수 있는 종류인 경우
     */
    public void verifyGenericMutationAllowed(String pkColNm) {
        FileTargetWriteAuthorizer authorizer = pkColNm == null ? null : byKind.get(pkColNm);
        if (authorizer != null && !authorizer.allowsGenericMutation()) {
            throw new AccessDeniedException("보호된 파일 종류는 generic 파일 API로 변경할 수 없습니다: " + pkColNm);
        }
    }
}
