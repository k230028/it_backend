package com.kdb.it.common.system.tiptap.service;

import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.CategoryMetadata;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ItemRef;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ProjectRef;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolvedValue;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.status.repository.BudgetStatusQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tiptap 변수 카탈로그 빌드 + 토큰 해석 서비스.
 * Design Ref: §2.2, §4.5
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TiptapVariableService {

    private static final List<ItemRef> ITEMS = List.of(
            new ItemRef("requestAmount",   "편성요청액"),
            new ItemRef("allocatedAmount", "편성액"),
            new ItemRef("allocationRate",  "편성률")
    );

    private final TiptapTokenParser tokenParser;
    private final ProjectRepository projectRepository;
    private final BudgetStatusQueryRepository budgetStatusRepository;

    /** 드롭다운용 카탈로그 반환. 권한 필터링은 후속 Task에서 SecurityContext 기준 적용. */
    public MetadataResponse getMetadata() {
        List<Integer> years = currentPlusMinusTwo();
        List<ProjectRef> projects = projectRepository.findActiveProjectRefs().stream()
                .map(r -> new ProjectRef(r.code(), r.name()))
                .toList();

        return new MetadataResponse(List.of(
                new CategoryMetadata("IT_BUDGET",  "전산예산",   years, null,     ITEMS),
                new CategoryMetadata("CAP_BUDGET", "자본예산",   years, null,     ITEMS),
                new CategoryMetadata("OPEX",       "일반관리비", years, null,     ITEMS),
                new CategoryMetadata("PROJ",       "사업별",     years, projects, ITEMS)
        ));
    }

    /** Task 4에서 본 구현 채워짐. 현재는 모든 토큰을 INVALID로 반환. */
    public ResolveResponse resolve(List<String> tokens) {
        Map<String, ResolvedValue> results = new LinkedHashMap<>();
        for (String token : tokens) {
            results.put(token, ResolvedValue.invalid());
        }
        return new ResolveResponse(results);
    }

    private List<Integer> currentPlusMinusTwo() {
        int now = Year.now().getValue();
        return IntStream.rangeClosed(now - 2, now + 2).boxed().toList();
    }
}
