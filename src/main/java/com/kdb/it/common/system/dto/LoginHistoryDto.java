package com.kdb.it.common.system.dto;

import com.kdb.it.common.system.entity.Clognh;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 공통로그인이력 관련 DTO 클래스 모음
 *
 * <p>로그인/로그아웃 이력(TPRMPP_CLOGNH) 조회에 사용되는 Response DTO를
 * 정적 중첩 클래스(Static Nested Class) 형태로 관리합니다.</p>
 */
public class LoginHistoryDto {

    /**
     * 로그인 이력 조회 응답 DTO
     *
     * <p>{@link Clognh} 엔티티의 정보를 클라이언트에 전달합니다.</p>
     *
     * <p>로그인구분코드({@code lgnTc})는 공통코드 {@code C_ID='LGN_TC'} 기반 1자리 값입니다.</p>
     * <ul>
     *   <li>{@code 1}: 로그인 성공</li>
     *   <li>{@code 2}: 로그인 실패</li>
     *   <li>{@code 3}: 로그아웃</li>
     * </ul>
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(name = "LoginHistoryResponse", description = "로그인 이력 응답")
    public static class Response {
        /** 이력 ID (자동 증가 PK) */
        @Schema(description = "이력 ID")
        private Long id;

        /** 사번 (이력의 주체 사용자) */
        @Schema(description = "사번")
        private String eno;

        /**
         * 로그인구분코드
         * <p>공통코드 C_ID='LGN_TC' 기반 1자리 값. 1=성공, 2=실패, 3=로그아웃</p>
         */
        @Schema(description = "로그인구분코드 (공통코드 LGN_TC; 1=성공, 2=실패, 3=로그아웃)")
        private String lgnTc;

        /** 클라이언트 IP 주소 */
        @Schema(description = "IP 주소")
        private String ipAddress;

        /** 클라이언트 브라우저/앱 정보 (User-Agent 헤더 값) */
        @Schema(description = "에이전트버전내용 (User-Agent)")
        private String agtVrsCone;

        /** 이벤트 발생 시각 (로그인/로그아웃 시각) */
        @Schema(description = "로그인 시간")
        private LocalDateTime loginTime;

        /**
         * 로그인 오류 사유
         * <p>lgnTc가 {@code "2"}(로그인 실패)인 경우에만 값이 있습니다.</p>
         */
        @Schema(description = "로그인 오류 사유")
        private String lgnErrRsn;

        /**
         * {@link Clognh} 엔티티를 단건 응답 DTO로 변환합니다.
         */
        public static Response fromEntity(Clognh clognh) {
            return Response.builder()
                    .id(clognh.getLgnHisSno())                  // 로그인이력일련번호 → id 키 유지
                    .eno(clognh.getEno())                       // 사원번호
                    .lgnTc(clognh.getLgnTc())                   // 로그인구분코드
                    .ipAddress(clognh.getIpAddr())              // IP주소 → JSON 키 유지
                    .agtVrsCone(clognh.getAgtVrsCone())         // 에이전트버전내용
                    .loginTime(clognh.getLgnDtm())              // 로그인일시 → JSON 키 유지
                    .lgnErrRsn(clognh.getLgnErrRsn())           // 로그인오류사유
                    .build();
        }

        /**
         * {@link Clognh} 엔티티 목록을 응답 DTO 목록으로 변환합니다.
         */
        public static List<Response> fromEntities(List<Clognh> clognhs) {
            return clognhs.stream()
                    .map(Response::fromEntity)
                    .collect(Collectors.toList());
        }
    }
}
