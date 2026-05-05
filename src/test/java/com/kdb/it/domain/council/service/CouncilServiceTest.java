package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.repository.CouncilRepository;
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
        // given
        Basctm council = mock(Basctm.class);
        given(council.getAsctSts()).willReturn("APPROVED");
        given(council.getPrjMngNo()).willReturn("PRJ-2026-0001");
        given(council.getPrjSno()).willReturn(1);
        given(councilRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(Optional.of(council));

        // when
        councilService.skipCouncil(ASCT_ID);

        // then
        verify(council).changeStatus("SKIPPED");
        verify(councilRepository).updateProjectStatus("PRJ-2026-0001", 1, "요건 상세화");
    }
}
