package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 호출자의 쓰기 트랜잭션에서 비용 개정본을 잠근 뒤 수정 대상 대표 행을 선택합니다. */
@Component
@RequiredArgsConstructor
public class CostWriteTargetLoader {

    private final CostRepository costRepository;

    Bcostm loadForUpdate(String id, Integer revision) {
        return (revision == null
                        ? lockCurrentCost(id)
                        : costRepository.findVersionForUpdate(id, revision))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Cost not found with id: "
                                                + id
                                                + (revision == null ? "" : ", sno: " + revision)));
    }

    private Optional<Bcostm> lockCurrentCost(String id) {
        List<Bcostm> candidates = costRepository.findCurrentVersionsForUpdate(id);
        return candidates.isEmpty()
                ? Optional.empty()
                : Optional.of(CostRepresentativeSelector.pick(candidates));
    }
}
