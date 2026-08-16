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
    private final List<FormSheetAdapter> adapters;
    private final int maxFilesPerBatch;

    public RequestFormImportService(
            WorkbookReader workbookReader,
            OrgIdentityResolver orgIdentityResolver,
            IoeHierarchyIndex ioeHierarchyIndex,
            RequestFormFileImporter fileImporter,
            List<FormSheetAdapter> adapters,
            @Value("${app.migration.request.max-files-per-batch}") int maxFilesPerBatch) {
        this.workbookReader = workbookReader;
        this.orgIdentityResolver = orgIdentityResolver;
        this.ioeHierarchyIndex = ioeHierarchyIndex;
        this.fileImporter = fileImporter;
        this.adapters = adapters;
        this.maxFilesPerBatch = maxFilesPerBatch;
    }

    /**
     * 배치 1회를 처리합니다.
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

        List<RequestFormDto.FileResult> results = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            RequestFormDto.FileEntry entry = manifest.entries().get(i);
            results.add(
                    processFile(
                            files.get(i),
                            entry,
                            manifest.bseYy(),
                            orgIndex,
                            ioeIndex,
                            overridesByFile.getOrDefault(entry.fileKey(), Map.of()),
                            actorEno,
                            dryRun));
        }
        return new RequestFormDto.ImportResponse(
                dryRun, summarize(files.size(), results), List.copyOf(results));
    }

    private RequestFormDto.FileResult processFile(
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
            Map<FormSheetKind, Sheet> sheets = workbookReader.classify(workbook);
            if (sheets.isEmpty()) {
                return failed(
                        entry,
                        RequestFormDiagnosticCode.SHEET_NOT_FOUND,
                        "인식할 수 있는 편성요청서 시트가 없습니다.");
            }

            // 폴더명은 `부서명(부서코드)` 표기이므로 이름이 아니라 병기된 코드를 우선 기준으로 삼는다
            OrgIdentityResolver.Resolution deptResolution =
                    orgIndex.resolveOrgFolder(entry.deptName());
            String deptCode =
                    entry.deptCodeOverride() != null
                            ? entry.deptCodeOverride()
                            : deptResolution.code();
            if (deptCode == null) return unresolvedDepartment(entry, deptResolution);

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

            FormAdapterOutput output = FormAdapterOutput.empty();
            for (FormSheetAdapter adapter : adapters) {
                if (sheets.containsKey(adapter.trigger()))
                    output = output.merge(adapter.adapt(context));
            }

            return dryRun
                    ? fileImporter.preview(output, entry, bseYy)
                    : fileImporter.apply(output, entry, bseYy, actorEno);
        } catch (WorkbookReader.WorkbookOpenException e) {
            return failed(entry, RequestFormDiagnosticCode.FILE_UNREADABLE, e.getMessage());
        } catch (RuntimeException e) {
            // 파일 하나의 예외가 배치를 무너뜨리지 않게 잡는다. 파일명만 남기고 내용은 로그에 남기지 않는다.
            log.warn("편성요청서 반입 실패: fileKey={}", entry.fileKey(), e);
            return failed(
                    entry,
                    RequestFormDiagnosticCode.FILE_UNREADABLE,
                    "파일을 처리하지 못했습니다. 양식을 확인해 주세요.");
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
