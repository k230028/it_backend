package com.kdb.it.common.system.tiptap.util;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiptap 변수 토큰 파서.
 *
 * <p>
 * 토큰 구조: {@code <YEAR>.<CATEGORY>[.<PROJECT_CODE>].<ITEM>}
 * 비-사업 카테고리(itBudget/capBudget/opex)는 PROJECT_CODE를 갖지 않으며,
 * 사업 카테고리(proj)는 PROJECT_CODE 세그먼트가 필수입니다.
 * </p>
 *
 * Design Ref: §3.7 토큰 문법
 */
@Component
public class TiptapTokenParser {

    public enum Category { IT_BUDGET, CAP_BUDGET, OPEX, PROJ }

    public record ParseResult(boolean valid, Integer year, Category category,
                              String projectCode, String item) {
        public static ParseResult invalid() {
            return new ParseResult(false, null, null, null, null);
        }
    }

    private static final Pattern NON_PROJ_PATTERN =
            Pattern.compile("^(\\d{4})\\.(itBudget|capBudget|opex)\\.(requestAmount|allocatedAmount|allocationRate)$");

    private static final Pattern PROJ_PATTERN =
            Pattern.compile("^(\\d{4})\\.proj\\.([A-Z0-9_-]+)\\.(requestAmount|allocatedAmount|allocationRate)$");

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
            case "itBudget"  -> Category.IT_BUDGET;
            case "capBudget" -> Category.CAP_BUDGET;
            case "opex"      -> Category.OPEX;
            default          -> throw new IllegalArgumentException("알 수 없는 카테고리 리터럴: " + literal);
        };
    }
}
