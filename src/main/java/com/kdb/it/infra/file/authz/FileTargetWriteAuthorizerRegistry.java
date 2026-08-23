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
 * <p>등록된 종류는 부모 존재와 작성 권한을 강제합니다. 전용 writer가 없는 종류는 기존 업로드 동작을 보존하되, {@link FileKindRegistry}가 아는
 * 종류여야 합니다 — 그 두 가지는 다른 질문입니다(부모 권한을 누가 판정하는가 / 이 종류를 아는가).
 */
@Component
public class FileTargetWriteAuthorizerRegistry {

    private final Map<String, FileTargetWriteAuthorizer> byKind;
    private final FileKindRegistry kindRegistry;

    public FileTargetWriteAuthorizerRegistry(
            List<FileTargetWriteAuthorizer> authorizers, FileKindRegistry kindRegistry) {
        this.kindRegistry = kindRegistry;
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
     * 첨부 대상 쓰기 권한을 검증합니다.
     *
     * <p>먼저 <b>아는 종류인지</b>를 봅니다(SEC-14). 이 검사가 없으면 클라이언트가 임의의 새 종류 이름으로 첨부를 만들 수 있고, 그 종류에는 아래 부모
     * 권한 검사가 아예 붙지 않습니다. {@code pkColNm}이 {@code null}이면 이 검사를 건너뜁니다 — 메타 수정에서 종류를 바꾸지 않는다는 뜻이기
     * 때문입니다(업로드 경로는 {@code @RequestPart}가 필수로 강제합니다).
     *
     * @param pkColNm 파일 종류. 메타 수정에서 종류를 바꾸지 않으면 null
     * @param pkCone 부모 식별자
     * @param user 현재 사용자
     * @throws AccessDeniedException 알 수 없는 종류이거나, 등록 종류의 부모가 없거나 쓰기 권한이 없는 경우
     */
    public void verifyTargetWriteAccess(String pkColNm, String pkCone, CustomUserDetails user) {
        if (pkColNm != null && !kindRegistry.isKnown(pkColNm)) {
            // 값 자체는 응답에 싣지 않는다 — 클라이언트가 통제하는 문자열이다.
            throw new AccessDeniedException("알 수 없는 파일 종류입니다.");
        }
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
