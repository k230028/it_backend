package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 전산업무비 첨부 대상 쓰기 판정기 — 현재 최종본의 수정 권한을 재사용합니다. */
@Component
@RequiredArgsConstructor
public class CostFileTargetWriteAuthorizer implements FileTargetWriteAuthorizer {

    private final CostRepository costRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(CostFileReadAuthorizer.COST_KIND);
    }

    @Override
    public boolean allowsGenericMutation() {
        return true;
    }

    /**
     * 전산업무비 첨부 대상 쓰기 가능 여부를 반환합니다.
     *
     * @param parentId 전산업무비코드
     * @param user 현재 사용자
     * @return 현재 최종·미삭제 부모가 있고 관리자·최초 작성자·주관부서 사용자 중 하나이면 true
     */
    @Override
    public boolean canWrite(String parentId, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(parentId)) {
            return false;
        }
        return costRepository
                .findByCostBgNoAndLstYnAndDelYn(parentId, "Y", "N")
                .map(
                        cost ->
                                OwnershipVerifier.canModify(
                                        cost.getFstEnrUsid(), cost.getCostSvnDpmC(), user))
                .orElse(false);
    }
}
