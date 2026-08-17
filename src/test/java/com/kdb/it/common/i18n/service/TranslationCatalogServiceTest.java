package com.kdb.it.common.i18n.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.entity.ClangmId;
import com.kdb.it.common.i18n.model.SupportedLanguage;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TranslationCatalogServiceTest {

    @Mock private ClangmRepository repository;

    @Test
    void 한국어조회는_번역저장소를_호출하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        assertThat(service.findActive(TranslationTarget.MENU, SupportedLanguage.KO, List.of("M1")))
                .isEmpty();
        verify(repository, never()).findActiveByTargetAndLanguageAndKeys(any(), any(), any());
    }

    @Test
    void 영어조회는_활성번역을_필드별_맵으로_반환한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.findActiveByTargetAndLanguageAndKeys("메뉴", "en", List.of("M1")))
                .thenReturn(List.of(row("M1", "en", TranslationColumns.MNU_NM, "Dashboard", "N")));

        assertThat(service.findActive(TranslationTarget.MENU, SupportedLanguage.EN, List.of("M1")))
                .containsEntry("M1", java.util.Map.of(TranslationColumns.MNU_NM, "Dashboard"));
    }

    @Test
    void 대상키가_천개를_넘어도_최대_구백개씩_조회한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        List<String> keys = IntStream.range(0, 1001).mapToObj(i -> "M" + i).toList();

        service.findActive(TranslationTarget.MENU, SupportedLanguage.EN, keys);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findActiveByTargetAndLanguageAndKeys(eq("메뉴"), eq("en"), captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(900, 101);
    }

    @Test
    void 저장은_삭제행을_복원하고_빈문구는_기존행만_삭제한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        Clangm deleted = row("M1", "en", TranslationColumns.MNU_NM, "Old", "Y");
        when(repository.findById(new ClangmId("M1", "en", TranslationColumns.MNU_NM)))
                .thenReturn(Optional.of(deleted));

        service.apply(
                TranslationTarget.MENU,
                "M1",
                List.of(new TranslationDto.Value("en", TranslationColumns.MNU_NM, "New")));
        assertThat(deleted.getTcDes()).isEqualTo("New");
        assertThat(deleted.getDelYn()).isEqualTo("N");

        service.apply(
                TranslationTarget.MENU,
                "M1",
                List.of(new TranslationDto.Value("en", TranslationColumns.MNU_NM, " ")));
        assertThat(deleted.getDelYn()).isEqualTo("Y");
    }

    @Test
    void 번역목록이_null이거나_비어있으면_기존값을_유지한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        service.apply(TranslationTarget.MENU, "M1", null);
        service.apply(TranslationTarget.MENU, "M1", List.of());

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    @Test
    void 한국어_허용외컬럼_중복항목은_저장하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        assertThatThrownBy(
                        () ->
                                service.apply(
                                        TranslationTarget.MENU,
                                        "M1",
                                        List.of(
                                                new TranslationDto.Value(
                                                        "ko", TranslationColumns.MNU_NM, "메뉴"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        TranslationTarget.MENU,
                                        "M1",
                                        List.of(
                                                new TranslationDto.Value(
                                                        "en", "CDVA_NM", "Wrong"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        TranslationTarget.MENU,
                                        "M1",
                                        List.of(
                                                new TranslationDto.Value(
                                                        "en", TranslationColumns.MNU_NM, "A"),
                                                new TranslationDto.Value(
                                                        "en", TranslationColumns.MNU_NM, "B"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 대상키_이동은_활성행을_복제하고_기존행을_논리삭제한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        Clangm old = row("M1", "en", TranslationColumns.MNU_NM, "Dashboard", "N");
        when(repository.existsByDttNmAndTcIdCone("메뉴", "M2")).thenReturn(false);
        when(repository.findByDttNmAndTcIdCone("메뉴", "M1")).thenReturn(List.of(old));

        service.moveTarget(TranslationTarget.MENU, "M1", "M2");

        ArgumentCaptor<Clangm> captor = ArgumentCaptor.forClass(Clangm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTcIdCone()).isEqualTo("M2");
        assertThat(captor.getValue().getTcDes()).isEqualTo("Dashboard");
        assertThat(old.getDelYn()).isEqualTo("Y");
    }

    @Test
    void 대상키가_null이거나_비어있으면_저장소를_호출하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        assertThat(service.findActive(TranslationTarget.MENU, SupportedLanguage.EN, null))
                .isEmpty();
        assertThat(service.findActive(TranslationTarget.MENU, SupportedLanguage.EN, List.of()))
                .isEmpty();
        verify(repository, never()).findActiveByTargetAndLanguageAndKeys(any(), any(), any());
    }

    @Test
    void 문구가_비어있는_활성행은_결과에서_제외한다() {
        // 번역행이 있으나 문구가 공백이면 원본 fallback 대상이므로 맵에 담지 않는다.
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.findActiveByTargetAndLanguageAndKeys("메뉴", "en", List.of("M1", "M2", "M3")))
                .thenReturn(
                        List.of(
                                row("M1", "en", TranslationColumns.MNU_NM, null, "N"),
                                row("M2", "en", TranslationColumns.MNU_NM, "  ", "N"),
                                row("M3", "en", TranslationColumns.MNU_NM, "Budget", "N")));

        assertThat(
                        service.findActive(
                                TranslationTarget.MENU,
                                SupportedLanguage.EN,
                                List.of("M1", "M2", "M3")))
                .containsOnlyKeys("M3");
    }

    @Test
    void 중복_대상키는_한_번만_조회한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        service.findActive(TranslationTarget.MENU, SupportedLanguage.EN, List.of("M1", "M1", "M2"));

        verify(repository).findActiveByTargetAndLanguageAndKeys("메뉴", "en", List.of("M1", "M2"));
    }

    @Test
    void 관리자조회는_삭제행과_빈문구행을_걸러_언어_컬럼_문구만_반환한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.findByDttNmAndTcIdCone("메뉴", "M1"))
                .thenReturn(
                        List.of(
                                row("M1", "en", TranslationColumns.MNU_NM, "Dashboard", "N"),
                                row("M1", "en", TranslationColumns.MNU_NM, "Deleted", "Y"),
                                row("M1", "en", TranslationColumns.MNU_NM, " ", "N"),
                                row("M1", "en", TranslationColumns.MNU_NM, null, "N")));

        assertThat(service.findAll(TranslationTarget.MENU, "M1"))
                .containsExactly(
                        new TranslationDto.Value("en", TranslationColumns.MNU_NM, "Dashboard"));
    }

    @Test
    void 신규_번역은_대상명과_함께_새_행으로_저장한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.findById(new ClangmId("M1", "en", TranslationColumns.MNU_NM)))
                .thenReturn(Optional.empty());

        service.apply(
                TranslationTarget.MENU,
                "M1",
                List.of(new TranslationDto.Value("en", TranslationColumns.MNU_NM, "Dashboard")));

        ArgumentCaptor<Clangm> captor = ArgumentCaptor.forClass(Clangm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue())
                .satisfies(
                        saved -> {
                            assertThat(saved.getTcIdCone()).isEqualTo("M1");
                            assertThat(saved.getDttLanC()).isEqualTo("en");
                            assertThat(saved.getTcColNm()).isEqualTo(TranslationColumns.MNU_NM);
                            assertThat(saved.getTcDes()).isEqualTo("Dashboard");
                            assertThat(saved.getDttNm()).isEqualTo("메뉴");
                            assertThat(saved.getDelYn()).isEqualTo("N");
                        });
    }

    @Test
    void 문구가_null인_항목은_기존행이_없으면_아무것도_하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.findById(new ClangmId("M1", "en", TranslationColumns.MNU_NM)))
                .thenReturn(Optional.empty());

        service.apply(
                TranslationTarget.MENU,
                "M1",
                List.of(new TranslationDto.Value("en", TranslationColumns.MNU_NM, null)));

        verify(repository, never()).save(any());
    }

    @Test
    void null항목과_2000자초과문구는_거부한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        List<TranslationDto.Value> withNull = new java.util.ArrayList<>();
        withNull.add(null);

        assertThatThrownBy(() -> service.apply(TranslationTarget.MENU, "M1", withNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
        assertThatThrownBy(
                        () ->
                                service.apply(
                                        TranslationTarget.MENU,
                                        "M1",
                                        List.of(
                                                new TranslationDto.Value(
                                                        "en",
                                                        TranslationColumns.MNU_NM,
                                                        "A".repeat(2001)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
        verify(repository, never()).save(any());
    }

    @Test
    void 대상키가_공백이거나_255자를_넘으면_거부한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        assertThatThrownBy(() -> service.findAll(TranslationTarget.MENU, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공백");
        assertThatThrownBy(() -> service.findAll(TranslationTarget.MENU, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(TranslationTarget.MENU, "A".repeat(256)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void 대상키_전체삭제는_삭제여부와_무관하게_모든_행을_논리삭제한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        Clangm active = row("M1", "en", TranslationColumns.MNU_NM, "Dashboard", "N");
        when(repository.findByDttNmAndTcIdCone("메뉴", "M1")).thenReturn(List.of(active));

        service.softDeleteTarget(TranslationTarget.MENU, "M1");

        assertThat(active.getDelYn()).isEqualTo("Y");
    }

    @Test
    void 같은_대상키로의_이동은_아무것도_하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);

        service.moveTarget(TranslationTarget.MENU, "M1", "M1");

        verify(repository, never()).existsByDttNmAndTcIdCone(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void 새_대상키에_번역이_이미_있으면_이동을_거부한다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        when(repository.existsByDttNmAndTcIdCone("메뉴", "M2")).thenReturn(true);

        assertThatThrownBy(() -> service.moveTarget(TranslationTarget.MENU, "M1", "M2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("M2");
        verify(repository, never()).save(any());
    }

    @Test
    void 이동_대상의_삭제행은_복제하지_않는다() {
        TranslationCatalogService service = new TranslationCatalogService(repository);
        Clangm deleted = row("M1", "en", TranslationColumns.MNU_NM, "Old", "Y");
        when(repository.existsByDttNmAndTcIdCone("메뉴", "M2")).thenReturn(false);
        when(repository.findByDttNmAndTcIdCone("메뉴", "M1")).thenReturn(List.of(deleted));

        service.moveTarget(TranslationTarget.MENU, "M1", "M2");

        verify(repository, never()).save(any());
    }

    private static Clangm row(
            String key, String language, String column, String text, String delYn) {
        return Clangm.builder()
                .tcIdCone(key)
                .dttLanC(language)
                .tcColNm(column)
                .tcDes(text)
                .dttNm("메뉴")
                .delYn(delYn)
                .build();
    }
}
