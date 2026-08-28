package com.kdb.it.infra.file.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 읽기 권한이 확인된 공통게시판 첨부파일을 ZIP으로 내보냅니다. */
@Service
@RequiredArgsConstructor
public class BoardAttachmentArchiveService {

    private static final String BOARD_FILE_KIND = "공통게시판";

    private final FileService fileService;

    /**
     * 요청을 검증하고 읽기 권한을 통과한 대상 목록을 확정합니다.
     *
     * <p><b>응답 헤더를 확정하기 전에 호출해야 합니다.</b> 이 단계에서 던지는 예외만 공통 예외 응답 계약을 탈 수 있습니다 — {@link
     * #writeArchive(ArchivePlan, OutputStream)}는 이미 {@code 200 OK}가 커밋된 뒤에 실행되므로 거기서 실패하면 사용자가 성공으로
     * 보고 절단된 ZIP을 받습니다(BE-67).
     *
     * @param nacMngNo 게시물 관리번호
     * @param fileIds 선택 파일 ID. {@code null}이면 접근 가능한 전체 첨부
     * @param userDetails 인증 사용자
     * @return ZIP에 담을 파일과 엔트리명이 확정된 계획
     * @throws CustomGeneralException 요청이 잘못됐거나 선택 파일이 접근 가능한 결과에 없는 경우
     */
    public ArchivePlan prepareArchive(
            String nacMngNo, List<String> fileIds, CustomUserDetails userDetails) {
        validateRequest(nacMngNo, fileIds);

        List<FileDto.Response> authorizedFiles =
                fileService.getFiles(
                        FileDto.SearchCondition.builder()
                                .apgFlKdNm(BOARD_FILE_KIND)
                                .apgFlLnkCtzNm(nacMngNo)
                                .build(),
                        userDetails);
        if (authorizedFiles.isEmpty()) {
            throw new CustomGeneralException("다운로드할 수 있는 게시판 첨부파일이 없습니다.");
        }

        Set<String> selection = resolveSelection(fileIds, authorizedFiles);
        return new ArchivePlan(preflight(authorizedFiles, selection));
    }

    /**
     * 확정된 계획의 바이트만 호출자가 소유한 출력 스트림에 ZIP으로 씁니다.
     *
     * <p>호출자가 소유한 출력 스트림은 닫지 않습니다. 검증·권한 판정은 {@link #prepareArchive}가 이미 끝냈으므로 여기서는 저장소 읽기 실패만
     * 남습니다.
     *
     * @param plan {@link #prepareArchive}가 확정한 계획
     * @param output ZIP을 받을 호출자 소유 출력 스트림
     * @throws CustomGeneralException ZIP 생성에 실패한 경우
     */
    public void writeArchive(ArchivePlan plan, OutputStream output) {
        writeZip(plan.files(), output);
    }

    private void validateRequest(String nacMngNo, List<String> fileIds) {
        if (!StringUtils.hasText(nacMngNo)) {
            throw new CustomGeneralException("게시물 관리번호는 필수입니다.");
        }
        if (fileIds == null) return;
        if (fileIds.isEmpty()) {
            throw new CustomGeneralException("선택 파일은 한 건 이상이어야 합니다.");
        }
        if (fileIds.stream().anyMatch(fileId -> !StringUtils.hasText(fileId))) {
            throw new CustomGeneralException("파일매핑ID는 공백일 수 없습니다.");
        }
        if (new HashSet<>(fileIds).size() != fileIds.size()) {
            throw new CustomGeneralException("중복된 파일매핑ID를 선택할 수 없습니다.");
        }
    }

    private Set<String> resolveSelection(
            List<String> requestedIds, List<FileDto.Response> authorizedFiles) {
        if (requestedIds == null) return null;

        Set<String> authorizedIds = new HashSet<>();
        authorizedFiles.forEach(file -> authorizedIds.add(file.getFlMpnId()));
        if (!authorizedIds.containsAll(requestedIds)) {
            throw new CustomGeneralException("선택한 게시판 첨부파일을 다운로드할 수 없습니다.");
        }
        return Set.copyOf(requestedIds);
    }

    private List<ArchiveFile> preflight(
            List<FileDto.Response> authorizedFiles, Set<String> selection) {
        List<ArchiveFile> archiveFiles = new ArrayList<>();
        Set<String> usedEntryNames = new LinkedHashSet<>();
        for (FileDto.Response file : authorizedFiles) {
            if (selection != null && !selection.contains(file.getFlMpnId())) continue;
            String safeName = safeEntryName(file.getFlNm(), file.getFlMpnId());
            archiveFiles.add(
                    new ArchiveFile(file.getFlMpnId(), uniqueEntryName(safeName, usedEntryNames)));
        }
        if (archiveFiles.isEmpty()) {
            throw new CustomGeneralException("다운로드할 수 있는 게시판 첨부파일이 없습니다.");
        }
        return archiveFiles;
    }

    private String safeEntryName(String fileName, String fallback) {
        if (!StringUtils.hasText(fileName)) return fallback;
        StringBuilder safe = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char character = fileName.charAt(i);
            safe.append(
                    character < 32 || character == 127 || character == '/' || character == '\\'
                            ? '_'
                            : character);
        }
        String result = safe.toString();
        return ".".equals(result) || "..".equals(result) ? result.replace('.', '_') : result;
    }

    private String uniqueEntryName(String requestedName, Set<String> usedNames) {
        if (usedNames.add(requestedName)) return requestedName;

        int dotIndex = requestedName.lastIndexOf('.');
        boolean hasExtension = dotIndex > 0;
        String stem = hasExtension ? requestedName.substring(0, dotIndex) : requestedName;
        String extension = hasExtension ? requestedName.substring(dotIndex) : "";
        int sequence = 2;
        String candidate;
        do {
            candidate = stem + "(" + sequence++ + ")" + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private void writeZip(List<ArchiveFile> archiveFiles, OutputStream output) {
        try (ZipOutputStream zip = new ZipOutputStream(CloseShieldOutputStream.wrap(output))) {
            for (ArchiveFile archiveFile : archiveFiles) {
                FileService.FileDownloadResult download =
                        fileService.downloadFile(archiveFile.fileId());
                zip.putNextEntry(new ZipEntry(archiveFile.entryName()));
                try (InputStream input =
                        Objects.requireNonNull(download.resource().getInputStream())) {
                    input.transferTo(zip);
                } finally {
                    zip.closeEntry();
                }
            }
        } catch (IOException error) {
            throw new CustomGeneralException("게시판 첨부파일 ZIP 생성에 실패했습니다.", error);
        }
    }

    /** 검증·권한 판정을 마친 ZIP 대상 목록 */
    public record ArchivePlan(List<ArchiveFile> files) {}

    /** ZIP에 담을 파일 한 건과 확정된 엔트리명 */
    public record ArchiveFile(String fileId, String entryName) {}
}
