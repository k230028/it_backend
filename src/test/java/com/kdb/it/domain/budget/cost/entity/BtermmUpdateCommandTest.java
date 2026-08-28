package com.kdb.it.domain.budget.cost.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.kdb.it.common.code.CodeDefaults;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 단말기 변경 명령({@link Btermm.UpdateCommand})의 전체 필드 매핑과 필수 코드 기본값 보정 검증. */
class BtermmUpdateCommandTest {

    @Test
    @DisplayName("update - 명령의 17개 필드를 모두 반영하고 필수 코드 빈값은 기본값으로 보정한다")
    void update_명령의모든필드반영_필수코드빈값보정() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).build();
        Btermm.UpdateCommand command =
                Btermm.UpdateCommand.builder()
                        .spfTmnNm("금융단말")
                        .tmnKdTc("01")
                        .nsfUsgCone("영업점 시세조회")
                        .tmnClsfC("02")
                        .termRqmBgAmt(new BigDecimal("1300500.000"))
                        .curC("USD")
                        .xcr(new BigDecimal("1300.5000"))
                        .xcrBseDt("20260729")
                        .dfrCleC(" ")
                        .indRsn("증액")
                        .cgprId("10001")
                        .termSvnTemC("18001")
                        .svnTemNm("디지털전략팀")
                        .termSvnDpmC("180")
                        .svnDpmNm("디지털전략부")
                        .rmk("비고")
                        .fcAmt(new BigDecimal("1000.000"))
                        .build();

        target.update(command);

        assertThat(target.getSpfTmnNm()).isEqualTo("금융단말");
        assertThat(target.getTmnKdTc()).isEqualTo("01");
        assertThat(target.getNsfUsgCone()).isEqualTo("영업점 시세조회");
        assertThat(target.getTmnClsfC()).isEqualTo("02");
        assertThat(target.getTermRqmBgAmt()).isEqualByComparingTo("1300500.000");
        assertThat(target.getCurC()).isEqualTo("USD");
        assertThat(target.getXcr()).isEqualByComparingTo("1300.5000");
        assertThat(target.getXcrBseDt()).isEqualTo("20260729");
        assertThat(target.getDfrCleC()).isEqualTo(CodeDefaults.NOT_APPLICABLE);
        assertThat(target.getIndRsn()).isEqualTo("증액");
        assertThat(target.getCgprId()).isEqualTo("10001");
        // 팀코드와 부서코드는 둘 다 String이라 위치 인자 시절에는 서로 바뀌어도 컴파일이 통과했다.
        assertThat(target.getTermSvnTemC()).isEqualTo("18001");
        assertThat(target.getSvnTemNm()).isEqualTo("디지털전략팀");
        assertThat(target.getTermSvnDpmC()).isEqualTo("180");
        assertThat(target.getSvnDpmNm()).isEqualTo("디지털전략부");
        assertThat(target.getRmk()).isEqualTo("비고");
        assertThat(target.getFcAmt()).isEqualByComparingTo("1000.000");
    }

    @Test
    @DisplayName("update - null 명령은 어떤 필드도 바꾸기 전에 명시적으로 실패한다")
    void update_null명령_대입전명시적예외() {
        Btermm target = Btermm.builder().tmnMngNo("TER-2026-0001").sno(1).spfTmnNm("기존단말").build();

        assertThatNullPointerException()
                .isThrownBy(() -> target.update(null))
                .withMessage("command");
        assertThat(target.getSpfTmnNm()).isEqualTo("기존단말");
    }
}
