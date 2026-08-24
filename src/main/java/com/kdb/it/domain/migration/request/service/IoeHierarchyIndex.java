package com.kdb.it.domain.migration.request.service;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 비목(IOE_C) 공통코드의 계층 인덱스입니다.
 *
 * <p>`CO_CDVA_SPS`가 `일반관리비 - 전산임차료 - 국내전산임차료` 형태의 계층 문자열이라, 편성요청서 시트 ③의 (A열 비목명, B열 세부비목) 쌍과 그대로
 * 대응합니다. 코드값명 하나로 맞추는 것보다 견고합니다 — 양식 표기와 코드값명이 어긋나는 경우(`국외전산유지보수료` 대 `국외유지보수료`)에도 중분류가 함께 걸려 오매칭이
 * 줄어듭니다.
 *
 * <p>시트 1-2·②는 세부 없이 중분류(`기계장치(HW)` 등)만 적혀 있어 통화의 국내·국외 구분을 두 번째 열쇠로 씁니다.
 */
@Component
@RequiredArgsConstructor
public class IoeHierarchyIndex {

    /** 중분류·국내여부로 유일하게 좁혀지지 않을 때 쓸 기본 코드. 나머지는 대안 후보로 함께 제시합니다. */
    private static final Map<String, String> DEFAULT_CODE_BY_GROUP =
            Map.of("개발비", "103", "기타무형자산", "106");

    /** 중분류·세부가 여러 코드에 걸릴 때 업무적으로 확정된 기본 코드값명입니다. */
    private static final Map<String, String> DEFAULT_NAME_BY_DETAIL =
            Map.of("전산용역비 외주용역", "외주용역(외주운영/관제 등)");

    /**
     * 국외 부점이 중분류만 적었을 때 쓸 기본 코드값명입니다.
     *
     * <p>국외 부점(`9**`)의 전산제비는 세부를 나누지 않고 `국외전산제비` 한 항목으로 편성합니다. 국외 세부가 회선사용료·유지보수료·기타제비로 갈려 있어 중분류만
     * 적힌 행이 늘 중의적으로 남던 자리입니다.
     *
     * <p>코드값이 아니라 <b>코드값명</b>으로 적는 이유는 같은 비목의 코드값이 환경마다 다를 수 있기 때문입니다({@link
     * #DEFAULT_CODE_BY_GROUP}는 자본예산 계열이라 코드값이 고정되어 있습니다).
     */
    private static final Map<String, String> FOREIGN_DEFAULT_NAME_BY_GROUP =
            Map.of("전산제비", "국외전산제비");

    /** 계층 문자열의 구분자. `대분류 - 중분류 - 세부` 형태입니다. */
    private static final String HIERARCHY_DELIMITER = "\\s*-\\s*";

    /** 인덱스 키의 중분류·세부 구분자. 정규화로 공백이 제거된 뒤라 공백은 안전한 구분자입니다. */
    private static final String KEY_DELIMITER = " ";

    private final CodeRepository codeRepository;

    /**
     * 비목 공통코드를 전량 읽어 인덱스를 만듭니다.
     *
     * <p>dry-run·commit 각 배치의 시작에서 한 번만 만들고 그 배치 안에서 재사용합니다. 파일마다 조회하면 수백 회 조회가 됩니다.
     *
     * @return 계층 인덱스 스냅샷
     */
    public Snapshot snapshot() {
        return new Snapshot(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"));
    }

    /** 비목 계층 인덱스입니다. 한 배치 처리 동안만 살아 있습니다. */
    public static final class Snapshot {

        /** (중분류, 세부) 정규화 키 → 코드 목록. 둘 이상이면 중의적입니다. */
        private final Map<String, List<Ccodem>> byDetail = new LinkedHashMap<>();

        /** 중분류 정규화 키 → 코드 목록. */
        private final Map<String, List<Ccodem>> byGroup = new LinkedHashMap<>();

        /** 코드값명 정규화 키 → 코드. 양식이 구체적인 코드값명을 적은 경우에 사용합니다. */
        private final Map<String, Ccodem> byName = new LinkedHashMap<>();

        private final Set<String> codes = new LinkedHashSet<>();

        private Snapshot(List<Ccodem> allCodes) {
            for (Ccodem code : allCodes) {
                if (code.getCdva() == null) continue;
                codes.add(code.getCdva().trim());
                if (code.getCdvaNm() != null) {
                    byName.put(SheetAnchorScanner.normalize(code.getCdvaNm()), code);
                }
                String[] parts = splitHierarchy(code.getCdvaDtl());
                if (parts.length < 3) continue;
                String group = SheetAnchorScanner.normalize(parts[1]);
                String detail = SheetAnchorScanner.normalize(parts[2]);
                byGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(code);
                byDetail.computeIfAbsent(group + KEY_DELIMITER + detail, key -> new ArrayList<>())
                        .add(code);
            }
        }

        /**
         * 시트 ③의 (중분류, 세부) 쌍으로 비목을 찾습니다.
         *
         * @param midCategory A열 비목명 (`전산 임차료`, `IT Expenses` 등)
         * @param detailName B열 세부비목 (`국내전산임차료`, `Foreign branch line usage fees` 등)
         * @return 해석 결과. 후보가 둘 이상이면 중의적, 없으면 미해석
         */
        public Resolution resolveByDetail(String midCategory, String detailName) {
            String group = SheetAnchorScanner.normalize(FormLexicon.canonicalIoeName(midCategory));
            String detail = SheetAnchorScanner.normalize(FormLexicon.canonicalIoeName(detailName));
            Ccodem exactName = byName.get(detail);
            if (exactName != null) return Resolution.of(exactName);
            List<Ccodem> matches = byDetail.get(group + KEY_DELIMITER + detail);
            if (matches == null || matches.isEmpty()) return Resolution.unresolved(detailName);
            if (matches.size() == 1) return Resolution.of(matches.get(0));
            Ccodem defaulted =
                    findByName(matches, DEFAULT_NAME_BY_DETAIL.get(group + KEY_DELIMITER + detail));
            if (defaulted != null) return Resolution.of(defaulted);
            return Resolution.ambiguous(detailName, matches);
        }

        /**
         * 시트 1-2·②의 중분류와 통화로 비목을 찾습니다.
         *
         * <p>중분류의 괄호 표기(`기계장치(HW)`)를 떼고 계층의 중분류(`기계장치`)와 맞춥니다. 국내·국외는 세부가 `국외`로 시작하는지로 가릅니다 — 세부에
         * 국내·국외 구분이 없는 중분류(개발비)에서는 이 필터가 아무것도 걸러내지 않아 기본값 규칙으로 넘어갑니다.
         *
         * @param groupLabel 시트의 중분류 표기
         * @param domestic 원화 행이면 true, 외화 행이면 false
         * @return 해석 결과. 기본값이 있으면 그 코드와 대안 후보를 함께 담습니다
         */
        public Resolution resolveByGroup(String groupLabel, boolean domestic) {
            String group =
                    SheetAnchorScanner.normalize(
                            stripParenthetical(FormLexicon.canonicalIoeName(groupLabel)));
            List<Ccodem> matches = byGroup.get(group);
            if (matches == null || matches.isEmpty()) return Resolution.unresolved(groupLabel);

            List<Ccodem> narrowed = new ArrayList<>();
            for (Ccodem code : matches) {
                String[] parts = splitHierarchy(code.getCdvaDtl());
                boolean foreign =
                        parts.length >= 3
                                && SheetAnchorScanner.normalize(parts[2]).startsWith("국외");
                if (foreign != domestic) narrowed.add(code);
            }
            if (narrowed.isEmpty()) narrowed = matches;
            if (narrowed.size() == 1) return Resolution.of(narrowed.get(0));

            if (!domestic) {
                Ccodem foreignDefault =
                        findByName(narrowed, FOREIGN_DEFAULT_NAME_BY_GROUP.get(group));
                if (foreignDefault != null) return Resolution.withDefault(foreignDefault, narrowed);
            }

            String defaultCode = DEFAULT_CODE_BY_GROUP.get(group);
            if (defaultCode != null) {
                for (Ccodem code : narrowed) {
                    if (defaultCode.equals(code.getCdva().trim())) {
                        return Resolution.withDefault(code, narrowed);
                    }
                }
            }
            return Resolution.ambiguous(groupLabel, narrowed);
        }

        /**
         * 비목코드가 실재하는지 확인합니다. 미리보기 보정값을 신뢰하기 전에 씁니다.
         *
         * @param ioeCode 비목코드
         * @return 등록되어 있으면 true. null이면 false
         */
        public boolean exists(String ioeCode) {
            return ioeCode != null && codes.contains(ioeCode.trim());
        }

        /**
         * 비목 전체를 후보로 돌려줍니다.
         *
         * <p>해석이 좁혀지지 않았을 때 <b>고를 수단</b>을 주기 위한 마지막 보루입니다. "다른 비목이면 골라 주세요"라고 하면서 후보를 비워 두면 화면에
         * 드롭다운이 그려지지 않아 사용자가 손댈 방법이 없습니다.
         *
         * @return 코드 오름차순 후보 목록
         */
        public List<MigrationDto.Candidate> allCandidates() {
            List<Ccodem> all = new ArrayList<>();
            for (List<Ccodem> group : byGroup.values()) all.addAll(group);
            all.sort(Comparator.comparing(code -> code.getCdva().trim()));
            List<MigrationDto.Candidate> out = new ArrayList<>();
            for (Ccodem code : all) {
                MigrationDto.Candidate candidate =
                        new MigrationDto.Candidate(code.getCdva().trim(), code.getCdvaNm());
                if (!out.contains(candidate)) out.add(candidate);
            }
            return List.copyOf(out);
        }

        /**
         * 후보 중 코드값명이 일치하는 코드를 찾습니다.
         *
         * @param candidates 좁혀진 후보
         * @param codeName 찾을 코드값명. null이면 찾지 않습니다
         * @return 일치하는 코드. 없으면 null
         */
        private static Ccodem findByName(List<Ccodem> candidates, String codeName) {
            if (codeName == null) return null;
            String normalized = SheetAnchorScanner.normalize(codeName);
            for (Ccodem candidate : candidates) {
                if (normalized.equals(SheetAnchorScanner.normalize(candidate.getCdvaNm()))) {
                    return candidate;
                }
            }
            return null;
        }

        private static String[] splitHierarchy(String hierarchy) {
            return hierarchy == null ? new String[0] : hierarchy.split(HIERARCHY_DELIMITER);
        }

        private static String stripParenthetical(String label) {
            if (label == null) return "";
            int open = label.indexOf('(');
            return open > 0 ? label.substring(0, open) : label;
        }
    }

    /**
     * 비목 해석 결과입니다.
     *
     * @param code 확정된 비목코드. 미해석·중의적이면 null
     * @param label 사용자에게 보여줄 이름. 미해석이면 입력 원문
     * @param candidates 보정 후보. 확정이고 대안도 없으면 빈 목록
     * @param ambiguous 후보들이 동등하게 성립하는 중의적 상태인지 여부
     */
    public record Resolution(
            String code, String label, List<MigrationDto.Candidate> candidates, boolean ambiguous) {

        private static Resolution of(Ccodem code) {
            return new Resolution(code.getCdva().trim(), code.getCdvaNm(), List.of(), false);
        }

        private static Resolution withDefault(Ccodem chosen, List<Ccodem> all) {
            return new Resolution(
                    chosen.getCdva().trim(), chosen.getCdvaNm(), toCandidates(all), false);
        }

        private static Resolution unresolved(String input) {
            return new Resolution(null, input == null ? "" : input, List.of(), false);
        }

        private static Resolution ambiguous(String input, List<Ccodem> all) {
            return new Resolution(null, input == null ? "" : input, toCandidates(all), true);
        }

        private static List<MigrationDto.Candidate> toCandidates(List<Ccodem> all) {
            List<MigrationDto.Candidate> candidates = new ArrayList<>();
            for (Ccodem code : all) {
                candidates.add(new MigrationDto.Candidate(code.getCdva().trim(), code.getCdvaNm()));
            }
            return List.copyOf(candidates);
        }

        /** 확정되지 않았고 중의적이지도 않은 상태인지 판정합니다. */
        public boolean isUnresolved() {
            return code == null && !ambiguous;
        }

        /** 후보들이 동등하게 성립하는 중의적 상태인지 판정합니다. */
        public boolean isAmbiguous() {
            return ambiguous;
        }
    }
}
