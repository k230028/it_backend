package com.kdb.it.common.popup;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.domain.budget.document.service.BgdocNumberAllocator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 공통 안내 팝업 최초 생성을 별도 트랜잭션에서 처리합니다. */
@Service
@RequiredArgsConstructor
class CommonPopupCreationService {

    private static final String DOCUMENT_NUMBER_PREFIX = "PDOC-";

    private final GuideDocRepository guideDocRepository;
    private final BgdocNumberAllocator bgdocNumberAllocator;

    /** 동시 최초 저장의 유일 제약 실패가 호출자 트랜잭션을 오염시키지 않도록 독립 실행합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Bgdocm createPopup(String sanitizedContent) {
        return createPopup(CommonPopupService.DOCUMENT_IDENTIFIER, sanitizedContent);
    }

    /** 지정된 PDOC 문서를 독립 트랜잭션에서 최초 생성합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Bgdocm createPopup(String identifier, String sanitizedContent) {
        return guideDocRepository
                .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                        identifier, DOCUMENT_NUMBER_PREFIX, "N")
                .map(
                        document -> {
                            document.update(identifier, sanitizedContent);
                            return guideDocRepository.saveAndFlush(document);
                        })
                .orElseGet(() -> createDocument(identifier, sanitizedContent));
    }

    private Bgdocm createDocument(String identifier, String sanitizedContent) {
        String documentNumber = bgdocNumberAllocator.next(DOCUMENT_NUMBER_PREFIX);
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo(documentNumber)
                        .docTtlCone(identifier)
                        .nacTxtInf(sanitizedContent)
                        .build();
        return guideDocRepository.saveAndFlush(document);
    }
}
