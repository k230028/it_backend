package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.service.FormLexicon;
import com.kdb.it.domain.migration.request.service.SheetAnchorScanner;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

/**
 * 소요자원 표 하나를 읽습니다.
 *
 * <p>시트 ②와 시트 1-2가 같은 모양의 표(`구분 | 항목 | 수량 | 단가 | 통화 | 소요예산 | …`)를 쓰므로 두 어댑터가 공유합니다. 1-2는 자본예산·일반관리비
 * 블록 두 개가 위아래로 놓여 있어 `fromRow`를 옮겨 가며 두 번 호출합니다.
 */
@Component
@RequiredArgsConstructor
public class ResourceTableReader {

    /** 시트 1-2의 비목(중분류) 열. `소요예산 | 대분류 | 중분류 | 항목` 배치의 세 번째 열입니다. */
    private static final int CAPITAL_RESOURCE_GROUP_COLUMN = 2;

    /** 지급주기: 해당없음. 자본예산 품목은 양식에 주기 열이 없어 이 값을 씁니다. */
    public static final String CYCLE_NOT_APPLICABLE = "0";

    /** JPY만 양식이 천엔 단위라 엔으로 폅니다. */
    private static final long JPY_MULTIPLIER = 1_000L;

    /** 도입시기 표기에서 월을 뽑는 패턴. `~26.2월`·`2분기 중` 등에서 씁니다. */
    private static final Pattern MONTH_PATTERN = Pattern.compile("(\\d{1,2})\\s*월");

    private final SheetAnchorScanner scanner;

    /**
     * 시트 1-2 `소요자원 상세내용`의 표를 읽습니다.
     *
     * <p>비목(중분류)은 <b>C열 고정</b>입니다. 이 시트는 A열이 블록 라벨(`소요예산`), B열이 대분류(`자본예산`·`일반관리비`), C열이 중분류
     * (`기계장치(HW)`·`전산제비` 등), D열이 항목명인 고정 배치입니다. 항목 열 기준으로 한 칸 왼쪽을 잡으면 부점이 열을 하나 끼워 넣은 파일에서 대분류나 빈
     * 열을 비목으로 읽어 조용히 어긋납니다.
     *
     * @param sheet 1-2 시트
     * @param fromRow 이 행부터 헤더를 찾습니다
     * @param annualHeader true면 `연간 소요예산` 헤더(일반관리비 블록), false면 `소요예산` 헤더(자본예산 블록)
     * @return 헤더 위치와 행 목록. 표를 못 찾으면 빈 Optional
     */
    public Optional<Result> readCapitalResource(Sheet sheet, int fromRow, boolean annualHeader) {
        return read(sheet, fromRow, annualHeader, CAPITAL_RESOURCE_GROUP_COLUMN);
    }

    /**
     * 시트 ② `경상적인 사업`의 소요자원 표를 읽습니다.
     *
     * <p>1-2와 달리 블록 라벨 열이 없어 비목 열의 위치가 한 칸 당겨집니다(실측: 런던 제출본은 A열이 `구분`, B열이 중분류, C열이 항목). 고정 열을 쓸 수
     * 없어 항목 열 바로 왼쪽으로 잡습니다.
     *
     * @param sheet ② 시트
     * @return 헤더 위치와 행 목록. 표를 못 찾으면 빈 Optional
     */
    public Optional<Result> readRecurring(Sheet sheet) {
        return read(sheet, 0, false, null);
    }

    /**
     * 표를 찾아 데이터 행을 읽습니다.
     *
     * @param groupColumn 비목(중분류) 열. null이면 항목 열 바로 왼쪽으로 잡습니다
     */
    private Optional<Result> read(
            Sheet sheet, int fromRow, boolean annualHeader, Integer groupColumn) {
        Map<String, List<String>> aliases =
                FormLexicon.columnAliases(
                        Map.of(
                                "item", "항목",
                                "qty", "수량",
                                "unitPrice", "단가",
                                "currency", "통화",
                                "amount", annualHeader ? "연간 소요예산 (부가세포함)" : "소요예산 (부가세포함)",
                                "basis", "산정근거",
                                "timing", annualHeader ? "대금지급주기 (월/분기/년)" : "도입시기",
                                "infoSec", "정보보호여부",
                                "infra", "인프라 통합관리 여부",
                                "remarks", "비고(적용 환율 등)"));

        Optional<SheetAnchorScanner.HeaderMap> header =
                scanner.findHeader(sheet, fromRow, aliases, "item", "qty", "currency", "amount");
        if (header.isEmpty()) return Optional.empty();

        SheetAnchorScanner.HeaderMap map = header.get();
        // 구분(대분류)·중분류는 헤더 라벨이 `구분` 하나로 병합돼 있어 헤더에서 열을 찾을 수 없다.
        int groupCol = groupColumn != null ? groupColumn : Math.max(map.column("item") - 1, 0);

        List<ResourceRow> rows = new ArrayList<>();
        String lastGroup = "";
        for (int rowIndex = map.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String itemName = text(sheet, map, rowIndex, "item");
            // 다음 블록의 헤더를 만나면 이 블록은 끝이다. 이 판정이 없으면 1-2의 자본예산 블록이
            // 아래 일반관리비 블록의 행까지 삼킨다(두 블록의 열 구성이 거의 같아 헤더 아래 행이
            // 그대로 데이터로 읽힌다).
            if (isHeaderRow(itemName)) break;
            if (isTotalRow(sheet, rowIndex)) break;

            BigDecimal amount = number(sheet, map, rowIndex, "amount");
            if (itemName.isEmpty() || amount == null || amount.signum() == 0) continue;

            String group = scanner.text(sheet, rowIndex, groupCol);
            if (!group.isEmpty()) lastGroup = group;

            rows.add(
                    new ResourceRow(
                            rowIndex + 1,
                            lastGroup,
                            itemName,
                            number(sheet, map, rowIndex, "qty"),
                            number(sheet, map, rowIndex, "unitPrice"),
                            text(sheet, map, rowIndex, "currency"),
                            amount,
                            text(sheet, map, rowIndex, "basis"),
                            text(sheet, map, rowIndex, "timing"),
                            text(sheet, map, rowIndex, "infoSec"),
                            text(sheet, map, rowIndex, "infra"),
                            text(sheet, map, rowIndex, "remarks")));
        }
        return Optional.of(new Result(map.rowIndex(), List.copyOf(rows)));
    }

    /**
     * 소요자원 행을 품목 DTO로 바꿉니다.
     *
     * <p>원화 행은 `AMT`에, 외화 행은 `FC_AMT`에만 담습니다. 외화의 원화금액과 환율은 서버 {@code BudgetAmountCalculator}가
     * `FC_AMT × Ccodem 환율`로 재계산하므로 여기서 채우면 그 값이 버려집니다. JPY만 양식이 천엔이라 엔으로 폅니다.
     *
     * @param row 소요자원 행
     * @param ioeCode 확정된 비목코드. 미해석이면 null
     * @param sno 품목 순번 (1부터)
     * @param bseYy 예산연도. 환율기준일자를 `{연도}0101`로 만듭니다
     * @return 품목 DTO
     */
    public static ProjectDto.BitemmDto toItem(
            ResourceRow row, String ioeCode, int sno, String bseYy) {
        String currency = normalizeCurrency(row.currency());
        ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
        item.setSno(sno);
        item.setIoeC(ioeCode);
        item.setGclNm(row.itemName());
        item.setQty(row.qty());
        item.setCurC(currency);
        item.setCncdFdtnCone(row.basis());
        item.setBseYm(toBseYm(row.timing(), bseYy));
        item.setDfrCleC(toPaymentCycle(row.timing()));
        item.setSectSysUtzYn(FormLexicon.toYn(row.infoSec()).orElse(null));
        item.setItrInfrYn(FormLexicon.toYn(row.infra()).orElse(null));
        item.setLstYn("Y");
        item.setXcrBseDt(bseYy + "0101");

        if ("KRW".equals(currency)) {
            item.setAmt(row.amount());
            item.setFcAmt(null);
        } else {
            long multiplier = "JPY".equals(currency) ? JPY_MULTIPLIER : 1L;
            item.setFcAmt(row.amount().multiply(BigDecimal.valueOf(multiplier)));
            item.setAmt(null);
            item.setXcr(null);
        }
        return item;
    }

    /** 엑셀 통화 셀의 대소문자와 일반·전각 공백을 공통코드 형식으로 맞춥니다. */
    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        return currency.replaceAll("[\\s\\p{Z}]+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 지급주기 표기를 코드로 바꿉니다.
     *
     * <p>자본예산 블록은 `도입시기(월)`만 있고 주기 열이 없어 대부분 해당없음(`0`)이 됩니다. 물리 컬럼 `DFR_CLE_C`가 NOT NULL이라 기본값이 반드시
     * 필요합니다.
     */
    private static String toPaymentCycle(String timing) {
        String normalized = SheetAnchorScanner.normalize(timing);
        if (normalized.contains("분기")) return "Q";
        if (normalized.contains("반기")) return "H";
        if (normalized.endsWith("월")) return "M";
        if (normalized.endsWith("년")) return "Y";
        return CYCLE_NOT_APPLICABLE;
    }

    /** `~26.2월` 같은 표기에서 추진년월(`YYYYMM`)을 뽑습니다. 못 뽑으면 null. */
    private static String toBseYm(String timing, String bseYy) {
        Matcher matcher = MONTH_PATTERN.matcher(timing == null ? "" : timing);
        if (!matcher.find()) return null;
        int month = Integer.parseInt(matcher.group(1));
        if (month < 1 || month > 12) return null;
        return bseYy + String.format("%02d", month);
    }

    private String text(
            Sheet sheet, SheetAnchorScanner.HeaderMap map, int rowIndex, String columnId) {
        Integer col = map.column(columnId);
        return col == null ? "" : scanner.text(sheet, rowIndex, col);
    }

    private BigDecimal number(
            Sheet sheet, SheetAnchorScanner.HeaderMap map, int rowIndex, String columnId) {
        String raw = text(sheet, map, rowIndex, columnId).replace(",", "").trim();
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 합계 행인지 판정합니다. 합계는 적재하지 않고 여기서 표를 끝냅니다. */
    private boolean isTotalRow(Sheet sheet, int rowIndex) {
        String label = SheetAnchorScanner.normalize(scanner.text(sheet, rowIndex, 0));
        return label.equals("계")
                || label.equals("소계")
                || label.equals("총계")
                || label.equalsIgnoreCase("Total");
    }

    /** 항목 열에 헤더 라벨이 그대로 들어 있으면 다음 블록의 헤더 행입니다. */
    private boolean isHeaderRow(String itemCellText) {
        String normalized = SheetAnchorScanner.normalize(itemCellText);
        return normalized.equals("항목") || normalized.equalsIgnoreCase("Item");
    }

    /**
     * 표를 읽은 결과입니다.
     *
     * @param headerRow 헤더의 0-based 행 번호. 다음 블록을 찾을 시작점으로 씁니다
     * @param rows 데이터 행 목록
     */
    public record Result(int headerRow, List<ResourceRow> rows) {}
}
