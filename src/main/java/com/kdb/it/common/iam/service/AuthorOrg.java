package com.kdb.it.common.iam.service;

/**
 * 작성자(현재 로그인 사용자) 소속 조직 스냅샷.
 *
 * <p>신규 마스터 레코드 생성 시 "작성자 기준"으로 채우는 주관부서코드/주관팀코드/인사상위조직코드내용을 한 번에 전달하기 위한 값 객체입니다. 미인증이거나 사용자 정보를
 * 찾지 못하면 각 필드는 {@code null}입니다 (해당 컬럼은 모두 nullable).
 *
 * @param svnDpmC 주관부서코드 (작성자 소속 부서코드 = {@code CuserI.bbrC})
 * @param svnTemC 주관팀코드 (작성자 소속 팀코드 = {@code CuserI.temC})
 * @param prlmHrkOgzCCone 인사상위조직코드내용 (작성자 소속 조직의 상위조직코드 = {@code CuserI.getPrlmHrkOgzCCone()})
 */
public record AuthorOrg(String svnDpmC, String svnTemC, String prlmHrkOgzCCone) {

    private static final AuthorOrg EMPTY = new AuthorOrg(null, null, null);

    /**
     * 미인증·미조회 시 사용할 빈 스냅샷(모든 필드 {@code null})을 반환합니다.
     *
     * @return 모든 필드가 {@code null}인 빈 스냅샷
     */
    public static AuthorOrg empty() {
        return EMPTY;
    }
}
