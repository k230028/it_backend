package com.kdb.it.domain.migration.request.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormSheetKindTest {

    @Test
    @DisplayName("양식 그대로의 시트명 4종을 각각 판별한다")
    void classifiesAllFormSheetNames() {
        assertThat(FormSheetKind.ofSheetName("① (정보화사업) 1-1. 정보화사업 개요"))
                .contains(FormSheetKind.CAPITAL_OVERVIEW);
        assertThat(FormSheetKind.ofSheetName("① (정보화사업) 1-2. 소요자원 상세내용"))
                .contains(FormSheetKind.CAPITAL_RESOURCE);
        assertThat(FormSheetKind.ofSheetName("② (경상사업) 2. 경상적인 사업"))
                .contains(FormSheetKind.RECURRING);
        assertThat(FormSheetKind.ofSheetName("③ (일반관리비) 전산 일반관리비 편성요청서"))
                .contains(FormSheetKind.GENERAL_EXPENSE);
    }

    @Test
    @DisplayName("부점이 시트명 앞뒤에 덧붙여도 키워드로 판별한다")
    void classifiesDecoratedSheetNames() {
        assertThat(FormSheetKind.ofSheetName("자금운용실 ① (정보화사업) 1-1. 정보화사업 개요 (최종)"))
                .contains(FormSheetKind.CAPITAL_OVERVIEW);
        assertThat(FormSheetKind.ofSheetName("  ③  일반관리비  "))
                .contains(FormSheetKind.GENERAL_EXPENSE);
    }

    @Test
    @DisplayName("어느 키워드에도 걸리지 않으면 빈 Optional을 돌려준다")
    void returnsEmptyForUnknownSheet() {
        assertThat(FormSheetKind.ofSheetName("Sheet1")).isEmpty();
        assertThat(FormSheetKind.ofSheetName("환율 기준")).isEmpty();
    }

    @Test
    @DisplayName("null·공백 시트명은 빈 Optional을 돌려준다")
    void returnsEmptyForBlankSheetName() {
        assertThat(FormSheetKind.ofSheetName(null)).isEmpty();
        assertThat(FormSheetKind.ofSheetName("")).isEmpty();
        assertThat(FormSheetKind.ofSheetName("   ")).isEmpty();
    }
}
