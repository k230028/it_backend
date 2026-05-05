package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;

/**
 * CouncilService 단위 테스트
 *
 * <p>
 * 협의회 기본 서비스의 상태 전이 메서드와 단건 조회를 검증합니다.
 * Basctm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouncilServiceTest {

    @Mock
    private CouncilRepository councilRepository;

    @Mock
    private ProjectOverviewRepository projectOverviewRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private EvaluationRepository evaluationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private CouncilService councilService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    // ───────────────────────────────────────────────────────
    // findActiveCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findActiveCouncil: 존재하지 않는 협의회ID이면 IllegalArgumentException을 던진다")
    void findActiveCouncil_존재하지않는협의회_IllegalArgumentException발생() {
        // given
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> councilService.findActiveCouncil(ASCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ASCT_ID);
    }

    @Test
    @DisplayName("findActiveCouncil: 존재하는 협의회ID이면 Basctm 엔티티를 반환한다")
    void findActiveCouncil_존재하는협의회_엔티티반환() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctId()).willReturn(ASCT_ID);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        Basctm result = councilService.findActiveCouncil(ASCT_ID);

        // then
        assertThat(result.getAsctId()).isEqualTo(ASCT_ID);
    }

    // ───────────────────────────────────────────────────────
    // changeStatus
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus: 협의회 상태를 지정한 값으로 변경한다")
    void changeStatus_정상호출_상태변경() {
        // given
        Basctm council = mock(Basctm.class);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        councilService.changeStatus(ASCT_ID, "PREPARING");

        // then
        verify(council).changeStatus("PREPARING");
    }

    // ───────────────────────────────────────────────────────
    // startCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("startCouncil: 협의회 상태가 SCHEDULED가 아니면 IllegalStateException을 던진다")
    void startCouncil_SCHEDULED아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("PREPARING");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when & then
        assertThatThrownBy(() -> councilService.startCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCHEDULED");
    }

    @Test
    @DisplayName("startCouncil: SCHEDULED 상태이면 IN_PROGRESS로 전이한다")
    void startCouncil_SCHEDULED상태_IN_PROGRESS전이() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("SCHEDULED");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        councilService.startCouncil(ASCT_ID);

        // then
        verify(council).changeStatus("IN_PROGRESS");
    }

    // ───────────────────────────────────────────────────────
    // skipCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("skipCouncil: 협의회 상태가 APPROVED가 아니면 IllegalStateException을 던진다")
    void skipCouncil_APPROVED아닌상태_IllegalStateException발생() {
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("DRAFT");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when & then
        assertThatThrownBy(() -> councilService.skipCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    @DisplayName("skipCouncil: APPROVED 상태이면 SKIPPED로 전이하고 사업 상태를 업데이트한다")
    void skipCouncil_APPROVED상태_SKIPPED전이() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("APPROVED");
        given(council.getPrjMngNo()).willReturn("PRJ-2026-0001");
        given(council.getPrjSno()).willReturn(1);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        councilService.skipCouncil(ASCT_ID);

        verify(council).changeStatus("SKIPPED");
        verify(councilRepository).updateProjectStatus("PRJ-2026-0001", 1, "요건 상세화");
    }

    // ───────────────────────────────────────────────────────
    // getCouncilList
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCouncilList: 관리자이면 전체 사업 목록을 반환한다")
    void getCouncilList_관리자_전체목록반환() {
        CustomUserDetails admin = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "IT001");
        given(councilRepository.findProjectsForCouncilAll(anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(admin);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCouncilList: 평가위원이면 배정된 협의회 목록을 반환한다")
    void getCouncilList_평가위원_배정협의회반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        Basctm council = mock(Basctm.class);
        given(council.getAsctId()).willReturn(ASCT_ID);
        given(councilRepository.findByCommitteeMember("10001", "N")).willReturn(List.of(council));
        given(projectOverviewRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.empty());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getCouncilList: 일반 사용자이면 부서별 사업 목록을 반환한다")
    void getCouncilList_일반사용자_부서별목록반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        given(councilRepository.findByCommitteeMember("10001", "N")).willReturn(List.of());
        given(councilRepository.findProjectsForCouncilByDepartment(
                anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of());

        List<CouncilDto.ListResponse> result = councilService.getCouncilList(user);

        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCouncil: 존재하는 협의회이면 DetailResponse를 반환한다")
    void getCouncil_존재하는협의회_DetailResponse반환() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctId()).willReturn(ASCT_ID);
        given(council.getPrjMngNo()).willReturn("PRJ-2026-0001");
        given(council.getPrjSno()).willReturn(1);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(projectRepository.findById(any())).willReturn(Optional.empty());

        CouncilDto.DetailResponse result = councilService.getCouncil(ASCT_ID);

        assertThat(result.asctId()).isEqualTo(ASCT_ID);
    }

    // ───────────────────────────────────────────────────────
    // createCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("createCouncil: 정상 요청이면 ASCT-{연도}-{순번} 형식의 협의회ID를 반환한다")
    void createCouncil_정상요청_협의회ID반환() {
        CustomUserDetails user = new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_USER), "IT001");
        CouncilDto.CreateRequest request = new CouncilDto.CreateRequest("PRJ-2026-0001", 1, "INFO_SYS");
        given(councilRepository.getNextSequenceValue()).willReturn(1L);

        String result = councilService.createCouncil(request, user);

        assertThat(result).startsWith("ASCT-");
        verify(councilRepository).save(any(Basctm.class));
    }

    // ───────────────────────────────────────────────────────
    // completeCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("completeCouncil: IN_PROGRESS가 아닌 상태이면 IllegalStateException을 던진다")
    void completeCouncil_IN_PROGRESS아닌상태_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("DRAFT");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("completeCouncil: 평가위원이 없으면 IllegalStateException을 던진다")
    void completeCouncil_평가위원없음_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("IN_PROGRESS");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평가위원");
    }

    @Test
    @DisplayName("completeCouncil: 평가 미완료 위원이 있으면 IllegalStateException을 던진다")
    void completeCouncil_평가미완료위원있음_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("IN_PROGRESS");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        Bcmmtm evaluator = mock(Bcmmtm.class);
        given(evaluator.getVlrTp()).willReturn("MAND");
        given(evaluator.getEno()).willReturn("10002");
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(evaluator));
        given(evaluationRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, "10002", "N")).willReturn(List.of());

        assertThatThrownBy(() -> councilService.completeCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("미완료");
    }

    @Test
    @DisplayName("completeCouncil: 모든 평가위원이 6항목 제출 완료이면 RESULT_WRITING으로 전이한다")
    void completeCouncil_정상완료_RESULT_WRITING전이() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("IN_PROGRESS");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        Bcmmtm evaluator = mock(Bcmmtm.class);
        given(evaluator.getVlrTp()).willReturn("MAND");
        given(evaluator.getEno()).willReturn("10002");
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(evaluator));
        given(evaluationRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, "10002", "N"))
                .willReturn(List.of(mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class),
                        mock(Bevalm.class), mock(Bevalm.class), mock(Bevalm.class)));

        councilService.completeCouncil(ASCT_ID);

        verify(council).changeStatus("RESULT_WRITING");
    }

    // ───────────────────────────────────────────────────────
    // notifyCouncil
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("notifyCouncil: COMPLETED가 아닌 상태이면 IllegalStateException을 던진다")
    void notifyCouncil_COMPLETED아닌상태_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("RESULT_WRITING");
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        assertThatThrownBy(() -> councilService.notifyCouncil(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("notifyCouncil: COMPLETED 상태이면 사업 상태를 갱신하고 수신자 정보를 반환한다")
    void notifyCouncil_정상통보_NotifyResponse반환() {
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("COMPLETED");
        given(council.getPrjMngNo()).willReturn("PRJ-2026-0001");
        given(council.getPrjSno()).willReturn(1);
        given(council.getFstEnrUsid()).willReturn(null);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        CouncilDto.NotifyResponse result = councilService.notifyCouncil(ASCT_ID);

        verify(councilRepository).updateProjectStatus("PRJ-2026-0001", 1, "요건 상세화");
        assertThat(result).isNotNull();
    }
}
