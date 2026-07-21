package com.kdb.it.common.iam.controller;

import com.kdb.it.common.iam.dto.OrganizationDto;
import com.kdb.it.common.iam.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직(부점) 관리 REST 컨트롤러
 *
 * <p>조직 정보(TPRMPP_CORGNI 테이블)를 조회합니다. 부점 코드, 상위 조직 코드, 부점명을 반환합니다.
 *
 * <p>기본 URL: {@code /api/organizations}
 *
 * <p>조직 구조: 계층형(트리) 구조로, {@code PRLM_HRK_OGZ_C_CONE}(상위 조직 코드)를 통해 부모-자식 관계를 표현합니다.
 *
 * <p>보안: JWT 토큰 인증 필요
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/organizations") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "Organization", description = "조직 관리 API") // Swagger UI 그룹 태그
public class OrganizationController {

    /** 조직 비즈니스 로직 서비스 */
    private final OrganizationService organizationService;

    /**
     * 전체 조직 목록 조회
     *
     * <p>DB에 등록된 모든 조직(부점) 정보를 반환합니다. 조직 코드, 상위 조직 코드, 부점명을 포함합니다.
     *
     * <p>활용 예:
     *
     * <ul>
     *   <li>사용자 등록/수정 시 조직 선택 드롭다운
     *   <li>조직별 사용자 조회 시 조직 코드 참조
     * </ul>
     *
     * @return HTTP 200 + 전체 조직 목록 ({@link OrganizationDto.Response} 리스트)
     */
    @GetMapping
    @Operation(summary = "전체 조직 조회", description = "모든 조직 정보를 조회합니다.")
    public ResponseEntity<List<OrganizationDto.Response>> getOrganizations() {
        return ResponseEntity.ok(organizationService.getOrganizations());
    }
}
