package com.kdb.it.domain.budget.document.dto;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사전협의 검토자 DTO */
public class ReviewerDto {

    private ReviewerDto() {}

    /**
     * 검토자 응답 DTO
     *
     * <p>사전협의 검토자 목록 조회 API 응답에 사용됩니다.
     */
    @Schema(name = "ReviewerResponse", description = "검토자 정보")
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {

        @Schema(description = "사번")
        private String eno;

        @Schema(description = "사용자명")
        private String empNm;

        @Schema(description = "소속 팀명", example = "PMO팀")
        private String teamName;

        /**
         * CuserI 엔티티와 팀명을 받아 응답 DTO를 생성합니다.
         *
         * @param user 사용자 엔티티
         * @param teamName 검토자 팀명 (ReviewerTeam 레이블)
         * @return 응답 DTO
         */
        public static Response from(CuserI user, String teamName) {
            return new Response(user.getEno(), user.getUsrNm(), teamName);
        }

        /**
         * 팀 대표 사용자 프로젝션과 팀명을 받아 응답 DTO를 생성합니다.
         *
         * @param user 팀 대표 사용자 프로젝션
         * @param teamName 검토자 팀명
         * @return 검토자 응답 DTO
         */
        public static Response fromView(UserRepository.CommitteeUserRow user, String teamName) {
            return new Response(user.getEno(), user.getUsrNm(), teamName);
        }
    }
}
