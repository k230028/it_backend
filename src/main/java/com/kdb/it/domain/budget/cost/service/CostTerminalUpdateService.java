package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.dto.CostTerminalDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기존 전산업무비 부모의 업무 필드를 보존하면서 금융정보단말기 상세만 치환합니다. */
@Service
@RequiredArgsConstructor
public class CostTerminalUpdateService {

    private static final String COST_TABLE = "BCOSTM";

    private final CodeService codeService;
    private final CostWriteTargetLoader writeTargetLoader;
    private final ApprovalWriteGuard approvalWriteGuard;
    private final CostConcurrencyGuard concurrencyGuard;
    private final CostTerminalSynchronizer terminalSynchronizer;
    private final CostNameSnapshotResolver nameResolver;

    /**
     * 정확한 부모 개정본의 단말기 목록을 치환하고 부모 예산을 원화 합계로 맞춥니다.
     *
     * @param costBgNo 부모 전산업무비 관리번호
     * @param bgSno 부모 전산업무비 순번. null이면 현재 대표 개정본
     * @param request 단말기 목록과 조회 시점 동시성 스탬프
     * @return 수정된 부모 전산업무비 관리번호
     */
    @Transactional
    public String replaceTerminals(
            String costBgNo,
            Integer bgSno,
            CostTerminalDto.TerminalUpdateRequest request) {
        return concurrencyGuard.runUserUpdate(
                () -> {
                    codeService.validateBudgetPeriod();
                    Bcostm target = writeTargetLoader.loadForUpdate(costBgNo, bgSno);
                    OwnershipVerifier.verifyModifiable(
                            target.getFstEnrUsid(), target.getCostSvnDpmC());
                    approvalWriteGuard.verifyWritable(
                            COST_TABLE, target.getCostBgNo(), target.getBgSno(), "수정");
                    concurrencyGuard.verifyStamp(
                            request.getConcurrencyStamp(), target, nameResolver::resolveCgprName);

                    CostDto.UpdateRequest terminalRequest =
                            CostDto.UpdateRequest.builder()
                                    .terminals(request.getTerminals())
                                    .build();
                    terminalSynchronizer.sync(target, terminalRequest, false);
                    BigDecimal totalKrwAmount =
                            request.getTerminals().stream()
                                    .map(CostDto.TerminalDto::getTermRqmBgAmt)
                                    .filter(java.util.Objects::nonNull)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                    target.replaceTerminalSummary(totalKrwAmount);
                    return target.getCostBgNo();
                });
    }
}
