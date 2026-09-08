package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결 단말기 등록을 부모 전산업무비에 표시합니다.
 *
 * <p>{@link CostService#updateCost(String,
 * com.kdb.it.domain.budget.cost.dto.CostDto.UpdateRequest)}는 전체 치환이라 이 표시 하나를 위해 최소 필드만 보내면 요청에 담기지
 * 않은 부모 업무 필드가 null이 되고 부모의 단말기 행이 모두 논리 삭제됩니다. 그래서 {@code TMN_YN}만 바꾸는 좁은 경로를 따로 둡니다.
 */
@Service
@RequiredArgsConstructor
public class CostTerminalLinkService {

    private static final String COST_TABLE = "BCOSTM";

    private final CostRepository costRepository;
    private final ApprovalWriteGuard approvalWriteGuard;

    /**
     * 부모 전산업무비의 단말기 보유 여부만 'Y'로 표시합니다.
     *
     * @param costBgNo 부모 전산업무비 관리번호
     * @throws IllegalArgumentException 활성 전산업무비가 없는 경우
     * @throws IllegalStateException 결재가 진행 중이라 수정할 수 없는 경우
     */
    @Transactional
    public void markTerminalLinked(String costBgNo) {
        Bcostm cost =
                costRepository
                        .findCurrentVersionForUpdate(costBgNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "전산업무비를 찾을 수 없습니다: " + costBgNo));
        approvalWriteGuard.verifyWritable(COST_TABLE, costBgNo, cost.getBgSno(), "수정");
        cost.markTerminalLinked();
    }
}
