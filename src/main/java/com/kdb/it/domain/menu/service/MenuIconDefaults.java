package com.kdb.it.domain.menu.service;

import java.util.Map;

/**
 * {@code TPRMPP_CMENUM.IMK_NM} 컬럼이 없는 환경에서만 쓰는 메뉴 아이콘 기본값.
 *
 * <p>값의 출처는 2026-08-13 로컬 DB {@code TPRMPP_CMENUM}의 현재 값({@code DEL_YN='N'} 중 {@code IMK_NM IS NOT
 * NULL}) 42건이다. {@code V20260806_002__AddMenuIconColumn.sql}의 시드가 아니라 실 DB를 뜬 이유는, 시드 이후 관리 화면에서
 * 편집된 값({@code MAUD0003}·{@code MAUD0009})과 시드 이후 생성된 메뉴({@code MNU0001006}·{@code MNU0001012})를
 * 포함해야 하기 때문이다.
 *
 * <p>컬럼이 있는 정상 환경에서는 한 번도 읽히지 않는다. 아이콘의 단일 출처는 여전히 메뉴 행이며, 이 맵은 갱신 의무가 없는 고정 스냅샷이다 — 컬럼이 없는 환경은 어차피
 * 아이콘 편집이 불가능하다.
 */
public final class MenuIconDefaults {

    private static final Map<String, String> ICONS =
            Map.ofEntries(
                    Map.entry("MADM0001", "pi pi-sitemap"),
                    Map.entry("MADM0002", "pi pi-link"),
                    Map.entry("MADM0003", "pi pi-chart-line"),
                    Map.entry("MADM0004", "pi pi-database"),
                    Map.entry("MADM0010", "pi pi-comments"),
                    Map.entry("MADM0012", "pi pi-shield"),
                    Map.entry("MADM0016", "pi pi-bolt"),
                    Map.entry("MADM0017", "pi pi-history"),
                    Map.entry("MADM0018", "pi pi-wallet"),
                    Map.entry("MADM0020", "pi pi-chart-bar"),
                    Map.entry("MADM0022", "pi pi-briefcase"),
                    Map.entry("MADM0025", "pi pi-desktop"),
                    Map.entry("MADM0028", "pi pi-file-check"),
                    Map.entry("MADM0032", "pi pi-shield"),
                    Map.entry("MAPV0001", "pi pi-home"),
                    Map.entry("MAPV0002", "pi pi-inbox"),
                    Map.entry("MAPV0005", "pi pi-send"),
                    Map.entry("MAUD0001", "pi pi-home"),
                    Map.entry("MAUD0002", "pi pi-check-square"),
                    Map.entry("MAUD0003", "pi pi-clock"),
                    Map.entry("MAUD0008", "pi pi-cog"),
                    Map.entry("MAUD0009", "pi pi-clock"),
                    Map.entry("MBRD0001", "pi pi-comments"),
                    Map.entry("MCDP0001", "pi pi-clock"),
                    Map.entry("MDOC0001", "pi pi-home"),
                    Map.entry("MDOC0002", "pi pi-folder"),
                    Map.entry("MDOC0005", "pi pi-chart-pie"),
                    Map.entry("MHED0001", "pi pi-file-check"),
                    Map.entry("MHED0002", "pi pi-wallet"),
                    Map.entry("MHED0003", "pi pi-sparkles"),
                    Map.entry("MHED0004", "pi pi-check-square"),
                    Map.entry("MHED0005", "pi pi-send"),
                    Map.entry("MHED0006", "pi pi-comments"),
                    Map.entry("MHED0007", "pi pi-cog"),
                    Map.entry("MINF0001", "pi pi-home"),
                    Map.entry("MINF0002", "pi pi-book"),
                    Map.entry("MINF0003", "pi pi-wallet"),
                    Map.entry("MINF0009", "pi pi-chart-pie"),
                    Map.entry("MINF0012", "pi pi-chart-bar"),
                    Map.entry("MINF0015", "pi pi-briefcase"),
                    Map.entry("MNU0001006", "pi pi-sitemap"),
                    Map.entry("MNU0001012", "pi pi-upload"));

    private MenuIconDefaults() {}

    /**
     * 메뉴ID에 대응하는 기본 아이콘 클래스를 반환한다.
     *
     * @param mnuId 메뉴ID. {@code null}이면 {@code null}을 반환한다
     * @return 스냅샷에 있는 아이콘 클래스. 스냅샷에 없으면 {@code null} — 호출부는 그대로 내려보내고 프론트 {@code iconFor()}가 기본
     *     아이콘으로 받는다
     */
    public static String iconOf(String mnuId) {
        return mnuId == null ? null : ICONS.get(mnuId);
    }

    /**
     * 스냅샷 전량.
     *
     * @return 불변 {@code MNU_ID → 아이콘 클래스} 맵
     */
    public static Map<String, String> all() {
        return ICONS;
    }
}
