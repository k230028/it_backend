package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연결 단말기 등록을 부모 전산업무비에 표시합니다.
 *
 * <p>{@link CostService#updateCost(String, CostDto.UpdateRequest)}는 전체 치환이라 이 표시 하나를 위해 최소 필드만 보내면
 * 요청에 담기지 않은 부모 업무 필드가 null이 되고 부모의 단말기 행이 모두 논리 삭제됩니다. 그래서 {@code TMN_YN}만 바꾸는 좁은 경로를 따로 둡니다.
 *
 * <p>화면의 신규 연결 등록은 {@link #createLinkedCost}로 단말 전산업무비 생성과 부모 표시를 한 트랜잭션에서 처리합니다. 두 호출을 순차로 보내면 두
 * 번째 호출(부모 표시)이 결재 상태 때문에 실패했을 때 단말 생성만 적용된 반쯤 적용 상태가 남습니다(BE-105).
 */
@Service
@RequiredArgsConstructor
public class CostTerminalLinkService {

    private static final String COST_TABLE = "BCOSTM";

    private final CostRepository costRepository;
    private final ApprovalWriteGuard approvalWriteGuard;
    private final CostService costService;

    /**
     * 부모 전산업무비의 단말기 보유 여부만 'Y'로 표시합니다.
     *
     * @param costBgNo 부모 전산업무비 관리번호
     * @throws IllegalArgumentException 활성 전산업무비가 없는 경우
     * @throws IllegalStateException 결재가 진행 중이라 수정할 수 없는 경우
     */
    @Transactional
    public void markTerminalLinked(String costBgNo) {
        lockWritableParent(costBgNo).markTerminalLinked();
    }

    /**
     * 부모에 연결되는 단말 전산업무비를 생성하고 부모의 단말기 보유 여부를 함께 표시합니다.
     *
     * <p>부모 행을 먼저 잠그고 결재 상태를 확인한 뒤에만 단말 전산업무비를 만듭니다. 부모가 결재 진행 중이면 아무것도 저장하지 않으며, 생성 이후의 실패도 같은
     * 트랜잭션으로 함께 되돌립니다.
     *
     * @param parentCostBgNo 부모 전산업무비 관리번호
     * @param request 단말 전산업무비 생성 요청 (사용자 화면 경로와 같은 검증을 거친다)
     * @return 생성된 단말 전산업무비 관리번호
     * @throws IllegalArgumentException 활성 부모 전산업무비가 없는 경우
     * @throws IllegalStateException 부모의 결재가 진행 중이라 수정할 수 없는 경우
     * @throws com.kdb.it.exception.CustomGeneralException 예산 신청 기간이 아닌 경우
     */
    @Transactional
    public String createLinkedCost(String parentCostBgNo, CostDto.CreateRequest request) {
        Bcostm parent = lockWritableParent(parentCostBgNo);
        String createdCostBgNo = costService.createCost(request);
        parent.markTerminalLinked();
        return createdCostBgNo;
    }

    private Bcostm lockWritableParent(String costBgNo) {
        Bcostm cost =
                costRepository
                        .findCurrentVersionForUpdate(costBgNo)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "전산업무비를 찾을 수 없습니다: " + costBgNo));
        approvalWriteGuard.verifyWritable(COST_TABLE, costBgNo, cost.getBgSno(), "수정");
        return cost;
    }
}
