package com.kdb.it.domain.budget.project.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DTO 구현 분리 시 공개 매핑·BYTE 검증 계약이 유지되는지 확인합니다. */
class ProjectDtoMappingTest {

    @Test
    @DisplayName("생성 요청은 사업 엔티티 필드와 기본값을 보존한다")
    void createRequestToEntity_preservesFieldsAndDefaults() {
        ProjectDto.CreateRequest request =
                ProjectDto.CreateRequest.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .abusNm("정보화 사업")
                        .bzTpC("10")
                        .sttDtm(LocalDate.of(2026, 1, 1))
                        .endDtm(LocalDate.of(2026, 12, 31))
                        .bzDttNm("업무구분")
                        .bseYy("2026")
                        .abusTc(null)
                        .build();

        Bprojm entity = request.toEntity();

        assertThat(entity.getAbusMngNo()).isEqualTo("PRJ-2026-0001");
        assertThat(entity.getSno()).isEqualTo(1);
        assertThat(entity.getAbusNm()).isEqualTo("정보화 사업");
        assertThat(entity.getBzTpC()).isEqualTo("10");
        assertThat(entity.getSttDtm()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(entity.getEndDtm()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(entity.getBzDttNm()).isEqualTo("업무구분");
        assertThat(entity.getBseYy()).isEqualTo("2026");
        assertThat(entity.getDplYn()).isEqualTo("N");
        assertThat(entity.getLstYn()).isEqualTo("Y");
        assertThat(entity.getAbusTc()).isEqualTo("0");
    }

    @Test
    @DisplayName("품목 엔티티는 공개 DTO 필드로 손실 없이 변환된다")
    void bitemmFromEntity_preservesFields() {
        Bitemm entity =
                Bitemm.builder()
                        .gclMngNo("GCL-2026-0001")
                        .sno(2)
                        .ioeC("571")
                        .gclNm("서버")
                        .qty(new BigDecimal("3"))
                        .curC("USD")
                        .xcr(new BigDecimal("1350.5"))
                        .xcrBseDt("20260903")
                        .cncdFdtnCone("산출 근거")
                        .bseYm("202610")
                        .dfrCleC("1")
                        .sectSysUtzYn("Y")
                        .itrInfrYn("N")
                        .lstYn("Y")
                        .amt(new BigDecimal("4051.5"))
                        .fcAmt(new BigDecimal("3"))
                        .mplAmt(new BigDecimal("2"))
                        .build();

        ProjectDto.BitemmDto result = ProjectDto.BitemmDto.fromEntity(entity);

        assertThat(result.getGclMngNo()).isEqualTo("GCL-2026-0001");
        assertThat(result.getSno()).isEqualTo(2);
        assertThat(result.getGclNm()).isEqualTo("서버");
        assertThat(result.getCncdFdtnCone()).isEqualTo("산출 근거");
        assertThat(result.getAmt()).isEqualByComparingTo("4051.5");
        assertThat(result.getFcAmt()).isEqualByComparingTo("3");
        assertThat(result.getMplAmt()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("사업과 품목의 BYTE 상한은 UTF-8 바이트로 판정한다")
    void byteLimitValidation_usesUtf8Bytes() {
        ProjectDto.CreateRequest validProject =
                ProjectDto.CreateRequest.builder().abusNm("가".repeat(33) + "a").build();
        ProjectDto.CreateRequest invalidProject =
                ProjectDto.CreateRequest.builder().abusNm("가".repeat(34)).build();
        ProjectDto.BitemmDto validItem =
                ProjectDto.BitemmDto.builder().gclNm("가".repeat(33) + "a").build();
        ProjectDto.BitemmDto invalidItem =
                ProjectDto.BitemmDto.builder().gclNm("가".repeat(34)).build();

        assertThat(validProject.isTextFieldsWithinByteLimit()).isTrue();
        assertThat(invalidProject.isTextFieldsWithinByteLimit()).isFalse();
        assertThat(validItem.isTextFieldsWithinByteLimit()).isTrue();
        assertThat(invalidItem.isTextFieldsWithinByteLimit()).isFalse();
    }
}
