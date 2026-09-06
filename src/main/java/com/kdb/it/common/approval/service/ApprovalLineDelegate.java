package com.kdb.it.common.approval.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.DecisionStatus;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.ApproverRole;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader.ParsedSnapshot;
import com.kdb.it.common.approval.service.ApprovalDetailPolicy.DetailMode;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.exception.DataCorruptionException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 저장 문서를 검증한 뒤 결재선 상태만 변경한다. 손상 문서는 예외로 호출 트랜잭션을 롤백한다. */
@Service
@RequiredArgsConstructor
public class ApprovalLineDelegate {
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final ItBudgetSnapshotReader snapshotReader;

    /**
     * 승인된 CDECIM 순번에 해당하는 결재일을 기록한다.
     *
     * @throws DataCorruptionException 문서 무결성 또는 필요한 결재선·대상자가 손상된 경우
     */
    @Transactional
    public void doUpdate(Capplm capplm, List<Cdecim> allApprovers, List<Cdecim> approvedItems) {
        boolean required =
                inProgress(capplm) || !allApprovers.isEmpty() || !approvedItems.isEmpty();
        ParsedSnapshot parsed = read(capplm, required);
        if (parsed == null) return;
        ObjectNode line = line(parsed, required);
        if (line == null) return;
        Map<String, Set<Integer>> targets = buildTargetOccurrences(allApprovers, approvedItems);
        if (parsed.version() == 2) {
            List<JsonNode> nodes = new ArrayList<>();
            line.get("approvers").forEach(nodes::add);
            validateTargets(nodes, targets, "eno");
            Map<String, Integer> occurrences = new HashMap<>();
            for (JsonNode node : nodes) {
                String eno = node.get("eno").textValue();
                int occurrence = occurrences.merge(eno, 1, Integer::sum);
                if (targets.getOrDefault(eno, Set.of()).contains(occurrence))
                    ((ObjectNode) node).put("date", LocalDate.now().toString());
            }
            capplm.updateDetailContent(parsed.write());
        } else {
            if (required) {
                List<ObjectNode> nodes = fixedApproverNodes(line);
                if (line.get("additionalApprovers") instanceof ArrayNode additions)
                    nodes.addAll(additionalApproverNodes(additions));
                validateTargets(new ArrayList<>(nodes), targets, "id");
            }
            if (applyDateToMatchingNodes(line, targets)) capplm.updateDetailContent(parsed.write());
        }
    }

    /** 회수 정보를 기록한다. v2 payload 무결성 실패는 상태 변경을 차단한다. */
    @Transactional
    public void applyRecallInfo(
            Capplm capplm, String recallerEno, String recallOpnn, DetailMode detailMode) {
        String raw = capplm.getDcdReqInf();
        ParsedSnapshot parsed = snapshotReader.read(raw == null || raw.isBlank() ? "{}" : raw);
        if (inProgress(capplm) && !(raw == null && detailMode == DetailMode.JSONLESS_COUNCIL))
            line(parsed, true);
        parsed.recall(recallerEno, recallOpnn);
        capplm.updateDetailContent(parsed.write());
    }

    /** IAM에서 해석한 결재자 정보를 뒤에 추가한다. v2의 필수 표시 값 누락은 데이터 오류다. */
    @Transactional
    public void addApproverToDetail(Capplm capplm, String eno, String name, String rank) {
        ParsedSnapshot parsed = read(capplm, inProgress(capplm));
        if (parsed == null) return;
        ObjectNode line = line(parsed, inProgress(capplm));
        if (line == null) return;
        if (parsed.version() == 2) {
            ((ArrayNode) line.get("approvers"))
                    .add(v2Approver(eno, name, rank, ApproverRole.ADDITIONAL));
        } else {
            ObjectNode approver = objectMapper.createObjectNode();
            approver.put("name", name == null ? "" : name);
            approver.put("rank", rank == null ? "" : rank);
            approver.put("date", "");
            approver.put("id", eno);
            line.withArray("additionalApprovers").add(approver);
            if (line.get("order") instanceof ArrayNode order) order.add(eno);
        }
        capplm.updateDetailContent(parsed.write());
    }

    /** CDECIM 전체 결재선의 인덱스로 v1 정적·추가 노드 또는 v2 결재자를 삭제한다. */
    @Transactional
    public void removeApproverFromDetail(Capplm capplm, int orderedIndex) {
        ParsedSnapshot parsed = read(capplm, inProgress(capplm));
        if (parsed == null) return;
        ObjectNode line = line(parsed, inProgress(capplm));
        if (line == null) return;
        if (parsed.version() == 1) {
            List<ObjectNode> nodes = legacyNodesInStoredOrder(line);
            if (orderedIndex < 0 || orderedIndex >= nodes.size()) {
                if (inProgress(capplm)) throw new DataCorruptionException("삭제할 결재선 항목이 없습니다.");
                return;
            }
            ObjectNode removed = nodes.remove(orderedIndex);
            removeLegacyNode(line, removed);
            if (line.has("order"))
                setLegacyOrder(line, nodes.stream().map(this::approverId).toList());
            capplm.updateDetailContent(parsed.write());
            return;
        }
        JsonNode additions = line.get("approvers");
        if (additions instanceof ArrayNode array
                && orderedIndex >= 0
                && orderedIndex < array.size()) {
            array.remove(orderedIndex);
            capplm.updateDetailContent(parsed.write());
        } else {
            throw new DataCorruptionException("삭제할 결재선 항목이 없습니다.");
        }
    }

    /** v2는 표시 정보·승인일을 포함한 노드를 재배치하며 v1은 기존 order 배열을 갱신한다. */
    @Transactional
    public void updateApprovalOrder(Capplm capplm, List<Cdecim> orderedApprovers) {
        ParsedSnapshot parsed = read(capplm, inProgress(capplm));
        if (parsed == null) return;
        ObjectNode line = line(parsed, inProgress(capplm));
        if (line == null) return;
        if (parsed.version() == 2) {
            Map<String, ArrayDeque<JsonNode>> byEno = new HashMap<>();
            for (JsonNode person : line.get("approvers"))
                byEno.computeIfAbsent(person.get("eno").textValue(), ignored -> new ArrayDeque<>())
                        .add(person);
            ArrayNode ordered = objectMapper.createArrayNode();
            for (Cdecim approver : orderedApprovers) {
                var candidates = byEno.get(approver.getDcrEno());
                if (candidates == null || candidates.isEmpty())
                    throw new DataCorruptionException("결재선 순서와 저장 사용자가 일치하지 않습니다.");
                ordered.add(candidates.removeFirst());
            }
            if (byEno.values().stream().anyMatch(values -> !values.isEmpty()))
                throw new DataCorruptionException("결재선 순서에서 사용자가 누락되었습니다.");
            line.set("approvers", ordered);
        } else {
            setLegacyOrder(line, orderedApprovers.stream().map(Cdecim::getDcrEno).toList());
        }
        capplm.updateDetailContent(parsed.write());
    }

    /**
     * 완료 결재자는 보존하고 미결재 구간은 IAM 사용자 정보로 교체한다.
     *
     * @throws DataCorruptionException 문서·완료 결재선·새 사용자 표시정보가 잘못된 경우
     */
    @Transactional
    public void replacePendingApproversInDetail(
            Capplm capplm, List<Cdecim> orderedApprovers, List<CuserI> replacementUsers) {
        ParsedSnapshot parsed = read(capplm, inProgress(capplm));
        if (parsed == null) return;
        ObjectNode lineObject = line(parsed, inProgress(capplm));
        if (lineObject == null) return;
        int completedCount = completedPrefixCount(orderedApprovers);
        if (parsed.version() == 2) {
            JsonNode current = lineObject.get("approvers");
            if (current.size() < completedCount
                    || replacementUsers.size() != orderedApprovers.size() - completedCount)
                throw new DataCorruptionException("교체할 결재선 구간이 올바르지 않습니다.");
            ArrayNode replaced = objectMapper.createArrayNode();
            for (int index = 0; index < completedCount; index++) {
                JsonNode node = current.get(index);
                if (!orderedApprovers.get(index).getDcrEno().equals(node.get("eno").textValue()))
                    throw new DataCorruptionException("완료 결재선 사용자가 일치하지 않습니다.");
                replaced.add(node);
            }
            for (int index = 0; index < replacementUsers.size(); index++) {
                CuserI user = replacementUsers.get(index);
                if (!user.getEno().equals(orderedApprovers.get(completedCount + index).getDcrEno()))
                    throw new DataCorruptionException("교체 결재자 정보가 일치하지 않습니다.");
                int slot = completedCount + index;
                ApproverRole role =
                        slot < current.size()
                                ? ApproverRole.valueOf(current.get(slot).get("role").textValue())
                                : ApproverRole.ADDITIONAL;
                replaced.add(v2Approver(user.getEno(), user.getUsrNm(), user.getPtCNm(), role));
            }
            lineObject.set("approvers", replaced);
        } else {
            List<ObjectNode> fixedApproverNodes = fixedApproverNodes(lineObject);
            ArrayNode additionalApprovers = lineObject.withArray("additionalApprovers");
            List<ObjectNode> additionalApproverNodes = additionalApproverNodes(additionalApprovers);
            List<ObjectNode> canonicalNodes = new ArrayList<>(fixedApproverNodes);
            canonicalNodes.addAll(additionalApproverNodes);
            List<ObjectNode> logicalNodes = nodesInStoredOrder(lineObject, canonicalNodes);
            if (logicalNodes.size() > orderedApprovers.size())
                logicalNodes = new ArrayList<>(logicalNodes.subList(0, orderedApprovers.size()));
            while (logicalNodes.size() < orderedApprovers.size()) {
                ObjectNode addedNode = objectMapper.createObjectNode();
                addedNode.put("date", "");
                additionalApprovers.add(addedNode);
                additionalApproverNodes.add(addedNode);
                logicalNodes.add(addedNode);
            }
            for (int index = completedCount; index < orderedApprovers.size(); index++)
                updateApproverNode(
                        logicalNodes.get(index), replacementUsers.get(index - completedCount));
            retainSelectedAdditionalApprovers(
                    additionalApprovers, additionalApproverNodes, logicalNodes);
            // order에 따라 선택된 노드만 남긴다. 완료 additional 앞/뒤 정적 노드도 객체 정체성으로 구별한다.
            Set<ObjectNode> selected = Collections.newSetFromMap(new IdentityHashMap<>());
            selected.addAll(logicalNodes);
            for (ObjectNode fixed : fixedApproverNodes)
                if (!selected.contains(fixed)) removeLegacyNode(lineObject, fixed);
            if (lineObject.has("order"))
                setLegacyOrder(
                        lineObject, orderedApprovers.stream().map(Cdecim::getDcrEno).toList());
        }
        capplm.updateDetailContent(parsed.write());
    }

    private ObjectNode v2Approver(String eno, String name, String rank, ApproverRole role) {
        ObjectNode approver = objectMapper.createObjectNode();
        approver.put("role", role.name());
        approver.put("eno", eno);
        approver.put("name", name);
        approver.put("rank", rank);
        approver.putNull("date");
        return approver;
    }

    private List<ObjectNode> legacyNodesInStoredOrder(ObjectNode line) {
        List<ObjectNode> nodes = fixedApproverNodes(line);
        if (line.get("additionalApprovers") instanceof ArrayNode additional)
            nodes.addAll(additionalApproverNodes(additional));
        return nodesInStoredOrder(line, nodes);
    }

    private void setLegacyOrder(ObjectNode line, List<String> enos) {
        ArrayNode order = objectMapper.createArrayNode();
        enos.forEach(order::add);
        line.set("order", order);
    }

    private void removeLegacyNode(ObjectNode line, ObjectNode target) {
        Iterator<String> names = line.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode value = line.get(name);
            if (value == target) {
                line.remove(name);
                return;
            }
            if (value instanceof ArrayNode array) {
                for (int index = 0; index < array.size(); index++) {
                    if (array.get(index) == target) {
                        array.remove(index);
                        return;
                    }
                }
            }
        }
        throw new DataCorruptionException("삭제할 결재선 노드가 없습니다.");
    }

    private ParsedSnapshot read(Capplm capplm, boolean required) {
        String raw = capplm.getDcdReqInf();
        if (!required && (raw == null || raw.isBlank())) return null;
        return snapshotReader.read(raw);
    }

    private static boolean inProgress(Capplm capplm) {
        return ApprovalStatus.IN_PROGRESS.code().equals(capplm.getItPtlApfPrgStsC());
    }

    private ObjectNode line(ParsedSnapshot parsed, boolean required) {
        ObjectNode line = parsed.approvalLine(required);
        if (line != null && required && parsed.version() == 1) validateLegacyLine(line);
        return line;
    }

    private void validateLegacyLine(ObjectNode line) {
        List<String> ids = new ArrayList<>();
        Iterator<String> fields = line.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            if ("drafter".equals(name) || "order".equals(name) || "caption".equals(name)) continue;
            JsonNode node = line.get(name);
            if (node.isArray()) {
                for (JsonNode person : node) ids.add(requiredLegacyId(person));
            } else {
                ids.add(requiredLegacyId(node));
            }
        }
        if (ids.isEmpty()) throw new DataCorruptionException("필수 결재선 사용자가 없습니다.");
        if (line.has("order")) {
            JsonNode order = line.get("order");
            List<String> remaining = new ArrayList<>(ids);
            if (!order.isArray()) throw new DataCorruptionException("결재 순서 형식이 올바르지 않습니다.");
            for (JsonNode id : order)
                if (!id.isTextual() || !remaining.remove(id.textValue()))
                    throw new DataCorruptionException("결재 순서 사용자가 일치하지 않습니다.");
            if (!remaining.isEmpty()) throw new DataCorruptionException("결재 순서에서 사용자가 누락되었습니다.");
        }
    }

    private String requiredLegacyId(JsonNode person) {
        if (!person.isObject()
                || !person.path("id").isTextual()
                || person.path("id").textValue().isBlank())
            throw new DataCorruptionException("필수 결재선 사용자 정보가 손상되었습니다.");
        return person.get("id").textValue();
    }

    private void validateTargets(
            List<JsonNode> nodes, Map<String, Set<Integer>> targets, String idField) {
        Map<String, Integer> counts = new HashMap<>();
        for (JsonNode node : nodes) counts.merge(node.path(idField).asText(), 1, Integer::sum);
        for (var target : targets.entrySet())
            for (int occurrence : target.getValue())
                if (counts.getOrDefault(target.getKey(), 0) < occurrence)
                    throw new DataCorruptionException("승인 대상 결재선 사용자가 없습니다.");
    }

    private int completedPrefixCount(List<Cdecim> orderedApprovers) {
        int count = 0;
        for (Cdecim orderedApprover : orderedApprovers) {
            if (DecisionStatus.isPendingCode(orderedApprover.getItPtlDcdStsC())) break;
            count++;
        }
        return count;
    }

    private List<ObjectNode> fixedApproverNodes(ObjectNode lineObject) {
        List<ObjectNode> nodes = new ArrayList<>();
        Iterator<String> fields = lineObject.fieldNames();
        while (fields.hasNext()) {
            String fieldName = fields.next();
            if ("drafter".equals(fieldName)
                    || "order".equals(fieldName)
                    || "additionalApprovers".equals(fieldName)) {
                continue;
            }
            JsonNode value = lineObject.get(fieldName);
            if (value instanceof ObjectNode objectNode && objectNode.has("id")) {
                nodes.add(objectNode);
            }
        }
        return nodes;
    }

    private List<ObjectNode> additionalApproverNodes(ArrayNode additionalApprovers) {
        List<ObjectNode> nodes = new ArrayList<>();
        for (JsonNode additionalApprover : additionalApprovers) {
            if (additionalApprover instanceof ObjectNode objectNode
                    && approverId(objectNode) != null) {
                nodes.add(objectNode);
            }
        }
        return nodes;
    }

    private List<ObjectNode> nodesInStoredOrder(
            ObjectNode lineObject, List<ObjectNode> canonicalNodes) {
        JsonNode storedOrder = lineObject.get("order");
        if (!(storedOrder instanceof ArrayNode) || storedOrder.size() != canonicalNodes.size()) {
            return new ArrayList<>(canonicalNodes);
        }

        Map<String, List<ObjectNode>> nodesById = new HashMap<>();
        for (ObjectNode canonicalNode : canonicalNodes) {
            String id = approverId(canonicalNode);
            if (id == null) return new ArrayList<>(canonicalNodes);
            nodesById.computeIfAbsent(id, ignored -> new ArrayList<>()).add(canonicalNode);
        }

        Map<String, Integer> occurrences = new HashMap<>();
        List<ObjectNode> orderedNodes = new ArrayList<>();
        for (JsonNode storedIdNode : storedOrder) {
            if (!storedIdNode.isTextual()) return new ArrayList<>(canonicalNodes);
            String storedId = storedIdNode.textValue();
            List<ObjectNode> candidates = nodesById.get(storedId);
            int occurrence = occurrences.getOrDefault(storedId, 0);
            if (candidates == null || occurrence >= candidates.size()) {
                return new ArrayList<>(canonicalNodes);
            }
            orderedNodes.add(candidates.get(occurrence));
            occurrences.put(storedId, occurrence + 1);
        }
        return orderedNodes;
    }

    private void retainSelectedAdditionalApprovers(
            ArrayNode additionalApprovers,
            List<ObjectNode> additionalApproverNodes,
            List<ObjectNode> logicalNodes) {
        Set<ObjectNode> selectedNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        selectedNodes.addAll(logicalNodes);
        additionalApprovers.removeAll();
        for (ObjectNode additionalApproverNode : additionalApproverNodes) {
            if (selectedNodes.contains(additionalApproverNode)) {
                additionalApprovers.add(additionalApproverNode);
            }
        }
    }

    private String approverId(ObjectNode node) {
        JsonNode idNode = node.get("id");
        return idNode != null && idNode.isTextual() ? idNode.textValue() : null;
    }

    private void updateApproverNode(ObjectNode node, CuserI user) {
        node.put("id", user.getEno());
        node.put("name", user.getUsrNm() == null ? "" : user.getUsrNm());
        node.put("rank", user.getPtCNm() == null ? "" : user.getPtCNm());
    }

    /**
     * 결재선에서 승인된 결재자의 occurrence(등장 순서) 맵을 생성합니다.
     *
     * <p>동일 사번이 결재선에 여러 번 등장할 경우 순서를 구분하기 위해 occurrence(1-based 등장 횟수)를 key로 사용합니다.
     *
     * @param allApprovers 전체 결재선 목록 (결재 순서 오름차순)
     * @param approvedItems 이미 승인 처리된 결재 항목 목록
     * @return 사번 → 해당 사번의 승인된 occurrence 집합 맵
     */
    private Map<String, Set<Integer>> buildTargetOccurrences(
            List<Cdecim> allApprovers, List<Cdecim> approvedItems) {
        Map<String, Set<Integer>> targetOccurrences = new HashMap<>();
        Map<String, Integer> globalCounters = new HashMap<>();

        for (Cdecim approver : allApprovers) {
            String eno = approver.getDcrEno();
            int occurrence = globalCounters.getOrDefault(eno, 0) + 1;
            globalCounters.put(eno, occurrence);

            boolean isApproved =
                    approvedItems.stream()
                            .anyMatch(item -> item.getDcrSqnSno().equals(approver.getDcrSqnSno()));
            if (isApproved) {
                targetOccurrences.computeIfAbsent(eno, k -> new HashSet<>()).add(occurrence);
            }
        }
        return targetOccurrences;
    }

    /**
     * 결재선 JSON 노드에서 대상 occurrence에 해당하는 결재자 노드에 날짜를 설정합니다.
     *
     * <p><strong>부수 효과</strong>: {@code approvalLineNode}의 대상 {@link ObjectNode}를 직접 수정합니다({@code
     * date} 필드 추가). 호출자는 원본 객체가 변경됨을 인지해야 합니다.
     *
     * <p>기안자 노드({@code drafter})는 occurrence 카운팅에서 제외합니다.
     *
     * @param approvalLineNode 결재선 정보를 담은 JSON 노드
     * @param targetOccurrences 날짜를 설정할 사번 → occurrence 집합 맵
     * @return 하나 이상의 노드가 실제로 수정되었으면 true
     */
    private boolean applyDateToMatchingNodes(
            JsonNode approvalLineNode, Map<String, Set<Integer>> targetOccurrences) {
        JsonNode orderNode = approvalLineNode.get("order");
        if (orderNode instanceof ArrayNode) {
            return applyDateInStoredOrder(approvalLineNode, orderNode, targetOccurrences);
        }
        Map<String, Integer> jsonCounters = new HashMap<>();
        boolean updated = false;

        Iterator<String> fieldNames = approvalLineNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode approverNode = approvalLineNode.get(fieldName);
            if (approverNode == null) {
                continue;
            }
            // 기안자(drafter)는 Cdecim 결재자 레코드가 아니므로 occurrence 카운팅에서 제외.
            // 포함하면 기안자==결재자인 자동결재 시 JSON occurrence와 Cdecim occurrence 간 1 차이가 발생.
            if ("drafter".equals(fieldName)) {
                continue;
            }
            if (approverNode.isArray()) {
                for (JsonNode additional : approverNode) {
                    updated |= applyDateToApproverNode(additional, jsonCounters, targetOccurrences);
                }
            } else if (approverNode.isObject() && approverNode.has("id")) {
                updated |= applyDateToApproverNode(approverNode, jsonCounters, targetOccurrences);
            }
        }
        return updated;
    }

    /** 저장된 결재 순서 배열을 사용해 결재일을 올바른 결재자 노드에 반영합니다. */
    private boolean applyDateInStoredOrder(
            JsonNode approvalLineNode,
            JsonNode orderNode,
            Map<String, Set<Integer>> targetOccurrences) {
        Map<String, List<JsonNode>> nodesById = new HashMap<>();
        Iterator<String> fieldNames = approvalLineNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if ("drafter".equals(fieldName) || "order".equals(fieldName)) continue;
            JsonNode approverNode = approvalLineNode.get(fieldName);
            if (approverNode == null) continue;
            if (approverNode.isArray()) {
                for (JsonNode additional : approverNode) addNodeById(nodesById, additional);
            } else {
                addNodeById(nodesById, approverNode);
            }
        }

        Map<String, Integer> jsonCounters = new HashMap<>();
        boolean updated = false;
        for (JsonNode idNode : orderNode) {
            String id = idNode.asText();
            List<JsonNode> candidates = nodesById.get(id);
            if (candidates == null || candidates.isEmpty()) continue;
            updated |=
                    applyDateToApproverNode(candidates.remove(0), jsonCounters, targetOccurrences);
        }
        return updated;
    }

    private void addNodeById(Map<String, List<JsonNode>> nodesById, JsonNode node) {
        if (node.isObject() && node.has("id")) {
            nodesById
                    .computeIfAbsent(
                            node.get("id").asText(), ignored -> new java.util.ArrayList<>())
                    .add(node);
        }
    }

    /** 결재자 JSON 한 항목의 사번 occurrence를 계산하고 승인일을 반영합니다. */
    private boolean applyDateToApproverNode(
            JsonNode approverNode,
            Map<String, Integer> jsonCounters,
            Map<String, Set<Integer>> targetOccurrences) {
        if (!approverNode.isObject() || !approverNode.has("id")) return false;
        String id = approverNode.get("id").asText();
        int jsonOccurrence = jsonCounters.getOrDefault(id, 0) + 1;
        jsonCounters.put(id, jsonOccurrence);
        Set<Integer> targets = targetOccurrences.get(id);
        if (targets != null
                && targets.contains(jsonOccurrence)
                && approverNode instanceof ObjectNode on) {
            on.put("date", LocalDateTime.now().format(DATE_FMT));
            return true;
        }
        return false;
    }
}
