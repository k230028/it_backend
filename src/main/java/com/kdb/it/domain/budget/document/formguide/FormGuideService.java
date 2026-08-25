package com.kdb.it.domain.budget.document.formguide;

import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.exception.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 사업 입력 필드별 길라잡이의 공개 조회와 관리자 저장 흐름을 처리합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormGuideService {

    private static final String FORM_GUIDE_DOCUMENT_PREFIX = "FDOC-";
    private static final String ACTIVE = "N";

    private final GuideDocRepository guideDocRepository;

    /**
     * 해당 사업 유형에 등록된 활성 길라잡이만 공개 응답으로 반환합니다.
     *
     * @param scope 조회할 사업 유형
     * @return 카탈로그에 등록되고 본문이 있는 길라잡이 목록
     * @throws IllegalArgumentException 사업 유형이 없을 때
     */
    public List<FormGuideDto.PublicResponse> getPublished(FormGuideScope scope) {
        Map<String, Bgdocm> documents = activeDocuments(scope);
        return FormGuideCatalog.entries(scope).stream()
                .filter(
                        entry -> {
                            Bgdocm document = documents.get(entry.guideId());
                            return document != null && hasContent(document.getNacTxtInf());
                        })
                .map(
                        entry -> {
                            Bgdocm document = documents.get(entry.guideId());
                            return new FormGuideDto.PublicResponse(
                                    entry.guideId(), entry.fieldLabel(), document.getNacTxtInf());
                        })
                .toList();
    }

    /**
     * 관리 화면에서 사용할 전체 카탈로그와 현재 등록 상태를 반환합니다.
     *
     * @param scope 조회할 사업 유형
     * @return 미등록 항목을 포함한 고정 카탈로그
     * @throws IllegalArgumentException 사업 유형이 없을 때
     */
    public List<FormGuideDto.CatalogResponse> getCatalog(FormGuideScope scope) {
        Map<String, Bgdocm> documents = activeDocuments(scope);
        return FormGuideCatalog.entries(scope).stream()
                .map(
                        entry -> {
                            Bgdocm document = documents.get(entry.guideId());
                            boolean registered =
                                    document != null && hasContent(document.getNacTxtInf());
                            return new FormGuideDto.CatalogResponse(
                                    entry.guideId(),
                                    entry.section(),
                                    entry.fieldLabel(),
                                    entry.controlType(),
                                    registered ? document.getDocMngNo() : null,
                                    registered ? document.getNacTxtInf() : null);
                        })
                .toList();
    }

    /**
     * 카탈로그 ID의 길라잡이 본문을 생성하거나 갱신합니다.
     *
     * @param guideId 경로로 전달된 고정 카탈로그 ID
     * @param request 정화 전 HTML 본문
     * @return 생성 또는 갱신한 입력 길라잡이 문서관리번호
     * @throws IllegalArgumentException 지원하지 않는 ID 또는 요청이 없을 때
     * @throws ResponseStatusException 동시 생성으로 활성 ID가 중복될 때 (409)
     */
    @Transactional
    public String save(String guideId, FormGuideDto.SaveRequest request) {
        FormGuideCatalog.Entry entry = FormGuideCatalog.require(guideId);
        if (request == null) {
            throw new IllegalArgumentException("길라잡이 저장 요청이 없습니다");
        }
        String contentHtml = HtmlSanitizer.sanitize(request.contentHtml());
        if (!hasContent(contentHtml)) {
            throw new IllegalArgumentException("길라잡이 본문을 입력해야 합니다");
        }

        return guideDocRepository
                .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                        entry.guideId(), FORM_GUIDE_DOCUMENT_PREFIX, ACTIVE)
                .map(
                        document -> {
                            document.update(entry.guideId(), contentHtml);
                            return document.getDocMngNo();
                        })
                .orElseGet(() -> create(entry.guideId(), contentHtml));
    }

    /**
     * 등록된 길라잡이를 논리 삭제합니다.
     *
     * @param guideId 삭제할 고정 카탈로그 ID
     * @throws IllegalArgumentException 지원하지 않는 ID일 때
     * @throws NotFoundException 활성 길라잡이가 등록되지 않았을 때
     */
    @Transactional
    public void delete(String guideId) {
        FormGuideCatalog.Entry entry = FormGuideCatalog.require(guideId);
        Bgdocm document =
                guideDocRepository
                        .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                entry.guideId(), FORM_GUIDE_DOCUMENT_PREFIX, ACTIVE)
                        .orElseThrow(
                                () -> new NotFoundException("등록되지 않은 길라잡이입니다: " + entry.guideId()));
        document.delete();
    }

    private Map<String, Bgdocm> activeDocuments(FormGuideScope scope) {
        requireScope(scope);
        return guideDocRepository
                .findActiveFormGuides(FORM_GUIDE_DOCUMENT_PREFIX, scope.guideIdPrefix())
                .stream()
                .filter(document -> isCatalogEntryForScope(document.getDocTtlCone(), scope))
                .collect(Collectors.toMap(Bgdocm::getDocTtlCone, Function.identity()));
    }

    private String create(String guideId, String contentHtml) {
        Long sequence = guideDocRepository.getNextSequenceValue();
        String docMngNo = String.format("FDOC-%d-%04d", LocalDate.now().getYear(), sequence);
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo(docMngNo)
                        .docTtlCone(guideId)
                        .nacTxtInf(contentHtml)
                        .build();
        try {
            guideDocRepository.save(document);
            guideDocRepository.flush();
            return docMngNo;
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 길라잡이입니다", exception);
        }
    }

    private boolean isCatalogEntryForScope(String guideId, FormGuideScope scope) {
        try {
            return FormGuideCatalog.require(guideId).scope() == scope;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void requireScope(FormGuideScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("사업 유형을 입력해야 합니다");
        }
    }

    private static boolean hasContent(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(contentHtml);
        if (!document.select("img, table, math-field, [data-file-id], [data-latex]").isEmpty()) {
            return true;
        }
        return document.text().codePoints().anyMatch(FormGuideService::isVisibleCodePoint);
    }

    private static boolean isVisibleCodePoint(int codePoint) {
        return !Character.isWhitespace(codePoint)
                && !Character.isSpaceChar(codePoint)
                && (codePoint < 0x200B || codePoint > 0x200D)
                && codePoint != 0x2060
                && codePoint != 0xFEFF;
    }
}
