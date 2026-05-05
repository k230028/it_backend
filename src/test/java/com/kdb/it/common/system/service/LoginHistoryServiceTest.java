package com.kdb.it.common.system.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kdb.it.common.system.dto.LoginHistoryDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.repository.LoginHistoryRepository;

/**
 * LoginHistoryService 단위 테스트
 *
 * <p>
 * 로그인 이력 조회 서비스의 2개 메서드(최대 50건 조회, 최근 10건 조회)가
 * 리포지토리에 정확히 위임하고 DTO로 변환하는지 검증합니다.
 * Clognh 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class LoginHistoryServiceTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @InjectMocks
    private LoginHistoryService loginHistoryService;

    private Clognh mockClognh(Long sno, String eno, String loginType) {
        Clognh history = mock(Clognh.class);
        given(history.getLgnSno()).willReturn(sno);
        given(history.getEno()).willReturn(eno);
        given(history.getLgnTp()).willReturn(loginType);
        given(history.getIpAddr()).willReturn("127.0.0.1");
        given(history.getUstAgt()).willReturn("Mozilla/5.0");
        given(history.getLgnDtm()).willReturn(LocalDateTime.of(2026, 4, 25, 9, 0));
        given(history.getFlurRsn()).willReturn(null);
        return history;
    }

    // ───────────────────────────────────────────────────────
    // getLoginHistory
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getLoginHistory: 사번으로 최대 50건 이력을 DTO 목록으로 반환한다")
    void getLoginHistory_이력있음_DTO목록반환() {
        // given
        String eno = "E10001";
        Clognh h1 = mockClognh(1L, eno, "LOGIN_SUCCESS");
        Clognh h2 = mockClognh(2L, eno, "LOGOUT");
        given(loginHistoryRepository.findTop50ByEnoOrderByLgnDtmDesc(eno)).willReturn(List.of(h1, h2));

        // when
        List<LoginHistoryDto.Response> result = loginHistoryService.getLoginHistory(eno);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEno()).isEqualTo(eno);
        assertThat(result.get(0).getLoginType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(result.get(1).getLoginType()).isEqualTo("LOGOUT");
        verify(loginHistoryRepository).findTop50ByEnoOrderByLgnDtmDesc(eno);
    }

    @Test
    @DisplayName("getLoginHistory: 이력이 없으면 빈 목록을 반환한다")
    void getLoginHistory_이력없음_빈목록반환() {
        // given
        given(loginHistoryRepository.findTop50ByEnoOrderByLgnDtmDesc("E99999")).willReturn(List.of());

        // when
        List<LoginHistoryDto.Response> result = loginHistoryService.getLoginHistory("E99999");

        // then
        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getRecentLoginHistory
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getRecentLoginHistory: 사번으로 최근 10건 이력을 DTO 목록으로 반환한다")
    void getRecentLoginHistory_이력있음_DTO목록반환() {
        // given
        String eno = "E10001";
        Clognh h1 = mockClognh(10L, eno, "LOGIN_SUCCESS");
        given(loginHistoryRepository.findTop10ByEnoOrderByLgnDtmDesc(eno)).willReturn(List.of(h1));

        // when
        List<LoginHistoryDto.Response> result = loginHistoryService.getRecentLoginHistory(eno);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        verify(loginHistoryRepository).findTop10ByEnoOrderByLgnDtmDesc(eno);
    }

    @Test
    @DisplayName("getRecentLoginHistory: 이력이 없으면 빈 목록을 반환한다")
    void getRecentLoginHistory_이력없음_빈목록반환() {
        // given
        given(loginHistoryRepository.findTop10ByEnoOrderByLgnDtmDesc("E99999")).willReturn(List.of());

        // when
        List<LoginHistoryDto.Response> result = loginHistoryService.getRecentLoginHistory("E99999");

        // then
        assertThat(result).isEmpty();
    }
}
