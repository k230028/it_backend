package com.kdb.it.domain.budget.cost.service;

import static com.kdb.it.domain.budget.cost.service.CostNameSnapshotResolver.snapshotOrResolved;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.service.ApprovalStamper;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 전산업무비 변경 로직과 기존 공개 조회 진입점을 제공하는 호환 파사드입니다.
 *
 * <p>원장(BCOSTM)의 검증·권한·스냅샷·결재 스탬프를 맡고, 금융정보단말기(BTERMM)의 생성·동기화는 {@link CostTerminalSynchronizer},
 * 담당자·조직 스냅샷 해석은 {@link CostNameSnapshotResolver}, 저장 동시성 방어는 {@link CostConcurrencyGuard}에 위임합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostService {

    private static final String COST_TABLE = "BCOSTM";

    private final CostRepository costRepository;
    private final CostWriteTargetLoader writeTargetLoader;
    private final BtermmRepository btermmRepository;
    private final OrgNameResolver orgNameResolver;
    private final com.kdb.it.common.code.service.CodeService codeService;
    private final XcrLookupService xcrLookupService;
    private final CostQueryService queryService;
    private final ApprovalWriteGuard approvalWriteGuard;

    private final ApprovalStamper approvalStamper;

    /** 저장 경로의 스탬프 대조와 잠금 대기 초과 변환을 담당합니다. */
    private final CostConcurrencyGuard concurrencyGuard;

    /** 금융정보단말기 행의 생성·동기화를 담당합니다. */
    private final CostTerminalSynchronizer terminalSynchronizer;

    /** 담당자 이름과 소속 조직 스냅샷을 해석합니다. */
    private final CostNameSnapshotResolver nameResolver;

    private static final int SERVICE_NAME_LOOKBACK_YEARS = 3;

    /**
     * 관리번호의 대표 전산업무비를 조회합니다.
     *
     * @param itMngcNo 전산업무비 관리번호
     * @return 연관 정보가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     */
    public CostDto.Response getCost(String itMngcNo) {
        return queryService.getCost(itMngcNo);
    }

    /**
     * 관리번호의 대표 전산업무비를 소속 부서 범위 검증과 함께 조회합니다.
     *
     * <p>시스템관리자가 아니면 담당부서(costSvnDpmC)가 인증 사용자의 부점코드와 같아야 합니다. 예산 작성 화면이 다른 부서 품목을 열지 못하게 하는 서버 측
     * 최종 검증입니다.
     *
     * @param itMngcNo 전산업무비 관리번호
     * @param user 인증 사용자. null이면 거부
     * @return 연관 정보가 조립된 상세 응답
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     * @throws AccessDeniedException 다른 부서 품목이거나 인증 정보가 없는 경우
     */
    public CostDto.Response getCost(String itMngcNo, CustomUserDetails user) {
        CostDto.Response response = queryService.getCost(itMngcNo);
        verifyDeptReadable(response.getCostSvnDpmC(), user);
        return response;
    }

    /** 정확한 예산일련번호의 전산업무비를 부서 권한과 함께 조회합니다. */
    public CostDto.Response getCost(String itMngcNo, Integer bgSno, CustomUserDetails user) {
        CostDto.Response response = queryService.getCost(itMngcNo, bgSno);
        verifyDeptReadable(response.getCostSvnDpmC(), user);
        return response;
    }

    /**
     * 삭제되지 않은 전산업무비 전체 목록을 조회합니다.
     *
     * @return 연관 정보가 조립된 목록
     */
    public List<CostDto.Response> getCostList() {
        return queryService.getCostList();
    }

    /**
     * 검색 조건에 맞는 전산업무비 목록을 조회합니다.
     *
     * @param condition 검색 조건
     * @return 조건에 맞는 목록
     */
    public List<CostDto.Response> searchCostList(CostDto.SearchCondition condition) {
        return queryService.searchCostList(condition);
    }

    /**
     * 검색 조건에 맞는 전산업무비 목록을 소속 부서 범위를 적용해 조회합니다.
     *
     * <p>일반 사용자는 {@code condition.myDeptOnly} 값과 무관하게 담당부서 조건을 인증 사용자의 부점코드로 덮어씁니다. 시스템관리자는 {@code
     * myDeptOnly=true}이면 본인 부서, 그 외에는 전체를 조회합니다. 클라이언트가 보낸 부서코드는 권한 근거로 쓰지 않으므로 일반 사용자가 다른 부서로 범위를
     * 넓힐 수 없습니다.
     *
     * @param condition 검색 조건
     * @param user 인증 사용자. null이면 조회를 거부
     * @return 조건에 맞는 목록. 부서 한정인데 사용자 부점코드가 없으면 빈 목록
     */
    public List<CostDto.Response> searchCostList(
            CostDto.SearchCondition condition, CustomUserDetails user) {
        return searchCostList(condition, user, com.kdb.it.common.util.ListPageParams.unpaged());
    }

    /**
     * 부서 범위를 적용해 전산업무비 목록을 지정한 페이지 구간만 조회합니다.
     *
     * @param condition 검색 조건
     * @param user 인증 사용자. null이면 조회를 거부
     * @param paging 페이지 파라미터 (미지정이면 상한까지)
     * @return 해당 구간의 목록. 부서 한정인데 사용자 부점코드가 없으면 빈 목록
     */
    public List<CostDto.Response> searchCostList(
            CostDto.SearchCondition condition,
            CustomUserDetails user,
            com.kdb.it.common.util.ListPageParams paging) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        /* 관리자는 화면의 [부서|전체] 선택을 사용할 수 있다. */
        if (user.isAdmin() && !Boolean.TRUE.equals(condition.getMyDeptOnly())) {
            return queryService.searchCostList(condition, paging);
        }
        // SSO 미동기화 등으로 부점코드가 없는 계정에 전체 조회를 열지 않는다(데이터 접근 범위 가이드).
        if (!StringUtils.hasText(user.getBbrC())) {
            return List.of();
        }
        condition.setCostSvnDpmC(user.getBbrC());
        return queryService.searchCostList(condition, paging);
    }

    /**
     * 목록과 같은 부서 범위를 적용해 전산업무비 전체 건수를 조회합니다.
     *
     * <p>범위 판정은 {@link #searchCostList(CostDto.SearchCondition, CustomUserDetails)}와 같아야 합니다. 다르면
     * 페이지 응답의 {@code X-Total-Count}가 실제로 조회 가능한 건수와 어긋납니다.
     *
     * @param condition 검색 조건
     * @param user 인증 사용자. null이면 조회를 거부
     * @return 조건에 맞는 전체 건수. 부서 한정인데 사용자 부점코드가 없으면 0
     */
    public long countCostList(CostDto.SearchCondition condition, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (user.isAdmin() && !Boolean.TRUE.equals(condition.getMyDeptOnly())) {
            return queryService.countCostList(condition);
        }
        if (!StringUtils.hasText(user.getBbrC())) {
            return 0L;
        }
        condition.setCostSvnDpmC(user.getBbrC());
        return queryService.countCostList(condition);
    }

    /**
     * 전산업무비 한 건을 인증 사용자가 조회할 수 있는지 검증합니다.
     *
     * @param costSvnDpmC 대상 품목의 담당부서코드
     * @param user 인증 사용자
     * @throws AccessDeniedException 인증 정보가 없거나 다른 부서 품목인 경우
     */
    private void verifyDeptReadable(String costSvnDpmC, CustomUserDetails user) {
        BudgetDetailAccessVerifier.verifyReadable(costSvnDpmC, user);
    }

    /**
     * 단말기 서비스명(SPF_TMN_NM) 입력 후보 목록을 조회합니다.
     *
     * <p>최근 {@value #SERVICE_NAME_LOOKBACK_YEARS}개 예산연도에 등록된 단말기의 서비스명을 중복 제거하여 사용 빈도 내림차순으로 반환합니다.
     * 단말기종류가 주어지면 같은 종류에서만 집계하고, 비어 있으면 종류 구분 없이 집계합니다.
     *
     * @param tmnClsfC 단말기종류 코드 (공통코드 IT_PTL_TMN_SVC_TC), 비어 있으면 전체
     * @return 빈도 내림차순 서비스명 목록 (해당 이력이 없으면 빈 목록)
     */
    public List<String> getTerminalServiceNames(String tmnClsfC) {
        String fromBseYy =
                String.valueOf(LocalDate.now().getYear() - (SERVICE_NAME_LOOKBACK_YEARS - 1));
        return StringUtils.hasText(tmnClsfC)
                ? btermmRepository.findServiceNamesByTmnClsfC(tmnClsfC, fromBseYy)
                : btermmRepository.findServiceNames(fromBseYy);
    }

    /**
     * 여러 전산업무비 관리번호를 부분 성공 방식으로 조회합니다.
     *
     * @param request 관리번호 목록과 편성예산 기준연도
     * @return 성공 항목과 누락 관리번호
     */
    public CostDto.BulkResponse getCostsByIds(CostDto.BulkGetRequest request) {
        return queryService.getCostsByIds(request);
    }

    /** 인증 사용자의 부서 범위를 적용해 전산업무비를 일괄 조회합니다. */
    public CostDto.BulkResponse getCostsByIds(
            CostDto.BulkGetRequest request, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        CostDto.BulkResponse response = queryService.getCostsByIds(request);
        List<CostDto.Response> readable = new ArrayList<>();
        List<String> failedIds = new ArrayList<>(response.failedIds());
        for (CostDto.Response item : response.items()) {
            if (BudgetDetailAccessVerifier.isReadable(item.getCostSvnDpmC(), user)) {
                readable.add(item);
            } else {
                failedIds.add(item.getCostBgNo());
            }
        }
        return new CostDto.BulkResponse(readable, failedIds);
    }

    /**
     * 신규 전산업무비와 요청 단말기를 생성합니다.
     *
     * <p>예산 신청 기간을 검증합니다. 기간 밖 호출은 실패합니다.
     *
     * @param request 생성 요청
     * @return 생성된 전산업무비 관리번호
     * @throws com.kdb.it.exception.CustomGeneralException 예산 신청 기간이 아닌 경우
     */
    @Transactional
    public String createCost(CostDto.CreateRequest request) {
        return createCost(request, false);
    }

    /** 인증 사용자의 부서와 요청 담당부서가 같은지 검증한 뒤 전산업무비를 생성합니다. */
    @Transactional
    public String createCost(CostDto.CreateRequest request, CustomUserDetails actor) {
        OwnershipVerifier.verifySameDepartmentOrAdmin(request.getCostSvnDpmC(), actor);
        return createCost(request, false);
    }

    /**
     * 신규 전산업무비와 요청 단말기를 생성합니다.
     *
     * <p>{@code skipBudgetPeriodValidation}은 관리자 전용 수기 엑셀 이관 경로만 사용합니다. 이관 작업은 편성 시즌 밖에서도 실행되어야 하므로
     * 기간 검증을 건너뛸 수 있어야 하지만, 일반 사용자 화면 경로는 반드시 검증을 거쳐야 하므로 기본값 false인 1-인자 시그니처를 남겨 둡니다.
     *
     * @param request 생성 요청
     * @param skipBudgetPeriodValidation true면 예산 신청 기간 검증을 생략 (이관 전용)
     * @return 생성된 전산업무비 관리번호
     * @throws com.kdb.it.exception.CustomGeneralException 검증을 수행했고 예산 신청 기간이 아닌 경우
     */
    @Transactional
    public String createCost(CostDto.CreateRequest request, boolean skipBudgetPeriodValidation) {
        return createCost(request, skipBudgetPeriodValidation, false, LocalDate.now().getYear());
    }

    /**
     * 금융정보단말기 일괄업로드용 생성 진입점입니다.
     *
     * <p>일괄업로드는 편성연도별로 관리번호를 만들어야 하고, 엑셀에 이미 원화 환산금액과 외화금액이 함께 있으므로 일반 화면의 현재 환율 재계산을 적용하지 않습니다. 일반
     * 사용자 생성 경로에는 이 정책을 노출하지 않습니다.
     *
     * @param request 생성 요청
     * @param budgetYear 관리번호에 사용할 예산연도
     * @return 생성된 전산업무비 관리번호
     */
    @Transactional
    public String createCostForMigration(CostDto.CreateRequest request, int budgetYear) {
        return createCost(request, true, true, budgetYear);
    }

    private String createCost(
            CostDto.CreateRequest request,
            boolean skipBudgetPeriodValidation,
            boolean preserveSubmittedAmounts,
            int idYear) {
        if (!skipBudgetPeriodValidation) {
            codeService.validateBudgetPeriod();
        }
        String costBgNo = request.getCostBgNo();
        if (costBgNo == null || costBgNo.isEmpty()) {
            Long sequence = costRepository.getNextSequenceValue();
            costBgNo = String.format("COST-%s-%04d", idYear, sequence);
            request.setCostBgNo(costBgNo);
        } else {
            // 이미 존재하는 관리번호의 다음 순번만 기존 행 잠금으로 직렬화한다.
            // 최초 생성처럼 행이 없으면 PK 유일 제약이 충돌을 검출한다.
            costRepository.findAllVersionsForUpdate(costBgNo);
        }
        Integer nextSno = costRepository.getNextSnoValue(costBgNo);
        if (nextSno == null) {
            nextSno = 1;
        }
        if (!preserveSubmittedAmounts) {
            reconcileParentAmount(request);
        }

        if (preserveSubmittedAmounts) {
            /* 금융정보단말기 신규 생성은 업로드 담당자 행번을 담당자ID로 저장하지 않는다. */
            request.setCgprId(null);
        }
        Bcostm cost = request.toEntity(nextSno);
        assignParentSnapshots(cost, request.getCostSvnDpmNm(), request.getSvnTemNm());
        cost.assignCgprName(request.getCgprNm());
        costRepository.save(cost);

        terminalSynchronizer.createAll(
                cost, request.getTerminals(), preserveSubmittedAmounts, idYear);
        if (!preserveSubmittedAmounts) {
            stampDraftedIfCompleted(request.getComplete(), cost);
        }
        return cost.getCostBgNo();
    }

    /** 서버 환율로 원화·외화 금액을 다시 맞춥니다. 사용자 화면 경로에서만 호출합니다. */
    private void reconcileParentAmount(CostDto.CreateRequest request) {
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));
        BigDecimal[] reconciled =
                BudgetAmountCalculator.reconcileAmount(
                        request.getFcAmt(),
                        request.getCostTotXpAmt(),
                        request.getCurC(),
                        request.getXcr());
        request.setCostTotXpAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);
    }

    /**
     * 원장의 담당자·조직 스냅샷 컬럼을 채웁니다.
     *
     * @param cost 대상 원장 (담당자 사번과 담당부서 코드가 이미 반영된 상태)
     * @param requestedDpmNm 요청이 보낸 담당부서명 스냅샷 (공백이면 조직 조회 결과 사용)
     * @param requestedTemNm 요청이 보낸 담당팀명 스냅샷 (공백이면 담당자 소속 팀명 사용)
     */
    private void assignParentSnapshots(Bcostm cost, String requestedDpmNm, String requestedTemNm) {
        CostNameSnapshotResolver.AuthorOrg orgSnapshot =
                nameResolver.resolveAuthorOrgNames(cost.getCgprId());
        cost.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        cost.assignCgprName(nameResolver.resolveCgprName(cost.getCgprId()));
        cost.assignSvnOrgNames(
                snapshotOrResolved(
                        requestedDpmNm, orgNameResolver.resolveName(cost.getCostSvnDpmC())),
                snapshotOrResolved(requestedTemNm, orgSnapshot.svnTemNm()));
    }

    private void stampDraftedIfCompleted(Boolean complete, Bcostm cost) {
        if (Boolean.TRUE.equals(complete)) {
            approvalStamper.stampDrafted(
                    COST_TABLE,
                    cost.getCostBgNo(),
                    cost.getBgSno(),
                    cost.getCttNm(),
                    OwnershipVerifier.currentEno(),
                    cost.getCostSvnDpmC(),
                    cost.getBseYy());
        }
    }

    /** 편성요청서 반입에서 사번을 추정하지 않고 양식의 작성자 이름만 스냅샷 컬럼에 기록합니다. */
    @Transactional
    public void assignImportedPersonName(String costBgNo, String cgprNm) {
        Bcostm cost =
                costRepository
                        .findCurrentVersionForUpdate(costBgNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "전산업무비를 찾을 수 없습니다: " + costBgNo));
        approvalWriteGuard.verifyWritable(COST_TABLE, costBgNo, cost.getBgSno(), "수정");
        cost.assignCgprName(cgprNm);
    }

    /**
     * 대표 전산업무비와 연결 단말기를 요청 내용으로 갱신합니다.
     *
     * @param itMngcNo 전산업무비 관리번호
     * @param request 수정 요청
     * @return 수정된 관리번호
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     * @throws CostConflictException 동시성 스탬프가 없거나(400) 현재 상태와 다르거나(409) 잠금 대기를 넘긴 경우(409)
     */
    @Transactional
    public String updateCost(String itMngcNo, CostDto.UpdateRequest request) {
        return concurrencyGuard.runUserUpdate(() -> updateCost(itMngcNo, null, request, false));
    }

    /** 정확한 예산일련번호의 미상신 개정본을 수정합니다. */
    @Transactional
    public String updateCost(String itMngcNo, Integer bgSno, CostDto.UpdateRequest request) {
        return concurrencyGuard.runUserUpdate(() -> updateCost(itMngcNo, bgSno, request, false));
    }

    /**
     * 관리자 금융정보단말기 일괄업로드용 수정 진입점입니다.
     *
     * <p>사람이 보는 화면이 없으므로 동시성 스탬프를 요구하지 않고, 잠금 대기 초과도 409로 바꾸지 않고 원래 예외를 그대로 전파합니다. 행 잠금은 사용자 경로와
     * 동일하게 유지합니다.
     */
    @Transactional
    public String updateCostForMigration(String itMngcNo, CostDto.UpdateRequest request) {
        return updateCost(itMngcNo, null, request, true);
    }

    /** 금융정보단말기 일괄업로드 원장에 원장 부서 소속의 수기등록 신청서를 연결합니다. */
    @Transactional
    public String stampManualMigration(String itMngcNo, String actorEno) {
        Bcostm target =
                costRepository
                        .findCurrentVersionForUpdate(itMngcNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "전산업무비를 찾을 수 없습니다: " + itMngcNo));
        return approvalStamper.stamp(
                COST_TABLE,
                target.getCostBgNo(),
                target.getBgSno(),
                target.getCttNm(),
                actorEno,
                target.getCostSvnDpmC(),
                target.getBseYy(),
                ApprovalStatus.MANUAL);
    }

    private String updateCost(
            String itMngcNo,
            Integer bgSno,
            CostDto.UpdateRequest request,
            boolean preserveSubmittedAmounts) {
        if (!preserveSubmittedAmounts) {
            codeService.validateBudgetPeriod();
        }
        Bcostm target = writeTargetLoader.loadForUpdate(itMngcNo, bgSno);
        if (!preserveSubmittedAmounts) {
            OwnershipVerifier.verifyModifiable(target.getFstEnrUsid(), target.getCostSvnDpmC());
        }
        // 이관도 잠근 정확한 개정본의 결재 상태를 확인한 뒤에만 원장과 단말기를 수정한다.
        approvalWriteGuard.verifyWritable(
                COST_TABLE, target.getCostBgNo(), target.getBgSno(), "수정");

        // 원장을 만지기 전에 검사해야 한다. 아래 블록부터 target과 request가 수정되므로 이 지점이 유일하게 안전하다.
        if (!preserveSubmittedAmounts) {
            concurrencyGuard.verifyStamp(request, target, nameResolver::resolveCgprName);
        }

        if (!preserveSubmittedAmounts) {
            request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));
            BigDecimal[] reconciled =
                    BudgetAmountCalculator.reconcileAmount(
                            request.getFcAmt(),
                            request.getCostTotXpAmt(),
                            request.getCurC(),
                            request.getXcr());
            request.setCostTotXpAmt(reconciled[0]);
            request.setFcAmt(reconciled[1]);
        }
        if (preserveSubmittedAmounts && !StringUtils.hasText(request.getSvnTemC())) {
            request.setSvnTemC(target.getSvnTemC());
        }
        if (preserveSubmittedAmounts) {
            request.setCttNm(target.getCttNm());
            request.setCttOppNm(target.getCttOppNm());
        }
        target.update(toUpdateCommand(request));
        assignParentSnapshots(target, request.getCostSvnDpmNm(), request.getSvnTemNm());
        target.assignCgprName(request.getCgprNm());

        terminalSynchronizer.sync(target, request, preserveSubmittedAmounts);
        if (!preserveSubmittedAmounts) {
            stampDraftedIfCompleted(request.getComplete(), target);
        }
        return target.getCostBgNo();
    }

    /** 지정한 전산업무비 개정본과 그 순번에 연결된 단말기만 논리 삭제합니다. */
    @Transactional
    public void deleteCost(String itMngcNo, Integer bgSno) {
        if (bgSno == null || bgSno < 1) {
            throw new IllegalArgumentException("삭제할 전산업무비 순번이 필요합니다.");
        }
        codeService.validateBudgetPeriod();
        Bcostm cost =
                costRepository
                        .findVersionForUpdate(itMngcNo, bgSno)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Cost not found with id: "
                                                        + itMngcNo
                                                        + ", sno: "
                                                        + bgSno));
        OwnershipVerifier.verifySameDepartmentOrAdmin(cost.getCostSvnDpmC());
        approvalWriteGuard.verifyDeletable(COST_TABLE, itMngcNo, bgSno);
        cost.delete();
        btermmRepository.findByTermBgNoAndTermBgSno(itMngcNo, bgSno).forEach(Btermm::delete);
    }

    private static Bcostm.UpdateCommand toUpdateCommand(CostDto.UpdateRequest request) {
        return Bcostm.UpdateCommand.builder()
                .ioeC(request.getIoeC())
                .cttNm(request.getCttNm())
                .cttOppNm(request.getCttOppNm())
                .costTotXpAmt(request.getCostTotXpAmt())
                .dfrCleC(request.getDfrCleC())
                .fstDfrDt(DateFormatUtil.toYmd8(request.getFstDfrDt()))
                .curC(request.getCurC())
                .xcr(request.getXcr())
                .xcrBseDt(DateFormatUtil.toYmd8(request.getXcrBseDt()))
                .sectSysUtzYn(request.getSectSysUtzYn())
                .indRsn(request.getIndRsn())
                .cgprId(request.getCgprId())
                .costSvnDpmC(request.getCostSvnDpmC())
                .svnTemC(request.getSvnTemC())
                .bgUntAbusC(request.getBgUntAbusC())
                .tmnYn(request.getTmnYn())
                .abusTc(request.getAbusTc())
                .bseYy(request.getBseYy())
                .cncdRfrNo(request.getCncdRfrNo())
                .fcAmt(request.getFcAmt())
                .build();
    }
}
