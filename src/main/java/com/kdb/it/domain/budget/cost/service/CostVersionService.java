package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.common.security.BudgetDetailAccessVerifier;
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
        return createReapplication(costBgNo, null);
    }

    /** 인증 사용자의 부서 범위를 확인한 뒤 재상신 초안을 생성합니다. */
    @Transactional
    public CostVersion createReapplication(String costBgNo, CustomUserDetails actor) {
        Bcostm source =
                costRepository
                        .findCurrentVersionForUpdate(costBgNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "재상신할 최종 전산업무비가 없습니다: " + costBgNo));
        if (actor != null) {
            BudgetDetailAccessVerifier.verifyReadable(source.getCostSvnDpmC(), actor);
        }
        // 원본을 잠근 뒤 미결 초안 존재를 확인한다. 잠금이 동시 요청을 직렬화하므로
        // 두 번째 트랜잭션은 여기서 차단되어 초안이 중첩 생성되지 않는다.
        // 판정은 최종본 순번 초과로 한다 — LST_YN='N'만 보면 승격으로 강등된 과거 버전까지
        // 초안으로 오인한다.
        if (costRepository.existsByCostBgNoAndBgSnoGreaterThanAndDelYn(
                costBgNo, source.getBgSno(), "N")) {
            throw new IllegalStateException("이미 재상신 초안이 있습니다: " + costBgNo);
        }
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

    /** 인증 사용자의 부서 범위 안에 있는 개정 이력만 반환합니다. */
    public List<Bcostm> findHistory(String costBgNo, CustomUserDetails actor) {
        List<Bcostm> history = findHistory(costBgNo);
        history.forEach(cost -> BudgetDetailAccessVerifier.verifyReadable(cost.getCostSvnDpmC(), actor));
        return history;
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
                        () -> new IllegalArgumentException("승격할 전산업무비 개정본이 없습니다: " + costBgNo));
        verifyNotRegressing(costBgNo, bgSno);
        costRepository.clearCurrentVersion(costBgNo, bgSno);
        if (costRepository.markVersionCurrent(costBgNo, bgSno) != 1) {
            throw new IllegalStateException("승격할 전산업무비 개정본이 없습니다: " + costBgNo);
        }
    }

    /**
     * 현재 최종본보다 낮은 순번으로 되돌리는 승격을 막습니다.
     *
     * <p>초안이 중복 생성돼 둘 다 승인되거나 상신 시 잘못된 순번이 결재 매핑에 실려 오면, 나중 승격이 이미 승인된 최신본을 조용히 강등시킵니다.
     *
     * @param costBgNo 전산업무비예산번호
     * @param bgSno 승격하려는 개정 순번
     * @throws IllegalStateException 현재 최종본보다 낮은 순번인 경우
     */
    private void verifyNotRegressing(String costBgNo, Integer bgSno) {
        Integer currentSno =
                costRepository
                        .findByCostBgNoAndLstYnAndDelYn(costBgNo, "Y", "N")
                        .map(Bcostm::getBgSno)
                        .orElse(null);
        if (currentSno != null && bgSno != null && bgSno < currentSno) {
            throw new IllegalStateException(
                    "이전 개정본으로 되돌릴 수 없습니다: %s (현재 최종본 %d, 요청 %d)"
                            .formatted(costBgNo, currentSno, bgSno));
        }
    }

    /** 전산업무비 개정본 식별 응답입니다. */
    public record CostVersion(String costBgNo, Integer bgSno, String lstYn) {}
}
