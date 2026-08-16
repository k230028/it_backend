package com.kdb.it.domain.migration.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 종합본 한 행을 어떻게 처리할지에 대한 관리자의 결정입니다.
 *
 * <p>전송은 기존 {@link MigrationDto.CellOverride} 경로를 그대로 씁니다 — 예약 컬럼 {@link #COLUMN}에 이 클래스가 정의한 문자열을
 * 담습니다. 결정 전용 DTO를 새로 두면 dry-run·commit 요청 계약이 갈라지고 프론트의 보정값 병합 코드가 두 벌이 되므로, 컬럼 id 하나만 늘립니다.
 *
 * <p>후보 드롭다운도 {@link MigrationDto.Candidate}를 그대로 쓰므로 화면은 다른 진단과 같은 위젯으로 결정을 받습니다.
 *
 * @param kind 결정 종류
 * @param pk 매칭 대상 원장 PK. {@link Kind#MATCH}가 아니면 null
 */
public record RowDecision(Kind kind, String pk) {

    /** 결정을 담는 예약 컬럼 id입니다. 어떤 시트의 정규 컬럼과도 겹치지 않습니다. */
    public static final String COLUMN = "__decision";

    private static final String MATCH_PREFIX = "MATCH:";

    /** 결정 종류입니다. */
    public enum Kind {
        /** 후보로 제시된 기존 원장에 편성합니다. */
        MATCH,
        /** 원장을 새로 만들고 편성합니다. 요청서 없이 종합본만 있는 최초 이관용입니다. */
        CREATE_NEW,
        /** 이 행을 편성 대상에서 제외합니다. */
        SKIP
    }

    /**
     * 보정값 문자열을 결정으로 해석합니다.
     *
     * @param value 보정값. {@code MATCH:{PK}}·{@code CREATE_NEW}·{@code SKIP}
     * @return 해석된 결정. null·공백·미인식 형식이면 null (결정하지 않은 것으로 봅니다)
     */
    public static RowDecision parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith(MATCH_PREFIX)) {
            String pk = trimmed.substring(MATCH_PREFIX.length()).trim();
            return pk.isEmpty() ? null : new RowDecision(Kind.MATCH, pk);
        }
        if (Kind.CREATE_NEW.name().equals(trimmed)) {
            return new RowDecision(Kind.CREATE_NEW, null);
        }
        if (Kind.SKIP.name().equals(trimmed)) {
            return new RowDecision(Kind.SKIP, null);
        }
        return null;
    }

    /**
     * 원장 PK를 매칭 결정 값으로 감쌉니다.
     *
     * @param pk 원장 PK
     * @return 보정값 문자열
     */
    public static String matchValue(String pk) {
        return MATCH_PREFIX + pk;
    }

    /**
     * 원장 후보를 결정 드롭다운 선택지로 바꿉니다.
     *
     * <p>후보를 비워 두면 화면에 드롭다운이 그려지지 않아 손댈 방법이 없으므로, 원장 후보가 없어도 {@code CREATE_NEW}·{@code SKIP} 두
     * 항목은 항상 붙입니다.
     *
     * @param ledgerCandidates 매처가 낸 원장 후보 (code=PK, label=이름)
     * @return 결정 선택지
     */
    public static List<MigrationDto.Candidate> decisionCandidates(
            List<MigrationDto.Candidate> ledgerCandidates) {
        List<MigrationDto.Candidate> out = new ArrayList<>();
        for (MigrationDto.Candidate candidate : ledgerCandidates) {
            out.add(
                    new MigrationDto.Candidate(
                            matchValue(candidate.code()), "기존 사업에 편성: " + candidate.label()));
        }
        out.add(new MigrationDto.Candidate(Kind.CREATE_NEW.name(), "원장을 새로 만들고 편성"));
        out.add(new MigrationDto.Candidate(Kind.SKIP.name(), "이 행은 편성하지 않음"));
        return out;
    }
}
