package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * ReviewerService 단위 테스트 — REV-02
 *
 * <p>사전협의 검토자 목록 조회: 팀코드별 사용자 조회 및 DTO 변환 검증</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewerServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewerService reviewerService;

    private CuserI makeUser(String eno, String usrNm) {
        return CuserI.builder()
                .eno(eno)
                .usrNm(usrNm)
                .build();
    }

    @Test
    @DisplayName("getReviewers: 검토 팀코드별 사용자를 조회하여 DTO로 반환한다")
    void getReviewers_팀코드별조회_성공() {
        given(userRepository.findByTemC(anyString())).willReturn(List.of());
        given(userRepository.findByTemC("18010")).willReturn(
                List.of(makeUser("E001", "홍길동")));

        List<ReviewerDto.Response> result = reviewerService.getReviewers("DOC-2026-0001");

        verify(userRepository, atLeastOnce()).findByTemC(anyString());
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getReviewers: PMO팀 사용자가 있으면 결과에 포함된다")
    void getReviewers_PMO팀_포함() {
        given(userRepository.findByTemC(anyString())).willReturn(List.of());
        given(userRepository.findByTemC("18010")).willReturn(
                List.of(makeUser("E001", "홍길동")));

        List<ReviewerDto.Response> result = reviewerService.getReviewers("DOC-2026-0001");

        assertThat(result)
                .filteredOn(r -> "PMO팀".equals(r.getTeamName()))
                .hasSize(1)
                .extracting(value -> value.getEno())
                .containsExactly("E001");
    }

    @Test
    @DisplayName("getReviewers: 해당 팀코드에 사용자가 없으면 해당 팀은 결과에서 제외된다")
    void getReviewers_사용자없는팀_제외() {
        given(userRepository.findByTemC(anyString())).willReturn(List.of());

        List<ReviewerDto.Response> result = reviewerService.getReviewers("DOC-2026-0001");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getReviewers: 팀당 최대 1명만 반환한다")
    void getReviewers_팀당1명_제한() {
        given(userRepository.findByTemC(anyString())).willReturn(List.of());
        given(userRepository.findByTemC("18010")).willReturn(List.of(
                makeUser("E001", "홍길동"),
                makeUser("E002", "김영희"),
                makeUser("E003", "이철수")));

        List<ReviewerDto.Response> result = reviewerService.getReviewers("DOC-2026-0001");

        assertThat(result.stream().filter(r -> "PMO팀".equals(r.getTeamName())).count())
                .isEqualTo(1);
    }
}
