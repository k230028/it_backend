package com.kdb.it.common.admin.controller;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.dto.AuthDto;
import com.kdb.it.common.system.service.AuthService;
import com.kdb.it.common.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자가 임직원 권한의 화면을 확인할 수 있게 사용자 세션을 전환합니다. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "AdminUserSwitch", description = "관리자 전용 사용자 전환 API")
public class AdminUserSwitchController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * 전환할 수 있는 활성 사용자를 이름 순으로 반환합니다.
     *
     * @return 삭제되지 않은 사용자 목록
     */
    @GetMapping("/switch-users")
    @Transactional(readOnly = true)
    @Operation(summary = "사용자 전환 대상 목록")
    public ResponseEntity<List<UserDto.ListResponse>> listUsers() {
        List<UserDto.ListResponse> users =
                userRepository.findAllByOrderByUsrNmAsc().stream()
                        .filter(user -> !"Y".equals(user.getDelYn()))
                        .map(this::toListResponse)
                        .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * 선택한 직원의 인증 쿠키로 현재 관리자 세션을 교체합니다.
     *
     * @param request 전환 대상 사번
     * @return 전환된 사용자 정보와 인증 쿠키
     */
    @PostMapping("/switch-user")
    @Operation(summary = "사용자 전환")
    public ResponseEntity<AuthDto.LoginResponse> switchUser(
            @Valid @RequestBody SwitchRequest request) {
        AuthDto.LoginResponse response = authService.issueUserSwitchTokens(request.getEno());
        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie =
                cookieUtil.createRefreshTokenCookie(response.getRefreshToken());
        ResponseCookie userCookie = cookieUtil.createUserInfoCookie(response);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, userCookie.toString())
                .body(response);
    }

    private UserDto.ListResponse toListResponse(CuserI user) {
        return UserDto.ListResponse.fromEntity(user, user.getBbrNm());
    }

    /** 사용자 전환 요청 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SwitchRequest {
        /** 전환 대상 사번 */
        @NotBlank(message = "사번은 필수입니다.")
        private String eno;
    }
}
