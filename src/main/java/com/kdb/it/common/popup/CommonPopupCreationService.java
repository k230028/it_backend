package com.kdb.it.common.popup;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 공통 안내 팝업 최초 생성을 별도 트랜잭션에서 처리합니다. */
@Service
@RequiredArgsConstructor
class CommonPopupCreationService {

    private static final String DOCUMENT_NUMBER_PREFIX = "GDOC-";

    private final GuideDocRepository guideDocRepository;

    /** 동시 최초 저장의 유일 제약 실패가 호출자 트랜잭션을 오염시키지 않도록 독립 실행합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Bgdocm createPopup(String sanitizedContent) {
        return guideDocRepository
                .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                        CommonPopupService.DOCUMENT_IDENTIFIER, DOCUMENT_NUMBER_PREFIX, "N")
                .map(
                        document -> {
                            document.update(
                                    CommonPopupService.DOCUMENT_IDENTIFIER, sanitizedContent);
                            return guideDocRepository.saveAndFlush(document);
                        })
                .orElseGet(() -> createDocument(sanitizedContent));
    }

    private Bgdocm createDocument(String sanitizedContent) {
        long nextValue = guideDocRepository.getNextSequenceValue();
        String documentNumber =
                "%s%s-%04d".formatted(DOCUMENT_NUMBER_PREFIX, LocalDate.now().getYear(), nextValue);
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo(documentNumber)
                        .docTtlCone(CommonPopupService.DOCUMENT_IDENTIFIER)
                        .nacTxtInf(sanitizedContent)
                        .build();
        return guideDocRepository.saveAndFlush(document);
    }
}
