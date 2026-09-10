package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationApproverDisplay;
import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApplicationRequesterInfo;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 신청서 일괄 조회의 배치 읽기와 응답 조립을 담당합니다. */
final class ApplicationBulkReadSupport {

    private ApplicationBulkReadSupport() {}

    /** 신청서·결재자·신청자·부서 정보를 배치로 읽어 일괄 응답을 조립합니다. */
    static ApplicationDto.BulkResponse read(
            ApplicationDto.BulkGetRequest request,
            ApplicationRepository applicationRepository,
            ApproverRepository approverRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository) {
        List<String> requestedIds = request.getApfMngNos();
        List<ApplicationRepository.ApplicationReadView> views =
                applicationRepository.findReadViewsByApfMngNoIn(requestedIds);
        Map<String, ApplicationRepository.ApplicationReadView> viewsById =
                views.stream()
                        .collect(
                                Collectors.toMap(
                                        ApplicationRepository.ApplicationReadView::getApfMngNo,
                                        value -> value,
                                        (left, right) -> left));
        List<String> foundIds =
                requestedIds.stream().filter(viewsById::containsKey).distinct().toList();
        List<ApplicationRepository.ApplicationReadView> foundViews =
                foundIds.stream().map(viewsById::get).toList();
        List<ApproverRepository.ApproverReadView> approverViews =
                approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(foundIds);
        Map<String, List<ApproverRepository.ApproverReadView>> approversByApf =
                approverViews.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ApproverRepository.ApproverReadView::getDcdMngNo));
        Map<String, ApplicationApproverDisplay> approverDisplaysByEno =
                resolveApproverDisplays(approverViews, userRepository);
        Map<String, UserRepository.UserNameView> requesterUsersByEno =
                resolveRequesterUsers(foundViews, userRepository);
        Map<String, String> requesterDeptNamesByBbrC =
                resolveRequesterDeptNames(foundViews, organizationRepository);
        Map<String, String> requesterOpinionsByApf =
                resolveRequesterOpinions(foundIds, approverRepository);
        List<ApplicationDto.Response> items =
                foundIds.stream()
                        .map(viewsById::get)
                        .map(
                                view ->
                                        ApplicationDto.Response.fromReadViews(
                                                view,
                                                approversByApf.getOrDefault(
                                                        view.getApfMngNo(), List.of()),
                                                requesterInfo(
                                                        requesterUsersByEno,
                                                        requesterDeptNamesByBbrC,
                                                        requesterOpinionsByApf,
                                                        view),
                                                approverDisplaysByEno))
                        .toList();
        List<String> failedIds = new ArrayList<>();
        for (String apfMngNo : requestedIds) {
            if (!viewsById.containsKey(apfMngNo) && !failedIds.contains(apfMngNo)) {
                // 미존재 ID는 조용히 버리지 않고 실패 목록에 수집해 호출자에게 노출합니다.
                failedIds.add(apfMngNo);
            }
        }
        return new ApplicationDto.BulkResponse(items, failedIds);
    }

    /**
     * 신청서 read view 목록에 결재선·신청자·부서 정보를 배치로 붙여 응답 DTO로 조립합니다.
     *
     * <p>목록 조회(전체·본인 결재 대기)가 공유하는 조립 경로입니다. 결재선은 IN 배치 1회로 읽어 N+1을 만들지 않으며, {@code
     * findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc}가 순번 오름차순으로 반환하므로 그룹 안의 결재자 순서가 보존됩니다.
     *
     * @param views 신청서 마스터 read view 목록 (입력 순서가 응답 순서가 된다)
     * @param approverRepository 결재선 리포지토리
     * @param userRepository 사용자 리포지토리 (신청자명)
     * @param organizationRepository 조직 리포지토리 (신청부서명)
     * @return 신청서 응답 DTO 목록
     */
    static List<ApplicationDto.Response> assembleList(
            List<ApplicationRepository.ApplicationReadView> views,
            ApproverRepository approverRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository) {
        List<String> apfMngNos =
                views.stream().map(ApplicationRepository.ApplicationReadView::getApfMngNo).toList();
        List<ApproverRepository.ApproverReadView> approverViews =
                approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(apfMngNos);
        Map<String, List<ApproverRepository.ApproverReadView>> approversByApf =
                approverViews.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ApproverRepository.ApproverReadView::getDcdMngNo));
        Map<String, ApplicationApproverDisplay> approverDisplaysByEno =
                resolveApproverDisplays(approverViews, userRepository);
        Map<String, UserRepository.UserNameView> requesterUsersByEno =
                resolveRequesterUsers(views, userRepository);
        Map<String, String> requesterDeptNamesByBbrC =
                resolveRequesterDeptNames(views, organizationRepository);
        Map<String, String> requesterOpinionsByApf =
                resolveRequesterOpinions(apfMngNos, approverRepository);
        return views.stream()
                .map(
                        view ->
                                ApplicationDto.Response.fromReadViews(
                                        view,
                                        approversByApf.getOrDefault(view.getApfMngNo(), List.of()),
                                        requesterInfo(
                                                requesterUsersByEno,
                                                requesterDeptNamesByBbrC,
                                                requesterOpinionsByApf,
                                                view),
                                        approverDisplaysByEno))
                .toList();
    }

    /** 단건 신청서에 결재선·신청자·부서 정보를 붙여 응답 DTO로 조립합니다. */
    static ApplicationDto.Response assembleOne(
            ApplicationRepository.ApplicationReadView view,
            ApproverRepository approverRepository,
            UserRepository userRepository,
            OrganizationRepository organizationRepository) {
        List<ApproverRepository.ApproverReadView> approvers =
                approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc(view.getApfMngNo());
        Map<String, ApplicationApproverDisplay> approverDisplaysByEno =
                resolveApproverDisplays(approvers, userRepository);
        Map<String, UserRepository.UserNameView> requesterUsersByEno =
                resolveRequesterUsers(List.of(view), userRepository);
        Map<String, String> requesterDeptNamesByBbrC =
                resolveRequesterDeptNames(List.of(view), organizationRepository);
        Map<String, String> requesterOpinionsByApf =
                resolveRequesterOpinions(List.of(view.getApfMngNo()), approverRepository);
        return ApplicationDto.Response.fromReadViews(
                view,
                approvers,
                requesterInfo(
                        requesterUsersByEno,
                        requesterDeptNamesByBbrC,
                        requesterOpinionsByApf,
                        view),
                approverDisplaysByEno);
    }

    /** 결재선의 사번을 한 번에 해석해 결재자 표시 정보 맵으로 변환합니다. */
    static Map<String, ApplicationApproverDisplay> resolveApproverDisplays(
            List<ApproverRepository.ApproverReadView> approvers, UserRepository userRepository) {
        Set<String> approverEnos =
                approvers.stream()
                        .map(ApproverRepository.ApproverReadView::getDcrEno)
                        .filter(eno -> eno != null && !eno.isBlank())
                        .collect(Collectors.toSet());
        if (approverEnos.isEmpty()) return Map.of();
        return userRepository.findByEnoInWithOrganization(approverEnos).stream()
                .collect(
                        Collectors.toMap(
                                CuserI::getEno,
                                user ->
                                        new ApplicationApproverDisplay(
                                                user.getUsrNm(), user.getPtCNm(), user.getBbrNm()),
                                (left, right) -> left));
    }

    /** 신청자 사번을 한 번에 해석해 성명·직위명 프로젝션 맵으로 변환합니다. */
    private static Map<String, UserRepository.UserNameView> resolveRequesterUsers(
            List<ApplicationRepository.ApplicationReadView> views, UserRepository userRepository) {
        Set<String> requesterEnos =
                views.stream()
                        .map(ApplicationRepository.ApplicationReadView::getDcdReqUsid)
                        .filter(eno -> eno != null && !eno.isBlank())
                        .collect(Collectors.toSet());
        if (requesterEnos.isEmpty()) return Map.of();
        return userRepository.findNameViewsByEnoIn(requesterEnos).stream()
                .collect(
                        Collectors.toMap(
                                UserRepository.UserNameView::getEno,
                                view -> view,
                                (left, right) -> left));
    }

    /**
     * 신청서별 기안자 결재의견을 IN 배치 1회로 읽습니다.
     *
     * <p>기안자 요청 행(순번 0)이 없는 신청서는 결과에 없으므로 의견도 null이 됩니다. 신청의견으로 대신 채우지 않습니다 — 두 값은 서로 다른 정보입니다.
     */
    private static Map<String, String> resolveRequesterOpinions(
            List<String> apfMngNos, ApproverRepository approverRepository) {
        if (apfMngNos.isEmpty()) return Map.of();
        return approverRepository.findRequesterDecisionViewsByDcdMngNoIn(apfMngNos).stream()
                .filter(view -> view.getDcrOpnnCone() != null)
                .collect(
                        Collectors.toMap(
                                ApproverRepository.RequesterDecisionView::getDcdMngNo,
                                ApproverRepository.RequesterDecisionView::getDcrOpnnCone,
                                (left, right) -> left));
    }

    /** 배치로 읽은 신청자 표시 정보와 기안자 의견을 한 신청서 기준으로 모읍니다. */
    private static ApplicationRequesterInfo requesterInfo(
            Map<String, UserRepository.UserNameView> requesterUsersByEno,
            Map<String, String> requesterDeptNamesByBbrC,
            Map<String, String> requesterOpinionsByApf,
            ApplicationRepository.ApplicationReadView view) {
        String eno = view.getDcdReqUsid();
        UserRepository.UserNameView requester =
                eno == null || eno.isBlank() ? null : requesterUsersByEno.get(eno);
        return new ApplicationRequesterInfo(
                requester == null ? null : requester.getUsrNm(),
                requester == null ? null : requester.getPtCNm(),
                requesterDeptName(requesterDeptNamesByBbrC, view.getDcdReqBbrC()),
                requesterOpinionsByApf.get(view.getApfMngNo()));
    }

    private static Map<String, String> resolveRequesterDeptNames(
            List<ApplicationRepository.ApplicationReadView> views,
            OrganizationRepository organizationRepository) {
        Set<String> requesterBbrCs =
                views.stream()
                        .map(ApplicationRepository.ApplicationReadView::getDcdReqBbrC)
                        .filter(bbrC -> bbrC != null && !bbrC.isBlank())
                        .collect(Collectors.toSet());
        if (requesterBbrCs.isEmpty()) return Map.of();
        return organizationRepository.findNameViewsByPrlmOgzCConeIn(requesterBbrCs).stream()
                .filter(org -> org.getBbrNm() != null)
                .collect(
                        Collectors.toMap(
                                OrganizationRepository.OrganizationNameView::getPrlmOgzCCone,
                                OrganizationRepository.OrganizationNameView::getBbrNm,
                                (left, right) -> left));
    }

    private static String requesterDeptName(Map<String, String> names, String bbrC) {
        return bbrC == null || bbrC.isBlank() ? null : names.get(bbrC);
    }
}
