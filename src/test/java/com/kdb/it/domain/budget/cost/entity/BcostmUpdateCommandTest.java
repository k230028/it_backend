package com.kdb.it.domain.budget.cost.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.kdb.it.common.code.CodeDefaults;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 전산업무비 변경 명령({@link Bcostm.UpdateCommand})의 전체 필드 매핑과 필수 코드 기본값 보정 검증. */
class BcostmUpdateCommandTest {

    @Test
    @DisplayName("update - 명령의 20개 필드를 모두 반영하고 필수 코드 빈값은 기본값으로 보정한다")
    void update_명령의모든필드반영_필수코드빈값보정() {
        Bcostm target = Bcostm.builder().costBgNo("COST-1").bgSno(1).build();
        Bcostm.UpdateCommand command =
                Bcostm.UpdateCommand.builder()
                        .ioeC("IOE001")
                        .cttNm("계약명")
                        .cttOppNm("계약상대")
                        .costTotXpAmt(new BigDecimal("1300500.000"))
                        .dfrCleC(" ")
                        .fstDfrDt("20260731")
                        .curC("USD")
                        .xcr(new BigDecimal("1300.5000"))
                        .xcrBseDt("20260729")
                        .sectSysUtzYn("Y")
                        .indRsn("증액")
                        .cgprId("10001")
                        .costSvnDpmC("180")
                        .svnTemC("18001")
                        .bgUntAbusC("101")
                        .tmnYn("N")
                        .abusTc(null)
                        .bseYy("2026")
                        .cncdRfrNo("COST-2025-1")
                        .fcAmt(new BigDecimal("1000.000"))
                        .build();

        target.update(command);

        assertThat(target.getIoeC()).isEqualTo("IOE001");
        assertThat(target.getCttNm()).isEqualTo("계약명");
        assertThat(target.getCttOppNm()).isEqualTo("계약상대");
        assertThat(target.getCostTotXpAmt()).isEqualByComparingTo("1300500.000");
        assertThat(target.getDfrCleC()).isEqualTo(CodeDefaults.NOT_APPLICABLE);
        assertThat(target.getFstDfrDt()).isEqualTo("20260731");
        assertThat(target.getCurC()).isEqualTo("USD");
        assertThat(target.getXcr()).isEqualByComparingTo("1300.5000");
        assertThat(target.getXcrBseDt()).isEqualTo("20260729");
        assertThat(target.getSectSysUtzYn()).isEqualTo("Y");
        assertThat(target.getIndRsn()).isEqualTo("증액");
        assertThat(target.getCgprId()).isEqualTo("10001");
        assertThat(target.getCostSvnDpmC()).isEqualTo("180");
        assertThat(target.getSvnTemC()).isEqualTo("18001");
        assertThat(target.getBgUntAbusC()).isEqualTo("101");
        assertThat(target.getTmnYn()).isEqualTo("N");
        assertThat(target.getAbusTc()).isEqualTo(CodeDefaults.NOT_APPLICABLE);
        assertThat(target.getBseYy()).isEqualTo("2026");
        assertThat(target.getCncdRfrNo()).isEqualTo("COST-2025-1");
        assertThat(target.getFcAmt()).isEqualByComparingTo("1000.000");
    }

    @Test
    @DisplayName("update - null 명령은 어떤 필드도 바꾸기 전에 명시적으로 실패한다")
    void update_null명령_대입전명시적예외() {
        Bcostm target = Bcostm.builder().costBgNo("COST-1").bgSno(1).ioeC("IOE000").build();

        assertThatNullPointerException()
                .isThrownBy(() -> target.update(null))
                .withMessage("command");
        assertThat(target.getIoeC()).isEqualTo("IOE000");
    }
}
