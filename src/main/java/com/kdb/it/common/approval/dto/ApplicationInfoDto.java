package com.kdb.it.common.approval.dto;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 신청서 상세 정보 공통 DTO
 *
 * <p>프로젝트({@link com.kdb.it.domain.budget.project.dto.ProjectDto.Response})와 전산관리비({@link
 * com.kdb.it.domain.budget.cost.dto.CostDto.Response}) 응답에 공통으로 포함되는 신청서 상세 정보를 담습니다.
 *
 * <p>포함 정보:
 *
 * <ul>
 *   <li>신청서 기본 정보: 관리번호, 상태, 신청서명, 신청자, 신청일, 의견 ({@link Capplm})
 *   <li>결재자 목록: 결재순서별 결재 정보 ({@link Cdecim} → {@link ApproverDto})
 * </ul>
 *
 * <p>{@link #fromEntities(Capplm, List)} 정적 팩토리 메서드로 엔티티에서 변환합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(
        name = "ApplicationInfo",
        description = "신청서 상세 정보",
        requiredProperties = {
            "apfMngNo",
            "apfSts",
            "apfNm",
            "rqsEno",
            "rqsDt",
            "rqsOpnn",
            "approvers"
        })
public class ApplicationInfoDto {

    /** 신청서관리번호 (APF_MNG_NO) */
    @Schema(description = "신청서관리번호")
    private String apfMngNo;

    /** 신청서상태 (APF_STS, 예: "결재중", "결재완료", "반려") */
    @Schema(description = "신청서상태", nullable = true)
    private String apfSts;

    /** 신청서명 (APF_NM) */
    @Schema(description = "신청서명")
    private String apfNm;

    /** 신청자 사번 (RQS_ENO) */
    @Schema(description = "신청자 사번")
    private String rqsEno;

    /** 신청일자 (RQS_DT) */
    @Schema(description = "신청일자")
    private LocalDate rqsDt;

    /** 신청의견 (RQS_OPNN) */
    @Schema(description = "신청의견", nullable = true)
    private String rqsOpnn;

    /** 결재자 목록 (결재순서 오름차순) */
    @Schema(description = "결재자 목록")
    private List<ApproverDto> approvers;

    /**
     * {@link Capplm} 엔티티와 {@link Cdecim} 목록을 DTO로 변환하는 정적 팩토리 메서드
     *
     * @param capplm 신청서 마스터 엔티티
     * @param decisions 결재선 목록 (결재순서 오름차순 정렬)
     * @return 변환된 신청서 상세 정보 DTO
     */
    public static ApplicationInfoDto fromEntities(Capplm capplm, List<Cdecim> decisions) {
        // 결재선 목록을 ApproverDto 목록으로 변환
        List<ApproverDto> approverDtos = decisions.stream().map(ApproverDto::fromEntity).toList();

        return ApplicationInfoDto.builder()
                .apfMngNo(capplm.getApfMngNo()) // 신청서관리번호
                .apfSts(
                        capplm.getItPtlApfPrgStsC() == null
                                ? null
                                : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(
                                                capplm.getItPtlApfPrgStsC())
                                        .label()) // 신청서상태(코드→라벨)
                .apfNm(capplm.getDcdReqTtl()) // 신청서명(결재요청제목에서 파생)
                .rqsEno(capplm.getDcdReqUsid()) // 신청자 사번(결재요청사용자ID에서 파생)
                .rqsDt(capplm.getDcdReqDtm()) // 신청일자(결재요청일시에서 파생)
                .rqsOpnn(capplm.getRgprDcdReqCone()) // 신청의견(등록자결재요청내용에서 파생)
                .approvers(approverDtos) // 결재자 목록
                .build();
    }

    /**
     * 신청서 요약 view와 결재선 read view를 신청서 상세 정보로 변환합니다.
     *
     * @param application 신청서 요약 view
     * @param decisions 결재 순번 오름차순 read view 목록
     * @return 변환된 신청서 상세 정보 DTO
     */
    public static ApplicationInfoDto fromReadViews(
            ApplicationRepository.ApplicationSummaryView application,
            List<ApproverRepository.ApproverReadView> decisions) {
        return ApplicationInfoDto.builder()
                .apfMngNo(application.getApfMngNo())
                .apfSts(
                        application.getItPtlApfPrgStsC() == null
                                ? null
                                : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(
                                                application.getItPtlApfPrgStsC())
                                        .label())
                .apfNm(application.getDcdReqTtl())
                .rqsEno(application.getDcdReqUsid())
                .rqsDt(application.getDcdReqDtm())
                .rqsOpnn(application.getRgprDcdReqCone())
                .approvers(decisions.stream().map(ApproverDto::fromReadView).toList())
                .build();
    }

    /**
     * 결재자 정보 DTO
     *
     * <p>{@link Cdecim} 엔티티에서 결재자의 결재 처리 정보를 담습니다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(
            name = "Approver",
            description = "결재자 정보",
            requiredProperties = {"dcdSqn", "dcdEno", "dcdTp", "dcdSts", "dcdDt", "dcdOpnn"})
    public static class ApproverDto {

        /** 결재순서 (DCD_SQN, 1부터 시작) */
        @Schema(description = "결재순서")
        private Integer dcdSqn;

        /** 결재자 사번 (DCD_ENO) */
        @Schema(description = "결재자 사번")
        private String dcdEno;

        /** 결재유형 (DCD_TP, null=미결재, "결재"=결재 처리됨) */
        @Schema(description = "결재유형", nullable = true)
        private String dcdTp;

        /** 결재상태 (IT_PTL_DCD_STS_C, null=미결재, "승인", "반려") */
        @Schema(description = "결재상태", nullable = true)
        private String dcdSts;

        /** 결재일자 (DCD_DT, 미결재 시 null) */
        @Schema(description = "결재일자", nullable = true)
        private LocalDate dcdDt;

        /** 결재의견 (DCD_OPNN) */
        @Schema(description = "결재의견", nullable = true)
        private String dcdOpnn;

        /**
         * {@link Cdecim} 엔티티를 결재자 DTO로 변환하는 정적 팩토리 메서드
         *
         * @param cdecim 변환할 Cdecim 엔티티
         * @return 변환된 결재자 DTO
         */
        public static ApproverDto fromEntity(Cdecim cdecim) {
            String itPtlDcdStsC = cdecim.getItPtlDcdStsC();
            boolean pending = itPtlDcdStsC == null || DecisionStatus.isPendingCode(itPtlDcdStsC);
            return ApproverDto.builder()
                    .dcdSqn(cdecim.getDcrSqnSno()) // 결재순서
                    .dcdEno(cdecim.getDcrEno()) // 결재자 사번
                    .dcdTp(pending ? null : "결재") // 결재유형(미결재면 null)
                    .dcdSts(pending ? null : DecisionStatus.ofCode(itPtlDcdStsC).label())
                    .dcdDt(cdecim.getDcdDtm()) // 결재일자
                    .dcdOpnn(cdecim.getDcrOpnnCone()) // 결재의견
                    .build();
        }

        /**
         * 결재선 read view를 결재자 DTO로 변환합니다.
         *
         * @param view 결재선 read view
         * @return 변환된 결재자 DTO
         */
        public static ApproverDto fromReadView(ApproverRepository.ApproverReadView view) {
            String status = view.getItPtlDcdStsC();
            boolean pending = status == null || DecisionStatus.isPendingCode(status);
            return ApproverDto.builder()
                    .dcdSqn(view.getDcrSqnSno())
                    .dcdEno(view.getDcrEno())
                    .dcdTp(pending ? null : "결재")
                    .dcdSts(pending ? null : DecisionStatus.ofCode(status).label())
                    .dcdDt(view.getDcdDtm())
                    .dcdOpnn(view.getDcrOpnnCone())
                    .build();
        }
    }
}
