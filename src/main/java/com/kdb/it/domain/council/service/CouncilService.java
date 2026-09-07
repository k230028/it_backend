package com.kdb.it.domain.council.service;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.plan.repository.BplanmRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import com.kdb.it.domain.budget.project.service.ProjectBudgetSummaryService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.dto.CouncilProjectRow;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bplevm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import com.kdb.it.domain.council.repository.PlanEvaluationRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정보화실무협의회 기본 서비스
 *
 * <p>협의회 목록 조회, 신규 생성, 단건 조회, 상태 전이를 담당합니다.
 *
 * <p>권한별 조회 범위:
 *
 * <ul>
 *   <li>일반사용자(ITPZZ001): 소속 부서(BBR_C) 기준 사업의 협의회만 조회
 *   <li>관리자(ITPAD001): 전체 협의회 조회
 *   <li>평가위원: BCMMTM에 ENO가 있는 협의회만 조회
 * </ul>
 *
 * <p>협의회 ID 채번 형식: {@code ASCT-{연도}-{4자리순번}} (예: ASCT-2026-0001)
 *
 * <p>설계 참조: §2.1 아키텍처 결정 — 클린 아키텍처, 서비스 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouncilService {

    /** 협의회 기본정보 리포지토리 (TPRMPP_BASCTM) */
    private final CouncilRepository councilRepository;

    /** 사업개요 리포지토리 (TPRMPP_BPOVWM) — 사업명 조회용 */
    private final ProjectOverviewRepository projectOverviewRepository;

    /** 정보화사업 리포지토리 — 사업명/전결권자 조회용 */
    private final ProjectRepository projectRepository;

    /** 품목 리포지토리 — 협의회 당해예산 원화 합계 계산용 */
    private final ProjectItemRepository projectItemRepository;

    /** 품목 기준 예산 합계 계산 서비스 — 협의회 당해예산 원화 합계 계산용 */
    private final ProjectBudgetSummaryService projectBudgetSummaryService;

    /** 정보화사업관계(BPROJA) 동기화 서비스: 협의회 단계 상태 갱신용 */
    private final BprojaSyncService bprojaSyncService;

    /** 평가위원 리포지토리 — completeCouncil 완료 검증용 */
    private final CommitteeRepository committeeRepository;

    /** 평가의견 리포지토리 — completeCouncil 완료 검증용 (사업 협의회 6항목) */
    private final EvaluationRepository evaluationRepository;

    /** 계획협의회 사업별 적정/유보 리포지토리 — completeCouncil 완료 검증용 (dbrTc='02') */
    private final PlanEvaluationRepository planEvaluationRepository;

    /** 계획 마스터 리포지토리 — 계획협의회(dbrTc='02') 목록 제목(정보기술부문계획 수립/조정) 조회용 */
    private final BplanmRepository bplanmRepository;

    /** 사용자 리포지토리 — 수신자 정보 조회용 */
    private final UserRepository userRepository;

    /** 조직 리포지토리 — 부서명 조회용 */
    private final OrganizationRepository organizationRepository;

    /** JPA EntityManager — 협의회 신규 INSERT persist용 (§5.12.1.1) */
    @PersistenceContext private EntityManager entityManager;

    // =========================================================================
    // 사업 상태 코드 (공통코드 그룹 IT_PTL_STS_TC, BPROJA.IT_PTL_STS_TC)
    // =========================================================================

    /** 협의회 신청 대상 상태: 예산편성 요청 결재완료 (09) */
    private static final String PRJ_STS_COUNCIL_TARGET = "09";

    /** 타당성검토 정실협 진행중 상태 (협의회 신청 시 전이) (45) */
    private static final String PRJ_STS_COUNCIL_IN_PROGRESS = "45";

    /** 타당성검토 정실협 완료 상태 (통보·생략 시 전이) (49) */
    private static final String PRJ_STS_COUNCIL_DONE = "49";

    /**
     * 협의회 진행상태 '생략' 코드 (CCODEM IT_PTL_ASCT_PRG_STS_TC '99')
     *
     * <p>IT_PTL_ASCT_PRG_STS_TC는 VARCHAR2(2)이므로 이전의 문자열 "SKIPPED"(7자)는 ORA-12899로 저장 실패했다. 선형
     * 흐름(01~13) 밖의 종료 상태로 '99'를 사용한다. (리뷰 C-1)
     */
    private static final String STS_COUNCIL_SKIPPED = "99";

    /** 개최준비(05) 이상 진행된 협의회 진행상태 — startPreparation 멱등 처리(재신청 시 되돌리지 않음)에 사용. */
    private static final Set<String> PREPARATION_OR_LATER_STATUSES =
            Set.of("05", "06", "07", "08", "09", "10", "11", "12", "13");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 권한별 협의회 목록 조회
     *
     * <p>Plan SC: Step 1~3 전 과정 온라인 처리 기반 목록 제공
     *
     * <p><strong>유니코드 이스케이프 주의</strong>: 쿼리 파라미터에 한글 리터럴 대신 유니코드 이스케이프({@code &#92;uXXXX})를 사용하는
     * 이유는 Oracle 소스 파일 인코딩(EUC-KR) 환경에서 한글 직접 삽입 시 문자 깨짐이 발생하는 문제를 방지하기 위함입니다. 빌드 환경 인코딩 표준화 후 한글
     * 리터럴로 교체할 예정입니다.
     *
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 권한에 맞는 협의회 목록
     */
    public List<CouncilDto.ListResponse> getCouncilList(CustomUserDetails userDetails) {
        log.debug(
                "[CouncilList] eno={}, isAdmin={}, isCommitteeMember={}, bbrC={}",
                userDetails.getEno(),
                userDetails.isAdmin(),
                isCommitteeMember(userDetails),
                userDetails.getBbrC());

        if (userDetails.isAdmin()) {
            // 관리자: 전체 부서 대상으로 결재완료 사업(미신청 포함) + 기신청 협의회 통합 조회
            List<CouncilProjectRow> rows =
                    councilRepository.findProjectRowsForCouncilAll(
                            PRJ_STS_COUNCIL_IN_PROGRESS, PRJ_STS_COUNCIL_TARGET);
            log.debug("[CouncilList] admin query result count={}", rows.size());
            // 당해예산을 품목 1회 배치 조회로 미리 계산 (행별 N+1 제거)
            Map<String, BigDecimal> budgetMap =
                    deriveCurrentYearBudgets(rows.stream().map(row -> row.abusMngNo()).toList());
            List<CouncilDto.ListResponse> result =
                    new java.util.ArrayList<>(
                            rows.stream()
                                    .map(
                                            row ->
                                                    CouncilResponseMapper.toListResponse(
                                                            row, budgetMap))
                                    .toList());
            // 계획협의회(dbrTc='02')는 사업이 아닌 계획(BPLANM)을 참조해 사업 기반 쿼리에 잡히지 않으므로 별도로 덧붙인다.
            result.addAll(
                    councilRepository.findByItPtlAsctDbrTcAndDelYn("02", "N").stream()
                            .map(council -> toListResponseFromEntity(council, budgetMap))
                            .toList());
            return result;
        }

        if (userDetails.isInfoSecAdmin()) {
            // 정보보호관리자(ITPAD002): 전체 부서 대상으로 조회하되 다음만 표출한다.
            //  1) 미신청 사업 중 '정보보호 소요자원(BITEMM.SECT_SYS_UTZ_YN='Y')' 보유 사업 —
            //     타당성검토 신청 시 심의유형 '04(정보보호시스템)'가 드롭다운에 뜨는 것과 동일 조건(생성 대상). (PRD_c_20260803 #1)
            //  2) 신청된 정보보호시스템(dbrTc='04') 협의회
            //  3) 본인이 평가위원으로 배정된 협의회(심의유형 무관 — 정보시스템(03) 등 배정 건을 놓치지 않도록)
            List<CouncilProjectRow> rows =
                    councilRepository.findProjectRowsForCouncilAll(
                            PRJ_STS_COUNCIL_IN_PROGRESS, PRJ_STS_COUNCIL_TARGET);
            Map<String, BigDecimal> budgetMap =
                    deriveCurrentYearBudgets(rows.stream().map(row -> row.abusMngNo()).toList());
            Set<String> memberAsctIds =
                    councilRepository.findByCommitteeMember(userDetails.getEno(), "N").stream()
                            .map(Basctm::getItPtlAsctId)
                            .collect(Collectors.toSet());
            List<CouncilDto.ListResponse> result =
                    rows.stream()
                            .map(row -> CouncilResponseMapper.toListResponse(row, budgetMap))
                            .filter(
                                    r ->
                                            (!r.applied() && r.hasInfoSecResource())
                                                    || "04".equals(r.dbrTc())
                                                    || memberAsctIds.contains(r.asctId()))
                            .toList();
            log.debug("[CouncilList] infosec-admin filtered count={}", result.size());
            return result;
        }

        if (isCommitteeMember(userDetails)) {
            // 평가위원: 배정된 협의회만 조회
            List<Basctm> councils =
                    councilRepository.findByCommitteeMember(userDetails.getEno(), "N");
            // 당해예산을 품목 1회 배치 조회로 미리 계산 (행별 N+1 제거)
            Map<String, BigDecimal> budgetMap =
                    deriveCurrentYearBudgets(
                            councils.stream().map(council -> council.getAbusMngNo()).toList());
            return councils.stream().map(c -> toListResponseFromEntity(c, budgetMap)).toList();
        }

        // 일반사용자: SVN_DPM = 사용자 BBR_C 조건으로 결재완료 사업 + 기신청 협의회 통합 조회
        List<CouncilProjectRow> rows =
                councilRepository.findProjectRowsForCouncilByDepartment(
                        userDetails.getBbrC(), PRJ_STS_COUNCIL_IN_PROGRESS, PRJ_STS_COUNCIL_TARGET);
        log.debug(
                "[CouncilList] user query bbrC={}, result count={}",
                userDetails.getBbrC(),
                rows.size());
        // 당해예산을 품목 1회 배치 조회로 미리 계산 (행별 N+1 제거)
        Map<String, BigDecimal> budgetMap =
                deriveCurrentYearBudgets(rows.stream().map(row -> row.abusMngNo()).toList());
        return rows.stream()
                .map(row -> CouncilResponseMapper.toListResponse(row, budgetMap))
                .toList();
    }

    /**
     * 협의회 단건 상세 조회
     *
     * @param asctId 협의회ID
     * @return 협의회 상세 정보
     * @throws IllegalArgumentException 존재하지 않는 협의회
     */
    public CouncilDto.DetailResponse getCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);
        return toDetailResponse(council);
    }

    // =========================================================================
    // 생성
    // =========================================================================

    /**
     * 협의회 신규 신청
     *
     * <p>소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 신청합니다. 초기 상태는 DRAFT(작성중)로 설정됩니다.
     *
     * @param request 협의회 신청 요청 (프로젝트 정보, 심의유형)
     * @param userDetails 신청자 정보
     * @return 생성된 협의회ID
     */
    @Transactional
    public String createCouncil(CouncilDto.CreateRequest request, CustomUserDetails userDetails) {
        // 정보기술부문계획 협의회(dbrTc='02')는 단일 사업이 아니라 계획(BPLANM)을 심의 대상으로 가진다.
        boolean isPlanCouncil = "02".equals(request.dbrTc());

        // 계획협의회는 계획(reqDocNo)당 하나만 진행한다. 이미 신청된 계획협의회가 있으면 새로 만들지 않고
        // 기존 협의회ID를 반환한다(중복 생성 방지 · 계획 상세에서 재신청 시 기존 신청서 재사용).
        if (isPlanCouncil) {
            var existing =
                    councilRepository.findByAbusMngNoAndDelYn(request.reqDocNo(), "N").stream()
                            .filter(council -> "02".equals(council.getItPtlAsctDbrTc()))
                            .findFirst();
            if (existing.isPresent()) {
                return existing.get().getItPtlAsctId();
            }
        }

        // 협의회ID 채번: ASCT-{연도}-{4자리순번}
        String asctId = generateItPtlAsctId();

        // 협의회 기본정보 생성 (초기 상태: DRAFT)
        Basctm council =
                Basctm.builder()
                        .itPtlAsctId(asctId)
                        // 운영 BASCTM에는 별도 계획키가 없으므로 계획협의회는 ABUS_MNG_NO에 계획관리번호를 저장한다.
                        .abusMngNo(isPlanCouncil ? request.reqDocNo() : request.prjMngNo())
                        .sno(isPlanCouncil ? null : request.prjSno())
                        .itPtlAsctPrgStsTc("01")
                        .itPtlAsctDbrTc(request.dbrTc())
                        .build();

        // 신규 INSERT는 persist()로 @PrePersist 발화 보장 (merge 분기 회귀 방지, §5.12.1.1)
        entityManager.persist(council);

        // 계획협의회(02)는 단일 사업 상태 전이 대상이 아니므로 사업 상태 동기화를 생략한다.
        if (!isPlanCouncil) {
            // 사업 상태를 '타당성검토 정실협 진행중'(32)으로 전이
            bprojaSyncService.upsert(
                    request.prjMngNo(), request.prjMngNo(), PRJ_STS_COUNCIL_IN_PROGRESS);
        }

        return asctId;
    }

    // =========================================================================
    // 상태 전이
    // =========================================================================

    /**
     * 협의회 상태 변경
     *
     * <p>각 서비스(FeasibilityService, CommitteeService 등)에서 비즈니스 이벤트 완료 시 호출합니다.
     *
     * @param asctId 협의회ID
     * @param targetSts 변경할 상태 코드 (CCODEM ASCT_STS_C 기준)
     */
    @Transactional
    public void changeStatus(String asctId, String targetSts) {
        Basctm council = findActiveCouncil(asctId);
        council.changeStatus(targetSts);
        // JPA Dirty Checking으로 자동 반영
    }

    /**
     * 협의회 개최 시작 처리 (SCHEDULED → IN_PROGRESS)
     *
     * <p>IT관리자가 오프라인 협의회 개최를 확인하고 진행 상태로 전이합니다. SCHEDULED 상태에서만 호출 가능합니다.
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 현재 상태가 SCHEDULED가 아닌 경우
     */
    @Transactional
    public void startCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        if (!"06".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "협의회 개최 시작은 SCHEDULED(006) 상태에서만 가능합니다. 현재 상태: "
                            + council.getItPtlAsctPrgStsTc());
        }

        council.changeStatus("07");
    }

    /**
     * 협의회 완료 처리 (IN_PROGRESS/EVALUATING → RESULT_WRITING)
     *
     * <p>모든 평가위원의 평가 제출이 확인된 후 IT관리자가 호출합니다. IN_PROGRESS 또는 EVALUATING 상태에서 호출 가능합니다.
     *
     * <p>완료 조건:
     *
     * <ol>
     *   <li>평가위원(간사 제외: MAND + CALL)이 1명 이상 존재
     *   <li>모든 평가위원이 6개 항목 평가의견을 제출 완료
     * </ol>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 진행 중 상태가 아니거나 평가 미완료인 경우
     */
    @Transactional
    public void completeCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);
        String status = council.getItPtlAsctPrgStsTc();

        // IN_PROGRESS(평가 미시작) 또는 EVALUATING(평가 진행 중) 상태에서만 가능
        if (!"07".equals(status) && !"08".equals(status)) {
            throw new IllegalStateException("협의회 완료는 진행 중 상태에서만 가능합니다. 현재 상태: " + status);
        }

        // 평가 대상 위원 조회 (간사 제외: MAND(001) + CALL(002)만 평가 의무)
        List<Bcmmtm> evaluators =
                committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                        .filter(m -> !"03".equals(m.getItPtlAsctMebTc()))
                        .toList();

        if (evaluators.isEmpty()) {
            throw new IllegalStateException("평가위원이 선정되지 않았습니다.");
        }

        long incompleteCount;
        if ("02".equals(council.getItPtlAsctDbrTc())) {
            // 계획협의회는 사업별 적정/유보를 일괄 제출하므로 위원이 한 건이라도 제출했으면 완료로 본다.
            Set<String> submittedEnos =
                    planEvaluationRepository.findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                            .map(Bplevm::getEno)
                            .collect(Collectors.toSet());
            incompleteCount =
                    evaluators.stream().filter(m -> !submittedEnos.contains(m.getEno())).count();
        } else {
            // 협의회별 GROUP BY 집계로 N+1을 막고, 데이터 이상으로 중복 키가 있어도 합산한다.
            Map<String, Long> submitCountByEno =
                    evaluationRepository.countByEnoForCouncil(asctId, "N").stream()
                            .collect(
                                    Collectors.toMap(
                                            row -> (String) row[0],
                                            row -> ((Number) row[1]).longValue(),
                                            (left, right) -> left + right));
            // 6개 항목 미만(미제출 포함=Map 누락 시 0)인 평가자 수 집계
            incompleteCount =
                    evaluators.stream()
                            .filter(m -> submitCountByEno.getOrDefault(m.getEno(), 0L) < 6)
                            .count();
        }

        if (incompleteCount > 0) {
            throw new IllegalStateException(
                    "아직 평가의견이 입력되지 않은 평가위원이 있습니다. (" + incompleteCount + "명 미완료)");
        }

        council.changeStatus("09");
    }

    /**
     * 추진부서 통보 처리 (COMPLETED)
     *
     * <p>협의회가 완료된 후 IT관리자가 추진부서 담당자에게 결과를 통보합니다. 사업 상태(BPROJA.IT_PTL_STS_TC)를 '타당성검토 정실협 완료'(49)로
     * 변경하고, 수신자(협의회 최초 등록자) 정보를 반환합니다.
     *
     * @param asctId 협의회ID
     * @return 수신자(추진부서 담당자) 정보 DTO
     * @throws IllegalStateException 현재 상태가 COMPLETED가 아닌 경우
     */
    @Transactional
    public CouncilDto.NotifyResponse notifyCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        // COMPLETED 상태에서만 통보 가능 (PRD §31: 완료 = 013)
        if (!"13".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "통보는 완료(013) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }

        // 사업 상태 전이: '타당성검토 정실협 진행중'(45) → '타당성검토 정실협 완료'(49)
        bprojaSyncService.upsert(
                council.getAbusMngNo(), council.getAbusMngNo(), PRJ_STS_COUNCIL_DONE);

        // 수신자(협의회 최초 등록자 = 추진부서 담당자) 정보 조회
        String recipientEno = council.getFstEnrUsid();
        String usrNm = null;
        String bbrNm = null;
        String temNm = null;

        if (recipientEno != null) {
            CuserI recipient = userRepository.findByEno(recipientEno).orElse(null);
            if (recipient != null) {
                usrNm = recipient.getUsrNm();
                temNm = recipient.getTemNm();
                // 부서명은 CorgnI에서 조회
                if (recipient.getBbrC() != null) {
                    CorgnI org = organizationRepository.findById(recipient.getBbrC()).orElse(null);
                    if (org != null) {
                        bbrNm = org.getBbrNm();
                    }
                }
            } else {
                // 수신자 사번은 있으나 사용자 정보를 못 찾음 → 통보 대상 누락 추적용 경고 (리뷰 3-2/M-2)
                log.warn(
                        "[협의회통보] 수신자 사용자 정보 조회 실패 - asctId={}, recipientEno={}",
                        asctId,
                        recipientEno);
            }
        } else {
            log.warn("[협의회통보] 수신자 사번 미확인 - asctId={} (FST_ENR_USID null)", asctId);
        }

        return new CouncilDto.NotifyResponse(recipientEno, usrNm, bbrNm, temNm);
    }

    /**
     * 정보화실무협의회 생략 처리 (APPROVED(04) → 생략(99))
     *
     * <p>IT관리자가 타당성검토표 검토 후 해당 사업이 협의회 생략 대상임을 확인한 경우 호출합니다.
     *
     * <p>처리 내용:
     *
     * <ol>
     *   <li>협의회 상태: 결재완료(04) → 생략(99)
     *   <li>사업 상태(IT_PTL_STS_TC): '타당성검토 정실협 진행중'(45) → '타당성검토 정실협 완료'(49)
     * </ol>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 현재 상태가 APPROVED(04)가 아닌 경우
     */
    @Transactional
    public void skipCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        // APPROVED 상태에서만 생략 가능
        if (!"04".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "생략 처리는 결재완료(04) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }

        // 협의회 상태 전이: 결재완료(04) → 생략(99)
        council.changeStatus(STS_COUNCIL_SKIPPED);

        // 사업 상태 전이: '타당성검토 정실협 진행중'(45) → '타당성검토 정실협 완료'(49)
        bprojaSyncService.upsert(
                council.getAbusMngNo(), council.getAbusMngNo(), PRJ_STS_COUNCIL_DONE);
    }

    /**
     * 개최준비 시작 (결재완료 → 개최준비)
     *
     * <p>IT관리자가 타당성검토표 검토 후 '개최준비 진행'을 선택한 경우 호출합니다. 기존에는 평가위원 저장(saveCommittee)의 부수효과로 04→05 전이가
     * 일어났으나, 의사결정 지점(Step1 상세의 '개최준비 진행' 버튼)으로 전이를 명시화했습니다. (PRD_c_20260620 #2)
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 일반 협의회가 결재완료(04)가 아니거나 계획협의회가 신청(01)이 아닌 경우
     */
    @Transactional
    public void startPreparation(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        // 정보기술부문계획(dbrTc='02')은 타당성검토표/결재 단계가 없어 신청(01)에서 바로 개최준비로 전이한다.
        // 그 외 심의유형(03/04/05)은 기존대로 결재완료(04)에서만 전이 가능.
        boolean isPlanCouncil = "02".equals(council.getItPtlAsctDbrTc());
        String current = council.getItPtlAsctPrgStsTc();
        boolean allowed = "04".equals(current) || (isPlanCouncil && "01".equals(current));
        if (allowed) {
            // 협의회 상태 전이: → PREPARING(05)
            council.changeStatus("05");
            return;
        }
        // 이미 개최준비(05) 이상 진행된 협의회는 재신청(계획 상세에서 기존 협의회 재사용) 시
        // 상태를 되돌리지 않고 그대로 둔다(멱등). 그 외(비정상 상태)만 예외로 막는다.
        if (PREPARATION_OR_LATER_STATUSES.contains(current)) {
            return;
        }
        throw new IllegalStateException("개최준비 전이는 결재완료(004) 상태에서만 가능합니다. 현재 상태: " + current);
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 협의회 목록의 모든 사업관리번호에 대한 당해예산을 1회 배치 조회로 계산.
     *
     * <p>전체 사업관리번호의 활성 품목(DEL_YN='N')을 1회 배치 조회한 뒤 메모리에서 사업관리번호별로 그룹핑하여 AMT 원화 합계 로직을 적용한다.
     *
     * @param abusMngNos 사업관리번호 목록 (null·빈 값은 무시)
     * @return 사업관리번호 → 당해예산 맵. 요청된 모든 사업관리번호에 대해 값이 채워지며, 품목이 없는 사업관리번호도 빈 품목 목록으로 동일 합산 로직을 적용한
     *     값(예: 0)을 가진다
     */
    private Map<String, BigDecimal> deriveCurrentYearBudgets(Collection<String> abusMngNos) {
        List<String> keys =
                abusMngNos.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
        if (keys.isEmpty()) {
            return new HashMap<>();
        }
        // 전체 사업관리번호의 활성 품목을 1회 배치 조회한 뒤 사업관리번호별로 그룹핑
        Map<String, List<ProjectItemRepository.ProjectItemBudgetView>> itemsByAbus =
                projectItemRepository.findBudgetViewsByAbusMngNoInAndDelYn(keys, "N").stream()
                        .collect(Collectors.groupingBy(item -> item.getAbusMngNo()));
        Map<String, BigDecimal> result = new HashMap<>();
        // 요청된 모든 키를 순회한다(itemsByAbus가 아님). 품목이 없는 키도 빈 목록으로
        // Task 3의 원화 스냅샷 계약을 적용한다. AMT는 이미 당해금액이므로 MPL을 다시 차감하지 않는다.
        for (String abusMngNo : keys) {
            List<ProjectItemRepository.ProjectItemBudgetView> items =
                    itemsByAbus.getOrDefault(abusMngNo, List.of());
            var tmp = ProjectDto.Response.builder().build();
            projectBudgetSummaryService.applyBudgetSummaryViews(tmp, items);
            result.put(abusMngNo, tmp.getTyyBgAmt());
        }
        return result;
    }

    /**
     * 활성 협의회 조회 (삭제되지 않은 항목)
     *
     * @param asctId 협의회ID
     * @return Basctm 엔티티
     * @throws IllegalArgumentException 존재하지 않는 경우
     */
    public Basctm findActiveCouncil(String asctId) {
        return councilRepository
                .findByItPtlAsctIdAndDelYn(asctId, "N")
                .orElseThrow(
                        () -> new IllegalArgumentException("존재하지 않는 협의회입니다. asctId=" + asctId));
    }

    /** 여러 협의회의 활성 행을 일괄 조회하여 ID별로 반환합니다. */
    public Map<String, Basctm> findActiveCouncils(Collection<String> asctIds) {
        if (asctIds == null || asctIds.isEmpty()) return Map.of();
        Map<String, Basctm> councilsById =
                councilRepository.findByItPtlAsctIdInAndDelYn(asctIds, "N").stream()
                        .collect(Collectors.toMap(Basctm::getItPtlAsctId, council -> council));
        for (String asctId : asctIds) {
            if (!councilsById.containsKey(asctId)) {
                throw new IllegalArgumentException("존재하지 않는 협의회입니다. asctId=" + asctId);
            }
        }
        return councilsById;
    }

    // =========================================================================
    // 권한 검증 (관리 액션 진입 가드) — 리뷰 PRD_c_20260701 1-1
    // =========================================================================

    /**
     * 협의회 관리 권한 검증 (개최준비·진행·일정·결과 등 관리 액션 공통 가드).
     *
     * <p>IT관리자(ITPAD001)는 전 심의유형, 정보보호관리자(ITPAD002)는 정보보호시스템(dbrTc='04') 협의회에 한해 관리할 수
     * 있습니다(PRD_c_20260620 #3). 프론트 canManageCouncil과 동일 스코프이며, 프론트 가드는 UX 보조이므로 서버 진입 가드가 최종 보안
     * 경계입니다.
     *
     * @param asctId 협의회ID
     * @param userDetails 요청자 (JWT 주입)
     * @throws AccessDeniedException 관리 권한이 없는 경우
     */
    public void verifyCouncilManager(String asctId, CustomUserDetails userDetails) {
        if (userDetails != null && userDetails.isAdmin()) {
            return;
        }
        if (userDetails != null
                && userDetails.isInfoSecAdmin()
                && "04".equals(findActiveCouncil(asctId).getItPtlAsctDbrTc())) {
            return;
        }
        throw new AccessDeniedException("협의회 관리 권한이 없습니다.");
    }

    /**
     * IT관리자(ITPAD001) 전용 액션 권한 검증.
     *
     * <p>협의회 직접 생략·결재 콜백처럼 IT기획 관할이 확정된 액션에 사용합니다. (정보보호시스템 사업의 생략은 판정 요청→IT기획 결재를 거쳐야 하므로 직접 생략은
     * IT관리자 전용)
     *
     * @param userDetails 요청자 (JWT 주입)
     * @throws AccessDeniedException IT관리자가 아닌 경우
     */
    public void verifyAdmin(CustomUserDetails userDetails) {
        if (userDetails == null || !userDetails.isAdmin()) {
            throw new AccessDeniedException("IT관리자 권한이 필요합니다.");
        }
    }

    /**
     * 협의회ID 채번
     *
     * <p>형식: ASCT-{연도}-{4자리순번} (예: ASCT-2026-0001)
     *
     * @return 생성된 협의회ID
     */
    private String generateItPtlAsctId() {
        int year = java.time.LocalDate.now().getYear();
        Long seq = councilRepository.getNextSequenceValue();
        return String.format("ASCT-%d-%04d", year, seq);
    }

    /**
     * 현재 사용자가 평가위원인지 확인
     *
     * <p>ITPZZ001이지만 특정 협의회에 배정된 경우 평가위원으로 동작합니다. 목록 조회 시 권한 분기 판단에 사용합니다.
     *
     * @param userDetails 현재 사용자 정보
     * @return 평가위원이면 true (일반사용자이면서 BCMMTM에 ENO가 있는 경우)
     */
    private boolean isCommitteeMember(CustomUserDetails userDetails) {
        // 일반사용자(ITPZZ001)인 경우 BCMMTM 배정 여부 확인
        // 관리자는 이미 위에서 분기 처리되므로 이 시점은 비관리자임
        if (!userDetails.hasAthId(CustomUserDetails.ATH_USER)) {
            return false;
        }
        // BCMMTM에 ENO가 있는 협의회 수 > 0 이면 평가위원
        List<Basctm> memberCouncils =
                councilRepository.findByCommitteeMember(userDetails.getEno(), "N");
        return !memberCouncils.isEmpty();
    }

    /**
     * Basctm 엔티티 → ListResponse 변환 (평가위원용, PRD §16)
     *
     * <p>리포지토리 조회만 여기서 수행하고 필드 매핑은 {@link CouncilResponseMapper}에 위임합니다.
     */
    private CouncilDto.ListResponse toListResponseFromEntity(
            Basctm council, Map<String, BigDecimal> budgetMap) {
        // BPROJM 조회 — 사업 상세 정보 원천
        var projectOpt =
                projectRepository.findById(new BprojmId(council.getAbusMngNo(), council.getSno()));
        // 소요자원(BITEMM) 정보보호 항목 존재 여부 — 심의유형 04 노출 조건
        boolean hasInfoSecResource =
                projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn(
                        council.getAbusMngNo(), "Y", "N");
        return CouncilResponseMapper.toListResponse(
                council,
                projectOpt.orElse(null),
                resolveListTitle(council, projectOpt),
                // 당해예산: 품목 활성 항목(DEL_YN='N')의 ∑AMT 원화 합계 (배치 조회 결과 사용)
                budgetMap.get(council.getAbusMngNo()),
                hasInfoSecResource);
    }

    /**
     * 협의회 목록 제목 해석
     *
     * <p>계획협의회(dbrTc='02')는 사업이 아닌 계획 단위이므로 '정보기술부문계획 수립/조정'을 제목으로 씁니다. 그 외 사업 협의회는 BPOVWM(타당성검토표)의
     * 사업명을 우선하고, 없으면 BPROJM의 사업명을 씁니다.
     *
     * @param council 협의회 엔티티
     * @param projectOpt BPROJM 조회 결과. 비어 있으면 BPOVWM도 없을 때 null을 반환한다
     * @return 목록에 표시할 제목. 어느 원천에서도 이름을 찾지 못하면 null
     */
    private String resolveListTitle(Basctm council, Optional<Bprojm> projectOpt) {
        if ("02".equals(council.getItPtlAsctDbrTc())) {
            return bplanmRepository
                    .findByReqDocNoAndDelYn(council.getAbusMngNo(), "N")
                    .map(plan -> "정보기술부문계획 " + ("02".equals(plan.getItPtlPlnTpC()) ? "조정" : "수립"))
                    .orElse("정보기술부문계획");
        }
        return projectOverviewRepository
                .findByItPtlAsctIdAndDelYn(council.getItPtlAsctId(), "N")
                .map(value -> value.getAbusNm())
                .orElseGet(() -> projectOpt.map(p -> p.getAbusNm()).orElse(null));
    }

    /** Basctm → DetailResponse 변환 BPROJM에서 사업명(prjNm)과 전결권자(edrt)를 함께 조회합니다. */
    private CouncilDto.DetailResponse toDetailResponse(Basctm council) {
        // BPROJM에서 타당성검토표 기본값 필드 조회
        String prjNm = null;
        String edrt = null;
        java.time.LocalDate sttDt = null;
        java.time.LocalDate endDt = null;
        String ncs = null;
        BigDecimal prjBg = null;
        String prjDes = null;
        String xptEff = null;
        String svnDpm = null; // 주관부서코드 — 추진부서 담당자 식별용(사전 Q&A 답변 권한)
        var projectOpt =
                projectRepository.findById(new BprojmId(council.getAbusMngNo(), council.getSno()));
        if (projectOpt.isPresent()) {
            var p = projectOpt.get();
            prjNm = p.getAbusNm();
            edrt = p.getEdrtTc();
            sttDt = p.getSttDtm();
            endDt = p.getEndDtm();
            ncs = p.getAbusPulNcsInf();
            prjBg = deriveCurrentYearBudgets(List.of(p.getAbusMngNo())).get(p.getAbusMngNo());
            prjDes = p.getAbusPulConeInf();
            xptEff = p.getAbusXptEffInf();
            svnDpm = p.getSvnDpmC();
        }

        return new CouncilDto.DetailResponse(
                council.getItPtlAsctId(),
                council.getAbusMngNo(),
                council.getSno(),
                council.getItPtlAsctPrgStsTc(),
                council.getItPtlAsctDbrTc(),
                council.getCnrcDt(),
                council.getCnrcSttTm(),
                council.getCnrcPlc(),
                prjNm,
                edrt,
                sttDt,
                endDt,
                ncs,
                prjBg,
                prjDes,
                xptEff,
                council.getCsfHeldYn(),
                svnDpm);
    }
}
