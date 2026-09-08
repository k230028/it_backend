package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 전산업무비 첨부 읽기 판정기 — 현재 최종본의 예산 상세 조회 범위를 재사용합니다. */
@Component
@RequiredArgsConstructor
public class CostFileReadAuthorizer implements FileReadAuthorizer {

    /** 전산업무비 첨부 종류(APG_FL_KD_NM). */
    public static final String COST_KIND = "전산업무비";

    private final CostRepository costRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(COST_KIND);
    }

    /**
     * 전산업무비 첨부 읽기 가능 여부를 반환합니다.
     *
     * @param file 대상 파일(부모 전산업무비코드는 {@code APG_FL_LNK_CTZ_NM} 값)
     * @param user 현재 사용자(null이면 비인증 → 불가)
     * @return 현재 최종·미삭제 부모가 있고 예산 상세 조회 범위에 포함되면 true
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null || file == null || !StringUtils.hasText(file.getApgFlLnkCtzNm())) {
            return false;
        }
        return costRepository
                .findByCostBgNoAndLstYnAndDelYn(file.getApgFlLnkCtzNm(), "Y", "N")
                .map(cost -> BudgetDetailAccessVerifier.isReadable(cost.getCostSvnDpmC(), user))
                .orElse(false);
    }
}
