package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestFormArchiveGroupTest {

    @Test
    @DisplayName("번호 사업 폴더 아래의 요청서와 견적서를 같은 그룹으로 묶는다")
    void groupsNumberedBusinessFolderAndDescendants() {
        assertThat(RequestFormArchiveGroup.keyOf("2026/IT기획부(180)/_IT인프라팀/붙임2/01. VDI/요청서.xlsx"))
                .isEqualTo("2026/IT기획부(180)/_IT인프라팀/붙임2/01. VDI");
        assertThat(
                        RequestFormArchiveGroup.keyOf(
                                "2026/IT기획부(180)/_IT인프라팀/붙임2/01. VDI/(견적서)/견적.pdf"))
                .isEqualTo("2026/IT기획부(180)/_IT인프라팀/붙임2/01. VDI");
    }

    @Test
    @DisplayName("번호 사업 폴더가 없으면 부서의 첫 하위 폴더를 그룹으로 쓴다")
    void groupsFirstFolderBelowDepartment() {
        assertThat(RequestFormArchiveGroup.keyOf("2026/IT기획부(180)/_품질관리팀/근거.pdf"))
                .isEqualTo("2026/IT기획부(180)/_품질관리팀");
    }

    @Test
    @DisplayName("부서 직속 파일과 Windows 구분자를 정규화한다")
    void groupsDepartmentRootAndNormalizesSeparators() {
        assertThat(RequestFormArchiveGroup.keyOf("2026\\PF2실(127)\\요청서.xls"))
                .isEqualTo("2026/PF2실(127)");
    }

    @Test
    @DisplayName("코드 부서가 없으면 첫 폴더를 부서로 사용한다")
    void fallsBackToFirstFolder() {
        assertThat(RequestFormArchiveGroup.keyOf("런던지점/2026/붙임.xls")).isEqualTo("런던지점/2026");
    }

    @Test
    @DisplayName("폴더가 없는 파일은 보관 그룹을 만들지 않는다")
    void returnsEmptyWithoutFolder() {
        assertThat(RequestFormArchiveGroup.keyOf("요청서.xlsx")).isEmpty();
        assertThat(RequestFormArchiveGroup.keyOf(null)).isEmpty();
    }
}
