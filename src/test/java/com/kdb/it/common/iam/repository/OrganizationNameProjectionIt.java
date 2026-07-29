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

/** 조직 읽기 프로젝션(이름·목록·관리자 목록)의 Oracle 통합 테스트. */
@DisplayName("조직 읽기 프로젝션")
class OrganizationNameProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired private OrganizationRepository organizationRepository;

    @Autowired private TestEntityManager em;

    @BeforeEach
    void setUp() {
        upsertOrganization("120", "디지털부", "Digital Division", 10, "100", "N");
        upsertOrganization("129", "폐지부서", "Abolished Dept", 99, "100", "Y");
        em.flush();
        em.clear();
    }

    /** 조직 fixture를 코드 존재 여부에 따라 신규 등록하거나 필드값을 결정적으로 갱신합니다. */
    private void upsertOrganization(
            String code, String name, String enName, Integer seq, String parentCode, String delYn) {
        CorgnI organization = em.find(CorgnI.class, code);
        if (organization == null) {
            em.persist(
                    CorgnI.builder()
                            .prlmOgzCCone(code)
                            .bbrNm(name)
                            .bbrWrenNm(enName)
                            .itmSqnSno(seq)
                            .prlmHrkOgzCCone(parentCode)
                            .delYn(delYn)
                            .fstEnrUsid("FIXTURE")
                            .fstEnrDtm(LocalDateTime.now())
                            .lstChgUsid("FIXTURE")
                            .lstChgDtm(LocalDateTime.now())
                            .build());
        } else {
            organization.update(name, enName, seq, parentCode);
            if ("Y".equals(delYn)) {
                organization.delete();
            } else {
                organization.restore();
            }
        }
    }

    @Test
    @DisplayName("조직코드 배치와 단건 조회는 코드와 이름만 반환한다")
    void findNameViews_returnsCodeAndName() {
        List<OrganizationRepository.OrganizationNameView> rows =
                organizationRepository.findNameViewsByPrlmOgzCConeIn(List.of("120", "없는조직"));

        assertThat(rows)
                .filteredOn(row -> row.getPrlmOgzCCone().equals("120"))
                .singleElement()
                .satisfies(row -> assertThat(row.getBbrNm()).isEqualTo("디지털부"));
        assertThat(organizationRepository.findNameViewByPrlmOgzCCone("120"))
                .get()
                .extracting(row -> row.getBbrNm())
                .isEqualTo("디지털부");
        assertThat(
                        Arrays.stream(
                                        OrganizationRepository.OrganizationNameView.class
                                                .getDeclaredMethods())
                                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("getPrlmOgzCCone", "getBbrNm");
    }

    @Test
    @DisplayName("목록 조회 프로젝션은 findAll() 엔티티와 필드가 동등하고 삭제 여부와 무관하게 전건을 반환한다")
    void findListViewsBy_matchesEntityFindAllRegardlessOfDelYn() {
        List<CorgnI> entities = organizationRepository.findAll();
        List<OrganizationRepository.OrganizationListView> views =
                organizationRepository.findListViewsBy();

        // findAll()과 동일한 무필터 의미: 삭제된 조직(129)도 결과에 포함되고 전체 건수가 일치한다
        assertThat(views).hasSameSizeAs(entities);
        assertThat(views).anyMatch(row -> row.getPrlmOgzCCone().equals("129"));
        assertThat(views)
                .filteredOn(row -> row.getPrlmOgzCCone().equals("120"))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getBbrNm()).isEqualTo("디지털부");
                            assertThat(row.getPrlmHrkOgzCCone()).isEqualTo("100");
                        });
        assertThat(
                        Arrays.stream(
                                        OrganizationRepository.OrganizationListView.class
                                                .getDeclaredMethods())
                                .map(method -> method.getName()))
                .containsExactlyInAnyOrder("getPrlmOgzCCone", "getPrlmHrkOgzCCone", "getBbrNm");
    }

    @Test
    @DisplayName("관리자 목록 프로젝션의 delYn='N' 쿼리 필터는 인메모리 필터와 동등하다")
    void findAdminViewsByDelYn_matchesInMemoryFilter() {
        // FST_ENR_USID/LST_CHG_USID는 JPA Auditing(@CreatedBy/@LastModifiedBy)이 실제 실행 환경의
        // 인증자로 덮어쓰므로, 고정 리터럴 대신 방금 갱신된 엔티티의 실제 값과 비교하여 동등성을 검증한다.
        CorgnI activeEntity = em.find(CorgnI.class, "120");
        List<CorgnI> activeEntities =
                organizationRepository.findAll().stream()
                        .filter(o -> "N".equals(o.getDelYn()))
                        .toList();
        List<OrganizationRepository.OrganizationAdminView> activeViews =
                organizationRepository.findAdminViewsByDelYn("N");

        // 인메모리 "N".equals(delYn) 필터와 쿼리 delYn='N' 필터의 건수가 동일하고, 삭제행(129)은 제외된다
        assertThat(activeViews).hasSameSizeAs(activeEntities);
        assertThat(activeViews).noneMatch(row -> row.getPrlmOgzCCone().equals("129"));
        assertThat(activeViews)
                .filteredOn(row -> row.getPrlmOgzCCone().equals("120"))
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getBbrNm()).isEqualTo(activeEntity.getBbrNm());
                            assertThat(row.getBbrWrenNm()).isEqualTo(activeEntity.getBbrWrenNm());
                            assertThat(row.getItmSqnSno()).isEqualTo(activeEntity.getItmSqnSno());
                            assertThat(row.getPrlmHrkOgzCCone())
                                    .isEqualTo(activeEntity.getPrlmHrkOgzCCone());
                            assertThat(row.getFstEnrUsid()).isEqualTo(activeEntity.getFstEnrUsid());
                            assertThat(row.getLstChgUsid()).isEqualTo(activeEntity.getLstChgUsid());
                            assertThat(row.getFstEnrDtm()).isEqualTo(activeEntity.getFstEnrDtm());
                            assertThat(row.getLstChgDtm()).isEqualTo(activeEntity.getLstChgDtm());
                        });
        assertThat(
                        Arrays.stream(
                                        OrganizationRepository.OrganizationAdminView.class
                                                .getDeclaredMethods())
                                .map(method -> method.getName()))
                .containsExactlyInAnyOrder(
                        "getPrlmOgzCCone",
                        "getBbrNm",
                        "getBbrWrenNm",
                        "getItmSqnSno",
                        "getPrlmHrkOgzCCone",
                        "getFstEnrDtm",
                        "getFstEnrUsid",
                        "getLstChgDtm",
                        "getLstChgUsid");
    }
}
