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
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bschdm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ScheduleService 단위 테스트
 *
 * <p>협의회 일정 서비스의 일정 입력·확정·조회 메서드를 검증합니다. Basctm·Bschdm 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로
 * 생성합니다. CouncilService·ScheduleRepository·CommitteeRepository·UserRepository는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleServiceTest {

    @Mock private ScheduleRepository scheduleRepository;

    @Mock private CommitteeRepository committeeRepository;

    @Mock private UserRepository userRepository;

    @Mock private CouncilService councilService;

    @Mock private EntityManager entityManager;

    @InjectMocks private ScheduleService scheduleService;

    @BeforeEach
    void injectEntityManager() {
        // @PersistenceContext 필드는 @InjectMocks가 constructor 주입 후 건너뛰므로 명시적 주입
        ReflectionTestUtils.setField(scheduleService, "entityManager", entityManager);
        Bcmmtm member = mock(Bcmmtm.class);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(Optional.of(member));
    }

    private static final String ASCT_ID = "ASCT-2026-0001";
    private static final String ENO = "E10001";
    private static final String TEST_DATE = "20260501";

    /** ScheduleConfirmRequest 등 일부 시그니처는 아직 LocalDate를 요구함 — 임시 변환용 */
    private static final LocalDate TEST_DATE_LD = LocalDate.of(2026, 5, 1);

    private CustomUserDetails mockUser(String eno) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        given(user.getEno()).willReturn(eno);
        return user;
    }

    private record CouncilMemberUser(String eno, String usrNm, String bbrNm, String ptCNm)
            implements UserRepository.CouncilMemberUserRow {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }

        @Override
        public String getPtCNm() {
            return ptCNm;
        }
    }

    // ───────────────────────────────────────────────────────
    // submitSchedule — 유효성 검증
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitSchedule: 허용되지 않은 시간대이면 IllegalArgumentException을 던진다")
    void submitSchedule_허용되지않은시간대_IllegalArgumentException발생() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "09:00", "Y")), null);

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
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.of(existing));

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "N")), null);

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(existing).respond("N");
    }

    @Test
    @DisplayName("submitSchedule: 기존 일정이 없으면 신규 저장한다")
    void submitSchedule_기존일정없으면_save호출() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "14:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "14:00", "Y")), null);

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(entityManager).persist(any(Bschdm.class));
    }

    // ───────────────────────────────────────────────────────
    // confirmSchedule
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmSchedule: 허용되지 않은 회의시간이면 IllegalArgumentException을 던진다")
    void confirmSchedule_허용되지않은회의시간_IllegalArgumentException발생() {
        CouncilDto.ScheduleConfirmRequest request =
                new CouncilDto.ScheduleConfirmRequest(TEST_DATE_LD, "13:00", "본관 1층");

        assertThatThrownBy(() -> scheduleService.confirmSchedule(ASCT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("13:00");
    }

    @Test
    @DisplayName("confirmSchedule: 정상 요청이면 일정을 확정하고 SCHEDULED로 전이한다")
    void confirmSchedule_정상요청_confirmSchedule호출후SCHEDULED전이() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");

        CouncilDto.ScheduleConfirmRequest request =
                new CouncilDto.ScheduleConfirmRequest(TEST_DATE_LD, "10:00", "본관 1층");

        scheduleService.confirmSchedule(ASCT_ID, request);

        verify(council).confirmSchedule(TEST_DATE_LD, "10:00", "본관 1층");
        verify(councilService).changeStatus(ASCT_ID, "06");
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
        given(slot.getCnrcDt()).willReturn(TEST_DATE);
        given(slot.getCnrcSttTm()).willReturn("10:00");
        given(slot.getUsePsbYn()).willReturn("Y");
        given(scheduleRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(slot));

        List<CouncilDto.ScheduleSlotResponse> result = scheduleService.getMySchedule(ASCT_ID, ENO);

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
        given(scheduleRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of());

        // when
        List<CouncilDto.ScheduleSlotResponse> result = scheduleService.getMySchedule(ASCT_ID, ENO);

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
        given(slot1.getCnrcDt()).willReturn(TEST_DATE);
        given(slot1.getCnrcSttTm()).willReturn("10:00");
        given(slot1.getUsePsbYn()).willReturn("Y");

        Bschdm slot2 = mock(Bschdm.class);
        given(slot2.getCnrcDt()).willReturn(TEST_DATE);
        given(slot2.getCnrcSttTm()).willReturn("14:00");
        given(slot2.getUsePsbYn()).willReturn("N");

        Bschdm slot3 = mock(Bschdm.class);
        given(slot3.getCnrcDt()).willReturn(TEST_DATE);
        given(slot3.getCnrcSttTm()).willReturn("15:00");
        given(slot3.getUsePsbYn()).willReturn("Y");

        given(scheduleRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(List.of(slot1, slot2, slot3));

        // when
        List<CouncilDto.ScheduleSlotResponse> result = scheduleService.getMySchedule(ASCT_ID, ENO);

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
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "16:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "16:00", "Y")), null);

        // when
        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        // then: 신규 저장 호출
        verify(entityManager).persist(any(Bschdm.class));
    }

    @Test
    @DisplayName("submitSchedule: 복수 슬롯 요청 시 슬롯 수만큼 save가 호출된다")
    void submitSchedule_복수슬롯_모두처리() {
        // given: 2개 슬롯 (10:00, 14:00) 동시 제출, 모두 신규
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.empty());
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "14:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(
                                new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "Y"),
                                new CouncilDto.ScheduleItem(TEST_DATE, "14:00", "Y")),
                        null);

        // when
        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        // then: persist가 2회 호출됨 (PRD §15 패턴)
        verify(entityManager, org.mockito.Mockito.times(2)).persist(any(Bschdm.class));
    }

    // ───────────────────────────────────────────────────────
    // submitSchedule — 대면희망여부 (PRD_c_20260620 #1)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("submitSchedule: 대면희망여부가 전달되면 위원의 respondFaceToFace를 호출한다")
    void submitSchedule_대면희망여부전달_위원에반영() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.empty());

        Bcmmtm member = mock(Bcmmtm.class);
        given(committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(ASCT_ID, ENO, "N"))
                .willReturn(Optional.of(member));

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "Y")), "N");

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(member).respondFaceToFace("N");
    }

    @Test
    @DisplayName("submitSchedule: 대면희망여부가 null이면 위원 선호도를 변경하지 않는다")
    void submitSchedule_대면희망여부null_위원미변경() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(
                        scheduleRepository.findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                ASCT_ID, ENO, TEST_DATE, "10:00", "N"))
                .willReturn(Optional.empty());

        CouncilDto.ScheduleRequest request =
                new CouncilDto.ScheduleRequest(
                        List.of(new CouncilDto.ScheduleItem(TEST_DATE, "10:00", "Y")), null);

        scheduleService.submitSchedule(ASCT_ID, request, mockUser(ENO));

        verify(committeeRepository, times(1)).findByItPtlAsctIdAndEnoAndDelYn(any(), any(), any());
    }

    // ───────────────────────────────────────────────────────
    // confirmWrittenMeeting — 서면개최 확정 (PRD_c_20260620 #1)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("confirmWrittenMeeting: 개최준비(05) + 위원 전원 서면(N)이면 서면확정 후 진행중(07)으로 전이한다")
    void confirmWrittenMeeting_전원서면_서면확정후진행중전이() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm m1 = mock(Bcmmtm.class);
        given(m1.getCsfHpYn()).willReturn("N");
        Bcmmtm m2 = mock(Bcmmtm.class);
        given(m2.getCsfHpYn()).willReturn("N");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(m1, m2));

        scheduleService.confirmWrittenMeeting(ASCT_ID);

        verify(council).markWrittenMeeting();
        verify(council).changeStatus("07");
    }

    @Test
    @DisplayName("confirmWrittenMeeting: 대면희망(Y) 위원이 한 명이라도 있으면 IllegalStateException을 던진다")
    void confirmWrittenMeeting_대면희망위원존재_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm m1 = mock(Bcmmtm.class);
        given(m1.getCsfHpYn()).willReturn("N");
        Bcmmtm m2 = mock(Bcmmtm.class);
        given(m2.getCsfHpYn()).willReturn("Y");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(m1, m2));

        assertThatThrownBy(() -> scheduleService.confirmWrittenMeeting(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전원");
    }

    @Test
    @DisplayName("confirmWrittenMeeting: 미응답(null) 위원이 있으면 IllegalStateException을 던진다")
    void confirmWrittenMeeting_미응답위원존재_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm m1 = mock(Bcmmtm.class);
        given(m1.getCsfHpYn()).willReturn("N");
        Bcmmtm m2 = mock(Bcmmtm.class);
        given(m2.getCsfHpYn()).willReturn(null);
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(m1, m2));

        assertThatThrownBy(() -> scheduleService.confirmWrittenMeeting(ASCT_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("confirmWrittenMeeting: 개최준비(05)가 아니면 IllegalStateException을 던진다")
    void confirmWrittenMeeting_잘못된상태_IllegalStateException발생() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctPrgStsTc()).willReturn("06");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        assertThatThrownBy(() -> scheduleService.confirmWrittenMeeting(ASCT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("05");
    }

    // ───────────────────────────────────────────────────────
    // getScheduleStatus
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getScheduleStatus: 위원 1명이 미응답인 경우 현황 DTO를 반환한다")
    void getScheduleStatus_위원1명미응답_현황반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(ENO);
        given(member.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(member));

        // 아직 일정 응답 없음
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        given(scheduleRepository.countPendingMembers(ASCT_ID)).willReturn(1L);
        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(List.of());

        CouncilDto.ScheduleStatusResponse result = scheduleService.getScheduleStatus(ASCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.respondedCount()).isEqualTo(0);
        assertThat(result.pendingCount()).isEqualTo(1L);
        assertThat(result.memberStatuses()).hasSize(1);
        assertThat(result.allRequiredResponded()).isFalse();
    }

    @Test
    @DisplayName("getScheduleStatus: 전원 응답(ETC 타입)이면 allRequiredResponded가 true이다")
    void getScheduleStatus_전원응답ETC_allRequiredRespondedTrue() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(ENO);
        given(member.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(member));

        // 해당 위원이 일정을 응답함
        Bschdm slot = mock(Bschdm.class);
        given(slot.getEno()).willReturn(ENO);
        given(slot.getCnrcDt()).willReturn(TEST_DATE);
        given(slot.getCnrcSttTm()).willReturn("10:00");
        given(slot.getUsePsbYn()).willReturn("Y");
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(slot));
        given(scheduleRepository.countPendingMembers(ASCT_ID)).willReturn(0L);

        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(List.of(new CouncilMemberUser(ENO, "홍길동", "IT기획부", "IT기획팀장")));

        CouncilDto.ScheduleStatusResponse result = scheduleService.getScheduleStatus(ASCT_ID);

        assertThat(result.respondedCount()).isEqualTo(1);
        assertThat(result.pendingCount()).isEqualTo(0L);
        assertThat(result.allRequiredResponded()).isTrue();
        assertThat(result.memberStatuses().get(0).usrNm()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("getScheduleStatus: 위원 전원이 응답하면 확정 가능하다 (INFO_SYS도 전원 기준)")
    void getScheduleStatus_INFO_SYS전원응답_true() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("03");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm budgetLead = mock(Bcmmtm.class);
        Bcmmtm itLead = mock(Bcmmtm.class);
        given(budgetLead.getEno()).willReturn("12004");
        given(budgetLead.getItPtlAsctMebTc()).willReturn("01");
        given(itLead.getEno()).willReturn("18001");
        given(itLead.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(budgetLead, itLead));

        Bschdm budgetSlot = mock(Bschdm.class);
        Bschdm itSlot = mock(Bschdm.class);
        given(budgetSlot.getEno()).willReturn("12004");
        given(budgetSlot.getCnrcDt()).willReturn(TEST_DATE);
        given(budgetSlot.getCnrcSttTm()).willReturn("10:00");
        given(budgetSlot.getUsePsbYn()).willReturn("Y");
        given(itSlot.getEno()).willReturn("18001");
        given(itSlot.getCnrcDt()).willReturn(TEST_DATE);
        given(itSlot.getCnrcSttTm()).willReturn("14:00");
        given(itSlot.getUsePsbYn()).willReturn("Y");
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(budgetSlot, itSlot));
        given(scheduleRepository.countPendingMembers(ASCT_ID)).willReturn(0L);

        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new CouncilMemberUser("12004", null, null, null),
                                new CouncilMemberUser("18001", null, null, null)));

        CouncilDto.ScheduleStatusResponse result = scheduleService.getScheduleStatus(ASCT_ID);

        assertThat(result.allRequiredResponded()).isTrue();
    }

    @Test
    @DisplayName("getScheduleStatus: 위원 중 한 명이라도 미응답이면 확정 불가다 (INFO_SYS도 전원 기준)")
    void getScheduleStatus_INFO_SYS일부미응답_false() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("03");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm budgetLead = mock(Bcmmtm.class);
        Bcmmtm itLead = mock(Bcmmtm.class);
        given(budgetLead.getEno()).willReturn("12004");
        given(itLead.getEno()).willReturn("18001");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(budgetLead, itLead));

        Bschdm budgetSlot = mock(Bschdm.class);
        given(budgetSlot.getEno()).willReturn("12004");
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(budgetSlot));
        given(scheduleRepository.countPendingMembers(ASCT_ID)).willReturn(1L);

        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new CouncilMemberUser("12004", null, null, null),
                                new CouncilMemberUser("18001", null, null, null)));

        CouncilDto.ScheduleStatusResponse result = scheduleService.getScheduleStatus(ASCT_ID);

        assertThat(result.allRequiredResponded()).isFalse();
    }

    @Test
    @DisplayName("getScheduleStatus: 간사(03)는 일정 취합 대상에서 제외된다")
    void getScheduleStatus_간사제외() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm evaluator = mock(Bcmmtm.class);
        given(evaluator.getEno()).willReturn("E1");
        given(evaluator.getItPtlAsctMebTc()).willReturn("01");
        Bcmmtm secretary = mock(Bcmmtm.class);
        given(secretary.getEno()).willReturn("S1");
        given(secretary.getItPtlAsctMebTc()).willReturn("03");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(evaluator, secretary));

        // 평가위원만 일정 응답
        Bschdm slot = mock(Bschdm.class);
        given(slot.getEno()).willReturn("E1");
        given(slot.getCnrcDt()).willReturn(TEST_DATE);
        given(slot.getCnrcSttTm()).willReturn("10:00");
        given(slot.getUsePsbYn()).willReturn("Y");
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(slot));

        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(List.of(new CouncilMemberUser("E1", null, null, null)));

        CouncilDto.ScheduleStatusResponse result = scheduleService.getScheduleStatus(ASCT_ID);

        // 간사 제외: 취합 대상은 평가위원 1명만
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.memberStatuses()).hasSize(1);
        assertThat(result.memberStatuses().get(0).eno()).isEqualTo("E1");
        assertThat(result.respondedCount()).isEqualTo(1);
        assertThat(result.pendingCount()).isEqualTo(0L);
        // 평가위원 전원 응답 → 확정 가능
        assertThat(result.allRequiredResponded()).isTrue();
    }

    @Test
    @DisplayName("위원 응답 프로젝션은 exactly 1회 배치 — 엔티티 조회 미호출")
    void 위원응답프로젝션_1회() {
        // given: 평가위원 2명(E001, E002)
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm m1 = mock(Bcmmtm.class);
        given(m1.getEno()).willReturn("E001");
        given(m1.getItPtlAsctMebTc()).willReturn("01");
        Bcmmtm m2 = mock(Bcmmtm.class);
        given(m2.getEno()).willReturn("E002");
        given(m2.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(m1, m2));

        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(
                        List.of(
                                new CouncilMemberUser("E001", "홍길동", "IT본부", "팀장"),
                                new CouncilMemberUser("E002", "김철수", "IT본부", "대리")));

        // when
        scheduleService.getScheduleStatus(ASCT_ID);

        // then: 응답 프로젝션 일괄 조회 1회, 엔티티 조회는 호출되지 않음
        then(userRepository).should(times(1)).findCouncilMemberUserRowsByEnoIn(anyCollection());
        then(userRepository).should(never()).findByEnoIn(anyCollection());
        then(userRepository).should(never()).findByEno(anyString());
    }

    @Test
    @DisplayName("getScheduleStatus: 사용자 미존재 시 사번만 유지하고 나머지 사용자 정보는 null이다")
    void getScheduleStatus_사용자미존재_fallback() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn("UNKNOWN");
        given(member.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(member));
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(List.of());

        CouncilDto.MemberScheduleStatus status =
                scheduleService.getScheduleStatus(ASCT_ID).memberStatuses().get(0);

        assertThat(status.eno()).isEqualTo("UNKNOWN");
        assertThat(status.usrNm()).isNull();
        assertThat(status.bbrNm()).isNull();
        assertThat(status.ptCNm()).isNull();
    }

    @Test
    @DisplayName("getScheduleStatus: 조직이 없는 사용자는 이름과 직위는 유지하고 부점명만 null이다")
    void getScheduleStatus_조직미존재_bbrNmNull() {
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(mock(Basctm.class));
        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(ENO);
        given(member.getItPtlAsctMebTc()).willReturn("01");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(member));
        given(scheduleRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());
        given(userRepository.findCouncilMemberUserRowsByEnoIn(anyCollection()))
                .willReturn(List.of(new CouncilMemberUser(ENO, "홍길동", null, "팀장")));

        CouncilDto.MemberScheduleStatus status =
                scheduleService.getScheduleStatus(ASCT_ID).memberStatuses().get(0);

        assertThat(status.usrNm()).isEqualTo("홍길동");
        assertThat(status.bbrNm()).isNull();
        assertThat(status.ptCNm()).isEqualTo("팀장");
    }
}
