package com.kdb.it.common.approval.dto;

import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import java.util.List;
import java.util.Map;

/** 신청서 read view와 배치 조회한 표시 정보를 응답 DTO로 조립합니다. */
final class ApplicationResponseAssembler {

    private ApplicationResponseAssembler() {}

    static ApplicationDto.Response fromReadViews(
            ApplicationRepository.ApplicationReadView view,
            List<ApproverRepository.ApproverReadView> approvers,
            String requesterNm,
            String requesterBbrNm,
            Map<String, ApplicationApproverDisplay> approverDisplaysByEno) {
        return ApplicationDto.Response.builder()
                .apfMngNo(view.getApfMngNo())
                .apfNm(view.getDcdReqTtl())
                .apfDtlCone(view.getDcdReqInf())
                .apfSts(
                        view.getItPtlApfPrgStsC() == null
                                ? null
                                : com.kdb.it.common.approval.domain.ApprovalStatus.ofCode(
                                                view.getItPtlApfPrgStsC())
                                        .label())
                .apfStsC(view.getItPtlApfPrgStsC())
                .rqsEno(view.getDcdReqUsid())
                .rqsNm(requesterNm)
                .rqsBbrC(view.getDcdReqBbrC())
                .rqsBbrNm(requesterBbrNm)
                .rqsDt(view.getDcdReqDtm())
                .rqsOpnn(view.getRgprDcdReqCone())
                .migrated(
                        MigrationApprovalMarker.isMigrated(
                                view.getItPtlApfPrgStsC(), view.getRgprDcdReqCone()))
                .approvers(
                        approvers.stream()
                                .map(
                                        approver ->
                                                toApprover(
                                                        approver,
                                                        approverDisplay(
                                                                approverDisplaysByEno,
                                                                approver.getDcrEno())))
                                .toList())
                .build();
    }

    private static ApplicationApproverDisplay approverDisplay(
            Map<String, ApplicationApproverDisplay> displaysByEno, String eno) {
        return eno == null || eno.isBlank() ? null : displaysByEno.get(eno);
    }

    private static ApplicationDto.ApproverResponse toApprover(
            ApproverRepository.ApproverReadView view, ApplicationApproverDisplay display) {
        String status = view.getItPtlDcdStsC();
        boolean pending = status == null || DecisionStatus.isPendingCode(status);
        return ApplicationDto.ApproverResponse.builder()
                .dcdSqn(view.getDcrSqnSno())
                .dcdEno(view.getDcrEno())
                .usrNm(display == null ? null : display.usrNm())
                .ptCNm(display == null ? null : display.ptCNm())
                .bbrNm(display == null ? null : display.bbrNm())
                .dcdTp(pending ? null : "결재")
                .dcdDt(view.getDcdDtm())
                .dcdOpnn(view.getDcrOpnnCone())
                .dcdSts(pending ? null : DecisionStatus.ofCode(status).label())
                .lstDcdYn(view.getLstDcdYn())
                .build();
    }
}
