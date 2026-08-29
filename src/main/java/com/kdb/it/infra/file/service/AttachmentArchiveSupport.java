package com.kdb.it.infra.file.service;

import com.kdb.it.exception.CustomGeneralException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.springframework.util.StringUtils;

/** 첨부파일 ZIP 서비스가 공유하는 선택 검증·엔트리명·스트리밍 기능입니다. */
public final class AttachmentArchiveSupport {

    private AttachmentArchiveSupport() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /** 선택 파일 ID의 공통 형식과 중복을 검증합니다. */
    public static void validateSelection(
            List<String> fileIds, String emptyMessage, String blankMessage, String duplicateMessage) {
        if (fileIds == null) return;
        if (fileIds.isEmpty()) throw new CustomGeneralException(emptyMessage);
        if (fileIds.stream().anyMatch(fileId -> !StringUtils.hasText(fileId)))
            throw new CustomGeneralException(blankMessage);
        if (new HashSet<>(fileIds).size() != fileIds.size())
            throw new CustomGeneralException(duplicateMessage);
    }

    /** 권한을 통과한 파일 ID 집합 안에서 사용자의 선택을 확정합니다. */
    public static Set<String> resolveSelection(
            List<String> requestedIds, Set<String> authorizedIds, String unauthorizedMessage) {
        if (requestedIds == null) return null;
        if (!authorizedIds.containsAll(requestedIds))
            throw new CustomGeneralException(unauthorizedMessage);
        return Set.copyOf(requestedIds);
    }

    /** ZIP 안에서 중복되지 않는 엔트리명을 encounter order로 만듭니다. */
    public static String uniqueEntryName(String requestedName, Set<String> usedNames) {
        if (usedNames.add(requestedName)) return requestedName;

        int slashIndex = requestedName.lastIndexOf('/');
        int dotIndex = requestedName.lastIndexOf('.');
        boolean hasExtension = dotIndex > slashIndex + 1;
        String stem = hasExtension ? requestedName.substring(0, dotIndex) : requestedName;
        String extension = hasExtension ? requestedName.substring(dotIndex) : "";
        int sequence = 2;
        String candidate;
        do {
            candidate = stem + "(" + sequence++ + ")" + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    /** 확정된 파일 목록을 호출자 출력 스트림을 닫지 않고 ZIP으로 씁니다. */
    public static void writeZip(
            List<ArchiveFile> archiveFiles,
            OutputStream output,
            FileService fileService,
            String failureMessage,
            Function<OutputStream, ZipOutputStream> zipOutputStreamFactory) {
        try (ZipOutputStream zip =
                zipOutputStreamFactory.apply(CloseShieldOutputStream.wrap(output))) {
            for (ArchiveFile archiveFile : archiveFiles) {
                FileService.FileDownloadResult download =
                        fileService.downloadFile(archiveFile.fileId());
                zip.putNextEntry(new ZipEntry(archiveFile.entryName()));
                try (InputStream input =
                        java.util.Objects.requireNonNull(download.resource().getInputStream())) {
                    input.transferTo(zip);
                } finally {
                    zip.closeEntry();
                }
            }
        } catch (IOException error) {
            throw new CustomGeneralException(failureMessage, error);
        }
    }

    /** ZIP에 담을 파일 한 건과 확정된 엔트리명입니다. */
    public record ArchiveFile(String fileId, String entryName) {}
}
