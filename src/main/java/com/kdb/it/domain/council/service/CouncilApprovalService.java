package com.kdb.it.domain.council.service;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.service.ApplicationService;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bpovwm;
import com.kdb.it.domain.council.repository.ProjectOverviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 정보화실무협의회 전자결재 연동 서비스 (Step 1 — 타당성검토표 팀장 결재)
 *
 * <p>타당성검토표 작성완료(SUBMITTED) 후 팀장 결재를 공통 전자결재 시스템과 연동합니다.</p>
 *
 * <p>결재 흐름:</p>
 * <pre>
 *   SUBMITTED
 *     │  requestApproval() 호출
 *     ↓  → ApplicationService.submit()으로 결재 시스템 등록
 *   APPROVAL_PENDING
 *     │  팀장 승인 → processApprovalCallback(approved=true)
 *     ↓
 *   APPROVED
 *
 *   APPROVAL_PENDING
 *     │  팀장 반려 → processApprovalCallback(approved=false)
 *     ↓
 *   DRAFT  (재작성 필요)
 * </pre>
 *
 * <p>원본 데이터 연결: {@code orcTbCd="BASCTM"}, {@code orcPkVl=asctId}</p>
 *
 * <p>Design Ref: §2.1 M5 — 전자결재 연동</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouncilApprovalService {

    /** 협의회 기본 서비스 — 존재 확인 및 상태 전이용 */
    private final CouncilService councilService;

    /** 사업개요 리포지토리 — 신청서명 생성용 */
    private final ProjectOverviewRepository projectOverviewRepository;

    /** 공통 전자결재 서비스 */
    private final ApplicationService applicationService;

    // 원본 테이블 코드 (CAPPLA.ORC_TB_CD)
    private static final String ORC_TB_CD = "BASCTM";

    // =========================================================================
    // 결재 요청
    // =========================================================================

    /**
     * 타당성검토표 결재 요청 (SUBMITTED → APPROVAL_PENDING)
     *
     * <p>전자결재 시스템에 신청서를 등록하고 협의회 상태를 APPROVAL_PENDING으로 전이합니다.</p>
     *
     * <p>Plan SC: 결재자는 소관부서 팀장 1인 (approverEno)으로 단일 결재선 구성</p>
     *
     * @param asctId      협의회ID
     * @param request     결재 요청 (팀장 사번, 신청의견)
     * @param userDetails 신청자 정보 (JWT 주입)
     * @return 생성된 신청관리번호 (APF_...형식)
     * @throws IllegalStateException 현재 상태가 SUBMITTED가 아닌 경우
     */
    @Transactional
    public CouncilDto.ApprovalResponse requestApproval(
            String asctId,
            CouncilDto.ApprovalRequest request,
            CustomUserDetails userDetails) {

        // 협의회 존재 확인
        Basctm council = councilService.findActiveCouncil(asctId);

        // SUBMITTED 상태 확인 (작성완료 후에만 결재 요청 가능)
        if (!"SUBMITTED".equals(council.getAsctSts())) {
            throw new IllegalStateException(
                "결재 요청은 작성완료(SUBMITTED) 상태에서만 가능합니다. 현재 상태: " + council.getAsctSts());
        }

        // 신청서명 생성: "협의회 타당성검토표 결재 요청 - {사업명}"
        String prjNm = projectOverviewRepository
                .findByAsctIdAndDelYn(asctId, "N")
                .map(Bpovwm::getPrjNm)
                .orElse(asctId);
        String apfNm = "협의회 타당성검토표 결재 요청 - " + prjNm;

        // 결재 시스템에 신청서 등록 (단일 결재자)
        ApplicationDto.CreateRequest createRequest = buildApprovalRequest(
                apfNm, asctId, List.of(request.approverEno()),
                request.rqsOpnn(), userDetails.getEno());

        String apfMngNo = applicationService.submit(createRequest);

        // 협의회 상태 전이: SUBMITTED → APPROVAL_PENDING
        councilService.changeStatus(asctId, "APPROVAL_PENDING");

        return new CouncilDto.ApprovalResponse(apfMngNo);
    }

    // =========================================================================
    // 개최결과서 결재 요청
    // =========================================================================

    /**
     * 개최결과서 결재 요청 (IT관리자, FINAL_APPROVAL → RESULT_APPROVAL_PENDING)
     *
     * <p>전원 결과서 확인 완료 후 IT관리자가 부장에게 결재를 요청합니다.
     * 전자결재 시스템에 신청서를 등록하고 협의회 상태를 RESULT_APPROVAL_PENDING으로 전이합니다.</p>
     *
     * @param asctId      협의회ID
     * @param request     결재 요청 (부장 사번, 신청의견)
     * @param userDetails 신청자 정보 (JWT 주입)
     * @return 생성된 신청관리번호 (APF_...형식)
     * @throws IllegalStateException 현재 상태가 FINAL_APPROVAL이 아닌 경우
     */
    @Transactional
    public CouncilDto.ApprovalResponse requestResultApproval(
            String asctId,
            CouncilDto.ResultApprovalRequest request,
            CustomUserDetails userDetails) {

        // 협의회 존재 확인
        Basctm council = councilService.findActiveCouncil(asctId);

        // FINAL_APPROVAL 상태 확인 (전원 결과서 확인 완료 후에만 결재 요청 가능)
        if (!"FINAL_APPROVAL".equals(council.getAsctSts())) {
            throw new IllegalStateException(
                "개최결과서 결재 요청은 FINAL_APPROVAL 상태에서만 가능합니다. 현재 상태: " + council.getAsctSts());
        }

        // 신청서명 생성: "협의회 개최결과서 결재 요청 - {사업명}"
        String prjNm = projectOverviewRepository
                .findByAsctIdAndDelYn(asctId, "N")
                .map(Bpovwm::getPrjNm)
                .orElse(asctId);
        String apfNm = "협의회 개최결과서 결재 요청 - " + prjNm;

        // 결재 시스템에 신청서 등록 (팀장 → 부장 순)
        ApplicationDto.CreateRequest createRequest = buildApprovalRequest(
                apfNm, asctId, List.of(request.teamLeadEno(), request.deptHeadEno()),
                request.rqsOpnn(), userDetails.getEno());

        String apfMngNo = applicationService.submit(createRequest);

        // 협의회 상태 전이: FINAL_APPROVAL → RESULT_APPROVAL_PENDING
        councilService.changeStatus(asctId, "RESULT_APPROVAL_PENDING");

        return new CouncilDto.ApprovalResponse(apfMngNo);
    }

    // =========================================================================
    // 결재 콜백
    // =========================================================================

    /**
     * 결재 완료/반려 처리 (전자결재 시스템 콜백)
     *
     * <p>결재 시스템에서 승인 또는 반려 처리 시 이 메서드로 협의회 상태를 업데이트합니다.
     * 현재 상태에 따라 두 가지 결재 흐름을 분기 처리합니다:</p>
     *
     * <ul>
     *   <li>APPROVAL_PENDING (타당성검토표 결재):
     *       승인 → APPROVED, 반려 → DRAFT</li>
     *   <li>RESULT_APPROVAL_PENDING (개최결과서 결재):
     *       승인 → COMPLETED, 반려 → FINAL_APPROVAL (재결재 가능)</li>
     * </ul>
     *
     * @param asctId  협의회ID
     * @param request 결재 콜백 요청 (approved: 승인/반려 여부)
     * @throws IllegalStateException 현재 상태가 결재 대기 상태가 아닌 경우
     */
    @Transactional
    public void processApprovalCallback(String asctId, CouncilDto.ApprovalCallbackRequest request) {
        // 협의회 존재 확인
        Basctm council = councilService.findActiveCouncil(asctId);
        String currentStatus = council.getAsctSts();

        if ("APPROVAL_PENDING".equals(currentStatus)) {
            // 타당성검토표 결재 콜백
            if (request.approved()) {
                // 승인: APPROVAL_PENDING → APPROVED
                councilService.changeStatus(asctId, "APPROVED");
            } else {
                // 반려: APPROVAL_PENDING → DRAFT (타당성검토표 재작성)
                councilService.changeStatus(asctId, "DRAFT");
            }
        } else if ("RESULT_APPROVAL_PENDING".equals(currentStatus)) {
            // 개최결과서 결재 콜백
            if (request.approved()) {
                // 승인: RESULT_APPROVAL_PENDING → COMPLETED
                councilService.changeStatus(asctId, "COMPLETED");
            } else {
                // 반려: RESULT_APPROVAL_PENDING → FINAL_APPROVAL (재결재 요청 가능)
                councilService.changeStatus(asctId, "FINAL_APPROVAL");
            }
        } else {
            throw new IllegalStateException(
                "결재 콜백 처리는 결재대기 상태에서만 가능합니다. 현재 상태: " + currentStatus);
        }
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * ApplicationDto.CreateRequest 생성
     *
     * <p>orcTbCd="BASCTM", orcPkVl=asctId 로 원본 데이터를 연결합니다.
     * 결재자 1인(팀장)으로 단일 결재선을 구성합니다.</p>
     */
    private ApplicationDto.CreateRequest buildApprovalRequest(
            String apfNm, String asctId, List<String> approverEnos,
            String rqsOpnn, String rqsEno) {

        // 원본 데이터 연결 항목: BASCTM → asctId
        ApplicationDto.OrcItem orcItem = new ApplicationDto.OrcItem();
        orcItem.setOrcTbCd(ORC_TB_CD);
        orcItem.setOrcPkVl(asctId);
        orcItem.setOrcSnoVl(null); // BASCTM은 SNO 없음

        ApplicationDto.CreateRequest createRequest = new ApplicationDto.CreateRequest();
        createRequest.setApfNm(apfNm);
        createRequest.setApfDtlCone(null); // 협의회 결재는 별도 JSON 없음
        createRequest.setRqsEno(rqsEno);
        createRequest.setRqsOpnn(rqsOpnn);
        createRequest.setOrcItems(List.of(orcItem));
        createRequest.setApproverEnos(approverEnos);

        return createRequest;
    }
}
