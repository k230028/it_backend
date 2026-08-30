package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
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
        Map<String, List<ApproverRepository.ApproverReadView>> approversByApf =
                approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(foundIds).stream()
                        .collect(
                                Collectors.groupingBy(
                                        ApproverRepository.ApproverReadView::getDcdMngNo));
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
                                                        view.getDcdReqBbrC())))
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
