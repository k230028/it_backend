package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전산업무비와 연결 단말기의 재상신 개정본을 관리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostVersionService {

    private static final String COST_TABLE = "BCOSTM";

    private final CostRepository costRepository;
    private final BtermmRepository terminalRepository;
    private final ApplicationMapRepository applicationMapRepository;

    /** 결재 완료된 현재 전산업무비와 단말기를 다음 순번의 미상신 초안으로 복제합니다. */
    @Transactional
    public CostVersion createReapplication(String costBgNo) {
        Bcostm source =
                costRepository
                        .findCurrentVersionForUpdate(costBgNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "재상신할 최종 전산업무비가 없습니다: " + costBgNo));
        String latestStatus =
                applicationMapRepository
                        .findLatestApplicationStatus(COST_TABLE, costBgNo, source.getBgSno())
                        .orElse(null);
        if (!ApprovalStatus.COMPLETED.code().equals(latestStatus)) {
            throw new IllegalArgumentException("결재 완료된 전산업무비만 재상신할 수 있습니다: " + costBgNo);
        }

        Bcostm draft = source.createReapplicationDraft(costRepository.getNextSnoValue(costBgNo));
        costRepository.save(draft);
        terminalRepository
                .findByTermBgNoAndTermBgSnoAndDelYn(costBgNo, source.getBgSno(), "N")
                .forEach(
                        terminal ->
                                terminalRepository.save(
                                        terminal.createReapplicationDraft(
                                                terminalRepository.getNextSnoValue(
                                                        terminal.getTmnMngNo()),
                                                draft.getBgSno())));
        return new CostVersion(draft.getCostBgNo(), draft.getBgSno(), draft.getLstYn());
    }

    /** 관리번호에 속한 모든 미삭제 전산업무비 개정본을 순번순으로 반환합니다. */
    public List<Bcostm> findHistory(String costBgNo) {
        return costRepository.findByCostBgNoAndDelYnOrderByBgSnoAsc(costBgNo, "N");
    }

    /** 관리번호와 순번이 정확히 일치하는 개정본을 반환합니다. */
    public Optional<Bcostm> findVersion(String costBgNo, Integer bgSno) {
        return costRepository.findByCostBgNoAndBgSnoAndDelYn(costBgNo, bgSno, "N");
    }

    /** 결재 완료된 정확한 개정본을 현재 최종본으로 원자적으로 전환합니다. */
    @Transactional
    public void promoteApprovedVersion(String costBgNo, Integer bgSno) {
        costRepository
                .findVersionForUpdate(costBgNo, bgSno)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "승격할 전산업무비 개정본이 없습니다: " + costBgNo));
        costRepository.clearCurrentVersion(costBgNo, bgSno);
        if (costRepository.markVersionCurrent(costBgNo, bgSno) != 1) {
            throw new IllegalStateException("승격할 전산업무비 개정본이 없습니다: " + costBgNo);
        }
    }

    /** 전산업무비 개정본 식별 응답입니다. */
    public record CostVersion(String costBgNo, Integer bgSno, String lstYn) {}
}
