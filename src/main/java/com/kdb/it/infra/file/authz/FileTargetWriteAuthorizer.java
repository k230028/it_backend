package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.Set;

/** 신규 첨부 대상 종류별 부모 쓰기 권한 판정기입니다. */
public interface FileTargetWriteAuthorizer {

    /** 이 판정기가 담당하는 파일 종류(PK_COL_NM) 집합입니다. */
    Set<String> supportedPkColNms();

    /**
     * generic 파일 API가 이 종류의 기존 행을 수정·삭제할 수 있는지 반환합니다.
     *
     * @return 기존 종류의 동작을 보존하려면 {@code true}, 전용 writer만 변경할 수 있으면 {@code false}
     */
    default boolean allowsGenericMutation() {
        return true;
    }

    /**
     * 첨부 대상 부모에 파일을 연결할 수 있는지 판정합니다.
     *
     * @param pkCone 부모 식별자
     * @param user 현재 사용자
     * @return 활성 부모가 존재하고 쓰기 권한이 있으면 {@code true}
     */
    boolean canWrite(String pkCone, CustomUserDetails user);
}
