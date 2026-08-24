package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 요구사항정의서 첨부 읽기 판정기 — 관리자 OR 작성자 OR 주관부서.
 *
 * <p>부모 문서({@code Brdocm})의 최신 버전을 조회해 작성자({@code FST_ENR_USID}) 또는 주관부서({@code SVN_DPM_C})와 현재
 * 사용자를 비교한다. {@code Brdocm}은 복합키(DOC_MNG_NO, DOC_VRS_SNO) 버전 엔티티이므로 최신 버전 로더로 조회한다.
 */
@Component
@RequiredArgsConstructor
public class RequirementDocFileReadAuthorizer implements FileReadAuthorizer {

    private final ServiceRequestDocRepository serviceRequestDocRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of("요구사항정의서");
    }

    /**
     * 요구사항정의서 파일 읽기 가능 여부.
     *
     * @param file 대상 파일(부모 문서관리번호는 {@code APG_FL_LNK_CTZ_NM} 값)
     * @param user 현재 사용자(null이면 비인증 → 불가)
     * @return 관리자이거나, 최신 문서의 작성자(FST_ENR_USID) 또는 주관부서(SVN_DPM_C)가 현재 사용자와 일치하면 true. 부모 ID가 없거나
     *     문서를 찾지 못하면 false
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (!StringUtils.hasText(file.getApgFlLnkCtzNm())) {
            return false;
        }
        return serviceRequestDocRepository
                .findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc(file.getApgFlLnkCtzNm(), "N")
                .map(
                        doc ->
                                user.getEno().equals(doc.getFstEnrUsid())
                                        || (user.getBbrC() != null
                                                && user.getBbrC().equals(doc.getSvnDpmC())))
                .orElse(false);
    }
}
