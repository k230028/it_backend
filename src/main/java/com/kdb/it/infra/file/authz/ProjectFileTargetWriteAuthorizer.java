package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 정보화사업·경상사업 첨부 대상 쓰기 판정기 — 사업 수정 권한자만 허용합니다.
 *
 * <p>판정 규칙은 사업 본문 수정({@code ProjectService.updateProject})이 쓰는 {@code
 * OwnershipVerifier.verifyModifiable}과 같습니다: 관리자, 최초 작성자, 주관부서가 같은 사용자. 첨부만 다른 규칙을 쓰면 본문은 못 고치는
 * 사용자가 첨부는 바꿀 수 있게 되므로 규칙을 일치시킵니다.
 */
@Component
@RequiredArgsConstructor
public class ProjectFileTargetWriteAuthorizer implements FileTargetWriteAuthorizer {

    private final ProjectRepository projectRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(ProjectFileReadAuthorizer.PROJECT_KIND);
    }

    @Override
    public boolean allowsGenericMutation() {
        return false;
    }

    /**
     * 정보화사업 첨부 대상 쓰기 가능 여부를 판정합니다.
     *
     * @param apgFlLnkCtzNm 사업관리번호
     * @param user 현재 사용자
     * @return 삭제되지 않은 사업이 있고 관리자·최초 작성자·주관부서 사용자 중 하나이면 {@code true}
     */
    @Override
    public boolean canWrite(String apgFlLnkCtzNm, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(apgFlLnkCtzNm)) {
            return false;
        }
        return projectRepository
                .findByAbusMngNoAndDelYn(apgFlLnkCtzNm, "N")
                .map(
                        project ->
                                OwnershipVerifier.canModify(
                                        project.getFstEnrUsid(), project.getSvnDpmC(), user))
                .orElse(false);
    }
}
