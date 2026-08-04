package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.dto.EvaluationItemAvgRow;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * EvaluationService 단위 테스트
 *
 * <p>평가의견 서비스의 저장(upsert)·조회·상태 전이 메서드를 검증합니다. Bevalm·Basctm 엔티티는 protected 생성자를 우회하기 위해
 * Mockito.mock()으로 생성합니다. CouncilService·EvaluationRepository·UserRepository는 @Mock으로 교체합니다. Oracle
 * DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationServiceTest {

    @Mock private EvaluationRepository evaluationRepository;

    @Mock private CommitteeRepository committeeRepository;

    @Mock private UserRepository userRepository;

    @Mock private CouncilService councilService;

    @Mock private EntityManager entityManager;

    @InjectMocks private EvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        // 신규 평가 저장은 persist()를 사용하므로 영속성 컨텍스트를 명시적으로 주입한다.
        ReflectionTestUtils.setField(evaluationService, "entityManager", entityManager);
        Bcmmtm member = mock(Bcmmtm.class);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(Optional.of(member));
    }

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

        CouncilDto.EvaluationRequest request =
                new CouncilDto.EvaluationRequest(List.of(item("01", 1, null)));

        assertThatThrownBy(() -> evaluationService.saveEvaluation(ASCT_ID, request, mockUser(ENO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
    }

    @Test
    @DisplayName("saveEvaluation: 3점 이상이면 의견이 없어도 정상 저장된다")
    void saveEvaluation_3점의견없음_정상저장() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of());
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("01", 3, null))),
                mockUser(ENO));

        verify(entityManager).persist(any(Bevalm.class));
    }

    @Test
    @DisplayName("saveEvaluation: 간사(03)는 평가의견을 제출할 수 없다 (AccessDeniedException)")
    void saveEvaluation_간사03_제출차단() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        // 이 위원의 위원유형을 순수 간사(03)로 설정 — 평가의견 작성 대상이 아님
        Bcmmtm secretary = mock(Bcmmtm.class);
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(Optional.of(secretary));

        assertThatThrownBy(
                        () ->
                                evaluationService.saveEvaluation(
                                        ASCT_ID,
                                        new CouncilDto.EvaluationRequest(
                                                List.of(item("01", 3, null))),
                                        mockUser(ENO)))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ───────────────────────────────────────────────────────
    // saveEvaluation — upsert
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvaluation: 기존 평가의견이 있으면 update()를 호출한다")
    void saveEvaluation_기존평가있으면_update호출() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        Bevalm existing = mock(Bevalm.class);
        given(existing.getItPtlCkgItmTc()).willReturn("02");
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(existing));

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("02", 4, "좋음"))),
                mockUser(ENO));

        verify(existing).update(4, "좋음");
        verify(entityManager, never()).persist(any());
    }

    @Test
    @DisplayName("saveEvaluation: 기존 평가의견이 없으면 신규 저장한다")
    void saveEvaluation_기존평가없으면_save호출() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of());
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("03", 5, null))),
                mockUser(ENO));

        verify(entityManager).persist(any(Bevalm.class));
    }

    // ───────────────────────────────────────────────────────
    // saveEvaluation — 상태 전이
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveEvaluation: IN_PROGRESS 상태이면 EVALUATING으로 전이한다")
    void saveEvaluation_IN_PROGRESS상태이면_EVALUATING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("07");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        evaluationRepository.findByItPtlAsctIdAndEnoAndItPtlCkgItmTcAndDelYn(
                                ASCT_ID, ENO, "06", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("06", 5, null))),
                mockUser(ENO));

        verify(councilService).changeStatus(ASCT_ID, "08");
    }

    @Test
    @DisplayName("saveEvaluation: 이미 EVALUATING 상태이면 상태 전이를 건너뛴다")
    void saveEvaluation_EVALUATING상태이면_전이skip() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        evaluationRepository.findByItPtlAsctIdAndEnoAndItPtlCkgItmTcAndDelYn(
                                ASCT_ID, ENO, "06", "N"))
                .willReturn(Optional.empty());
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("06", 5, null))),
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
        given(eval.getItPtlCkgItmTc()).willReturn("01");
        given(eval.getQuelRcrd()).willReturn(4);
        given(eval.getCkgOpnn()).willReturn("의견");
        given(evaluationRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(eval));

        List<CouncilDto.EvaluationItemResponse> result =
                evaluationService.getMyEvaluation(ASCT_ID, mockUser(ENO));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ckgItmC()).isEqualTo("01");
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
        given(eval.getItPtlCkgItmTc()).willReturn("01");
        given(eval.getQuelRcrd()).willReturn(4);
        given(eval.getCkgOpnn()).willReturn("좋음");
        given(evaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(eval));

        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(evaluationRepository.findAvgRowsByItem(ASCT_ID, "N")).willReturn(List.of());

        CouncilDto.EvaluationSummaryResponse result = evaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.evaluations()).hasSize(1);
        assertThat(result.evaluations().get(0).ckgItmC()).isEqualTo("01");
        assertThat(result.avgScores()).isEmpty();
    }

    @Test
    @DisplayName("getAllEvaluations: 사용자명은 이름 프로젝션 1회 배치 — 단건 조회 미호출")
    void getAllEvaluations_이름프로젝션_1회() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getItPtlCkgItmTc()).willReturn("01");
        given(eval.getQuelRcrd()).willReturn(4);
        given(eval.getCkgOpnn()).willReturn("좋음");
        given(evaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(eval));
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of());
        given(evaluationRepository.findAvgRowsByItem(ASCT_ID, "N")).willReturn(List.of());

        evaluationService.getAllEvaluations(ASCT_ID);

        then(userRepository).should(times(1)).findNameViewsByEnoIn(anyCollection());
        then(userRepository).should(never()).findByEno(anyString());
        then(userRepository).should(never()).findNameViewByEno(anyString());
    }

    // ───────────────────────────────────────────────────────
    // buildAvgScores
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("buildAvgScores: 항목별 평균점수 DTO를 반환한다")
    void buildAvgScores_평균점수반환() {
        // native Object[]를 fromRow로 봉인한 DTO 경로(findAvgRowsByItem)를 stub해 변환 의미를 그대로 검증
        EvaluationItemAvgRow row = EvaluationItemAvgRow.fromRow(new Object[] {"01", 4.0});
        given(evaluationRepository.findAvgRowsByItem(ASCT_ID, "N"))
                .willReturn(java.util.Collections.singletonList(row));

        List<CouncilDto.CheckItemAvgScore> result = evaluationService.buildAvgScores(ASCT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ckgItmC()).isEqualTo("01");
        assertThat(result.get(0).avgScore()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("saveEvaluation: 2점 의견이 공백이면 IllegalArgumentException을 던진다")
    void saveEvaluation_2점공백의견_IllegalArgumentException발생() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        assertThatThrownBy(
                        () ->
                                evaluationService.saveEvaluation(
                                        ASCT_ID,
                                        new CouncilDto.EvaluationRequest(
                                                List.of(item("UNKNOWN", 2, "   "))),
                                        mockUser(ENO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("saveEvaluation: 전원이 6개 항목을 제출하면 RESULT_WRITING으로 전이한다")
    void saveEvaluation_전원제출완료_RESULT_WRITING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("08");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        evaluationRepository.findByItPtlAsctIdAndEnoAndItPtlCkgItmTcAndDelYn(
                                ASCT_ID, ENO, "06", "N"))
                .willReturn(Optional.empty());

        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(ENO);
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(member));
        List<Bevalm> submitted =
                List.of(
                        evalOf("01"),
                        evalOf("02"),
                        evalOf("03"),
                        evalOf("04"),
                        evalOf("05"),
                        evalOf("06"));
        given(evaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(submitted);

        evaluationService.saveEvaluation(
                ASCT_ID,
                new CouncilDto.EvaluationRequest(List.of(item("06", 5, null))),
                mockUser(ENO));

        verify(councilService).changeStatus(ASCT_ID, "09");
    }

    @Test
    @DisplayName("getAllEvaluations: 사용자 정보가 있으면 평가 응답에 이름을 포함한다")
    void getAllEvaluations_사용자정보있음_이름포함() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getItPtlCkgItmTc()).willReturn("UNKNOWN");
        given(eval.getQuelRcrd()).willReturn(3);
        given(evaluationRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(eval));
        UserRepository.UserNameView user = mock(UserRepository.UserNameView.class);
        given(user.getEno()).willReturn(ENO);
        given(user.getUsrNm()).willReturn("홍길동");
        given(userRepository.findNameViewsByEnoIn(anyCollection())).willReturn(List.of(user));
        given(evaluationRepository.findAvgRowsByItem(ASCT_ID, "N"))
                .willReturn(
                        java.util.Collections.singletonList(
                                EvaluationItemAvgRow.fromRow(new Object[] {"UNKNOWN", 2.5})));

        CouncilDto.EvaluationSummaryResponse result = evaluationService.getAllEvaluations(ASCT_ID);

        assertThat(result.evaluations().get(0).usrNm()).isEqualTo("홍길동");
        assertThat(result.evaluations().get(0).ckgItmNm()).isEqualTo("UNKNOWN");
    }

    private Bevalm evalOf(String itemCode) {
        Bevalm eval = mock(Bevalm.class);
        given(eval.getEno()).willReturn(ENO);
        given(eval.getItPtlCkgItmTc()).willReturn(itemCode);
        return eval;
    }
}
