package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.PendingApprovalRow;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

/** 결재 대기 항목의 긴급도 판정을 검증한다. 신청일자 누락은 정상(normal)으로 숨기지 않고 unknown으로 드러낸다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApplicationDashboardServiceTest {

    private static final CustomUserDetails ADMIN =
            new CustomUserDetails("E10001", List.of(CustomUserDetails.ATH_ADMIN), "D001");

    @Mock private ApplicationRepository applicationRepository;
    @InjectMocks private ApplicationDashboardService service;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ApplicationDashboardService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        given(applicationRepository.findMonthlyTrendRowsByBbrC("D001")).willReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName(
            "getDashboard: 신청일자 없는 결재 대기 건은 urgency=unknown·requestedAt=null이며 WARN 로그에 신청서번호를 남긴다")
    void getDashboard_신청일자없음_unknown과경고로그() {
        given(applicationRepository.findPendingRowsByEno("E10001"))
                .willReturn(
                        List.of(
                                PendingApprovalRow.fromRow(
                                        new Object[] {"APF-NULL", "날짜 없음", "김길동", null})));

        ApplicationDto.DashboardResponse result = service.getDashboard("D001", "E10001", ADMIN);

        assertThat(result.getPendingList())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.getApfMngNo()).isEqualTo("APF-NULL");
                            assertThat(item.getRequestedAt()).isNull();
                            assertThat(item.getUrgency()).isEqualTo("unknown");
                        });
        assertThat(appender.list)
                .anySatisfy(
                        event -> {
                            assertThat(event.getLevel()).isEqualTo(Level.WARN);
                            assertThat(event.getFormattedMessage()).contains("APF-NULL");
                        });
    }

    @Test
    @DisplayName("getDashboard: 신청일자가 있으면 3일 초과는 urgent, 그 외는 normal이며 경고 로그가 없다")
    void getDashboard_신청일자있음_urgent또는normal() {
        given(applicationRepository.findPendingRowsByEno("E10001"))
                .willReturn(
                        List.of(
                                PendingApprovalRow.fromRow(
                                        new Object[] {
                                            "APF-OLD",
                                            "오래된 신청",
                                            "홍길동",
                                            LocalDate.now().minusDays(4).toString()
                                        }),
                                PendingApprovalRow.fromRow(
                                        new Object[] {
                                            "APF-NEW", "최근 신청", "이길동", LocalDate.now().toString()
                                        })));

        ApplicationDto.DashboardResponse result = service.getDashboard("D001", "E10001", ADMIN);

        assertThat(result.getPendingList())
                .extracting(ApplicationDto.PendingItem::getUrgency)
                .containsExactly("urgent", "normal");
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }
}
