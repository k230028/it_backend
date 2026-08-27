package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.service.FormAmount;
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

    /** 구양식 시트 1-2의 비목(중분류) 열. 항목이 D열 이후면 이 C열을 유지합니다. */
    private static final int CAPITAL_RESOURCE_GROUP_COLUMN = 2;

    /** 지급주기: 해당없음. 자본예산 품목은 양식에 주기 열이 없어 이 값을 씁니다. */
    public static final String CYCLE_NOT_APPLICABLE = "0";

    /** JPY 금액 칸이 천엔을 명시했을 때 엔으로 펼 배수입니다. */
    private static final long JPY_MULTIPLIER = 1_000L;

    /** 도입시기 표기에서 월을 뽑는 패턴. `~26.2월`·`2분기 중` 등에서 씁니다. */
    private static final Pattern MONTH_PATTERN = Pattern.compile("(\\d{1,2})\\s*월");

    /** 도입시기 표기에 연도까지 있으면 예산연도와 분리하기 위한 패턴입니다. */
    private static final Pattern YEAR_MONTH_PATTERN =
            Pattern.compile("(?:20)?(\\d{2})\\s*[./년-]\\s*(\\d{1,2})\\s*월");

    private final SheetAnchorScanner scanner;

    /**
     * 시트 1-2 `소요자원 상세내용`의 표를 읽습니다.
     *
     * <p>구양식은 항목이 D열 이후여도 C열을 비목(중분류)으로 유지합니다. 신양식처럼 항목 자체가 C열이면 바로 왼쪽 B열을 비목으로 읽습니다.
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
                                "amount", annualHeader ? "연간 소요예산 (부가세포함)" : "소요예산",
                                "basis", "산정근거",
                                "timing", annualHeader ? "대금지급주기 (월/분기/년)" : "도입시기",
                                "infoSec", "정보보호여부",
                                "infra", "인프라 통합관리 여부",
                                "remarks", "비고(적용 환율 등)"));

        Optional<SheetAnchorScanner.HeaderMap> header =
                scanner.findHeader(sheet, fromRow, aliases, "item", "qty", "currency", "amount");
        if (header.isEmpty()) return Optional.empty();

        SheetAnchorScanner.HeaderMap map = header.get();
        int itemCol = map.column("item");
        // 구양식은 중간 보조 열 때문에 C열을 유지해야 하고, 신양식은 항목 자체가 C열이라
        // 그 바로 왼쪽 B열을 써야 합니다.
        int groupCol =
                groupColumn == null || groupColumn >= itemCol
                        ? Math.max(itemCol - 1, 0)
                        : groupColumn;

        List<ResourceRow> rows = new ArrayList<>();
        String lastGroup = "";
        for (int rowIndex = map.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String itemName = text(sheet, map, rowIndex, "item");
            // 다음 블록의 헤더를 만나면 이 블록은 끝이다. 이 판정이 없으면 1-2의 자본예산 블록이
            // 아래 일반관리비 블록의 행까지 삼킨다(두 블록의 열 구성이 거의 같아 헤더 아래 행이
            // 그대로 데이터로 읽힌다).
            if (isHeaderRow(itemName)) break;
            if (isTotalRow(sheet, rowIndex)) break;

            BigDecimal qty = number(sheet, map, rowIndex, "qty");
            FormAmount.Parsed price = FormAmount.parse(text(sheet, map, rowIndex, "unitPrice"));
            BigDecimal unitPrice = price == null ? null : price.value();
            FormAmount.Parsed declared = FormAmount.parse(text(sheet, map, rowIndex, "amount"));
            BigDecimal amount = declared == null ? null : declared.value();
            AmountUnit amountUnit = declared == null ? null : declared.unit();
            if (amount == null || amount.signum() == 0) {
                // 단가로 대신 채울 때는 단위 표기도 단가 칸에서 가져온다 — 값만 옮기면 `2,122백만원`이 2,122원이 된다
                amount = fromUnitPrice(qty, unitPrice);
                amountUnit = price == null ? null : price.unit();
            }
            if (itemName.isEmpty() || amount == null || amount.signum() == 0) continue;

            String group = scanner.text(sheet, rowIndex, groupCol);
            if (!group.isEmpty()) lastGroup = group;

            rows.add(
                    new ResourceRow(
                            rowIndex + 1,
                            lastGroup,
                            itemName,
                            qty,
                            unitPrice,
                            text(sheet, map, rowIndex, "currency"),
                            amount,
                            amountUnit,
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
     * `FC_AMT × Ccodem 환율`로 재계산하므로 여기서 채우면 그 값이 버려집니다. JPY는 금액 칸이 밝힌 단위만 적용하고, 표기가 없으면 엔으로 봅니다.
     *
     * @param row 소요자원 행
     * @param ioeCode 확정된 비목코드. 미해석이면 null
     * @param sno 품목 순번 (1부터)
     * @param bseYy 예산연도. 환율기준일자를 `{연도}0101`로 만듭니다
     * @return 품목 DTO
     */
    public static ProjectDto.BitemmDto toItem(
            ResourceRow row, String ioeCode, int sno, String bseYy) {
        return toItem(row, ioeCode, sno, bseYy, (AmountUnit) null);
    }

    /**
     * 국내 정보화사업의 통화 단위 미기재 행에는 양식 기본값인 KRW 백만원을 적용합니다.
     *
     * @param domesticDefault 국내 정보화사업 기본값 적용 여부
     */
    public static ProjectDto.BitemmDto toItem(
            ResourceRow row, String ioeCode, int sno, String bseYy, boolean domesticDefault) {
        return toItem(row, ioeCode, sno, bseYy, domesticDefault ? AmountUnit.MILLION : null);
    }

    /**
     * 국내 사업의 통화 단위 미기재 행에 사업 유형별 KRW 기본 단위를 적용합니다.
     *
     * @param domesticDefaultUnit 국내 기본 단위. 국외지점처럼 기본값을 적용하지 않으면 null
     */
    public static ProjectDto.BitemmDto toItem(
            ResourceRow row,
            String ioeCode,
            int sno,
            String bseYy,
            AmountUnit domesticDefaultUnit) {
        return toItem(row, ioeCode, sno, bseYy, domesticDefaultUnit, false);
    }

    /**
     * 국내 기본 단위를 통화 미기재 행뿐 아니라 시트의 모든 KRW 행에 적용할 수 있습니다.
     *
     * @param applyDomesticUnitToAllKrw true이면 명시적으로 KRW인 행에도 국내 기본 단위를 적용
     */
    public static ProjectDto.BitemmDto toItem(
            ResourceRow row,
            String ioeCode,
            int sno,
            String bseYy,
            AmountUnit domesticDefaultUnit,
            boolean applyDomesticUnitToAllKrw) {
        String currency = normalizeCurrency(row.currency());
        // 일부 구양식은 통화 열이 없고 병합 헤더 때문에 단가가 통화 위치로 잡힌다.
        // 실제 통화코드는 문자이므로 국내 사업의 숫자값만 열 부재로 판정한다.
        if (domesticDefaultUnit != null && isNumericText(currency)) currency = null;
        boolean defaultedDomestic =
                domesticDefaultUnit != null && (currency == null || currency.isBlank());
        if (defaultedDomestic) currency = "KRW";
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
            // 칸이 스스로 단위를 밝혔으면(`2,122백만원`) 어댑터 기본 단위보다 그 값이 정확하다.
            // 이 구분이 없으면 백만원으로 적은 금액이 원 단위로 들어가 1/1,000,000이 된다
            boolean applyDomesticUnit =
                    defaultedDomestic || (applyDomesticUnitToAllKrw && domesticDefaultUnit != null);
            AmountUnit unit =
                    row.amountUnit() != null
                            ? row.amountUnit()
                            : (applyDomesticUnit ? domesticDefaultUnit : null);
            BigDecimal won = unit == null ? row.amount() : unit.toWon(row.amount());
            if (isAfterBudgetYear(item.getBseYm(), bseYy)) {
                item.setAmt(BigDecimal.ZERO);
                item.setMplAmt(won);
            } else {
                item.setAmt(won);
            }
            item.setFcAmt(null);
        } else if (currency != null && !currency.isBlank()) {
            long multiplier =
                    "JPY".equals(currency) && row.amountUnit() == AmountUnit.THOUSAND
                            ? JPY_MULTIPLIER
                            : 1L;
            item.setFcAmt(row.amount().multiply(BigDecimal.valueOf(multiplier)));
            item.setAmt(null);
            item.setXcr(null);
        }
        return item;
    }

    private static boolean isAfterBudgetYear(String bseYm, String bseYy) {
        return bseYm != null
                && bseYm.length() >= 4
                && bseYy != null
                && bseYm.substring(0, 4).compareTo(bseYy) > 0;
    }

    /**
     * 소요예산 칸이 비었을 때 단가로 금액을 채웁니다.
     *
     * <p>수량·단가·통화만 적고 소요예산 칸을 비워 내는 제출본이 있습니다(실측: 상하이지점 1-2). 그대로 두면 그 행이 통째로 버려져 사업 소요금액이 0원이 되고
     * 파일이 차단됩니다. 수량이 비어 있으면 1건으로 봅니다 — 단가만 적었다는 것은 그 금액이 곧 소요예산이라는 뜻입니다.
     *
     * <p>산출값이 실제와 어긋나면 1-1 선언 금액과 1-2 합계를 맞대보는 기존 대사가 {@code AMOUNT_MISMATCH} 경고로 잡아 줍니다.
     *
     * @param qty 수량. 비어 있거나 0이면 1로 봅니다
     * @param unitPrice 단가
     * @return 산출 금액. 단가가 없으면 null
     */
    private static BigDecimal fromUnitPrice(BigDecimal qty, BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.signum() == 0) return null;
        if (qty == null || qty.signum() == 0) return unitPrice;
        return unitPrice.multiply(qty);
    }

    /** 엑셀 통화 셀의 대소문자와 일반·전각 공백을 공통코드 형식으로 맞춥니다. */
    private static String normalizeCurrency(String currency) {
        if (currency == null) return null;
        String normalized = currency.replaceAll("[\\s\\p{Z}]+", "").toUpperCase(Locale.ROOT);
        if ("원".equals(normalized)
                || "원화".equals(normalized)
                || "₩".equals(normalized)
                || "원화(KRW)".equals(normalized)
                || "KRW(원화)".equals(normalized)) {
            return "KRW";
        }
        return normalized.isBlank() ? null : normalized;
    }

    /** 쉼표를 포함한 순수 숫자 표기인지 확인합니다. 문자 통화코드 오기는 기본값으로 숨기지 않습니다. */
    private static boolean isNumericText(String value) {
        if (value == null) return false;
        try {
            new BigDecimal(value.replace(",", ""));
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
        String value = timing == null ? "" : timing;
        Matcher yearMonth = YEAR_MONTH_PATTERN.matcher(value);
        if (yearMonth.find()) {
            int month = Integer.parseInt(yearMonth.group(2));
            if (month < 1 || month > 12) return null;
            return "20" + yearMonth.group(1) + String.format("%02d", month);
        }
        Matcher matcher = MONTH_PATTERN.matcher(value);
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
        return FormAmount.value(text(sheet, map, rowIndex, columnId));
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
