package com.kdb.it.domain.budget.document.budgetnote;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 예산작성 카드 참고사항의 공개 조회와 관리자 저장 API입니다. */
@RestController
@RequestMapping("/api/budget/card-notes")
@RequiredArgsConstructor
public class BudgetCardNoteController {

    private final BudgetCardNoteService service;

    /** 등록된 카드 참고사항을 조회합니다. */
    @GetMapping
    public ResponseEntity<List<BudgetCardNoteDto.Response>> getNotes() {
        return ResponseEntity.ok(service.getNotes());
    }

    /** 관리자만 지정 카드의 참고사항을 저장할 수 있습니다. */
    @PutMapping("/{cardType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetCardNoteDto.Response> save(
            @PathVariable("cardType") String cardType,
            @Valid @RequestBody BudgetCardNoteDto.SaveRequest request) {
        return ResponseEntity.ok(service.save(cardType, request));
    }
}
