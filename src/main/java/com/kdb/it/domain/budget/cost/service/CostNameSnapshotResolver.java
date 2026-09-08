package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.util.UserNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 전산업무비·금융정보단말기의 담당자 이름과 소속 조직 스냅샷을 해석합니다.
 *
 * <p>원장과 단말기 저장 경로가 같은 규칙으로 스냅샷 컬럼을 채우도록 해석 지점을 한곳에 둡니다. 해석에 실패하면 null을 돌려주고, 호출자는 그 경우 기존 스냅샷을
 * 유지합니다(BE-63).
 */
@Component
@RequiredArgsConstructor
public class CostNameSnapshotResolver {

    private final UserRepository cuserIRepository;

    /**
     * 담당자 표시명을 해석합니다.
     *
     * @param cgprId 담당자 컬럼 저장값 — 사번 또는 이름. null·공백이면 해석하지 않습니다
     * @return 표시명. 해석 실패 시 null
     */
    public String resolveCgprName(String cgprId) {
        if (cgprId == null || cgprId.isBlank()) {
            return null;
        }
        String lookedUp =
                cuserIRepository.findByEno(cgprId).map(user -> user.getUsrNm()).orElse(null);
        return UserNameResolver.resolve(cgprId, lookedUp);
    }

    /**
     * 담당자 사번으로 소속 팀명과 상위 조직명을 조회합니다.
     *
     * @param cgprId 담당자 사번. null·공백이거나 미등록이면 {@link AuthorOrg#EMPTY}
     * @return 팀명·상위 조직명 스냅샷
     */
    public AuthorOrg resolveAuthorOrgNames(String cgprId) {
        if (cgprId == null || cgprId.isBlank()) {
            return AuthorOrg.EMPTY;
        }
        return cuserIRepository
                .findByEno(cgprId)
                .map(user -> new AuthorOrg(user.getTemNm(), user.getPrlmHrkOgzCNm()))
                .orElse(AuthorOrg.EMPTY);
    }

    /**
     * 요청이 보낸 스냅샷이 있으면 그 값을, 없으면 해석 결과를 씁니다.
     *
     * @param snapshot 요청의 스냅샷 값 (공백이면 무시)
     * @param resolved 서버가 해석한 값
     * @return 공백을 제거한 스냅샷 또는 해석 결과
     */
    public static String snapshotOrResolved(String snapshot, String resolved) {
        return StringUtils.hasText(snapshot) ? snapshot.trim() : resolved;
    }

    /**
     * 담당자 소속 조직 스냅샷.
     *
     * @param svnTemNm 담당팀명 (CUSERI.TEM_NM)
     * @param prlmHrkOgzCNm 상위 조직명 (CUSERI.PRLM_HRK_OGZ_C_NM)
     */
    public record AuthorOrg(String svnTemNm, String prlmHrkOgzCNm) {
        /** 담당자 미지정·미조회 시 사용하는 빈 스냅샷 */
        public static final AuthorOrg EMPTY = new AuthorOrg(null, null);
    }
}
