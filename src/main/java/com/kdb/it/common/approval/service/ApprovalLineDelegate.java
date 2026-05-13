package com.kdb.it.common.approval.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.exception.CustomGeneralException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재선 JSON 업데이트 위임 서비스
 *
 * <p>{@code ApplicationService.updateApprovalLineInDetail}이 private @Transactional로 선언되어
 * Spring AOP 프록시를 우회하던 문제를 해결하기 위해 별도 빈으로 분리합니다.
 * 실패 시 예외를 재발생시켜 트랜잭션 롤백을 보장합니다.</p>
 */
@Service
@RequiredArgsConstructor
public class ApprovalLineDelegate {

    private static final Logger log = LoggerFactory.getLogger(ApprovalLineDelegate.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;

    /**
     * 신청서 상세 JSON 내 결재선 정보를 업데이트합니다.
     *
     * <p>JSON 파싱 실패 등 예외 발생 시 {@code CustomGeneralException}으로 재발생시켜
     * 호출 트랜잭션이 롤백되도록 합니다.</p>
     *
     * @param capplm        결재 처리 중인 신청서 마스터 엔티티
     * @param allApprovers  해당 신청서의 전체 결재자 목록
     * @param approvedItems 이번에 승인된 결재 항목 목록
     * @throws CustomGeneralException JSON 파싱·직렬화 실패 시
     */
    @Transactional
    public void doUpdate(Capplm capplm, List<Cdecim> allApprovers, List<Cdecim> approvedItems) {
        String detailJson = capplm.getApfDtlCone();
        if (detailJson == null || detailJson.isEmpty()) {
            return;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(detailJson);
            JsonNode approvalLineNode = rootNode.path("approvalLine");

            if (approvalLineNode.isMissingNode() || !approvalLineNode.isObject()) {
                return;
            }

            Map<String, Set<Integer>> targetOccurrences = buildTargetOccurrences(allApprovers, approvedItems);

            boolean updated = applyDateToMatchingNodes(approvalLineNode, targetOccurrences);

            if (updated) {
                capplm.updateDetailContent(objectMapper.writeValueAsString(rootNode));
            }

        } catch (Exception e) {
            log.error("결재선 JSON 업데이트 실패 - 신청관리번호: {}", capplm.getApfMngNo(), e);
            throw new CustomGeneralException("결재선 JSON 업데이트 실패: " + capplm.getApfMngNo(), e);
        }
    }

    private Map<String, Set<Integer>> buildTargetOccurrences(
            List<Cdecim> allApprovers, List<Cdecim> approvedItems) {
        Map<String, Set<Integer>> targetOccurrences = new HashMap<>();
        Map<String, Integer> globalCounters = new HashMap<>();

        for (Cdecim approver : allApprovers) {
            String eno = approver.getDcdEno();
            int occurrence = globalCounters.getOrDefault(eno, 0) + 1;
            globalCounters.put(eno, occurrence);

            boolean isApproved = approvedItems.stream()
                    .anyMatch(item -> item.getDcdSqn().equals(approver.getDcdSqn()));
            if (isApproved) {
                targetOccurrences.computeIfAbsent(eno, k -> new HashSet<>()).add(occurrence);
            }
        }
        return targetOccurrences;
    }

    private boolean applyDateToMatchingNodes(JsonNode approvalLineNode,
                                             Map<String, Set<Integer>> targetOccurrences) {
        Map<String, Integer> jsonCounters = new HashMap<>();
        boolean updated = false;

        Iterator<String> fieldNames = approvalLineNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode approverNode = approvalLineNode.get(fieldName);
            if (approverNode == null || !approverNode.isObject() || !approverNode.has("id")) {
                continue;
            }
            // 기안자(drafter)는 Cdecim 결재자 레코드가 아니므로 occurrence 카운팅에서 제외.
            // 포함하면 기안자==결재자인 자동결재 시 JSON occurrence와 Cdecim occurrence 간 1 차이가 발생.
            if ("drafter".equals(fieldName)) {
                continue;
            }
            String id = approverNode.get("id").asText();
            int jsonOccurrence = jsonCounters.getOrDefault(id, 0) + 1;
            jsonCounters.put(id, jsonOccurrence);

            Set<Integer> targets = targetOccurrences.get(id);
            if (targets != null && targets.contains(jsonOccurrence)
                    && approverNode instanceof ObjectNode on) {
                on.put("date", LocalDateTime.now().format(DATE_FMT));
                updated = true;
            }
        }
        return updated;
    }
}
