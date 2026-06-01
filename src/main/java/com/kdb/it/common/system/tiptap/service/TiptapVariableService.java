package com.kdb.it.common.system.tiptap.service;

import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.CategoryMetadata;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ItemRef;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.MetadataResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ProjectRef;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolveResponse;
import com.kdb.it.common.system.tiptap.dto.TiptapVariableDto.ResolvedValue;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser;
import com.kdb.it.common.system.tiptap.util.TiptapTokenParser.ParseResult;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.status.dto.BudgetStatusDto.AggregatedAmount;
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

    /**
     * 전 카테고리 공통 항목 목록.
     * 순서(requestAmount→allocatedAmount→allocationRate)는 UI 드롭다운 표시 순서와 일치.
     */
    private static final List<ItemRef> ITEMS = List.of(
            new ItemRef("requestAmount",   "편성요청액"),
            new ItemRef("allocatedAmount", "편성액"),
            new ItemRef("allocationRate",  "편성률")
    );

    private final TiptapTokenParser tokenParser;
    private final ProjectRepository projectRepository;
    private final BudgetStatusQueryRepository budgetStatusRepository;

    /**
     * 드롭다운용 카탈로그 반환. 권한 필터링은 후속 Task에서 SecurityContext 기준 적용.
     *
     * @return 카테고리 메타데이터 응답 (IT_BUDGET, CAP_BUDGET, OPEX, PROJ 4개 카테고리)
     */
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

    /**
     * 변수 토큰 배열을 해석하여 표시값/상태를 매핑해 반환합니다.
     *
     * <p>
     * 동작 순서:
     * <ol>
     *   <li>토큰 정규식 검증 — 실패 시 {@code INVALID}</li>
     *   <li>카테고리에 따라 {@link BudgetStatusQueryRepository#aggregateByCategory(int, String)} 또는
     *       {@link BudgetStatusQueryRepository#aggregateByProject(int, String)} 호출</li>
     *   <li>편성요청액·편성액·편성률 항목별 포맷팅 적용</li>
     * </ol>
     * </p>
     *
     * @param tokens 해석 대상 토큰 배열 (호출자는 1~200 사이로 검증)
     * @return 토큰별 해석 결과(삽입 순서 유지)
     */
    public ResolveResponse resolve(List<String> tokens) {
        Map<String, ResolvedValue> results = new LinkedHashMap<>();
        for (String token : tokens) {
            results.put(token, resolveOne(token));
        }
        return new ResolveResponse(results);
    }

    /**
     * 단일 토큰 해석. INVALID/MISSING/OK 분기.
     * FORBIDDEN 반환 경로는 현재 미구현 — 향후 SecurityContext 기준 권한 검증 추가 시 이 분기에서 처리.
     * // TODO: 권한 검증 구현 후 FORBIDDEN 분기 추가 (TASK.md)
     */
    private ResolvedValue resolveOne(String token) {
        ParseResult parsed = tokenParser.parse(token);
        if (!parsed.valid()) {
            return ResolvedValue.invalid();
        }

        AggregatedAmount agg = switch (parsed.category()) {
            case IT_BUDGET, CAP_BUDGET, OPEX ->
                    budgetStatusRepository.aggregateByCategory(parsed.year(), parsed.category().name());
            case PROJ ->
                    budgetStatusRepository.aggregateByProject(parsed.year(), parsed.projectCode());
        };

        if (agg == null || (agg.requestSum() == null && agg.allocatedSum() == null)) {
            return ResolvedValue.missing();
        }

        return switch (parsed.item()) {
            case "requestAmount" -> agg.requestSum() == null
                    ? ResolvedValue.missing()
                    : ResolvedValue.ok(formatAmount(agg.requestSum()));
            case "allocatedAmount" -> agg.allocatedSum() == null
                    ? ResolvedValue.missing()
                    : ResolvedValue.ok(formatAmount(agg.allocatedSum()));
            case "allocationRate" -> formatRate(agg);
            default -> ResolvedValue.invalid();
        };
    }

    /**
     * 원(KRW) 단위 정수를 한국식 단위(억원/만원/원)로 포맷합니다.
     *
     * <ul>
     *   <li>1억 이상: {@code "{n}억원"} (예: 90000000000 → "900억원")</li>
     *   <li>1만 이상 ~ 1억 미만: {@code "{n}만원"}</li>
     *   <li>그 외: {@code "{n}원"}</li>
     * </ul>
     */
    private String formatAmount(long won) {
        if (won >= 100_000_000L) {
            long uk = won / 100_000_000L;
            return uk + "억원";
        }
        if (won >= 10_000L) {
            long man = won / 10_000L;
            return man + "만원";
        }
        return won + "원";
    }

    /**
     * 편성률 = 편성액 / 편성요청액 × 100 (소수점 한 자리, 예: "85.3%").
     *
     * <p>편성요청액이 0이거나 두 값 중 어느 하나가 null이면 {@code MISSING}을 반환합니다.</p>
     */
    private ResolvedValue formatRate(AggregatedAmount agg) {
        if (agg.requestSum() == null || agg.requestSum() == 0L || agg.allocatedSum() == null) {
            return ResolvedValue.missing();
        }
        double rate = (agg.allocatedSum() * 100.0) / agg.requestSum();
        return ResolvedValue.ok(String.format("%.1f%%", rate));
    }

    /**
     * 현재 연도 ±2 범위의 연도 목록 반환. 총 5개 원소.
     * 범위 변경 시 UI 드롭다운 동시 갱신 필요.
     */
    private List<Integer> currentPlusMinusTwo() {
        int now = Year.now().getValue();
        return IntStream.rangeClosed(now - 2, now + 2).boxed().toList();
    }
}
