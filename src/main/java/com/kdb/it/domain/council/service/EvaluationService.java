package com.kdb.it.domain.council.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bevalm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 협의회 평가의견 서비스 (Step 3 — 평가의견 작성)
 *
 * <p>평가위원이 협의회 당일 또는 이후에 6개 점검항목에 대해 점수와 의견을 작성합니다.</p>
 *
 * <p>평가의견 작성 규칙:</p>
 * <ul>
 *   <li>6개 항목(MGMT_STR/FIN_EFC/RISK_IMP/REP_IMP/DUP_SYS/ETC) 전체 입력 필수</li>
 *   <li>점수 1~2점 입력 시 의견(ckgOpnn) 작성 필수</li>
 *   <li>기존 의견이 있으면 update, 없으면 신규 INSERT (upsert)</li>
 *   <li>첫 제출 시 협의회 상태를 EVALUATING으로 전이</li>
 * </ul>
 *
 * <p>Design Ref: §2.1 EvaluationService — Step 3 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationService {

    /** 평가의견 리포지토리 (TAAABB_BEVALM) */
    private final EvaluationRepository evaluationRepository;

    /** 사용자 리포지토리 — 위원 이름 조회용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 상태 전이용 */
    private final CouncilService councilService;

    /** 평가위원 리포지토리 — 전원 제출 여부 확인용 */
    private final CommitteeRepository committeeRepository;

    // 점검항목코드 → 한글명 매핑 (CCODEM CKG_ITM 기준)
    private static final Map<String, String> CHECK_ITEM_NAMES = Map.of(
        "MGMT_STR", "경영전략/계획 부합",
        "FIN_EFC",  "재무 효과",
        "RISK_IMP", "리스크 개선 효과",
        "REP_IMP",  "평판/이미지 개선 효과",
        "DUP_SYS",  "유사/중복 시스템 유무",
        "ETC",      "기타"
    );

    // 6개 고정 점검항목 순서
    private static final List<String> CHECK_ITEM_ORDER =
        List.of("MGMT_STR", "FIN_EFC", "RISK_IMP", "REP_IMP", "DUP_SYS", "ETC");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 평가의견 전체 현황 조회 (IT관리자용)
     *
     * <p>전체 위원별 평가의견 목록 + 점검항목별 평균점수를 반환합니다.
     * 결과서 작성 화면에서 참고 데이터로 활용됩니다.</p>
     *
     * @param asctId 협의회ID
     * @return 위원별 평가의견 + 항목별 평균점수
     */
    public CouncilDto.EvaluationSummaryResponse getAllEvaluations(String asctId) {
        councilService.findActiveCouncil(asctId);

        // 전체 평가의견 조회
        List<Bevalm> allEvaluations = evaluationRepository.findByAsctIdAndDelYn(asctId, "N");

        // 위원별 사용자 정보 Map (N+1 방지)
        Map<String, CuserI> userMap = buildUserMapFromEvaluations(allEvaluations);

        // 평가의견 → 응답 DTO 변환
        List<CouncilDto.EvaluationItemResponse> evaluationResponses = allEvaluations.stream()
                .map(e -> {
                    CuserI user = userMap.get(e.getEno());
                    return new CouncilDto.EvaluationItemResponse(
                            e.getEno(),
                            user != null ? user.getUsrNm() : null,
                            e.getCkgItmC(),
                            CHECK_ITEM_NAMES.getOrDefault(e.getCkgItmC(), e.getCkgItmC()),
                            e.getCkgRcrd(),
                            e.getCkgOpnn()
                    );
                })
                .collect(Collectors.toList());

        // 점검항목별 평균점수 계산
        List<CouncilDto.CheckItemAvgScore> avgScores = buildAvgScores(asctId);

        return new CouncilDto.EvaluationSummaryResponse(evaluationResponses, avgScores);
    }

    /**
     * 내 평가의견 조회 (평가위원)
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @return 내 6개 점검항목 평가의견 목록
     */
    public List<CouncilDto.EvaluationItemResponse> getMyEvaluation(
            String asctId, CustomUserDetails userDetails) {
        councilService.findActiveCouncil(asctId);

        String eno = userDetails.getEno();
        List<Bevalm> myEvaluations =
                evaluationRepository.findByAsctIdAndEnoAndDelYn(asctId, eno, "N");

        return myEvaluations.stream()
                .map(e -> new CouncilDto.EvaluationItemResponse(
                        e.getEno(),
                        null,   // 본인 조회 시 성명 불필요
                        e.getCkgItmC(),
                        CHECK_ITEM_NAMES.getOrDefault(e.getCkgItmC(), e.getCkgItmC()),
                        e.getCkgRcrd(),
                        e.getCkgOpnn()
                ))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // 저장
    // =========================================================================

    /**
     * 평가의견 작성/수정 (평가위원)
     *
     * <p>6개 점검항목을 upsert합니다.
     * 1~2점 입력 시 의견(ckgOpnn) 작성이 필수입니다.
     * 첫 제출 시 협의회 상태를 IN_PROGRESS → EVALUATING으로 전이합니다.</p>
     *
     * <p>Plan SC: 상태 전이는 EVALUATING이 아닌 경우에만 수행 (중복 전이 방지)</p>
     *
     * @param asctId      협의회ID
     * @param request     평가의견 요청 (6개 항목)
     * @param userDetails 로그인한 평가위원
     * @throws IllegalArgumentException 1~2점인데 의견 미작성 시
     */
    @Transactional
    public void saveEvaluation(String asctId, CouncilDto.EvaluationRequest request,
                               CustomUserDetails userDetails) {
        councilService.findActiveCouncil(asctId);

        String eno = userDetails.getEno();

        for (CouncilDto.EvaluationItem item : request.items()) {
            // 1~2점 시 의견 필수 검증
            if (item.ckgRcrd() != null && item.ckgRcrd() <= 2) {
                if (item.ckgOpnn() == null || item.ckgOpnn().isBlank()) {
                    String itemNm = CHECK_ITEM_NAMES.getOrDefault(item.ckgItmC(), item.ckgItmC());
                    throw new IllegalArgumentException(
                        "점수 1~2점 입력 시 의견 작성이 필수입니다. 항목: " + itemNm);
                }
            }

            // upsert: 기존 의견 있으면 update, 없으면 신규 INSERT
            evaluationRepository
                    .findByAsctIdAndEnoAndCkgItmCAndDelYn(asctId, eno, item.ckgItmC(), "N")
                    .ifPresentOrElse(
                        // 기존 의견 업데이트
                        existing -> existing.update(item.ckgRcrd(), item.ckgOpnn()),
                        // 신규 INSERT
                        () -> {
                            Bevalm evaluation = Bevalm.builder()
                                    .asctId(asctId)
                                    .eno(eno)
                                    .ckgItmC(item.ckgItmC())
                                    .ckgRcrd(item.ckgRcrd())
                                    .ckgOpnn(item.ckgOpnn())
                                    .build();
                            evaluationRepository.save(evaluation);
                        }
                    );
        }

        // 협의회 상태 전이: IN_PROGRESS → EVALUATING (첫 제출 시 1회만)
        // Plan SC: 이미 EVALUATING 이상이면 상태 전이 skip
        String currentStatus = councilService.findActiveCouncil(asctId).getAsctSts();
        if ("IN_PROGRESS".equals(currentStatus)) {
            councilService.changeStatus(asctId, "EVALUATING");
        }

        // 전원 제출 완료 시 EVALUATING → RESULT_WRITING 자동 전이
        // 조건: 현재 상태가 EVALUATING이고, 모든 위원이 6개 항목을 전부 제출한 경우
        if ("EVALUATING".equals(currentStatus) || "IN_PROGRESS".equals(currentStatus)) {
            if (isAllMembersSubmitted(asctId)) {
                councilService.changeStatus(asctId, "RESULT_WRITING");
            }
        }
    }

    /**
     * 모든 평가위원이 6개 점검항목을 전부 제출했는지 확인
     *
     * <p>위원 전원의 사번을 조회한 후, 각 사번에 대해 6개 항목 제출 여부를 검증합니다.
     * 단 한 명이라도 미제출 항목이 있으면 false를 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 전원 제출 완료 여부
     */
    private boolean isAllMembersSubmitted(String asctId) {
        // 등록된 평가위원 사번 목록 조회
        List<Bcmmtm> members = committeeRepository.findByAsctIdAndDelYn(asctId, "N");
        if (members.isEmpty()) return false;

        Set<String> memberEnos = members.stream()
                .map(Bcmmtm::getEno)
                .collect(Collectors.toSet());

        // 제출된 평가의견에서 6개 항목을 모두 제출한 사번 목록 추출
        List<Bevalm> allEvaluations = evaluationRepository.findByAsctIdAndDelYn(asctId, "N");

        // 사번별 제출 항목 수 집계
        Map<String, Long> submitCountByEno = allEvaluations.stream()
                .collect(Collectors.groupingBy(Bevalm::getEno, Collectors.counting()));

        // 전원이 6개 항목을 모두 제출했는지 검사
        return memberEnos.stream()
                .allMatch(eno -> submitCountByEno.getOrDefault(eno, 0L) >= CHECK_ITEM_ORDER.size());
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 평가의견 목록에서 사번 중복 없이 사용자 정보 Map 생성 (N+1 방지)
     */
    private Map<String, CuserI> buildUserMapFromEvaluations(List<Bevalm> evaluations) {
        return evaluations.stream()
                .map(Bevalm::getEno)
                .distinct()
                .map(eno -> userRepository.findByEno(eno))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(CuserI::getEno, u -> u, (a, b) -> a));
    }

    /**
     * 점검항목별 평균점수 목록 생성
     *
     * <p>native query 결과(Object[])를 CheckItemAvgScore DTO로 변환하고
     * 고정 항목 순서(CHECK_ITEM_ORDER)에 맞게 정렬합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 점검항목별 평균점수 목록 (최대 6개)
     */
    public List<CouncilDto.CheckItemAvgScore> buildAvgScores(String asctId) {
        List<Object[]> raw = evaluationRepository.findAverageScoreByItem(asctId, "N");

        // Object[]{ckgItmC, avgScore} → Map<ckgItmC, avgScore>
        Map<String, Double> avgMap = raw.stream()
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> ((Number) r[1]).doubleValue()
                ));

        // 고정 항목 순서로 정렬하여 반환
        return CHECK_ITEM_ORDER.stream()
                .filter(avgMap::containsKey)
                .map(code -> new CouncilDto.CheckItemAvgScore(
                        code,
                        CHECK_ITEM_NAMES.getOrDefault(code, code),
                        avgMap.get(code)
                ))
                .collect(Collectors.toList());
    }
}
