package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 예산 첨부 삭제의 부서 권한을 판정합니다. 파일 연결 변경 권한에는 적용하지 않습니다. */
@Component
@RequiredArgsConstructor
public class BudgetFileDeleteAuthorizer {

    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;

    /**
     * 활성 최종 예산 원장의 주관부서와 현재 사용자의 소속부서가 같은지 판정합니다.
     *
     * <p>정보화사업에는 경상사업이, 전산업무비에는 금융단말 첨부가 포함됩니다. 첨부의 부모 키에는 개정 순번이 없으므로 기존 첨부 조회·업로드와 같이 관리번호의 활성
     * 최종본을 기준으로 합니다. 일괄 삭제에서도 부모당 한 번만 호출하여 파일 수에 비례한 부모 조회를 피합니다.
     *
     * @param kind 저장된 첨부 종류
     * @param parentId 저장된 예산 관리번호
     * @param user 현재 인증 사용자
     * @return 예산 종류이고 활성 부모의 주관부서가 일치하면 true; 누락·다른 종류는 false
     */
    public boolean canDeleteForDepartment(String kind, String parentId, CustomUserDetails user) {
        if (user == null
                || !StringUtils.hasText(user.getBbrC())
                || !StringUtils.hasText(parentId)) {
            return false;
        }
        if (ProjectFileReadAuthorizer.PROJECT_KIND.equals(kind)) {
            return projectRepository
                    .findByAbusMngNoAndDelYn(parentId, "N")
                    .map(project -> user.getBbrC().equals(project.getSvnDpmC()))
                    .orElse(false);
        }
        if (CostFileReadAuthorizer.COST_KIND.equals(kind)) {
            return costRepository
                    .findByCostBgNoAndLstYnAndDelYn(parentId, "Y", "N")
                    .map(cost -> user.getBbrC().equals(cost.getCostSvnDpmC()))
                    .orElse(false);
        }
        return false;
    }
}
