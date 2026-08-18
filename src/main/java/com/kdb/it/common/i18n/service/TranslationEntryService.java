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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public List<TranslationDto.TranslationEntry> findEntries(TranslationTarget target) {
        List<Draft> drafts = target == TranslationTarget.MENU ? menuDrafts() : commonCodeDrafts();
        Map<String, List<Clangm>> translations =
                findTranslations(target, drafts.stream().map(Draft::targetKey).toList());

        List<TranslationDto.TranslationEntry> entries = new ArrayList<>(drafts.size());
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
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (Ccodem code : codeRepository.findAllActive()) {
            // CodeRepository.findAllActive()는 DEL_YN만 거르고 END_DT 만료를 보지 않는다(관리자 공통코드
            // 화면은 만료 코드도 조회·편집해야 하므로 공유 메서드는 그대로 둔다). 번역 현황 화면에는 번역할
            // 필요가 없는 종료 코드값이 "미번역"으로 쌓이지 않도록 이 서비스에서만 만료 코드를 제외한다.
            if (isExpired(code.getEndDt(), today)) {
                continue;
            }
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

    private TranslationDto.TranslationEntry toEntry(Draft draft, List<Clangm> rows) {
        List<TranslationDto.TranslationColumnValue> columns = new ArrayList<>();
        // 번역할 한국어 원문이 있는 컬럼만 완료 판정에 넣는다. 원문이 없는 컬럼(공통코드 nullable
        // 컬럼 다수)까지 미번역으로 세면, 번역 행을 만들 원문 자체가 없어 영원히 완료가 될 수 없다.
        boolean hasSourceText = false;
        boolean sourcedColumnsAllTranslated = true;
        for (Map.Entry<String, String> koText : draft.koTexts().entrySet()) {
            Map<String, String> byLanguage = new LinkedHashMap<>();
            for (Clangm row : rows) {
                if (row.getTcColNm().equals(koText.getKey())
                        && row.getTcDes() != null
                        && !row.getTcDes().isBlank()) {
                    byLanguage.put(row.getDttLanC(), row.getTcDes());
                }
            }
            boolean hasSource = koText.getValue() != null && !koText.getValue().isBlank();
            if (hasSource) {
                hasSourceText = true;
                if (byLanguage.isEmpty()) {
                    sourcedColumnsAllTranslated = false;
                }
            }
            columns.add(
                    new TranslationDto.TranslationColumnValue(
                            koText.getKey(),
                            koText.getValue(),
                            maxLengthOf(koText.getKey()),
                            byLanguage));
        }
        // 번역 대상 컬럼의 원문이 전부 비어 있으면(hasSourceText=false) 번역할 것이 아예 없으므로
        // 미번역 필터에 남길 이유가 없어 완료로 취급한다.
        boolean translated = !hasSourceText || sourcedColumnsAllTranslated;

        Clangm latest = null;
        for (Clangm row : rows) {
            if (latest == null || isAfter(row.getLstChgDtm(), latest.getLstChgDtm())) {
                latest = row;
            }
        }
        return new TranslationDto.TranslationEntry(
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

    /**
     * {@link #SOURCE_COLUMN_LENGTHS}에 등록된 컬럼명 집합을 드러냅니다.
     *
     * <p>{@code TranslationTarget.columns()}가 번역 가능 컬럼의 단일 진실 공급원이고 이 맵은 그 컬럼들의 길이만 나열합니다. 필드 자체의
     * 가시성을 넓히는 대신 조회 진입점만 패키지 전용으로 열어, 드리프트 방지 테스트가 두 목록이 정확히 일치하는지 검증할 수 있게 합니다.
     */
    static Set<String> sourceColumnNames() {
        return SOURCE_COLUMN_LENGTHS.keySet();
    }

    /** END_DT가 null이 아니고 기준일(YYYYMMDD) 미만이면 만료로 본다. 두 값 모두 문자열이라 사전식 비교가 곧 날짜 비교와 일치한다. */
    private static boolean isExpired(String endDt, String today) {
        return endDt != null && endDt.compareTo(today) < 0;
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
