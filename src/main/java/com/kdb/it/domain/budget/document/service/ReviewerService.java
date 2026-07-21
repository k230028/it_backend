package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.UserRepresentativeSelector;
import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사전협의 검토자 서비스
 *
 * <p>검토 팀코드 전체를 1회 배치 조회한 뒤, 팀별 대표자(팀장 우선→사번 오름차순)를 결정적으로 선택해 반환합니다. (BE-10)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewerService {

    private final UserRepository userRepository;

    /** 검토 대상 팀코드 → 팀명 매핑 (응답 순서 고정을 위해 삽입 순서 보존) */
    private static final Map<String, String> REVIEW_TEAM_MAP;

    static {
        Map<String, String> teams = new LinkedHashMap<>();
        teams.put("12004", "계약팀");
        teams.put("18001", "기획팀");
        teams.put("18010", "PMO팀");
        teams.put("18501", "개발/운영팀");
        REVIEW_TEAM_MAP = Collections.unmodifiableMap(teams);
    }

    /**
     * 사전협의 공통 검토자 후보 목록을 반환합니다.
     *
     * <p>팀코드 전체의 활성 사용자를 팀 대표 프로젝션으로 한 번에 조회하고, 각 팀의 대표자는 {@link UserRepresentativeSelector}가 결정적으로
     * 선택합니다. 사용자가 없는 팀은 결과에서 제외합니다. 저장소 조회 실패는 변환하지 않고 호출자에게 그대로 전파합니다.
     *
     * @return 팀별 검토자 DTO 목록 (계약팀→기획팀→PMO팀→개발/운영팀 순서)
     */
    public List<ReviewerDto.Response> getReviewers() {
        Map<String, List<UserRepository.CommitteeUserRow>> usersByTeam =
                userRepository
                        .findCommitteeUserRowsByTemCInAndDelYn(REVIEW_TEAM_MAP.keySet(), "N")
                        .stream()
                        .collect(Collectors.groupingBy(user -> user.getTemC()));

        List<ReviewerDto.Response> reviewers = new ArrayList<>();
        REVIEW_TEAM_MAP.forEach(
                (temC, teamName) ->
                        UserRepresentativeSelector.pickView(
                                        usersByTeam.getOrDefault(temC, List.of()))
                                .map(user -> ReviewerDto.Response.fromView(user, teamName))
                                .ifPresent(reviewers::add));
        return reviewers;
    }
}
