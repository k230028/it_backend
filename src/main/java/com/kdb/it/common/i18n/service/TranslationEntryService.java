package com.kdb.it.common.i18n.service;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.dto.TranslationDto;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 마스터와 번역 마스터를 병합해 관리자 화면용 번역 현황 목록을 만드는 서비스입니다.
 *
 * <p>번역 행 자체의 생성·변경은 {@link TranslationCatalogService}가 담당하고, 이 서비스는 조회만 합니다.
 */
@Service
public class TranslationEntryService {

    /** Oracle IN 절 한계(1000)를 넘지 않도록 나누는 단위입니다. */
    private static final int ORACLE_IN_BATCH_SIZE = 900;

    /** 번역 문구 저장 컬럼 TC_DES의 길이입니다. */
    private static final int TRANSLATION_TEXT_MAX_LENGTH = 2000;

    /** 번역 대상 원본 컬럼의 길이입니다. 화면 입력 제한은 이 값과 TC_DES 길이 중 작은 값을 씁니다. */
    private static final Map<String, Integer> SOURCE_COLUMN_LENGTHS =
            Map.of(
                    TranslationColumns.MNU_NM, 100,
                    TranslationColumns.CO_C_NM, 100,
                    TranslationColumns.CDVA_NM, 200,
                    TranslationColumns.CO_CDVA_ABV_NM, 100,
                    TranslationColumns.CO_CDVA_SPS, 2000,
                    TranslationColumns.CO_C_INTN_CONE, 500);

    private final CmenumRepository menuRepository;
    private final CodeRepository codeRepository;
    private final ClangmRepository translationRepository;

    public TranslationEntryService(
            CmenumRepository menuRepository,
            CodeRepository codeRepository,
            ClangmRepository translationRepository) {
        this.menuRepository = menuRepository;
        this.codeRepository = codeRepository;
        this.translationRepository = translationRepository;
    }

    /**
     * 대상 구분의 활성 원본을 전부 나열하고 등록된 번역을 병합합니다.
     *
     * <p>번역이 없는 원본도 {@code translated=false}로 포함하므로 미번역 항목을 화면에서 찾을 수 있습니다.
     *
     * @param target 번역 대상 구분
     * @return 원본 순서를 유지한 번역 현황 목록
     */
    @Transactional(readOnly = true)
    public List<TranslationDto.Entry> findEntries(TranslationTarget target) {
        List<Draft> drafts = target == TranslationTarget.MENU ? menuDrafts() : commonCodeDrafts();
        Map<String, List<Clangm>> translations =
                findTranslations(target, drafts.stream().map(Draft::targetKey).toList());

        List<TranslationDto.Entry> entries = new ArrayList<>(drafts.size());
        for (Draft draft : drafts) {
            entries.add(toEntry(draft, translations.getOrDefault(draft.targetKey(), List.of())));
        }
        return entries;
    }

    private List<Draft> menuDrafts() {
        List<Draft> drafts = new ArrayList<>();
        for (Cmenum menu : menuRepository.findAllActive()) {
            drafts.add(
                    new Draft(
                            TranslationTargetKey.menu(menu.getMnuId()),
                            Map.of("mnuId", menu.getMnuId()),
                            menu.getMnuId(),
                            Map.of(TranslationColumns.MNU_NM, menu.getMnuNm())));
        }
        return drafts;
    }

    private List<Draft> commonCodeDrafts() {
        List<Draft> drafts = new ArrayList<>();
        for (Ccodem code : codeRepository.findAllActive()) {
            Map<String, String> koTexts = new LinkedHashMap<>();
            koTexts.put(TranslationColumns.CO_C_NM, code.getCNm());
            koTexts.put(TranslationColumns.CDVA_NM, code.getCdvaNm());
            koTexts.put(TranslationColumns.CO_CDVA_ABV_NM, code.getCdvaDes());
            koTexts.put(TranslationColumns.CO_CDVA_SPS, code.getCdvaDtl());
            koTexts.put(TranslationColumns.CO_C_INTN_CONE, code.getCTpDes());
            drafts.add(
                    new Draft(
                            TranslationTargetKey.code(
                                    code.getCId(), code.getCdva(), code.getSttDt()),
                            Map.of(
                                    "cId", code.getCId(),
                                    "cdva", code.getCdva(),
                                    "sttDt", code.getSttDt()),
                            code.getCId() + " / " + code.getCdva(),
                            koTexts));
        }
        return drafts;
    }

    private Map<String, List<Clangm>> findTranslations(
            TranslationTarget target, List<String> targetKeys) {
        Map<String, List<Clangm>> grouped = new LinkedHashMap<>();
        for (int from = 0; from < targetKeys.size(); from += ORACLE_IN_BATCH_SIZE) {
            List<String> batch =
                    List.copyOf(
                            targetKeys.subList(
                                    from,
                                    Math.min(from + ORACLE_IN_BATCH_SIZE, targetKeys.size())));
            for (Clangm row :
                    translationRepository.findActiveByTargetAndKeys(target.dbName(), batch)) {
                grouped.computeIfAbsent(row.getTcIdCone(), ignored -> new ArrayList<>()).add(row);
            }
        }
        return grouped;
    }

    private TranslationDto.Entry toEntry(Draft draft, List<Clangm> rows) {
        List<TranslationDto.ColumnValue> columns = new ArrayList<>();
        boolean translated = true;
        for (Map.Entry<String, String> koText : draft.koTexts().entrySet()) {
            Map<String, String> byLanguage = new LinkedHashMap<>();
            for (Clangm row : rows) {
                if (row.getTcColNm().equals(koText.getKey())
                        && row.getTcDes() != null
                        && !row.getTcDes().isBlank()) {
                    byLanguage.put(row.getDttLanC(), row.getTcDes());
                }
            }
            if (byLanguage.isEmpty()) {
                translated = false;
            }
            columns.add(
                    new TranslationDto.ColumnValue(
                            koText.getKey(),
                            koText.getValue(),
                            maxLengthOf(koText.getKey()),
                            byLanguage));
        }

        Clangm latest = null;
        for (Clangm row : rows) {
            if (latest == null || isAfter(row.getLstChgDtm(), latest.getLstChgDtm())) {
                latest = row;
            }
        }
        return new TranslationDto.Entry(
                draft.targetKey(),
                draft.source(),
                draft.label(),
                columns,
                translated,
                latest == null ? null : latest.getLstChgUsid(),
                latest == null ? null : latest.getLstChgDtm());
    }

    /** 원본 컬럼 길이와 번역 문구 길이 중 작은 값을 화면 입력 제한으로 씁니다. */
    private static int maxLengthOf(String columnName) {
        return Math.min(
                TRANSLATION_TEXT_MAX_LENGTH,
                SOURCE_COLUMN_LENGTHS.getOrDefault(columnName, TRANSLATION_TEXT_MAX_LENGTH));
    }

    private static boolean isAfter(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    /** 원본 한 건에서 뽑아낸 병합 전 값입니다. */
    private record Draft(
            String targetKey,
            Map<String, String> source,
            String label,
            Map<String, String> koTexts) {}
}
