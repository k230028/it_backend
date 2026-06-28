package com.kdb.it.domain.council.service;

import java.util.List;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectBudgetSummaryService;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.EvaluationRepository;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 정보화실무협의회 기본 서비스
 *
 * <p>
 * 협의회 목록 조회, 신규 생성, 단건 조회, 상태 전이를 담당합니다.
 * </p>
 *
 * <p>
 * 권한별 조회 범위:
 * </p>
 * <ul>
 * <li>일반사용자(ITPZZ001): 소속 부서(BBR_C) 기준 사업의 협의회만 조회</li>
 * <li>관리자(ITPAD001): 전체 협의회 조회</li>
 * <li>평가위원: BCMMTM에 ENO가 있는 협의회만 조회</li>
 * </ul>
 *
 * <p>
 * 협의회 ID 채번 형식: {@code ASCT-{연도}-{4자리순번}} (예: ASCT-2026-0001)
 * </p>
 *
 * <p>
 * Design Ref: §2.1 Architecture Decision — Clean Architecture, 서비스 분리
 * </p>
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

    /** 품목 리포지토리 — 협의회 당해예산(파생) 계산용 */
    private final ProjectItemRepository projectItemRepository;

    /** 품목 기준 예산 합계 계산 서비스 — 협의회 당해예산(파생) 계산용 */
    private final ProjectBudgetSummaryService projectBudgetSummaryService;

    /** 평가위원 리포지토리 — completeCouncil 완료 검증용 */
    private final CommitteeRepository committeeRepository;

    /** 평가의견 리포지토리 — completeCouncil 완료 검증용 */
    private final EvaluationRepository evaluationRepository;

    /** 사용자 리포지토리 — 수신자 정보 조회용 */
    private final UserRepository userRepository;

    /** 조직 리포지토리 — 부서명 조회용 */
    private final OrganizationRepository organizationRepository;

    // =========================================================================
    // 사업 상태 코드 (공통코드 그룹 IT_PTL_STS_TC, BPROJM.IT_PTL_STS_TC)
    // =========================================================================

    /** 협의회 신청 대상 상태: 예산편성 작업 완료 (09, IT_PTL_STS_TC 재정렬 후) */
    private static final String PRJ_STS_COUNCIL_TARGET = "09";

    /** 타당성검토 정실협 진행중 상태 (협의회 신청 시 전이) (32) */
    private static final String PRJ_STS_COUNCIL_IN_PROGRESS = "32";

    /** 타당성검토 정실협 완료 상태 (통보·생략 시 전이) (39) */
    private static final String PRJ_STS_COUNCIL_DONE = "39";

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 권한별 협의회 목록 조회
     *
     * <p>
     * Plan SC: Step 1~3 전 과정 온라인 처리 기반 목록 제공
     * </p>
     *
     * <p>
     * <strong>유니코드 이스케이프 주의</strong>: 쿼리 파라미터에 한글 리터럴 대신
     * 유니코드 이스케이프({@code &#92;uXXXX})를 사용하는 이유는 Oracle 소스 파일 인코딩(EUC-KR) 환경에서
     * 한글 직접 삽입 시 문자 깨짐이 발생하는 문제를 방지하기 위함입니다.
     * 빌드 환경 인코딩 표준화 후 한글 리터럴로 교체할 예정입니다.
     * </p>
     *
     * @param userDetails 현재 로그인한 사용자 정보
     * @return 권한에 맞는 협의회 목록
     */
    public List<CouncilDto.ListResponse> getCouncilList(CustomUserDetails userDetails) {
        log.debug("[CouncilList] eno={}, isAdmin={}, isCommitteeMember={}, bbrC={}",
                userDetails.getEno(), userDetails.isAdmin(), isCommitteeMember(userDetails), userDetails.getBbrC());

        if (userDetails.isAdmin()) {
            // 관리자: 전체 부서 대상으로 결재완료 사업(미신청 포함) + 기신청 협의회 통합 조회
            List<Object[]> rows = councilRepository.findProjectsForCouncilAll(
                    PRJ_STS_COUNCIL_IN_PROGRESS, PRJ_STS_COUNCIL_TARGET);
            log.debug("[CouncilList] admin query result count={}", rows.size());
            return rows.stream().map(row -> toListResponseFromRow(row)).toList();
        }

        if (isCommitteeMember(userDetails)) {
            // 평가위원: 배정된 협의회만 조회
            return councilRepository.findByCommitteeMember(userDetails.getEno(), "N").stream()
                    .map(c -> toListResponseFromEntity(c))
                    .toList();
        }

        // 일반사용자: SVN_DPM = 사용자 BBR_C 조건으로 결재완료 사업 + 기신청 협의회 통합 조회
        List<Object[]> rows = councilRepository.findProjectsForCouncilByDepartment(
                userDetails.getBbrC(), PRJ_STS_COUNCIL_IN_PROGRESS, PRJ_STS_COUNCIL_TARGET);
        log.debug("[CouncilList] user query bbrC={}, result count={}", userDetails.getBbrC(), rows.size());
        return rows.stream().map(row -> toListResponseFromRow(row)).toList();
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
     * <p>
     * 소관부서 담당자(ITPZZ001)가 타당성검토표 작성 전 협의회를 신청합니다.
     * 초기 상태는 DRAFT(작성중)로 설정됩니다.
     * </p>
     *
     * @param request     협의회 신청 요청 (프로젝트 정보, 심의유형)
     * @param userDetails 신청자 정보
     * @return 생성된 협의회ID
     */
    @Transactional
    public String createCouncil(CouncilDto.CreateRequest request, CustomUserDetails userDetails) {
        // 협의회ID 채번: ASCT-{연도}-{4자리순번}
        String asctId = generateItPtlAsctId();

        // 협의회 기본정보 생성 (초기 상태: DRAFT)
        Basctm council = Basctm.builder()
                .itPtlAsctId(asctId)
                .abusMngNo(request.prjMngNo())
                .sno(request.prjSno())
                .itPtlAsctPrgStsTc("01")
                .itPtlAsctDbrTc(request.dbrTc())
                .build();

        councilRepository.save(council);

        // 사업 상태를 '타당성검토 정실협 진행중'(32)으로 전이
        councilRepository.updateProjectStatus(request.prjMngNo(), request.prjSno(),
                PRJ_STS_COUNCIL_IN_PROGRESS);

        return asctId;
    }

    // =========================================================================
    // 상태 전이
    // =========================================================================

    /**
     * 협의회 상태 변경
     *
     * <p>
     * 각 서비스(FeasibilityService, CommitteeService 등)에서 비즈니스 이벤트 완료 시 호출합니다.
     * </p>
     *
     * @param asctId    협의회ID
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
     * <p>
     * IT관리자가 오프라인 협의회 개최를 확인하고 진행 상태로 전이합니다.
     * SCHEDULED 상태에서만 호출 가능합니다.
     * </p>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 현재 상태가 SCHEDULED가 아닌 경우
     */
    @Transactional
    public void startCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        if (!"06".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "협의회 개최 시작은 SCHEDULED(006) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }

        council.changeStatus("07");
    }

    /**
     * 협의회 완료 처리 (IN_PROGRESS/EVALUATING → RESULT_WRITING)
     *
     * <p>
     * 모든 평가위원의 평가 제출이 확인된 후 IT관리자가 호출합니다.
     * IN_PROGRESS 또는 EVALUATING 상태에서 호출 가능합니다.
     * </p>
     *
     * <p>
     * 완료 조건:
     * </p>
     * <ol>
     * <li>평가위원(간사 제외: MAND + CALL)이 1명 이상 존재</li>
     * <li>모든 평가위원이 6개 항목 평가의견을 제출 완료</li>
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
            throw new IllegalStateException(
                    "협의회 완료는 진행 중 상태에서만 가능합니다. 현재 상태: " + status);
        }

        // 평가 대상 위원 조회 (간사 제외: MAND(001) + CALL(002)만 평가 의무)
        List<Bcmmtm> evaluators = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .stream()
                .filter(m -> !"03".equals(m.getItPtlAsctMebTc()))
                .toList();

        if (evaluators.isEmpty()) {
            throw new IllegalStateException("평가위원이 선정되지 않았습니다.");
        }

        // 각 위원별 6개 항목 제출 완료 여부 확인
        long incompleteCount = evaluators.stream()
                .filter(m -> evaluationRepository
                        .findByItPtlAsctIdAndEnoAndDelYn(asctId, m.getEno(), "N").size() < 6)
                .count();

        if (incompleteCount > 0) {
            throw new IllegalStateException(
                    "아직 평가의견이 입력되지 않은 평가위원이 있습니다. (" + incompleteCount + "명 미완료)");
        }

        council.changeStatus("09");
    }

    /**
     * 추진부서 통보 처리 (COMPLETED)
     *
     * <p>
     * 협의회가 완료된 후 IT관리자가 추진부서 담당자에게 결과를 통보합니다.
     * 사업 상태(BPROJM.IT_PTL_STS_TC)를 '타당성검토 정실협 완료'(39)로 변경하고,
     * 수신자(협의회 최초 등록자) 정보를 반환합니다.
     * </p>
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

        // 사업 상태 전이: '타당성검토 정실협 진행중'(32) → '타당성검토 정실협 완료'(39)
        councilRepository.updateProjectStatus(council.getAbusMngNo(), council.getSno(), PRJ_STS_COUNCIL_DONE);

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
            }
        }

        return new CouncilDto.NotifyResponse(recipientEno, usrNm, bbrNm, temNm);
    }

    /**
     * 정보화실무협의회 생략 처리 (APPROVED → SKIPPED)
     *
     * <p>
     * IT관리자가 타당성검토표 검토 후 해당 사업이 협의회 생략 대상임을 확인한 경우 호출합니다.
     * </p>
     *
     * <p>
     * 처리 내용:
     * </p>
     * <ol>
     * <li>협의회 상태: APPROVED → SKIPPED</li>
     * <li>사업 상태(IT_PTL_STS_TC): '타당성검토 정실협 진행중'(32) → '타당성검토 정실협 완료'(39)</li>
     * </ol>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 현재 상태가 APPROVED가 아닌 경우
     */
    @Transactional
    public void skipCouncil(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        // APPROVED 상태에서만 생략 가능
        if (!"04".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "생략 처리는 결재완료(004) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }

        // 협의회 상태 전이: APPROVED → SKIPPED
        council.changeStatus("SKIPPED");

        // 사업 상태 전이: '타당성검토 정실협 진행중'(32) → '타당성검토 정실협 완료'(39)
        councilRepository.updateProjectStatus(council.getAbusMngNo(), council.getSno(), PRJ_STS_COUNCIL_DONE);
    }

    /**
     * 개최준비 시작 (APPROVED → PREPARING)
     *
     * <p>
     * IT관리자가 타당성검토표 검토 후 '개최준비 진행'을 선택한 경우 호출합니다.
     * 기존에는 평가위원 저장(saveCommittee)의 부수효과로 04→05 전이가 일어났으나,
     * 의사결정 지점(Step1 상세의 '개최준비 진행' 버튼)으로 전이를 명시화했습니다. (PRD_c_20260620 #2)
     * </p>
     *
     * @param asctId 협의회ID
     * @throws IllegalStateException 현재 상태가 APPROVED(04)가 아닌 경우
     */
    @Transactional
    public void startPreparation(String asctId) {
        Basctm council = findActiveCouncil(asctId);

        // 결재완료(04) 상태에서만 개최준비로 전이 가능
        if (!"04".equals(council.getItPtlAsctPrgStsTc())) {
            throw new IllegalStateException(
                    "개최준비 전이는 결재완료(004) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
        }

        // 협의회 상태 전이: APPROVED(04) → PREPARING(05)
        council.changeStatus("05");
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 협의회 화면 표시용 당해예산(파생) 계산.
     *
     * <p>
     * 프로젝트 활성 품목(DEL_YN='N')의 ∑AMT − ∑MPL_AMT 기반으로 산출한다.
     * TOT_RQM_AMT 컬럼이 제거됨에 따라 협의회 목록/상세에서 사용하는 당해예산을
     * 품목 단위 파생값으로 대체한다.
     * </p>
     *
     * <p>
     * <strong>N+1 주의</strong>: 현재 협의회 목록 각 행마다 호출되므로 사업 수가 많을 때
     * 다수의 품목 조회가 발생한다. 추후 배치 조회 방식으로 개선 대상(TASK.md 등록).
     * </p>
     *
     * @param abusMngNo 프로젝트관리번호 (null 또는 빈 값이면 null 반환)
     * @return 당해예산(파생값), 프로젝트 품목이 없으면 0
     */
    private java.math.BigDecimal deriveCurrentYearBudget(String abusMngNo) {
        if (abusMngNo == null || abusMngNo.isBlank())
            return null;
        var items = projectItemRepository.findByAbusMngNoAndDelYn(abusMngNo, "N");
        var tmp = ProjectDto.Response.builder().build();
        projectBudgetSummaryService.applyBudgetSummary(tmp, items);
        return tmp.getTotRqmAmt();
    }

    /**
     * 활성 협의회 조회 (삭제되지 않은 항목)
     *
     * @param asctId 협의회ID
     * @return Basctm 엔티티
     * @throws IllegalArgumentException 존재하지 않는 경우
     */
    public Basctm findActiveCouncil(String asctId) {
        return councilRepository.findByItPtlAsctIdAndDelYn(asctId, "N")
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 협의회입니다. asctId=" + asctId));
    }

    /**
     * 협의회ID 채번
     *
     * <p>
     * 형식: ASCT-{연도}-{4자리순번} (예: ASCT-2026-0001)
     * </p>
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
     * <p>
     * ITPZZ001이지만 특정 협의회에 배정된 경우 평가위원으로 동작합니다.
     * 목록 조회 시 권한 분기 판단에 사용합니다.
     * </p>
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
        List<Basctm> memberCouncils = councilRepository.findByCommitteeMember(userDetails.getEno(), "N");
        return !memberCouncils.isEmpty();
    }

    /**
     * Basctm 엔티티 → ListResponse 변환 (평가위원용, PRD §16)
     *
     * <p>
     * 사업명은 BPOVWM 우선, 없으면 BPROJM에서 가져옵니다.
     * 평가위원 사업카드도 일반사용자/관리자와 동일하게 사업 상세 필드를 채워야 하므로
     * BPROJM에서 prjYy/prjTp/svnDpm/prjBg/sttDt/endDt/itDpm/prjDes를 함께 매핑합니다.
     * </p>
     */
    private CouncilDto.ListResponse toListResponseFromEntity(Basctm council) {
        // BPROJM 조회 — 사업 상세 정보 원천
        var projectOpt = projectRepository.findById(new BprojmId(council.getAbusMngNo(), council.getSno()));

        // 사업명: BPOVWM(타당성검토표) 우선, 없으면 BPROJM
        String prjNm = projectOverviewRepository
                .findByItPtlAsctIdAndDelYn(council.getItPtlAsctId(), "N")
                .map(value -> value.getAbusNm())
                .orElseGet(() -> projectOpt.map(p -> p.getAbusNm()).orElse(null));

        // 사업 상세 (BPROJM 기반)
        String prjYy = projectOpt.map(p -> p.getBseYy()).orElse(null);
        String prjTp = projectOpt.map(p -> p.getBzTpC()).orElse(null);
        String svnDpm = projectOpt.map(p -> p.getSvnDpmC()).orElse(null);
        // 당해예산: 품목 활성 항목(DEL_YN='N')의 ∑AMT − ∑MPL_AMT 기반 파생값
        java.math.BigDecimal prjBg = deriveCurrentYearBudget(council.getAbusMngNo());
        java.time.LocalDate sttDt = projectOpt.map(p -> p.getSttDtm()).orElse(null);
        java.time.LocalDate endDt = projectOpt.map(p -> p.getEndDtm()).orElse(null);
        String itDpm = projectOpt.map(p -> p.getDvmDpmC()).orElse(null);
        String prjDes = projectOpt.map(p -> p.getAbusCone()).orElse(null);

        return new CouncilDto.ListResponse(
                council.getItPtlAsctId(),
                council.getAbusMngNo(),
                council.getSno(),
                prjNm,
                council.getItPtlAsctPrgStsTc(),
                council.getItPtlAsctDbrTc(),
                council.getCnrcDt(),
                council.getCnrcSttTm(),
                true,
                prjYy, prjTp, svnDpm, prjBg, sttDt, endDt, itDpm, prjDes,
                council.getCsfHeldYn());
    }

    /**
     * Native Query Object[] 행 → ListResponse 변환 (관리자/일반사용자용)
     *
     * <p>
     * 컬럼 순서: prjMngNo(0), prjSno(1), prjNm(2), asctId(3), asctStsC(4),
     * dbrTc(5), cnrcDt(6), cnrcTm(7), applied(8), prjYy(9), prjTp(10), svnDpm(11),
     * prjBg(12), sttDt(13), endDt(14), itDpm(15), prjDes(16) — PRD §25 cnrcTm 추가
     * </p>
     */
    private CouncilDto.ListResponse toListResponseFromRow(Object[] row) {
        String asctId = (String) row[3];
        String asctStsC = (String) row[4];
        String dbrTc = (String) row[5];
        // Oracle JDBC 버전에 따라 DATE → java.sql.Date 또는 java.time.LocalDateTime /
        // String(yyyyMMdd)으로 반환
        java.time.LocalDate cnrcDt = toLocalDate(row[6]);
        String cnrcTm = (String) row[7];
        java.time.LocalDate sttDt = toLocalDate(row[13]);
        java.time.LocalDate endDt = toLocalDate(row[14]);
        // Oracle NUMBER(1) → BigDecimal 등으로 반환되므로 intValue() 처리
        boolean applied = row[8] != null && ((Number) row[8]).intValue() == 1;
        // 당해예산: TOT_RQM_AMT 컬럼 제거로 row[12]는 NULL. 품목 파생값으로 산출한다.
        String rowAbusMngNo = (String) row[0];
        java.math.BigDecimal prjBg = deriveCurrentYearBudget(rowAbusMngNo);

        return new CouncilDto.ListResponse(
                asctId,
                (String) row[0],
                row[1] != null ? ((Number) row[1]).intValue() : null,
                (String) row[2],
                asctStsC,
                dbrTc,
                cnrcDt,
                cnrcTm,
                applied,
                (String) row[9], // prjYy
                (String) row[10], // prjTp
                (String) row[11], // svnDpm
                prjBg, // prjBg
                sttDt, // sttDt
                endDt, // endDt
                (String) row[15], // itDpm
                (String) row[16], // prjDes
                (String) row[17] // csfHeldYn (PRD_c_20260620 #1)
        );
    }

    /**
     * Oracle Native Query DATE 컬럼 → LocalDate 변환
     *
     * <p>
     * Oracle JDBC 드라이버 버전에 따라 DATE 컬럼이 java.sql.Date,
     * java.time.LocalDateTime, java.time.LocalDate 등 다양한 타입으로 반환될 수 있어
     * 방어적으로 처리합니다.
     * </p>
     *
     * @param val Native Query 결과의 날짜 컬럼 값
     * @return LocalDate (null이면 null 반환)
     */
    private java.time.LocalDate toLocalDate(Object val) {
        if (val == null)
            return null;
        if (val instanceof java.time.LocalDate)
            return (java.time.LocalDate) val;
        if (val instanceof java.time.LocalDateTime)
            return ((java.time.LocalDateTime) val).toLocalDate();
        if (val instanceof java.sql.Date)
            return ((java.sql.Date) val).toLocalDate();
        if (val instanceof java.sql.Timestamp)
            return ((java.sql.Timestamp) val).toLocalDateTime().toLocalDate();
        // PRD §25 — DT 도메인 VARCHAR2(8) yyyyMMdd / yyyy-MM-dd String 케이스 처리
        // (BASCTM.CNRC_DT 등이 String으로 반환되면 기존엔 null로 떨어져 카드에 회의일자 표시 안 됨)
        if (val instanceof String s) {
            String digits = s.replaceAll("[^0-9]", "");
            if (digits.length() >= 8) {
                try {
                    return java.time.LocalDate.of(
                            Integer.parseInt(digits.substring(0, 4)),
                            Integer.parseInt(digits.substring(4, 6)),
                            Integer.parseInt(digits.substring(6, 8)));
                } catch (NumberFormatException | java.time.DateTimeException e) {
                    // 회의일자 문자열이 yyyyMMdd로 변환되지 않으면 null 반환(카드에 미표시).
                    // 데이터 정합성 점검을 위해 원본 값과 원인 예외를 warn으로 남긴다.
                    log.warn("[협의회] 회의일자 파싱 실패 — 원본값={}", s, e);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Basctm → DetailResponse 변환
     * BPROJM에서 사업명(prjNm)과 전결권자(edrt)를 함께 조회합니다.
     */
    private CouncilDto.DetailResponse toDetailResponse(Basctm council) {
        // BPROJM에서 타당성검토표 기본값 필드 조회
        String prjNm = null;
        String edrt = null;
        java.time.LocalDate sttDt = null;
        java.time.LocalDate endDt = null;
        String ncs = null;
        java.math.BigDecimal prjBg = null;
        String prjDes = null;
        String xptEff = null;
        var projectOpt = projectRepository.findById(new BprojmId(council.getAbusMngNo(), council.getSno()));
        if (projectOpt.isPresent()) {
            var p = projectOpt.get();
            prjNm = p.getAbusNm();
            edrt = p.getEdrtTc();
            sttDt = p.getSttDtm();
            endDt = p.getEndDtm();
            ncs = p.getAbusNcsCone();
            prjBg = deriveCurrentYearBudget(p.getAbusMngNo()); // 당해예산: 품목 ∑AMT − ∑MPL_AMT 파생값
            prjDes = p.getAbusCone();
            xptEff = p.getDgogPpoCone();
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
                council.getCsfHeldYn());
    }
}
