package com.kdb.it.common.popup;

import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.document.entity.BgdocDocumentType;
import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TPRMPP_BGDOCM의 안내 팝업 문서를 조회·저장·게시 중지합니다.
 *
 * <p>대상은 {@link CommonPopupType}이 허용하는 공통·화면별 안내 4종과 사업 전결권 안내({@link
 * #APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER})이며, 유형마다 {@code DOC_TTL_CONE} 고정 식별자로 활성 1건을 유지합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonPopupService {

    /** 공통 안내 팝업을 식별하는 DOC_TTL_CONE 고정값입니다. */
    public static final String DOCUMENT_IDENTIFIER = "common.popup";

    /** 사업 작성·조회 화면의 전결권 안내를 식별하는 DOC_TTL_CONE 고정값입니다. */
    public static final String APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER =
            "project.approval-authority";

    /**
     * 전결권 안내 신규 문서의 DOC_MNG_NO 접두사입니다.
     *
     * <p>공통 안내 팝업({@link CommonPopupType#POPUP}, {@code PDOC-})과 접두사를 공유하면 접두사만으로 필터링하는 다른 기능의 목록에
     * 이 문서가 섞이므로 전용 접두사를 씁니다. 이 상수 도입 전에 만들어진 전결권 안내 행은 {@code PDOC-} 관리번호를 그대로 유지합니다 — {@code
     * DOC_MNG_NO}는 PK이며 BGDOCL·CFILEM이 참조하므로 개명하지 않습니다. 조회는 {@code DOC_TTL_CONE}과 {@code
     * DOC_DTL_ITM_C} 조합만 사용하고 접두사에 의존하지 않으므로 기존 행도 동일하게 활성 문서로 찾습니다.
     */
    public static final String APPROVAL_AUTHORITY_DOCUMENT_NUMBER_PREFIX = "APOP-";

    private static final String DOCUMENT_TYPE = BgdocDocumentType.NOTICE_POPUP.code();

    private final GuideDocRepository guideDocRepository;
    private final CommonPopupCreationService creationService;

    /** 인증 사용자에게 게시 중인 팝업을 반환합니다. */
    public Optional<CommonPopupDto.Response> getActivePopup() {
        return getActivePopup(CommonPopupType.POPUP);
    }

    /** 인증 사용자에게 지정된 유형의 게시 중인 팝업을 반환합니다. */
    public Optional<CommonPopupDto.Response> getActivePopup(CommonPopupType type) {
        return findActiveDocument(type.documentIdentifier()).map(this::toResponse);
    }

    /** 인증 사용자에게 게시 중인 전결권 안내를 반환합니다. */
    public Optional<CommonPopupDto.Response> getApprovalAuthorityNotice() {
        return findActiveDocument(APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER).map(this::toResponse);
    }

    /** 관리자에게 현재 등록 상태를 반환합니다. */
    public CommonPopupDto.AdminResponse getAdminPopup() {
        return getAdminPopup(CommonPopupType.POPUP);
    }

    /** 관리자에게 지정된 유형의 현재 등록 상태를 반환합니다. */
    public CommonPopupDto.AdminResponse getAdminPopup(CommonPopupType type) {
        return getAdminDocument(type.documentIdentifier());
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
        return save(CommonPopupType.POPUP, contentHtml);
    }

    /** 지정된 유형의 팝업 본문을 최초 생성하거나 현재 활성 문서에 저장합니다. */
    @Transactional
    public CommonPopupDto.AdminResponse save(CommonPopupType type, String contentHtml) {
        return saveDocument(
                type.documentIdentifier(),
                type.documentNumberPrefix(),
                contentHtml,
                type.documentName());
    }

    /**
     * 전결권 안내 본문을 최초 생성하거나 현재 활성 문서에 저장합니다.
     *
     * <p>최초 생성 시 관리번호는 {@link #APPROVAL_AUTHORITY_DOCUMENT_NUMBER_PREFIX}로 채번합니다. 기존 {@code PDOC-}
     * 행이 활성이면 그 행을 갱신하며 새로 채번하지 않습니다.
     */
    @Transactional
    public CommonPopupDto.AdminResponse saveApprovalAuthorityNotice(String contentHtml) {
        return saveDocument(
                APPROVAL_AUTHORITY_DOCUMENT_IDENTIFIER,
                APPROVAL_AUTHORITY_DOCUMENT_NUMBER_PREFIX,
                contentHtml,
                "전결권 안내");
    }

    private CommonPopupDto.AdminResponse saveDocument(
            String identifier,
            String documentNumberPrefix,
            String contentHtml,
            String documentName) {
        String sanitizedContent = HtmlSanitizer.sanitize(contentHtml);
        if (!hasMeaningfulContent(sanitizedContent)) {
            throw new IllegalArgumentException(documentName + " 본문은 비어 있을 수 없습니다.");
        }

        return findActiveDocument(identifier)
                .map(document -> update(document, identifier, sanitizedContent))
                .orElseGet(
                        () ->
                                createOrUpdateAfterConcurrentSave(
                                        identifier, documentNumberPrefix, sanitizedContent));
    }

    /** 현재 팝업이 있으면 논리 삭제하여 게시를 중지합니다. */
    @Transactional
    public void stopPublishing() {
        stopPublishing(CommonPopupType.POPUP);
    }

    /** 지정된 유형의 현재 팝업이 있으면 논리 삭제하여 게시를 중지합니다. */
    @Transactional
    public void stopPublishing(CommonPopupType type) {
        findActiveDocument(type.documentIdentifier())
                .ifPresent(
                        document -> {
                            document.delete();
                            guideDocRepository.saveAndFlush(document);
                        });
    }

    private Optional<Bgdocm> findActiveDocument(String identifier) {
        return guideDocRepository.findByDocTtlConeAndDocDtlItmCAndDelYn(
                identifier, DOCUMENT_TYPE, "N");
    }

    private CommonPopupDto.AdminResponse update(
            Bgdocm document, String identifier, String sanitizedContent) {
        document.update(identifier, sanitizedContent);
        Bgdocm saved = guideDocRepository.saveAndFlush(document);
        return toAdminResponse(saved, sanitizedContent);
    }

    private CommonPopupDto.AdminResponse createOrUpdateAfterConcurrentSave(
            String identifier, String documentNumberPrefix, String sanitizedContent) {
        try {
            Bgdocm created =
                    creationService.createPopup(identifier, documentNumberPrefix, sanitizedContent);
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
