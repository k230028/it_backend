package com.kdb.it.common.iam.repository;

import static com.kdb.it.support.ProjectionContracts.declaredMethodNames;
import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CauthI;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.entity.CroleIId;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/** 사용자 목록·상세 읽기 프로젝션의 Oracle 통합 테스트. */
@DisplayName("사용자 읽기 프로젝션")
class UserReadProjectionIt extends AbstractOracleRepositoryTest {

    /*
     * BE-27: 조직 픽스처는 클래스마다 고유 코드를 쓴다.
     *
     * 종전에는 이 클래스와 OrganizationNameProjectionIt·CommitteeUserProjectionIt이 모두
     * "120" 행을 각자 upsert/변형해 공유했다. 순차 실행에서는 우연히 안전했지만 Gradle 병렬
     * 테스트를 켜는 순간 경합한다. BBR_C가 VARCHAR2(3)이라 접두사를 길게 붙일 수 없으므로
     * 운영 조직코드와 겹치지 않는 3자 코드를 클래스별로 나눠 쓴다.
     */
    private static final String ORG_CODE = "Z71";
    private static final String PARENT_ORG_CODE = "P-Z71";

    /** 키워드 검색 상한 — 픽스처 전건이 들어오도록 충분히 큰 값 */
    private static final int SEARCH_LIMIT = 200;

    @Autowired private UserRepository userRepository;

    @Autowired private TestEntityManager em;

    @BeforeEach
    void setUp() {
        CorgnI parentOrganization = em.find(CorgnI.class, PARENT_ORG_CODE);
        if (parentOrganization == null) {
            em.persist(organization(PARENT_ORG_CODE, "IT부문", null));
            em.flush();
        } else {
            parentOrganization.update(
                    "IT부문",
                    parentOrganization.getBbrWrenNm(),
                    parentOrganization.getItmSqnSno(),
                    parentOrganization.getPrlmHrkOgzCCone());
        }

        CorgnI organization = em.find(CorgnI.class, ORG_CODE);
        if (organization == null) {
            em.persist(organization(ORG_CODE, "디지털부", PARENT_ORG_CODE));
        } else {
            organization.update(
                    "디지털부",
                    organization.getBbrWrenNm(),
                    organization.getItmSqnSno(),
                    PARENT_ORG_CODE);
        }
        persistUser("BE03001", "홍길동", "팀장", ORG_CODE, "12004", "N");
        persistUser("BE03002", "김길동", "사원", ORG_CODE, "12004", "Y");
        persistUser("BE03003", "null조직", "사원", null, "12005", "N");
        // 표시 정렬(K 행번 우선 → 직위코드 오름차순) 검증용 픽스처.
        // 행번 접두어가 정렬 키이므로 이 클래스 전용 조직코드(Z71)를 붙여 다른 픽스처와 겹치지 않게 한다.
        persistOrderingUser("KZ71002", "정렬케이뒤", "20");
        persistOrderingUser("OZ71001", "정렬오앞", "10");
        persistOrderingUser("KZ71001", "정렬케이앞", "10");
        persistOrderingUser("OZ71002", "정렬오뒤", null);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("부서 목록은 삭제 필터 없이 7개 필드와 부점명을 반환한다")
    void findListRowsByBbrC_returnsActiveAndDeletedRows() {
        List<UserDto.ListRow> rows = userRepository.findListRowsByBbrC(ORG_CODE, null);

        assertThat(rows)
                .filteredOn(row -> row.eno().startsWith("BE03"))
                .extracting(row -> row.eno())
                .containsExactlyInAnyOrder("BE03001", "BE03002");
        assertThat(rows)
                .filteredOn(row -> row.eno().equals("BE03001"))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.bbrNm()).isEqualTo("디지털부");
                            assertThat(row.temC()).isEqualTo("12004");
                            assertThat(row.usrNm()).isEqualTo("홍길동");
                        });
    }

    @Test
    @DisplayName("키워드 검색은 이름·팀명·사번을 대상으로 하고 삭제 필터 없이 조직 없는 사용자도 반환한다")
    void searchListRowsByKeyword_matchesNameTeamAndEno() {
        // 이름 부분 일치 — 삭제된 사용자(BE03002)도 기존 정책대로 포함한다
        assertThat(userRepository.searchListRowsByKeyword("길동", null, SEARCH_LIMIT))
                .filteredOn(row -> row.eno().startsWith("BE03"))
                .extracting(row -> row.eno())
                .containsExactlyInAnyOrder("BE03001", "BE03002");

        // 조직이 없는 사용자도 left join으로 반환한다 (부점명은 null)
        assertThat(userRepository.searchListRowsByKeyword("null조직", null, SEARCH_LIMIT))
                .filteredOn(row -> row.eno().equals("BE03003"))
                .singleElement()
                .satisfies(row -> assertThat(row.bbrNm()).isNull());

        // 팀명 부분 일치 — 이름이 서로 달라도 같은 팀이면 모두 조회된다
        assertThat(userRepository.searchListRowsByKeyword("테스트팀", null, SEARCH_LIMIT))
                .extracting(row -> row.eno())
                .contains("BE03001", "BE03002", "BE03003");

        // 사번 부분 일치(대소문자 무시)와 이름 오름차순 정렬
        List<String> names =
                userRepository.searchListRowsByKeyword("be0300", null, SEARCH_LIMIT).stream()
                        .map(UserDto.ListRow::usrNm)
                        .toList();
        assertThat(names).contains("홍길동", "김길동", "null조직");
        assertThat(names.indexOf("김길동")).isLessThan(names.indexOf("홍길동"));
    }

    @Test
    @DisplayName("부서 목록과 키워드 검색은 K 행번을 앞세우고 직위코드 오름차순으로 정렬한다")
    void employeeRows_orderByEnoPrefixThenPositionCode() {
        List<String> expected = List.of("KZ71001", "KZ71002", "OZ71001", "OZ71002");

        // 부서 목록: K 행번(직위코드 10 → 20) → O 행번(직위코드 10 → 직위코드 없음)
        assertThat(userRepository.findListRowsByBbrC(ORG_CODE, null))
                .extracting(UserDto.ListRow::eno)
                .filteredOn(eno -> expected.contains(eno))
                .containsExactlyElementsOf(expected);

        // 키워드 검색도 같은 순서를 사용한다 (상한 절단보다 정렬이 먼저 적용됨)
        assertThat(userRepository.searchListRowsByKeyword("정렬", null, SEARCH_LIMIT))
                .extracting(UserDto.ListRow::eno)
                .filteredOn(eno -> expected.contains(eno))
                .containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("키워드 검색은 요청한 상한까지만 반환한다")
    void searchListRowsByKeyword_appliesLimit() {
        assertThat(userRepository.searchListRowsByKeyword("테스트팀", null, 1)).hasSize(1);
        assertThat(userRepository.searchListRowsByKeyword("테스트팀", null, 2)).hasSize(2);
    }

    @Test
    @DisplayName("행번 접두사를 주면 부서 목록과 키워드 검색 모두 해당 접두사 행번만 반환한다")
    void employeeRows_filterByEnoPrefix() {
        // 부서 목록: 같은 부서의 O 행번(OZ71001·OZ71002)은 제외된다
        assertThat(userRepository.findListRowsByBbrC(ORG_CODE, "K"))
                .extracting(UserDto.ListRow::eno)
                .containsExactly("KZ71001", "KZ71002");

        // 키워드 검색: 접두사 필터가 상한 절단보다 먼저 적용된다
        assertThat(userRepository.searchListRowsByKeyword("정렬", "K", SEARCH_LIMIT))
                .extracting(UserDto.ListRow::eno)
                .filteredOn(eno -> eno.endsWith("Z71001") || eno.endsWith("Z71002"))
                .containsExactly("KZ71001", "KZ71002");
    }

    @Test
    @DisplayName("행번 접두사가 공백이면 필터를 적용하지 않는다")
    void employeeRows_blankEnoPrefixKeepsAllRows() {
        assertThat(userRepository.searchListRowsByKeyword("정렬", "   ", SEARCH_LIMIT))
                .extracting(UserDto.ListRow::eno)
                .contains("KZ71001", "OZ71001");
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
    @DisplayName("보유 자격등급은 활성 역할과 활성 자격등급의 명칭만 반환한다")
    void findActiveQualificationGradeNamesByEno_filtersInactiveAndDeletedRows() {
        em.persist(qualification("TSTBEA01", "시스템관리자", "Y", "N"));
        em.persist(qualification("TSTBEA02", "정보보호관리자", "Y", "N"));
        em.persist(qualification("TSTBEA03", "미사용역할등급", "Y", "N"));
        em.persist(qualification("TSTBEA04", "미사용자격등급", "N", "N"));
        em.persist(qualification("TSTBEA05", "삭제자격등급", "Y", "Y"));
        em.persist(qualification("TSTBEA06", "삭제역할등급", "Y", "N"));
        em.persist(role("TSTBEA01", "BE03001", "Y", "N"));
        em.persist(role("TSTBEA02", "BE03001", "Y", "N"));
        em.persist(role("TSTBEA03", "BE03001", "N", "N"));
        em.persist(role("TSTBEA04", "BE03001", "Y", "N"));
        em.persist(role("TSTBEA05", "BE03001", "Y", "N"));
        em.persist(role("TSTBEA06", "BE03001", "Y", "Y"));
        em.flush();

        assertThat(userRepository.findActiveQualificationGradeNamesByEno("BE03001"))
                .containsExactly("시스템관리자", "정보보호관리자");
    }

    @Test
    @DisplayName("이름·조직코드·관리자 사용자 view는 용도별 필드와 삭제 조건을 반환한다")
    void readViews_returnPurposeSpecificFields() {
        assertThat(userRepository.findNameViewsByEnoIn(List.of("BE03001", "BE03002")))
                .extracting(row -> row.getUsrNm())
                .containsExactlyInAnyOrder("홍길동", "김길동");
        assertThat(userRepository.findNameViewByEno("BE03001"))
                .get()
                .extracting(row -> row.getUsrNm())
                .isEqualTo("홍길동");

        assertThat(userRepository.findOrgCodeViewsByEnoIn(List.of("BE03001")))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getTemC()).isEqualTo("12004");
                            assertThat(row.getBbrC()).isEqualTo(ORG_CODE);
                        });

        assertThat(userRepository.findAdminUserViewsByDelYn("N"))
                .filteredOn(row -> row.getEno().startsWith("BE03"))
                .extracting(row -> row.getEno())
                .contains("BE03001", "BE03003")
                .doesNotContain("BE03002");
    }

    @Test
    @DisplayName("결재자 일괄 조회는 조직을 함께 적재해 부점명 역참조를 추가 조회 없이 지원한다")
    void findByEnoInWithOrganization_fetchesOrganizationForApproverDisplay() {
        List<CuserI> users =
                userRepository.findByEnoInWithOrganization(List.of("BE03001", "BE03002"));

        assertThat(users)
                .extracting(CuserI::getEno)
                .containsExactlyInAnyOrder("BE03001", "BE03002");
        assertThat(users)
                .allSatisfy(
                        user -> {
                            assertThat(Hibernate.isInitialized(user.getOrganization())).isTrue();
                            assertThat(user.getBbrNm()).isEqualTo("디지털부");
                        });
    }

    @Test
    @DisplayName("기존 사용자 일괄 조회는 조직을 지연 적재해 경량 호출 계약을 유지한다")
    void findByEnoIn_keepsOrganizationLazyForExistingCallers() {
        List<CuserI> users = userRepository.findByEnoIn(List.of("BE03001", "BE03002"));

        assertThat(users)
                .extracting(CuserI::getEno)
                .containsExactlyInAnyOrder("BE03001", "BE03002");
        assertThat(users)
                .allSatisfy(
                        user ->
                                assertThat(Hibernate.isInitialized(user.getOrganization()))
                                        .isFalse());
    }

    @Test
    @DisplayName("사용자 row와 view는 승인된 필드만 노출한다")
    void rowAndViewContracts_areExact() {
        assertThat(
                        Arrays.stream(UserDto.ListRow.class.getRecordComponents())
                                .map(component -> component.getName()))
                .containsExactly("eno", "bbrC", "bbrNm", "temC", "temNm", "usrNm", "ptCNm");
        assertThat(
                        Arrays.stream(UserDto.DetailRow.class.getRecordComponents())
                                .map(component -> component.getName()))
                .containsExactly(
                        "eno",
                        "bbrC",
                        "bbrNm",
                        "temC",
                        "temNm",
                        "usrNm",
                        "ptCNm",
                        "etrMilAddrNm",
                        "inleNo",
                        "cadrTpn",
                        "dtsDtlCone",
                        "prlmHrkOgzCCone",
                        "prlmHrkOgzCNm");
        assertThat(declaredMethodNames(UserRepository.UserNameView.class))
                .containsExactlyInAnyOrder("getEno", "getUsrNm", "getPtCNm");
        assertThat(declaredMethodNames(UserRepository.UserTeamNameView.class))
                .containsExactlyInAnyOrder("getEno", "getTemNm");
        assertThat(declaredMethodNames(UserRepository.UserOrgCodeView.class))
                .containsExactlyInAnyOrder("getEno", "getTemC", "getBbrC");
        assertThat(declaredMethodNames(UserRepository.AdminUserView.class))
                .containsExactlyInAnyOrder(
                        "getEno",
                        "getUsrNm",
                        "getPtCNm",
                        "getTemC",
                        "getTemNm",
                        "getBbrC",
                        // 파생 프로젝션은 부점명을 채울 수 없어 default null을 반환하고,
                        // QueryDSL 구현(AdminUserProjection)만 실제 값을 채운다.
                        "getBbrNm",
                        "getEtrMilAddrNm",
                        "getInleNo",
                        "getCpnTpn",
                        "getFstEnrDtm",
                        "getLstChgDtm");
        assertThat(declaredMethodNames(UserRepository.CommitteeUserRow.class))
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

    private CauthI qualification(String athId, String name, String useYn, String delYn) {
        return CauthI.builder()
                .athId(athId)
                .qlfGrNm(name)
                .useYn(useYn)
                .delYn(delYn)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
    }

    private CroleI role(String athId, String eno, String useYn, String delYn) {
        return CroleI.builder()
                .id(new CroleIId(athId, eno))
                .useYn(useYn)
                .delYn(delYn)
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
    }

    /** 표시 정렬 검증용 사용자 — 행번 접두어와 직위코드만 다르게 두고 나머지는 동일하게 맞춘다. */
    private void persistOrderingUser(String eno, String name, String ptC) {
        em.persist(
                CuserI.builder()
                        .eno(eno)
                        .usrNm(name)
                        .ptC(ptC)
                        .ptCNm("정렬")
                        .bbrC(ORG_CODE)
                        .temC("12004")
                        .temNm("테스트팀")
                        .etrMilAddrNm(eno + "@example.test")
                        .delYn("N")
                        .fstEnrUsid("FIXTURE")
                        .fstEnrDtm(LocalDateTime.now())
                        .lstChgUsid("FIXTURE")
                        .lstChgDtm(LocalDateTime.now())
                        .build());
    }

    private void persistUser(
            String eno, String name, String title, String bbrC, String temC, String delYn) {
        em.persist(
                CuserI.builder()
                        .eno(eno)
                        .usrNm(name)
                        .ptCNm(title)
                        .bbrC(bbrC)
                        .temC(temC)
                        .temNm("테스트팀")
                        .etrMilAddrNm(eno + "@example.test")
                        .inleNo("1234")
                        .cpnTpn("0221001234")
                        .cadrTpn("01000000000")
                        .dtsDtlCone("테스트 직무")
                        .delYn(delYn)
                        .fstEnrUsid("FIXTURE")
                        .fstEnrDtm(LocalDateTime.now())
                        .lstChgUsid("FIXTURE")
                        .lstChgDtm(LocalDateTime.now())
                        .build());
    }
}
