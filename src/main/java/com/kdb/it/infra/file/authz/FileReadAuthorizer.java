package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;

/**
 * 파일 종류(PK_COL_NM)별 읽기 권한 판정기.
 *
 * <p>각 구현은 담당 종류 집합({@link #supportedPkColNms()})을 선언하고,
 * 해당 종류 파일의 부모 자원 읽기 권한을 재사용해 읽기 가능 여부를 판정한다.</p>
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
