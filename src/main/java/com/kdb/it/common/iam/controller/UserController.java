package com.kdb.it.common.iam.controller;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.service.UserService;
import com.kdb.it.common.system.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 REST 컨트롤러
 *
 * <p>사용자(TPRMPP_CUSERI 테이블) 정보를 조회합니다.
 *
 * <p>기본 URL: {@code /api/users}
 *
 * <p>조회 기능:
 *
 * <ul>
 *   <li>조직 코드(BBR_C)별 사용자 목록 조회
 *   <li>사번(ENO)으로 사용자 상세 정보 조회
 * </ul>
 *
 * <p>보안: JWT 토큰 인증 필요
 */
@RestController // REST API 컨트롤러로 등록
@RequestMapping("/api/users") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "User", description = "사용자 관리 API") // Swagger UI 그룹 태그
public class UserController {

    /** 사용자 비즈니스 로직 서비스 */
    private final UserService userService;

    /**
     * 조직별 사용자 목록 조회
     *
     * <p>특정 조직 코드(BBR_C)에 해당하는 사용자 목록을 반환합니다. EntityGraph를 사용하여 조직 정보(CorgnI)를 즉시 로딩합니다.
     *
     * <p>반환 데이터: 사번, 부점명, 팀명, 사용자명, 직위명
     *
     * <p>{@code enoPrefix}를 주면 해당 접두사로 시작하는 행번만 반환합니다. (예: {@code K} → K로 시작하는 행번만)
     *
     * @param orgCode 조직 코드 (BBR_C 컬럼 값, 예: {@code "001"})
     * @param enoPrefix 행번(ENO) 접두사 필터 (선택, 미입력 시 전체)
     * @return HTTP 200 + 해당 조직의 사용자 목록 ({@link UserDto.ListResponse} 리스트)
     */
    @GetMapping
    @Operation(
            summary = "조직별 사용자 조회",
            description = "특정 조직코드에 해당하는 사용자 목록을 조회합니다. enoPrefix를 주면 해당 접두사 행번만 반환합니다.")
    public ResponseEntity<List<UserDto.ListResponse>> getUsersByOrganization(
            @RequestParam("orgCode") String orgCode,
            @RequestParam(value = "enoPrefix", required = false) String enoPrefix) {
        return ResponseEntity.ok(userService.getUsersByOrganization(orgCode, enoPrefix));
    }

    /**
     * 사용자 상세 정보 단건 조회
     *
     * <p>사번(ENO)으로 사용자의 상세 정보를 조회합니다. EntityGraph를 사용하여 조직 정보(CorgnI)를 즉시 로딩합니다.
     *
     * <p>반환 데이터: 사번, 부점명, 팀명, 사용자명, 직위명, 내선번호, 휴대폰번호, 상세직무내용
     *
     * <p>직원 정보 다이얼로그를 위해 인증된 사용자가 조회할 수 있습니다.
     *
     * @param eno 사번(행번, ENO 컬럼 값)
     * @param currentUser 현재 인증 사용자
     * @return HTTP 200 + 사용자 상세 정보 ({@link UserDto.DetailResponse})
     */
    @GetMapping("/{eno}")
    @Operation(summary = "사용자 상세 조회", description = "인증된 사용자가 행번으로 직원 상세 정보를 조회합니다.")
    public ResponseEntity<UserDto.DetailResponse> getUser(
            @PathVariable("eno") String eno,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(userService.getUser(eno, currentUser));
    }

    /**
     * 사용자 검색 (이름·사번·직위명·팀명)
     *
     * <p>전체 조직을 대상으로 사용자명(USR_NM)·팀명(TEM_NM)·사번(ENO)에 검색어를 포함하는 사용자 목록을 반환합니다. 부서코드(orgCode)를 추가로
     * 전달하면 해당 부서 소속만 필터링합니다.
     *
     * <p>검색어는 2자 이상이어야 하며 결과 건수는 서버에서 제한합니다.
     *
     * <p>{@code enoPrefix}를 주면 해당 접두사로 시작하는 행번만 반환합니다. (예: {@code K} → K로 시작하는 행번만)
     *
     * @param keyword 검색어 (이름·사번·직위명·팀명 부분 일치)
     * @param orgCode 부서코드 (선택, 미입력 시 전체 조직 대상)
     * @param enoPrefix 행번(ENO) 접두사 필터 (선택, 미입력 시 전체)
     * @return HTTP 200 + 검색 결과 사용자 목록
     */
    @GetMapping("/search")
    @Operation(
            summary = "사용자 검색 (이름·사번·직위·팀명)",
            description =
                    "전체 조직에서 이름·사번·직위명·팀명 부분 일치로 검색합니다(2자 이상, 결과 건수 제한). "
                            + "keyword 비어있고 orgCode 지정 시 해당 부서 사용자 전체 반환. 둘 다 비어있으면 빈 리스트. "
                            + "enoPrefix를 주면 해당 접두사 행번만 반환합니다.")
    public ResponseEntity<List<UserDto.ListResponse>> searchUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "enoPrefix", required = false) String enoPrefix) {
        return ResponseEntity.ok(userService.searchUsers(keyword, orgCode, enoPrefix));
    }
}
