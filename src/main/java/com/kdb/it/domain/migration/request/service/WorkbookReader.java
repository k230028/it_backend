package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 업로드된 편성요청서 워크북을 안전하게 엽니다.
 *
 * <p>파일은 인증된 관리자만 올릴 수 있지만 엑셀 파서는 신뢰할 수 없는 바이너리를 다루는 지점이라, 크기·시트 수·행 수 상한을 파싱 전후로 강제합니다.
 *
 * <p>zip 압축 폭탄과 거대 레코드 할당을 막는 POI 전역 한도는 이 클래스가 아니라 {@link
 * com.kdb.it.domain.migration.request.config.PoiHardeningConfig}가 기동 시 한 번만 설정합니다 — 그 설정은 JVM 전역 정적
 * 상태라 생성자에서 건드리면 인스턴스마다 전역값을 덮어써 서로 간섭합니다.
 */
@Component
public class WorkbookReader {

    /** OLE2 복합문서(.xls) 시그니처. */
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    /** ZIP(.xlsx) 시그니처. */
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    private final long maxFileBytes;
    private final int maxSheets;
    private final int maxRowsPerSheet;

    public WorkbookReader(
            @Value("${app.migration.request.max-file-bytes}") long maxFileBytes,
            @Value("${app.migration.request.max-sheets}") int maxSheets,
            @Value("${app.migration.request.max-rows-per-sheet}") int maxRowsPerSheet) {
        this.maxFileBytes = maxFileBytes;
        this.maxSheets = maxSheets;
        this.maxRowsPerSheet = maxRowsPerSheet;
    }

    /**
     * 바이트 배열을 워크북으로 엽니다.
     *
     * <p>확장자가 아니라 매직바이트로 형식을 정합니다 — 부점이 `.xls` 파일을 `.xlsx`로 이름만 바꿔 보내는 경우가 있어 확장자를 믿으면 파싱이 실패합니다.
     *
     * @param bytes 업로드 파일 전체 바이트
     * @param originalFilename 진단 메시지에만 쓰는 원본 파일명
     * @return 열린 워크북. 호출자가 닫아야 합니다
     * @throws WorkbookOpenException 비어 있음, 크기·시트 수 상한 초과, 엑셀이 아닌 바이트, POI 파싱 실패
     */
    public Workbook open(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0) {
            throw new WorkbookOpenException("파일이 비어 있습니다: " + originalFilename);
        }
        if (bytes.length > maxFileBytes) {
            throw new WorkbookOpenException(
                    "파일 크기가 상한(%d바이트)을 넘습니다: %s".formatted(maxFileBytes, originalFilename));
        }
        Workbook workbook = openByMagic(bytes, originalFilename);
        if (workbook.getNumberOfSheets() > maxSheets) {
            closeQuietly(workbook);
            throw new WorkbookOpenException(
                    "시트 수가 상한(%d개)을 넘습니다: %s".formatted(maxSheets, originalFilename));
        }
        return workbook;
    }

    private Workbook openByMagic(byte[] bytes, String originalFilename) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            if (startsWith(bytes, OLE2_MAGIC)) return new HSSFWorkbook(in);
            if (startsWith(bytes, ZIP_MAGIC)) return new XSSFWorkbook(in);
            throw new WorkbookOpenException("엑셀 파일이 아닙니다: " + originalFilename);
        } catch (WorkbookOpenException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new WorkbookOpenException("엑셀 파일을 열지 못했습니다: " + originalFilename, e);
        }
    }

    /**
     * 워크북의 시트를 종류별로 분류합니다.
     *
     * <p>행이 하나도 없는 시트는 부점이 쓰지 않은 시트이므로 제외합니다. 같은 종류가 둘 이상이면 먼저 나온 시트를 씁니다.
     *
     * <p>행 수 상한은 마지막 행 <b>번호</b>가 아니라 실제 행 레코드 수({@code getPhysicalNumberOfRows()})로 잽니다. `.xls`
     * 제출본에는 데이터가 20행뿐인데 서식만 남은 빈 행이 시트 맨 아래(65534행)에 하나 붙어 있는 경우가 있어, 행 번호로 재면 정상 파일이 상한에 걸립니다(실측:
     * 자금운용실 시트 ③). 이 상한의 목적은 뒤따르는 앵커 스캔의 작업량을 묶는 것이고 스캔은 실재하는 행만 훑으므로, 행 레코드 수가 재야 할 값입니다.
     *
     * @param workbook 열린 워크북
     * @return 종류별 시트. 인식된 시트가 없으면 빈 맵
     * @throws WorkbookOpenException 어떤 시트의 행 수가 상한을 넘는 경우
     */
    public Map<FormSheetKind, Sheet> classify(Workbook workbook) {
        List<Map<FormSheetKind, Sheet>> groups = classifyGroups(workbook);
        return groups.isEmpty() ? Map.of() : groups.getFirst();
    }

    /**
     * 같은 종류의 시트가 여러 장인 워크북을 제출 순서별 요청서 묶음으로 분류합니다.
     *
     * <p>부점은 한 파일에 `1-1`·`1-2`·`일반관리비` 시트를 복제해 여러 요청서를 담기도 합니다. 종류별 n번째 시트를 같은 n번째 묶음으로 짝지어 모든 요청서를
     * 보존합니다.
     *
     * @param workbook 열린 워크북
     * @return 요청서 묶음 목록. 인식된 시트가 없으면 빈 목록
     */
    public List<Map<FormSheetKind, Sheet>> classifyGroups(Workbook workbook) {
        Map<FormSheetKind, List<Sheet>> sheetsByKind = new EnumMap<>(FormSheetKind.class);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            Optional<FormSheetKind> kind = FormSheetKind.ofSheetName(sheet.getSheetName());
            if (kind.isEmpty() || sheet.getPhysicalNumberOfRows() == 0) continue;
            if (sheet.getPhysicalNumberOfRows() > maxRowsPerSheet) {
                throw new WorkbookOpenException(
                        "시트 `%s`의 행 수가 상한(%d행)을 넘습니다"
                                .formatted(sheet.getSheetName(), maxRowsPerSheet));
            }
            sheetsByKind.computeIfAbsent(kind.get(), ignored -> new ArrayList<>()).add(sheet);
        }
        int groupCount = sheetsByKind.values().stream().mapToInt(List::size).max().orElse(0);
        List<Map<FormSheetKind, Sheet>> groups = new ArrayList<>(groupCount);
        for (int groupIndex = 0; groupIndex < groupCount; groupIndex++) {
            Map<FormSheetKind, Sheet> group = new EnumMap<>(FormSheetKind.class);
            for (Map.Entry<FormSheetKind, List<Sheet>> entry : sheetsByKind.entrySet()) {
                if (groupIndex < entry.getValue().size()) {
                    group.put(entry.getKey(), entry.getValue().get(groupIndex));
                }
            }
            groups.add(Map.copyOf(group));
        }
        return List.copyOf(groups);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) return false;
        }
        return true;
    }

    private static void closeQuietly(Workbook workbook) {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // 상한 위반으로 이미 실패하는 경로라 닫기 실패로 원인을 덮어쓰지 않는다
        }
    }

    /** 워크북을 열지 못한 상태입니다. 파일 단위 `FILE_UNREADABLE` 진단으로 변환됩니다. */
    public static class WorkbookOpenException extends RuntimeException {

        public WorkbookOpenException(String message) {
            super(message);
        }

        public WorkbookOpenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
