package com.kdb.it.domain.migration.request.service;

import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.adapter.FormAdapterOutput;
import com.kdb.it.domain.migration.service.MigrationIoeCatalogReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 편성요청서 외화 행의 환율기준일자를 통화 공통코드의 코드값상세로 채웁니다.
 *
 * <p>어댑터는 통화와 무관하게 `예산연도0101`을 넣습니다. 그러나 환율은 통화 공통코드({@code CUR_C})의 코드값상세코드에서 읽어 저장하므로, 그 환율이 어느 날
 * 기준인지도 같은 행의 <b>코드값상세</b>(`YYYYMMDD`)가 말해 줘야 합니다 — 사용자 전산업무비 작성 화면이 통화 선택 시 자동 입력하는 값과 같은 칸입니다.
 * 정보화사업·경상사업 품목(BITEMM)과 전산업무비(BCOSTM)의 외화 행에 모두 적용하고 원화·통화 미해석 행은 손대지 않습니다.
 *
 * <p>코드값상세가 비어 있거나 `YYYYMMDD`가 아니면 기본값(`예산연도0101`)을 유지하되, 관리자가 공통코드를 고칠 수 있게 통화별 WARNING을 한 건씩
 * 남깁니다. 행마다 내지 않는 이유는 같은 통화의 외화 행이 수십 건인 파일에서 같은 문구가 수십 줄 늘어서기 때문입니다.
 */
@Component
@RequiredArgsConstructor
public class RequestFormXcrBaseDateAssigner {

    /** 환율기준일자 필드 id. 진단 표에서 어느 칸의 문제인지 가리킵니다. */
    private static final String FIELD = "xcrBseDt";

    /** 코드값상세의 환율기준일자 표기. `20260231` 같은 존재하지 않는 날짜도 형식 오류로 봅니다. */
    private static final DateTimeFormatter BASE_DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    private final MigrationIoeCatalogReader catalogReader;

    /**
     * 외화 행의 환율기준일자를 통화 코드값상세로 덮어씁니다. 요청 DTO를 제자리에서 수정합니다.
     *
     * @param output 어댑터가 조립한 생성 요청
     * @param bseYy 예산연도 4자리. 코드값상세를 쓸 수 없을 때의 기본 기준일(`{연도}0101`)을 만듭니다
     * @return 코드값상세가 없거나 형식이 다른 통화별 WARNING 진단. 모두 정상이면 빈 목록
     */
    @Transactional(readOnly = true)
    public List<RequestFormDto.FormDiagnostic> assign(FormAdapterOutput output, String bseYy) {
        Map<String, String> baseDates = catalogReader.xcrBaseDateByCurrency();
        String fallback = bseYy + "0101";
        Map<String, Integer> fallbackRowsByCurrency = new LinkedHashMap<>();

        for (CostDto.CreateRequest cost : output.costs()) {
            apply(cost.getCurC(), cost::setXcrBseDt, baseDates, fallback, fallbackRowsByCurrency);
        }
        for (ProjectDto.CreateRequest project : output.projects()) {
            if (project.getItems() == null) continue;
            for (ProjectDto.BitemmDto item : project.getItems()) {
                apply(
                        item.getCurC(),
                        item::setXcrBseDt,
                        baseDates,
                        fallback,
                        fallbackRowsByCurrency);
            }
        }

        List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : fallbackRowsByCurrency.entrySet()) {
            String currency = entry.getKey();
            String registered = baseDates.get(currency);
            String reason =
                    registered == null
                            ? "등록되어 있지 않아"
                            : "'%s'로 YYYYMMDD 형식이 아니어서".formatted(registered);
            diagnostics.add(
                    RequestFormDto.FormDiagnostic.about(
                            null,
                            null,
                            FIELD,
                            currency,
                            RequestFormDiagnosticCode.DATE_UNPARSEABLE,
                            "통화 %s의 공통코드 환율기준일자(코드값상세)가 %s 외화 행 %d건에 예산연도 1월 1일(%s)을 적용했습니다."
                                    .formatted(currency, reason, entry.getValue(), fallback),
                            List.of()));
        }
        return List.copyOf(diagnostics);
    }

    /** 외화 행이면 기준일자를 정하고, 코드값상세를 쓰지 못한 행은 통화별로 셉니다. */
    private static void apply(
            String curC,
            Consumer<String> setter,
            Map<String, String> baseDates,
            String fallback,
            Map<String, Integer> fallbackRowsByCurrency) {
        if (!isForeign(curC)) return;
        String registered = baseDates.get(curC);
        if (registered != null && isYmd(registered)) {
            setter.accept(registered);
            return;
        }
        setter.accept(fallback);
        fallbackRowsByCurrency.merge(curC, 1, Integer::sum);
    }

    private static boolean isForeign(String curC) {
        return curC != null && !curC.isBlank() && !"KRW".equalsIgnoreCase(curC);
    }

    private static boolean isYmd(String value) {
        try {
            LocalDate.parse(value, BASE_DATE);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
