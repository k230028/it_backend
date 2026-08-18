package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 비목·환율 공통코드를 이관용 맵으로 접는 규칙을 고정합니다. */
@ExtendWith(MockitoExtension.class)
class MigrationIoeCatalogReaderTest {

    @Mock private CodeRepository codeRepository;

    private MigrationIoeCatalogReader readerWithRepo() {
        return new MigrationIoeCatalogReader(codeRepository);
    }

    @Test
    @DisplayName("비목 코드값명 → 코드값 맵을 만든다")
    void 비목_맵을_만든다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(List.of(code("001", "국내전산임차료"), code("011", "유지보수료")));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).containsEntry("국내전산임차료", "001").containsEntry("유지보수료", "011");
    }

    @Test
    @DisplayName("같은 코드값명이 중복되면 먼저 나온 코드값을 유지한다")
    void 중복_코드값명은_먼저나온값을_유지한다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(List.of(code("001", "유지보수료"), code("999", "유지보수료")));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).containsEntry("유지보수료", "001");
    }

    @Test
    @DisplayName("코드값명이 null인 행은 건너뛴다")
    void 코드값명이_null이면_건너뛴다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N"))
                .thenReturn(
                        List.of(Ccodem.builder().cId(CommonCodeGroups.IOE).cdva("001").build()));

        Map<String, String> result = readerWithRepo().ioeCodeByName();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("통화 → 예산환율 맵을 만든다")
    void 환율_맵을_만든다() {
        when(codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null))
                .thenReturn(List.of(currency("GBP", "1924"), currency("USD", "1432.5")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result)
                .containsEntry("GBP", new BigDecimal("1924"))
                .containsEntry("USD", new BigDecimal("1432.5"));
    }

    @Test
    @DisplayName("환율 값이 숫자로 파싱되지 않는 행은 예외를 던지지 않고 건너뛴다")
    void 숫자가_아닌_환율은_건너뛴다() {
        when(codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null))
                .thenReturn(List.of(currency("XXX", "해당없음"), currency("GBP", "1924")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).doesNotContainKey("XXX").containsEntry("GBP", new BigDecimal("1924"));
    }

    @Test
    @DisplayName("환율 값이 null인 행은 건너뛴다")
    void 환율값이_null이면_건너뛴다() {
        when(codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId(CommonCodeGroups.CURRENCY)
                                        .cdva("JPY")
                                        .build()));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("같은 통화가 중복되면 먼저 나온 환율을 유지한다")
    void 중복_통화는_먼저나온값을_유지한다() {
        when(codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null))
                .thenReturn(List.of(currency("GBP", "1924"), currency("GBP", "9999")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).containsEntry("GBP", new BigDecimal("1924"));
    }

    @Test
    @DisplayName("환율은 resolveXcr과 같은 유효일자 필터로 읽는다 — 만료 행은 조회 자체에 들어오지 않는다")
    void 환율은_유효일자_필터로_읽는다() {
        // findByCIdAndDelYn(유효일자 무시)으로 되돌리면 이 스텁이 비어 맵이 빈 채로 나온다.
        when(codeRepository.findByCIdWithValidDate(CommonCodeGroups.CURRENCY, null))
                .thenReturn(List.of(currency("GBP", "1924")));

        Map<String, BigDecimal> result = readerWithRepo().xcrByCurrency();

        assertThat(result).containsExactly(java.util.Map.entry("GBP", new BigDecimal("1924")));
        // 유효일자를 보지 않는 조회는 아예 쓰지 않는다 (resolveXcr과 판정 기준이 어긋나는 지점)
        org.mockito.Mockito.verify(codeRepository, org.mockito.Mockito.never())
                .findByCIdAndDelYn(CommonCodeGroups.CURRENCY, "N");
    }

    @Test
    @DisplayName("추진가능성·전결권·사업코드 카탈로그를 만든다 — 전결권은 자본 계열만 채택한다")
    void 코드_카탈로그를_만든다() {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.EXE_POSSIBLE, "N"))
                .thenReturn(
                        List.of(
                                named(CommonCodeGroups.EXE_POSSIBLE, "1", "확정", null),
                                named(CommonCodeGroups.EXE_POSSIBLE, "2", "미정(검토중)", null)));
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.EDRT, "N"))
                .thenReturn(
                        List.of(
                                named(CommonCodeGroups.EDRT, "12", "부문장", "EDRT_MNGC"),
                                named(CommonCodeGroups.EDRT, "22", "부문장", "EDRT_CPIT"),
                                named(CommonCodeGroups.EDRT, "25", "이사회", "EDRT_CPIT")));
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.ABUS_UNIT, "N"))
                .thenReturn(List.of(named(CommonCodeGroups.ABUS_UNIT, "571", "운영시스템 유지보수", null)));

        MigrationIoeCatalogReader reader = readerWithRepo();

        assertThat(reader.exePttCodeByName()).containsEntry("미정(검토중)", "2");
        // 같은 이름이 경상(12)·자본(22) 양쪽에 있으므로 코드타입으로 갈라야 자본 코드가 나온다
        assertThat(reader.edrtCapitalCodeByName())
                .containsEntry("부문장", "22")
                .containsEntry("이사회", "25")
                .hasSize(2);
        assertThat(reader.abusUnitNameByCode()).containsEntry("571", "운영시스템 유지보수");
    }

    @Test
    @DisplayName("전결권 자본예산 후보는 자본 계열만 담고 후보값은 코드값이다")
    void 전결권_자본_후보를_만든다() {
        // 경상 계열(EDRT_MNGC) 행을 섞어 두어야 계열 필터가 실제로 걸러내는지 검증된다.
        // 물리 컬럼 IT_PTL_EDRT_TC는 2자리 코드이므로 후보값은 코드값명이 아니라 코드값이어야 한다.
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.EDRT, "N"))
                .thenReturn(
                        List.of(
                                named(CommonCodeGroups.EDRT, "12", "부문장", "EDRT_MNGC"),
                                named(CommonCodeGroups.EDRT, "22", "부문장", "EDRT_CPIT"),
                                named(CommonCodeGroups.EDRT, "25", "이사회", "EDRT_CPIT")));

        List<MigrationDto.Candidate> result = readerWithRepo().edrtCapitalCandidates();

        assertThat(result).extracting(MigrationDto.Candidate::code).containsExactly("22", "25");
        assertThat(result).extracting(MigrationDto.Candidate::label).containsExactly("부문장", "이사회");
    }

    @Test
    @DisplayName("일반관리비 기본 편성률을 DUP_IOE_MNGC 코드에서 읽는다")
    void 일반관리비_편성률을_읽는다() {
        when(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .thenReturn(
                        List.of(
                                named("DUP_IOE", "237", "자산비", "DUP_IOE_CPIT"),
                                Ccodem.builder()
                                        .cId("DUP_IOE")
                                        .cdva("999")
                                        .cTp("DUP_IOE_MNGC")
                                        .cdvaDtlC("90")
                                        .build()));

        assertThat(readerWithRepo().generalExpenseRate()).isEqualByComparingTo("90");
    }

    @Test
    @DisplayName("DUP_IOE_MNGC 코드가 없으면 100을 기본값으로 돌려준다")
    void 일반관리비_편성률_코드가_없으면_100이다() {
        when(codeRepository.findByCIdWithValidDate("DUP_IOE", null)).thenReturn(List.of());

        assertThat(readerWithRepo().generalExpenseRate()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("DUP_IOE_MNGC 값이 숫자가 아니면 100을 기본값으로 돌려준다")
    void 일반관리비_편성률이_숫자가_아니면_100이다() {
        when(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("DUP_IOE")
                                        .cdva("999")
                                        .cTp("DUP_IOE_MNGC")
                                        .cdvaDtlC("해당없음")
                                        .build()));

        assertThat(readerWithRepo().generalExpenseRate()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("DUP_IOE_MNGC 코드는 있는데 값 상세가 null이면 건너뛰고 100을 기본값으로 돌려준다")
    void DUP_IOE_MNGC_값이_null이면_100이다() {
        // 코드가 아예 없는 경우(위 테스트)·숫자가 아닌 경우와 달리, 코드타입은 일치하는데 CDVA_DTL_C 자체가
        // null인 행이다 — parse 시도 없이 건너뛰어야 한다(건너뛰지 않으면 trim()에서 NPE).
        when(codeRepository.findByCIdWithValidDate("DUP_IOE", null))
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("DUP_IOE")
                                        .cdva("999")
                                        .cTp("DUP_IOE_MNGC")
                                        .build()));

        assertThat(readerWithRepo().generalExpenseRate()).isEqualByComparingTo("100");
    }

    private static Ccodem named(String cId, String cdva, String cdvaNm, String cTp) {
        return Ccodem.builder().cId(cId).cdva(cdva).cdvaNm(cdvaNm).cTp(cTp).build();
    }

    private static Ccodem code(String cdva, String cdvaNm) {
        return Ccodem.builder().cId(CommonCodeGroups.IOE).cdva(cdva).cdvaNm(cdvaNm).build();
    }

    private static Ccodem currency(String cdva, String cdvaDtlC) {
        return Ccodem.builder()
                .cId(CommonCodeGroups.CURRENCY)
                .cdva(cdva)
                .cdvaDtlC(cdvaDtlC)
                .build();
    }
}
