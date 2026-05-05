package com.kdb.it.common.approval.service;

import java.time.LocalDate;
import java.util.List;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.event.ApprovalCompletedEvent;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import org.springframework.context.ApplicationEventPublisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 신청서(결재) 관리 서비스
 *
 * <p>
 * 정보화사업·전산관리비 등 각종 신청서의 등록, 조회, 결재(승인/반려), 일괄 결재를
 * 처리하는 비즈니스 로직을 담당합니다.
 * </p>
 *
 * <p>
 * 신청서 상태 흐름:
 * </p>
 *
 * <pre>
 *   [신청서 등록] → 결재중
 *                      ↓ (모든 결재자 순차 승인)
 *                   결재완료
 *                      ↓ (중간 결재자 반려)
 *                    반려
 * </pre>
 *
 * <p>
 * 결재선(Approval Line):
 * </p>
 * <ul>
 * <li>등록 시 결재자 사번 목록({@code approverEnos})을 순서대로 받아 {@link Cdecim}으로 저장</li>
 * <li>순차 결재: 앞 순번이 승인해야 다음 순번이 결재 가능</li>
 * <li>동일 결재자 연속 등장 시 일괄 승인 처리</li>
 * </ul>
 *
 * <p>
 * 원본 데이터 연결:
 * </p>
 * <ul>
 * <li>신청서는 원본 테이블(예: BPROJM)과 {@link Cappla}로 연결</li>
 * <li>{@code orcTbCd}: 원본 테이블 코드 (예: "BPROJM")</li>
 * <li>{@code orcPkVl}: 원본 테이블의 PK 값 (예: 프로젝트관리번호)</li>
 * <li>{@code orcSnoVl}: 원본 테이블의 SNO 값 (예: 프로젝트순번)</li>
 * </ul>
 *
 * <p>
 * {@code @Transactional(readOnly = true)}: 조회 메서드의 기본값.
 * 쓰기 메서드는 {@code @Transactional}로 오버라이드합니다.
 * </p>
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 기본 읽기 전용 트랜잭션
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    /** 신청서 마스터 데이터 접근 리포지토리 (TAAABB_CAPPLM) */
    private final ApplicationRepository applicationRepository;

    /** 결재(승인) 데이터 접근 리포지토리 (TAAABB_CDECIM) */
    private final ApproverRepository approverRepository;

    /** 신청서-원본 데이터 연결 리포지토리 (TAAABB_CAPPLA) */
    private final ApplicationMapRepository applicationMapRepository;

    /** JSON 직렬화/역직렬화를 위한 Jackson ObjectMapper */
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 정보화사업(Bprojm) 리포지토리: 미상신 건수 집계용 */
    private final ProjectRepository projectRepository;

    /** 전산업무비(Bcostm) 리포지토리: 미상신 건수 집계용 */
    private final CostRepository costRepository;
    /** 결재 완료/반려 시 도메인 이벤트 발행 (도메인 간 직접 의존 제거) */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 신청서 상세 내용(JSON)의 결재선 정보 업데이트
     *
     * <p>
     * 신청서 본문({@code APF_DTL_CONE})에 저장된 JSON 내의 {@code approvalLine} 객체를 탐색하여
     * 승인된 결재자 항목에 결재 일자({@code date})를 현재 날짜로 기록합니다.
     * </p>
     *
     * <p>
     * 동일 결재자(ID)가 결재선에 여러 번 등장할 수 있으므로, 등장 순서(Occurrence Index)를
     * 추적하여 정확한 위치만 업데이트합니다.
     * </p>
     *
     * <p>
     * 처리 흐름:
     * </p>
     *
     * <pre>
     *   1. allApprovers 전체 순회 → 각 사원번호별 등장 횟수(Occurrence) 계산
     *      → approvedItems에 포함된 항목의 Occurrence를 targetOccurrences에 저장
     *   2. JSON의 approvalLine 필드 순회
     *      → id 필드로 사원번호 매칭, JSON 내 등장 횟수 추적
     *      → targetOccurrences와 일치하는 노드에 date 필드 기록
     *   3. 변경된 경우 JSON을 문자열로 직렬화하여 엔티티에 반영
     * </pre>
     *
     * @param capplm        결재 처리 중인 신청서 마스터 엔티티
     * @param allApprovers  해당 신청서의 전체 결재자 목록 (순번 오름차순)
     * @param approvedItems 이번에 승인된 결재 항목 목록 (동일인 연속 승인 포함)
     */
    @Transactional
    private void updateApprovalLineInDetail(Capplm capplm, List<Cdecim> allApprovers, List<Cdecim> approvedItems) {
        String detailJson = capplm.getApfDtlCone(); // 신청서 상세 내용 JSON 문자열
        if (detailJson == null || detailJson.isEmpty()) {
            return; // JSON이 없으면 업데이트 불필요
        }

        try {
            // JSON 파싱: 루트 노드 → approvalLine 객체 추출
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(detailJson);
            com.fasterxml.jackson.databind.JsonNode approvalLineNode = rootNode.path("approvalLine");

            if (approvalLineNode.isMissingNode() || !approvalLineNode.isObject()) {
                return; // approvalLine 필드가 없거나 객체가 아니면 처리 불필요
            }

            // ===== 1단계: Target Occurrences 계산 =====
            // 각 사원번호(ID)별로 승인된 항목이 몇 번째 등장인지(Occurrence Index)를 저장
            // Map<사원번호, Set<등장순서>>
            java.util.Map<String, java.util.Set<Integer>> targetOccurrences = new java.util.HashMap<>();
            // 전체 결재자 목록에서 각 사원번호의 현재까지 등장 횟수를 추적
            java.util.Map<String, Integer> globalOccurrenceCounters = new java.util.HashMap<>();

            // 전체 결재자 목록을 순회하며 각 항목의 등장 순서를 계산
            for (Cdecim approver : allApprovers) {
                String eno = approver.getDcdEno(); // 결재자 사원번호
                // 이 사원번호의 현재 등장 횟수 (1부터 시작)
                int currentOccurrence = globalOccurrenceCounters.getOrDefault(eno, 0) + 1;
                globalOccurrenceCounters.put(eno, currentOccurrence);

                // 현재 항목이 승인 대상 목록에 포함되어 있다면 Target Occurrences에 추가
                // dcdSqn(결재순번)은 신청서 내에서 유니크하다고 가정
                boolean isApprovedItem = approvedItems.stream()
                        .anyMatch(item -> item.getDcdSqn().equals(approver.getDcdSqn()));

                if (isApprovedItem) {
                    // 해당 사원번호의 이번 등장 순서를 타겟으로 등록
                    targetOccurrences.computeIfAbsent(eno, k -> new java.util.HashSet<>()).add(currentOccurrence);
                }
            }

            boolean updated = false; // JSON 변경 여부 플래그
            // JSON 순회 시 각 사원번호의 등장 횟수를 추적
            java.util.Map<String, Integer> jsonOccurrenceCounters = new java.util.HashMap<>();

            // ===== 2단계: JSON approvalLine 순회 및 date 필드 업데이트 =====
            java.util.Iterator<String> fieldNames = approvalLineNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                com.fasterxml.jackson.databind.JsonNode approverNode = approvalLineNode.get(fieldName);

                if (approverNode != null && approverNode.isObject() && approverNode.has("id")) {
                    String id = approverNode.get("id").asText(); // JSON에서 사원번호(id) 추출

                    // JSON 내에서의 해당 ID 등장 횟수 카운트
                    int currentJsonOccurrence = jsonOccurrenceCounters.getOrDefault(id, 0) + 1;
                    jsonOccurrenceCounters.put(id, currentJsonOccurrence);

                    // targetOccurrences와 일치하는 노드에 결재 일자 기록
                    if (targetOccurrences.containsKey(id)
                            && targetOccurrences.get(id).contains(currentJsonOccurrence)) {
                        if (approverNode instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                            // date 필드에 현재 날짜·시각을 ISO 형식("yyyy-MM-dd'T'HH:mm:ss")으로 기록
                            // 프론트엔드 splitDateTime()이 'T' 구분자로 날짜/시간을 분리합니다.
                            ((com.fasterxml.jackson.databind.node.ObjectNode) approverNode)
                                    .put("date", java.time.LocalDateTime.now()
                                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                            updated = true;
                        }
                    }
                }
            }

            // 변경된 경우에만 JSON 재직렬화 및 엔티티 업데이트
            if (updated) {
                String updatedJson = objectMapper.writeValueAsString(rootNode);
                capplm.updateDetailContent(updatedJson); // 신청서 상세 내용 갱신
            }

        } catch (Exception e) {
            // JSON 파싱 실패 시 비즈니스 로직 중단을 막기 위해 예외를 삼키고 경고 로그만 출력
            log.warn("신청서 상세 내용(JSON) 결재선 업데이트 실패 - 신청관리번호: {}", capplm.getApfMngNo(), e);
        }
    }

    /**
     * 신청서 등록 (결재 요청)
     *
     * <p>
     * 신청서 마스터({@link Capplm})를 생성하고, 원본 데이터 연결({@link Cappla})
     * 및 결재선({@link Cdecim})을 함께 저장합니다.
     * </p>
     *
     * <p>
     * 신청관리번호 생성 규칙: {@code APF_{yyyy}{시퀀스8자리}}
     * </p>
     * <p>
     * 예: {@code APF_202600000001}
     * </p>
     *
     * <p>
     * 처리 순서:
     * </p>
     * <ol>
     * <li>Oracle 시퀀스로 신청관리번호 채번</li>
     * <li>신청서 마스터 저장 (상태: "결재중")</li>
     * <li>원본 데이터 연결 저장 (orcTbCd가 있는 경우)</li>
     * <li>결재선 생성 (승인자 순서대로, 마지막 승인자는 lstDcdYn='Y')</li>
     * </ol>
     *
     * @param request 신청서 생성 요청 DTO (신청서명, 세부내용, 신청자, 결재자 목록 등)
     * @return 생성된 신청관리번호 (예: "APF_202600000001")
     */
    @Transactional
    public String submit(ApplicationDto.CreateRequest request) {

        // Oracle 시퀀스로 채번하여 신청관리번호 생성 (APF_{yyyy}{seq:08d})
        Long capplmSeq = applicationRepository.getNextVal();
        String apfMngNo = "APF_" + String.valueOf(java.time.LocalDate.now().getYear())
                + String.format("%08d", capplmSeq);

        // 1. 신청서 마스터 생성 (초기 상태: "결재중")
        Capplm capplm = Capplm.builder()
                .apfMngNo(apfMngNo) // 신청관리번호 (PK)
                .apfNm(request.getApfNm()) // 신청서명
                .apfDtlCone(request.getApfDtlCone()) // 신청서세부내용 (JSON)
                .apfSts("결재중") // 초기 결재상태
                .rqsEno(request.getRqsEno()) // 신청자 사원번호
                .rqsDt(LocalDate.now()) // 신청일자 = 오늘
                .rqsOpnn(request.getRqsOpnn()) // 신청의견
                .build();
        applicationRepository.save(capplm);

        // 1-1. 원본 데이터 연결 저장 (orcItems 각각에 대해 Cappla 생성)
        // 하나의 신청서가 복수의 원본 레코드(정보화사업, 전산관리비 등)를 연결할 수 있습니다.
        if (request.getOrcItems() != null && !request.getOrcItems().isEmpty()) {
            for (ApplicationDto.OrcItem item : request.getOrcItems()) {
                Long seq = applicationMapRepository.getNextVal(); // 항목마다 CAPPLA 시퀀스 채번
                String apfRelSno = "APPL_" + String.format("%028d", seq); // 신청서관계일련번호

                Cappla cappla = Cappla.builder()
                        .apfRelSno(apfRelSno) // 신청서관계일련번호 (PK)
                        .apfMngNo(apfMngNo) // 신청관리번호 (FK)
                        .orcTbCd(item.getOrcTbCd()) // 원본 테이블코드
                        .orcPkVl(item.getOrcPkVl()) // 원본 PK값
                        .orcSnoVl(item.getOrcSnoVl() != null ? Integer.parseInt(item.getOrcSnoVl()) : null) // 원본 SNO
                        .build();
                applicationMapRepository.save(cappla);
            }
        }

        // 2. 결재선 생성: 요청받은 결재자 사번 목록을 순번(dcdSqn)대로 저장
        List<String> approverEnos = request.getApproverEnos();
        List<Cdecim> savedApprovers = new java.util.ArrayList<>();

        for (int i = 0; i < approverEnos.size(); i++) {
            Cdecim cdecim = Cdecim.builder()
                    .dcdMngNo(apfMngNo) // 결재관리번호 (FK)
                    .dcdSqn(i + 1) // 결재순번 (1부터 시작)
                    .dcdEno(approverEnos.get(i)) // 결재자 사원번호
                    .lstDcdYn(i == approverEnos.size() - 1 ? "Y" : "N") // 마지막 결재자 여부
                    .build();
            approverRepository.save(cdecim);
            savedApprovers.add(cdecim);
        }

        // 3. 기안자 == 1차 결재자(팀장) 자동 승인 처리
        //    기안자와 첫 번째 결재자가 동일한 경우, 신청 행위 자체를 묵시적 1차 승인으로 간주합니다.
        //    - Cdecim 레코드를 즉시 승인 상태로 전환하여 이후 2차 결재자(부서장)가 바로 결재 가능하게 합니다.
        //    - updateApprovalLineInDetail()을 호출하여 JSON 결재선의 팀장 date 필드도 함께 기록합니다.
        //      (이 처리가 없으면 JSON date가 빈 문자열로 남아 PDF 상 팀장 결재 시각이 누락됩니다.)
        if (!approverEnos.isEmpty() && approverEnos.get(0).equals(request.getRqsEno())) {
            Cdecim firstApprover = savedApprovers.get(0);
            firstApprover.approve("기안자 자동 승인", "승인");
            approverRepository.save(firstApprover);
            updateApprovalLineInDetail(capplm, savedApprovers, java.util.List.of(firstApprover));
        }

        return apfMngNo; // 생성된 신청관리번호 반환
    }

    /**
     * 결재 처리 (승인 또는 반려)
     *
     * <p>
     * 순차 결재 방식으로, 이전 결재자가 모두 승인한 경우에만 다음 결재자가 결재할 수 있습니다.
     * 동일 결재자가 연속으로 등장한 경우 한 번의 요청으로 연속 항목 모두 승인합니다.
     * </p>
     *
     * <p>
     * 처리 흐름:
     * </p>
     * <ol>
     * <li>신청서 존재 확인</li>
     * <li>전체 결재자 목록 조회 (순번 오름차순)</li>
     * <li>현재 결재 차례(미결재, 이전 모두 승인) 탐색</li>
     * <li>요청자가 현재 결재자인지 확인</li>
     * <li>결재 상태 저장 (승인/반려)</li>
     * <li>동일 결재자 연속 등장 시 일괄 승인</li>
     * <li>JSON 결재선 정보 업데이트</li>
     * <li>반려: 신청서 상태 → "반려" / 마지막 승인: 신청서 상태 → "결재완료"</li>
     * </ol>
     *
     * @param apfMngNo 결재할 신청관리번호
     * @param request  결재 요청 DTO (결재자 사번, 의견, 승인/반려 상태)
     * @throws IllegalArgumentException 신청서가 없거나 결재자가 아닌 경우
     * @throws IllegalStateException    결재 차례가 아닌 경우
     */
    @Transactional
    public void approve(String apfMngNo, ApplicationDto.ApproveRequest request) {
        // 신청서 마스터 조회 (없으면 예외)
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));

        // 해당 신청서의 전체 결재자 목록 조회 (순번 오름차순)
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcdSqnAsc(apfMngNo);

        // ===== 현재 결재 차례 탐색 =====
        Cdecim currentApprover = null; // 현재 결재해야 할 결재자
        boolean isPreviousApproved = true; // 이전 결재자가 모두 승인했는지 여부

        for (Cdecim approver : approvers) {
            String dcdTp = approver.getDcdTp(); // 결재유형 (null이면 미결재)
            String dcdSts = approver.getDcdSts(); // 결재상태

            if (dcdTp == null) {
                // 아직 결재하지 않은 항목: 이전이 모두 승인되었을 때만 현재 차례
                if (isPreviousApproved) {
                    currentApprover = approver;
                }
                break; // 미결재 항목 발견 시 탐색 종료
            } else if ("결재".equals(dcdTp) && !"승인".equals(dcdSts)) {
                isPreviousApproved = false; // 이전 결재자가 반려 또는 기타 상태
                break;
            }
        }

        // 결재 차례가 아닌 경우 (이미 완료되었거나 순서 불일치)
        if (currentApprover == null) {
            throw new IllegalStateException("결재할 차례가 아니거나 이미 모든 결재가 완료되었습니다.");
        }

        // 요청한 결재자가 현재 차례의 결재자인지 확인
        if (!currentApprover.getDcdEno().equals(request.getDcdEno())) {
            throw new IllegalArgumentException("현재 결재자가 아닙니다.");
        }

        // 결재 상태 유효성 검증 (승인 또는 반려만 허용)
        String status = request.getDcdSts();
        if (status == null || status.isEmpty()) {
            throw new IllegalArgumentException("결재 상태(승인/반려)는 필수입니다.");
        }

        // 현재 결재자의 결재 처리 및 저장
        currentApprover.approve(request.getDcdOpnn(), status);
        approverRepository.save(currentApprover);

        // ===== 연속된 동일 결재자 일괄 승인 처리 =====
        // 예: [A, A, B] 결재선에서 A가 승인하면 두 A 항목 모두 승인
        Cdecim lastApproved = currentApprover;
        List<Cdecim> approvedList = new java.util.ArrayList<>();
        approvedList.add(currentApprover);

        if ("승인".equals(status)) {
            int currentIndex = approvers.indexOf(currentApprover);
            for (int i = currentIndex + 1; i < approvers.size(); i++) {
                Cdecim nextApprover = approvers.get(i);
                if (nextApprover.getDcdEno().equals(currentApprover.getDcdEno())) {
                    // 같은 결재자가 연속으로 등장하면 자동 승인
                    nextApprover.approve(request.getDcdOpnn(), status);
                    approverRepository.save(nextApprover);
                    lastApproved = nextApprover;
                    approvedList.add(nextApprover);
                } else {
                    break; // 다른 결재자 만나면 일괄 승인 종료
                }
            }
        }

        // 신청서 상세 내용(JSON) 내 결재선 정보 업데이트 (결재 일자 기록)
        updateApprovalLineInDetail(capplm, approvers, approvedList);

        // 신청서 전체 상태 업데이트
        String newApfSts = null;
        if ("반려".equals(status)) {
            // 반려인 경우 신청서 상태도 "반려"로 변경
            capplm.updateStatus("반려");
            newApfSts = "반려";
        } else if ("승인".equals(status)) {
            // 마지막 결재자(lstDcdYn='Y')가 승인한 경우 "결재완료"로 변경
            if ("Y".equals(lastApproved.getLstDcdYn())) {
                capplm.updateStatus("결재완료");
                newApfSts = "결재완료";
            }
        }

        // 신청서 상태가 종결(결재완료/반려)된 경우, 도메인 이벤트 발행
        // 구독 리스너(예: CouncilApprovalEventListener)가 도메인별 후처리를 담당합니다.
        if (newApfSts != null) {
            eventPublisher.publishEvent(new ApprovalCompletedEvent(apfMngNo, newApfSts));
        }
    }

    /**
     * 일괄 결재 (여러 신청서를 하나의 트랜잭션으로 처리)
     *
     * <p>
     * 복수의 신청서에 대해 순차적으로 {@link #approve(String, ApplicationDto.ApproveRequest)}를
     * 호출합니다. 하나라도 실패하면 전체 트랜잭션이 롤백됩니다.
     * </p>
     *
     * <p>
     * 주의: 예외 발생 시 {@link RuntimeException}을 다시 던져 트랜잭션 롤백을 유발합니다.
     * </p>
     *
     * @param request 일괄 결재 요청 DTO (처리할 신청서 목록)
     * @return 일괄 결재 결과 DTO (전체/성공/실패 건수, 개별 결과 목록)
     * @throws RuntimeException 개별 신청서 처리 실패 시 (전체 롤백)
     */
    @Transactional
    public ApplicationDto.BulkApproveResponse bulkApprove(ApplicationDto.BulkApproveRequest request) {
        List<ApplicationDto.ApprovalResult> results = new java.util.ArrayList<>(); // 개별 결과 목록
        int successCount = 0; // 성공 건수
        int failureCount = 0; // 실패 건수

        // 모든 신청서를 순회하며 승인 처리
        for (ApplicationDto.ApprovalItem item : request.getApprovals()) {
            try {
                // 개별 승인 요청 생성 (ApprovalItem → ApproveRequest 변환)
                ApplicationDto.ApproveRequest approveRequest = new ApplicationDto.ApproveRequest();
                approveRequest.setDcdEno(item.getDcdEno()); // 승인자 사원번호
                approveRequest.setDcdOpnn(item.getDcdOpnn()); // 승인 의견
                approveRequest.setDcdSts(item.getDcdSts()); // 승인 상태 (승인, 반려)

                // 개별 승인 처리
                approve(item.getApfMngNo(), approveRequest);

                // 성공 결과 추가
                results.add(ApplicationDto.ApprovalResult.builder()
                        .apfMngNo(item.getApfMngNo())
                        .success(true)
                        .message("처리 완료")
                        .build());
                successCount++;

            } catch (Exception e) {
                // 실패 시 RuntimeException을 던져 전체 트랜잭션 롤백
                throw new RuntimeException("신청서 " + item.getApfMngNo() + " 처리 실패: " + e.getMessage(), e);
            }
        }

        // 최종 결과 응답 생성
        return ApplicationDto.BulkApproveResponse.builder()
                .totalCount(request.getApprovals().size()) // 전체 요청 건수
                .successCount(successCount) // 성공 건수
                .failureCount(failureCount) // 실패 건수 (롤백 시 항상 0)
                .results(results) // 개별 결과 목록
                .build();
    }

    /**
     * 신청서 세부내용(APF_DTL_CONE) 단건 조회
     *
     * <p>
     * 신청관리번호로 신청서 마스터를 조회하여 세부내용({@code APF_DTL_CONE}) 필드만 반환합니다.
     * </p>
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청관리번호와 세부내용을 담은 응답 DTO ({@link ApplicationDto.ApfDtlConeResponse})
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     */
    public ApplicationDto.ApfDtlConeResponse getApfDtlCone(String apfMngNo) {
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        return ApplicationDto.ApfDtlConeResponse.fromEntity(capplm);
    }

    /**
     * 단건 신청서 조회
     *
     * <p>
     * 신청관리번호로 신청서 마스터와 결재자 목록을 조회하여 DTO로 반환합니다.
     * </p>
     *
     * @param apfMngNo 조회할 신청관리번호
     * @return 신청서 상세 응답 DTO (결재자 목록 포함)
     * @throws IllegalArgumentException 해당 신청관리번호의 신청서가 없는 경우
     */
    public ApplicationDto.Response getApplication(String apfMngNo) {
        // 신청서 마스터 조회
        Capplm capplm = applicationRepository.findById(apfMngNo)
                .orElseThrow(() -> new IllegalArgumentException("신청서를 찾을 수 없습니다: " + apfMngNo));
        // 결재자 목록 조회 (순번 오름차순)
        List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcdSqnAsc(apfMngNo);
        return ApplicationDto.Response.fromEntity(capplm, approvers);
    }

    /**
     * 전체 신청서 목록 조회
     *
     * <p>
     * DB의 모든 신청서를 조회하고, 각 신청서의 결재자 목록을 포함하여 반환합니다.
     * </p>
     *
     * @return 전체 신청서 응답 DTO 목록 (각각 결재자 목록 포함)
     */
    public List<ApplicationDto.Response> getApplications() {
        return applicationRepository.findAll().stream()
                .map(capplm -> {
                    // 각 신청서의 결재자 목록을 별도 조회하여 DTO에 포함
                    List<Cdecim> approvers = approverRepository.findByDcdMngNoOrderByDcdSqnAsc(capplm.getApfMngNo());
                    return ApplicationDto.Response.fromEntity(capplm, approvers);
                })
                .toList();
    }

    /**
     * 일괄 조회 (여러 신청관리번호로 한 번에 조회)
     *
     * <p>
     * 요청 목록의 각 신청관리번호에 대해 {@link #getApplication(String)}을 호출합니다.
     * 존재하지 않는 신청서는 결과에서 제외합니다 (null 필터링).
     * </p>
     *
     * @param request 일괄 조회 요청 DTO (신청관리번호 목록)
     * @return 존재하는 신청서의 응답 DTO 목록 (없는 항목 제외)
     */
    public List<ApplicationDto.Response> getApplicationsByIds(ApplicationDto.BulkGetRequest request) {
        return request.getApfMngNos().stream()
                .map(apfMngNo -> {
                    try {
                        return getApplication(apfMngNo); // 개별 신청서 조회
                    } catch (IllegalArgumentException e) {
                        return null; // 존재하지 않는 신청서는 null로 처리
                    }
                })
                .filter(response -> response != null) // null 제거 (존재하지 않는 항목 제외)
                .toList();
    }

    /**
     * 전자결재 대시보드 집계 조회
     *
     * <p>bbrC 기준 부서 통계와 eno 기준 본인 결재 대기 목록을 반환합니다.</p>
     *
     * @param bbrC 부서코드 (TAAABB_CUSERI.BBR_C)
     * @param eno  사원번호 (본인 결재 대기 필터)
     * @return 대시보드 집계 응답 DTO
     */
    public ApplicationDto.DashboardResponse getDashboard(String bbrC, String eno) {
        int pendingCount          = applicationRepository.countPendingByEno(eno);
        int inProgressCount       = applicationRepository.countInProgressByEno(eno);
        int monthlyCompletedCount = applicationRepository.countMonthlyCompletedByBbrC(bbrC);
        int rejectedCount         = applicationRepository.countRejectedByEno(eno);

        List<ApplicationDto.MonthlyCount> monthlyTrend =
            applicationRepository.findMonthlyTrendByBbrC(bbrC).stream()
                .map(row -> ApplicationDto.MonthlyCount.builder()
                    .month((String) row[0])
                    .count(((Number) row[1]).intValue())
                    .build())
                .toList();

        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        List<ApplicationDto.PendingItem> pendingList =
            applicationRepository.findPendingListByEno(eno).stream()
                .map(row -> {
                    String rqsDtStr = (String) row[3];
                    LocalDate rqsDt = rqsDtStr != null ? LocalDate.parse(rqsDtStr) : LocalDate.now();
                    String urgency = rqsDt.isBefore(threeDaysAgo) ? "urgent" : "normal";
                    return ApplicationDto.PendingItem.builder()
                        .apfMngNo((String) row[0])
                        .title((String) row[1])
                        .requesterName((String) row[2])
                        .requestedAt(rqsDtStr)
                        .urgency(urgency)
                        .build();
                })
                .toList();

        return ApplicationDto.DashboardResponse.builder()
            .pendingCount(pendingCount)
            .inProgressCount(inProgressCount)
            .monthlyCompletedCount(monthlyCompletedCount)
            .rejectedCount(rejectedCount)
            .monthlyTrend(monthlyTrend)
            .pendingList(pendingList)
            .build();
    }

    /**
     * 사이드바 배지용 결재 현황 수 조회
     *
     * @param bbrC 부서코드 (향후 부서 기준 집계 확장용, 현재 미사용)
     * @param eno  사원번호
     * @return 배지 건수 응답 DTO
     */
    public ApplicationDto.ApprovalBadgeCountResponse getApprovalBadgeCount(String bbrC, String eno) {
        return ApplicationDto.ApprovalBadgeCountResponse.builder()
            .pendingCount(applicationRepository.countPendingByEno(eno))
            .inProgressCount(applicationRepository.countInProgressByEno(eno))
            .build();
    }

    /**
     * 미상신(결재 신청 이력 없음) 건수 집계
     *
     * <p>
     * 사이드바의 [결재 상신] 메뉴 옆 배지에서 사용됩니다.
     * 전체 목록 대신 건수만 반환하여 데이터 전송량을 최소화합니다.
     * </p>
     *
     * <p>
     * 집계 로직: {@code apfSts='none'} 조건으로 {@code ProjectRepository} 및
     * {@code CostRepository}의 {@code searchByCondition}을 호출하여 각각의 건수를 계산합니다.
     * (CAPPLA 연결이 없는 BPROJM/BCOSTM 레코드 = 아직 결재 상신되지 않은 항목)
     * </p>
     *
     * @return 미상신 건수 응답 DTO (정보화사업/전산업무비 개별 건수 + 총합)
     */
    public ApplicationDto.PendingCountResponse getPendingCount() {
        // 미상신 정보화사업 건수: CAPPLA에 연결되지 않은 BPROJM
        ProjectDto.SearchCondition projectCondition = new ProjectDto.SearchCondition();
        projectCondition.setApfSts("none");
        long projectCount = projectRepository.searchByCondition(projectCondition).size();

        // 미상신 전산업무비 건수: CAPPLA에 연결되지 않은 BCOSTM
        CostDto.SearchCondition costCondition = new CostDto.SearchCondition();
        costCondition.setApfSts("none");
        long costCount = costRepository.searchByCondition(costCondition).size();

        return ApplicationDto.PendingCountResponse.builder()
                .projectCount(projectCount)
                .costCount(costCount)
                .totalCount(projectCount + costCount)
                .build();
    }
}
