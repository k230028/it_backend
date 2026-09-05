package com.kdb.it.domain.migration.terminal;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import com.kdb.it.domain.migration.terminal.dto.TerminalBulkImportDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 금융정보단말기 업로드 행을 연도별 전산업무비 원장으로 반영합니다. */
@Service
@RequiredArgsConstructor
public class TerminalBulkImportService {

    private final TerminalBulkImportPlanner planner;
    private final CostService costService;
    private final CostRepository costRepository;
    private final CodeService codeService;
    private final OrgIdentityResolver orgIdentityResolver;

    /** 저장 없이 행·코드·조직·관리번호를 검증하고 반영 예정 내역을 반환합니다. */
    @Transactional(readOnly = true)
    public TerminalBulkImportDto.Response dryRun(TerminalBulkImportDto.Request request) {
        Prepared prepared = prepare(request, false);
        return response(prepared, request.rows().size());
    }

    /** 미리보기와 같은 검증을 다시 수행한 뒤 모든 연도 그룹을 하나의 트랜잭션으로 반영합니다. */
    @Transactional
    public TerminalBulkImportDto.Response commit(
            TerminalBulkImportDto.Request request, String actorEno) {
        Prepared prepared = prepare(request, true);
        List<TerminalBulkImportDto.Group> groups = new ArrayList<>();
        for (PreparedGroup group : prepared.groups()) {
            String costId;
            if (group.createNew()) {
                costId =
                        costService.createCostForMigration(
                                toCreateRequest(group, actorEno),
                                Integer.parseInt(group.plan().bseYy()));
            } else {
                costId =
                        costService.updateCostForMigration(
                                group.plan().costId(), toUpdateRequest(group, actorEno));
            }
            groups.add(toResponse(group, costId));
        }
        return new TerminalBulkImportDto.Response(
                request.rows().size(),
                groups.stream().mapToInt(TerminalBulkImportDto.Group::terminalCount).sum(),
                (int) groups.stream().filter(TerminalBulkImportDto.Group::createNew).count(),
                (int) groups.stream().filter(group -> !group.createNew()).count(),
                groups);
    }

    private Prepared prepare(TerminalBulkImportDto.Request request, boolean lock) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new IllegalArgumentException("업로드할 금융정보단말기 행이 없습니다.");
        }
        TerminalCodeCatalog codes = TerminalCodeCatalog.load(codeService);
        OrgIdentityResolver.Index org = orgIdentityResolver.snapshot();
        List<TerminalBulkImportDto.Row> rows = request.rows();
        for (TerminalBulkImportDto.Row row : rows) {
            validateRow(row);
        }

        List<TerminalBulkImportPlanner.PlannedGroup> plans = planner.plan(request.baseYear(), rows);
        Map<String, List<com.kdb.it.domain.budget.cost.entity.Bcostm>> locked = new HashMap<>();
        if (lock) {
            // 모든 기존 부모를 ID·개정 순서로 먼저 잠근 뒤 검증과 자식 쓰기를 시작한다.
            plans.stream()
                    .filter(plan -> !plan.createNew())
                    .map(TerminalBulkImportPlanner.PlannedGroup::costId)
                    .distinct()
                    .sorted()
                    .forEach(id -> locked.put(id, costRepository.findCurrentVersionsForUpdate(id)));
        }
        List<PreparedGroup> groups = new ArrayList<>();
        for (TerminalBulkImportPlanner.PlannedGroup plan : plans) {
            if (!plan.createNew()) {
                if (lock) {
                    if (locked.get(plan.costId()).stream()
                            .noneMatch(cost -> plan.bseYy().equals(cost.getBseYy()))) {
                        throw new IllegalArgumentException(
                                "엑셀 " + plan.bseYy() + "년 전산업무비 ID를 찾을 수 없습니다: " + plan.costId());
                    }
                } else {
                    validateExistingCost(plan.costId(), plan.bseYy());
                }
            }
            List<PreparedRow> preparedRows =
                    plan.rows().stream().map(row -> prepareRow(row, plan, org, codes)).toList();
            groups.add(new PreparedGroup(plan, preparedRows));
        }
        return new Prepared(groups);
    }

    private PreparedRow prepareRow(
            TerminalBulkImportDto.Row row,
            TerminalBulkImportPlanner.PlannedGroup plan,
            OrgIdentityResolver.Index org,
            TerminalCodeCatalog codes) {
        String department = resolveOrg(org, row.department(), row.excelRow(), "부서");
        String team = resolveTeam(org, row.team(), department);
        OrgIdentityResolver.Resolution managerResolution =
                org.resolveUser(row.managerName(), department);
        String manager = managerResolution.code();
        String currency =
                codes.resolve(CommonCodeGroups.CURRENCY, row.currency(), row.excelRow(), "통화");
        String service =
                codes.resolve(
                        CommonCodeGroups.TERM_SERVICE, row.terminalName(), row.excelRow(), "단말기명");
        String method =
                codes.resolve(
                        CommonCodeGroups.TERM_KIND, row.usageMethod(), row.excelRow(), "이용방식");
        String payment =
                codes.resolve(CommonCodeGroups.DFR_CLE, row.paymentCycle(), row.excelRow(), "지급주기");
        String businessType = codes.resolveBusinessType(row.currentKind(), row.excelRow());
        TerminalBulkImportDto.YearValues values =
                plan.previousPeriod() ? row.previousValues() : row.currentValues();
        BigDecimal annual = values.annualAmount();
        BigDecimal foreignAnnual =
                "KRW".equals(currency) || values.foreignMonthly() == null
                        ? null
                        : values.foreignMonthly().multiply(BigDecimal.valueOf(12));
        BigDecimal xcr =
                foreignAnnual == null || foreignAnnual.signum() == 0
                        ? null
                        : annual.divide(foreignAnnual, 4, RoundingMode.HALF_UP);
        return new PreparedRow(
                row,
                department,
                row.department(),
                team,
                row.team(),
                manager,
                currency,
                service,
                method,
                payment,
                businessType,
                annual,
                foreignAnnual,
                xcr);
    }

    private CostDto.CreateRequest toCreateRequest(PreparedGroup group, String actorEno) {
        PreparedRow first = group.rows().getFirst();
        return CostDto.CreateRequest.builder()
                .bseYy(group.plan().bseYy())
                .cttNm(first.row().terminalName())
                .cttOppNm(first.row().service())
                .costTotXpAmt(group.plan().totalKrwAmount())
                .dfrCleC(first.payment())
                .fstDfrDt(group.plan().bseYy() + "0101")
                .curC("KRW")
                .sectSysUtzYn("N")
                .indRsn(first.row().note())
                /* 신규 생성 시 업로드 담당자의 행번을 전산업무비 담당자ID로 저장하지 않는다. */
                .cgprId(null)
                .cgprNm(first.row().managerName())
                .costSvnDpmC(first.department())
                .svnTemC(first.team())
                .costSvnDpmNm(first.departmentName())
                .svnTemNm(first.teamName())
                .tmnYn("Y")
                .abusTc(first.businessType())
                .terminals(toTerminals(group))
                .build();
    }

    private CostDto.UpdateRequest toUpdateRequest(PreparedGroup group, String actorEno) {
        PreparedRow first = group.rows().getFirst();
        return CostDto.UpdateRequest.builder()
                .cttNm(first.row().terminalName())
                .cttOppNm(first.row().service())
                .costTotXpAmt(group.plan().totalKrwAmount())
                .dfrCleC(first.payment())
                .fstDfrDt(group.plan().bseYy() + "0101")
                .curC("KRW")
                .sectSysUtzYn("N")
                .indRsn(first.row().note())
                .cgprId(first.manager())
                .cgprNm(first.row().managerName())
                .costSvnDpmC(first.department())
                .svnTemC(first.team())
                .costSvnDpmNm(first.departmentName())
                .svnTemNm(first.teamName())
                .tmnYn("Y")
                .abusTc(first.businessType())
                .bseYy(group.plan().bseYy())
                .terminals(toTerminals(group))
                .build();
    }

    private List<CostDto.TerminalDto> toTerminals(PreparedGroup group) {
        return group.rows().stream()
                .map(
                        row ->
                                CostDto.TerminalDto.builder()
                                        .spfTmnNm(row.row().service())
                                        .tmnKdTc(row.method())
                                        .nsfUsgCone(row.row().purpose())
                                        .tmnClsfC(row.service())
                                        .termRqmBgAmt(row.annual())
                                        .fcAmt(row.foreignAnnual())
                                        .curC(row.currency())
                                        .xcr(row.xcr())
                                        .xcrBseDt(group.plan().bseYy() + "0101")
                                        .dfrCleC(row.payment())
                                        .indRsn(row.row().currentKind())
                                        .cgprId(null)
                                        .cgprNm(row.row().managerName())
                                        .termSvnDpmNm(row.departmentName())
                                        .termSvnTemNm(row.teamName())
                                        .termSvnDpmC(row.department())
                                        .termSvnTemC(row.team())
                                        .rmk(row.row().note())
                                        .build())
                .toList();
    }

    private void validateRow(TerminalBulkImportDto.Row row) {
        if (row == null) {
            throw new IllegalArgumentException("빈 업로드 행이 있습니다.");
        }
        if (!row.hasPreviousData() && !row.hasCurrentData()) {
            throw new IllegalArgumentException("엑셀 " + row.excelRow() + "행에 집행금액이 없습니다.");
        }
        if (row.terminalName() == null || row.terminalName().isBlank()) {
            throw new IllegalArgumentException("엑셀 " + row.excelRow() + "행의 단말기명이 비어 있습니다.");
        }
    }

    private void validateExistingCost(String costId, String year) {
        costRepository
                .findByCostBgNoAndBseYyAndLstYnAndDelYn(costId, year, "Y", "N")
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "엑셀 " + year + "년 전산업무비 ID를 찾을 수 없습니다: " + costId));
    }

    private static String resolveOrg(
            OrgIdentityResolver.Index org, String raw, int excelRow, String label) {
        String code = org.resolveOrg(raw).code();
        if (code == null) {
            throw new IllegalArgumentException(
                    "엑셀 " + excelRow + "행의 " + label + "을(를) 조직코드로 해석할 수 없습니다: " + raw);
        }
        return code;
    }

    private static String resolveTeam(
            OrgIdentityResolver.Index org, String raw, String departmentCode) {
        return org.resolveTeam(raw, departmentCode).code();
    }

    private static TerminalBulkImportDto.Response response(Prepared prepared, int rowCount) {
        List<TerminalBulkImportDto.Group> groups =
                prepared.groups().stream()
                        .map(group -> toResponse(group, group.plan().costId()))
                        .toList();
        return new TerminalBulkImportDto.Response(
                rowCount,
                groups.stream().mapToInt(TerminalBulkImportDto.Group::terminalCount).sum(),
                (int) groups.stream().filter(TerminalBulkImportDto.Group::createNew).count(),
                (int) groups.stream().filter(group -> !group.createNew()).count(),
                groups);
    }

    private static TerminalBulkImportDto.Group toResponse(PreparedGroup group, String costId) {
        return new TerminalBulkImportDto.Group(
                group.plan().bseYy(),
                costId,
                group.createNew(),
                group.rows().size(),
                group.plan().totalKrwAmount(),
                group.rows().stream().map(row -> row.row().excelRow()).toList());
    }

    private record Prepared(List<PreparedGroup> groups) {}

    private record PreparedGroup(
            TerminalBulkImportPlanner.PlannedGroup plan, List<PreparedRow> rows) {
        boolean createNew() {
            return plan.createNew();
        }
    }

    private record PreparedRow(
            TerminalBulkImportDto.Row row,
            String department,
            String departmentName,
            String team,
            String teamName,
            String manager,
            String currency,
            String service,
            String method,
            String payment,
            String businessType,
            BigDecimal annual,
            BigDecimal foreignAnnual,
            BigDecimal xcr) {}

    private record TerminalCodeCatalog(Map<String, Map<String, String>> byGroup) {

        static TerminalCodeCatalog load(CodeService codeService) {
            Map<String, Map<String, String>> groups = new HashMap<>();
            for (String group :
                    List.of(
                            CommonCodeGroups.CURRENCY,
                            CommonCodeGroups.TERM_SERVICE,
                            CommonCodeGroups.TERM_KIND,
                            CommonCodeGroups.DFR_CLE,
                            CommonCodeGroups.ABUS)) {
                Map<String, String> aliases = new LinkedHashMap<>();
                for (Ccodem code : codeService.findCodeEntitiesByCIdWithoutCache(group)) {
                    add(aliases, code.getCdvaNm(), code.getCdva());
                    add(aliases, code.getCdvaDtl(), code.getCdva());
                    add(aliases, code.getCdvaDtlC(), code.getCdva());
                    add(aliases, code.getCdva(), code.getCdva());
                }
                addTemplateAliases(group, aliases);
                groups.put(group, aliases);
            }
            return new TerminalCodeCatalog(groups);
        }

        private static void addTemplateAliases(String group, Map<String, String> aliases) {
            if (CommonCodeGroups.DFR_CLE.equals(group)) {
                addAlias(aliases, "월별", "M");
                addAlias(aliases, "분기별", "Q");
                addAlias(aliases, "반기별", "H");
                addAlias(aliases, "연간", "Y");
                addAlias(aliases, "연도별", "Y");
            } else if (CommonCodeGroups.TERM_KIND.equals(group)) {
                addAlias(aliases, "Web접속", "02");
                addAlias(aliases, "웹접속", "02");
                addAlias(aliases, "API연계", "03");
                addAlias(aliases, "API연동", "03");
            }
        }

        private static void addAlias(Map<String, String> aliases, String label, String code) {
            if (aliases.containsValue(code)) add(aliases, label, code);
        }

        String resolve(String group, String raw, int excelRow, String label) {
            if (raw == null || raw.isBlank()) {
                if (CommonCodeGroups.DFR_CLE.equals(group)) return CodeDefaults.NOT_APPLICABLE;
                throw new IllegalArgumentException(
                        "엑셀 " + excelRow + "행의 " + label + "이(가) 비어 있습니다.");
            }
            String value = byGroup.getOrDefault(group, Map.of()).get(normalize(raw));
            if (value == null) {
                throw new IllegalArgumentException(
                        "엑셀 " + excelRow + "행의 " + label + " 공통코드를 찾을 수 없습니다: " + raw);
            }
            return value;
        }

        String resolveBusinessType(String raw, int excelRow) {
            if (raw == null || raw.isBlank()) return CodeDefaults.NOT_APPLICABLE;
            String normalized = raw.replace(" ", "").trim();
            if (normalized.startsWith("유지") || normalized.equals("해지")) normalized = "계속";
            return resolve(CommonCodeGroups.ABUS, normalized, excelRow, "구분");
        }

        private static void add(Map<String, String> aliases, String label, String code) {
            if (label != null && !label.isBlank()) aliases.putIfAbsent(normalize(label), code);
        }

        private static String normalize(String value) {
            String normalized = value.trim().replaceAll("[\\s()（）]", "").toLowerCase();
            return normalized.matches("블룸버그\\*+") ? "블룸버그" : normalized;
        }
    }
}
