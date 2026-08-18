package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 신청서 스냅샷 JSON 파싱과 금액 파생값을 검증한다. */
class ApprovalMailSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("신청서 스냅샷의 사업·전산업무비 배열을 읽는다")
    void parse_readsProjectsAndCosts() throws Exception {
        String json =
                """
                {
                  "form": {"id": "IT_BUDGET", "version": 1},
                  "projects": [
                    {"abusNm": "차세대 시스템", "odnYn": "N", "totRqmAmt": 1000,
                     "assetBg": 600, "costBg": 400}
                  ],
                  "costs": [
                    {"cttNm": "유지보수 계약", "costTotXpAmt": 500, "assetBg": 200}
                  ],
                  "approvalLine": {"approvers": []}
                }
                """;

        ApprovalMailSnapshot snapshot = objectMapper.readValue(json, ApprovalMailSnapshot.class);

        assertThat(snapshot.projects()).hasSize(1);
        assertThat(snapshot.projects().get(0).abusNm()).isEqualTo("차세대 시스템");
        assertThat(snapshot.costs()).hasSize(1);
        assertThat(snapshot.costs().get(0).cttNm()).isEqualTo("유지보수 계약");
    }

    @Test
    @DisplayName("모르는 필드가 있어도 파싱이 깨지지 않는다")
    void parse_ignoresUnknownFields() throws Exception {
        String json =
                "{\"projects\": [{\"abusNm\": \"사업\", \"future\": \"값\"}], \"costs\": [],"
                        + " \"extra\": 1}";

        ApprovalMailSnapshot snapshot = objectMapper.readValue(json, ApprovalMailSnapshot.class);

        assertThat(snapshot.projects()).hasSize(1);
    }

    @Test
    @DisplayName("배열이 없으면 빈 목록으로 접는다")
    void parse_missingArrays_returnsEmptyLists() throws Exception {
        ApprovalMailSnapshot snapshot = objectMapper.readValue("{}", ApprovalMailSnapshot.class);

        assertThat(snapshot.projects()).isEmpty();
        assertThat(snapshot.costs()).isEmpty();
        assertThat(ApprovalMailSnapshot.empty().projects()).isEmpty();
    }

    @Test
    @DisplayName("경상여부는 odnYn이 Y일 때만 참이다")
    void projectItem_ordinaryFlag() {
        assertThat(new ApprovalMailSnapshot.ProjectItem("a", "Y", null, null, null).ordinary())
                .isTrue();
        assertThat(new ApprovalMailSnapshot.ProjectItem("a", "N", null, null, null).ordinary())
                .isFalse();
        assertThat(new ApprovalMailSnapshot.ProjectItem("a", null, null, null, null).ordinary())
                .isFalse();
    }

    @Test
    @DisplayName("null 금액은 0으로 취급한다")
    void nullAmounts_treatedAsZero() {
        ApprovalMailSnapshot.ProjectItem project =
                new ApprovalMailSnapshot.ProjectItem("a", "N", null, null, null);

        assertThat(project.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(project.asset()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(project.cost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("전산업무비 일반관리비는 총 예산에서 자본예산을 뺀 값이다")
    void costItem_costIsTotalMinusAsset() {
        ApprovalMailSnapshot.CostItem cost =
                new ApprovalMailSnapshot.CostItem(
                        "계약", new BigDecimal("500"), new BigDecimal("200"));

        assertThat(cost.total()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(cost.asset()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(cost.cost()).isEqualByComparingTo(new BigDecimal("300"));
    }
}
