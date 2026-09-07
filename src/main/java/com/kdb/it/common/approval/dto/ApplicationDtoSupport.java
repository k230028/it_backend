package com.kdb.it.common.approval.dto;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.dto.ApplicationDto.ApproverResponse;
import com.kdb.it.common.approval.dto.ApplicationDto.Response;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import java.util.List;

/** 신청서 DTO의 엔티티 매핑을 담당합니다. */
final class ApplicationDtoSupport {

    private ApplicationDtoSupport() {}

    static Response toResponse(
            Capplm capplm, List<Cdecim> approvers, String requesterNm, String requesterBbrNm) {
        return Response.builder()
                .apfMngNo(capplm.getApfMngNo()) // 신청관리번호
                .apfNm(capplm.getDcdReqTtl()) // 신청서명(결재요청제목에서 파생)
                .apfDtlCone(capplm.getDcdReqInf()) // 신청서세부내용(결재요청정보에서 파생)
                .apfSts(
                        capplm.getItPtlApfPrgStsC() == null
                                ? null
                                : ApprovalStatus.ofCode(capplm.getItPtlApfPrgStsC())
                                        .label()) // 신청상태(라벨, 코드에서 파생)
                .apfStsC(capplm.getItPtlApfPrgStsC()) // 신청상태코드
                .rqsEno(capplm.getDcdReqUsid()) // 신청자 사원번호(결재요청사용자ID에서 파생)
                .rqsNm(requesterNm) // 신청자명
                .rqsBbrC(capplm.getDcdReqBbrC()) // 신청부서코드
                .rqsBbrNm(requesterBbrNm) // 신청부서명
                .rqsDt(capplm.getDcdReqDtm()) // 신청일자(결재요청일시에서 파생)
                .rqsOpnn(capplm.getRgprDcdReqCone()) // 신청의견(등록자결재요청내용에서 파생)
                .migrated(
                        MigrationApprovalMarker.isMigrated(
                                capplm.getItPtlApfPrgStsC(), capplm.getRgprDcdReqCone()))
                .approvers(
                        approvers.stream()
                                .map(ApproverResponse::fromEntity) // 각 결재자 엔티티를 DTO로 변환
                                .toList())
                .build();
    }

    static ApproverResponse toApproverResponse(Cdecim cdecim) {
        return ApproverResponse.builder()
                .dcdSqn(cdecim.getDcrSqnSno()) // 결재순번
                .dcdEno(cdecim.getDcrEno()) // 결재자 사원번호
                // 결재유형: 미결재(001) 또는 null이면 null, 그 외는 "결재"로 표시
                .dcdTp(
                        cdecim.getItPtlDcdStsC() == null
                                        || DecisionStatus.isPendingCode(cdecim.getItPtlDcdStsC())
                                ? null
                                : "결재")
                .dcdDt(cdecim.getDcdDtm()) // 결재일자
                .dcdOpnn(cdecim.getDcrOpnnCone()) // 결재의견
                // 결재상태: 코드 → 라벨 변환 (미결재/null이면 null)
                .dcdSts(
                        cdecim.getItPtlDcdStsC() == null
                                        || DecisionStatus.isPendingCode(cdecim.getItPtlDcdStsC())
                                ? null
                                : DecisionStatus.ofCode(cdecim.getItPtlDcdStsC()).label())
                .lstDcdYn(cdecim.getLstDcdYn()) // 최종결재자여부
                .build();
    }
}
