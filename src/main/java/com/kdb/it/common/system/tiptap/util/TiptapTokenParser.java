package com.kdb.it.common.system.tiptap.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Tiptap 변수 토큰 파서.
 *
 * <p>토큰 구조: {@code <YEAR>.<CATEGORY>[.<PROJECT_CODE>].<ITEM>} 비-사업 카테고리(itBudget/capBudget/opex)는
 * PROJECT_CODE를 갖지 않으며, 사업 카테고리(proj)는 PROJECT_CODE 세그먼트가 필수입니다. 설계 참조: §3.7 토큰 문법
 */
@Component
public class TiptapTokenParser {

    /** 토큰에서 허용하는 변수 카테고리. */
    public enum Category {
        IT_BUDGET,
        CAP_BUDGET,
        OPEX,
        PROJ
    }

    /**
     * 토큰 파싱 결과.
     *
     * @param valid 형식이 허용 문법과 일치하면 true
     * @param year 토큰의 4자리 기준연도, invalid 결과에서는 null
     * @param category 변수 카테고리, invalid 결과에서는 null
     * @param projectCode 사업별 토큰일 때만 존재하는 사업코드
     * @param item 금액/편성률 항목명, invalid 결과에서는 null
     */
    public record ParseResult(
            boolean valid, Integer year, Category category, String projectCode, String item) {
        /** 형식 오류를 예외로 던지지 않고 서비스 계층의 INVALID 상태로 전달하기 위한 실패 결과를 생성합니다. */
        public static ParseResult invalid() {
            return new ParseResult(false, null, null, null, null);
        }
    }

    private static final Pattern NON_PROJ_PATTERN =
            Pattern.compile(
                    "^(\\d{4})\\.(itBudget|capBudget|opex)\\.(requestAmount|allocatedAmount|allocationRate)$");

    private static final Pattern PROJ_PATTERN =
            Pattern.compile(
                    "^(\\d{4})\\.proj\\.([A-Z0-9_-]+)\\.(requestAmount|allocatedAmount|allocationRate)$");

    /**
     * Tiptap 변수 토큰을 구조화된 값으로 파싱합니다.
     *
     * @param token 원본 토큰 문자열
     * @return 허용 문법에 맞으면 valid 결과, null/blank/형식 불일치이면 {@link ParseResult#invalid()}
     */
    public ParseResult parse(String token) {
        if (token == null || token.isBlank()) {
            return ParseResult.invalid();
        }

        Matcher nonProj = NON_PROJ_PATTERN.matcher(token);
        if (nonProj.matches()) {
            int year = Integer.parseInt(nonProj.group(1));
            Category category = mapCategory(nonProj.group(2));
            String item = nonProj.group(3);
            return new ParseResult(true, year, category, null, item);
        }

        Matcher proj = PROJ_PATTERN.matcher(token);
        if (proj.matches()) {
            int year = Integer.parseInt(proj.group(1));
            String projectCode = proj.group(2);
            String item = proj.group(3);
            return new ParseResult(true, year, Category.PROJ, projectCode, item);
        }

        return ParseResult.invalid();
    }

    private Category mapCategory(String literal) {
        return switch (literal) {
            case "itBudget" -> Category.IT_BUDGET;
            case "capBudget" -> Category.CAP_BUDGET;
            case "opex" -> Category.OPEX;
            default -> throw new IllegalArgumentException("알 수 없는 카테고리 리터럴: " + literal);
        };
    }
}
