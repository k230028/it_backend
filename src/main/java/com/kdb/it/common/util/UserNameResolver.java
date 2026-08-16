package com.kdb.it.common.util;

import java.util.regex.Pattern;

/**
 * 담당자 표시명 결정 유틸.
 *
 * <p>정보화사업 담당자 컬럼({@code USID}, {@code TLR_USID}, {@code DVM_USID}, {@code DVM_TLR_USID})은 사번 <b>또는
 * 이름</b>을 담는다(엔티티 {@code Bprojm} 주석 참조). 사번이 저장된 행은 {@code TPRMPP_CUSERI} 조회로 사용자명을 얻지만, 이름이 그대로
 * 저장된 행은 조회가 실패해 이름이 비어 화면에 표시할 값이 없어진다.
 *
 * <p>이때 저장값 자체가 이름이므로 그대로 표시명으로 쓴다. 다만 사번({@code TPRMPP_CUSERI.ENO})은 항상 ASCII이므로, 조회에 실패한 ASCII 값은
 * 퇴직·미등록 사번으로 보고 이름처럼 노출하지 않는다. 한글 등 비ASCII가 섞인 값만 이름으로 판정한다.
 */
public final class UserNameResolver {

    /** 사번은 ASCII 문자만 사용한다. 이 패턴에 걸리면(한글 등) 사번이 아니라 이름이 저장된 값이다. */
    private static final Pattern NON_ASCII = Pattern.compile("[^\\p{ASCII}]");

    private UserNameResolver() {}

    /**
     * 담당자 표시명을 결정한다.
     *
     * @param storedValue 담당자 컬럼 저장값 — 사번 또는 이름(null 허용)
     * @param lookedUpName 저장값을 사번으로 보고 조회한 사용자명(조회 실패 시 null)
     * @return 조회된 사용자명. 없으면 저장값에 비ASCII가 섞였을 때 저장값(=이름), 그 외에는 null
     */
    public static String resolve(String storedValue, String lookedUpName) {
        if (lookedUpName != null && !lookedUpName.isBlank()) {
            return lookedUpName;
        }
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        String trimmed = storedValue.trim();
        return NON_ASCII.matcher(trimmed).find() ? trimmed : null;
    }
}
