package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;

import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 이관 전용 품목 교체({@link ProjectService#replaceItemsForMigration})가 사업 단위 금액 스냅샷을 다시 계산하는지 고정합니다.
 *
 * <p>부문계획 조정으로 품목 금액이 바뀐 사업에서 {@code TOT_RQM_AMT}·{@code MPL_AMT}가 조정 전 합계로 남으면 컬럼 계약("활성 품목 합계
 * 스냅샷")이 깨지므로, 교체 직후 재계산이 일어나는지와 사용자 입력인 {@code DFR_AMT}는 건드리지 않는지를 함께 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectServiceMigrationSnapshotTest {

    private static final String ABUS_MNG_NO = "PRJ-2026-0001";

    @Mock private ProjectRepository projectRepository;
    @Mock private ApplicationMapRepository capplaRepository;
    @Mock private ProjectItemRepository bitemmRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private OrgNameResolver orgNameResolver;
    @Mock private CodeService codeService;
    @Mock private XcrLookupService xcrLookupService;
    @Mock private BprojaSyncService bprojaSyncService;
    @Mock private ProjectQueryService projectQueryService;
    @Mock private ProjectBudgetSummaryService budgetSummaryService;

    @InjectMocks private ProjectService projectService;

    @BeforeEach
    void setUp() {
        given(xcrLookupService.resolveXcr(anyString(), org.mockito.ArgumentMatchers.any()))
                .willReturn(BigDecimal.ONE);
        given(bitemmRepository.getNextSequenceValue()).willReturn(1L);
        // 합계 계산은 실제 구현에 위임한다 (Mock 기본값은 record에 대해 null이라 스텁 없이는 NPE).
        doAnswer(
                        invocation ->
                                new ProjectBudgetSummaryService(codeService)
                                        .calculateAmountSnapshot(invocation.getArgument(0)))
                .when(budgetSummaryService)
                .calculateAmountSnapshot(anyList());
    }

    /** 기존 스냅샷을 들고 있는 대상 사업 (조정 전 총 예산 1,000 / 이후 400 / 기 지급예산 300). */
    private Bprojm givenProject() {
        Bprojm project =
                Bprojm.builder()
                        .abusMngNo(ABUS_MNG_NO)
                        .sno(1)
                        .delYn("N")
                        .totRqmAmt(new BigDecimal("1000"))
                        .mplAmt(new BigDecimal("400"))
                        .dfrAmt(new BigDecimal("300"))
                        .build();
        given(projectRepository.findByAbusMngNoAndLstYnAndDelYn(ABUS_MNG_NO, "Y", "N"))
                .willReturn(Optional.of(project));
        return project;
    }

    /** 교체 후 재조회되는 활성 품목을 지정한다. */
    private void givenActiveItemsAfterReplace(String amt, String mplAmt) {
        given(bitemmRepository.findByAbusMngNoAndFntTbCrySnoAndDelYn(ABUS_MNG_NO, 1, "N"))
                .willReturn(
                        List.of(
                                Bitemm.builder()
                                        .amt(new BigDecimal(amt))
                                        .mplAmt(new BigDecimal(mplAmt))
                                        .build()));
    }

    /** 교체 요청 품목 1건 (금액은 재조회 스텁이 대신하므로 형식만 갖춘다). */
    private List<ProjectDto.BitemmDto> replacementItems() {
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setCurC("KRW");
        item.setAmt(new BigDecimal("600"));
        return List.of(item);
    }

    @Test
    @DisplayName("품목 교체 후 총 예산·익년 이후 예산을 새 활성 품목 합계로 갱신한다")
    void 품목교체후_스냅샷을_재계산한다() {
        Bprojm project = givenProject();
        givenActiveItemsAfterReplace("600", "250");

        projectService.replaceItemsForMigration(ABUS_MNG_NO, replacementItems());

        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("600");
        assertThat(project.getMplAmt()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("기 지급예산(DFR_AMT)은 이관 경로에서 그대로 유지된다")
    void 기지급예산은_유지된다() {
        Bprojm project = givenProject();
        givenActiveItemsAfterReplace("600", "250");

        projectService.replaceItemsForMigration(ABUS_MNG_NO, replacementItems());

        assertThat(project.getDfrAmt()).isEqualByComparingTo("300");
    }

    /**
     * 조정 결과 총 예산이 기 지급예산보다 낮아져도 이관은 중단되지 않아야 한다.
     *
     * <p>{@code applyAmountSnapshot}의 {@code dfrAmt <= totRqmAmt} 검증을 이 경로에서 재사용하면 전체 이관 트랜잭션이 함께
     * 롤백된다. 이관은 사용자가 보낸 기 지급예산을 받지 않으므로 검증 대상이 아니다.
     */
    @Test
    @DisplayName("새 합계가 기 지급예산보다 낮아도 예외 없이 스냅샷만 갱신한다")
    void 총예산이_기지급예산보다_낮아도_예외가_없다() {
        Bprojm project = givenProject();
        givenActiveItemsAfterReplace("100", "0");

        assertThatCode(
                        () ->
                                projectService.replaceItemsForMigration(
                                        ABUS_MNG_NO, replacementItems()))
                .doesNotThrowAnyException();

        assertThat(project.getTotRqmAmt()).isEqualByComparingTo("100");
        assertThat(project.getMplAmt()).isEqualByComparingTo("0");
        assertThat(project.getDfrAmt()).isEqualByComparingTo("300");
    }
}
