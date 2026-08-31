package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 전산업무비 조회 흐름과 대표행 선택을 담당합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CostQueryService {

    private static final Logger log = LoggerFactory.getLogger(CostQueryService.class);

    private final CostRepository costRepository;
    private final CostQueryAssembler queryAssembler;

    /**
     * 관리번호의 활성 이력 중 대표행을 상세 응답으로 조회합니다.
     *
     * @param costBgNo 전산업무비 관리번호
     * @return 신청서·조직·코드·단말기·예산 정보가 조립된 응답
     * @throws IllegalArgumentException 활성 비용이 없는 경우
     */
    public CostDto.Response getCost(String costBgNo) {
        List<Bcostm> costs = costRepository.findByCostBgNoAndDelYn(costBgNo, "N");
        if (costs.isEmpty()) {
            throw new IllegalArgumentException("Cost not found with id: " + costBgNo);
        }
        return queryAssembler.assembleDetail(CostRepresentativeSelector.pick(costs));
    }

    /** 관리번호와 예산일련번호가 정확히 일치하는 개정본을 조회합니다. */
    public CostDto.Response getCost(String costBgNo, Integer bgSno) {
        Bcostm cost =
                costRepository
                        .findByCostBgNoAndBgSnoAndDelYn(costBgNo, bgSno, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Cost not found with id: "
                                                        + costBgNo
                                                        + ", sno: "
                                                        + bgSno));
        return queryAssembler.assembleDetail(cost);
    }

    /**
     * 삭제되지 않은 모든 전산업무비 목록을 조회합니다.
     *
     * @return 연관 정보가 배치 조립된 목록
     */
    public List<CostDto.Response> getCostList() {
        return queryAssembler.assembleList(costRepository.findAllByDelYn("N"));
    }

    /**
     * 검색 조건에 맞는 전산업무비 목록을 조회합니다.
     *
     * @param condition 검색 조건
     * @return 조건에 맞고 연관 정보가 조립된 목록
     */
    public List<CostDto.Response> searchCostList(CostDto.SearchCondition condition) {
        return searchCostList(condition, com.kdb.it.common.util.ListPageParams.unpaged());
    }

    /**
     * 검색 조건에 맞는 전산업무비를 지정한 페이지 구간만 조회합니다.
     *
     * @param condition 검색 조건
     * @param paging 페이지 파라미터 (미지정이면 상한까지)
     * @return 해당 구간의 목록
     */
    public List<CostDto.Response> searchCostList(
            CostDto.SearchCondition condition, com.kdb.it.common.util.ListPageParams paging) {
        return queryAssembler.assembleList(costRepository.searchByCondition(condition, paging));
    }

    /**
     * 검색 조건에 맞는 전산업무비 전체 건수를 조회합니다.
     *
     * <p>페이지 응답의 {@code X-Total-Count}에 사용합니다. 목록과 같은 WHERE를 공유하므로 페이지를 지정하지 않았을 때의 목록 건수와 일치합니다.
     *
     * @param condition 검색 조건 (page·size는 건수에 영향을 주지 않음)
     * @return 조건에 맞는 전체 건수
     */
    public long countCostList(CostDto.SearchCondition condition) {
        return costRepository.countBySearchCondition(condition);
    }

    /**
     * 여러 관리번호를 입력 순서대로 일괄 조회합니다.
     *
     * @param request 관리번호 목록과 편성예산 기준연도
     * @return 성공 항목과 누락 관리번호를 분리한 응답
     */
    public CostDto.BulkResponse getCostsByIds(CostDto.BulkGetRequest request) {
        if (request == null || request.getCostBgNos() == null || request.getCostBgNos().isEmpty()) {
            return new CostDto.BulkResponse(List.of(), List.of());
        }

        Map<String, List<Bcostm>> historiesById =
                costRepository.findByCostBgNoInAndDelYn(request.getCostBgNos(), "N").stream()
                        .collect(
                                Collectors.groupingBy(
                                        Bcostm::getCostBgNo,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        List<Bcostm> selected = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        for (String costBgNo : request.getCostBgNos()) {
            List<Bcostm> histories = historiesById.get(costBgNo);
            if (histories == null || histories.isEmpty()) {
                failedIds.add(costBgNo);
            } else {
                selected.add(CostRepresentativeSelector.pick(histories));
            }
        }
        if (!failedIds.isEmpty()) {
            log.warn("bulk-get 누락: type=cost, failedIds={}", failedIds);
        }
        return new CostDto.BulkResponse(
                queryAssembler.assembleBulk(selected, request.getBseYy()), failedIds);
    }
}
