package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.Set;

/** 신규 첨부 대상 종류별 부모 쓰기 권한 판정기입니다. */
public interface FileTargetWriteAuthorizer {

    /** 이 판정기가 담당하는 파일 종류(APG_FL_KD_NM) 집합입니다. */
    Set<String> supportedApgFlKdNms();

    /**
     * generic 파일 API의 연결 수정 허용 여부와 별도 재정의가 없는 삭제의 기본 정책을 반환합니다.
     *
     * @return 기존 종류의 동작을 보존하려면 {@code true}, 전용 writer만 변경할 수 있으면 {@code false}
     */
    default boolean allowsGenericMutation() {
        return true;
    }

    /**
     * 범용 삭제 허용 여부를 반환합니다. 별도 허용이 없으면 기존 수정·삭제 제한을 유지합니다.
     *
     * @return 소유권·부서 권한 검사를 통과한 파일을 범용 API로 삭제할 수 있으면 true
     */
    default boolean allowsGenericDeletion() {
        return allowsGenericMutation();
    }

    /**
     * 첨부 대상 부모에 파일을 연결할 수 있는지 판정합니다.
     *
     * @param apgFlLnkCtzNm 부모 식별자
     * @param user 현재 사용자
     * @return 활성 부모가 존재하고 쓰기 권한이 있으면 {@code true}
     */
    boolean canWrite(String apgFlLnkCtzNm, CustomUserDetails user);
}
