package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.support.TestIoeIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IoeHierarchyIndexTest {

    private IoeHierarchyIndex.Snapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = TestIoeIndex.snapshot();
    }

    @Test
    @DisplayName("시트 ③의 (중분류, 세부) 쌍으로 비목을 확정한다")
    void resolvesByDetailPair() {
        assertThat(snapshot.resolveByDetail("전산 임차료", "국내전산임차료").code()).isEqualTo("001");
        assertThat(snapshot.resolveByDetail("전산 여비", "국외출장").code()).isEqualTo("004");
        assertThat(snapshot.resolveByDetail("전산 제비", "회선사용료").code()).isEqualTo("010");
    }

    @Test
    @DisplayName("양식 표기가 공통코드와 달라도 대조표로 되돌려 확정한다")
    void resolvesThroughLexiconAlias() {
        // 양식은 `국외전산유지보수료`, 공통코드 014는 `국외유지보수료`
        assertThat(snapshot.resolveByDetail("전산 제비", "국외전산유지보수료").code()).isEqualTo("014");
        // 영문 양식
        assertThat(snapshot.resolveByDetail("IT Expenses", "Foreign branch line usage fees").code())
                .isEqualTo("013");
        assertThat(snapshot.resolveByDetail("전산 제비", "유지보수").code()).isEqualTo("011");
    }

    @Test
    @DisplayName("외주용역은 외주운영·관제 코드 008로 확정한다")
    void defaultsOutsourcingToOperationsAndMonitoring() {
        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByDetail("전산 용역비", "외주용역");

        assertThat(resolution.isAmbiguous()).isFalse();
        assertThat(resolution.code()).isEqualTo("008");
        assertThat(resolution.label()).isEqualTo("외주용역(외주운영/관제 등)");
        assertThat(resolution.candidates()).isEmpty();
    }

    @Test
    @DisplayName("자문·심사로 구체적으로 적은 외주용역은 코드 009로 확정한다")
    void resolvesExplicitConsultingOutsourcing() {
        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByDetail("전산 용역비", "외주용역(자문/심사)");

        assertThat(resolution.code()).isEqualTo("009");
    }

    @Test
    @DisplayName("대응 코드가 없는 세부비목은 미해석으로 남는다")
    void marksUnknownDetailUnresolved() {
        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByDetail("전산 제비", "국외전산기타제비");

        assertThat(resolution.isUnresolved()).isTrue();
        assertThat(resolution.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("시트 1-2의 중분류와 통화로 자본예산 비목을 정한다")
    void resolvesCapitalByGroupAndCurrency() {
        assertThat(snapshot.resolveByGroup("기계장치(HW)", true).code()).isEqualTo("101");
        assertThat(snapshot.resolveByGroup("기계장치(HW)", false).code()).isEqualTo("102");
        assertThat(snapshot.resolveByGroup("기타무형자산(SW)", false).code()).isEqualTo("105");
        assertThat(snapshot.resolveByGroup("전산임차료", true).code()).isEqualTo("001");
        assertThat(snapshot.resolveByGroup("전산임차료", false).code()).isEqualTo("002");
    }

    @Test
    @DisplayName("기본값이 있는 중분류는 기본 코드와 대안 후보를 함께 준다")
    void offersDefaultWithAlternatives() {
        IoeHierarchyIndex.Resolution development = snapshot.resolveByGroup("개발비", true);
        assertThat(development.code()).isEqualTo("103");
        assertThat(development.candidates())
                .extracting(MigrationDto.Candidate::code)
                .contains("104");

        IoeHierarchyIndex.Resolution software = snapshot.resolveByGroup("기타무형자산(SW)", true);
        assertThat(software.code()).isEqualTo("106");
        assertThat(software.candidates()).extracting(MigrationDto.Candidate::code).contains("107");
    }

    @Test
    @DisplayName("전산제비는 세부가 양식에 없어 항상 중의적이다")
    void marksGeneralExpenseGroupAmbiguous() {
        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByGroup("전산제비", true);

        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.candidates()).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("보정값으로 들어온 비목코드의 실재 여부를 확인한다")
    void checksCodeExistence() {
        assertThat(snapshot.exists("101")).isTrue();
        assertThat(snapshot.exists("999")).isFalse();
        assertThat(snapshot.exists(null)).isFalse();
    }
}
