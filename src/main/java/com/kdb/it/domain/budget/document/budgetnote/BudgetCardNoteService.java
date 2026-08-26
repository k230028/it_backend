package com.kdb.it.domain.budget.document.budgetnote;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** TPRMPP_BGDOCM을 이용해 예산작성 카드 참고사항을 조회하고 저장합니다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetCardNoteService {

    private static final String DOCUMENT_PREFIX = "BNOTE-";
    private static final String ACTIVE = "N";

    private final GuideDocRepository guideDocRepository;

    /** 등록된 카드 참고사항을 화면 순서대로 반환합니다. */
    public List<BudgetCardNoteDto.Response> getNotes() {
        Map<String, Bgdocm> documents =
                guideDocRepository.findAllByDocMngNoStartingWithAndDelYn(DOCUMENT_PREFIX, ACTIVE)
                        .stream()
                        .collect(Collectors.toMap(Bgdocm::getDocTtlCone, Function.identity()));
        return Arrays.stream(BudgetCardNoteType.values())
                .filter(type -> documents.containsKey(type.documentTitle()))
                .map(
                        type ->
                                new BudgetCardNoteDto.Response(
                                        type.name(),
                                        documents.get(type.documentTitle()).getNacTxtInf()))
                .toList();
    }

    /** 관리자가 카드 참고사항을 신규 등록하거나 수정합니다. */
    @Transactional
    public BudgetCardNoteDto.Response save(
            String cardType, BudgetCardNoteDto.SaveRequest request) {
        BudgetCardNoteType type = BudgetCardNoteType.require(cardType);
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("참고사항을 입력해야 합니다");
        }
        String content = request.content().trim();
        Bgdocm document =
                guideDocRepository
                        .findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                type.documentTitle(), DOCUMENT_PREFIX, ACTIVE)
                        .map(
                                existing -> {
                                    existing.update(type.documentTitle(), content);
                                    return existing;
                                })
                        .orElseGet(() -> create(type, content));
        return new BudgetCardNoteDto.Response(type.name(), document.getNacTxtInf());
    }

    private Bgdocm create(BudgetCardNoteType type, String content) {
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo(type.docMngNo())
                        .docTtlCone(type.documentTitle())
                        .nacTxtInf(content)
                        .build();
        return guideDocRepository.save(document);
    }
}
