package com.kdb.it.domain.menu.repository;

/**
 * 메뉴 트리 조립 전용 경량 프로젝션.
 *
 * <p>{@code Cmenum} 엔티티 대신 이 record로 읽는 이유는 두 가지다. 첫째, 메뉴 트리는 응답 직렬화 전용 조회다(it_backend/CLAUDE.md
 * §4). 둘째, {@code IMK_NM} 컬럼이 없는 환경에서는 select 목록에서 그 컬럼을 빼야 하는데 엔티티 매핑은 정적이라 그럴 수 없다.
 *
 * @param imkNm 아이콘 클래스. 컬럼이 없는 환경에서는 항상 {@code null}이며 서비스 계층이 {@code MenuIconDefaults}로 채운다
 */
public record MenuTreeRow(
        String mnuId,
        String hrkMnuId,
        String mnuNm,
        String mnuTpC,
        String srePth,
        Integer mnuSotSqnSno,
        String hidYn,
        Integer mnuDep,
        String whlMnuPth,
        String imkNm) {

    /** {@code IMK_NM} 컬럼이 없는 환경용 보조 생성자. 아이콘을 select하지 않고 {@code null}로 채운다. */
    public MenuTreeRow(
            String mnuId,
            String hrkMnuId,
            String mnuNm,
            String mnuTpC,
            String srePth,
            Integer mnuSotSqnSno,
            String hidYn,
            Integer mnuDep,
            String whlMnuPth) {
        this(mnuId, hrkMnuId, mnuNm, mnuTpC, srePth, mnuSotSqnSno, hidYn, mnuDep, whlMnuPth, null);
    }
}
