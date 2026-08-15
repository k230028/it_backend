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
