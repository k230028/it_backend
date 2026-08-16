package com.kdb.it.domain.migration.request.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 편성요청서 어휘 대조표입니다.
 *
 * <p>해외점포는 같은 양식을 영문으로 번역해 제출합니다(런던지점 실측). 시트명은 국문 그대로라 시트 판별은 영향받지 않지만 라벨·비목명이 전부 영문이라 국문 기준 매칭이
 * 빗나갑니다. 어휘를 이 클래스 한곳에 모아 새 표기가 나타났을 때 상수 한 줄 추가로 끝나게 합니다.
 *
 * <p>대조표는 런던지점 제출본 1건에서 뽑았습니다. 다른 점포의 번역이 다를 수 있으므로 <b>미식별 어휘를 추측해 매핑하지 않습니다</b> — 원문을 그대로 넘겨 미해석
 * 진단이 나게 하고 미리보기에서 사람이 고르게 합니다.
 */
public final class FormLexicon {

    private FormLexicon() {
        throw new UnsupportedOperationException("상수 컨테이너 — 인스턴스화 금지");
    }

    /** 사업구분코드: 신규. 공통코드 ABUS_TC. */
    public static final String ABUS_TC_NEW = "10";

    /** 사업구분코드: 계속. 공통코드 ABUS_TC. */
    public static final String ABUS_TC_CONTINUED = "20";

    /** 국문 정본 라벨(정규화 키) → 영문 표기. 국문 자신은 조회 시 앞에 붙입니다. */
    private static final Map<String, List<String>> ENGLISH_BY_LABEL = englishByLabel();

    /** 양식 표기(정규화 키) → 공통코드(IOE_C) 코드값명. */
    private static final Map<String, String> IOE_CANONICAL = ioeCanonical();

    /** 체크박스 문구(정규화 키) → 공통코드 코드값명. 문구와 코드값명이 어긋나는 것만 담습니다. */
    private static final Map<String, String> OPTION_CANONICAL = optionCanonical();

    /** 긍정 표기 집합. 양식마다 O·√·● 등이 섞여 있습니다. */
    private static final Set<String> AFFIRMATIVE =
            Set.of("O", "o", "○", "◯", "０", "0", "√", "∨", "V", "v", "Y", "y", "●", "◎");

    /** 부정 표기 집합. 런던 제출본의 `Ⅹ`는 알파벳 X가 아니라 로마숫자 10(U+2169)입니다. */
    private static final Set<String> NEGATIVE = Set.of("X", "x", "Ⅹ", "✕", "✖", "ㄨ", "N", "n", "-");

    /**
     * 국문 정본 라벨의 표기 목록을 만듭니다.
     *
     * @param canonicalLabel 국문 정본 라벨
     * @return 국문 정본이 첫 원소인 표기 목록. 대조표에 없으면 정본 하나만
     */
    public static List<String> labelAliases(String canonicalLabel) {
        List<String> english = ENGLISH_BY_LABEL.get(SheetAnchorScanner.normalize(canonicalLabel));
        if (english == null || english.isEmpty()) return List.of(canonicalLabel);
        List<String> aliases = new ArrayList<>();
        aliases.add(canonicalLabel);
        aliases.addAll(english);
        return List.copyOf(aliases);
    }

    /**
     * 컬럼 id별 국문 정본을 별칭 목록으로 펼칩니다.
     *
     * <p>{@link SheetAnchorScanner#findHeader}에 그대로 넘길 수 있는 형태입니다.
     *
     * @param canonicalByColumnId 컬럼 id 별 국문 정본 라벨
     * @return 컬럼 id 별 표기 목록
     */
    public static Map<String, List<String>> columnAliases(Map<String, String> canonicalByColumnId) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        canonicalByColumnId.forEach(
                (columnId, canonical) -> out.put(columnId, labelAliases(canonical)));
        return out;
    }

    /**
     * 양식의 O/X 표기를 Y/N으로 접습니다.
     *
     * <p>정의되지 않은 문자를 조용히 `N`으로 접지 않습니다 — 정보보호 항목이 통째로 뒤집혀도 아무도 알아채지 못합니다. 미식별은 빈 Optional로 남겨 호출자가
     * 미해석 진단을 내게 합니다.
     *
     * @param raw 셀 원문. null·공백은 미표기로 보아 `N`
     * @return `Y` 또는 `N`. 정의되지 않은 표기면 빈 Optional
     */
    public static Optional<String> toYn(String raw) {
        String normalized = SheetAnchorScanner.normalize(raw);
        if (normalized.isEmpty()) return Optional.of("N");
        if (AFFIRMATIVE.contains(normalized)) return Optional.of("Y");
        if (NEGATIVE.contains(normalized)) return Optional.of("N");
        return Optional.empty();
    }

    /**
     * 양식의 비목 표기를 공통코드 코드값명으로 되돌립니다.
     *
     * @param raw 양식의 비목명 (국문 또는 영문)
     * @return 공통코드 표기. 대조표에 없으면 원문 그대로 (미해석 진단으로 이어집니다). null이면 빈 문자열
     */
    public static String canonicalIoeName(String raw) {
        if (raw == null) return "";
        String canonical = IOE_CANONICAL.get(SheetAnchorScanner.normalize(raw));
        return canonical == null ? raw.trim() : canonical;
    }

    /**
     * 계약구분의 계속·신규 표시를 사업구분코드로 바꿉니다.
     *
     * @param continued `계속` 열의 셀 원문
     * @param isNew `신규` 열의 셀 원문
     * @return `20`(계속) 또는 `10`(신규). 둘 다 비었거나 둘 다 표시되면 빈 Optional
     */
    public static Optional<String> toAbusTc(String continued, String isNew) {
        boolean continuedMarked = toYn(continued).filter("Y"::equals).isPresent();
        boolean newMarked = toYn(isNew).filter("Y"::equals).isPresent();
        if (continuedMarked == newMarked) return Optional.empty();
        return Optional.of(continuedMarked ? ABUS_TC_CONTINUED : ABUS_TC_NEW);
    }

    private static Map<String, List<String>> englishByLabel() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        put(map, "사업명", "Business Name");
        put(map, "사업 개요", "Business Overview");
        put(map, "항목", "Item");
        put(map, "수량", "Qty", "Quantity");
        put(map, "단가", "Unit Cost");
        put(map, "통화", "Currency");
        put(map, "통화 구분", "Currency");
        put(map, "소요예산 (부가세포함)", "Budget (Tax Included)");
        put(map, "연간 소요예산 (부가세포함)", "Annual Budget (Tax Included)");
        put(map, "도입시기", "Timing");
        put(map, "비고(적용 환율 등)", "Remarks");
        put(map, "비 목 명", "Expense");
        put(map, "계약명 / 건명", "Name of the Contract / Item");
        put(map, "소요예산", "Budget");
        put(map, "월간", "Monthly");
        put(map, "연간", "Annual");
        put(map, "계약", "Contract");
        put(map, "계약구분", "Contract Type");
        put(map, "상대처", "Counterparty");
        put(map, "계속", "Cont.");
        put(map, "신규", "New");
        put(map, "정보보호 관련여부", "InfoSec. Related");
        put(map, "비고(증감사유, 적용환율 등)", "Remarks (Reasons for increase / decrease)");
        return Map.copyOf(map);
    }

    private static void put(Map<String, List<String>> map, String korean, String... english) {
        map.put(SheetAnchorScanner.normalize(korean), List.of(english));
    }

    /**
     * 체크박스 문구를 공통코드 코드값명으로 되돌립니다.
     *
     * <p>양식은 체크박스 옆에 설명을 덧붙이지만(`부문(본부장) 보고`) 코드표는 짧은 이름(`부문(본부)장`)을 씁니다. 대조표에 없으면 원문을 그대로 넘겨 코드
     * 조회에서 걸러지게 합니다.
     *
     * @param raw 체크박스 문구
     * @return 공통코드 표기. 대조표에 없으면 원문 그대로. null이면 빈 문자열
     */
    public static String canonicalOptionName(String raw) {
        if (raw == null) return "";
        String canonical = OPTION_CANONICAL.get(SheetAnchorScanner.normalize(raw));
        return canonical == null ? raw.trim() : canonical;
    }

    private static Map<String, String> optionCanonical() {
        Map<String, String> map = new LinkedHashMap<>();
        // 최종보고 (IT_PTL_RPR_STS_TC)
        alias(map, "부문(본부장) 보고", "부문(본부)장");
        alias(map, "부서장 보고", "부서장");
        // 추진가능성 (EXE_PTT_YN)
        alias(map, "확정(변동가능성 無)", "확정");
        alias(map, "추진계획 검토중", "미정(검토중)");
        alias(map, "변동가능성 有", "미정(검토중)");
        // 전결권자 (IT_PTL_EDRT_TC). 부점은 직위 통칭을 쓰지만 코드표는 직명을 씁니다
        alias(map, "수석부행장", "전무이사");
        return Map.copyOf(map);
    }

    private static Map<String, String> ioeCanonical() {
        Map<String, String> map = new LinkedHashMap<>();
        // 국문 양식 표기와 공통코드 표기가 어긋나는 것만 담는다.
        // 실측: 양식은 `국외전산유지보수료`, 공통코드(014)는 `국외유지보수료`.
        alias(map, "국외전산유지보수료", "국외유지보수료");
        // 영문 양식 (런던지점 실측)
        alias(map, "Foreign branch IT service", "국외전산용역비");
        alias(map, "Foreign branch line usage fees", "국외회선사용료");
        alias(map, "Foreign branch IT maintenance fees", "국외유지보수료");
        alias(map, "IT Service", "전산용역비");
        alias(map, "IT Expenses", "전산제비");
        alias(map, "IT Lease", "전산임차료");
        alias(map, "IT Travel", "전산여비");
        // 자본예산 중분류. 시트 1-2·②의 `구분` 열과 시트 ③의 세부비목 열 양쪽에 나타납니다.
        alias(map, "Machinery", "기계장치(HW)");
        return Map.copyOf(map);
    }

    private static void alias(Map<String, String> map, String formName, String codeName) {
        map.put(SheetAnchorScanner.normalize(formName), codeName);
    }
}
