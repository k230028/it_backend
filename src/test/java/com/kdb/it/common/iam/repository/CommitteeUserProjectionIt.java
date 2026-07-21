package com.kdb.it.common.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.service.UserRepresentativeSelector;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 위원·검토자 팀 대표 읽기 프로젝션의 Oracle 통합 테스트. */
@DisplayName("팀 대표 사용자 읽기 프로젝션")
class CommitteeUserProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired
    private UserRepository userRepository;

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
        persistUser("BE03001", "홍길동", "팀장", "12004", "N");
        persistUser("BE03002", "김길동", "사원", "12004", "Y");
        persistUser("BE03010", "팀원", "과장", "18010", "N");
        persistUser("BE03011", "팀장", "팀장", "18010", "N");
        persistUser("BE03012", "무소속", "대리", "18010", "N", null);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("지정 팀의 활성 사용자만 조직명과 함께 조회하고 대표자를 결정한다")
    void findCommitteeUserRows_returnsActiveUsersAndRepresentatives() {
        List<UserRepository.CommitteeUserRow> rows = userRepository
                .findCommitteeUserRowsByTemCInAndDelYn(List.of("12004", "18001", "18010", "18501"), "N")
                .stream()
                .filter(row -> row.getEno().startsWith("BE03"))
                .toList();
        Map<String, List<UserRepository.CommitteeUserRow>> byTeam = rows.stream()
                .collect(Collectors.groupingBy(row -> row.getTemC()));

        assertThat(byTeam.get("12004")).singleElement().satisfies(row -> {
            assertThat(row.getEno()).isEqualTo("BE03001");
            assertThat(row.getBbrNm()).isEqualTo("디지털부");
        });
        assertThat(UserRepresentativeSelector.pickView(byTeam.get("12004")))
                .get().extracting(row -> row.getEno()).isEqualTo("BE03001");
        assertThat(UserRepresentativeSelector.pickView(byTeam.get("18010")))
                .get().extracting(row -> row.getEno()).isEqualTo("BE03011");
    }

    @Test
    @DisplayName("위원 응답 프로젝션은 정확히 4개 필드만 조회하고 조직이 없어도 사용자를 유지한다")
    void findCouncilMemberUserRows_returnsExactFieldsWithLeftJoin() {
        List<UserRepository.CouncilMemberUserRow> rows = userRepository
                .findCouncilMemberUserRowsByEnoIn(List.of("BE03001", "BE03002", "BE03012", "UNKNOWN"));
        Map<String, UserRepository.CouncilMemberUserRow> byEno = rows.stream()
                .collect(Collectors.toMap(row -> row.getEno(), Function.identity()));

        assertThat(Arrays.stream(UserRepository.CouncilMemberUserRow.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("getEno", "getUsrNm", "getBbrNm", "getPtCNm");
        assertThat(byEno).containsOnlyKeys("BE03001", "BE03002", "BE03012");
        assertThat(byEno.get("BE03001").getBbrNm()).isEqualTo("디지털부");
        assertThat(byEno.get("BE03002").getUsrNm()).isEqualTo("김길동");
        assertThat(byEno.get("BE03012")).satisfies(row -> {
            assertThat(row.getUsrNm()).isEqualTo("무소속");
            assertThat(row.getBbrNm()).isNull();
            assertThat(row.getPtCNm()).isEqualTo("대리");
        });
    }

    private void persistUser(String eno, String name, String title, String temC, String delYn) {
        persistUser(eno, name, title, temC, delYn, "120");
    }

    private void persistUser(String eno, String name, String title, String temC, String delYn, String bbrC) {
        em.persist(CuserI.builder()
                .eno(eno)
                .usrNm(name)
                .ptCNm(title)
                .bbrC(bbrC)
                .temC(temC)
                .delYn(delYn)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build());
    }
}
