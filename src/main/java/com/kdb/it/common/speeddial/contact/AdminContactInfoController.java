package com.kdb.it.common.speeddial.contact;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 전용 담당자 정보 작성 API입니다. */
@RestController
@RequestMapping("/api/admin/contact-information")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Contact Information", description = "담당자 정보 작성 API")
public class AdminContactInfoController {

    private final ContactInfoService contactInfoService;

    /**
     * 현재 담당자 정보 문서를 조회합니다.
     *
     * @return 등록 전이면 본문이 null인 응답
     */
    @GetMapping
    @Operation(summary = "담당자 정보 조회")
    public ResponseEntity<ContactInfoDto.Response> getContactInfo() {
        return ResponseEntity.ok(contactInfoService.getContactInfo());
    }

    /**
     * 담당자 정보 문서를 최초 등록하거나 갱신합니다.
     *
     * @param request HTML 본문
     * @return 저장한 GDOC 문서관리번호와 정화된 본문
     */
    @PutMapping
    @Operation(summary = "담당자 정보 저장")
    public ResponseEntity<ContactInfoDto.Response> saveContactInfo(
            @Valid @RequestBody ContactInfoDto.SaveRequest request) {
        return ResponseEntity.ok(contactInfoService.saveContactInfo(request.contentHtml()));
    }
}
