package com.kdb.it.common.system.tiptap.util;

import com.kdb.it.common.system.tiptap.util.TiptapTokenParser.ParseResult;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TiptapTokenParserTest {

    private final TiptapTokenParser parser = new TiptapTokenParser();

    @Test
    @DisplayName("전산예산 토큰 — 카테고리 prefix 정상 파싱")
    void parse_itBudgetToken_returnsCategoryAndItem() {
        ParseResult result = parser.parse("2026.itBudget.requestAmount");
        assertThat(result.valid()).isTrue();
        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.category()).isEqualTo(Category.IT_BUDGET);
        assertThat(result.projectCode()).isNull();
        assertThat(result.item()).isEqualTo("requestAmount");
    }

    @Test
    @DisplayName("사업별 토큰 — 사업코드 세그먼트 포함 파싱")
    void parse_projToken_returnsProjectCode() {
        ParseResult result = parser.parse("2026.proj.PROJ001.allocationRate");
        assertThat(result.valid()).isTrue();
        assertThat(result.category()).isEqualTo(Category.PROJ);
        assertThat(result.projectCode()).isEqualTo("PROJ001");
        assertThat(result.item()).isEqualTo("allocationRate");
    }

    @Test
    @DisplayName("사업별 토큰 — 사업코드 없으면 INVALID")
    void parse_projWithoutCode_returnsInvalid() {
        assertThat(parser.parse("2026.proj.requestAmount").valid()).isFalse();
    }

    @Test
    @DisplayName("비-사업 카테고리에 사업코드 세그먼트 있으면 INVALID")
    void parse_itBudgetWithCode_returnsInvalid() {
        assertThat(parser.parse("2026.itBudget.PROJ001.requestAmount").valid()).isFalse();
    }

    @Test
    @DisplayName("미지원 항목 → INVALID")
    void parse_unknownItem_returnsInvalid() {
        assertThat(parser.parse("2026.itBudget.unknownField").valid()).isFalse();
    }

    @Test
    @DisplayName("연도 4자리 미만 → INVALID")
    void parse_shortYear_returnsInvalid() {
        assertThat(parser.parse("26.itBudget.requestAmount").valid()).isFalse();
    }

    @Test
    @DisplayName("자본예산·일반관리비 카테고리 인식")
    void parse_capBudgetAndOpex() {
        assertThat(parser.parse("2026.capBudget.allocatedAmount").category()).isEqualTo(Category.CAP_BUDGET);
        assertThat(parser.parse("2026.opex.allocatedAmount").category()).isEqualTo(Category.OPEX);
    }
}
