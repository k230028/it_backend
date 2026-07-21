package com.kdb.it.common.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 사용자 목록·상세 읽기 프로젝션의 Oracle 통합 테스트. */
@DisplayName("사용자 읽기 프로젝션")
class UserReadProjectionIt extends AbstractOracleRepositoryTest {

    private static final String ORG_CODE = "120";
    private static final String PARENT_ORG_CODE = "P120";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    @BeforeEach
    void setUp() {
        CorgnI parentOrganization = em.find(CorgnI.class, PARENT_ORG_CODE);
        if (parentOrganization == null) {
            em.persist(organization(PARENT_ORG_CODE, "IT부문", null));
            em.flush();
        } else {
            parentOrganization.update("IT부문", parentOrganization.getBbrWrenNm(),
                    parentOrganization.getItmSqnSno(), parentOrganization.getPrlmHrkOgzCCone());
        }

        CorgnI organization = em.find(CorgnI.class, ORG_CODE);
        if (organization == null) {
            em.persist(organization(ORG_CODE, "디지털부", PARENT_ORG_CODE));
        } else {
            organization.update("디지털부", organization.getBbrWrenNm(),
                    organization.getItmSqnSno(), PARENT_ORG_CODE);
        }
        persistUser("BE03001", "홍길동", "팀장", ORG_CODE, "12004", "N");
        persistUser("BE03002", "김길동", "사원", ORG_CODE, "12004", "Y");
        persistUser("BE03003", "null조직", "사원", null, "12005", "N");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("부서 목록은 삭제 필터 없이 7개 필드와 부점명을 반환한다")
    void findListRowsByBbrC_returnsActiveAndDeletedRows() {
        List<UserDto.ListRow> rows = userRepository.findListRowsByBbrC(ORG_CODE);

        assertThat(rows).filteredOn(row -> row.eno().startsWith("BE03"))
                .extracting(UserDto.ListRow::eno)
                .containsExactlyInAnyOrder("BE03001", "BE03002");
        assertThat(rows).filteredOn(row -> row.eno().equals("BE03001"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.bbrNm()).isEqualTo("디지털부");
                    assertThat(row.temC()).isEqualTo("12004");
                    assertThat(row.usrNm()).isEqualTo("홍길동");
                });
    }

    @Test
    @DisplayName("이름 검색은 삭제 필터와 정렬을 추가하지 않고 조직 없는 사용자도 반환한다")
    void searchListRowsByName_preservesExistingFilterPolicy() {
        assertThat(userRepository.searchListRowsByName("길동"))
                .filteredOn(row -> row.eno().startsWith("BE03"))
                .extracting(UserDto.ListRow::eno)
                .containsExactlyInAnyOrder("BE03001", "BE03002");
        assertThat(userRepository.searchListRowsByName("null조직"))
                .filteredOn(row -> row.eno().equals("BE03003"))
                .singleElement()
                .satisfies(row -> assertThat(row.bbrNm()).isNull());
    }

    @Test
    @DisplayName("상세 조회는 소속 조직과 상위 조직 별칭을 정확히 매핑한다")
    void findDetailRowByEno_mapsOrganizationAliases() {
        UserDto.DetailRow row = userRepository.findDetailRowByEno("BE03001").orElseThrow();

        assertThat(row.eno()).isEqualTo("BE03001");
        assertThat(row.bbrNm()).isEqualTo("디지털부");
        assertThat(row.etrMilAddrNm()).isEqualTo("BE03001@example.test");
        assertThat(row.prlmHrkOgzCCone()).isEqualTo(PARENT_ORG_CODE);
        assertThat(row.prlmHrkOgzCNm()).isEqualTo("IT부문");
    }

    @Test
    @DisplayName("이름·조직코드·관리자 사용자 view는 용도별 필드와 삭제 조건을 반환한다")
    void readViews_returnPurposeSpecificFields() {
        assertThat(userRepository.findNameViewsByEnoIn(List.of("BE03001", "BE03002")))
                .extracting(UserRepository.UserNameView::getUsrNm)
                .containsExactlyInAnyOrder("홍길동", "김길동");
        assertThat(userRepository.findNameViewByEno("BE03001"))
                .get().extracting(UserRepository.UserNameView::getUsrNm).isEqualTo("홍길동");

        assertThat(userRepository.findOrgCodeViewsByEnoIn(List.of("BE03001")))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getTemC()).isEqualTo("12004");
                    assertThat(row.getBbrC()).isEqualTo("120");
                });

        assertThat(userRepository.findAdminUserViewsByDelYn("N"))
                .filteredOn(row -> row.getEno().startsWith("BE03"))
                .extracting(UserRepository.AdminUserView::getEno)
                .contains("BE03001", "BE03003")
                .doesNotContain("BE03002");
    }

    @Test
    @DisplayName("사용자 row와 view는 승인된 필드만 노출한다")
    void rowAndViewContracts_areExact() {
        assertThat(Arrays.stream(UserDto.ListRow.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("eno", "bbrC", "bbrNm", "temC", "temNm", "usrNm", "ptCNm");
        assertThat(Arrays.stream(UserDto.DetailRow.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("eno", "bbrC", "bbrNm", "temC", "temNm", "usrNm", "ptCNm",
                        "etrMilAddrNm", "inleNo", "cpnTpn", "dtsDtlCone", "prlmHrkOgzCCone", "prlmHrkOgzCNm");
        assertThat(Arrays.stream(UserRepository.UserNameView.class.getDeclaredMethods()).map(method -> method.getName()))
                .containsExactlyInAnyOrder("getEno", "getUsrNm");
        assertThat(Arrays.stream(UserRepository.UserOrgCodeView.class.getDeclaredMethods()).map(method -> method.getName()))
                .containsExactlyInAnyOrder("getEno", "getTemC", "getBbrC");
        assertThat(Arrays.stream(UserRepository.AdminUserView.class.getDeclaredMethods()).map(method -> method.getName()))
                .containsExactlyInAnyOrder("getEno", "getUsrNm", "getPtCNm", "getTemC", "getTemNm", "getBbrC",
                        "getEtrMilAddrNm", "getInleNo", "getCpnTpn", "getFstEnrDtm", "getLstChgDtm");
        assertThat(Arrays.stream(UserRepository.CommitteeUserRow.class.getDeclaredMethods()).map(method -> method.getName()))
                .containsExactlyInAnyOrder("getTemC", "getEno", "getUsrNm", "getBbrNm", "getPtCNm");
    }

    private CorgnI organization(String code, String name, String parentCode) {
        return CorgnI.builder()
                .prlmOgzCCone(code)
                .bbrNm(name)
                .prlmHrkOgzCCone(parentCode)
                .delYn("N")
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
    }

    private void persistUser(String eno, String name, String title, String bbrC, String temC, String delYn) {
        em.persist(CuserI.builder()
                .eno(eno)
                .usrNm(name)
                .ptCNm(title)
                .bbrC(bbrC)
                .temC(temC)
                .temNm("테스트팀")
                .etrMilAddrNm(eno + "@example.test")
                .inleNo("1234")
                .cpnTpn("01000000000")
                .dtsDtlCone("테스트 직무")
                .delYn(delYn)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build());
    }
}
