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
