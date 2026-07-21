package com.kdb.it.common.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 조직명 읽기 프로젝션의 Oracle 통합 테스트. */
@DisplayName("조직명 읽기 프로젝션")
class OrganizationNameProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        CorgnI organization = em.find(CorgnI.class, "120");
        if (organization == null) {
            em.persist(CorgnI.builder()
                    .prlmOgzCCone("120")
                    .bbrNm("디지털부")
                    .delYn("N")
                    .fstEnrUsid("FIXTURE")
                    .fstEnrDtm(LocalDateTime.now())
                    .lstChgUsid("FIXTURE")
                    .lstChgDtm(LocalDateTime.now())
                    .build());
        } else {
            organization.update("디지털부", organization.getBbrWrenNm(),
                    organization.getItmSqnSno(), organization.getPrlmHrkOgzCCone());
        }
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("조직코드 배치와 단건 조회는 코드와 이름만 반환한다")
    void findNameViews_returnsCodeAndName() {
        List<OrganizationRepository.OrganizationNameView> rows =
                organizationRepository.findNameViewsByPrlmOgzCConeIn(List.of("120", "없는조직"));

        assertThat(rows).filteredOn(row -> row.getPrlmOgzCCone().equals("120"))
                .singleElement()
                .satisfies(row -> assertThat(row.getBbrNm()).isEqualTo("디지털부"));
        assertThat(organizationRepository.findNameViewByPrlmOgzCCone("120"))
                .get()
                .extracting(row -> row.getBbrNm())
                .isEqualTo("디지털부");
        assertThat(Arrays.stream(OrganizationRepository.OrganizationNameView.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("getPrlmOgzCCone", "getBbrNm");
    }
}
