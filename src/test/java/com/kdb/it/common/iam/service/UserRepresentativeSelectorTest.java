package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UserRepresentativeSelector 단위 테스트 — 팀 대표자 결정적 선택 규칙 (BE-10) */
class UserRepresentativeSelectorTest {

    private CuserI user(String eno, String ptCNm) {
        return CuserI.builder().eno(eno).ptCNm(ptCNm).build();
    }

    @Test
    @DisplayName("팀장이 있으면 입력 순서와 무관하게 팀장을 선택한다")
    void teamLead_preferred() {
        CuserI staff = user("E0001", "과장");
        CuserI lead = user("E0009", "팀장");
        assertThat(UserRepresentativeSelector.pick(List.of(staff, lead))).contains(lead);
    }

    @Test
    @DisplayName("팀장이 없으면 사번 오름차순 첫 번째를 선택한다")
    void noLead_lowestEno() {
        CuserI second = user("E0002", "과장");
        CuserI first = user("E0001", "차장");
        assertThat(UserRepresentativeSelector.pick(List.of(second, first))).contains(first);
    }

    @Test
    @DisplayName("팀장이 여러 명이면 팀장 중 사번 오름차순 첫 번째를 선택한다")
    void multipleLeads_lowestEnoAmongLeads() {
        CuserI leadB = user("E0005", "팀장");
        CuserI leadA = user("E0003", "팀장");
        CuserI staff = user("E0001", "과장");
        assertThat(UserRepresentativeSelector.pick(List.of(leadB, staff, leadA))).contains(leadA);
    }

    @Test
    @DisplayName("빈 목록이면 empty를 반환한다")
    void emptyList_returnsEmpty() {
        assertThat(UserRepresentativeSelector.pick(List.of())).isEmpty();
    }

    @Test
    @DisplayName("프로젝션 대표자는 팀장을 우선하고 같은 직위에서는 사번 오름차순으로 선택한다")
    void pickView_prefersTeamLeadThenLowestEno() {
        UserRepository.CommitteeUserRow staff = committeeUser("E0001", "과장");
        UserRepository.CommitteeUserRow secondLead = committeeUser("E0005", "팀장");
        UserRepository.CommitteeUserRow firstLead = committeeUser("E0003", "팀장");

        assertThat(UserRepresentativeSelector.pickView(List.of(staff, secondLead, firstLead)))
                .contains(firstLead);
    }

    @Test
    @DisplayName("프로젝션 사용자 목록이 비어 있으면 대표자가 없다")
    void pickView_empty_returnsEmpty() {
        assertThat(UserRepresentativeSelector.pickView(List.of())).isEmpty();
    }

    private UserRepository.CommitteeUserRow committeeUser(String eno, String ptCNm) {
        UserRepository.CommitteeUserRow row = mock(UserRepository.CommitteeUserRow.class);
        when(row.getEno()).thenReturn(eno);
        when(row.getPtCNm()).thenReturn(ptCNm);
        return row;
    }
}
