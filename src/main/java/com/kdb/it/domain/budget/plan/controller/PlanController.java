package com.kdb.it.domain.budget.plan.controller;

import com.kdb.it.domain.budget.plan.dto.PlanDto;
import com.kdb.it.domain.budget.plan.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 정보기술부문 계획 컨트롤러
 *
 * <p>
 * 정보기술부문 계획(TAAABB_BPLANM) 등록, 조회, 삭제 API를 제공합니다.
 * </p>
 *
 * <p>
 * 엔드포인트 목록:
 * </p>
 * <ul>
 * <li>GET /api/plans - 전체 계획 목록 조회</li>
 * <li>GET /api/plans/{id} - 단건 계획 상세 조회</li>
 * <li>POST /api/plans - 계획 등록</li>
 * <li>DELETE /api/plans/{id} - 계획 논리 삭제</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
@Tag(name = "정보기술부문 계획", description = "IT 부문 연도별 계획 관리 API")
public class PlanController {

        private final PlanService planService;

        /**
         * 전체 계획 목록 조회
         *
         * <p>
         * 삭제되지 않은 전체 계획을 등록일시 내림차순으로 반환합니다.
         * </p>
         *
         * @return 계획 목록 (200 OK)
         */
        @GetMapping
        @Operation(summary = "계획 목록 조회", description = """
                        삭제되지 않은 IT 부문 연도별 계획 목록을 등록일시 내림차순으로 조회합니다.

                        - 조회 대상: TAAABB_BPLANM
                        - 포함 정보: 계획관리번호, 계획구분, 대상년도, 총예산, 자본예산, 일반관리비
                        - 화면 용도: 계획 관리 목록 화면
                        """, responses = @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = PlanDto.ListResponse.class), examples = @ExampleObject(name = "계획 목록 응답 예시", value = """
                        [
                          {
                            "plnMngNo": "PLN-2026-0001",
                            "plnTp": "신규",
                            "plnYy": "2026",
                            "ttlBg": 1500000000,
                            "cptBg": 1000000000,
                            "mngc": 500000000
                          }
                        ]
                        """))))
        public ResponseEntity<List<PlanDto.ListResponse>> getPlans() {
                return ResponseEntity.ok(planService.getPlans());
        }

        /**
         * 단건 계획 상세 조회
         *
         * <p>
         * 계획관리번호로 단건 계획과 JSON 스냅샷, 연결된 프로젝트 목록을 조회합니다.
         * </p>
         *
         * @param plnMngNo 계획관리번호 (예: PLN-2026-0001)
         * @return 계획 상세 정보 (200 OK) 또는 404 Not Found
         */
        @GetMapping("/{plnMngNo}")
        @Operation(summary = "계획 상세 조회", description = """
                        계획관리번호로 단건 계획 상세와 저장 시점의 JSON 스냅샷을 조회합니다.

                        - 조회 대상: TAAABB_BPLANM, TAAABB_BPROJA
                        - 스냅샷: 계획 생성 당시 프로젝트 목록과 예산 집계 정보를 JSON 문자열로 보관합니다.
                        - 존재하지 않는 계획관리번호는 서비스 계층에서 404로 처리됩니다.
                        """, responses = @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = PlanDto.DetailResponse.class))))
        public ResponseEntity<PlanDto.DetailResponse> getPlan(
                        @Parameter(description = "계획관리번호", required = true, example = "PLN-2026-0001") @PathVariable("plnMngNo") String plnMngNo) {
                return ResponseEntity.ok(planService.getPlan(plnMngNo));
        }

        /**
         * 계획 등록
         *
         * <p>
         * 대상년도, 계획구분, 대상 프로젝트 목록을 받아 계획을 등록합니다.
         * 대상 프로젝트의 예산 합계와 JSON 스냅샷을 자동으로 생성하여 저장합니다.
         * </p>
         *
         * @param request    계획 생성 요청 DTO
         * @param uriBuilder URI 빌더 (Location 헤더 생성용)
         * @return 201 Created (Location: /api/plans/{plnMngNo})
         */
        @PostMapping
        @Operation(summary = "계획 등록", description = """
                        대상년도, 계획구분, 대상 프로젝트/전산업무비 목록을 받아 IT 부문 계획을 등록합니다.

                        - 계획관리번호는 서버에서 자동 채번합니다.
                        - 대상 프로젝트와 전산업무비의 예산을 집계하여 총예산/자본예산/일반관리비를 산출합니다.
                        - 생성된 리소스 경로는 Location 헤더(/api/plans/{plnMngNo})로 반환합니다.
                        """, responses = @ApiResponse(responseCode = "201", description = "등록 성공"))
        public ResponseEntity<Void> createPlan(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "계획 생성 요청. 대상년도, 계획구분, 프로젝트/전산업무비 관리번호 목록을 전달합니다.", required = true, content = @Content(schema = @Schema(implementation = PlanDto.CreateRequest.class), examples = @ExampleObject(name = "계획 등록 요청 예시", value = """
                                        {
                                          "plnYy": "2026",
                                          "plnTp": "신규",
                                          "prjMngNos": [
                                            "PRJ-2026-0001",
                                            "PRJ-2026-0002"
                                          ],
                                          "itMngcNos": [
                                            "COST-2026-0001"
                                          ]
                                        }
                                        """))) @RequestBody PlanDto.CreateRequest request,
                        UriComponentsBuilder uriBuilder) {
                String plnMngNo = planService.createPlan(request);
                URI location = uriBuilder.path("/api/plans/{plnMngNo}").buildAndExpand(plnMngNo).toUri();
                return ResponseEntity.created(location).build();
        }

        /**
         * 계획 논리 삭제
         *
         * <p>
         * 계획 엔티티의 DEL_YN을 'Y'로 변경합니다.
         * 연결된 정보화사업 관계(BPROJA) 레코드도 함께 논리 삭제됩니다.
         * </p>
         *
         * @param plnMngNo 계획관리번호
         * @return 204 No Content 또는 404 Not Found
         */
        @DeleteMapping("/{plnMngNo}")
        @Operation(summary = "계획 삭제", description = """
                        계획과 연결 정보화사업 관계를 논리 삭제합니다.

                        - 삭제 방식: DEL_YN='Y'로 변경
                        - 대상 테이블: TAAABB_BPLANM, TAAABB_BPROJA
                        - 실제 레코드는 제거하지 않아 이력 추적이 가능합니다.
                        """, responses = @ApiResponse(responseCode = "204", description = "삭제 성공"))
        public ResponseEntity<Void> deletePlan(
                        @Parameter(description = "계획관리번호", required = true, example = "PLN-2026-0001") @PathVariable("plnMngNo") String plnMngNo) {
                planService.deletePlan(plnMngNo);
                return ResponseEntity.noContent().build();
        }
}
