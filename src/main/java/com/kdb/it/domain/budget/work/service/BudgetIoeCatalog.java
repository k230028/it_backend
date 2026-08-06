package com.kdb.it.domain.budget.work.service;

import com.kdb.it.common.code.IoeCategories;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 예산작업 조회와 적용에서 공통으로 쓰는 비목 코드 변환을 제공합니다. */
@Component
@RequiredArgsConstructor
class BudgetIoeCatalog {

    private final CodeRepository codeRepository;

    List<Ccodem> findCodes(String cId) {
        return Optional.ofNullable(codeRepository.findByCIdWithValidDate(cId, null))
                .orElse(List.of());
    }

    boolean isCapitalCTp(String cTp) {
        return IoeCategories.isCapitalCTp(cTp);
    }

    String resolveGroupName(Ccodem code) {
        return IoeCategories.resolveGroupName(code);
    }

    String extractPrefix(String cdva) {
        return cdva.replace("DUP-", "");
    }

    Map<String, Set<String>> buildPrefixToIoeCValuesMap(List<Ccodem> allIoeCodes) {
        Map<String, Set<String>> map = new LinkedHashMap<>();
        for (Ccodem code : allIoeCodes) {
            String hierarchyCode = code.getCdvaDtlC();
            if (hierarchyCode == null || code.getCdva() == null) continue;
            int dashIdx = hierarchyCode.indexOf('-');
            String prefix = dashIdx > 0 ? hierarchyCode.substring(0, dashIdx) : hierarchyCode;
            map.computeIfAbsent(prefix, ignored -> new HashSet<>()).add(code.getCdva());
        }
        return map;
    }
}
