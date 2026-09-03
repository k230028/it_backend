package com.kdb.it.domain.budget.cost.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BcostmOrgNameByteValidationTest {

    private static final String EXACTLY_100_BYTES = "가".repeat(33) + "a";
    private static final String OVER_100_BYTES = "가".repeat(34);

    @Test
    void 주관부서명과_주관팀명은_각각_100바이트까지_허용한다() {
        Bcostm target = Bcostm.builder().build();

        assertThatCode(() -> target.assignSvnOrgNames(EXACTLY_100_BYTES, EXACTLY_100_BYTES))
                .doesNotThrowAnyException();
        assertThat(target.getSvnDpmNm()).isEqualTo(EXACTLY_100_BYTES);
        assertThat(target.getSvnTemNm()).isEqualTo(EXACTLY_100_BYTES);
    }

    @Test
    void 주관부서명과_주관팀명은_100바이트를_초과하면_대입전에_거부한다() {
        Bcostm target = Bcostm.builder().svnDpmNm("기존 부서").svnTemNm("기존 팀").build();

        assertThatThrownBy(() -> target.assignSvnOrgNames(OVER_100_BYTES, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVN_DPM_NM");
        assertThatThrownBy(() -> target.assignSvnOrgNames(null, OVER_100_BYTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SVN_TEM_NM");
        assertThat(target.getSvnDpmNm()).isEqualTo("기존 부서");
        assertThat(target.getSvnTemNm()).isEqualTo("기존 팀");
    }

    @Test
    void 담당자명은_100바이트를_초과하면_대입전에_거부한다() {
        Bcostm target = Bcostm.builder().cgprNm("기존 담당자").build();

        assertThatCode(() -> target.assignCgprName(EXACTLY_100_BYTES)).doesNotThrowAnyException();
        assertThatThrownBy(() -> target.assignCgprName(OVER_100_BYTES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CGPR_NM");
        assertThat(target.getCgprNm()).isEqualTo(EXACTLY_100_BYTES);
    }
}
