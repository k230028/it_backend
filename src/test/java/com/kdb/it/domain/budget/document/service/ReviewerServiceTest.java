package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ReviewerService 단위 테스트 — BE-10
 *
 * <p>검토자 목록: findByTemCInAndDelYn 1회 활성 사용자 배치 조회, 대표자 결정 규칙(팀장 우선→사번 오름차순),
 * 팀 표시 순서 고정을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReviewerServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewerService reviewerService;

    private UserRepository.CommitteeUserRow makeUser(String eno, String usrNm, String temC, String ptCNm) {
        return new CommitteeUser(temC, eno, usrNm, "IT본부", ptCNm);
    }

    private record CommitteeUser(String temC, String eno, String usrNm, String bbrNm, String ptCNm)
            implements UserRepository.CommitteeUserRow {
        @Override public String getTemC() { return temC; }
        @Override public String getEno() { return eno; }
        @Override public String getUsrNm() { return usrNm; }
        @Override public String getBbrNm() { return bbrNm; }
        @Override public String getPtCNm() { return ptCNm; }
    }

    /** 실제 요청된 팀코드에 속한 활성 사용자만 반환하는 배치 조회 스텁. */
    private void stubUsersByRequestedTeam(UserRepository.CommitteeUserRow... users) {
        given(userRepository.findCommitteeUserRowsByTemCInAndDelYn(anyCollection(), eq("N"))).willAnswer(inv -> {
            Collection<String> requestedTeamCodes = inv.getArgument(0);
            return Arrays.stream(users)
                    .filter(user -> requestedTeamCodes.contains(user.getTemC()))
                    .toList();
        });
    }

    @Test
    @DisplayName("getReviewers: 팀코드 전체의 활성 사용자를 1회로 배치 조회한다")
    void getReviewers_배치조회_1회() {
        stubUsersByRequestedTeam();

        reviewerService.getReviewers();

        verify(userRepository, times(1)).findCommitteeUserRowsByTemCInAndDelYn(
                eq(Set.of("12004", "18001", "18010", "18501")), eq("N"));
    }

    @Test
    @DisplayName("getReviewers: 팀장이 있으면 팀장을 검토자로 선택한다")
    void getReviewers_팀장우선() {
        stubUsersByRequestedTeam(
                makeUser("E002", "김과장", "18010", "과장"),
                makeUser("E009", "박팀장", "18010", "팀장"));

        List<ReviewerDto.Response> result = reviewerService.getReviewers();

        assertThat(result)
                .filteredOn(r -> "PMO팀".equals(r.getTeamName()))
                .extracting(ReviewerDto.Response::getEno)
                .containsExactly("E009");
    }

    @Test
    @DisplayName("getReviewers: 팀장이 없으면 사번 오름차순 첫 번째를 선택한다")
    void getReviewers_사번오름차순() {
        stubUsersByRequestedTeam(
                makeUser("E005", "이차장", "18010", "차장"),
                makeUser("E001", "정과장", "18010", "과장"));

        List<ReviewerDto.Response> result = reviewerService.getReviewers();

        assertThat(result)
                .filteredOn(r -> "PMO팀".equals(r.getTeamName()))
                .extracting(ReviewerDto.Response::getEno)
                .containsExactly("E001");
    }

    @Test
    @DisplayName("getReviewers: 사용자가 없는 팀은 결과에서 제외된다")
    void getReviewers_사용자없는팀_제외() {
        stubUsersByRequestedTeam();

        assertThat(reviewerService.getReviewers()).isEmpty();
    }

    @Test
    @DisplayName("getReviewers: 결과는 계약팀→기획팀→PMO팀→개발/운영팀 순서로 고정된다")
    void getReviewers_팀순서고정() {
        stubUsersByRequestedTeam(
                makeUser("E301", "개발A", "18501", "과장"),
                makeUser("E101", "기획A", "18001", "과장"),
                makeUser("E201", "PMOA", "18010", "과장"),
                makeUser("E001", "계약A", "12004", "과장"));

        List<ReviewerDto.Response> result = reviewerService.getReviewers();

        assertThat(result).extracting(ReviewerDto.Response::getTeamName)
                .containsExactly("계약팀", "기획팀", "PMO팀", "개발/운영팀");
    }
}
