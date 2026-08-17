package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationLedgerMatcherTest {

    private final MigrationLedgerMatcher matcher = new MigrationLedgerMatcher();

    @Test
    @DisplayName("matchProject_정규화_사업명이_같으면_매칭된다")
    void matchProject_정규화_사업명이_같으면_매칭된다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject("웹한글기안기도입", "PRJ-2026-0001", "웹한글 기안기 도입");

        MigrationLedgerMatcher.Match match = matcher.matchProject("웹한글기안기도입", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.MATCHED);
        assertThat(match.pk()).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("matchProject_없으면_전체_사업을_후보로_낸다")
    void matchProject_없으면_전체_사업을_후보로_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshotWithProject("웹한글기안기도입", "PRJ-2026-0001", "웹한글 기안기 도입");

        MigrationLedgerMatcher.Match match = matcher.matchProject("문자메시지안심마크도입", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.NOT_FOUND);
        assertThat(match.pk()).isNull();
        assertThat(match.candidates())
                .containsExactly(new MigrationDto.Candidate("PRJ-2026-0001", "웹한글 기안기 도입"));
    }

    @Test
    @DisplayName("matchOrdinaryProject_부서에_경상사업이_하나면_매칭된다")
    void matchOrdinaryProject_부서에_경상사업이_하나면_매칭된다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"),
                        Map.of("920", List.of("PRJ-2026-0002")),
                        Map.of(),
                        Map.of(),
                        Map.of());

        MigrationLedgerMatcher.Match match = matcher.matchOrdinaryProject("920", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.MATCHED);
        assertThat(match.pk()).isEqualTo("PRJ-2026-0002");
    }

    @Test
    @DisplayName("matchOrdinaryProject_부서에_경상사업이_둘이면_중의적이다")
    void matchOrdinaryProject_부서에_경상사업이_둘이면_중의적이다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of("PRJ-2026-0002", "런던 위임예산", "PRJ-2026-0003", "런던 PF 위임예산"),
                        Map.of("920", List.of("PRJ-2026-0002", "PRJ-2026-0003")),
                        Map.of(),
                        Map.of(),
                        Map.of());

        MigrationLedgerMatcher.Match match = matcher.matchOrdinaryProject("920", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.AMBIGUOUS);
        assertThat(match.candidates()).hasSize(2);
    }

    @Test
    @DisplayName("matchCost_5요소가_같으면_매칭된다")
    void matchCost_5요소가_같으면_매칭된다() {
        String key = MigrationYearSnapshot.costDeptKey("2026", "0210", "001", "커브", "올인원워크스페이스");
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "COST-26-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0001",
                                        1,
                                        "001",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / 커브")),
                        Map.of(key, "COST-26-0001"),
                        Map.of("0210|001", List.of("COST-26-0001")));

        MigrationLedgerMatcher.Match match =
                matcher.matchCost("2026", "0210", "001", "커브", "올인원워크스페이스", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.MATCHED);
        assertThat(match.pk()).isEqualTo("COST-26-0001");
    }

    @Test
    @DisplayName("matchCost_상대처가_공란이면_상대처를_빼고_다시_찾는다")
    void matchCost_상대처가_공란이면_상대처를_빼고_다시_찾는다() {
        String key = MigrationYearSnapshot.costDeptKey("2026", "0210", "001", "커브", "올인원워크스페이스");
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "COST-26-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0001",
                                        1,
                                        "001",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / 커브")),
                        Map.of(key, "COST-26-0001"),
                        Map.of("0210|001", List.of("COST-26-0001")));

        // 종합본에 상대처가 비어 있어도 부서+비목+계약명이 유일하면 매칭한다
        MigrationLedgerMatcher.Match match =
                matcher.matchCost("2026", "0210", "001", "", "올인원워크스페이스", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.MATCHED);
        assertThat(match.pk()).isEqualTo("COST-26-0001");
    }

    @Test
    @DisplayName("matchCost_상대처에_슬래시가_있어도_계약명_원문으로_비교한다")
    void matchCost_상대처에_슬래시가_있어도_계약명_원문으로_비교한다() {
        // label은 "계약명 / 상대처" 형식이라 상대처에 " / "가 섞이면 문자열 파싱으로는 계약명이 잘못 잘린다.
        // contractName 원문으로 비교하면 이 문제가 생기지 않는다.
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "COST-26-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0001",
                                        1,
                                        "001",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / A사 / B사업부")),
                        Map.of(),
                        Map.of("0210|001", List.of("COST-26-0001")));

        MigrationLedgerMatcher.Match match =
                matcher.matchCost("2026", "0210", "001", "A사 / B사업부", "올인원워크스페이스", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.MATCHED);
        assertThat(match.pk()).isEqualTo("COST-26-0001");
    }

    @Test
    @DisplayName("matchCost_계약명이_달라도_부서와_비목이_같으면_후보를_낸다")
    void matchCost_계약명이_달라도_부서와_비목이_같으면_후보를_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "COST-26-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0001",
                                        1,
                                        "001",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / 커브")),
                        Map.of(),
                        Map.of("0210|001", List.of("COST-26-0001")));

        MigrationLedgerMatcher.Match match =
                matcher.matchCost("2026", "0210", "001", "커브", "전혀 다른 계약명", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.NOT_FOUND);
        assertThat(match.candidates())
                .containsExactly(new MigrationDto.Candidate("COST-26-0001", "올인원워크스페이스 / 커브"));
    }

    @Test
    @DisplayName("matchCost_같은_계약명의_원장행이_둘이면_중의적이다")
    void matchCost_같은_계약명의_원장행이_둘이면_중의적이다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "COST-26-0001",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0001",
                                        1,
                                        "001",
                                        new BigDecimal("15401000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / 커브"),
                                "COST-26-0002",
                                new MigrationYearSnapshot.CostRef(
                                        "COST-26-0002",
                                        1,
                                        "001",
                                        new BigDecimal("2000000"),
                                        "올인원워크스페이스",
                                        "올인원워크스페이스 / 다른상대처")),
                        Map.of(),
                        Map.of("0210|001", List.of("COST-26-0001", "COST-26-0002")));

        MigrationLedgerMatcher.Match match =
                matcher.matchCost("2026", "0210", "001", "제3의상대처", "올인원워크스페이스", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.AMBIGUOUS);
        assertThat(match.candidates()).hasSize(2);
    }

    @Test
    @DisplayName("matchOrdinaryProject_부서코드가_null이면_전체_사업을_후보로_낸다")
    void matchOrdinaryProject_부서코드가_null이면_전체_사업을_후보로_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"),
                        Map.of("920", List.of("PRJ-2026-0002")),
                        Map.of(),
                        Map.of(),
                        Map.of());

        MigrationLedgerMatcher.Match match = matcher.matchOrdinaryProject(null, snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.NOT_FOUND);
        assertThat(match.candidates())
                .containsExactly(
                        new MigrationDto.Candidate("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"));
    }

    @Test
    @DisplayName("matchOrdinaryProject_부서코드가_공백이면_전체_사업을_후보로_낸다")
    void matchOrdinaryProject_부서코드가_공백이면_전체_사업을_후보로_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"),
                        Map.of("920", List.of("PRJ-2026-0002")),
                        Map.of(),
                        Map.of(),
                        Map.of());

        MigrationLedgerMatcher.Match match = matcher.matchOrdinaryProject("  ", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.NOT_FOUND);
        assertThat(match.candidates())
                .containsExactly(
                        new MigrationDto.Candidate("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"));
    }

    @Test
    @DisplayName("matchOrdinaryProject_부서에_경상사업이_없으면_전체_사업을_후보로_낸다")
    void matchOrdinaryProject_부서에_경상사업이_없으면_전체_사업을_후보로_낸다() {
        MigrationYearSnapshot.Data snapshot =
                snapshot(
                        Map.of(),
                        Map.of("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"),
                        Map.of("920", List.of("PRJ-2026-0002")),
                        Map.of(),
                        Map.of(),
                        Map.of());

        MigrationLedgerMatcher.Match match = matcher.matchOrdinaryProject("0210", snapshot);

        assertThat(match.outcome()).isEqualTo(MigrationLedgerMatcher.Outcome.NOT_FOUND);
        assertThat(match.candidates())
                .containsExactly(
                        new MigrationDto.Candidate("PRJ-2026-0002", "2026년 런던지점 위임예산(경상)"));
    }

    private MigrationYearSnapshot.Data snapshotWithProject(
            String normalizedName, String projectNo, String projectName) {
        return snapshot(
                Map.of(normalizedName, projectNo),
                Map.of(projectNo, projectName),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    private MigrationYearSnapshot.Data snapshot(
            Map<String, String> projectNoByName,
            Map<String, String> projectNameByNo,
            Map<String, List<String>> ordinaryByDept,
            Map<String, MigrationYearSnapshot.CostRef> costByNo,
            Map<String, String> costNoByDeptKey,
            Map<String, List<String>> costNosByDeptIoe) {
        return new MigrationYearSnapshot.Data(
                "2026",
                projectNoByName,
                projectNameByNo,
                ordinaryByDept,
                Map.of(),
                costByNo,
                costNoByDeptKey,
                costNosByDeptIoe,
                Map.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                List.copyOf(projectNameByNo.keySet()),
                List.copyOf(costByNo.keySet()));
    }
}
