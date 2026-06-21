package com.kdb.it.common.system.controller;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 개발 편의용 사용자 전환 컨트롤러
 *
 * <p>비밀번호 입력 없이 임의의 사용자로 세션을 전환할 수 있는 개발 전용 API입니다.
 * 협의회 등 권한별 화면을 빠르게 검증하기 위해 도입했습니다.</p>
 *
 * <p>활성화 조건: {@code app.dev.user-switch.enabled=true}.
 * 기본값은 비활성이며 로컬/개발 프로파일에서만 명시적으로 켭니다.</p>
 *
 * <p>제공 엔드포인트:</p>
 * <ul>
 *   <li>{@code GET  /api/auth/dev/users} — 전환 가능한 사용자 목록(ID/부서명/팀명/이름)</li>
 *   <li>{@code POST /api/auth/dev/switch-user} — 선택한 사번으로 JWT 쿠키 재발급</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dev.user-switch.enabled", havingValue = "true")
@Tag(name = "DevAuth", description = "개발 전용 사용자 전환 API")
public class DevAuthController {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final CookieUtil cookieUtil;

    /**
     * 사용자 전환 팝업에 표시할 사용자 목록 조회
     *
     * <p>이름순 정렬로 전체 활성 사용자(미삭제 사용자)를 반환합니다.
     * {@link UserDto.ListResponse}를 재사용해 ID/부서명/팀명/이름 등을 한 번에 전달합니다.</p>
     *
     * @return 사용자 목록 (이름 오름차순)
     */
    @GetMapping("/users")
    @Operation(summary = "개발용 사용자 목록", description = "사용자 전환 팝업에 표시할 전체 사용자 목록을 반환합니다.")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserDto.ListResponse>> listUsers() {
        List<UserDto.ListResponse> users = userRepository.findAllByOrderByUsrNmAsc().stream()
                .filter(u -> !"Y".equals(u.getDelYn()))
                .map(this::toListResponse)
                .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * 지정 사번으로 세션 전환 (비밀번호 미검증)
     *
     * <p>{@link AuthService#issueDevSwitchTokens(String)}을 호출해 토큰을 재발급하고
     * httpOnly 쿠키 3종(Access/Refresh/it-portal-user)을 응답에 세팅합니다.
     * 프론트엔드는 응답 body의 사용자 정보로 Pinia 상태를 갱신합니다.</p>
     *
     * @param request 전환 대상 사번이 담긴 요청 DTO
     * @return Set-Cookie 헤더 + 응답 body(사용자 정보)
     */
    @PostMapping("/switch-user")
    @Operation(summary = "개발용 사용자 전환", description = "비밀번호 검증 없이 지정 사번으로 JWT 쿠키를 재발급합니다.")
    public ResponseEntity<AuthDto.LoginResponse> switchUser(@Valid @RequestBody SwitchRequest request) {
        AuthDto.LoginResponse response = authService.issueDevSwitchTokens(request.getEno());

        ResponseCookie accessCookie = cookieUtil.createAccessTokenCookie(response.getAccessToken());
        ResponseCookie refreshCookie = cookieUtil.createRefreshTokenCookie(response.getRefreshToken());
        ResponseCookie userCookie = cookieUtil.createUserInfoCookie(response);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.SET_COOKIE, userCookie.toString())
                .body(response);
    }

    /**
     * CuserI 엔티티를 목록 응답 DTO로 변환합니다.
     *
     * <p>{@link CuserI#getBbrNm()}은 조직 연관관계를 통해 조회되므로
     * EntityGraph로 즉시 로딩된 조직 정보가 필요합니다.</p>
     */
    private UserDto.ListResponse toListResponse(CuserI user) {
        return UserDto.ListResponse.fromEntity(user, user.getBbrNm());
    }

    /** 사용자 전환 요청 본문 */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class SwitchRequest {
        /** 전환 대상 사번 */
        @NotBlank(message = "사번은 필수입니다.")
        private String eno;
    }
}
