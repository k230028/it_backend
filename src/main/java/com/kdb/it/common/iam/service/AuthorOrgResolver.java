package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 로그인 사용자(작성자) 소속 조직 스냅샷 해석기.
 *
 * <p>
 * JWT 클레임에는 부서코드({@code bbrC})만 담겨 있고 팀코드/상위조직코드는 없으므로,
 * 사번(eno)으로 {@link com.kdb.it.common.iam.entity.CuserI}를 조회해
 * 주관부서코드/주관팀코드/인사상위조직코드내용을 함께 해석합니다.
 * 신규 마스터 레코드 생성 시 "작성자 기준" 컬럼(예: BPROJM.SVN_TEM_C,
 * BCOSTM.PRLM_HRK_OGZ_C_CONE, BRDOCM.SVN_DPM_C/SVN_TEM_C)을 채우는 데 사용합니다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class AuthorOrgResolver {

    /** 사용자(CuserI) 리포지토리: 사번→소속 조직 스냅샷 조회용 (organization JOIN FETCH) */
    private final UserRepository userRepository;

    /**
     * 현재 인증 사용자의 소속 조직 스냅샷을 해석합니다.
     *
     * <p>
     * 미인증이거나 사용자 정보를 찾지 못하면 모든 필드가 {@code null}인
     * {@link AuthorOrg#empty()}를 반환합니다(대상 컬럼은 모두 nullable).
     * </p>
     *
     * @return 작성자 소속 조직 스냅샷 (미인증·미조회 시 빈 스냅샷)
     */
    @Transactional(readOnly = true)
    public AuthorOrg resolveCurrent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return AuthorOrg.empty();
        }
        String eno = auth.getName();
        if (eno == null || eno.isBlank()) {
            return AuthorOrg.empty();
        }
        return userRepository.findByEno(eno)
                .map(user -> new AuthorOrg(user.getBbrC(), user.getTemC(), user.getPrlmHrkOgzCCone()))
                .orElse(AuthorOrg.empty());
    }
}
