package com.kdb.it.domain.budget.cost.util;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공통코드 {@code cId} 기준으로 지정한 {@code cdva} 집합을 {@code cdva → CDVA_NM} 맵으로 만든다.
 *
 * <p>예산/사업 서비스에서 비목·구분 코드 표시명을 한 번에 조회하기 위한 공통 헬퍼.
 * {@code cdvas}가 비어 있으면 빈 맵을 반환하고, 코드명이 null인 항목은 제외한다(표시용 매핑).</p>
 */
@Component
@RequiredArgsConstructor
public class CodeNameMapBuilder {

    private final CodeRepository codeRepository;

    /**
     * @param cId   공통코드 ID(예: {@code IOE_C})
     * @param cdvas 매핑 대상 코드값 집합. null/빈 집합이면 빈 맵 반환.
     * @return {@code cdva → CDVA_NM} 맵(코드명 null 항목 제외, 중복 키는 선순위 유지)
     */
    public Map<String, String> build(String cId, Set<String> cdvas) {
        if (cdvas == null || cdvas.isEmpty()) {
            return Map.of();
        }
        return codeRepository.findByCIdWithValidDate(cId, null).stream()
                .filter(c -> cdvas.contains(c.getCdva()) && c.getCdvaNm() != null)
                .collect(Collectors.toMap(Ccodem::getCdva, Ccodem::getCdvaNm, (a, b) -> a));
    }
}
