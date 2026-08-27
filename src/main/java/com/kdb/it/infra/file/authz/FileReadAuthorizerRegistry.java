package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 파일 종류별 {@link FileReadAuthorizer}를 수집·조회하는 레지스트리.
 *
 * <p>등록되지 않은 종류는 관리자만 허용한다(default-deny). 신규 종류 추가 시 authorizer 등록을 강제한다.
 */
@Component
public class FileReadAuthorizerRegistry {

    private final Map<String, FileReadAuthorizer> byKind;

    public FileReadAuthorizerRegistry(List<FileReadAuthorizer> authorizers) {
        Map<String, FileReadAuthorizer> map = new HashMap<>();
        for (FileReadAuthorizer authorizer : authorizers) {
            for (String kind : authorizer.supportedApgFlKdNms()) {
                FileReadAuthorizer previous = map.put(kind, authorizer);
                if (previous != null) {
                    throw new IllegalStateException("파일 종류 authorizer 중복 등록: " + kind);
                }
            }
        }
        this.byKind = Map.copyOf(map);
    }

    /**
     * 파일 읽기 가능 여부. 미등록 종류는 관리자만 허용한다(default-deny).
     *
     * <p>종류(APG_FL_KD_NM)가 null이면 조회 전에 미등록으로 간주해 관리자만 허용한다. ({@code byKind}는 {@link Map#copyOf}로
     * 만든 불변 맵이라 {@code get(null)}이 {@link NullPointerException}을 던지므로, null 종류는 반드시 조회 전에 처리한다.)
     */
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        String kind = file.getApgFlKdNm();
        FileReadAuthorizer authorizer = (kind == null) ? null : byKind.get(kind);
        if (authorizer == null) {
            return user != null && user.isAdmin();
        }
        return authorizer.canRead(file, user);
    }
}
