package com.kdb.it.domain.council.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Brsltm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 협의회 결과서 서비스 (Step 3 — 결과서 작성/검토)
 *
 * <p>IT관리자(ITPAD001)가 오프라인 협의회 결과를 포탈에 기록하는 서비스입니다.
 * 협의회는 오프라인으로 진행되므로 일정 확정(SCHEDULED) 직후 결과서 작성이 가능합니다.</p>
 *
 * <p>상태 전이 흐름:</p>
 * <pre>
 *   SCHEDULED
 *     │  IT관리자가 결과서 최초 저장 (POST /result)
 *     ↓
 *   RESULT_WRITING
 *     │  IT관리자가 결과서 확정 (PUT /result/confirm)
 *     ↓
 *   RESULT_REVIEW
 * </pre>
 *
 * <p>Design Ref: §2.1 ResultService — Step 3 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResultService {

    /** 결과서 리포지토리 (TAAABB_BRSLTM) */
    private final ResultRepository resultRepository;

    /** 협의회 기본 서비스 — 상태 전이용 */
    private final CouncilService councilService;

    /** 평가의견 서비스 — 항목별 평균점수 조회용 */
    private final EvaluationService evaluationService;

    /** 평가위원 리포지토리 — 결과서 검토 확인 처리 및 전원 확인 여부 판단용 */
    private final CommitteeRepository committeeRepository;

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 결과서 조회 (IT관리자)
     *
     * <p>결과서 내용(종합의견, 타당성검토의견, 첨부파일)과
     * 점검항목별 평균점수를 함께 반환합니다.
     * 아직 작성 전이면 avgScores만 채워진 빈 결과서를 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @return 결과서 내용 + 항목별 평균점수
     */
    public CouncilDto.ResultResponse getResult(String asctId) {
        councilService.findActiveCouncil(asctId);

        // 점검항목별 평균점수 (EvaluationService 위임)
        List<CouncilDto.CheckItemAvgScore> avgScores = evaluationService.buildAvgScores(asctId);

        // 결과서 조회 (아직 작성 전이면 빈 DTO 반환)
        return resultRepository.findByAsctIdAndDelYn(asctId, "N")
                .map(r -> new CouncilDto.ResultResponse(
                        r.getSynOpnn(),
                        r.getCkgOpnn(),
                        r.getFlMngNo(),
                        avgScores
                ))
                .orElse(new CouncilDto.ResultResponse(null, null, null, avgScores));
    }

    // =========================================================================
    // 저장
    // =========================================================================

    /**
     * 결과서 저장 (IT관리자)
     *
     * <p>기존 결과서가 있으면 update, 없으면 신규 INSERT합니다.
     * 최초 저장 시 협의회 상태를 SCHEDULED → RESULT_WRITING으로 전이합니다.</p>
     *
     * <p>이미 RESULT_WRITING 이상이면 상태 전이 skip (중복 전이 방지)</p>
     *
     * @param asctId  협의회ID
     * @param request 결과서 작성/수정 요청 (종합의견, 타당성검토의견, 첨부파일번호)
     */
    @Transactional
    public void saveResult(String asctId, CouncilDto.ResultRequest request) {
        councilService.findActiveCouncil(asctId);

        // upsert: 기존 결과서 있으면 update, 없으면 신규 INSERT
        resultRepository.findByAsctIdAndDelYn(asctId, "N")
                .ifPresentOrElse(
                    // 기존 결과서 업데이트
                    existing -> existing.update(
                            request.synOpnn(), request.ckgOpnn(), request.flMngNo()),
                    // 신규 INSERT
                    () -> {
                        Brsltm result = Brsltm.builder()
                                .asctId(asctId)
                                .synOpnn(request.synOpnn())
                                .ckgOpnn(request.ckgOpnn())
                                .flMngNo(request.flMngNo())
                                .build();
                        resultRepository.save(result);
                    }
                );

        // 협의회 상태 전이: → RESULT_WRITING (최초 저장 시 1회만)
        // RESULT_WRITING: 이미 '협의회 완료' 버튼으로 전이된 정상 흐름 (전이 skip)
        // EVALUATING: 구버전 평가의견 흐름 호환 처리
        // 참고: IN_PROGRESS → RESULT_WRITING 전이는 completeCouncil (PATCH /complete)에서 처리
        String currentStatus = councilService.findActiveCouncil(asctId).getAsctSts();
        if ("EVALUATING".equals(currentStatus)) {
            councilService.changeStatus(asctId, "RESULT_WRITING");
        }
    }

    /**
     * 결과서 확정 (IT관리자)
     *
     * <p>작성 완료된 결과서를 확정하고 협의회 상태를 RESULT_REVIEW로 전이합니다.
     * RESULT_REVIEW 단계에서 평가위원들이 결과서를 최종 검토합니다.</p>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 결과서가 아직 작성되지 않은 경우
     */
    @Transactional
    public void confirmResult(String asctId) {
        councilService.findActiveCouncil(asctId);

        // 결과서 존재 여부 검증
        resultRepository.findByAsctIdAndDelYn(asctId, "N")
                .orElseThrow(() -> new IllegalStateException(
                    "결과서가 아직 작성되지 않았습니다. 결과서를 먼저 저장해 주세요."));

        // 협의회 상태 전이: RESULT_WRITING → RESULT_REVIEW
        councilService.changeStatus(asctId, "RESULT_REVIEW");
    }

    /**
     * 평가위원 결과서 검토 확인 (평가위원)
     *
     * <p>RESULT_REVIEW 상태에서 평가위원(MAND/CALL)이 결과서를 확인합니다.
     * 간사(SECR)는 결과서 확인 의무가 없으므로 호출 시 예외를 반환합니다.
     * 전원 확인 완료 시 협의회 상태를 FINAL_APPROVAL로 자동 전이합니다.</p>
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @throws IllegalStateException RESULT_REVIEW 상태가 아닌 경우
     * @throws SecurityException     평가위원이 아니거나 간사인 경우
     */
    @Transactional
    public void reviewResult(String asctId, CustomUserDetails userDetails) {
        // RESULT_REVIEW 상태 검증
        var council = councilService.findActiveCouncil(asctId);
        if (!"RESULT_REVIEW".equals(council.getAsctSts())) {
            throw new IllegalStateException(
                "결과서 검토 확인은 RESULT_REVIEW 상태에서만 가능합니다. 현재 상태: " + council.getAsctSts());
        }

        // 위원 레코드 조회 — SECR 제외 검증
        Bcmmtm member = committeeRepository
                .findByAsctIdAndEnoAndDelYn(asctId, userDetails.getEno(), "N")
                .orElseThrow(() -> new SecurityException("해당 협의회의 평가위원이 아닙니다."));

        if ("SECR".equals(member.getVlrTp())) {
            throw new SecurityException("간사(SECR)는 결과서 검토 확인 대상이 아닙니다.");
        }

        // 결과서 확인 완료 처리 (CNFM_YN = 'Y')
        member.confirmReview();

        // 전체 MAND+CALL 위원의 CNFM_YN 확인 → 전원 'Y'이면 FINAL_APPROVAL 자동 전이
        List<Bcmmtm> evaluators = committeeRepository.findByAsctIdAndDelYn(asctId, "N")
                .stream()
                .filter(m -> !"SECR".equals(m.getVlrTp()))
                .toList();

        boolean allConfirmed = !evaluators.isEmpty()
                && evaluators.stream().allMatch(m -> "Y".equals(m.getCnfmYn()));

        if (allConfirmed) {
            councilService.changeStatus(asctId, "FINAL_APPROVAL");
        }
    }

    /**
     * 본인 결과서 검토 확인 여부 조회 (평가위원)
     *
     * <p>평가위원이 페이지 진입 시 이미 결과서 확인을 완료했는지 조회합니다.
     * 완료 시 버튼 대신 완료 UI를 표시하는 데 사용합니다.</p>
     *
     * @param asctId      협의회ID
     * @param userDetails 로그인한 평가위원
     * @return true: 이미 확인 완료, false: 미확인
     */
    public boolean getMyReviewStatus(String asctId, CustomUserDetails userDetails) {
        return committeeRepository
                .findByAsctIdAndEnoAndDelYn(asctId, userDetails.getEno(), "N")
                .map(m -> "Y".equals(m.getCnfmYn()))
                .orElse(false);
    }
}
