package com.kdb.it.domain.budget.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.dto.ProjectListRow;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 정보화사업 목록의 상태 필터를 실제 Oracle에서 검증한다 (BE-33).
 *
 * <p>BPROJA는 {@code (ABUS_MNG_NO, CNCD_RFR_NO)} 단위로 단계 문서별 상태를 보관하므로 사업 자신의 상태는 {@code CNCD_RFR_NO
 * = ABUS_MNG_NO}인 행에서만 읽어야 한다. 종전 필터는 사업의 BPROJA 행 전체에서 {@code IT_PTL_STS_TC} 최댓값을 구해 비교했기 때문에, 사업
 * 자신이 결재완료('09')여도 상위 계획 행('11')이 있으면 <b>'11'로 검색해야 잡히고 '09'로는 안 잡히는</b> 정반대 동작을 했다.
 *
 * <p>단위 테스트({@code ProjectServiceCoverageTest})는 표시값을 덮지만, 필터는 QueryDSL 서브쿼리 제거를 포함해 실제 SQL로 도는지를
 * 통합 테스트에서만 보증할 수 있다. {@code searchByCondition}·{@code searchListByCondition}·{@code
 * countByCondition}이 같은 WHERE를 공유한다는 계약도 함께 고정한다.
 */
@DisplayName("정보화사업 목록 상태 필터: 사업 자신의 BPROJA 행으로 판정한다 (BE-33)")
class ProjectStatusFilterIt extends AbstractOracleRepositoryTest {

    /** 예산편성 요청 결재완료 — 사업 자신의 단계 */
    private static final String OWN_STATUS = "09";

    /** 정보기술부문계획 정실협 진행중 — 상위 계획 문서의 상태(사업 자신의 단계가 아니다) */
    private static final String PLAN_STATUS = "11";

    @Autowired ProjectRepository projectRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("상위 계획 행이 더 큰 코드여도 자신의 상태('09')로 검색하면 잡힌다")
    void 자신의상태로검색_잡힌다() {
        String abusMngNo = persistProjectWithBothStatuses();

        List<Bprojm> rows = projectRepository.searchByCondition(condition(OWN_STATUS));

        assertThat(rows).extracting(Bprojm::getAbusMngNo).contains(abusMngNo);
    }

    @Test
    @DisplayName("상위 계획 행의 상태('11')로 검색하면 잡히지 않는다 — 종전에는 이쪽이 잡혔다")
    void 다른문서상태로검색_잡히지않는다() {
        String abusMngNo = persistProjectWithBothStatuses();

        List<Bprojm> rows = projectRepository.searchByCondition(condition(PLAN_STATUS));

        assertThat(rows).extracting(Bprojm::getAbusMngNo).doesNotContain(abusMngNo);
    }

    @Test
    @DisplayName("경량 목록·건수 조회도 같은 WHERE를 써서 결과가 일치한다")
    void 경량목록과건수_동일WHERE() {
        String abusMngNo = persistProjectWithBothStatuses();

        ProjectDto.SearchCondition own = condition(OWN_STATUS);
        ProjectDto.SearchCondition plan = condition(PLAN_STATUS);

        assertThat(projectRepository.searchListByCondition(own))
                .extracting(ProjectListRow::abusMngNo)
                .contains(abusMngNo);
        assertThat(projectRepository.searchListByCondition(plan))
                .extracting(ProjectListRow::abusMngNo)
                .doesNotContain(abusMngNo);

        // 건수 경로도 같은 WHERE를 공유한다 — 엔티티 조회 결과 수와 정확히 일치해야 한다.
        assertThat(projectRepository.countBySearchCondition(own))
                .isEqualTo(projectRepository.searchByCondition(own).size());
        assertThat(projectRepository.countBySearchCondition(plan))
                .isEqualTo(projectRepository.searchByCondition(plan).size());
    }

    @Test
    @DisplayName("자신의 행이 없으면 다른 문서 상태로도 잡히지 않는다")
    void 자신의행없음_어느상태로도안잡힌다() {
        String suffix = suffix();
        String abusMngNo = "PSF2-PRJ-N-" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 13, 0);

        entityManager.persist(project(abusMngNo, "상태필터-자기행없음-" + suffix, createdAt));
        // 상위 계획 행만 있고 사업 자신의 행이 없다.
        entityManager.persist(status(abusMngNo, "PSF2-PLN-" + suffix, PLAN_STATUS, createdAt));
        entityManager.flush();
        entityManager.clear();

        assertThat(projectRepository.searchByCondition(condition(PLAN_STATUS)))
                .extracting(Bprojm::getAbusMngNo)
                .doesNotContain(abusMngNo);
        assertThat(projectRepository.searchByCondition(condition(OWN_STATUS)))
                .extracting(Bprojm::getAbusMngNo)
                .doesNotContain(abusMngNo);
    }

    /**
     * 사업 자신의 행('09')과 상위 계획 행('11')을 함께 가진 사업을 적재한다.
     *
     * <p>MAX 집계로 읽으면 '11'이 '09'를 가리는 배치다 — BE-33이 관측한 그 상태다.
     *
     * @return 적재한 사업관리번호
     */
    private String persistProjectWithBothStatuses() {
        String suffix = suffix();
        String abusMngNo = "PSF2-PRJ-" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 12, 0);

        entityManager.persist(project(abusMngNo, "상태필터-" + suffix, createdAt));
        entityManager.persist(status(abusMngNo, abusMngNo, OWN_STATUS, createdAt));
        entityManager.persist(status(abusMngNo, "PSF2-PLN-" + suffix, PLAN_STATUS, createdAt));
        entityManager.flush();
        entityManager.clear();
        return abusMngNo;
    }

    private ProjectDto.SearchCondition condition(String stsTc) {
        ProjectDto.SearchCondition condition = new ProjectDto.SearchCondition();
        condition.setStsTc(stsTc);
        return condition;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Bprojm project(String abusMngNo, String abusNm, LocalDateTime createdAt) {
        return Bprojm.builder()
                .abusMngNo(abusMngNo)
                .sno(1)
                .abusNm(abusNm)
                .abusTc("01")
                .svnDpmC("PSF2-DPM")
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("PSF2-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("PSF2-TEST")
                .build();
    }

    private Bproja status(
            String abusMngNo, String cncdRfrNo, String stsTc, LocalDateTime createdAt) {
        return Bproja.builder()
                .abusMngNo(abusMngNo)
                .cncdRfrNo(cncdRfrNo)
                .stsTc(stsTc)
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("PSF2-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("PSF2-TEST")
                .build();
    }
}
