package com.kdb.it.common.i18n.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationEntryServiceTest {

    @Mock private CmenumRepository menuRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ClangmRepository translationRepository;

    @Test
    void 메뉴는_번역이_있으면_번역완료로_표시한다() {
        TranslationEntryService service = service();
        when(menuRepository.findAllActive()).thenReturn(List.of(menu("MNU0001001", "대시보드")));
        when(translationRepository.findActiveByTargetAndKeys("메뉴", List.of("MNU0001001")))
                .thenReturn(
                        List.of(
                                translation(
                                        "MNU0001001",
                                        "en",
                                        TranslationColumns.MNU_NM,
                                        "Dashboard")));

        List<TranslationDto.TranslationEntry> entries = service.findEntries(TranslationTarget.MENU);

        assertThat(entries).hasSize(1);
        TranslationDto.TranslationEntry entry = entries.get(0);
        assertThat(entry.targetKey()).isEqualTo("MNU0001001");
        assertThat(entry.label()).isEqualTo("MNU0001001");
        assertThat(entry.source()).containsEntry("mnuId", "MNU0001001");
        assertThat(entry.translated()).isTrue();
        assertThat(entry.columns()).hasSize(1);
        assertThat(entry.columns().get(0).koText()).isEqualTo("대시보드");
        assertThat(entry.columns().get(0).maxLength()).isEqualTo(100);
        assertThat(entry.columns().get(0).translations()).containsEntry("en", "Dashboard");
    }

    @Test
    void 번역이_없는_메뉴는_미번역으로_표시한다() {
        TranslationEntryService service = service();
        when(menuRepository.findAllActive()).thenReturn(List.of(menu("MNU0001002", "예산")));
        when(translationRepository.findActiveByTargetAndKeys("메뉴", List.of("MNU0001002")))
                .thenReturn(List.of());

        List<TranslationDto.TranslationEntry> entries = service.findEntries(TranslationTarget.MENU);

        assertThat(entries.get(0).translated()).isFalse();
        assertThat(entries.get(0).columns().get(0).translations()).isEmpty();
        assertThat(entries.get(0).lastChangedAt()).isNull();
    }

    @Test
    void 공통코드는_길이prefix_대상키로_번역을_병합한다() {
        TranslationEntryService service = service();
        when(codeRepository.findAllActive()).thenReturn(List.of(code()));
        String targetKey = "8:ABUS_PPO3:0018:20260101";
        when(translationRepository.findActiveByTargetAndKeys("공통코드", List.of(targetKey)))
                .thenReturn(
                        List.of(
                                translation(
                                        targetKey,
                                        "en",
                                        TranslationColumns.CDVA_NM,
                                        "New Development")));

        List<TranslationDto.TranslationEntry> entries =
                service.findEntries(TranslationTarget.COMMON_CODE);

        assertThat(entries.get(0).targetKey()).isEqualTo(targetKey);
        assertThat(entries.get(0).source())
                .containsEntry("cId", "ABUS_PPO")
                .containsEntry("cdva", "001")
                .containsEntry("sttDt", "20260101");
        assertThat(entries.get(0).columns()).hasSize(5);
        assertThat(columnOf(entries.get(0), TranslationColumns.CDVA_NM).translations())
                .containsEntry("en", "New Development");
        // CO_CDVA_SPS는 원본 2000자와 TC_DES 2000자 중 작은 값이 그대로 2000이다
        assertThat(columnOf(entries.get(0), TranslationColumns.CO_CDVA_SPS).maxLength())
                .isEqualTo(2000);
        assertThat(columnOf(entries.get(0), TranslationColumns.CDVA_NM).maxLength()).isEqualTo(200);
        // 5개 대상 컬럼 중 1개만 번역되었으므로 완료가 아니다
        assertThat(entries.get(0).translated()).isFalse();
    }

    @Test
    void 공통코드에서_원문있는_컬럼만_모두_번역되면_번역완료로_표시한다() {
        TranslationEntryService service = service();
        Ccodem code =
                Ccodem.builder()
                        .cId("ABUS_PPO")
                        .cdva("001")
                        .sttDt("20260101")
                        .cNm("사업목적")
                        .cdvaNm("신규개발")
                        .cdvaDes(null)
                        .cdvaDtl(null)
                        .cTpDes(null)
                        .delYn("N")
                        .build();
        when(codeRepository.findAllActive()).thenReturn(List.of(code));
        String targetKey = TranslationTargetKey.code("ABUS_PPO", "001", "20260101");
        when(translationRepository.findActiveByTargetAndKeys("공통코드", List.of(targetKey)))
                .thenReturn(
                        List.of(
                                translation(
                                        targetKey,
                                        "en",
                                        TranslationColumns.CO_C_NM,
                                        "Business Purpose"),
                                translation(
                                        targetKey,
                                        "en",
                                        TranslationColumns.CDVA_NM,
                                        "New Development")));

        List<TranslationDto.TranslationEntry> entries =
                service.findEntries(TranslationTarget.COMMON_CODE);

        // 원문 없는 CO_CDVA_ABV_NM/CO_CDVA_SPS/CO_C_INTN_CONE도 목록에는 그대로 남는다
        assertThat(entries.get(0).columns()).hasSize(5);
        assertThat(entries.get(0).translated()).isTrue();
    }

    @Test
    void 원문있는_컬럼_중_하나라도_번역이_비면_미번역으로_표시한다() {
        TranslationEntryService service = service();
        Ccodem code =
                Ccodem.builder()
                        .cId("ABUS_PPO")
                        .cdva("001")
                        .sttDt("20260101")
                        .cNm("사업목적")
                        .cdvaNm("신규개발")
                        .cdvaDes(null)
                        .cdvaDtl(null)
                        .cTpDes("사업목적 코드")
                        .delYn("N")
                        .build();
        when(codeRepository.findAllActive()).thenReturn(List.of(code));
        String targetKey = TranslationTargetKey.code("ABUS_PPO", "001", "20260101");
        // CO_C_INTN_CONE(cTpDes)에는 원문이 있지만 번역을 등록하지 않는다
        when(translationRepository.findActiveByTargetAndKeys("공통코드", List.of(targetKey)))
                .thenReturn(
                        List.of(
                                translation(
                                        targetKey,
                                        "en",
                                        TranslationColumns.CO_C_NM,
                                        "Business Purpose"),
                                translation(
                                        targetKey,
                                        "en",
                                        TranslationColumns.CDVA_NM,
                                        "New Development")));

        List<TranslationDto.TranslationEntry> entries =
                service.findEntries(TranslationTarget.COMMON_CODE);

        assertThat(entries.get(0).translated()).isFalse();
    }

    @Test
    void 번역대상_원문이_전부_비어있으면_번역완료로_표시한다() {
        TranslationEntryService service = service();
        Ccodem code =
                Ccodem.builder()
                        .cId("ABUS_PPO")
                        .cdva("001")
                        .sttDt("20260101")
                        .cNm(null)
                        .cdvaNm(null)
                        .cdvaDes(null)
                        .cdvaDtl(null)
                        .cTpDes(null)
                        .delYn("N")
                        .build();
        when(codeRepository.findAllActive()).thenReturn(List.of(code));
        String targetKey = TranslationTargetKey.code("ABUS_PPO", "001", "20260101");
        when(translationRepository.findActiveByTargetAndKeys("공통코드", List.of(targetKey)))
                .thenReturn(List.of());

        List<TranslationDto.TranslationEntry> entries =
                service.findEntries(TranslationTarget.COMMON_CODE);

        // 번역할 원문 자체가 없으므로 컬럼 목록은 유지한 채 완료로 취급한다
        assertThat(entries.get(0).columns()).hasSize(5);
        assertThat(entries.get(0).translated()).isTrue();
    }

    @Test
    void 서비스의_길이_상수_맵이_번역대상_컬럼_전체와_정확히_일치한다() {
        // TranslationTarget.columns()가 번역 가능 컬럼의 단일 진실 공급원이므로, 서비스 내부
        // 길이 상수 맵의 키 집합이 그 합집합과 어긋나면(enum에 컬럼 추가·삭제 시) 이 테스트가 실패해야 한다.
        // 상수를 나열하지 않고 values()를 순회해야 세 번째 대상 구분이 추가돼도 드리프트를 잡는다.
        java.util.Set<String> expectedColumns =
                java.util.Arrays.stream(TranslationTarget.values())
                        .flatMap(target -> target.columns().stream())
                        .collect(java.util.stream.Collectors.toSet());

        assertThat(TranslationEntryService.sourceColumnNames()).isEqualTo(expectedColumns);
    }

    @Test
    void 종료일자가_지난_공통코드는_번역현황목록에서_제외한다() {
        TranslationEntryService service = service();
        String yesterday =
                java.time.LocalDate.now()
                        .minusDays(1)
                        .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        Ccodem expired =
                Ccodem.builder()
                        .cId("ABUS_PPO")
                        .cdva("999")
                        .sttDt("20200101")
                        .endDt(yesterday)
                        .cNm("종료된코드")
                        .delYn("N")
                        .build();
        Ccodem active = code();
        when(codeRepository.findAllActive()).thenReturn(List.of(expired, active));
        when(translationRepository.findActiveByTargetAndKeys(
                        org.mockito.ArgumentMatchers.eq("공통코드"),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        List<TranslationDto.TranslationEntry> entries =
                service.findEntries(TranslationTarget.COMMON_CODE);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).source()).containsEntry("cdva", "001");
    }

    @Test
    void 대상키가_구백개를_넘으면_나눠서_조회한다() {
        TranslationEntryService service = service();
        List<Cmenum> menus =
                java.util.stream.IntStream.range(0, 901)
                        .mapToObj(index -> menu("MNU" + index, "메뉴" + index))
                        .toList();
        when(menuRepository.findAllActive()).thenReturn(menus);
        when(translationRepository.findActiveByTargetAndKeys(
                        org.mockito.ArgumentMatchers.eq("메뉴"),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());

        assertThat(service.findEntries(TranslationTarget.MENU)).hasSize(901);
        org.mockito.Mockito.verify(translationRepository, org.mockito.Mockito.times(2))
                .findActiveByTargetAndKeys(
                        org.mockito.ArgumentMatchers.eq("메뉴"),
                        org.mockito.ArgumentMatchers.anyList());
    }

    private TranslationEntryService service() {
        return new TranslationEntryService(menuRepository, codeRepository, translationRepository);
    }

    private static TranslationDto.TranslationColumnValue columnOf(
            TranslationDto.TranslationEntry entry, String columnName) {
        return entry.columns().stream()
                .filter(column -> column.columnName().equals(columnName))
                .findFirst()
                .orElseThrow();
    }

    private static Cmenum menu(String mnuId, String mnuNm) {
        return Cmenum.builder().mnuId(mnuId).mnuNm(mnuNm).delYn("N").build();
    }

    private static Ccodem code() {
        return Ccodem.builder()
                .cId("ABUS_PPO")
                .cdva("001")
                .sttDt("20260101")
                .cNm("사업목적")
                .cdvaNm("신규개발")
                .cdvaDes("신규")
                .cdvaDtl("신규 개발 사업")
                .cTpDes("사업목적 코드")
                .delYn("N")
                .build();
    }

    private static Clangm translation(
            String targetKey, String language, String columnName, String text) {
        return Clangm.builder()
                .tcIdCone(targetKey)
                .dttLanC(language)
                .tcColNm(columnName)
                .tcDes(text)
                .dttNm("메뉴")
                .delYn("N")
                .build();
    }
}
