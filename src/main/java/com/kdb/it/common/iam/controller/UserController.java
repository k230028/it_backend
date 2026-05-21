package com.kdb.it.common.iam.controller;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사용자 관리 REST 컨트롤러
 *
 * <p>사용자(TPRMPP_CUSERI 테이블) 정보를 조회합니다.</p>
 *
 * <p>기본 URL: {@code /api/users}</p>
 *
 * <p>조회 기능:</p>
 * <ul>
 *   <li>조직 코드(BBR_C)별 사용자 목록 조회</li>
 *   <li>사번(ENO)으로 사용자 상세 정보 조회</li>
 * </ul>
 *
 * <p>보안: JWT 토큰 인증 필요</p>
 */
@RestController                        // REST API 컨트롤러로 등록
@RequestMapping("/api/users")          // 기본 URL 경로 설정
@RequiredArgsConstructor               // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "User", description = "사용자 관리 API") // Swagger UI 그룹 태그
public class UserController {

    /** 사용자 비즈니스 로직 서비스 */
    private final UserService userService;

    /**
     * 조직별 사용자 목록 조회
     *
     * <p>특정 조직 코드(BBR_C)에 해당하는 사용자 목록을 반환합니다.
     * EntityGraph를 사용하여 조직 정보(CorgnI)를 즉시 로딩합니다.</p>
     *
     * <p>반환 데이터: 사번, 부점명, 팀명, 사용자명, 직위명</p>
     *
     * @param orgCode 조직 코드 (BBR_C 컬럼 값, 예: {@code "001"})
     * @return HTTP 200 + 해당 조직의 사용자 목록 ({@link UserDto.ListResponse} 리스트)
     */
    @GetMapping
    @Operation(summary = "조직별 사용자 조회", description = "특정 조직코드에 해당하는 사용자 목록을 조회합니다.")
    public ResponseEntity<List<UserDto.ListResponse>> getUsersByOrganization(
            @RequestParam("orgCode") String orgCode) {
        return ResponseEntity.ok(userService.getUsersByOrganization(orgCode));
    }

    /**
     * 사용자 상세 정보 단건 조회
     *
     * <p>사번(ENO)으로 사용자의 상세 정보를 조회합니다.
     * EntityGraph를 사용하여 조직 정보(CorgnI)를 즉시 로딩합니다.</p>
     *
     * <p>반환 데이터: 사번, 부점명, 팀명, 사용자명, 직위명, 내선번호, 휴대폰번호, 상세직무내용</p>
     *
     * @param eno 사번(행번, ENO 컬럼 값)
     * @return HTTP 200 + 사용자 상세 정보 ({@link UserDto.DetailResponse})
     */
    @GetMapping("/{eno}")
    @Operation(summary = "사용자 상세 조회", description = "행번으로 사용자 상세 정보를 조회합니다.")
    public ResponseEntity<UserDto.DetailResponse> getUser(@PathVariable("eno") String eno) {
        return ResponseEntity.ok(userService.getUser(eno));
    }

    /**
     * 사용자 이름 검색 (자동완성용)
     *
     * <p>사용자명(USR_NM)에 검색어를 포함하는 사용자 목록을 반환합니다.
     * 부서코드(orgCode)를 추가로 전달하면 해당 부서 소속만 필터링합니다.</p>
     *
     * @param keyword 검색할 사용자명 (부분 일치)
     * @param orgCode 부서코드 (선택, 미입력 시 전체 부서 대상)
     * @return HTTP 200 + 검색 결과 사용자 목록
     */
    @GetMapping("/search")
    @Operation(summary = "사용자 이름 검색",
            description = "사용자명으로 검색합니다. keyword 비어있고 orgCode 지정 시 해당 부서 사용자 전체 반환. 둘 다 비어있으면 빈 리스트.")
    public ResponseEntity<List<UserDto.ListResponse>> searchUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "orgCode", required = false) String orgCode) {
        return ResponseEntity.ok(userService.searchUsersByName(keyword, orgCode));
    }
}
