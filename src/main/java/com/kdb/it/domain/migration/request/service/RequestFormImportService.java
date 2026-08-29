package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterContext;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.request.service.adapter.FormSheetAdapter;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 편성요청서 반입 배치를 진행합니다.
 *
 * <p><b>이 클래스에 트랜잭션을 걸지 않습니다.</b> 반영의 원자 단위는 {@link RequestFormFileImporter}의 `REQUIRES_NEW`
 * 트랜잭션이며, 여기에 트랜잭션을 걸면 바깥 롤백이 안쪽 커밋과 어긋나 "정상 파일만 반영"이 성립하지 않습니다.
 *
 * <p>조직·비목 인덱스는 배치 시작에 한 번만 만들어 모든 파일이 공유합니다. 파일마다 만들면 수백 회 전량 조회가 됩니다.
 */
@Service
@Slf4j
public class RequestFormImportService {

    private final WorkbookReader workbookReader;
    private final OrgIdentityResolver orgIdentityResolver;
    private final IoeHierarchyIndex ioeHierarchyIndex;
    private final RequestFormFileImporter fileImporter;
    private final RequestFormSourceFileArchiver sourceFileArchiver;
    private final List<FormSheetAdapter> adapters;
    private final int maxFilesPerBatch;

    /** 파일 처리 결과와 그 처리에 실제 적용한 검증 부서코드를 함께 유지합니다. */
    private record ProcessedFile(RequestFormDto.FileResult result, String effectiveDeptCode) {}

    public RequestFormImportService(
            WorkbookReader workbookReader,
            OrgIdentityResolver orgIdentityResolver,
            IoeHierarchyIndex ioeHierarchyIndex,
            RequestFormFileImporter fileImporter,
            RequestFormSourceFileArchiver sourceFileArchiver,
            List<FormSheetAdapter> adapters,
            @Value("${app.migration.request.max-files-per-batch}") int maxFilesPerBatch) {
        this.workbookReader = workbookReader;
        this.orgIdentityResolver = orgIdentityResolver;
        this.ioeHierarchyIndex = ioeHierarchyIndex;
        this.fileImporter = fileImporter;
        this.sourceFileArchiver = sourceFileArchiver;
        this.adapters = adapters;
        this.maxFilesPerBatch = maxFilesPerBatch;
    }

    /**
     * 배치 1회를 처리합니다.
     *
     * <p>commit 경로에서는 원장 반영이 끝난 뒤 반입 원본 파일을 공통첨부파일에 보관합니다({@link RequestFormSourceFileArchiver}).
     * 보관 실패는 반입 결과에 영향을 주지 않습니다.
     *
     * @param files 업로드 파일. `manifest.entries`와 순서로 짝지어집니다
     * @param manifest 예산연도·파일별 부가 정보·보정값
     * @param actorEno 업로드 사용자 사번
     * @param dryRun true면 검증만 하고 원장을 만들지 않습니다
     * @return 파일별 결과와 배치 요약
     * @throws IllegalArgumentException 파일 수가 상한을 넘거나 manifest 항목 수와 다른 경우
     */
    public RequestFormDto.ImportResponse importBatch(
            List<MultipartFile> files,
            RequestFormDto.ImportManifest manifest,
            String actorEno,
            boolean dryRun) {
        if (files.size() > maxFilesPerBatch) {
            throw new IllegalArgumentException(
                    "한 번에 보낼 수 있는 파일은 %d개까지입니다.".formatted(maxFilesPerBatch));
        }
        if (files.size() != manifest.entries().size()) {
            throw new IllegalArgumentException("파일 수와 manifest 항목 수가 다릅니다.");
        }

        OrgIdentityResolver.Index orgIndex = orgIdentityResolver.snapshot();
        IoeHierarchyIndex.Snapshot ioeIndex = ioeHierarchyIndex.snapshot();
        Map<String, Map<String, String>> overridesByFile = groupOverrides(manifest);
        Map<String, Integer> missingRequestRepresentatives =
                missingRequestRepresentatives(files, manifest.entries());

        List<RequestFormDto.FileResult> results = new ArrayList<>();
        List<RequestFormSourceFileArchiver.ArchivePlanItem> archivePlan = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            RequestFormDto.FileEntry entry = manifest.entries().get(i);
            MultipartFile file = files.get(i);
            String archiveGroupKey = RequestFormArchiveGroup.keyOf(entry.fileKey());
            boolean effectiveArchiveOnly =
                    entry.archiveOnly()
                            || !RequestFormParseTarget.isTarget(file.getOriginalFilename());
            if (effectiveArchiveOnly) {
                String deptCode = resolveDepartmentCode(entry, orgIndex);
                archivePlan.add(
                        new RequestFormSourceFileArchiver.ArchivePlanItem(
                                file, entry.fileKey(), archiveGroupKey, deptCode, null));
                if (Integer.valueOf(i).equals(missingRequestRepresentatives.get(archiveGroupKey))) {
                    results.add(missingRequestWorkbook(entry));
                }
                continue;
            }
            ProcessedFile processed =
                    processFile(
                            file,
                            entry,
                            manifest.bseYy(),
                            orgIndex,
                            ioeIndex,
                            overridesByFile.getOrDefault(entry.fileKey(), Map.of()),
                            actorEno,
                            dryRun);
            results.add(processed.result());
            archivePlan.add(
                    new RequestFormSourceFileArchiver.ArchivePlanItem(
                            file,
                            entry.fileKey(),
                            archiveGroupKey,
                            processed.effectiveDeptCode(),
                            processed.result()));
        }
        // 원장 반영(파일별 REQUIRES_NEW)이 모두 끝난 뒤에 보관한다. 순서를 뒤집으면 첨부 실패가
        // 정상 반입을 통째로 되돌린다. 보관은 예외를 던지지 않으므로 여기서 감싸지 않는다.
        List<String> archiveFailedFileKeys =
                dryRun ? List.of() : sourceFileArchiver.archive(archivePlan);
        return new RequestFormDto.ImportResponse(
                dryRun,
                withArchiveFailures(summarize(files.size(), results), archiveFailedFileKeys),
                List.copyOf(results));
    }

    /** 요청서 파싱 대상이 하나도 없는 원본 보관 그룹별 대표 파일 인덱스를 찾습니다. */
    private Map<String, Integer> missingRequestRepresentatives(
            List<MultipartFile> files, List<RequestFormDto.FileEntry> entries) {
        Map<String, Integer> representatives = new LinkedHashMap<>();
        Map<String, Boolean> hasRequestWorkbook = new HashMap<>();
        for (int i = 0; i < files.size(); i++) {
            RequestFormDto.FileEntry entry = entries.get(i);
            String groupKey = RequestFormArchiveGroup.keyOf(entry.fileKey());
            representatives.putIfAbsent(groupKey, i);
            boolean parseTarget =
                    !entry.archiveOnly()
                            && RequestFormParseTarget.isTarget(files.get(i).getOriginalFilename());
            hasRequestWorkbook.merge(groupKey, parseTarget, Boolean::logicalOr);
        }
        hasRequestWorkbook.forEach(
                (groupKey, hasRequest) -> {
                    if (hasRequest) representatives.remove(groupKey);
                });
        return representatives;
    }

    private String resolveDepartmentCode(
            RequestFormDto.FileEntry entry, OrgIdentityResolver.Index orgIndex) {
        if (entry.deptCodeOverride() != null) return entry.deptCodeOverride();
        return orgIndex.resolveOrgFolder(entry.deptName()).code();
    }

    private ProcessedFile processFile(
            MultipartFile file,
            RequestFormDto.FileEntry entry,
            String bseYy,
            OrgIdentityResolver.Index orgIndex,
            IoeHierarchyIndex.Snapshot ioeIndex,
            Map<String, String> overrides,
            String actorEno,
            boolean dryRun) {
        Workbook workbook = null;
        try {
            workbook = workbookReader.open(readBytes(file), entry.fileKey());
            List<Map<FormSheetKind, Sheet>> sheetGroups = workbookReader.classifyGroups(workbook);
            if (sheetGroups.isEmpty()) {
                return new ProcessedFile(skipped(entry), resolveDepartmentCode(entry, orgIndex));
            }

            // 폴더명은 `부서명(부서코드)` 표기이므로 이름이 아니라 병기된 코드를 우선 기준으로 삼는다
            OrgIdentityResolver.Resolution deptResolution =
                    orgIndex.resolveOrgFolder(entry.deptName());
            String deptCode =
                    entry.deptCodeOverride() != null
                            ? entry.deptCodeOverride()
                            : deptResolution.code();
            if (deptCode == null) {
                return new ProcessedFile(unresolvedDepartment(entry, deptResolution), null);
            }

            FormAdapterOutput output = FormAdapterOutput.empty();
            for (Map<FormSheetKind, Sheet> sheets : sheetGroups) {
                FormAdapterContext context =
                        new FormAdapterContext(
                                sheets,
                                bseYy,
                                entry,
                                deptCode,
                                deptResolution.label(),
                                orgIndex,
                                ioeIndex,
                                overrides,
                                actorEno);
                for (FormSheetAdapter adapter : adapters) {
                    if (sheets.containsKey(adapter.trigger())) {
                        output = output.merge(adapter.adapt(context));
                    }
                }
            }
            FormResponsibleFallback.apply(output);

            RequestFormDto.FileResult result =
                    dryRun
                            ? fileImporter.preview(output, entry, bseYy)
                            : fileImporter.apply(output, entry, bseYy, actorEno);
            return new ProcessedFile(result, deptCode);
        } catch (WorkbookReader.WorkbookOpenException e) {
            return new ProcessedFile(
                    failed(entry, RequestFormDiagnosticCode.FILE_UNREADABLE, e.getMessage()), null);
        } catch (IllegalStateException e) {
            log.warn("편성요청서 반입 실패: fileKey={}", entry.fileKey(), e);
            String reason =
                    e.getMessage() == null || e.getMessage().isBlank()
                            ? "알 수 없는 처리 오류"
                            : e.getMessage();
            return new ProcessedFile(
                    failed(
                            entry,
                            RequestFormDiagnosticCode.FILE_UNREADABLE,
                            "파일 처리 중 오류가 발생했습니다: " + reason),
                    null);
        } catch (RuntimeException e) {
            // 파일 하나의 예외가 배치를 무너뜨리지 않게 잡는다. 파일명만 남기고 내용은 로그에 남기지 않는다.
            log.warn("편성요청서 반입 실패: fileKey={}", entry.fileKey(), e);
            return new ProcessedFile(
                    failed(
                            entry,
                            RequestFormDiagnosticCode.FILE_UNREADABLE,
                            "파일을 처리하지 못했습니다. 양식을 확인해 주세요."),
                    null);
        } finally {
            closeQuietly(workbook);
        }
    }

    private Map<String, Map<String, String>> groupOverrides(
            RequestFormDto.ImportManifest manifest) {
        Map<String, Map<String, String>> grouped = new HashMap<>();
        for (RequestFormDto.CellOverride override : manifest.overrides()) {
            grouped.computeIfAbsent(override.fileKey(), key -> new LinkedHashMap<>())
                    .put(
                            FormAdapterContext.overrideKey(
                                    override.sheet(), override.excelRow(), override.field()),
                            override.value());
        }
        return grouped;
    }

    private RequestFormDto.ImportSummary summarize(
            int totalFiles, List<RequestFormDto.FileResult> results) {
        int applied = 0;
        int blocked = 0;
        RequestFormDto.RecordCounts created = RequestFormDto.RecordCounts.zero();
        for (RequestFormDto.FileResult result : results) {
            if (result.status() == RequestFormDto.FileStatus.BLOCKED) blocked++;
            if (result.status() != RequestFormDto.FileStatus.APPLIED) continue;
            // 요약은 실제로 반영되는 것만 센다. 파일별 건수는 차단된 파일도 담고 있어 그대로 더하면
            // "반영 0건 / 사업 12건" 같은 모순이 생긴다.
            applied++;
            created = created.plus(result.counts());
        }
        return new RequestFormDto.ImportSummary(totalFiles, applied, blocked, created);
    }

    /** 원장 반영 요약에 원본 보관 실패 파일 목록을 결합합니다. */
    private RequestFormDto.ImportSummary withArchiveFailures(
            RequestFormDto.ImportSummary summary, List<String> archiveFailedFileKeys) {
        return new RequestFormDto.ImportSummary(
                summary.totalFiles(),
                summary.appliedFiles(),
                summary.blockedFiles(),
                summary.created(),
                archiveFailedFileKeys);
    }

    private RequestFormDto.FileResult failed(
            RequestFormDto.FileEntry entry, RequestFormDiagnosticCode code, String message) {
        return new RequestFormDto.FileResult(
                entry.fileKey(),
                entry.deptName(),
                RequestFormDto.FileStatus.FAILED,
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null, null, null, code, message, List.of())),
                List.of(),
                RequestFormDto.RecordCounts.zero(),
                null);
    }

    /** 사업 원본 그룹에 요청서 Excel이 없을 때 보관 대표 파일에 남기는 비차단 경고입니다. */
    private RequestFormDto.FileResult missingRequestWorkbook(RequestFormDto.FileEntry entry) {
        return new RequestFormDto.FileResult(
                entry.fileKey(),
                entry.deptName(),
                RequestFormDto.FileStatus.SKIPPED,
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null,
                                null,
                                "requestFormFile",
                                RequestFormDiagnosticCode.SHEET_NOT_FOUND,
                                "사업 폴더에 파일명에 '요청서'가 포함된 Excel 파일이 없습니다.",
                                List.of())),
                List.of(),
                RequestFormDto.RecordCounts.zero(),
                null);
    }

    /**
     * 반입 대상이 아닌 파일의 결과를 만듭니다.
     *
     * <p>{@link #failed}와 달리 실패가 아닙니다 — 열리기는 했고 편성요청서가 아니었을 뿐입니다. 요약의 반영·차단 건수 어디에도 세지 않으므로 {@code
     * summarize()}는 손대지 않습니다.
     *
     * @param entry 파일별 부가 정보
     * @return 상태 {@code SKIPPED}, 진단 1건, 생성 목록·건수는 빈 값
     */
    private RequestFormDto.FileResult skipped(RequestFormDto.FileEntry entry) {
        return new RequestFormDto.FileResult(
                entry.fileKey(),
                entry.deptName(),
                RequestFormDto.FileStatus.SKIPPED,
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null,
                                null,
                                null,
                                RequestFormDiagnosticCode.SHEET_NOT_FOUND,
                                "인식할 수 있는 편성요청서 시트가 없습니다. 반입 대상이 아닌 파일로 보고 건너뜁니다.",
                                List.of())),
                List.of(),
                RequestFormDto.RecordCounts.zero(),
                null);
    }

    private RequestFormDto.FileResult unresolvedDepartment(
            RequestFormDto.FileEntry entry, OrgIdentityResolver.Resolution resolution) {
        RequestFormDiagnosticCode code =
                resolution.isAmbiguous()
                        ? RequestFormDiagnosticCode.ORG_AMBIGUOUS
                        : RequestFormDiagnosticCode.ORG_UNRESOLVED;
        return new RequestFormDto.FileResult(
                entry.fileKey(),
                entry.deptName(),
                RequestFormDto.FileStatus.BLOCKED,
                List.of(
                        RequestFormDto.FormDiagnostic.of(
                                null,
                                null,
                                "deptCodeOverride",
                                code,
                                "폴더명 `%s`에 해당하는 부서를 확정하지 못했습니다.".formatted(entry.deptName()),
                                resolution.candidates())),
                List.of(),
                RequestFormDto.RecordCounts.zero(),
                null);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void closeQuietly(Workbook workbook) {
        if (workbook == null) return;
        try {
            workbook.close();
        } catch (IOException e) {
            log.debug("워크북을 닫지 못했습니다", e);
        }
    }
}
