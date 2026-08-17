package com.kdb.it.domain.migration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RowDecisionTest {

    @Test
    @DisplayName("parse_MATCH는_원장_PK를_담는다")
    void parse_MATCH는_원장_PK를_담는다() {
        RowDecision decision = RowDecision.parse("MATCH:PRJ-2026-0001");

        assertThat(decision.kind()).isEqualTo(RowDecision.Kind.MATCH);
        assertThat(decision.pk()).isEqualTo("PRJ-2026-0001");
    }

    @Test
    @DisplayName("parse_CREATE_NEW와_SKIP은_PK가_없다")
    void parse_CREATE_NEW와_SKIP은_PK가_없다() {
        assertThat(RowDecision.parse("CREATE_NEW").kind()).isEqualTo(RowDecision.Kind.CREATE_NEW);
        assertThat(RowDecision.parse("CREATE_NEW").pk()).isNull();
        assertThat(RowDecision.parse("SKIP").kind()).isEqualTo(RowDecision.Kind.SKIP);
    }

    @Test
    @DisplayName("parse_미인식_값은_null이다")
    void parse_미인식_값은_null이다() {
        assertThat(RowDecision.parse(null)).isNull();
        assertThat(RowDecision.parse("")).isNull();
        assertThat(RowDecision.parse("MATCH:")).isNull();
        assertThat(RowDecision.parse("무엇인가")).isNull();
    }

    @Test
    @DisplayName("decisionCandidates_원장후보_뒤에_생성과_제외가_붙는다")
    void decisionCandidates_원장후보_뒤에_생성과_제외가_붙는다() {
        List<MigrationDto.Candidate> candidates =
                RowDecision.decisionCandidates(
                        List.of(new MigrationDto.Candidate("PRJ-2026-0001", "웹한글 기안기 도입")),
                        SheetKind.CAPITAL_PROJECT);

        assertThat(candidates)
                .extracting(MigrationDto.Candidate::code)
                .containsExactly("MATCH:PRJ-2026-0001", "CREATE_NEW", "SKIP");
        assertThat(candidates.get(0).label()).isEqualTo("기존 사업에 편성: 웹한글 기안기 도입");
    }

    /**
     * 부문계획 조정 시트는 원장을 만들지 않으므로 {@code CREATE_NEW}를 후보로 내지 않습니다.
     *
     * <p>{@code PlanAdjustmentSheetAdapter}는 {@code projects}·{@code costs}를 아예 만들지 않아, 이 결정을 받아도
     * {@code MigrationImportService.hasCreateRequest}가 false를 돌려주고 그 행의 조정이 통째로 사라집니다.
     */
    @Test
    @DisplayName("decisionCandidates_원장을_만들_수_없는_시트에는_생성_선택지가_없다")
    void decisionCandidates_원장을_만들_수_없는_시트에는_생성_선택지가_없다() {
        List<MigrationDto.Candidate> candidates =
                RowDecision.decisionCandidates(
                        List.of(new MigrationDto.Candidate("PRJ-2026-0001", "웹한글 기안기 도입")),
                        SheetKind.PLAN_ADJUSTMENT);

        assertThat(candidates)
                .extracting(MigrationDto.Candidate::code)
                .containsExactly("MATCH:PRJ-2026-0001", "SKIP");
    }

    @Test
    @DisplayName("canCreateLedger는_부문계획만_false다")
    void canCreateLedger는_부문계획만_false다() {
        assertThat(SheetKind.PLAN_ADJUSTMENT.canCreateLedger()).isFalse();
        assertThat(SheetKind.COST.canCreateLedger()).isTrue();
        assertThat(SheetKind.CAPITAL_PROJECT.canCreateLedger()).isTrue();
        assertThat(SheetKind.DELEGATED_BUDGET.canCreateLedger()).isTrue();
    }

    @Test
    @DisplayName("COLUMN은_어떤_시트의_정규컬럼과도_겹치지_않는다")
    void COLUMN은_어떤_시트의_정규컬럼과도_겹치지_않는다() {
        for (SheetKind kind : SheetKind.values()) {
            assertThat(MigrationColumns.of(kind)).doesNotContain(RowDecision.COLUMN);
        }
    }
}
