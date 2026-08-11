package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 연도 스냅샷 로드와 자연키·이름 정규화 규칙을 고정합니다 (§6.2, §7 5단계). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MigrationYearSnapshotTest {

    @Mock private CostRepository costRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private BbugtmRepository bbugtmRepository;
    @Mock private BplanmRepository planRepository;

    @InjectMocks private MigrationYearSnapshot snapshot;

    @Test
    @DisplayName("load가 다섯 개 컬렉션을 모두 채운다")
    void load가_다섯_컬렉션을_채운다() {
        when(costRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(
                        List.of(
                                Bcostm.builder()
                                        .costBgNo("COST-2026-0001")
                                        .bseYy("2026")
                                        .bgUntAbusC("571")
                                        .ioeC("011")
                                        .cttOppNm("커브")
                                        .cttNm("유지보수")
                                        .build()));
        when(projectRepository.findByBseYyAndLstYnAndDelYn("2026", "Y", "N"))
                .thenReturn(
                        List.of(
                                Bprojm.builder()
                                        .abusMngNo("PRJ-2026-0001")
                                        .abusNm("정보화 사업")
                                        .build()));
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(true);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N"))
                .thenReturn(
                        List.of(
                                Bbugtm.builder()
                                        .fntTbNm("BPROJM")
                                        .pkColNm("PRJ-2026-0001")
                                        .asgRt(80)
                                        .build()));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.bseYy()).isEqualTo("2026");
        assertThat(data.costNaturalKeys())
                .containsExactly(
                        MigrationYearSnapshot.costNaturalKey("2026", "571", "011", "커브", "유지보수"));
        assertThat(data.projectNoByName("정보화사업")).isEqualTo("PRJ-2026-0001");
        assertThat(data.planExists("신규")).isTrue();
        assertThat(data.planExists("조정")).isFalse();
        assertThat(data.existingRateOf("BPROJM", "PRJ-2026-0001")).isEqualTo(80);
        assertThat(data.allCostNos()).containsExactly("COST-2026-0001");
        assertThat(data.allProjectNos()).containsExactly("PRJ-2026-0001");
    }

    @Test
    @DisplayName("존재하지 않는 계획구분·편성률·사업명은 not-found 경로로 null·false를 돌려준다")
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
        assertThat(data.existingRateOf("BPROJM", "PRJ-없음")).isNull();
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
                                Bprojm.builder()
                                        .abusMngNo("PRJ-2026-0001")
                                        .abusNm("정보화 사업")
                                        .build(),
                                Bprojm.builder()
                                        .abusMngNo("PRJ-2026-0002")
                                        .abusNm("정보화사업")
                                        .build()));
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "신규", "N"))
                .thenReturn(false);
        when(planRepository.existsByBseYyAndItPtlPlnTpCAndDelYn("2026", "조정", "N"))
                .thenReturn(false);
        when(bbugtmRepository.findByBseYyAndDelYn("2026", "N")).thenReturn(List.of());

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.projectNoByName("정보화사업")).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("같은 원천(테이블,PK)의 편성률이 중복되면 먼저 나온 값을 유지한다")
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
                                        .fntTbNm("BPROJM")
                                        .pkColNm("PRJ-1")
                                        .asgRt(80)
                                        .build(),
                                Bbugtm.builder()
                                        .fntTbNm("BPROJM")
                                        .pkColNm("PRJ-1")
                                        .asgRt(50)
                                        .build()));

        MigrationYearSnapshot.Data data = snapshot.load("2026");

        assertThat(data.existingRateOf("BPROJM", "PRJ-1")).isEqualTo(80);
    }

    @Test
    @DisplayName("엔티티를 받는 costNaturalKey와 값을 받는 오버로드가 같은 키를 만든다")
    void 두_costNaturalKey_오버로드가_같은_키를_만든다() {
        Bcostm cost =
                Bcostm.builder()
                        .bseYy("2026")
                        .bgUntAbusC("571")
                        .ioeC("011")
                        .cttOppNm(" 커브 ")
                        .cttNm(" 유지보수 ")
                        .build();

        String fromEntity = MigrationYearSnapshot.costNaturalKey(cost);
        String fromValues =
                MigrationYearSnapshot.costNaturalKey("2026", "571", "011", "커브", "유지보수");

        assertThat(fromEntity).isEqualTo(fromValues);
    }

    @Test
    @DisplayName("costNaturalKey는 null 값을 빈 문자열로 접는다")
    void costNaturalKey는_null값을_빈문자열로_접는다() {
        String key = MigrationYearSnapshot.costNaturalKey(null, null, null, null, null);

        assertThat(key).isEqualTo("||||");
    }

    @Test
    @DisplayName("normalizeName은 공백을 모두 압축한다")
    void normalizeName은_공백을_압축한다() {
        assertThat(MigrationYearSnapshot.normalizeName("정보화   사업 이관")).isEqualTo("정보화사업이관");
        assertThat(MigrationYearSnapshot.normalizeName(null)).isEmpty();
    }
}
