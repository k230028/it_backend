package com.kdb.it.domain.budget.document.controller;

import com.kdb.it.domain.budget.document.dto.ReviewCommentDto;
import com.kdb.it.domain.budget.document.service.ReviewCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 문서 검토의견 REST 컨트롤러
 *
 * <p>
 * 문서(TAAABB_BRDOCM) 특정 버전에 달린 검토의견(TAAABB_BRIVGM)의
 * 조회·등록·해결처리 엔드포인트를 제공합니다.
 * </p>
 *
 * <p>
 * 기본 URL: {@code /api/documents/{docMngNo}/review-comments}
 * </p>
 *
 * <p>
 * 보안: JWT 토큰 인증 필요
 * </p>
 */
@Tag(name = "검토의견", description = "문서 검토의견 CRUD API")
@RestController
@RequestMapping("/api/documents/{docMngNo}/review-comments")
@RequiredArgsConstructor
public class ReviewCommentController {

    /** 검토의견 비즈니스 로직 서비스 */
    private final ReviewCommentService reviewCommentService;

    /**
     * 특정 문서+버전의 검토의견 목록 조회
     *
     * @param docMngNo 문서관리번호 (예: DOC-2026-0001)
     * @param docVrs   문서버전 (필수)
     * @return HTTP 200 + 검토의견 응답 DTO 목록 (생성일시 오름차순)
     */
    @Operation(summary = "검토의견 목록 조회",
            description = """
                    특정 문서와 문서버전에 등록된 미삭제 검토의견 목록을 조회합니다.

                    - 조회 대상: TAAABB_BRIVGM
                    - 정렬 기준: 생성일시 오름차순
                    - 의견 유형: I(인라인), G(전반)
                    - 화면 용도: 문서 편집/검토 화면의 코멘트 패널
                    """,
            responses = @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(
                            schema = @Schema(implementation = ReviewCommentDto.Response.class),
                            examples = @ExampleObject(
                                    name = "검토의견 목록 응답 예시",
                                    value = """
                                            [
                                              {
                                                "ivgSno": "4f7f1fd8623c48eab6d52a789c10e001",
                                                "docMngNo": "DOC-2026-0001",
                                                "docVrs": 1.00,
                                                "ivgTp": "I",
                                                "ivgCone": "예산 산출 근거를 보완해 주세요.",
                                                "markId": "mark-budget-001",
                                                "qtdCone": "예산 산출 근거",
                                                "rslvYn": "N",
                                                "authorEno": "123456",
                                                "authorName": "홍길동",
                                                "createdAt": "2026-04-28T10:30:00"
                                              }
                                            ]
                                            """))))
    @GetMapping
    public List<ReviewCommentDto.Response> getComments(
            @Parameter(description = "문서관리번호", required = true, example = "DOC-2026-0001")
            @PathVariable("docMngNo") String docMngNo,
            @Parameter(description = "문서버전", required = true, example = "1.00")
            @RequestParam("docVrs") BigDecimal docVrs) {
        return reviewCommentService.getComments(docMngNo, docVrs);
    }

    /**
     * 검토의견 신규 등록
     *
     * @param docMngNo 대상 문서관리번호
     * @param request  검토의견 생성 요청 DTO
     * @return HTTP 201 Created + 저장된 검토의견 응답 DTO
     */
    @Operation(summary = "검토의견 추가",
            description = """
                    특정 문서버전에 검토의견을 신규 등록합니다.

                    - 인라인 의견(I): markId와 qtdCone을 함께 전달해 편집기 하이라이트와 연결합니다.
                    - 전반 의견(G): 문서 전체에 대한 의견이며 markId/qtdCone은 생략할 수 있습니다.
                    - 의견일련번호는 서버에서 UUID v4 기반 32자 문자열로 생성합니다.
                    """,
            responses = @ApiResponse(responseCode = "201", description = "등록 성공",
                    content = @Content(schema = @Schema(implementation = ReviewCommentDto.Response.class))))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewCommentDto.Response addComment(
            @Parameter(description = "문서관리번호", required = true, example = "DOC-2026-0001")
            @PathVariable("docMngNo") String docMngNo,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "검토의견 생성 요청. ivgTp가 I이면 markId/qtdCone을 함께 전달합니다.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ReviewCommentDto.CreateRequest.class),
                            examples = @ExampleObject(
                                    name = "인라인 검토의견 등록 예시",
                                    value = """
                                            {
                                              "docVrs": 1.00,
                                              "ivgTp": "I",
                                              "ivgCone": "예산 산출 근거를 보완해 주세요.",
                                              "markId": "mark-budget-001",
                                              "qtdCone": "예산 산출 근거"
                                            }
                                            """)))
            @Valid @RequestBody ReviewCommentDto.CreateRequest request) {
        return reviewCommentService.addComment(docMngNo, request);
    }

    /**
     * 검토의견 해결 처리
     *
     * <p>
     * 지정한 의견일련번호의 RSLV_YN 값을 'Y'로 변경합니다.
     * </p>
     *
     * @param docMngNo 문서관리번호 (코멘트 소속 검증에 사용)
     * @param ivgSno   의견일련번호 (UUID v4 32자)
     */
    @Operation(summary = "검토의견 해결 처리",
            description = """
                    지정한 의견일련번호의 해결여부를 완료 상태로 변경합니다.

                    - 변경 값: RSLV_YN='Y'
                    - 문서관리번호와 의견일련번호를 함께 검증하여 다른 문서의 의견이 잘못 처리되지 않게 합니다.
                    - 응답 본문 없이 204 No Content를 반환합니다.
                    """,
            responses = @ApiResponse(responseCode = "204", description = "해결 처리 성공"))
    @PatchMapping("/{ivgSno}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveComment(
            @Parameter(description = "문서관리번호", required = true, example = "DOC-2026-0001")
            @PathVariable("docMngNo") String docMngNo,
            @Parameter(description = "의견일련번호(UUID v4 32자)", required = true, example = "4f7f1fd8623c48eab6d52a789c10e001")
            @PathVariable("ivgSno") String ivgSno) {
        reviewCommentService.resolveComment(docMngNo, ivgSno);
    }
}
