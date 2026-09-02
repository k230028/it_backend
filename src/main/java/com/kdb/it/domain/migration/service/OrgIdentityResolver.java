package com.kdb.it.domain.migration.service;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 엑셀의 부서명·팀명·담당자명을 조직코드·사번으로 해석합니다.
 *
 * <p>수기 엑셀에는 코드가 전혀 없고 이름만 있습니다. 행마다 DB를 조회하면 N+1이 되므로 {@link #snapshot()}으로 조직·사용자를 각 1회 전량 읽어 메모리
 * 인덱스를 만들고, 그 인덱스에서 매칭합니다. 조직·직원 규모가 작아 전량 로드가 타당합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrgIdentityResolver {

    /**
     * 팀·부서 미지정을 뜻하는 엑셀 표기. 이 값들은 해석하지 않고 미해석으로 둡니다.
     *
     * <p>하이픈류는 반각 하이픈(-), en dash(–), em dash(—), 전각 하이픈(－) 네 글자를 모두 포함합니다. 누락되면 부분 일치 단계로 흘러 들어가
     * 스푸리어스 후보를 만듭니다.
     */
    private static final List<String> BLANK_TOKENS = List.of("", "-", "–", "—", "－", "없음", "해당없음");

    /**
     * 부서 폴더명에 부서코드를 병기하는 표기(`부서명(부서코드)`)에서 꼬리 괄호를 떼어냅니다.
     *
     * <p>괄호 안을 <b>영숫자로만</b> 제한합니다. 부점이 폴더명에 붙이는 괄호는 코드만 있는 것이 아니라 `2026년 요청서(최종)`처럼 한글 메모인 경우도 많은데,
     * 이런 값을 코드 자리로 읽으면 조회가 헛돌고 이름 해석까지 늦어집니다. 반각·전각 괄호를 모두 받는 것은 부점 제출본에 전각 괄호가 섞여 오기 때문입니다.
     */
    private static final Pattern FOLDER_CODE_SUFFIX =
            Pattern.compile("^(.*?)\\s*[(（]\\s*([0-9A-Za-z]{1,100})\\s*[)）]$");

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    /** 유사도 제안 후보의 최대 개수. 드롭다운 선택지가 되므로 훑어볼 수 있는 수로 제한합니다. */
    private static final int MAX_SUGGESTIONS = 5;

    /**
     * 유사도 채택 하한. `가장 긴 공통 부분문자열 / 짧은 쪽 길이`가 이 값 이상이어야 후보로 냅니다.
     *
     * <p>0.5는 한글 3자 이름에서 2자가 겹칠 때(`김성원`↔`김성완`)는 제안하고 1자만 겹칠 때(`김철수`↔`김영희`)는 제안하지 않는 경계입니다.
     */
    private static final double SUGGESTION_THRESHOLD = 0.5d;

    /**
     * 해석 결과입니다.
     *
     * @param code 확정된 코드값. 미확정이면 null
     * @param label 확정된 표시명. 미확정이면 입력 원문
     * @param candidates 보정 후보. 중의적일 때의 동등 후보이거나, 미해석일 때의 유사도 제안입니다. 확정이면 빈 목록
     * @param ambiguous 후보들이 동등하게 성립하는 중의적 상태인지 여부. 미해석 + 유사도 제안이면 false
     */
    public record Resolution(
            String code, String label, List<MigrationDto.Candidate> candidates, boolean ambiguous) {

        /** 확정 결과를 만듭니다. */
        static Resolution of(String code, String label) {
            return new Resolution(code, label, List.of(), false);
        }

        /** 후보 없는 미해석 결과를 만듭니다. */
        static Resolution unresolved(String input) {
            return new Resolution(null, input, List.of(), false);
        }

        /**
         * 미해석이지만 유사한 값을 후보로 제안하는 결과를 만듭니다.
         *
         * <p>{@link #ambiguous(String, List)}와 구분해야 합니다 — 이쪽은 "찾지 못했고 비슷한 것을 제안한다"이고, 저쪽은 "여러 개가
         * 똑같이 맞는다"입니다. 진단 문구가 이 구분을 그대로 반영합니다.
         */
        static Resolution suggested(String input, List<MigrationDto.Candidate> candidates) {
            return new Resolution(null, input, List.copyOf(candidates), false);
        }

        /** 후보가 있는 중의적 결과를 만듭니다. */
        static Resolution ambiguous(String input, List<MigrationDto.Candidate> candidates) {
            return new Resolution(null, input, List.copyOf(candidates), true);
        }

        /** 확정되지 않은 상태인지 판정합니다. 유사도 제안만 있는 경우도 미해석입니다. */
        public boolean isUnresolved() {
            return code == null && !ambiguous;
        }

        /** 후보들이 동등하게 성립하는 중의적 상태인지 판정합니다. */
        public boolean isAmbiguous() {
            return ambiguous;
        }
    }

    /**
     * 조직·사용자 전량 스냅샷 인덱스를 만듭니다.
     *
     * <p>dry-run·commit 각 호출의 시작에서 한 번만 만들고 그 호출 안에서 재사용합니다. 트랜잭션 밖에서 오래 들고 있지 않습니다.
     *
     * @return 이름 → 코드 매칭 인덱스
     */
    public Index snapshot() {
        return Index.of(organizationRepository.findByDelYn("N"), userRepository.findByDelYn("N"));
    }

    /** 이름 → 코드 역방향 매칭 인덱스입니다. 한 요청 처리 동안만 살아 있습니다. */
    public static final class Index {

        private final Map<String, CorgnI> orgByExactName = new LinkedHashMap<>();
        private final Map<String, CorgnI> orgByNormalizedName = new LinkedHashMap<>();
        private final Map<String, String> orgNameByCode = new LinkedHashMap<>();
        private final Map<String, String> parentCodeByCode = new LinkedHashMap<>();
        private final List<CorgnI> allOrgs;
        private final List<CuserI> allUsers;
        private final Map<String, CuserI> userByEno = new LinkedHashMap<>();

        /**
         * 조직·사용자 목록으로 인덱스를 만듭니다.
         *
         * <p>운영 경로는 {@link OrgIdentityResolver#snapshot()}을 쓰고, 이 팩토리는 리포지토리 없이 인덱스를 조립해야 하는 단위
         * 테스트가 씁니다(어댑터·검증기 테스트가 공유). 그래서 생성자 대신 패키지 밖에서도 보이는 정적 팩토리로 둡니다.
         *
         * @param orgs 조직 목록 (null 아님)
         * @param users 사용자 목록 (null 아님)
         * @return 이름 → 코드 매칭 인덱스
         */
        public static Index of(List<CorgnI> orgs, List<CuserI> users) {
            return new Index(orgs, users);
        }

        private Index(List<CorgnI> orgs, List<CuserI> users) {
            this.allOrgs = List.copyOf(orgs);
            this.allUsers = List.copyOf(users);
            for (CorgnI org : orgs) {
                if (org.getBbrNm() != null) {
                    orgByExactName.putIfAbsent(org.getBbrNm(), org);
                    orgByNormalizedName.putIfAbsent(normalize(org.getBbrNm()), org);
                }
                orgNameByCode.put(org.getPrlmOgzCCone(), org.getBbrNm());
                parentCodeByCode.put(org.getPrlmOgzCCone(), org.getPrlmHrkOgzCCone());
            }
            for (CuserI user : users) {
                userByEno.put(user.getEno(), user);
            }
        }

        /**
         * 부서 폴더명을 조직코드로 해석합니다. 폴더에 코드가 적혀 있으면 <b>그 코드를 기준</b>으로 삼습니다.
         *
         * <p>부점이 올리는 폴더는 `자금운용실(420)`처럼 부서명 뒤에 부서코드를 병기합니다. 코드가 있으면 이름 매칭을 아예 거치지 않고 코드로 조직을 찾습니다 —
         * 이름 매칭은 부분 일치 단계에서 `금융공학실`·`금융공학실 퀀트인프라팀`처럼 상·하위 조직이 함께 걸려 중의적으로 차단되거나, 조직 개편으로 부점명이 바뀌면
         * 통째로 미해석이 되지만, 코드는 그런 흔들림이 없습니다.
         *
         * <p>폴더에 코드가 없거나 그 코드가 조직에 없으면 {@link #resolveOrg(String)} 이름 해석으로 되돌아갑니다. 이때 넘기는 값은 괄호를 뗀
         * 이름 부분입니다 — 코드가 붙은 원문은 정확·정규화 일치를 모두 빗나가 부분 일치까지 흘러가므로, 이름만 남겨야 1단계에서 확정됩니다.
         *
         * @param folderName 최상위 폴더명. null·공백·`-` 같은 미지정 표기는 미해석으로 처리
         * @return 해석 결과. 코드로 확정하면 표시명은 조직에 등록된 부점명입니다
         */
        public Resolution resolveOrgFolder(String folderName) {
            if (isBlankToken(folderName)) {
                return Resolution.unresolved(folderName == null ? "" : folderName);
            }
            Matcher matcher = FOLDER_CODE_SUFFIX.matcher(folderName.trim());
            if (!matcher.matches()) {
                return resolveOrg(folderName);
            }
            String code = Objects.requireNonNull(matcher.group(2));
            String name = Objects.requireNonNull(matcher.group(1)).trim();
            if (orgNameByCode.containsKey(code)) {
                String registeredName = orgNameByCode.get(code);
                return Resolution.of(code, registeredName == null ? name : registeredName);
            }
            if (!name.isEmpty()) {
                return Resolution.of(code, name);
            }
            return Resolution.unresolved(folderName);
        }

        /**
         * 부서명·팀명을 조직코드로 해석합니다.
         *
         * <p>3단계로 좁힙니다. ① 정확 일치 ② 공백·괄호를 제거한 정규화 일치 ③ 부분 일치. ③에서 후보가 둘 이상이면 확정하지 않고 후보를 돌려줍니다.
         *
         * <p>세 단계가 모두 실패하면 ④ 유사도 제안으로 **가장 비슷한 몇 개를 후보로** 돌려줍니다. 후보가 빈 미해석 결과는 미리보기에 보정 드롭다운을 그릴 수
         * 없어 사용자가 손댈 방법이 사라지기 때문입니다(진단 문구는 "찾지 못했다 + 비슷한 값"으로 유지합니다). 셀 자체가 비어 있으면 제안할 근거가 없으므로
         * 종전처럼 후보 없이 돌려줍니다.
         *
         * @param name 엑셀 부서명·팀명. null·공백·`-` 같은 미지정 표기는 미해석으로 처리
         * @return 해석 결과
         */
        public Resolution resolveOrg(String name) {
            if (isBlankToken(name)) {
                return Resolution.unresolved(name == null ? "" : name);
            }
            CorgnI exact = orgByExactName.get(name);
            if (exact != null) {
                return Resolution.of(exact.getPrlmOgzCCone(), exact.getBbrNm());
            }
            CorgnI normalized = orgByNormalizedName.get(normalize(name));
            if (normalized != null) {
                return Resolution.of(normalized.getPrlmOgzCCone(), normalized.getBbrNm());
            }
            String needle = normalize(name);
            List<MigrationDto.Candidate> partial = new ArrayList<>();
            for (CorgnI org : allOrgs) {
                if (org.getBbrNm() == null) {
                    continue;
                }
                String hay = normalize(org.getBbrNm());
                if (hay.contains(needle) || needle.contains(hay)) {
                    partial.add(new MigrationDto.Candidate(org.getPrlmOgzCCone(), org.getBbrNm()));
                }
            }
            if (partial.size() == 1) {
                MigrationDto.Candidate only = partial.get(0);
                return Resolution.of(only.code(), only.label());
            }
            if (!partial.isEmpty()) {
                return Resolution.ambiguous(name, partial);
            }
            List<MigrationDto.Candidate> suggestions =
                    suggest(
                            needle,
                            allOrgs,
                            CorgnI::getBbrNm,
                            org ->
                                    new MigrationDto.Candidate(
                                            org.getPrlmOgzCCone(), org.getBbrNm()));
            return suggestions.isEmpty()
                    ? Resolution.unresolved(name)
                    : Resolution.suggested(name, suggestions);
        }

        /** 사용자 마스터의 팀명(TEM_NM)을 팀코드(TEM_C)로 해석합니다. */
        public Resolution resolveTeam(String name, String deptCodeHint) {
            if (isBlankToken(name)) {
                return Resolution.unresolved(name == null ? "" : name);
            }

            Resolution organizationResolution = resolveOrg(name);
            if (organizationResolution.code() != null) {
                return organizationResolution;
            }

            Map<String, String> teams = matchingTeams(name, deptCodeHint);
            if (teams.isEmpty() && deptCodeHint != null && !deptCodeHint.isBlank()) {
                teams = matchingTeams(name, null);
            }
            if (teams.size() == 1) {
                Map.Entry<String, String> only = teams.entrySet().iterator().next();
                return Resolution.of(only.getKey(), only.getValue());
            }
            if (!teams.isEmpty()) {
                List<MigrationDto.Candidate> candidates =
                        teams.entrySet().stream()
                                .map(
                                        entry ->
                                                new MigrationDto.Candidate(
                                                        entry.getKey(), entry.getValue()))
                                .toList();
                return Resolution.ambiguous(name, candidates);
            }
            return Resolution.unresolved(name);
        }

        private Map<String, String> matchingTeams(String name, String deptCodeHint) {
            String normalizedName = normalize(name);
            Map<String, String> teams = new LinkedHashMap<>();
            for (CuserI user : allUsers) {
                if (user.getTemC() == null || user.getTemC().isBlank() || user.getTemNm() == null) {
                    continue;
                }
                if (deptCodeHint != null && !deptCodeHint.equals(user.getBbrC())) {
                    continue;
                }
                if (normalizedName.equals(normalize(user.getTemNm()))) {
                    teams.putIfAbsent(user.getTemC(), user.getTemNm());
                }
            }
            return teams;
        }

        /**
         * 이름이 하나도 걸리지 않았을 때 가장 비슷한 후보를 고릅니다.
         *
         * <p>편집거리 대신 **가장 긴 공통 부분문자열 길이 / 짧은 쪽 길이**를 씁니다. 은행 조직·직원 이름의 오차는 접미어 차이(`런던지점`↔`런던PF`)나 한
         * 글자 오기가 대부분이라 이 척도로 충분히 갈라지고, 짧은 문자열 대상이라 비용도 무시할 수 있습니다(조직 200건 × 20자 수준).
         *
         * @param needle 정규화된 입력
         * @param source 후보 원본 목록
         * @param nameOf 원본에서 비교할 이름을 꺼내는 함수
         * @param toCandidate 원본을 후보로 바꾸는 함수
         * @return 점수 내림차순, 같으면 표시명 오름차순으로 정렬한 최대 {@link #MAX_SUGGESTIONS}개
         */
        private static <T> List<MigrationDto.Candidate> suggest(
                String needle,
                List<T> source,
                Function<T, String> nameOf,
                Function<T, MigrationDto.Candidate> toCandidate) {
            if (needle.isEmpty()) {
                return List.of();
            }
            record Scored(double score, MigrationDto.Candidate candidate) {}
            List<Scored> scored = new ArrayList<>();
            for (T item : source) {
                String name = nameOf.apply(item);
                if (name == null || name.isBlank()) {
                    continue;
                }
                double score = similarity(needle, normalize(name));
                if (score >= SUGGESTION_THRESHOLD) {
                    scored.add(new Scored(score, toCandidate.apply(item)));
                }
            }
            scored.sort(
                    Comparator.comparingDouble(Scored::score)
                            .reversed()
                            .thenComparing(
                                    s -> s.candidate().label(),
                                    Comparator.nullsLast(Comparator.naturalOrder())));
            return scored.stream().limit(MAX_SUGGESTIONS).map(Scored::candidate).toList();
        }

        /** 가장 긴 공통 부분문자열 길이를 짧은 쪽 길이로 나눈 0~1 유사도입니다. */
        private static double similarity(String a, String b) {
            if (a.isEmpty() || b.isEmpty()) {
                return 0d;
            }
            int[][] dp = new int[a.length() + 1][b.length() + 1];
            int longest = 0;
            for (int i = 1; i <= a.length(); i++) {
                for (int j = 1; j <= b.length(); j++) {
                    if (a.charAt(i - 1) == b.charAt(j - 1)) {
                        dp[i][j] = dp[i - 1][j - 1] + 1;
                        longest = Math.max(longest, dp[i][j]);
                    }
                }
            }
            return (double) longest / Math.min(a.length(), b.length());
        }

        /**
         * `김성원 과장` 형태의 담당자 표기를 사번으로 해석합니다.
         *
         * <p>공백으로 이름과 직위를 분리해 사용자명+직위명으로 좁히고, 직위가 없거나 일치하지 않으면 이름만으로 좁힙니다. 그래도 둘 이상이면 {@code
         * deptCodeHint}(같은 행의 주관부서코드)로 한 번 더 좁힙니다.
         *
         * <p>이름이 하나도 걸리지 않으면 조직 해석과 같은 이유로 유사도 제안을 후보로 돌려줍니다({@link #resolveOrg} 참고).
         *
         * @param nameWithTitle 엑셀 담당자 표기. null·공백은 미해석
         * @param deptCodeHint 같은 행에서 해석된 부서코드. 없으면 null
         * @return 해석 결과
         */
        public Resolution resolveUser(String nameWithTitle, String deptCodeHint) {
            if (isBlankToken(nameWithTitle)) {
                return Resolution.unresolved(nameWithTitle == null ? "" : nameWithTitle);
            }
            String trimmed = nameWithTitle.trim();
            int lastSpace = trimmed.lastIndexOf(' ');
            String name = lastSpace > 0 ? trimmed.substring(0, lastSpace).trim() : trimmed;
            String title = lastSpace > 0 ? trimmed.substring(lastSpace + 1).trim() : null;

            List<CuserI> byName = new ArrayList<>();
            for (CuserI user : allUsers) {
                if (name.equals(user.getUsrNm())) {
                    byName.add(user);
                }
            }
            if (byName.isEmpty()) {
                List<MigrationDto.Candidate> suggestions =
                        suggest(
                                normalize(name),
                                allUsers,
                                CuserI::getUsrNm,
                                user -> new MigrationDto.Candidate(user.getEno(), label(user)));
                return suggestions.isEmpty()
                        ? Resolution.unresolved(nameWithTitle)
                        : Resolution.suggested(nameWithTitle, suggestions);
            }
            List<CuserI> narrowed = byName;
            if (title != null && byName.size() > 1) {
                List<CuserI> byTitle = new ArrayList<>();
                for (CuserI user : byName) {
                    if (title.equals(user.getPtCNm())) {
                        byTitle.add(user);
                    }
                }
                if (!byTitle.isEmpty()) {
                    narrowed = byTitle;
                }
            }
            if (narrowed.size() > 1 && deptCodeHint != null && !deptCodeHint.isBlank()) {
                List<CuserI> byDept = new ArrayList<>();
                for (CuserI user : narrowed) {
                    if (deptCodeHint.equals(user.getBbrC())) {
                        byDept.add(user);
                    }
                }
                if (!byDept.isEmpty()) {
                    narrowed = byDept;
                }
            }
            if (narrowed.size() == 1) {
                CuserI only = narrowed.get(0);
                return Resolution.of(only.getEno(), label(only));
            }
            List<MigrationDto.Candidate> candidates = new ArrayList<>();
            for (CuserI user : narrowed) {
                candidates.add(new MigrationDto.Candidate(user.getEno(), label(user)));
            }
            return Resolution.ambiguous(nameWithTitle, candidates);
        }

        /**
         * 조직코드에 해당하는 조직명을 반환합니다. 스냅샷 컬럼(SVN_DPM_NM 등) 채우기에 사용합니다.
         *
         * @param code 조직코드
         * @return 조직명. 미등록이면 null
         */
        public String orgNameOf(String code) {
            return code == null ? null : orgNameByCode.get(code);
        }

        /**
         * 조직코드의 <b>상위조직명</b>을 반환합니다. 부서코드로 주관부문/본부를 채우는 데 씁니다.
         *
         * <p>상위조직은 `CORGNI.PRLM_HRK_OGZ_C_CONE`이 가리키는 조직입니다(실측: `180 IT기획부` → `013`). 최상위 조직이거나 상위
         * 코드가 스냅샷에 없으면 null입니다.
         *
         * @param code 조직코드
         * @return 상위조직명. 상위가 없거나 미등록이면 null
         */
        public String parentOrgNameOf(String code) {
            if (code == null) return null;
            String parentCode = parentCodeByCode.get(code);
            return parentCode == null ? null : orgNameByCode.get(parentCode);
        }

        /**
         * 부점에 소속된 사용자를 보정 후보로 돌려줍니다 (MIG-03).
         *
         * <p>위임예산 담당자는 시트에 열이 없어 이름으로 해석할 대상이 없습니다. 그래서 이름 해석 후보가 아니라 <b>그 부점의 소속 사용자 전체</b>를 후보로
         * 냅니다 — 후보가 비면 화면에 드롭다운이 그려지지 않아 "고르라"고 해놓고 고를 수단이 없는 상태가 됩니다.
         *
         * @param orgCode 부점 조직코드. null이면 빈 목록
         * @return 사번 오름차순 후보. 표시명은 `이름 직위`(직위가 없으면 이름)
         */
        public List<MigrationDto.Candidate> userCandidatesOfOrg(String orgCode) {
            if (orgCode == null) {
                return List.of();
            }
            return allUsers.stream()
                    .filter(user -> orgCode.equals(user.getBbrC()))
                    .sorted(Comparator.comparing(CuserI::getEno))
                    .map(
                            user ->
                                    new MigrationDto.Candidate(
                                            user.getEno(),
                                            user.getPtCNm() == null || user.getPtCNm().isBlank()
                                                    ? user.getUsrNm()
                                                    : user.getUsrNm() + " " + user.getPtCNm()))
                    .toList();
        }

        /**
         * 사번이 스냅샷에 등록되어 있는지 확인합니다.
         *
         * <p>미리보기 보정값({@code CellOverride.value})은 사용자가 후보 목록에서 고른 사번을 그대로 담아 오므로, 이 값을 신뢰하기 전에 실재
         * 여부를 확인해야 합니다. 검증기가 보정값을 검증 없이 통과시키면 잘못되거나 오래된 사번이 그대로 원장 반영 단계까지 흘러갑니다.
         *
         * @param eno 사번
         * @return 등록되어 있으면 true
         */
        public boolean userExists(String eno) {
            return eno != null && userByEno.containsKey(eno);
        }

        /**
         * 사번의 소속 팀코드를 반환합니다.
         *
         * @param eno 사번
         * @return 팀코드. 미등록이면 null
         */
        public String teamOfUser(String eno) {
            CuserI user = eno == null ? null : userByEno.get(eno);
            return user == null ? null : user.getTemC();
        }

        /**
         * 사번의 소속 팀명을 반환합니다.
         *
         * @param eno 사번
         * @return 팀명. 미등록이면 null
         */
        public String teamNameOfUser(String eno) {
            CuserI user = eno == null ? null : userByEno.get(eno);
            return user == null ? null : user.getTemNm();
        }

        private static String label(CuserI user) {
            return user.getPtCNm() == null
                    ? user.getUsrNm()
                    : user.getUsrNm() + " " + user.getPtCNm();
        }

        private static boolean isBlankToken(String value) {
            return value == null || BLANK_TOKENS.contains(value.trim());
        }

        /** 공백·괄호·중점을 제거해 표기 차이를 흡수합니다. */
        private static String normalize(String value) {
            return value.replaceAll("[\\s()\\[\\]·]", "");
        }
    }
}
