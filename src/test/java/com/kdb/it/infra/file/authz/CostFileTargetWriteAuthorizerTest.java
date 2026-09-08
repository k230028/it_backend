package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CostFileTargetWriteAuthorizerTest {

    private static final String COST = "COST-2026-0001";

    private final CostRepository costRepository = mock(CostRepository.class);
    private final CostFileTargetWriteAuthorizer authorizer =
            new CostFileTargetWriteAuthorizer(costRepository);

    private CustomUserDetails user(String eno, String bbrC) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), bbrC);
    }

    private CustomUserDetails admin() {
        return new CustomUserDetails("ADMIN", List.of(CustomUserDetails.ATH_ADMIN), "99999");
    }

    private void givenCurrentCost(String owner, String department) {
        given(costRepository.findByCostBgNoAndLstYnAndDelYn(COST, "Y", "N"))
                .willReturn(
                        Optional.of(
                                Bcostm.builder()
                                        .costBgNo(COST)
                                        .bgSno(2)
                                        .fstEnrUsid(owner)
                                        .costSvnDpmC(department)
                                        .build()));
    }

    @Test
    @DisplayName("전산업무비 종류만 담당한다")
    void supportsOnlyCostKind() {
        assertThat(authorizer.supportedApgFlKdNms()).containsExactly("전산업무비");
    }

    @Test
    @DisplayName("현재 최종본의 작성자·같은 부서·관리자는 첨부 대상에 쓰고 타인은 쓸 수 없다")
    void ownerSameDepartmentAndAdminCanWriteButUnrelatedUserCannot() {
        givenCurrentCost("OWNER", "29001");

        assertThat(authorizer.canWrite(COST, user("OWNER", "39001"))).isTrue();
        assertThat(authorizer.canWrite(COST, user("OTHER", "29001"))).isTrue();
        assertThat(authorizer.canWrite(COST, admin())).isTrue();
        assertThat(authorizer.canWrite(COST, user("OTHER", "39001"))).isFalse();
        assertThat(authorizer.allowsGenericMutation()).isTrue();
    }

    @Test
    @DisplayName("부모 최종본이 없거나 식별자·인증 정보가 없으면 첨부 대상에 쓸 수 없다")
    void missingBlankAndAnonymousTargetsAreDenied() {
        given(costRepository.findByCostBgNoAndLstYnAndDelYn(COST, "Y", "N"))
                .willReturn(Optional.empty());

        assertThat(authorizer.canWrite(COST, admin())).isFalse();
        assertThat(authorizer.canWrite(" ", admin())).isFalse();
        assertThat(authorizer.canWrite(null, admin())).isFalse();

        assertThat(authorizer.canWrite(COST, null)).isFalse();
    }

    @Test
    @DisplayName("비인증 사용자는 부모를 조회하지 않고 거부한다")
    void anonymousIsDeniedWithoutLookup() {
        assertThat(authorizer.canWrite(COST, null)).isFalse();

        then(costRepository).shouldHaveNoInteractions();
    }
}
