package com.kdb.it.domain.migration.service;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    /**
     * 해석 결과입니다.
     *
     * @param code 확정된 코드값. 미확정이면 null
     * @param label 확정된 표시명. 미확정이면 입력 원문
     * @param candidates 중의적일 때의 후보. 확정·미해석이면 빈 목록
     */
    public record Resolution(String code, String label, List<MigrationDto.Candidate> candidates) {

        /** 확정 결과를 만듭니다. */
        static Resolution of(String code, String label) {
            return new Resolution(code, label, List.of());
        }

        /** 후보 없는 미해석 결과를 만듭니다. */
        static Resolution unresolved(String input) {
            return new Resolution(null, input, List.of());
        }

        /** 후보가 있는 중의적 결과를 만듭니다. */
        static Resolution ambiguous(String input, List<MigrationDto.Candidate> candidates) {
            return new Resolution(null, input, List.copyOf(candidates));
        }

        /** 확정되지 않았고 후보도 없는 상태인지 판정합니다. */
        public boolean isUnresolved() {
            return code == null && candidates.isEmpty();
        }

        /** 확정되지 않았으나 후보가 있는 상태인지 판정합니다. */
        public boolean isAmbiguous() {
            return code == null && !candidates.isEmpty();
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
            }
            for (CuserI user : users) {
                userByEno.put(user.getEno(), user);
            }
        }

        /**
         * 부서명·팀명을 조직코드로 해석합니다.
         *
         * <p>3단계로 좁힙니다. ① 정확 일치 ② 공백·괄호를 제거한 정규화 일치 ③ 부분 일치. ③에서 후보가 둘 이상이면 확정하지 않고 후보를 돌려줍니다.
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
            return partial.isEmpty()
                    ? Resolution.unresolved(name)
                    : Resolution.ambiguous(name, partial);
        }

        /**
         * `김성원 과장` 형태의 담당자 표기를 사번으로 해석합니다.
         *
         * <p>공백으로 이름과 직위를 분리해 사용자명+직위명으로 좁히고, 직위가 없거나 일치하지 않으면 이름만으로 좁힙니다. 그래도 둘 이상이면 {@code
         * deptCodeHint}(같은 행의 주관부서코드)로 한 번 더 좁힙니다.
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
                return Resolution.unresolved(nameWithTitle);
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
