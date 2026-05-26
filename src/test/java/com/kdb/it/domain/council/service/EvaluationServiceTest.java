package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;

/**
 * EvaluationService 단위 테스트
 *
 * <p>
 * 평가의견 서비스의 저장(upsert)·조회·상태 전이 메서드를 검증합니다.
 * Bevalm·Basctm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CouncilService·EvaluationRepository·UserRepository는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceTest {

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CouncilService councilService;

    @InjectMocks
    private EvaluationService evaluationService;

    private static final String ASCT_ID = "ASCT-2026-0001";
    private static final String ENO = "E10001";

    private CustomUserDetails mockUser(String eno) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getEno()).willReturn(eno);
        return user;
    }

    private CouncilDto.EvaluationItem item(String code, int score, String opnn) {
        return new CouncilDto.EvaluationItem(code, score, opnn);
    }

    // ───────────────────────────────────────────────────────
    // saveEvaluation — 유효성 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvaluation: 1점 입력 시 의견이 없으면 IllegalArgumentException을 던진다")
    void saveEvaluation_1점의견미작성_IllegalArgumentException발생() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CouncilDto.EvaluationRequest request = new CouncilDto.EvaluationRequest(
                List.of(item("MGMT_STR", 1, null)));

        assertThatThrownBy(() -> evaluationService.saveEvaluation(
                ASCT_ID, request, mockUser(ENO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
    }

    @Test
    @DisplayName("saveEvaluation: 3점 이상이면 의견이 없어도 정상 저장된다")
    void saveEvaluation_3점의견없음_정상저장() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("IN_PROGRESS");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "MGMT_STR", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("MGMT_STR", 3, null))),
                mockUser(ENO));

        verify(evaluationRepository).save(any(Bevalm.class));
    }

    // ───────────────────────────────────────────────────────
    // saveEvaluation — upsert
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvaluation: 기존 평가의견이 있으면 update()를 호출한다")
    void saveEvaluation_기존평가있으면_update호출() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("EVALUATING");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        Bevalm existing = mock(Bevalm.class);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "FIN_EFC", "N"))
                .willReturn(Optional.of(existing));

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("FIN_EFC", 4, "좋음"))),
                mockUser(ENO));

        verify(existing).update(4, "좋음");
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveEvaluation: 기존 평가의견이 없으면 신규 저장한다")
    void saveEvaluation_기존평가없으면_save호출() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("IN_PROGRESS");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "RISK_IMP", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("RISK_IMP", 5, null))),
                mockUser(ENO));

        verify(evaluationRepository).save(any(Bevalm.class));
    }

    // ───────────────────────────────────────────────────────
    // saveEvaluation — 상태 전이
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvaluation: IN_PROGRESS 상태이면 EVALUATING으로 전이한다")
    void saveEvaluation_IN_PROGRESS상태이면_EVALUATING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("IN_PROGRESS");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "ETC", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("ETC", 5, null))),
                mockUser(ENO));

        verify(councilService).changeStatus(ASCT_ID, "EVALUATING");
    }

    @Test
    @DisplayName("saveEvaluation: 이미 EVALUATING 상태이면 상태 전이를 건너뛴다")
    void saveEvaluation_EVALUATING상태이면_전이skip() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("EVALUATING");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "ETC", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("ETC", 5, null))),
                mockUser(ENO));

        verify(councilService, never()).changeStatus(any(), any());
    }

    // ───────────────────────────────────────────────────────
    // getMyEvaluation
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyEvaluation: 본인의 평가의견 목록을 DTO로 반환한다")
    void getMyEvaluation_본인평가목록반환() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getCkgItmC()).willReturn("MGMT_STR");
        given(eval.getCkgRcrd()).willReturn(4);
        given(eval.getCkgOpnn()).willReturn("의견");
        given(evaluationRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(eval));

        List<CouncilDto.EvaluationItemResponse> result =
                evaluationService.getMyEvaluation(ASCT_ID, mockUser(ENO));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ckgItmC()).isEqualTo("MGMT_STR");
        assertThat(result.get(0).ckgRcrd()).isEqualTo(4);
    }

    // ───────────────────────────────────────────────────────
    // getAllEvaluations
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllEvaluations: 전체 평가의견 목록과 평균점수를 반환한다")
    void getAllEvaluations_전체평가목록반환() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getCkgItmC()).willReturn("MGMT_STR");
        given(eval.getCkgRcrd()).willReturn(4);
        given(eval.getCkgOpnn()).willReturn("좋음");
        given(evaluationRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(eval));

        given(userRepository.findByEno(ENO)).willReturn(java.util.Optional.empty());
        given(evaluationRepository.findAverageScoreByItem(ASCT_ID, "N")).willReturn(List.of());

        CouncilDto.EvaluationSummaryResponse result =
                evaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.evaluations()).hasSize(1);
        assertThat(result.evaluations().get(0).ckgItmC()).isEqualTo("MGMT_STR");
        assertThat(result.avgScores()).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // buildAvgScores
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("buildAvgScores: 항목별 평균점수 DTO를 반환한다")
    void buildAvgScores_평균점수반환() {
        Object[] row = new Object[]{"MGMT_STR", 4.0};
        given(evaluationRepository.findAverageScoreByItem(ASCT_ID, "N"))
                .willReturn(java.util.Collections.singletonList(row));

        List<CouncilDto.CheckItemAvgScore> result =
                evaluationService.buildAvgScores(ASCT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ckgItmC()).isEqualTo("MGMT_STR");
        assertThat(result.get(0).avgScore()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("saveEvaluation: 2점 의견이 공백이면 IllegalArgumentException을 던진다")
    void saveEvaluation_2점공백의견_IllegalArgumentException발생() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        assertThatThrownBy(() -> evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("UNKNOWN", 2, "   "))),
                mockUser(ENO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("saveEvaluation: 전원이 6개 항목을 제출하면 RESULT_WRITING으로 전이한다")
    void saveEvaluation_전원제출완료_RESULT_WRITING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctStsC()).willReturn("EVALUATING");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByAsctIdAndEnoAndCkgItmCAndDelYn(ASCT_ID, ENO, "ETC", "N"))
                .willReturn(Optional.empty());

        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(ENO);
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(member));
        List<Bevalm> submitted = List.of(
                evalOf("MGMT_STR"), evalOf("FIN_EFC"), evalOf("RISK_IMP"),
                evalOf("REP_IMP"), evalOf("DUP_SYS"), evalOf("ETC"));
        given(evaluationRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(submitted);

        evaluationService.saveEvaluation(ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("ETC", 5, null))),
                mockUser(ENO));

        verify(councilService).changeStatus(ASCT_ID, "RESULT_WRITING");
    }

    @Test
    @DisplayName("getAllEvaluations: 사용자 정보가 있으면 평가 응답에 이름을 포함한다")
    void getAllEvaluations_사용자정보있음_이름포함() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getCkgItmC()).willReturn("UNKNOWN");
        given(eval.getCkgRcrd()).willReturn(3);
        given(evaluationRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(eval));
        com.kdb.it.common.iam.entity.CuserI user = mock(com.kdb.it.common.iam.entity.CuserI.class);
        given(user.getEno()).willReturn(ENO);
        given(user.getUsrNm()).willReturn("홍길동");
        given(userRepository.findByEno(ENO)).willReturn(Optional.of(user));
        given(evaluationRepository.findAverageScoreByItem(ASCT_ID, "N"))
                .willReturn(java.util.Collections.singletonList(new Object[]{"UNKNOWN", 2.5}));

        CouncilDto.EvaluationSummaryResponse result = evaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations().get(0).usrNm()).isEqualTo("홍길동");
        assertThat(result.evaluations().get(0).ckgItmNm()).isEqualTo("UNKNOWN");
    }

    private Bevalm evalOf(String itemCode) {
        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getCkgItmC()).willReturn(itemCode);
        return eval;
    }
}
