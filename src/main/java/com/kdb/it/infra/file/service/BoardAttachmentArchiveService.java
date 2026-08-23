package com.kdb.it.infra.file.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.dto.FileDto;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 읽기 권한이 확인된 공통게시판 첨부파일을 ZIP으로 내보냅니다. */
@Service
@RequiredArgsConstructor
public class BoardAttachmentArchiveService {

    private static final String BOARD_FILE_KIND = "공통게시판";

    private final FileService fileService;

    /**
     * 게시물 전체 또는 선택 첨부파일을 호출자가 소유한 출력 스트림에 ZIP으로 씁니다.
     *
     * <p>파일 목록은 공통 파일 읽기 권한 경계를 통과한 결과만 사용하며, 호출자가 소유한 출력 스트림은 닫지 않습니다.
     *
     * @param nacMngNo 게시물 관리번호
     * @param fileIds 선택 파일 ID. 비어 있으면 접근 가능한 전체 첨부
     * @param userDetails 인증 사용자
     * @param output ZIP을 받을 호출자 소유 출력 스트림
     * @throws CustomGeneralException 요청이 잘못됐거나 선택 파일이 접근 가능한 결과에 없거나 ZIP 생성에 실패한 경우
     */
    public void writeArchive(
            String nacMngNo,
            List<String> fileIds,
            CustomUserDetails userDetails,
            OutputStream output) {
        validateRequest(nacMngNo, fileIds);

        List<FileDto.Response> authorizedFiles =
                fileService.getFiles(
                        FileDto.SearchCondition.builder()
                                .pkColNm(BOARD_FILE_KIND)
                                .pkCone(nacMngNo)
                                .build(),
                        userDetails);
        if (authorizedFiles.isEmpty()) {
            throw new CustomGeneralException("다운로드할 수 있는 게시판 첨부파일이 없습니다.");
        }

        Set<String> selection = resolveSelection(fileIds, authorizedFiles);
        List<ArchiveFile> archiveFiles = preflight(authorizedFiles, selection);
        writeZip(archiveFiles, output);
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
        try (ZipOutputStream zip = new ZipOutputStream(new NonClosingOutputStream(output))) {
            for (ArchiveFile archiveFile : archiveFiles) {
                FileService.FileDownloadResult download =
                        fileService.downloadFile(archiveFile.fileId());
                zip.putNextEntry(new ZipEntry(archiveFile.entryName()));
                try (InputStream input = download.resource().getInputStream()) {
                    input.transferTo(zip);
                } finally {
                    zip.closeEntry();
                }
            }
        } catch (IOException error) {
            throw new CustomGeneralException("게시판 첨부파일 ZIP 생성에 실패했습니다.", error);
        }
    }

    private record ArchiveFile(String fileId, String entryName) {}

    private static final class NonClosingOutputStream extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
