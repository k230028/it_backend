package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;

/**
 * 파일 종류(PK_COL_NM)별 읽기 권한 판정기.
 *
 * <p>각 구현은 담당 종류 집합({@link #supportedPkColNms()})을 선언하고, 해당 종류 파일의 부모 자원 읽기 권한을 재사용해 읽기 가능 여부를
 * 판정한다.
 *
 * <p><b>불변식:</b> 읽기 판정은 {@code (PK_COL_NM, PK_CONE, user)}의 순수 함수다. 판정은 파일 종류·부모 식별자와 사용자만으로 결정되며
 * 개별 파일의 다른 속성에는 의존하지 않는다. 따라서 같은 {@code (종류, 부모)}를 가리키는 파일은 언제나 동일한 판정을 받는다. 목록 조회는 이 불변식에 기대어 서로
 * 다른 {@code (PK_COL_NM, PK_CONE)} 조합마다 한 번만 판정하고 그 결과를 같은 조합의 모든 파일에 재사용한다({@code
 * FileService.getFiles}의 요청 범위 캐시). 신규 구현도 이 불변식을 반드시 유지해야 한다.
 */
public interface FileReadAuthorizer {

    /** 이 판정기가 담당하는 파일 종류(PK_COL_NM) 집합. */
    Set<String> supportedPkColNms();

    /**
     * 읽기 가능 여부.
     *
     * @param file 대상 파일
     * @param user 현재 사용자(null이면 비인증)
     * @return 읽기 가능하면 true
     */
    boolean canRead(Cfilem file, CustomUserDetails user);
}
