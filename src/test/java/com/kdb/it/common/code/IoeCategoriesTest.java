package com.kdb.it.common.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.entity.Ccodem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IoeCategoriesTest {

    @ParameterizedTest
    @ValueSource(strings = {"IOE_DVC", "IOE_HW", "IOE_SW", "IOE_CPIT"})
    @DisplayName("isCapitalCTp: 자본예산 계열 코드타입은 true")
    void isCapitalCTp_capitalTypes_returnsTrue(String cTp) {
        assertThat(IoeCategories.isCapitalCTp(cTp)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IOE_IDR", "IOE_SEVS", "IOE_XPN", "IOE_LEAFE"})
    @DisplayName("isCapitalCTp: 일반관리비 계열 코드타입은 false")
    void isCapitalCTp_expenseTypes_returnsFalse(String cTp) {
        assertThat(IoeCategories.isCapitalCTp(cTp)).isFalse();
    }

    @Test
    @DisplayName("isCapitalCTp: null 코드타입은 false")
    void isCapitalCTp_null_returnsFalse() {
        assertThat(IoeCategories.isCapitalCTp(null)).isFalse();
    }

    @Test
    @DisplayName("resolveGroupName: C_TP_DES가 있으면 우선 사용한다")
    void resolveGroupName_prefersCTpDes() {
        Ccodem code =
                Ccodem.builder()
                        .cTpDes("전산임차료")
                        .cdvaDtl("일반관리비 - 전산여비 - 국내출장비")
                        .cdvaDes("무시됨")
                        .build();

        assertThat(IoeCategories.resolveGroupName(code)).isEqualTo("전산임차료");
    }

    @Test
    @DisplayName("resolveGroupName: C_TP_DES가 공백이면 CDVA_DTL 계층의 중분류를 사용한다")
    void resolveGroupName_blankCTpDes_usesCdvaDtlMiddle() {
        Ccodem code =
                Ccodem.builder()
                        .cTpDes("   ")
                        .cdvaDtl("일반관리비 - 전산여비 - 국내출장비")
                        .cdvaDes("무시됨")
                        .build();

        assertThat(IoeCategories.resolveGroupName(code)).isEqualTo("전산여비");
    }

    @Test
    @DisplayName("resolveGroupName: 계층 구분자가 없으면 CDVA_DES로 대체한다")
    void resolveGroupName_noHierarchy_fallsBackToCdvaDes() {
        Ccodem code = Ccodem.builder().cdvaDtl("국내출장비").cdvaDes("전산제비").build();

        assertThat(IoeCategories.resolveGroupName(code)).isEqualTo("전산제비");
    }

    @Test
    @DisplayName("resolveGroupName: 해석 근거가 전혀 없으면 null")
    void resolveGroupName_noSource_returnsNull() {
        Ccodem code = Ccodem.builder().cdva("001").build();

        assertThat(IoeCategories.resolveGroupName(code)).isNull();
    }
}
