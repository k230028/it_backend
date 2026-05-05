package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
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
import com.kdb.it.domain.council.entity.Bschdm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ScheduleRepository;

/**
 * ScheduleService 단위 테스트
 *
 * <p>
 * 협의회 일정 서비스의 일정 입력·확정·조회 메서드를 검증합니다.
 * Basctm·Bschdm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CouncilService·ScheduleRepository·CommitteeRepository·UserRepository는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CouncilService councilService;

    @InjectMocks
    private ScheduleService scheduleService;

    private static final String ASCT_ID = "ASCT-2026-0001";
    private static final String ENO = "E10001";
    private static final LocalDate TEST_DATE = LocalDate.of(2026, 5, 1);

    private CustomUserDetails mockUser(String eno) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getEno()).willReturn(eno);
        return user;
    }

    // ───────────────────────────────────────────────────────
    // submitSchedule — 유효성 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitSchedule: 허용되지 않은 시간대이면 IllegalArgumentException을 던진다")
    void submitSchedule_허용되지않은시간대_IllegalArgumentException발생() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CouncilDto.ScheduleRequest request = new CouncilDto.ScheduleRequest(
                List.of(new CouncilDto.ScheduleItem(TEST_DATE, "09:00", "Y")));

        assertThatThrownBy(() -> scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("09:00");
    }

    // ───────────────────────────────────────────────────────
    // submitSchedule — upsert
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitSchedule: 기존 일정이 있으면 respond()를 호출한다")
    void submitSchedule_기존일정있으면_respond호출() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bschdm existing = mock(Bschdm.class);
        given(scheduleRepository.findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.of(existing));

        CouncilDto.ScheduleRequest request = new CouncilDto.ScheduleRequest(
                List.of(new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "N")));

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(existing).respond("N");
    }

    @Test
    @DisplayName("submitSchedule: 기존 일정이 없으면 신규 저장한다")
    void submitSchedule_기존일정없으면_save호출() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(scheduleRepository.findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                ASCT_ID, ENO, TEST_DATE, "14:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request = new CouncilDto.ScheduleRequest(
                List.of(new CouncilDto.ScheduleItem(TEST_DATE, "14:00", "Y")));

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(scheduleRepository).save(any(Bschdm.class));
    }

    // ───────────────────────────────────────────────────────
    // confirmSchedule
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmSchedule: 허용되지 않은 회의시간이면 IllegalArgumentException을 던진다")
    void confirmSchedule_허용되지않은회의시간_IllegalArgumentException발생() {
        CouncilDto.ScheduleConfirmRequest request =
                new CouncilDto.ScheduleConfirmRequest(TEST_DATE, "13:00", "본관 1층");

        assertThatThrownBy(() -> scheduleService.confirmSchedule(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13:00");
    }

    @Test
    @DisplayName("confirmSchedule: 정상 요청이면 일정을 확정하고 SCHEDULED로 전이한다")
    void confirmSchedule_정상요청_confirmSchedule호출후SCHEDULED전이() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CouncilDto.ScheduleConfirmRequest request =
                new CouncilDto.ScheduleConfirmRequest(TEST_DATE, "10:00", "본관 1층");

        scheduleService.confirmSchedule(ASCT_ID, request);

        verify(council).confirmSchedule(TEST_DATE, "10:00", "본관 1층");
        verify(councilService).changeStatus(ASCT_ID, "SCHEDULED");
    }

    // ───────────────────────────────────────────────────────
    // getMySchedule
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getMySchedule: 본인이 제출한 일정 슬롯 목록을 DTO로 반환한다")
    void getMySchedule_본인일정슬롯반환() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bschdm slot = mock(Bschdm.class);
        given(slot.getDsdDt()).willReturn(TEST_DATE);
        given(slot.getDsdTm()).willReturn("10:00");
        given(slot.getPsbYn()).willReturn("Y");
        given(scheduleRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(slot));

        List<CouncilDto.ScheduleSlotResponse> result =
                scheduleService.getMySchedule(ASCT_ID, ENO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dsdTm()).isEqualTo("10:00");
        assertThat(result.get(0).psbYn()).isEqualTo("Y");
    }

    // ───────────────────────────────────────────────────────
    // getMySchedule — 추가 케이스
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getMySchedule: 제출한 일정이 없으면 빈 목록을 반환한다")
    void getMySchedule_일정없음_빈목록반환() {
        // given: 아직 일정을 제출하지 않은 위원
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(scheduleRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of());

        // when
        List<CouncilDto.ScheduleSlotResponse> result =
                scheduleService.getMySchedule(ASCT_ID, ENO);

        // then: 빈 목록 반환
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMySchedule: 여러 슬롯을 제출한 경우 모두 반환한다")
    void getMySchedule_복수슬롯_모두반환() {
        // given: 3개 슬롯 제출
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bschdm slot1 = mock(Bschdm.class);
        given(slot1.getDsdDt()).willReturn(TEST_DATE);
        given(slot1.getDsdTm()).willReturn("10:00");
        given(slot1.getPsbYn()).willReturn("Y");

        Bschdm slot2 = mock(Bschdm.class);
        given(slot2.getDsdDt()).willReturn(TEST_DATE);
        given(slot2.getDsdTm()).willReturn("14:00");
        given(slot2.getPsbYn()).willReturn("N");

        Bschdm slot3 = mock(Bschdm.class);
        given(slot3.getDsdDt()).willReturn(TEST_DATE);
        given(slot3.getDsdTm()).willReturn("15:00");
        given(slot3.getPsbYn()).willReturn("Y");

        given(scheduleRepository.findByAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(slot1, slot2, slot3));

        // when
        List<CouncilDto.ScheduleSlotResponse> result =
                scheduleService.getMySchedule(ASCT_ID, ENO);

        // then: 3개 슬롯 모두 반환
        assertThat(result).hasSize(3);
        assertThat(result.get(1).dsdTm()).isEqualTo("14:00");
        assertThat(result.get(1).psbYn()).isEqualTo("N");
    }

    // ───────────────────────────────────────────────────────
    // submitSchedule — 허용 시간대 경계 및 복수 슬롯 처리
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitSchedule: 허용된 시간대 16:00은 정상 저장된다")
    void submitSchedule_허용시간대16시_정상저장() {
        // given: 16:00은 허용 시간대
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(scheduleRepository.findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                ASCT_ID, ENO, TEST_DATE, "16:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request = new CouncilDto.ScheduleRequest(
                List.of(new CouncilDto.ScheduleItem(TEST_DATE, "16:00", "Y")));

        // when
        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        // then: 신규 저장 호출
        verify(scheduleRepository).save(any(Bschdm.class));
    }

    @Test
    @DisplayName("submitSchedule: 복수 슬롯 요청 시 슬롯 수만큼 save가 호출된다")
    void submitSchedule_복수슬롯_모두처리() {
        // given: 2개 슬롯 (10:00, 14:00) 동시 제출, 모두 신규
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(scheduleRepository.findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.empty());
        given(scheduleRepository.findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                ASCT_ID, ENO, TEST_DATE, "14:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request = new CouncilDto.ScheduleRequest(List.of(
                new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "Y"),
                new CouncilDto.ScheduleItem(TEST_DATE, "14:00", "Y")
        ));

        // when
        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        // then: save가 2회 호출됨
        verify(scheduleRepository, org.mockito.Mockito.times(2)).save(any(Bschdm.class));
    }
}
