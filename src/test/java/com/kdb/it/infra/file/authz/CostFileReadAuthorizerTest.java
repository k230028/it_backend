package com.kdb.it.infra.file.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CostFileReadAuthorizerTest {

    private static final String COST = "COST-2026-0001";

    private final CostRepository costRepository = mock(CostRepository.class);
    private final CostFileReadAuthorizer authorizer = new CostFileReadAuthorizer(costRepository);

    private CustomUserDetails user(String eno, String bbrC) {
        return new CustomUserDetails(eno, List.of(CustomUserDetails.ATH_USER), bbrC);
    }

    private Cfilem file(String parentId) {
        return Cfilem.builder()
                .apgFlKdNm(CostFileReadAuthorizer.COST_KIND)
                .apgFlLnkCtzNm(parentId)
                .build();
    }

    @Test
    @DisplayName("전산업무비 첨부는 같은 부서와 IT 조직 사용자만 현재 최종본을 읽을 수 있다")
    void sameDepartmentAndItOrganizationCanReadCurrentCost() {
        given(costRepository.findByCostBgNoAndLstYnAndDelYn(COST, "Y", "N"))
                .willReturn(
                        Optional.of(
                                Bcostm.builder()
                                        .costBgNo(COST)
                                        .bgSno(2)
                                        .costSvnDpmC("29001")
                                        .build()));

        assertThat(authorizer.canRead(file(COST), user("E001", "29001"))).isTrue();
        assertThat(authorizer.canRead(file(COST), user("E002", "180"))).isTrue();
        assertThat(authorizer.canRead(file(COST), user("E003", "39001"))).isFalse();
    }

    @Test
    @DisplayName("부모 최종본이 없거나 인증 정보가 없으면 전산업무비 첨부를 읽을 수 없다")
    void missingParentAndAnonymousAreDenied() {
        given(costRepository.findByCostBgNoAndLstYnAndDelYn(COST, "Y", "N"))
                .willReturn(Optional.empty());

        assertThat(authorizer.canRead(file(COST), user("E001", "180"))).isFalse();
        assertThat(authorizer.canRead(file(COST), null)).isFalse();
    }

    @Test
    @DisplayName("부모 식별자가 비어 있거나 파일이 없으면 조회하지 않고 거부한다")
    void blankParentOrMissingFileIsDeniedWithoutLookup() {
        assertThat(authorizer.canRead(file(" "), user("E001", "180"))).isFalse();
        assertThat(authorizer.canRead(null, user("E001", "180"))).isFalse();

        then(costRepository).shouldHaveNoInteractions();
    }
}
