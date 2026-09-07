package com.kdb.it.common.speeddial.contact;

import com.kdb.it.common.util.HtmlSanitizer;
import com.kdb.it.domain.budget.document.entity.BgdocDocumentType;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TPRMPP_BGDOCM의 담당자 정보 단일 문서를 조회·저장합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactInfoService {

    /** 담당자 정보 문서를 식별하는 DOC_TTL_CONE 고정값입니다. */
    public static final String DOCUMENT_IDENTIFIER = "SPEED_DIAL_CONTACT_INFO";

    private static final String DOCUMENT_TYPE = BgdocDocumentType.CONTACT_INFO.code();

    private final GuideDocRepository guideDocRepository;
    private final ContactInfoCreationService contactInfoCreationService;

    /**
     * 활성 담당자 정보 문서를 반환합니다.
     *
     * @return 문서가 없으면 관리번호와 본문이 모두 null인 응답
     */
    public ContactInfoDto.Response getContactInfo() {
        return guideDocRepository
                .findByDocTtlConeAndDocDtlItmCAndDelYn(DOCUMENT_IDENTIFIER, DOCUMENT_TYPE, "N")
                .map(
                        document ->
                                new ContactInfoDto.Response(
                                        document.getDocMngNo(), document.getNacTxtInf()))
                .orElseGet(() -> new ContactInfoDto.Response(null, null));
    }

    /**
     * 담당자 정보 본문을 최초 생성하거나 기존 단일 문서를 갱신합니다.
     *
     * @param contentHtml 사용자 입력 HTML 본문
     * @return 저장한 문서의 관리번호와 정화된 본문
     */
    @Transactional
    public ContactInfoDto.Response saveContactInfo(String contentHtml) {
        String sanitizedContent = HtmlSanitizer.sanitize(contentHtml);
        if (!hasMeaningfulContent(sanitizedContent)) {
            throw new IllegalArgumentException("담당자 정보 본문은 비어 있을 수 없습니다.");
        }
        return guideDocRepository
                .findByDocTtlConeAndDocDtlItmCAndDelYn(DOCUMENT_IDENTIFIER, DOCUMENT_TYPE, "N")
                .map(
                        document -> {
                            document.update(DOCUMENT_IDENTIFIER, sanitizedContent);
                            return new ContactInfoDto.Response(
                                    document.getDocMngNo(), sanitizedContent);
                        })
                .orElseGet(() -> createOrUpdateAfterConcurrentFirstSave(sanitizedContent));
    }

    /** 허용되지 않는 태그를 제거한 뒤에도 실제로 표시할 안내 내용이 있는지 확인합니다. */
    private boolean hasMeaningfulContent(String html) {
        return html != null
                && (html.replaceAll("<[^>]*>", "")
                                        .replace("&nbsp;", "")
                                        .replaceAll("[\\s\\u200B-\\u200D]", "")
                                        .length()
                                > 0
                        || html.matches("(?is).*<(img|table|hr|ul|ol|blockquote)\\b.*"));
    }

    private ContactInfoDto.Response createOrUpdateAfterConcurrentFirstSave(
            String sanitizedContent) {
        try {
            return contactInfoCreationService.createContactInfo(sanitizedContent);
        } catch (DataIntegrityViolationException conflict) {
            return guideDocRepository
                    .findByDocTtlConeAndDocDtlItmCAndDelYn(DOCUMENT_IDENTIFIER, DOCUMENT_TYPE, "N")
                    .map(
                            document -> {
                                document.update(DOCUMENT_IDENTIFIER, sanitizedContent);
                                return new ContactInfoDto.Response(
                                        document.getDocMngNo(), sanitizedContent);
                            })
                    .orElseThrow(() -> conflict);
        }
    }
}
