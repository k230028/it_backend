package com.kdb.it.common.popup;

import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TPRMPP_BGDOCM의 단일 공통 안내 팝업을 조회·저장·게시 중지합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonPopupService {

    /** 공통 안내 팝업을 식별하는 DOC_TTL_CONE 고정값입니다. */
    public static final String DOCUMENT_IDENTIFIER = "common.popup";

    /** 사업 작성·조회 화면의 전결권 안내를 식별하는 DOC_TTL_CONE 고정값입니다. */
    public static final String APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER =
            "project.approval-authority";

    private static final String DOCUMENT_NUMBER_PREFIX = "PDOC-";

    private final GuideDocRepository guideDocRepository;
    private final CommonPopupCreationService creationService;

    /** 인증 사용자에게 게시 중인 팝업을 반환합니다. */
    public Optional<CommonPopupDto.Response> getActivePopup() {
        return findActiveDocument(DOCUMENT_IDENTIFIER).map(this::toResponse);
    }

    /** 인증 사용자에게 게시 중인 전결권 안내를 반환합니다. */
    public Optional<CommonPopupDto.Response> getApprovalAuthorityNotice() {
        return findActiveDocument(APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER).map(this::toResponse);
    }

    /** 관리자에게 현재 등록 상태를 반환합니다. */
    public CommonPopupDto.AdminResponse getAdminPopup() {
        return getAdminDocument(DOCUMENT_IDENTIFIER);
    }

    /** 관리자에게 현재 전결권 안내 등록 상태를 반환합니다. */
    public CommonPopupDto.AdminResponse getAdminApprovalAuthorityNotice() {
        return getAdminDocument(APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER);
    }

    private CommonPopupDto.AdminResponse getAdminDocument(String identifier) {
        return findActiveDocument(identifier)
                .map(document -> toAdminResponse(document, document.getNacTxtInf()))
                .orElseGet(() -> new CommonPopupDto.AdminResponse(null, null, null));
    }

    /** 팝업 본문을 최초 생성하거나 현재 활성 문서에 저장합니다. */
    @Transactional
    public CommonPopupDto.AdminResponse save(String contentHtml) {
        return saveDocument(DOCUMENT_IDENTIFIER, contentHtml, "공통 안내 팝업");
    }

    /** 전결권 안내 본문을 최초 생성하거나 현재 활성 문서에 저장합니다. */
    @Transactional
    public CommonPopupDto.AdminResponse saveApprovalAuthorityNotice(String contentHtml) {
        return saveDocument(APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER, contentHtml, "전결권 안내");
    }

    private CommonPopupDto.AdminResponse saveDocument(
            String identifier, String contentHtml, String documentName) {
        String sanitizedContent = HtmlSanitizer.sanitize(contentHtml);
        if (!hasMeaningfulContent(sanitizedContent)) {
            throw new IllegalArgumentException(documentName + " 본문은 비어 있을 수 없습니다.");
        }

        return findActiveDocument(identifier)
                .map(document -> update(document, identifier, sanitizedContent))
                .orElseGet(() -> createOrUpdateAfterConcurrentSave(identifier, sanitizedContent));
    }

    /** 현재 팝업이 있으면 논리 삭제하여 게시를 중지합니다. */
    @Transactional
    public void stopPublishing() {
        findActiveDocument(DOCUMENT_IDENTIFIER)
                .ifPresent(
                        document -> {
                            document.delete();
                            guideDocRepository.saveAndFlush(document);
                        });
    }

    private Optional<Bgdocm> findActiveDocument(String identifier) {
        return guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                identifier, DOCUMENT_NUMBER_PREFIX, "N");
    }

    private CommonPopupDto.AdminResponse update(
            Bgdocm document, String identifier, String sanitizedContent) {
        document.update(identifier, sanitizedContent);
        Bgdocm saved = guideDocRepository.saveAndFlush(document);
        return toAdminResponse(saved, sanitizedContent);
    }

    private CommonPopupDto.AdminResponse createOrUpdateAfterConcurrentSave(
            String identifier, String sanitizedContent) {
        try {
            Bgdocm created = creationService.createPopup(identifier, sanitizedContent);
            return toAdminResponse(created, sanitizedContent);
        } catch (DataIntegrityViolationException conflict) {
            return findActiveDocument(identifier)
                    .map(document -> update(document, identifier, sanitizedContent))
                    .orElseThrow(() -> conflict);
        }
    }

    private CommonPopupDto.Response toResponse(Bgdocm document) {
        return new CommonPopupDto.Response(
                document.getDocMngNo(), document.getNacTxtInf(), contentVersion(document));
    }

    private CommonPopupDto.AdminResponse toAdminResponse(Bgdocm document, String contentHtml) {
        return new CommonPopupDto.AdminResponse(
                document.getDocMngNo(), contentHtml, contentVersion(document));
    }

    private String contentVersion(Bgdocm document) {
        return document.getDocMngNo() + ":" + document.getLstChgDtm();
    }

    /** 정화 뒤 텍스트 또는 지원 블록 요소가 남아 있는지 판정합니다. */
    private boolean hasMeaningfulContent(String html) {
        return html != null
                && (html.replaceAll("<[^>]*>", "")
                                        .replace("&nbsp;", "")
                                        .replaceAll("[\\s\\u200B-\\u200D]", "")
                                        .length()
                                > 0
                        || html.matches("(?is).*<(img|table|hr|ul|ol|blockquote)\\b.*"));
    }
}
