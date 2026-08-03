package com.kdb.it.domain.council.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 협의회 신청대상 목록의 사업 상태 필터를 실제 Oracle에서 검증한다.
 *
 * <p>BPROJA는 {@code (ABUS_MNG_NO, CNCD_RFR_NO)} 단위로 단계 문서별 상태를 보관하므로, 사업 자신의 상태는 반드시 {@code
 * CNCD_RFR_NO = ABUS_MNG_NO}인 행에서 읽어야 한다. 상위 계획 행({@code PLN-...})이나 사업계획 행({@code BIZ-...})이 함께
 * 존재해도 판정이 흔들리지 않아야 한다.
 */
@DisplayName("협의회 신청대상 목록: 사업 자신의 BPROJA 행으로 상태를 판정한다")
class CouncilProjectStatusFilterIt extends AbstractOracleRepositoryTest {

    /** 타당성검토 정실협 진행중 (IT_PTL_STS_TC=45) */
    private static final String IN_PROGRESS = "45";

    /** 예산편성 요청 결재완료 = 협의회 신청 대상 (IT_PTL_STS_TC=09) */
    private static final String PENDING = "09";

    /** 정보기술부문계획 정실협 진행중 (IT_PTL_STS_TC=11) — 상위 계획 문서의 상태 */
    private static final String PLAN_IN_PROGRESS = "11";

    @Autowired CouncilRepository councilRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("상위 계획 행이 더 큰 코드여도 미신청 사업(자신='09')을 신청대상으로 조회한다")
    void 미신청사업_상위계획행에가려지지않는다() {
        String suffix = suffix();
        String abusMngNo = "PSF-PRJ-A-" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 9, 0);

        entityManager.persist(project(abusMngNo, "상태필터-미신청-" + suffix, createdAt));
        // 사업 자신의 단계 상태: 신청 대상(09)
        entityManager.persist(status(abusMngNo, abusMngNo, PENDING, createdAt));
        // 상위 계획 문서의 상태: 11 — MAX() 집계로 읽으면 이 행이 09를 가린다
        entityManager.persist(status(abusMngNo, "PSF-PLN-" + suffix, PLAN_IN_PROGRESS, createdAt));
        entityManager.flush();
        entityManager.clear();

        List<CouncilProjectRow> rows =
                councilRepository.findProjectRowsForCouncilAll(IN_PROGRESS, PENDING);

        assertThat(rows)
                .filteredOn(row -> abusMngNo.equals(row.abusMngNo()))
                .singleElement()
                .satisfies(row -> assertThat(row.applied()).isFalse());
    }

    @Test
    @DisplayName("신청된 사업은 자신의 상태가 정실협 진행중(45)일 때 조회한다")
    void 신청된사업_자신의상태로판정한다() {
        String suffix = suffix();
        String abusMngNo = "PSF-PRJ-B-" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 10, 0);

        entityManager.persist(project(abusMngNo, "상태필터-신청됨-" + suffix, createdAt));
        entityManager.persist(status(abusMngNo, abusMngNo, IN_PROGRESS, createdAt));
        entityManager.persist(status(abusMngNo, "PSF-PLN-" + suffix, PLAN_IN_PROGRESS, createdAt));
        entityManager.persist(council("PSF-ASCT-" + suffix, abusMngNo, createdAt));
        entityManager.flush();
        entityManager.clear();

        List<CouncilProjectRow> rows =
                councilRepository.findProjectRowsForCouncilAll(IN_PROGRESS, PENDING);

        assertThat(rows)
                .filteredOn(row -> abusMngNo.equals(row.abusMngNo()))
                .singleElement()
                .satisfies(row -> assertThat(row.applied()).isTrue());
    }

    @Test
    @DisplayName("부서 조회도 사업 자신의 상태로 판정한다")
    void 부서조회_자신의상태로판정한다() {
        String suffix = suffix();
        String abusMngNo = "PSF-PRJ-C-" + suffix;
        String svnDpmC = "PSFD" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 11, 0);

        entityManager.persist(project(abusMngNo, "상태필터-부서-" + suffix, svnDpmC, createdAt));
        entityManager.persist(status(abusMngNo, abusMngNo, PENDING, createdAt));
        entityManager.persist(status(abusMngNo, "PSF-PLN-" + suffix, PLAN_IN_PROGRESS, createdAt));
        entityManager.flush();
        entityManager.clear();

        List<CouncilProjectRow> rows =
                councilRepository.findProjectRowsForCouncilByDepartment(
                        svnDpmC, IN_PROGRESS, PENDING);

        assertThat(rows).extracting(CouncilProjectRow::abusMngNo).containsExactly(abusMngNo);
    }

    @Test
    @DisplayName("삭제된 단계 상태 행은 판정에서 제외한다")
    void 삭제된상태행은무시한다() {
        String suffix = suffix();
        String abusMngNo = "PSF-PRJ-D-" + suffix;
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 12, 0);

        entityManager.persist(project(abusMngNo, "상태필터-삭제행-" + suffix, createdAt));
        Bproja deleted = status(abusMngNo, abusMngNo, PENDING, createdAt);
        deleted.delete();
        entityManager.persist(deleted);
        entityManager.flush();
        entityManager.clear();

        List<CouncilProjectRow> rows =
                councilRepository.findProjectRowsForCouncilAll(IN_PROGRESS, PENDING);

        assertThat(rows).extracting(CouncilProjectRow::abusMngNo).doesNotContain(abusMngNo);
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private Bprojm project(String abusMngNo, String abusNm, LocalDateTime createdAt) {
        return project(abusMngNo, abusNm, "PSF-DPM", createdAt);
    }

    private Bprojm project(
            String abusMngNo, String abusNm, String svnDpmC, LocalDateTime createdAt) {
        return Bprojm.builder()
                .abusMngNo(abusMngNo)
                .sno(1)
                .abusNm(abusNm)
                .abusTc("01")
                .svnDpmC(svnDpmC)
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("PSF-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("PSF-TEST")
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
                .fstEnrUsid("PSF-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("PSF-TEST")
                .build();
    }

    private Basctm council(String asctId, String abusMngNo, LocalDateTime createdAt) {
        return Basctm.builder()
                .itPtlAsctId(asctId)
                .abusMngNo(abusMngNo)
                .sno(1)
                .itPtlAsctDbrTc("03")
                .itPtlAsctPrgStsTc("01")
                .delYn("N")
                .fstEnrDtm(createdAt)
                .fstEnrUsid("PSF-TEST")
                .lstChgDtm(createdAt)
                .lstChgUsid("PSF-TEST")
                .build();
    }
}
