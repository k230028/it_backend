package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.common.util.DateFormatUtil;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        if (!skipBudgetPeriodValidation) {
            codeService.validateBudgetPeriod();
        }
        String costBgNo = request.getCostBgNo();
        if (costBgNo == null || costBgNo.isEmpty()) {
            Long sequence = costRepository.getNextSequenceValue();
            costBgNo = String.format("COST-%s-%04d", LocalDate.now().getYear(), sequence);
            request.setCostBgNo(costBgNo);
        }
        Integer nextSno = costRepository.getNextSnoValue(costBgNo);
        if (nextSno == null) {
            nextSno = 1;
        }
        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));
        BigDecimal[] reconciled =
                BudgetAmountCalculator.reconcileAmount(
                        request.getFcAmt(),
                        request.getCostTotXpAmt(),
                        request.getCurC(),
                        request.getXcr());
        request.setCostTotXpAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);

        Bcostm cost = request.toEntity(nextSno);
        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(cost.getCgprId());
        cost.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        cost.assignSvnOrgNames(
                orgNameResolver.resolveName(cost.getCostSvnDpmC()), orgSnapshot.svnTemNm());
        costRepository.save(cost);

        applyTerminalOrgCodes(request.getTerminals());
        if (request.getTerminals() != null) {
            for (CostDto.TerminalDto terminal : request.getTerminals()) {
                if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                    terminal.setTmnMngNo(generateTmnMngNo());
                }
                if (terminal.getSno() == null) {
                    terminal.setSno(1);
                }
                reconcileTerminalAmount(terminal);
                Btermm entity = terminal.toEntity();
                entity.setBcostmInfo(cost.getCostBgNo(), cost.getBgSno());
                btermmRepository.save(entity);
            }
        }
        return cost.getCostBgNo();
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
        codeService.validateBudgetPeriod();
        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(itMngcNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + itMngcNo);
        }
        Bcostm target = CostRepresentativeSelector.pick(costs);
        OwnershipVerifier.verifyModifiable(target.getFstEnrUsid(), target.getCostSvnDpmC());

        request.setXcr(xcrLookupService.resolveXcr(request.getCurC(), LocalDate.now()));
        BigDecimal[] reconciled =
                BudgetAmountCalculator.reconcileAmount(
                        request.getFcAmt(),
                        request.getCostTotXpAmt(),
                        request.getCurC(),
                        request.getXcr());
        request.setCostTotXpAmt(reconciled[0]);
        request.setFcAmt(reconciled[1]);
        target.update(toUpdateCommand(request));

        CostOrgSnapshot orgSnapshot = resolveAuthorOrgNames(target.getCgprId());
        target.assignPrlmHrkOgzCCone(orgSnapshot.prlmHrkOgzCNm());
        target.assignSvnOrgNames(
                orgNameResolver.resolveName(target.getCostSvnDpmC()), orgSnapshot.svnTemNm());

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
            reconcileTerminalAmount(terminal);
            Btermm existing =
                    terminal.getTmnMngNo() != null && terminal.getSno() != null
                            ? existingByPk.get(
                                    terminalPk(terminal.getTmnMngNo(), terminal.getSno()))
                            : null;
            if (existing != null) {
                updateTerminal(existing, terminal);
                keptPks.add(terminalPk(existing.getTmnMngNo(), existing.getSno()));
            } else {
                if (terminal.getTmnMngNo() == null || terminal.getTmnMngNo().isEmpty()) {
                    terminal.setTmnMngNo(generateTmnMngNo());
                }
                if (terminal.getSno() == null) {
                    terminal.setSno(1);
                }
                Btermm entity = terminal.toEntity();
                entity.setBcostmInfo(target.getCostBgNo(), target.getBgSno());
                btermmRepository.save(entity);
                keptPks.add(terminalPk(terminal.getTmnMngNo(), terminal.getSno()));
            }
        }
        existingTerminals.stream()
                .filter(
                        terminal ->
                                !keptPks.contains(
                                        terminalPk(terminal.getTmnMngNo(), terminal.getSno())))
                .forEach(Btermm::delete);
        return target.getCostBgNo();
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

    private static void updateTerminal(Btermm existing, CostDto.TerminalDto terminal) {
        existing.update(toTerminalUpdateCommand(terminal));
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
                .termSvnDpmC(terminal.getTermSvnDpmC())
                .rmk(terminal.getRmk())
                .fcAmt(terminal.getFcAmt())
                .build();
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
        Long sequence = btermmRepository.getNextSequenceValue();
        return String.format("TER-%s-%04d", LocalDate.now().getYear(), sequence);
    }

    private static String terminalPk(String terminalNo, Integer sno) {
        return terminalNo + "_" + sno;
    }

    private record CostOrgSnapshot(String svnTemNm, String prlmHrkOgzCNm) {
        private static final CostOrgSnapshot EMPTY = new CostOrgSnapshot(null, null);
    }
}
