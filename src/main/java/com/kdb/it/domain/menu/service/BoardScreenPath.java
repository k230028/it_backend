package com.kdb.it.domain.menu.service;

/**
 * 게시판 화면과 게시판을 잇는 경로 규약.
 *
 * <p>게시판 참조는 별도 컬럼이 아니라 화면경로({@code /board/{게시판관리번호}})로 표현한다. 메뉴 저장 검증(AdminMenuService)과 사용자 트리
 * 필터(MenuQueryService)가 같은 규약을 봐야 하므로 문자열을 여기 한 곳에 둔다.
 */
public final class BoardScreenPath {
    private static final String PATH_PREFIX = "/board/";

    private BoardScreenPath() {}

    /**
     * 화면경로가 게시판 후보 접두사로 시작하는지 판정한다.
     *
     * @param srePth 화면경로
     * @return {@code /board/} 접두사로 시작하면 true, null이거나 다른 경로면 false
     */
    public static boolean isBoardPath(String srePth) {
        return srePth != null && srePth.startsWith(PATH_PREFIX);
    }

    /**
     * 게시판관리번호로 화면경로를 만든다.
     *
     * @param blbMngNo 게시판관리번호 (예: BLBM-0001)
     * @return {@code /board/{게시판관리번호}} 형식의 화면경로
     */
    public static String pathOf(String blbMngNo) {
        return PATH_PREFIX + blbMngNo;
    }

    /**
     * 화면경로에서 게시판관리번호를 꺼낸다.
     *
     * @param srePth 화면경로
     * @return 게시판관리번호. 접두사가 없거나 번호가 비었거나 하위 경로가 더 붙어 있으면 null
     */
    public static String boardNoOf(String srePth) {
        if (srePth == null || !srePth.startsWith(PATH_PREFIX)) return null;
        String blbMngNo = srePth.substring(PATH_PREFIX.length());
        return blbMngNo.isBlank() || blbMngNo.contains("/") ? null : blbMngNo;
    }
}
