package com.kdb.it.domain.budget.project.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BprojmSnapshotNameByteValidationTest {

    private static final String EXACTLY_100_BYTES = "가".repeat(33) + "a";
    private static final String OVER_100_BYTES = "가".repeat(34);

    @Test
    void 조직명과_담당자명_스냅샷은_각각_100바이트까지_허용한다() {
        Bprojm target = Bprojm.builder().build();

        assertThatCode(
                        () -> {
                            target.assignSvnOrgNames(EXACTLY_100_BYTES, EXACTLY_100_BYTES);
                            target.assignPersonNames(EXACTLY_100_BYTES, EXACTLY_100_BYTES);
                        })
                .doesNotThrowAnyException();
        assertThatCode(target::validateSnapshotNamesBeforePersist).doesNotThrowAnyException();
    }

    @Test
    void 조직명과_담당자명_스냅샷은_각각_100바이트를_초과하면_거부한다() {
        Bprojm target = Bprojm.builder().build();

        assertThatThrownBy(() -> target.assignSvnOrgNames(OVER_100_BYTES, null))
                .hasMessageContaining("SVN_DPM_NM");
        assertThatThrownBy(() -> target.assignSvnOrgNames(null, OVER_100_BYTES))
                .hasMessageContaining("SVN_TEM_NM");
        assertThatThrownBy(() -> target.assignPersonNames(OVER_100_BYTES, null))
                .hasMessageContaining("TLR_NM");
        assertThatThrownBy(() -> target.assignPersonNames(null, OVER_100_BYTES))
                .hasMessageContaining("USR_NM");
    }

    @Test
    void 업무구분명은_영속화직전_100바이트_초과를_거부한다() {
        Bprojm target = Bprojm.builder().bzDttNm(OVER_100_BYTES).build();

        assertThatThrownBy(target::validateSnapshotNamesBeforePersist)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BZ_DTT_NM");
    }

    @Test
    void 수정할_업무구분명은_100바이트를_초과하면_기존값을_유지하고_거부한다() {
        Bprojm target = Bprojm.builder().bzDttNm("기존 업무").build();

        assertThatThrownBy(() -> target.update(commandWithBusinessDetailName(OVER_100_BYTES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BZ_DTT_NM");
        assertThat(target.getBzDttNm()).isEqualTo("기존 업무");
    }

    private static Bprojm.UpdateCommand commandWithBusinessDetailName(String bzDttNm) {
        return new Bprojm.UpdateCommand(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, bzDttNm, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}
