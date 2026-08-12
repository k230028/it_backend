package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 이름 → 코드 역방향 해석의 3단계와 중의성 처리를 고정합니다. */
@ExtendWith(MockitoExtension.class)
class OrgIdentityResolverTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private OrgIdentityResolver resolver;

    private OrgIdentityResolver.Index index;

    @BeforeEach
    void setUp() {
        when(organizationRepository.findByDelYn("N"))
                .thenReturn(
                        List.of(
                                org("0210", "IT기획부"),
                                org("0211", "IT기획팀"),
                                org("0212", "IT인프라팀"),
                                org("0330", "PF2실"),
                                org("0450", "금융공학실"),
                                org("0451", "금융공학실 퀀트인프라팀")));
        when(userRepository.findByDelYn("N"))
                .thenReturn(
                        List.of(
                                user("100001", "김성원", "과장", "0210", "0211", "IT기획팀"),
                                user("100002", "장원섭", "차장", "0330", "0331", "글로벌IT혁신팀"),
                                user("100003", "장원섭", "부장", "0450", "0451", "퀀트인프라팀")));
        index = resolver.snapshot();
    }

    @Test
    @DisplayName("부서명이 정확히 일치하면 조직코드를 확정한다")
    void 정확일치_부서명은_코드를_확정한다() {
        OrgIdentityResolver.Resolution result = index.resolveOrg("IT기획부");

        assertThat(result.code()).isEqualTo("0210");
        assertThat(result.label()).isEqualTo("IT기획부");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("공백이 다른 부서명도 2단계에서 확정한다")
    void 공백차이_부서명은_2단계에서_확정한다() {
        assertThat(index.resolveOrg("IT 기획부").code()).isEqualTo("0210");
        assertThat(index.resolveOrg(" IT기획부 ").code()).isEqualTo("0210");
    }

    @Test
    @DisplayName("부분 일치가 둘 이상이면 미확정으로 두고 후보를 돌려준다")
    void 부분일치_다수는_후보를_돌려준다() {
        OrgIdentityResolver.Resolution result = index.resolveOrg("금융공학");

        assertThat(result.code()).isNull();
        assertThat(result.candidates())
                .extracting(com.kdb.it.domain.migration.dto.MigrationDto.Candidate::code)
                .containsExactlyInAnyOrder("0450", "0451");
    }

    @Test
    @DisplayName("어디에도 없는 부서명은 후보 없이 미해석이다")
    void 미등록_부서명은_후보없이_미해석이다() {
        OrgIdentityResolver.Resolution result = index.resolveOrg("없는부서");

        assertThat(result.code()).isNull();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("빈 부서명은 후보 없이 미해석이며 예외를 던지지 않는다")
    void 빈_부서명은_미해석이다() {
        assertThat(index.resolveOrg("").code()).isNull();
        assertThat(index.resolveOrg(null).code()).isNull();
        assertThat(index.resolveOrg("-").code()).isNull();
        assertThat(index.resolveOrg("—").code()).isNull();
        assertThat(index.resolveOrg("—").candidates()).isEmpty();
    }

    @Test
    @DisplayName("부분 일치가 하나뿐이면 후보 없이 바로 확정한다")
    void 부분일치_하나뿐이면_후보없이_확정한다() {
        OrgIdentityResolver.Resolution result = index.resolveOrg("PF2");

        assertThat(result.code()).isEqualTo("0330");
        assertThat(result.label()).isEqualTo("PF2실");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("이름+직위로 담당자 사번을 확정한다")
    void 이름과직위로_사번을_확정한다() {
        OrgIdentityResolver.Resolution result = index.resolveUser("김성원 과장", null);

        assertThat(result.code()).isEqualTo("100001");
        assertThat(result.label()).isEqualTo("김성원 과장");
    }

    @Test
    @DisplayName("직위가 없어도 이름이 유일하면 확정한다")
    void 직위없이_이름이_유일하면_확정한다() {
        assertThat(index.resolveUser("김성원", null).code()).isEqualTo("100001");
    }

    @Test
    @DisplayName("동명이인은 부서 힌트로 좁힌다")
    void 동명이인은_부서힌트로_좁힌다() {
        OrgIdentityResolver.Resolution result = index.resolveUser("장원섭", "0450");

        assertThat(result.code()).isEqualTo("100003");
    }

    @Test
    @DisplayName("부서 힌트로도 좁혀지지 않는 동명이인은 후보를 돌려준다")
    void 좁혀지지_않는_동명이인은_후보를_돌려준다() {
        OrgIdentityResolver.Resolution result = index.resolveUser("장원섭", null);

        assertThat(result.code()).isNull();
        assertThat(result.candidates())
                .extracting(com.kdb.it.domain.migration.dto.MigrationDto.Candidate::code)
                .containsExactlyInAnyOrder("100002", "100003");
    }

    @Test
    @DisplayName("엑셀의 직위가 실제와 달라도(오탈자·이동) 무시하고 부서 힌트로 동명이인을 확정한다")
    void 직위가_틀려도_부서힌트가_있으면_동명이인을_확정한다() {
        // "이사"는 100002(차장)·100003(부장) 어느 쪽 실제 직위와도 일치하지 않는 낡은 직위 표기다.
        // byTitle 좁히기가 공집합이 되어 이름 전체 목록으로 되돌아가야 하고, 그 다음 부서 힌트로 확정되어야 한다.
        OrgIdentityResolver.Resolution result = index.resolveUser("장원섭 이사", "0450");

        assertThat(result.code()).isEqualTo("100003");
        assertThat(result.label()).isEqualTo("장원섭 부장");
    }

    @Test
    @DisplayName("엑셀의 직위가 실제와 달라도 미해석으로 떨어지지 않고 동명이인 후보를 돌려준다")
    void 직위가_틀리고_부서힌트도_없으면_동명이인_후보를_돌려준다() {
        // 위와 같은 낡은 직위 표기이지만 부서 힌트가 없는 경우: USER_UNRESOLVED가 아니라
        // byName 전체가 중의적 후보로 나와야 한다(직위 불일치로 결과가 사라지면 안 됨).
        OrgIdentityResolver.Resolution result = index.resolveUser("장원섭 이사", null);

        assertThat(result.code()).isNull();
        assertThat(result.candidates())
                .extracting(com.kdb.it.domain.migration.dto.MigrationDto.Candidate::code)
                .containsExactlyInAnyOrder("100002", "100003");
    }

    @Test
    @DisplayName("빈 담당자명은 후보 없이 미해석이며 예외를 던지지 않는다")
    void 빈_담당자명은_미해석이다() {
        assertThat(index.resolveUser("", null).code()).isNull();
        assertThat(index.resolveUser("", null).candidates()).isEmpty();
        assertThat(index.resolveUser(null, null).code()).isNull();
        assertThat(index.resolveUser(null, null).candidates()).isEmpty();
        assertThat(index.resolveUser("-", null).code()).isNull();
        assertThat(index.resolveUser("－", null).code()).isNull();
        assertThat(index.resolveUser("－", null).candidates()).isEmpty();
    }

    @Test
    @DisplayName("조직코드로 조직명 스냅샷을 얻는다")
    void 조직코드로_조직명을_얻는다() {
        assertThat(index.orgNameOf("0210")).isEqualTo("IT기획부");
        assertThat(index.orgNameOf("9999")).isNull();
    }

    @Test
    @DisplayName("사번으로 소속 팀코드를 얻는다")
    void 사번으로_팀코드를_얻는다() {
        assertThat(index.teamOfUser("100001")).isEqualTo("0211");
        assertThat(index.teamOfUser("999999")).isNull();
    }

    @Test
    @DisplayName("확정 결과(of)는 미해석도 중의적도 아니다")
    void 확정결과는_미해석도_중의적도_아니다() {
        OrgIdentityResolver.Resolution result = OrgIdentityResolver.Resolution.of("0210", "IT기획부");

        assertThat(result.code()).isEqualTo("0210");
        assertThat(result.label()).isEqualTo("IT기획부");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.isUnresolved()).isFalse();
        assertThat(result.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("후보 없는 미해석 결과(unresolved)는 isUnresolved만 참이다")
    void 미해석결과는_isUnresolved만_참이다() {
        OrgIdentityResolver.Resolution result = OrgIdentityResolver.Resolution.unresolved("없는부서");

        assertThat(result.code()).isNull();
        assertThat(result.label()).isEqualTo("없는부서");
        assertThat(result.candidates()).isEmpty();
        assertThat(result.isUnresolved()).isTrue();
        assertThat(result.isAmbiguous()).isFalse();
    }

    @Test
    @DisplayName("후보 있는 중의적 결과(ambiguous)는 isAmbiguous만 참이다")
    void 중의적결과는_isAmbiguous만_참이다() {
        List<com.kdb.it.domain.migration.dto.MigrationDto.Candidate> candidates =
                List.of(
                        new com.kdb.it.domain.migration.dto.MigrationDto.Candidate(
                                "0450", "금융공학실"));

        OrgIdentityResolver.Resolution result =
                OrgIdentityResolver.Resolution.ambiguous("금융공학", candidates);

        assertThat(result.code()).isNull();
        assertThat(result.label()).isEqualTo("금융공학");
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.isUnresolved()).isFalse();
        assertThat(result.isAmbiguous()).isTrue();
    }

    @Test
    @DisplayName("유사도 제안 결과(suggested)는 후보가 있어도 isUnresolved가 참이다")
    void 유사도제안결과는_미해석이다() {
        OrgIdentityResolver.Resolution result =
                OrgIdentityResolver.Index.of(List.of(org("0910", "런던지점")), List.of())
                        .resolveOrg("런던PF데스크");

        assertThat(result.code()).isNull();
        // 후보가 붙어도 "여러 개가 똑같이 맞는다"(ambiguous)가 아니라 "못 찾았고 비슷한 것을 제안한다"다
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.isUnresolved()).isTrue();
        assertThat(result.candidates())
                .extracting(com.kdb.it.domain.migration.dto.MigrationDto.Candidate::code)
                .containsExactly("0910");
    }

    @Test
    @DisplayName("한 글자만 겹치는 조직은 제안하지 않는다")
    void 한글자만_겹치면_제안하지_않는다() {
        OrgIdentityResolver.Resolution result =
                OrgIdentityResolver.Index.of(List.of(org("0100", "여신관리부")), List.of())
                        .resolveOrg("총무팀부");

        assertThat(result.candidates()).isEmpty();
        assertThat(result.isUnresolved()).isTrue();
    }

    @Test
    @DisplayName("유사도 제안은 최대 5개까지, 점수가 높은 순으로 온다")
    void 유사도제안은_다섯개까지다() {
        List<CorgnI> orgs = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            orgs.add(org("01" + i, "테스트조직" + i));
        }
        orgs.add(org("099", "테스트조직"));

        OrgIdentityResolver.Resolution result =
                // 부분 일치 단계에 걸리지 않도록 마지막 글자만 다른 값을 쓴다
                OrgIdentityResolver.Index.of(orgs, List.of()).resolveOrg("테스트조진");

        assertThat(result.candidates()).hasSize(5);
        assertThat(result.candidates().get(0).code()).isEqualTo("099");
    }

    @Test
    @DisplayName("이름이 하나도 걸리지 않는 담당자에도 유사한 사번 후보를 제안한다")
    void 미해석_담당자도_후보를_제안한다() {
        OrgIdentityResolver.Resolution result =
                OrgIdentityResolver.Index.of(
                                List.of(),
                                List.of(user("K1", "김성원", "과장", "180", "18001", "IT기획팀")))
                        .resolveUser("김성완 과장", null);

        assertThat(result.code()).isNull();
        assertThat(result.isAmbiguous()).isFalse();
        assertThat(result.candidates())
                .extracting(com.kdb.it.domain.migration.dto.MigrationDto.Candidate::code)
                .containsExactly("K1");
    }

    @Test
    @DisplayName("빈 담당자 셀은 후보를 제안하지 않는다")
    void 빈_담당자셀은_후보가_없다() {
        OrgIdentityResolver.Resolution result =
                OrgIdentityResolver.Index.of(
                                List.of(),
                                List.of(user("K1", "김성원", "과장", "180", "18001", "IT기획팀")))
                        .resolveUser("-", null);

        assertThat(result.candidates()).isEmpty();
    }

    private static CorgnI org(String code, String name) {
        return CorgnI.builder().prlmOgzCCone(code).bbrNm(name).build();
    }

    private static CuserI user(
            String eno, String name, String title, String bbrC, String temC, String temNm) {
        return CuserI.builder()
                .eno(eno)
                .usrNm(name)
                .ptCNm(title)
                .bbrC(bbrC)
                .temC(temC)
                .temNm(temNm)
                .build();
    }
}
