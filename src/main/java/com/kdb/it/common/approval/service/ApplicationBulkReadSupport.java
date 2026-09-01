package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.dto.ApplicationApproverDisplay;
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
        Map<String, String> requesterNamesByEno = resolveRequesterNames(foundViews, userRepository);
        Map<String, String> requesterDeptNamesByBbrC =
                resolveRequesterDeptNames(foundViews, organizationRepository);
        List<ApplicationDto.Response> items =
                foundIds.stream()
                        .map(viewsById::get)
                        .map(
                                view ->
                                        ApplicationDto.Response.fromReadViews(
                                                view,
                                                approversByApf.getOrDefault(
                                                        view.getApfMngNo(), List.of()),
                                                requesterName(
                                                        requesterNamesByEno, view.getDcdReqUsid()),
                                                requesterDeptName(
                                                        requesterDeptNamesByBbrC,
                                                        view.getDcdReqBbrC()),
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
        Map<String, String> requesterNamesByEno = resolveRequesterNames(views, userRepository);
        Map<String, String> requesterDeptNamesByBbrC =
                resolveRequesterDeptNames(views, organizationRepository);
        return views.stream()
                .map(
                        view ->
                                ApplicationDto.Response.fromReadViews(
                                        view,
                                        approversByApf.getOrDefault(view.getApfMngNo(), List.of()),
                                        requesterName(requesterNamesByEno, view.getDcdReqUsid()),
                                        requesterDeptName(
                                                requesterDeptNamesByBbrC, view.getDcdReqBbrC()),
                                        approverDisplaysByEno))
                .toList();
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
        return userRepository.findByEnoIn(approverEnos).stream()
                .collect(
                        Collectors.toMap(
                                CuserI::getEno,
                                user ->
                                        new ApplicationApproverDisplay(
                                                user.getUsrNm(), user.getPtCNm(), user.getBbrNm()),
                                (left, right) -> left));
    }

    private static Map<String, String> resolveRequesterNames(
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
                                UserRepository.UserNameView::getUsrNm,
                                (left, right) -> left));
    }

    private static String requesterName(Map<String, String> names, String eno) {
        return eno == null || eno.isBlank() ? null : names.get(eno);
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
