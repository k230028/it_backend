package com.kdb.it.common.speeddial.contact;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 담당자 정보 최초 생성만 별도 트랜잭션에서 처리합니다. */
@Service
@RequiredArgsConstructor
class ContactInfoCreationService {

    private static final String DOCUMENT_NUMBER_PREFIX = "GDOC-";

    private final GuideDocRepository guideDocRepository;

    /** 동시 최초 등록 시 고유 제약 위반을 호출자 트랜잭션까지 전파하지 않도록 독립 트랜잭션으로 생성합니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ContactInfoDto.Response createContactInfo(String sanitizedContent) {
        return guideDocRepository
                .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                        ContactInfoService.DOCUMENT_IDENTIFIER, DOCUMENT_NUMBER_PREFIX, "N")
                .map(
                        document -> {
                            document.update(
                                    ContactInfoService.DOCUMENT_IDENTIFIER, sanitizedContent);
                            return new ContactInfoDto.Response(
                                    document.getDocMngNo(), sanitizedContent);
                        })
                .orElseGet(
                        () -> {
                            long nextValue = guideDocRepository.getNextSequenceValue();
                            String documentNumber =
                                    "%s%s-%04d"
                                            .formatted(
                                                    DOCUMENT_NUMBER_PREFIX,
                                                    LocalDate.now().getYear(),
                                                    nextValue);
                            Bgdocm document =
                                    Bgdocm.builder()
                                            .docMngNo(documentNumber)
                                            .docTtlCone(ContactInfoService.DOCUMENT_IDENTIFIER)
                                            .nacTxtInf(sanitizedContent)
                                            .build();
                            guideDocRepository.saveAndFlush(document);
                            return new ContactInfoDto.Response(documentNumber, sanitizedContent);
                        });
    }
}
