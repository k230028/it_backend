package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationAmounts;
import com.kdb.it.domain.migration.service.MigrationValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 어댑터가 공유하는 셀 읽기·단위 변환·코드 변환 헬퍼입니다. */
public final class AdapterSupport {

    /** `'26.05`·`26.5`·`'26.12월` 형태를 받습니다. */
    private static final Pattern YM = Pattern.compile("^'?(\\d{2})\\.(\\d{1,2})월?$");

    private AdapterSupport() {
        throw new UnsupportedOperationException("유틸 클래스 — 인스턴스화 금지");
    }

    /**
     * 보정값이 있으면 보정값을, 없으면 원본 셀을 읽습니다.
     *
     * @param sheet 시트 페이로드 (종류를 보정 키에 씁니다)
     * @param row 정규화 행
     * @param column 정규 컬럼 id
     * @param ctx 어댑터 컨텍스트
     * @return 셀 문자열. 없거나 null이면 빈 문자열
     */
    public static String cellOf(
            MigrationDto.SheetPayload sheet,
            MigrationDto.NormalizedRow row,
            String column,
            AdapterContext ctx) {
        String override =
                ctx.overrides()
                        .get(MigrationValidator.overrideKey(sheet.kind(), row.excelRow(), column));
        if (override != null) {
            return override;
        }
        String value = row.cells().get(column);
        return value == null ? "" : value.trim();
    }

    /**
     * 엑셀 금액을 원 단위로 올립니다.
     *
     * @param raw 엑셀 셀 문자열 (쉼표 허용)
     * @param kind 시트 종류 — 배수를 정합니다 (일반관리비 ×1,000 / 자본예산·부문계획 ×1,000,000 / 위임예산 ×1)
     * @return 원 단위 금액. 셀이 비었거나 숫자가 아니면 null
     */
    public static BigDecimal amount(String raw, SheetKind kind) {
        BigDecimal parsed = MigrationAmounts.number(raw);
        if (parsed == null) {
            return null;
        }
        // 배수 규칙은 MigrationAmounts가 단일 출처다. 여기서 switch를 다시 만들면 검증기와 조용히 어긋난다
        return parsed.multiply(MigrationAmounts.amountMultiplier(kind))
                .setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * 외화 원금을 통화 기본 단위로 맞춥니다. JPY만 엑셀이 천엔 단위라 ×1,000합니다 (§5.1).
     *
     * @param raw 엑셀 외화 셀 문자열
     * @param currency 통화코드
     * @return 통화 기본 단위 금액. 셀이 비었거나 원화면 null
     */
    public static BigDecimal foreignAmount(String raw, String currency) {
        if (currency == null || currency.isBlank() || "KRW".equals(currency)) {
            return null;
        }
        BigDecimal parsed = MigrationAmounts.number(raw);
        if (parsed == null) {
            return null;
        }
        return "JPY".equals(currency) ? parsed.multiply(new BigDecimal("1000")) : parsed;
    }

    /**
     * `O`·`Y`·`y`를 `Y`로, 나머지를 `N`으로 바꿉니다.
     *
     * @param raw 엑셀 셀 문자열
     * @return `Y` 또는 `N`
     */
    public static String flag(String raw) {
        if (raw == null) {
            return "N";
        }
        String v = raw.trim();
        return ("O".equalsIgnoreCase(v) || "Y".equalsIgnoreCase(v) || "○".equals(v)) ? "Y" : "N";
    }

    /**
     * `신규`·`계속` 라벨을 `ABUS_TC` 코드값으로 바꿉니다.
     *
     * @param label 엑셀 구분·진행상황 라벨
     * @return `10`(신규)·`20`(계속)·`0`(그 외·해당없음)
     */
    public static String abusTc(String label) {
        if (label == null) {
            return "0";
        }
        return switch (label.trim()) {
            case "신규" -> "10";
            case "계속" -> "20";
            default -> "0";
        };
    }

    /**
     * `'26.05`를 해당 월 1일로 바꿉니다.
     *
     * @param raw 엑셀 연월 표기
     * @return 해당 월 1일. 파싱 실패면 null
     */
    public static LocalDate ymToFirstDay(String raw) {
        YearMonth ym = yearMonth(raw);
        return ym == null ? null : ym.atDay(1);
    }

    /**
     * `'26.12`를 해당 월 말일로 바꿉니다.
     *
     * @param raw 엑셀 연월 표기
     * @return 해당 월 말일. 파싱 실패면 null
     */
    public static LocalDate ymToLastDay(String raw) {
        YearMonth ym = yearMonth(raw);
        return ym == null ? null : ym.atEndOfMonth();
    }

    /**
     * `'26.12`를 `BSE_YM` 6자리로 바꿉니다.
     *
     * @param raw 엑셀 연월 표기
     * @return `202612` 형태. 파싱 실패면 null
     */
    public static String ymToYyyymm(String raw) {
        YearMonth ym = yearMonth(raw);
        return ym == null ? null : String.format("%04d%02d", ym.getYear(), ym.getMonthValue());
    }

    /**
     * 조정비율(`0.7`·`1`)을 정수 편성률로 바꿉니다.
     *
     * @param raw 엑셀 조정비율
     * @return 0~100으로 잘린 편성률. 셀이 비었으면 100
     */
    public static int ratePercent(String raw) {
        BigDecimal parsed = MigrationAmounts.number(raw);
        if (parsed == null) {
            return 100;
        }
        int percent =
                parsed.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * 조정비율 셀을 소수 배수로 읽습니다.
     *
     * <p>엑셀 `조정구분` 시트가 `1`·`0.7`처럼 배수로 적기 때문에 값을 그대로 씁니다. 비었거나 숫자가 아니면 조정하지 않은 것으로 보고 1을 돌려줍니다 — 0을
     * 돌려주면 조정비율 칸이 빈 사업의 편성액이 통째로 0이 됩니다.
     *
     * @param raw 조정비율 셀 원문
     * @return 배수 (0.7·1 등). 파싱 실패는 1
     */
    public static BigDecimal rateFraction(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ONE;
        }
    }

    /**
     * 조직 이름 또는 코드값을 조직코드로 바꿉니다.
     *
     * <p>세 어댑터(전산일반관리비·자본예산·위임예산)가 같은 규칙을 씁니다 — 이름으로 해석되면 그 코드를, 해석되지 않았지만 값 자체가 등록된 조직코드면(미리보기
     * 보정값은 코드값으로 옵니다) 그 값을 그대로 씁니다. 둘 다 아니면 null이며, 이 상태는 검증이 이미 BLOCKER로 막았어야 합니다.
     *
     * @param raw 셀 값 또는 보정값
     * @param ctx 어댑터 컨텍스트
     * @return 조직코드. 미해석이면 null
     */
    public static String resolveOrgCode(String raw, AdapterContext ctx) {
        String code = ctx.index().org().resolveOrg(raw).code();
        if (code != null) {
            return code;
        }
        return ctx.index().org().orgNameOf(raw) != null ? raw : null;
    }

    /**
     * 코드값명 맵으로 라벨을 코드값으로 바꿉니다.
     *
     * @param codeByName 코드값명 → 코드값 맵
     * @param raw 셀 값 또는 보정값
     * @return 코드값. 라벨이 매칭되지 않고 값 자체도 등록된 코드값이 아니면 null
     */
    public static String resolveCode(java.util.Map<String, String> codeByName, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String mapped = codeByName.get(raw.trim());
        if (mapped != null) {
            return mapped;
        }
        return codeByName.containsValue(raw.trim()) ? raw.trim() : null;
    }

    /** 2자리 연도를 2000년대로 해석합니다. 은행 편성 문서가 `'26` 표기를 쓰므로 세기 보정이 필요합니다. */
    private static YearMonth yearMonth(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = YM.matcher(raw.trim());
        if (!m.matches()) {
            return null;
        }
        return YearMonth.of(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }
}
