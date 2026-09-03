package com.kdb.it.common.speeddial.controller;

import com.kdb.it.common.speeddial.contact.ContactInfoDto;
import com.kdb.it.common.speeddial.contact.ContactInfoService;
import com.kdb.it.common.speeddial.dto.SpeedDialDto;
import com.kdb.it.common.speeddial.service.SpeedDialService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 전역 스피드다이얼의 FAQ·Q&amp;A·담당자 정보 API입니다. */
@RestController
@RequestMapping("/api/speed-dial")
@RequiredArgsConstructor
@Tag(name = "Speed Dial", description = "스피드다이얼 FAQ·Q&A API")
public class SpeedDialController {

    private final SpeedDialService speedDialService;
    private final ContactInfoService contactInfoService;

    /**
     * 스피드다이얼에 표시할 담당자 정보 문서를 조회합니다.
     *
     * @return 등록 전이면 본문이 null인 응답
     */
    @GetMapping("/contact-information")
    @Operation(summary = "스피드다이얼 담당자 정보 조회")
    public ResponseEntity<ContactInfoDto.Response> getContactInfo() {
        return ResponseEntity.ok(contactInfoService.getContactInfo());
    }

    @GetMapping("/faqs")
    @Operation(summary = "스피드다이얼 FAQ 조회")
    public ResponseEntity<List<SpeedDialDto.FaqResponse>> getFaqs() {
        return ResponseEntity.ok(speedDialService.getFaqs());
    }

    @PostMapping("/qna")
    @Operation(summary = "스피드다이얼 문의 등록")
    public ResponseEntity<SpeedDialDto.QnaCreateResponse> createQna(
            @Valid @RequestBody SpeedDialDto.QnaCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        String postId = speedDialService.createQna(request, user);
        return ResponseEntity.ok(new SpeedDialDto.QnaCreateResponse(postId));
    }
}
