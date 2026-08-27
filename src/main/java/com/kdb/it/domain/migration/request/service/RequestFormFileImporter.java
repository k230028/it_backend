package com.kdb.it.domain.migration.request.service;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.ProjectAmounts;
import com.kdb.it.domain.migration.service.MigrationApprovalStamper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편성요청서 파일 1건을 반영합니다.
 *
 * <p>이 클래스가 <b>트랜잭션 원자 단위</b>입니다. `REQUIRES_NEW`로 파일마다 독립 트랜잭션을 열어, 한 파일이 실패해도 같은 배치의 다른 파일은 커밋되게
 * 합니다. 파일 안의 업무 영역은 독립적으로 검증해 BLOCKER가 난 정보화사업·경상사업·일반관리비 영역만 제외합니다.
 *
 * <p>원장은 기존 서비스의 이관 전용 오버로드로 만듭니다. 이관은 편성 시즌 밖에서도 실행되어야 해 기간 검증을 건너뛰지만, 채번·조직명 스냅샷·감사로그는 그대로 타야 하므로
 * 새 INSERT 경로를 만들지 않습니다.
 *
 * <p>편성행({@code BBUGTM})은 만들지 않습니다. {@code BudgetRateApplicationService.applyItemRates}가 연도 전량을
 * 논리삭제한 뒤 재삽입하는 구조라, 파일마다 부르면 앞서 반입한 편성행이 전부 사라집니다. 편성은 반입을 마친 뒤 예산작업 화면에서 한 번에 적용합니다.
 *
 * <p>정보화사업의 당해·예정·총소요금액은 {@code createProject}가 품목 AMT·MPL을 중앙 계산한 값을 정본으로 기록합니다. 1-1 선언값에서는 품목으로 알
 * 수 없는 지급금액(DFR)만 생성 요청에 전달하며, 생성 뒤 선언 total/MPL로 스냅샷을 덮어쓰지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class RequestFormFileImporter {

    /** 이관 결재 받이의 제목 접두어. 정상 결재와 구분되도록 제목에 이관임을 남깁니다. */
    private static final String APPROVAL_TITLE_PREFIX = "[편성요청서 반입]";

    /** 결재 연결의 원천 일련번호. 반입은 사업·전산업무비마다 1번 행만 만듭니다. */
    private static final int SOURCE_SEQUENCE = 1;

    /** 원천테이블명: 정보화사업·경상사업 마스터. */
    private static final String TABLE_PROJECT = "BPROJM";

    /** 원천테이블명: 전산업무비 마스터. */
    private static final String TABLE_COST = "BCOSTM";

    /** 상시운영여부. 경상사업이면 `Y`이고 정보화사업과 같은 테이블에 들어갑니다. */
    private static final String RECURRING_FLAG = "Y";

    private final CostService costService;
    private final ProjectService projectService;
    private final MigrationApprovalStamper approvalStamper;
    private final RequestFormValidator validator;

    /**
     * 파일 1건을 원장에 반영합니다.
     *
     * <p>독립 트랜잭션에서 실행됩니다. BLOCKER가 남은 업무 영역은 제외하고 같은 파일의 정상 영역은 반입합니다. 파일 단위 BLOCKER이거나 반입 가능한 영역이
     * 하나도 없을 때만 파일을 차단합니다. 제외 진단은 응답에 그대로 남겨 화면에서 원인을 확인할 수 있게 합니다.
     *
     * @param output 어댑터가 조립한 생성 요청
     * @param entry 파일별 부가 정보
     * @param bseYy 예산연도 4자리
     * @param actorEno 업로드 사용자 사번
     * @return 파일 처리 결과
     * @throws org.springframework.dao.DataAccessException 원장 저장이 DB 수준에서 실패한 경우. 이 트랜잭션만 롤백됩니다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RequestFormDto.FileResult apply(
            FormAdapterOutput output,
            RequestFormDto.FileEntry entry,
            String bseYy,
            String actorEno) {
        List<RequestFormDto.FormDiagnostic> diagnostics =
                new ArrayList<>(allDiagnostics(output, bseYy));
        prepareNameOnlyImport(output, diagnostics);
        FormAdapterOutput applicable = withoutBlockedSections(output, diagnostics);
        if (RequestFormValidator.hasBlocker(diagnostics) && hasNoRecords(applicable)) {
            return result(entry, RequestFormDto.FileStatus.BLOCKED, diagnostics, List.of(), output);
        }
        FormAdapterOutput importTarget = validator.withoutDuplicateProjects(applicable, bseYy);

        List<RequestFormDto.CreatedRecord> created = new ArrayList<>();
        List<ProjectDto.CreateRequest> projects = importTarget.projects();
        for (int index = 0; index < projects.size(); index++) {
            ProjectDto.CreateRequest project = projects.get(index);
            String tlrNm = project.getTlrUsid();
            String usrNm = project.getUsid();
            clearProjectIds(project);
            project.setBseYy(bseYy);
            ProjectAmounts amounts = importTarget.projectAmounts().get(index);
            if (amounts.isPresent()) {
                project.setDfrAmt(amounts.dfrAmt());
            }
            String abusMngNo = projectService.createProject(project, true);
            projectService.assignImportedPersonNames(abusMngNo, tlrNm, usrNm);
            if (amounts.isPresent()) {
                projectService.assignDeclaredAmounts(
                        abusMngNo, amounts.totRqmAmt(), amounts.mplAmt(), amounts.dfrAmt());
            }
            String apfMngNo = stamp(TABLE_PROJECT, abusMngNo, project.getAbusNm(), actorEno, bseYy);
            created.add(
                    new RequestFormDto.CreatedRecord(
                            TABLE_PROJECT, abusMngNo, project.getAbusNm(), apfMngNo));
        }
        for (CostDto.CreateRequest cost : importTarget.costs()) {
            String cgprNm = cost.getCgprId();
            cost.setCgprId(null);
            cost.setBseYy(bseYy);
            String costBgNo = costService.createCost(cost, true);
            costService.assignImportedPersonName(costBgNo, cgprNm);
            String apfMngNo = stamp(TABLE_COST, costBgNo, cost.getCttNm(), actorEno, bseYy);
            created.add(
                    new RequestFormDto.CreatedRecord(
                            TABLE_COST, costBgNo, cost.getCttNm(), apfMngNo));
        }

        return result(
                entry,
                RequestFormDto.FileStatus.APPLIED,
                List.copyOf(diagnostics),
                List.copyOf(created),
                output);
    }

    /**
     * 파일 1건을 검증만 합니다. 원장을 만들지 않습니다.
     *
     * @param output 어댑터가 조립한 생성 요청
     * @param entry 파일별 부가 정보
     * @param bseYy 예산연도 4자리
     * @return 파일 처리 결과. `created`는 항상 빈 목록
     */
    public RequestFormDto.FileResult preview(
            FormAdapterOutput output, RequestFormDto.FileEntry entry, String bseYy) {
        List<RequestFormDto.FormDiagnostic> diagnostics =
                new ArrayList<>(allDiagnostics(output, bseYy));
        prepareNameOnlyImport(output, diagnostics);
        clearIds(output);
        FormAdapterOutput applicable = withoutBlockedSections(output, diagnostics);
        RequestFormDto.FileStatus status =
                RequestFormValidator.hasBlocker(diagnostics) && hasNoRecords(applicable)
                        ? RequestFormDto.FileStatus.BLOCKED
                        : RequestFormDto.FileStatus.APPLIED;
        return result(entry, status, List.copyOf(diagnostics), List.of(), output);
    }

    /** 양식의 담당자 표기는 이름 스냅샷으로 옮기고 사번 컬럼에는 넣지 않습니다. */
    private void prepareNameOnlyImport(
            FormAdapterOutput output, List<RequestFormDto.FormDiagnostic> diagnostics) {
        for (ProjectDto.CreateRequest project : output.projects()) {
            warnDroppedItPerson(project.getDvmTlrUsid(), "dvmTlrUsid", "IT담당팀장", diagnostics);
            warnDroppedItPerson(project.getDvmUsid(), "dvmUsid", "IT담당자", diagnostics);
        }
    }

    private void clearIds(FormAdapterOutput output) {
        output.projects().forEach(this::clearProjectIds);
        output.costs().forEach(cost -> cost.setCgprId(null));
    }

    private void clearProjectIds(ProjectDto.CreateRequest project) {
        project.setTlrUsid(null);
        project.setUsid(null);
        project.setDvmTlrUsid(null);
        project.setDvmUsid(null);
    }

    private void warnDroppedItPerson(
            String name,
            String field,
            String label,
            List<RequestFormDto.FormDiagnostic> diagnostics) {
        if (name == null || name.isBlank()) return;
        diagnostics.add(
                RequestFormDto.FormDiagnostic.about(
                        FormSheetKind.CAPITAL_OVERVIEW,
                        null,
                        field,
                        name,
                        com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode
                                .SUBSTITUTE_DROPPED,
                        "%s `%s`는 대응하는 이름 컬럼이 없어 ID 컬럼에 저장하지 않습니다.".formatted(label, name),
                        List.of()));
    }

    /**
     * BLOCKER가 속한 시트 영역만 저장 대상에서 제외합니다.
     *
     * <p>진단 목록은 건드리지 않아 제외 사유가 화면의 진단 컬럼에 그대로 남습니다. 시트가 없는 파일 단위 BLOCKER는 어느 원장도 안전하게 만들 수 없으므로 전체를
     * 제외합니다.
     */
    private FormAdapterOutput withoutBlockedSections(
            FormAdapterOutput output, List<RequestFormDto.FormDiagnostic> diagnostics) {
        boolean globalBlocked = hasBlockerFor(diagnostics, null);
        boolean capitalBlocked =
                globalBlocked
                        || hasBlockerFor(diagnostics, FormSheetKind.CAPITAL_OVERVIEW)
                        || hasBlockerFor(diagnostics, FormSheetKind.CAPITAL_RESOURCE);
        boolean recurringBlocked =
                globalBlocked || hasBlockerFor(diagnostics, FormSheetKind.RECURRING);
        boolean costsBlocked =
                globalBlocked || hasBlockerFor(diagnostics, FormSheetKind.GENERAL_EXPENSE);

        List<ProjectDto.CreateRequest> projects = new ArrayList<>();
        List<ProjectAmounts> amounts = new ArrayList<>();
        for (int index = 0; index < output.projects().size(); index++) {
            ProjectDto.CreateRequest project = output.projects().get(index);
            boolean recurring = RECURRING_FLAG.equals(project.getOdnYn());
            if ((recurring && recurringBlocked) || (!recurring && capitalBlocked)) continue;
            projects.add(project);
            amounts.add(output.projectAmounts().get(index));
        }
        return new FormAdapterOutput(
                List.copyOf(projects),
                costsBlocked ? List.of() : output.costs(),
                output.diagnostics(),
                output.suggestedGeneralExpenseUnit(),
                List.copyOf(amounts));
    }

    private static boolean hasBlockerFor(
            List<RequestFormDto.FormDiagnostic> diagnostics, FormSheetKind sheet) {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.code().blocks() && diagnostic.sheet() == sheet);
    }

    private static boolean hasNoRecords(FormAdapterOutput output) {
        return output.projects().isEmpty() && output.costs().isEmpty();
    }

    private String stamp(String table, String key, String label, String actorEno, String bseYy) {
        return approvalStamper.stamp(
                table,
                key,
                SOURCE_SEQUENCE,
                "%s %s".formatted(APPROVAL_TITLE_PREFIX, label == null ? key : label),
                actorEno,
                bseYy,
                ApprovalStatus.MANUAL);
    }

    private List<RequestFormDto.FormDiagnostic> allDiagnostics(
            FormAdapterOutput output, String bseYy) {
        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>(output.diagnostics());
        diagnostics.addAll(validator.validate(output, bseYy));
        return List.copyOf(diagnostics);
    }

    private RequestFormDto.FileResult result(
            RequestFormDto.FileEntry entry,
            RequestFormDto.FileStatus status,
            List<RequestFormDto.FormDiagnostic> diagnostics,
            List<RequestFormDto.CreatedRecord> created,
            FormAdapterOutput output) {
        return new RequestFormDto.FileResult(
                entry.fileKey(),
                entry.deptName(),
                status,
                diagnostics,
                created,
                countOf(output),
                output.suggestedGeneralExpenseUnit());
    }

    /**
     * 조립 결과의 원장 건수를 종류별로 셉니다.
     *
     * <p>{@code created}가 아니라 조립 결과를 세는 이유는 dry-run과 <b>차단된 파일에도</b> 같은 건수를 채우기 위해서입니다. 사전검증에서 알고
     * 싶은 것은 "차단을 풀면 무엇이 몇 건 생기는가"인데 생성된 원장 목록은 두 경우 모두 비어 있어, 그 목록을 세면 화면이 늘 0건으로 보입니다. 반영 경로에서는 이
     * 조립 결과가 그대로 원장이 되므로 두 값이 일치합니다.
     */
    private RequestFormDto.RecordCounts countOf(FormAdapterOutput output) {
        int capital = 0;
        int recurring = 0;
        for (ProjectDto.CreateRequest project : output.projects()) {
            if (RECURRING_FLAG.equals(project.getOdnYn())) recurring++;
            else capital++;
        }
        return new RequestFormDto.RecordCounts(capital, recurring, output.costs().size());
    }
}
