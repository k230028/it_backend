package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 협의회 연계 파일(사업계획서·타당성검토표·협의회관련자료) 읽기 판정기.
 *
 * <p>세 종류 모두 {@code APG_FL_LNK_CTZ_NM}가 협의회ID({@code IT_PTL_ASCT_ID})이며, 규칙은 다음 중 하나를 만족하면 읽기 허용이다.
 *
 * <ul>
 *   <li>관리자 또는 정보보안관리자
 *   <li>해당 협의회의 위원(BCMMTM에 사번 존재)
 *   <li>협의회 사업(BPROJM)의 주관부서({@code SVN_DPM_C})가 사용자 부서({@code bbrC})와 동일
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CouncilFileReadAuthorizer implements FileReadAuthorizer {

    private final CouncilRepository councilRepository;
    private final CommitteeRepository committeeRepository;
    private final ProjectRepository projectRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of("사업계획서", "타당성검토표", "협의회관련자료");
    }

    /**
     * 협의회 연계 파일 읽기 가능 여부.
     *
     * @param file 대상 파일(부모 협의회 ID는 {@code APG_FL_LNK_CTZ_NM} 값)
     * @param user 현재 사용자(null이면 비인증 → 불가)
     * @return 관리자·정보보안관리자이거나, 해당 협의회 위원이거나, 협의회 사업의 주관부서가 사용자 부서와 일치하면 true. 부모 ID가 없거나 협의회·사업을 찾지
     *     못하면 false
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin() || user.isInfoSecAdmin()) {
            return true;
        }
        String asctId = file.getApgFlLnkCtzNm();
        if (!StringUtils.hasText(asctId)) {
            return false;
        }
        // 해당 협의회 위원 여부
        if (committeeRepository
                .findByItPtlAsctIdAndEnoAndDelYn(asctId, user.getEno(), "N")
                .isPresent()) {
            return true;
        }
        // 관련 부서: 협의회 → 사업(BPROJM) 주관부서와 사용자 부서 비교
        return councilRepository
                .findByItPtlAsctIdAndDelYn(asctId, "N")
                .flatMap(
                        council ->
                                projectRepository.findById(
                                        new BprojmId(council.getAbusMngNo(), council.getSno())))
                .map(
                        project ->
                                user.getBbrC() != null
                                        && user.getBbrC().equals(project.getSvnDpmC()))
                .orElse(false);
    }
}
