package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ReviewerDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 사전협의 검토자 서비스
 *
 * <p>팀코드별 검토자 목록을 조회하여 반환합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewerService {

    private final UserRepository userRepository;

    /** 검토 대상 팀코드 → 팀명 매핑 */
    private static final Map<String, String> REVIEW_TEAM_MAP = Map.of(
            "12004", "계약팀",
            "18001", "기획팀",
            "18010", "PMO팀",
            "18501", "개발/운영팀"
    );

    /**
     * 사전협의 문서에 대한 검토자 목록을 반환합니다.
     *
     * <p>각 검토 팀에서 첫 번째 사용자 1명씩만 포함합니다.</p>
     *
     * @param docMngNo 사전협의 관리번호
     * @return 팀별 검토자 DTO 목록
     */
    public List<ReviewerDto.Response> getReviewers(String docMngNo) {
        List<ReviewerDto.Response> reviewers = new ArrayList<>();
        REVIEW_TEAM_MAP.forEach((temC, teamName) ->
                userRepository.findByTemC(temC).stream()
                        .findFirst()
                        .map(user -> ReviewerDto.Response.from(user, teamName))
                        .ifPresent(reviewers::add));
        return reviewers;
    }
}
