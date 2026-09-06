package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostRepresentativeSelector;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 이관 커밋 트랜잭션 안에서 사업코드 후보 검증과 잠금 후 원장 보충을 수행합니다. */
@Component
@RequiredArgsConstructor
@Slf4j
class MigrationCostBudgetUnitWriter {

    private final CostRepository costRepository;
    private final ApprovalWriteGuard approvalWriteGuard;
    private final EntityManager entityManager;

    /**
     * 매칭된 전산업무비 원장의 빈 사업코드를 종합본 값으로 채웁니다 (§4.1).
     *
     * <p>편성요청서 양식에 사업코드 열이 없어 1단계가 만든 {@code BCOSTM}은 대부분 이 값이 비어 있는데, 예산 집계가 사업코드로 묶이므로 비워 두면 집계에서
     * 빠집니다. 이미 값이 있으면 건드리지 않습니다 — 부서가 적어 낸 값을 종합본이 조용히 바꾸지 않게 합니다.
     *
     * <p>여러 버전 중 대표 행({@code LST_YN='Y'} 우선)만 채웁니다. 과거 버전은 그 시점의 기록이라 소급해 바꾸지 않습니다.
     *
     * <p><b>코드표에 있는 값만 씁니다.</b> {@code MigrationValidator}가 이 값을 {@code validateAlways}에서 검사하므로 여기
     * 닿는 값은 이미 해석된 값이지만, 그 검사는 코드 카탈로그가 비면(코드그룹 미적재 등) 판정 근거가 없어 그대로 통과시킵니다. 그 구멍으로 엑셀 원문이 흘러들면
     * {@code BG_UNT_ABUS_C}가 3자라 flush에서 {@code ORA-12899}가 나거나, 길이가 맞는 오타가 조용히 저장돼 그 전산업무비가 엉뚱한 예산
     * 집계 버킷에 들어갑니다. 쓰기 직전에 한 번 더 막고, 막힌 값은 로그로 남깁니다 — 채우지 않으면 집계에서 빠질 뿐 오염되지는 않습니다.
     */
    void fillCostBudgetUnitCodes(
            List<MigrationDto.SheetPayload> sheets,
            Map<Integer, String> matched,
            MigrationYearSnapshot.Data snapshot,
            MigrationLookupIndex index,
            Map<String, String> overrides) {
        Map<String, String> requestedCodes = new java.util.TreeMap<>();
        if (matched.isEmpty()) {
            return;
        }
        for (MigrationDto.SheetPayload sheet : sheets) {
            if (sheet.kind() != SheetKind.COST) {
                continue;
            }
            for (MigrationDto.NormalizedRow row : sheet.rows()) {
                String costNo = matched.get(row.excelRow());
                if (costNo == null || snapshot.bgUntAbusCOf(costNo) != null) {
                    continue;
                }
                String abusCode =
                        MigrationDiagnostics.cell(row, "abusCode", overrides, sheet).trim();
                if (abusCode.isBlank()) {
                    continue;
                }
                if (!index.abusUnitNameByCode().containsKey(abusCode)) {
                    log.warn(
                            "코드표에 없는 사업코드라 전산업무비에 채우지 않습니다 (전산업무비={}, 행={}, 값='{}')",
                            costNo,
                            row.excelRow(),
                            abusCode);
                    continue;
                }
                requestedCodes.putIfAbsent(costNo, abusCode);
            }
        }
        for (var entry : requestedCodes.entrySet()) {
            Bcostm target =
                    CostRepresentativeSelector.pick(
                            costRepository.findCurrentVersionsForUpdate(entry.getKey()));
            // 연도 스냅샷이 먼저 읽은 영속 엔티티도 잠금 뒤 최신 DB 값으로 다시 읽는다.
            entityManager.refresh(target);
            approvalWriteGuard.verifyWritable(
                    "BCOSTM", target.getCostBgNo(), target.getBgSno(), "수정");
            target.fillBudgetUnitCodeIfAbsent(entry.getValue());
        }
    }
}
