package com.kdb.it.common.system.controller;

import com.kdb.it.common.system.dto.LoginHistoryDto;
import com.kdb.it.common.system.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 로그인 이력 조회 REST 컨트롤러
 *
 * <p>현재 로그인한 사용자의 로그인/로그아웃 이력을 조회합니다.</p>
 *
 * <p>기본 URL: {@code /api/login-history}</p>
 *
 * <p>보안: JWT 토큰 인증 필요. 본인의 이력만 조회 가능합니다.
 * (SecurityContextHolder에서 현재 사용자 사번을 추출하여 조회)</p>
 *
 * <p>이력 유형 ({@code LOGIN_TYPE}):</p>
 * <ul>
 *   <li>{@code 1=성공}: 로그인 성공</li>
 *   <li>{@code LOGIN_FAILURE}: 로그인 실패 (비밀번호 불일치 등)</li>
 *   <li>{@code LOGOUT}: 로그아웃</li>
 * </ul>
 */
@RestController                              // REST API 컨트롤러로 등록
@RequestMapping("/api/login-history")        // 기본 URL 경로 설정
@RequiredArgsConstructor                     // final 필드 생성자 자동 주입 (Lombok)
@Tag(name = "LoginHistory", description = "로그인 이력 API") // Swagger UI 그룹 태그
public class LoginHistoryController {

    /** 로그인 이력 비즈니스 로직 서비스 */
    private final LoginHistoryService loginHistoryService;

    /**
     * 본인 로그인 이력 조회 (최신순, 최대 50건)
     *
     * <p>현재 JWT 토큰으로 인증된 사용자의 로그인 이력을 조회합니다.
     * {@link SecurityContextHolder}에서 사번(eno)을 추출하여 해당 사용자의 이력만 반환합니다.</p>
     *
     * <p>반환 데이터:</p>
     * <ul>
     *   <li>이력 ID</li>
     *   <li>로그인 유형 (1=성공 / LOGIN_FAILURE / LOGOUT)</li>
     *   <li>접속 IP 주소</li>
     *   <li>User-Agent (브라우저/기기 정보)</li>
     *   <li>로그인 시각</li>
     *   <li>실패 사유 (실패한 경우만)</li>
     * </ul>
     *
     * @return HTTP 200 + 로그인 이력 목록 ({@link LoginHistoryDto.Response} 리스트, 최신순)
     */
    @GetMapping
    @Operation(summary = "본인 로그인 이력 조회", description = "본인의 로그인 이력을 조회합니다.")
    public ResponseEntity<List<LoginHistoryDto.Response>> getMyLoginHistory() {
        // SecurityContextHolder에서 현재 인증된 사용자 정보 조회
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // JWT에서 추출된 사용자 사번(행번)
        String eno = authentication.getName();

        // 해당 사번의 로그인 이력 조회 (최대 50건, 최신순)
        List<LoginHistoryDto.Response> history = loginHistoryService.getLoginHistory(eno);
        return ResponseEntity.ok(history);
    }
}
