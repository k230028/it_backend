package com.kdb.it.common.util;

import java.util.regex.Pattern;

/**
 * 담당자 표시명 결정 유틸.
 *
 * <p>정보화사업 담당자 컬럼({@code USID}, {@code TLR_USID}, {@code DVM_USID}, {@code DVM_TLR_USID})은 사번 <b>또는
 * 이름</b>을 담는다(엔티티 {@code Bprojm} 주석 참조). 사번이 저장된 행은 {@code TPRMPP_CUSERI} 조회로 사용자명을 얻지만, 이름이 그대로
 * 저장된 행은 조회가 실패해 이름이 비어 화면에 표시할 값이 없어진다.
 *
 * <p>이때 저장값 자체가 이름이므로 그대로 표시명으로 쓴다. 다만 조회에 실패한 <b>사번 형태</b>의 값은 퇴직·미등록 사번으로 보고 이름처럼 노출하지 않는다.
 *
 * <p>사번 형태는 <b>ASCII 영숫자만 쓰고 숫자를 하나 이상 포함</b>하는 값이다(실측 {@code TPRMPP_CUSERI.ENO}: `K140024` 형태 25건,
 * `ABCD123` 형태 1건 — 모두 7자, 공백 없음). "ASCII이면 사번"으로 보면 <b>영문 성명이 사번으로 오인된다</b> — 해외 점포 제출본의 담당자
 * (`Luke Buckingham-Brown`)가 이름 자리에 뜨지 않고 행번 자리에만 남았고, 화면에서는 조회되지 않는 사번 링크가 걸렸다. 영문 이름에는 숫자가 없고 대개
 * 공백·하이픈이 들어가므로 이 형태로 갈리며, 국문 이름은 애초에 ASCII가 아니라 그대로 이름으로 남는다.
 */
public final class UserNameResolver {

    /**
     * 사번 형태 — ASCII 영숫자만 쓰고 숫자를 하나 이상 포함한다.
     *
     * <p>숫자를 요구하는 이유는 숫자가 없는 ASCII 값(단일 토큰 영문 이름 등)을 사번으로 오인하지 않기 위함이다.
     */
    private static final Pattern EMPLOYEE_NUMBER = Pattern.compile("(?=.*\\d)[A-Za-z0-9]+");

    private UserNameResolver() {}

    /**
     * 담당자 표시명을 결정한다.
     *
     * @param storedValue 담당자 컬럼 저장값 — 사번 또는 이름(null 허용)
     * @param lookedUpName 저장값을 사번으로 보고 조회한 사용자명(조회 실패 시 null)
     * @return 조회된 사용자명. 없으면 저장값이 사번 형태가 아닐 때 저장값(=이름), 그 외에는 null
     */
    public static String resolve(String storedValue, String lookedUpName) {
        if (lookedUpName != null && !lookedUpName.isBlank()) {
            return lookedUpName;
        }
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        String trimmed = storedValue.trim();
        return isEmployeeNumber(trimmed) ? null : trimmed;
    }

    /**
     * 저장값이 사번이 아니라 이름인지 판정합니다.
     *
     * @param storedValue 담당자 컬럼 저장값
     * @param lookedUpName 사번으로 조회한 사용자명
     * @return 조회가 비었고 저장값이 사번 형태가 아니면 true
     */
    public static boolean isStoredName(String storedValue, String lookedUpName) {
        return (lookedUpName == null || lookedUpName.isBlank())
                && storedValue != null
                && !storedValue.isBlank()
                && !isEmployeeNumber(storedValue.trim());
    }

    private static boolean isEmployeeNumber(String value) {
        return EMPLOYEE_NUMBER.matcher(value).matches();
    }
}
