package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 연도 스냅샷 로드와 부서 기준 자연키·품목 인덱스 규칙을 고정합니다 (§4, §3). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigrationYearSnapshotTest {

    @Mock private CostRepository costRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectItemRepository projectItemRepository;
    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private BplanmRepository planRepository;

    @InjectMocks private MigrationYearSnapshot snapshot;

    @Test
    @DisplayName("load_부서기준_전산업무비_자연키로_조회된다")
    void load_부서기준_전산업무비_자연키로_조회된다() {
        Bcostm cost =
                Bcostm.builder()
                        .costBgNo("COST-26-0001")
                        .bgSno(1)
                        .bseYy("2026")
                        .costSvnDpmC("0210")
                        .bgUntAbusC(null)
                        .ioeC("001")
                        .cttOppNm("커브")
                        .cttNm("올인원워크스페이스")
                        .costTotXpAmt(new BigDecimal("15401000"))
                        .build();
        given(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of(cost));
        given(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        String key =
                MigrationYearSnapshot.costDeptKey("2026", "0210", "001", "커브", "올인원워크스페이스");
        assertThat(data.costNoByDeptKey(key)).isEqualTo("COST-26-0001");
        assertThat(data.costNosByDeptAndIoe("0210", "001")).containsExactly("COST-26-0001");
        assertThat(data.costOf("COST-26-0001").amount()).isEqualByComparingTo("15401000");
        assertThat(data.costOf("COST-26-0001").contractName()).isEqualTo("올인원워크스페이스");
        assertThat(data.bgUntAbusCOf("COST-26-0001")).isNull();
    }

    @Test
    @DisplayName("load_경상사업은_부서코드로_모인다")
    void load_경상사업은_부서코드로_모인다() {
        Bprojm ordinary = projectOf("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)", "920", "Y");
        Bprojm capital = projectOf("PRJ-2026-0001", "웹한글 기안기 도입", "0210", "N");
        given(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .willReturn(List.of(ordinary, capital));
        given(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(projectItemRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N"))).willReturn(List.of());

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.ordinaryProjectNosOfDept("920")).containsExactly("PRJ-2026-0002");
        assertThat(data.ordinaryProjectNosOfDept("0210")).isEmpty();
        assertThat(data.projectNameOf("PRJ-2026-0001")).isEqualTo("웹한글 기안기 도입");
    }

    @Test
    @DisplayName("load_품목은_사업별로_비목과_금액을_함께_들고온다")
    void load_품목은_사업별로_비목과_금액을_함께_들고온다() {
        Bprojm project = projectOf("PRJ-2026-0001", "웹한글 기안기 도입", "0210", "N");
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(1)
                        .abusMngNo("PRJ-2026-0001")
                        .ioeC("106")
                        .amt(new BigDecimal("1406000000"))
                        .lstYn("Y")
                        .build();
        given(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .willReturn(List.of(project));
        given(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(projectItemRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N")))
                .willReturn(List.of(item));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.itemsOfProject("PRJ-2026-0001"))
                .containsExactly(
                        new MigrationYearSnapshot.RequestItem(
                                "GCL-2026-0001", 1, "106", new BigDecimal("1406000000")));
    }

    @Test
    @DisplayName("load_비활성_최신이_아닌_품목은_제외된다")
    void load_비활성_최신이_아닌_품목은_제외된다() {
        Bprojm project = projectOf("PRJ-2026-0001", "웹한글 기안기 도입", "0210", "N");
        Bitemm stale =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0002")
                        .sno(1)
                        .abusMngNo("PRJ-2026-0001")
                        .ioeC("106")
                        .amt(new BigDecimal("100"))
                        .lstYn("N")
                        .build();
        given(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .willReturn(List.of(project));
        given(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).willReturn(List.of());
        given(projectItemRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N")))
                .willReturn(List.of(stale));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.itemsOfProject("PRJ-2026-0001")).isEmpty();
    }

    @Test
    @DisplayName("load_기존_편성률은_품목별_원본으로_보존된다")
    void load_기존_편성률은_품목별_원본으로_보존된다() {
        given(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).willReturn(List.of());
        given(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .willReturn(
                        List.of(
                                budgetOf("BITEMM", "GCL-2026-0001", "103", new BigDecimal("70")),
                                budgetOf("BITEMM", "GCL-2026-0002", "101", new BigDecimal("29.58748"))));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.existingItemRateByItemNo())
                .containsEntry("GCL-2026-0001", new BigDecimal("70"))
                .containsEntry("GCL-2026-0002", new BigDecimal("29.58748"));
    }

    @Test
    @DisplayName("load가 기존 편성행과 정규화 사업명 색인을 채운다")
    void load가_기존_편성행과_정규화_사업명_색인을_채운다() {
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2026-0001")
                                        .bseYy("2026")
                                        .costSvnDpmC("571")
                                        .ioeC("011")
                                        .cttOppNm("커브")
                                        .cttNm("유지보수")
                                        .build()));
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(List.of(projectOf("PRJ-2026-0001", "정보화 사업", "571", "N")));
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(true);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(projectItemRepository.findByAbusMngNoInAndDelYn(List.of("PRJ-2026-0001"), "N"))
                .thenReturn(List.of());
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .thenReturn(
                        List.of(
                                Bbugtm.builder()
                                        .fntTbNm("BCOSTM")
                                        .pkColNm("COST-2026-0001")
                                        .ioeC("011")
                                        .asgRt(new BigDecimal("90"))
                                        .build()));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.bseYy()).isEqualTo("2026");
        assertThat(data.projectNoByName("정보화사업")).isEqualTo("PRJ-2026-0001");
        assertThat(data.planExists("신규")).isTrue();
        assertThat(data.planExists("조정")).isFalse();
        assertThat(data.existingCostRateOf("COST-2026-0001")).isEqualByComparingTo("90");
        assertThat(data.allCostNos()).containsExactly("COST-2026-0001");
        assertThat(data.allProjectNos()).containsExactly("PRJ-2026-0001");
    }

    @Test
    @DisplayName("존재하지 않는 계획구분·편성률·사업명·품목은 not-found 경로로 null·빈값을 돌려준다")
    void 미등록값은_not_found_경로를_돌려준다() {
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).thenReturn(List.of());
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).thenReturn(List.of());
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(false);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).thenReturn(List.of());

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.projectNoByName("없는사업")).isNull();
        assertThat(data.planExists("신규")).isFalse();
        assertThat(data.costOf("COST-없음")).isNull();
        assertThat(data.costNoByDeptKey("없는키")).isNull();
        assertThat(data.costNosByDeptAndIoe("없음", "999")).isEmpty();
        assertThat(data.itemsOfProject("PRJ-없음")).isEmpty();
        assertThat(data.existingCostRateOf("COST-없음")).isNull();
        assertThat(data.existingItemRateByItemNo()).doesNotContainKey("GCL-없음");
        assertThat(data.allProjectNos()).isEmpty();
        assertThat(data.allCostNos()).isEmpty();
    }

    @Test
    @DisplayName("같은 정규화 사업명이 중복되면 먼저 나온 사업관리번호를 유지한다")
    void 중복_사업명은_먼저나온값을_유지한다() {
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).thenReturn(List.of());
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(
                        List.of(
                                projectOf("PRJ-2026-0001", "정보화 사업", "571", "N"),
                                projectOf("PRJ-2026-0002", "정보화사업", "571", "N")));
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(false);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(projectItemRepository.findByAbusMngNoInAndDelYn(anyList(), eq("N")))
                .thenReturn(List.of());
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).thenReturn(List.of());

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.projectNoByName("정보화사업")).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("같은 전산업무비의 편성행이 중복되면 먼저 나온 값을 유지한다")
    void 중복_편성률원천은_먼저나온값을_유지한다() {
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).thenReturn(List.of());
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N")).thenReturn(List.of());
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(false);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .thenReturn(
                        List.of(
                                Bbugtm.builder()
                                        .fntTbNm("BCOSTM")
                                        .pkColNm("COST-1")
                                        .ioeC("011")
                                        .asgRt(new BigDecimal("80"))
                                        .build(),
                                Bbugtm.builder()
                                        .fntTbNm("BCOSTM")
                                        .pkColNm("COST-1")
                                        .ioeC("012")
                                        .asgRt(new BigDecimal("50"))
                                        .build()));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.existingCostRateOf("COST-1")).isEqualByComparingTo("80");
    }

    @Test
    @DisplayName("costDeptKey는 null 값을 빈 문자열로 접는다")
    void costDeptKey는_null값을_빈문자열로_접는다() {
        String key = MigrationYearSnapshot.costDeptKey(null, null, null, null, null);

        assertThat(key).isEqualTo("||||");
    }

    @Test
    @DisplayName("normalizeText는 공백을 모두 제거하고 소문자로 접는다")
    void normalizeText는_공백을_제거하고_소문자로_접는다() {
        assertThat(MigrationYearSnapshot.normalizeText(" Curve Inc ")).isEqualTo("curveinc");
        assertThat(MigrationYearSnapshot.normalizeText(null)).isEmpty();
    }

    @Test
    @DisplayName("normalizeName은 공백을 모두 압축한다")
    void normalizeName은_공백을_압축한다() {
        assertThat(MigrationYearSnapshot.normalizeName("정보화   사업 이관")).isEqualTo("정보화사업이관");
        assertThat(MigrationYearSnapshot.normalizeName(null)).isEmpty();
    }

    private Bprojm projectOf(String no, String name, String deptCode, String odnYn) {
        return Bprojm.builder()
                .abusMngNo(no)
                .sno(1)
                .abusNm(name)
                .svnDpmC(deptCode)
                .odnYn(odnYn)
                .bseYy("2026")
                .lstYn("Y")
                .build();
    }

    private Bbugtm budgetOf(String table, String pk, String ioeC, BigDecimal rate) {
        return Bbugtm.builder()
                .bgNo("BG-2026-0001")
                .sno(1)
                .bseYy("2026")
                .fntTbNm(table)
                .pkColNm(pk)
                .fntTbCrySno(1)
                .ioeC(ioeC)
                .asgRt(rate)
                .build();
    }
}
