package com.kdb.it.common.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 인증(Authentication) 관련 DTO 클래스 모음
 *
 * <p>
 * 로그인, 회원가입, JWT 토큰 갱신에 사용되는 Request/Response DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.
 * </p>
 *
 * <p>
 * 포함된 DTO:
 * </p>
 * <ul>
 * <li>{@link LoginRequest}: 로그인 요청 (사번 + 비밀번호)</li>
 * <li>{@link SignupRequest}: 회원가입 요청 (사번 + 이름 + 비밀번호)</li>
 * <li>{@link LoginResponse}: 로그인 응답 (Access Token + Refresh Token + 사용자
 * 정보)</li>
 * <li>{@link RefreshRequest}: Access Token 갱신 요청 (Refresh Token)</li>
 * <li>{@link RefreshResponse}: Access Token 갱신 응답 (새 Access Token)</li>
 * </ul>
 */
public class AuthDto {

    /**
     * 로그인 요청 DTO
     *
     * <p>
     * 사번({@code eno})과 비밀번호({@code password})를 입력받아 인증을 시도합니다.
     * </p>
     *
     * <p>
     * 비밀번호는 평문으로 전송되며, 서버에서 SHA-256 + Base64 방식으로 변환하여
     * DB에 저장된 암호화된 비밀번호와 비교합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "LoginRequest", description = "로그인 요청")
    public static class LoginRequest {
        /** 사번 (직원 고유 식별자) */
        @Schema(description = "사번")
        private String eno;

        /** 비밀번호 (평문, 서버에서 SHA-256+Base64 해싱 후 비교) */
        @Schema(description = "비밀번호")
        private String password;
    }

    /**
     * 회원가입 요청 DTO
     *
     * <p>
     * 새 사용자를 등록할 때 사용합니다. 사번 중복 여부를 확인하고
     * 비밀번호를 암호화하여 저장합니다.
     * </p>
     *
     * <p>
     * 주의: 현재 구현에서는 외부에서 회원가입이 가능하므로,
     * 운영 환경에서는 관리자 권한 제한이 필요합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "SignupRequest", description = "회원가입 요청")
    public static class SignupRequest {
        /** 사번 (중복 불가, PK) */
        @Schema(description = "사번")
        private String eno;

        /** 직원명 (한국어 이름) */
        @Schema(description = "이름")
        private String empNm;

        /** 비밀번호 (평문, 저장 시 SHA-256+Base64로 암호화) */
        @Schema(description = "비밀번호")
        private String password;

        /** 부서코드 (BBR_C) */
        @Schema(description = "부서코드")
        private String bbrC;

        /** 직위명 (예: 부장, 팀장, 대리) */
        @Schema(description = "직위명")
        private String ptCNm;

        /** 팀코드 */
        @Schema(description = "팀코드")
        private String temC;

        /** 팀명 */
        @Schema(description = "팀명")
        private String temNm;
    }

    /**
     * 로그인 응답 DTO
     *
     * <p>
     * 로그인 성공 시 발급되는 JWT 토큰과 기본 사용자 정보를 반환합니다.
     * </p>
     *
     * <p>
     * 토큰 사용 방법:
     * </p>
     * <ul>
     * <li>{@code accessToken}: API 요청 시 {@code Authorization: Bearer {accessToken}}
     * 헤더에 포함</li>
     * <li>{@code refreshToken}: Access Token 만료 시 {@code /api/auth/refresh} 엔드포인트에
     * 제출</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "LoginResponse", description = "로그인 응답")
    public static class LoginResponse {
        /**
         * JWT Access Token
         * <p>
         * 단기 유효 토큰 (기본 15분). httpOnly 쿠키로 전달됩니다.
         * </p>
         * <p>
         * JSON 응답 body에는 포함되지 않습니다 ({@code @JsonIgnore}).
         * </p>
         */
        @Schema(hidden = true)
        @JsonIgnore
        private String accessToken;

        /**
         * JWT Refresh Token
         * <p>
         * 장기 유효 토큰 (기본 7일). httpOnly 쿠키로 전달됩니다.
         * DB에 저장되어 관리됩니다.
         * </p>
         * <p>
         * JSON 응답 body에는 포함되지 않습니다 ({@code @JsonIgnore}).
         * </p>
         */
        @Schema(hidden = true)
        @JsonIgnore
        private String refreshToken;

        /** 로그인한 사용자의 사번 */
        @Schema(description = "사번")
        private String eno;

        /** 로그인한 사용자의 이름 */
        @Schema(description = "이름")
        private String empNm;

        /**
         * 자격등급 ID 목록 (다중 자격등급 지원)
         * 예: ["ITPZZ001", "ITPZZ002"]
         * ITPZZ001=일반사용자, ITPZZ002=기획통할담당자, ITPAD001=시스템관리자
         */
        @Schema(description = "자격등급 ID 목록")
        private List<String> athIds;

        /** 소속 부서코드: 권한 범위 결정에 사용 */
        @Schema(description = "소속 부서코드")
        private String bbrC;

        /** 소속 팀코드 */
        @Schema(description = "소속 팀코드")
        private String temC;
    }

    /**
     * Access Token 갱신 요청 DTO
     *
     * <p>
     * Access Token이 만료되었을 때 Refresh Token을 제출하여
     * 새 Access Token을 발급받습니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(name = "RefreshRequest", description = "토큰 갱신 요청")
    public static class RefreshRequest {
        /**
         * 기존에 발급받은 Refresh Token
         * <p>
         * 만료되지 않아야 하며, DB에 저장된 토큰과 일치해야 합니다.
         * </p>
         */
        @Schema(description = "Refresh Token")
        private String refreshToken;
    }

    /**
     * Access Token 갱신 응답 DTO
     *
     * <p>
     * Refresh Token 검증 성공 시 새로 발급된 Access Token을 반환합니다.
     * </p>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "RefreshResponse", description = "토큰 갱신 응답")
    public static class RefreshResponse {
        /**
         * 새로 발급된 JWT Access Token
         * <p>
         * 단기 유효 토큰 (기본 15분). 컨트롤러에서 httpOnly 쿠키로 전달됩니다.
         * </p>
         */
        @Schema(description = "새로운 Access Token")
        private String accessToken;
    }
}
