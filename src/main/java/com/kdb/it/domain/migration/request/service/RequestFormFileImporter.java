package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
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
 * <p>이 클래스가 <b>반영의 원자 단위</b>입니다. `REQUIRES_NEW`로 파일마다 독립 트랜잭션을 열어, 한 파일이 실패해도 같은 배치의 다른 파일은 커밋되게
 * 합니다. 오케스트레이터에 트랜잭션을 걸면 이 경계가 무의미해지므로 {@code RequestFormImportService}는 트랜잭션을 열지 않습니다.
 *
 * <p>원장은 기존 서비스의 이관 전용 오버로드로 만듭니다. 이관은 편성 시즌 밖에서도 실행되어야 해 기간 검증을 건너뛰지만, 채번·조직명 스냅샷·감사로그는 그대로 타야 하므로
 * 새 INSERT 경로를 만들지 않습니다.
 *
 * <p>편성행({@code BBUGTM})은 만들지 않습니다. {@code BudgetRateApplicationService.applyItemRates}가 연도 전량을
 * 논리삭제한 뒤 재삽입하는 구조라, 파일마다 부르면 앞서 반입한 편성행이 전부 사라집니다. 편성은 반입을 마친 뒤 예산작업 화면에서 한 번에 적용합니다.
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

    private final CostService costService;
    private final ProjectService projectService;
    private final MigrationApprovalStamper approvalStamper;
    private final RequestFormValidator validator;

    /**
     * 파일 1건을 원장에 반영합니다.
     *
     * <p>독립 트랜잭션에서 실행됩니다. BLOCKER가 남으면 아무것도 쓰지 않고 차단 상태로 돌려줍니다 — 예외를 던지지 않는 이유는 배치의 나머지 파일을 계속 처리해야
     * 하고, 예외로 알리면 호출자가 정상 흐름과 오류 흐름을 뒤섞어 다뤄야 하기 때문입니다.
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
        List<RequestFormDto.FormDiagnostic> diagnostics = allDiagnostics(output, bseYy);
        if (RequestFormValidator.hasBlocker(diagnostics)) {
            return result(entry, RequestFormDto.FileStatus.BLOCKED, diagnostics, List.of(), output);
        }

        List<RequestFormDto.CreatedRecord> created = new ArrayList<>();
        for (ProjectDto.CreateRequest project : output.projects()) {
            project.setBseYy(bseYy);
            String abusMngNo = projectService.createProject(project, true);
            stamp(TABLE_PROJECT, abusMngNo, project.getAbusNm(), actorEno, bseYy);
            created.add(
                    new RequestFormDto.CreatedRecord(
                            TABLE_PROJECT, abusMngNo, project.getAbusNm()));
        }
        for (CostDto.CreateRequest cost : output.costs()) {
            cost.setBseYy(bseYy);
            String costBgNo = costService.createCost(cost, true);
            stamp(TABLE_COST, costBgNo, cost.getCttNm(), actorEno, bseYy);
            created.add(new RequestFormDto.CreatedRecord(TABLE_COST, costBgNo, cost.getCttNm()));
        }

        return result(
                entry,
                RequestFormDto.FileStatus.APPLIED,
                diagnostics,
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
        List<RequestFormDto.FormDiagnostic> diagnostics = allDiagnostics(output, bseYy);
        RequestFormDto.FileStatus status =
                RequestFormValidator.hasBlocker(diagnostics)
                        ? RequestFormDto.FileStatus.BLOCKED
                        : RequestFormDto.FileStatus.APPLIED;
        return result(entry, status, diagnostics, List.of(), output);
    }

    private void stamp(String table, String key, String label, String actorEno, String bseYy) {
        approvalStamper.stamp(
                table,
                key,
                SOURCE_SEQUENCE,
                "%s %s".formatted(APPROVAL_TITLE_PREFIX, label == null ? key : label),
                actorEno,
                bseYy);
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
                output.suggestedGeneralExpenseUnit());
    }
}
