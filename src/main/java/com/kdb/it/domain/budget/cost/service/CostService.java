package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.common.util.UserNameResolver;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.BudgetAmountCalculator;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 전산업무비 변경 로직과 기존 공개 조회 진입점을 제공하는 호환 파사드입니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostService {

    private final CostRepository costRepository;
    private final BtermmRepository btermmRepository;
    private final UserRepository cuserIRepository;
    private final OrgNameResolver orgNameResolver;
    private final com.kdb.it.common.code.service.CodeService codeService;
    private final XcrLookupService xcrLookupService;
    private final CostQueryService queryService;

    /** 단말기 서비스명 후보를 집계할 최근 예산연도 범위(당해 연도 포함) */
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
     * <p>일반 사용자는 {@code condition.myDeptOnly} 값과 무관하게 담당부서 조건을 인증 사용자의 부점코드로 덮어씁니다. 시스템관리자는
     * {@code myDeptOnly=true}이면 본인 부서, 그 외에는 전체를 조회합니다. 클라이언트가 보낸 부서코드는 권한 근거로 쓰지 않으므로 일반 사용자가 다른 부서로 범위를
     * 넓힐 수 없습니다.
     *
     * @param condition 검색 조건
     * @param user 인증 사용자. null이면 조회를 거부
     * @return 조건에 맞는 목록. 부서 한정인데 사용자 부점코드가 없으면 빈 목록
     */
    public List<CostDto.Response> searchCostList(
            CostDto.SearchCondition condition, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        /* 관리자는 화면의 [부서|전체] 선택을 사용할 수 있다. */
        if (user.isAdmin() && !Boolean.TRUE.equals(condition.getMyDeptOnly())) {
            return queryService.searchCostList(condition);
        }
        // SSO 미동기화 등으로 부점코드가 없는 계정에 전체 조회를 열지 않는다(데이터 접근 범위 가이드).
        if (!StringUtils.hasText(user.getBbrC())) {
            return List.of();
        }
        condition.setCostSvnDpmC(user.getBbrC());
        return queryService.searchCostList(condition);
    }

    /**
     * 전산업무비 한 건을 인증 사용자가 조회할 수 있는지 검증합니다.
     *
     * @param costSvnDpmC 대상 품목의 담당부서코드
     * @param user 인증 사용자
     * @throws AccessDeniedException 인증 정보가 없거나 다른 부서 품목인 경우
     */
    private void verifyDeptReadable(String costSvnDpmC, CustomUserDetails user) {
        if (user == null) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (user.isAdmin()) {
            return;
        }
        if (StringUtils.hasText(user.getBbrC()) && user.getBbrC().equals(costSvnDpmC)) {
            return;
        }
        throw new AccessDeniedException("소속 부서의 전산업무비만 조회할 수 있습니다.");
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
        }
        Integer nextSno = costRepository.getNextSnoValue(costBgNo);
        if (nextSno == null) {
            nextSno = 1;
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

        if (preserveSubmittedAmounts) {
            /* 금융정보단말기 신규 생성은 업로드 담당자 행번을 담당자ID로 저장하지 않는다. */
            request.setCgprId(null);
        }
        Bcostm cost = request.toEntity(nextSno);
        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(cost.getCgprId());
        cost.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        cost.assignSvnOrgNames(
                snapshotOrResolved(
                        request.getCostSvnDpmNm(),
                        orgNameResolver.resolveName(cost.getCostSvnDpmC())),
                snapshotOrResolved(request.getSvnTemNm(), orgSnapshot.svnTemNm()));
        cost.assignCgprName(resolveCgprName(cost.getCgprId()));
        cost.assignCgprName(request.getCgprNm());
        costRepository.save(cost);

        applyTerminalOrgCodes(request.getTerminals());
        if (request.getTerminals() != null) {
            for (CostDto.TerminalDto terminal : request.getTerminals()) {
                if (preserveSubmittedAmounts
                        && (terminal.getCgprId() == null || terminal.getCgprId().isBlank())) {
                    terminal.setCgprId(cost.getCgprId());
                }
                if (preserveSubmittedAmounts) {
                    terminal.setCgprId(null);
                }
                if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                    terminal.setTmnMngNo(
                            preserveSubmittedAmounts
                                    ? generateTmnMngNo(idYear)
                                    : generateTmnMngNo());
                }
                if (terminal.getSno() == null) {
                    terminal.setSno(1);
                }
                if (!preserveSubmittedAmounts) {
                    reconcileTerminalAmount(terminal);
                }
                Btermm entity = terminal.toEntity();
                entity.setBcostmInfo(cost.getCostBgNo(), cost.getBgSno());
                entity.assignCgprName(resolveCgprName(entity.getCgprId()));
                entity.assignCgprName(terminal.getCgprNm());
                assignTerminalOrgNames(entity, terminal);
                btermmRepository.save(entity);
            }
        }
        return cost.getCostBgNo();
    }

    /** 편성요청서 반입에서 사번을 추정하지 않고 양식의 작성자 이름만 스냅샷 컬럼에 기록합니다. */
    @Transactional
    public void assignImportedPersonName(String costBgNo, String cgprNm) {
        Bcostm cost =
                costRepository
                        .findByCostBgNoAndLstYnAndDelYn(costBgNo, "Y", "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "전산업무비를 찾을 수 없습니다: " + costBgNo));
        cost.assignCgprName(cgprNm);
    }

    /**
     * 대표 전산업무비와 연결 단말기를 요청 내용으로 갱신합니다.
     *
     * @param itMngcNo 전산업무비 관리번호
     * @param request 수정 요청
     * @return 수정된 관리번호
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     */
    @Transactional
    public String updateCost(String itMngcNo, CostDto.UpdateRequest request) {
        return updateCost(itMngcNo, request, false);
    }

    /** 관리자 금융정보단말기 일괄업로드용 수정 진입점입니다. */
    @Transactional
    public String updateCostForMigration(String itMngcNo, CostDto.UpdateRequest request) {
        return updateCost(itMngcNo, request, true);
    }

    private String updateCost(
            String itMngcNo, CostDto.UpdateRequest request, boolean preserveSubmittedAmounts) {
        if (!preserveSubmittedAmounts) {
            codeService.validateBudgetPeriod();
        }
        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }
        Bcostm target = CostRepresentativeSelector.pick(costs);
        if (!preserveSubmittedAmounts) {
            OwnershipVerifier.verifyModifiable(target.getFstEnrUsid(), target.getCostSvnDpmC());
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

        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(target.getCgprId());
        target.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        target.assignCgprName(resolveCgprName(target.getCgprId()));
        target.assignCgprName(request.getCgprNm());
        target.assignSvnOrgNames(
                snapshotOrResolved(
                        request.getCostSvnDpmNm(),
                        orgNameResolver.resolveName(target.getCostSvnDpmC())),
                snapshotOrResolved(request.getSvnTemNm(), orgSnapshot.svnTemNm()));

        List<Btermm> existingTerminals =
                btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(
                        target.getCostBgNo(), target.getBgSno(), "N");
        Map<String, Btermm> existingByPk =
                existingTerminals.stream()
                        .collect(
                                Collectors.toMap(
                                        terminal ->
                                                terminalPk(
                                                        terminal.getTmnMngNo(), terminal.getSno()),
                                        terminal -> terminal,
                                        (first, second) -> first));
        List<CostDto.TerminalDto> requestedTerminals =
                request.getTerminals() != null ? request.getTerminals() : List.of();
        applyTerminalOrgCodes(requestedTerminals);
        Set<String> keptPks = new java.util.HashSet<>();
        for (CostDto.TerminalDto terminal : requestedTerminals) {
            if (!preserveSubmittedAmounts) {
                reconcileTerminalAmount(terminal);
            }
            Btermm existing =
                    terminal.getTmnMngNo() != null && terminal.getSno() != null
                            ? existingByPk.get(
                                    terminalPk(terminal.getTmnMngNo(), terminal.getSno()))
                            : null;
            if (existing == null && preserveSubmittedAmounts) {
                existing =
                        existingTerminals.stream()
                                .filter(
                                        candidate ->
                                                !keptPks.contains(
                                                        terminalPk(
                                                                candidate.getTmnMngNo(),
                                                                candidate.getSno())))
                                .filter(candidate -> matchesMigrationTerminal(candidate, terminal))
                                .findFirst()
                                .orElse(null);
            }
            if (existing != null) {
                updateTerminal(existing, terminal, preserveSubmittedAmounts);
                existing.assignCgprName(resolveCgprName(existing.getCgprId()));
                existing.assignCgprName(terminal.getCgprNm());
                keptPks.add(terminalPk(existing.getTmnMngNo(), existing.getSno()));
            } else {
                if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                    terminal.setTmnMngNo(
                            preserveSubmittedAmounts
                                    ? generateTmnMngNo(Integer.parseInt(request.getBseYy()))
                                    : generateTmnMngNo());
                }
                if (terminal.getSno() == null) {
                    terminal.setSno(1);
                }
                Btermm entity = terminal.toEntity();
                entity.setBcostmInfo(target.getCostBgNo(), target.getBgSno());
                entity.assignCgprName(resolveCgprName(entity.getCgprId()));
                entity.assignCgprName(terminal.getCgprNm());
                assignTerminalOrgNames(entity, terminal);
                btermmRepository.save(entity);
                keptPks.add(terminalPk(terminal.getTmnMngNo(), terminal.getSno()));
            }
        }
        if (!preserveSubmittedAmounts) {
            existingTerminals.stream()
                    .filter(
                            terminal ->
                                    !keptPks.contains(
                                            terminalPk(terminal.getTmnMngNo(), terminal.getSno())))
                    .forEach(Btermm::delete);
        }
        return target.getCostBgNo();
    }

    private static boolean matchesMigrationTerminal(
            Btermm existing, CostDto.TerminalDto requested) {
        return java.util.Objects.equals(existing.getSpfTmnNm(), requested.getSpfTmnNm())
                && java.util.Objects.equals(existing.getTmnKdTc(), requested.getTmnKdTc())
                && java.util.Objects.equals(existing.getTmnClsfC(), requested.getTmnClsfC())
                && matchesNullableIdentity(
                        requested.getCgprId(),
                        existing.getCgprId(),
                        requested.getCgprNm(),
                        existing.getCgprNm())
                && matchesNullableIdentity(
                        requested.getTermSvnDpmC(),
                        existing.getTermSvnDpmC(),
                        requested.getTermSvnDpmNm(),
                        existing.getSvnDpmNm())
                && matchesNullableIdentity(
                        requested.getTermSvnTemC(),
                        existing.getTermSvnTemC(),
                        requested.getTermSvnTemNm(),
                        existing.getSvnTemNm());
    }

    private static boolean matchesNullableIdentity(
            String requestedCode, String existingCode, String requestedName, String existingName) {
        if (StringUtils.hasText(requestedCode)) {
            return java.util.Objects.equals(requestedCode, existingCode);
        }
        if (StringUtils.hasText(requestedName) && StringUtils.hasText(existingName)) {
            return requestedName.trim().equals(existingName.trim());
        }
        return true;
    }

    /**
     * 전산업무비 전체 이력과 연결된 활성 단말기를 논리 삭제합니다.
     *
     * @param itMngcNo 전산업무비 관리번호
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     */
    @Transactional
    public void deleteCost(String itMngcNo) {
        codeService.validateBudgetPeriod();
        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }
        Bcostm primary = CostRepresentativeSelector.pick(costs);
        OwnershipVerifier.verifyModifiable(primary.getFstEnrUsid(), primary.getCostSvnDpmC());
        List<String> costNos = costs.stream().map(Bcostm::getCostBgNo).distinct().toList();
        Map<String, List<Btermm>> terminalsByKey =
                btermmRepository.findByTermBgNoInAndDelYn(costNos, "N").stream()
                        .collect(
                                Collectors.groupingBy(
                                        terminal ->
                                                terminalPk(
                                                        terminal.getTermBgNo(),
                                                        terminal.getTermBgSno())));
        for (Bcostm cost : costs) {
            cost.delete();
            terminalsByKey
                    .getOrDefault(terminalPk(cost.getCostBgNo(), cost.getBgSno()), List.of())
                    .forEach(Btermm::delete);
        }
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

    private void reconcileTerminalAmount(CostDto.TerminalDto terminal) {
        terminal.setXcr(xcrLookupService.resolveXcr(terminal.getCurC(), LocalDate.now()));
        BigDecimal[] reconciled =
                BudgetAmountCalculator.reconcileAmount(
                        terminal.getFcAmt(),
                        terminal.getTermRqmBgAmt(),
                        terminal.getCurC(),
                        terminal.getXcr());
        terminal.setTermRqmBgAmt(reconciled[0]);
        terminal.setFcAmt(reconciled[1]);
    }

    private void updateTerminal(
            Btermm existing, CostDto.TerminalDto terminal, boolean preserveSubmittedAmounts) {
        if (preserveSubmittedAmounts) {
            if (!StringUtils.hasText(terminal.getCgprId())) {
                terminal.setCgprId(existing.getCgprId());
            }
            if (!StringUtils.hasText(terminal.getTermSvnDpmC())) {
                terminal.setTermSvnDpmC(existing.getTermSvnDpmC());
            }
            if (!StringUtils.hasText(terminal.getTermSvnTemC())) {
                terminal.setTermSvnTemC(existing.getTermSvnTemC());
            }
        }
        existing.update(toTerminalUpdateCommand(terminal));
        assignTerminalOrgNames(existing, terminal);
    }

    private void assignTerminalOrgNames(Btermm entity, CostDto.TerminalDto terminal) {
        entity.assignSvnOrgNames(
                snapshotOrResolved(
                        terminal.getTermSvnDpmNm(),
                        orgNameResolver.resolveName(entity.getTermSvnDpmC())),
                snapshotOrResolved(
                        terminal.getTermSvnTemNm(),
                        orgNameResolver.resolveName(entity.getTermSvnTemC())));
    }

    private static String snapshotOrResolved(String snapshot, String resolved) {
        return StringUtils.hasText(snapshot) ? snapshot.trim() : resolved;
    }

    private static Btermm.UpdateCommand toTerminalUpdateCommand(CostDto.TerminalDto terminal) {
        return Btermm.UpdateCommand.builder()
                .spfTmnNm(terminal.getSpfTmnNm())
                .tmnKdTc(terminal.getTmnKdTc())
                .nsfUsgCone(terminal.getNsfUsgCone())
                .tmnClsfC(terminal.getTmnClsfC())
                .termRqmBgAmt(terminal.getTermRqmBgAmt())
                .curC(terminal.getCurC())
                .xcr(terminal.getXcr())
                .xcrBseDt(DateFormatUtil.toYmd8(terminal.getXcrBseDt()))
                .dfrCleC(terminal.getDfrCleC())
                .indRsn(terminal.getIndRsn())
                .cgprId(terminal.getCgprId())
                .termSvnTemC(terminal.getTermSvnTemC())
                .svnTemNm(terminal.getTermSvnTemNm())
                .termSvnDpmC(terminal.getTermSvnDpmC())
                .svnDpmNm(terminal.getTermSvnDpmNm())
                .rmk(terminal.getRmk())
                .fcAmt(terminal.getFcAmt())
                .build();
    }

    /**
     * 담당자 표시명을 해석한다. 해석 실패 시 null이며, 그 경우 스냅샷은 기존 값을 유지한다(BE-63).
     *
     * @param cgprId 담당자 컬럼 저장값 — 사번 또는 이름
     * @return 표시명. 해석 실패 시 null
     */
    private String resolveCgprName(String cgprId) {
        if (cgprId == null || cgprId.isBlank()) {
            return null;
        }
        String lookedUp =
                cuserIRepository.findByEno(cgprId).map(user -> user.getUsrNm()).orElse(null);
        return UserNameResolver.resolve(cgprId, lookedUp);
    }

    private CostOrgSnapshot resolveAuthorOrgNames(String cgprId) {
        if (cgprId == null || cgprId.isBlank()) {
            return CostOrgSnapshot.EMPTY;
        }
        return cuserIRepository
                .findByEno(cgprId)
                .map(user -> new CostOrgSnapshot(user.getTemNm(), user.getPrlmHrkOgzCNm()))
                .orElse(CostOrgSnapshot.EMPTY);
    }

    private void applyTerminalOrgCodes(List<CostDto.TerminalDto> terminals) {
        if (terminals == null || terminals.isEmpty()) {
            return;
        }
        Set<String> userIds =
                terminals.stream()
                        .map(CostDto.TerminalDto::getCgprId)
                        .filter(userId -> userId != null && !userId.isBlank())
                        .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, CuserI> usersById =
                cuserIRepository.findByEnoIn(userIds).stream()
                        .collect(
                                Collectors.toMap(
                                        CuserI::getEno, user -> user, (first, second) -> first));
        for (CostDto.TerminalDto terminal : terminals) {
            CuserI user = usersById.get(terminal.getCgprId());
            if (user != null) {
                terminal.setTermSvnTemC(user.getTemC());
                terminal.setTermSvnDpmC(user.getBbrC());
            }
        }
    }

    private String generateTmnMngNo() {
        return generateTmnMngNo(LocalDate.now().getYear());
    }

    private String generateTmnMngNo(int idYear) {
        Long sequence = btermmRepository.getNextSequenceValue();
        return String.format("TER-%s-%04d", idYear, sequence);
    }

    private static String terminalPk(String terminalNo, Integer sno) {
        return terminalNo + "_" + sno;
    }

    private record CostOrgSnapshot(String svnTemNm, String prlmHrkOgzCNm) {
        private static final CostOrgSnapshot EMPTY = new CostOrgSnapshot(null, null);
    }
}
