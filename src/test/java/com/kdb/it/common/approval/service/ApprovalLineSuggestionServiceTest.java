package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApplicationDto.ApprovalLineSuggestion.SuggestionReason;
import com.kdb.it.common.code.dto.CodeDto;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 국내점포 결재라인 자동지정 판정 규칙을 고정합니다 (설계 §7.1). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalLineSuggestionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CodeService codeService;
    @InjectMocks private ApprovalLineSuggestionService service;

    private static CuserI user(String eno, String bbrC, String temC, String ptC, String name) {
        return CuserI.builder().eno(eno).bbrC(bbrC).temC(temC).ptC(ptC).usrNm(name).build();
    }

    private static CodeDto.Response code(String cdva, String tier) {
        return CodeDto.Response.builder().cdva(cdva).cdvaDtlC(tier).build();
    }

    @BeforeEach
    void seedPositionCodes() {
        given(codeService.getCcodemsByCId(eq("IT_PTL_APF_DCR_PT_C"), any()))
                .willReturn(
                        List.of(
                                code("B1EX", "1"),
                                code("I3EX", "1"),
                                code("B1AX", "2"),
                                code("B1CX", "2"),
                                code("B1BX", "2"),
                                code("B1AY", "2"),
                                code("B1GX", "2"),
                                code("Z2C", "2")));
        given(userRepository.findByEno("K10001"))
                .willReturn(Optional.of(user("K10001", "120", "T01", "B2AX", "기안자")));
    }

    @Test
    @DisplayName("같은 팀 팀장 1명, 같은 부점 부장 1명이면 둘 다 지정한다")
    void suggestsBothWhenSingleCandidates() {
        given(
                        userRepository.findByBbrCAndTemCAndPtCInAndDelYn(
                                eq("120"), eq("T01"), any(), eq("N")))
                .willReturn(List.of(user("K20001", "120", "T01", "B1EX", "팀장")));
        given(userRepository.findByBbrCAndPtCInAndDelYn(eq("120"), any(), eq("N")))
                .willReturn(List.of(user("K30001", "120", "T09", "B1AX", "부장")));

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.isForeignBranch()).isFalse();
        assertThat(result.getTeamLead().getEno()).isEqualTo("K20001");
        assertThat(result.getDeptHead().getEno()).isEqualTo("K30001");
        assertThat(result.getTeamLeadReason()).isNull();
        assertThat(result.getDeptHeadReason()).isNull();
    }

    @Test
    @DisplayName("후보가 없으면 NONE, 2명 이상이면 MULTIPLE 사유로 비운다")
    void reportsNoneAndMultiple() {
        given(
                        userRepository.findByBbrCAndTemCAndPtCInAndDelYn(
                                eq("120"), eq("T01"), any(), eq("N")))
                .willReturn(List.of());
        given(userRepository.findByBbrCAndPtCInAndDelYn(eq("120"), any(), eq("N")))
                .willReturn(
                        List.of(
                                user("K30001", "120", "T09", "B1AX", "부장"),
                                user("K30002", "120", "T08", "B1CX", "실장")));

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.getTeamLead()).isNull();
        assertThat(result.getTeamLeadReason()).isEqualTo(SuggestionReason.NONE);
        assertThat(result.getDeptHead()).isNull();
        assertThat(result.getDeptHeadReason()).isEqualTo(SuggestionReason.MULTIPLE);
    }

    @Test
    @DisplayName("사번 접두사가 K가 아니거나 기안자 본인이면 후보에서 뺀다")
    void excludesNonPrefixAndSelf() {
        given(userRepository.findByEno("K10001"))
                .willReturn(Optional.of(user("K10001", "120", "T01", "B1EX", "팀장 기안자")));
        given(
                        userRepository.findByBbrCAndTemCAndPtCInAndDelYn(
                                eq("120"), eq("T01"), any(), eq("N")))
                .willReturn(
                        List.of(
                                user("K10001", "120", "T01", "B1EX", "팀장 기안자"),
                                user("A20002", "120", "T01", "I3EX", "외부 CO")));
        given(userRepository.findByBbrCAndPtCInAndDelYn(eq("120"), any(), eq("N")))
                .willReturn(List.of(user("K30001", "120", "T09", "B1AX", "부장")));

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.getTeamLead()).isNull();
        assertThat(result.getTeamLeadReason()).isEqualTo(SuggestionReason.NONE);
        assertThat(result.getDeptHead().getEno()).isEqualTo("K30001");
    }

    @Test
    @DisplayName("1차와 2차 후보가 같은 사람이면 두 차수에 모두 지정한다")
    void suggestsSamePersonForBothSteps() {
        given(
                        userRepository.findByBbrCAndTemCAndPtCInAndDelYn(
                                eq("120"), eq("T01"), any(), eq("N")))
                .willReturn(List.of(user("K20001", "120", "T01", "B1EX", "팀장 겸 부장")));
        given(userRepository.findByBbrCAndPtCInAndDelYn(eq("120"), any(), eq("N")))
                .willReturn(List.of(user("K20001", "120", "T01", "B1EX", "팀장 겸 부장")));

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.getTeamLead().getEno()).isEqualTo("K20001");
        assertThat(result.getDeptHead().getEno()).isEqualTo("K20001");
        assertThat(result.getDeptHeadReason()).isNull();
    }

    @Test
    @DisplayName("국외점포(부점코드 9로 시작) 기안자는 자동지정하지 않는다")
    void skipsForeignBranch() {
        given(userRepository.findByEno("K10001"))
                .willReturn(Optional.of(user("K10001", "920", "T01", "B2AX", "런던")));

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.isForeignBranch()).isTrue();
        assertThat(result.getTeamLead()).isNull();
        assertThat(result.getDeptHead()).isNull();
    }

    @Test
    @DisplayName("직위코드 그룹이 비어 있으면 두 차수 모두 NONE으로 응답한다")
    void reportsNoneWhenCodeGroupEmpty() {
        given(codeService.getCcodemsByCId(anyString(), any())).willReturn(List.of());

        ApplicationDto.ApprovalLineSuggestion result = service.suggest("K10001");

        assertThat(result.getTeamLeadReason()).isEqualTo(SuggestionReason.NONE);
        assertThat(result.getDeptHeadReason()).isEqualTo(SuggestionReason.NONE);
    }

    @Test
    @DisplayName("기안자를 찾지 못하면 IllegalArgumentException")
    void throwsWhenDrafterMissing() {
        given(userRepository.findByEno("NOBODY")).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.suggest("NOBODY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
