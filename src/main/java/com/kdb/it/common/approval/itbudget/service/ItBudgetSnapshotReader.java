package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import com.kdb.it.exception.DataCorruptionException;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 저장 신청서를 한 번 파싱하고 전산예산 v2의 구조·무결성을 검증한다. 원장을 다시 읽지 않는다. */
@Component
public final class ItBudgetSnapshotReader {
    private static final Logger log = LoggerFactory.getLogger(ItBudgetSnapshotReader.class);
    private final ObjectMapper mapper;
    private final Validator validator;
    private final ItBudgetCanonicalJson canonical;
    private final MeterRegistry meterRegistry;

    public ItBudgetSnapshotReader(
            ObjectMapper mapper,
            Validator validator,
            ItBudgetCanonicalJson canonical,
            MeterRegistry meterRegistry) {
        this.mapper =
                mapper.copy()
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.validator = validator;
        this.canonical = canonical;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 원문 JSON을 버전별 읽기 결과로 변환한다. v1·다른 양식은 기존 필드 해석을 유지한다.
     *
     * @param raw CAPPLM에 저장된 신청서 상세
     * @return v1 비검증 문서 또는 구조와 payloadDigest를 검증한 v2 문서
     * @throws DataCorruptionException JSON·버전·필수 구조·무결성이 잘못된 경우
     */
    public ParsedSnapshot read(String raw) {
        String tag = "unknown";
        try {
            if (raw == null || raw.isBlank()) throw corrupt(tag, "신청서 상세 JSON이 없습니다.");
            JsonNode tree = mapper.readTree(raw);
            if (!(tree instanceof ObjectNode root)) throw corrupt(tag, "신청서 상세 JSON은 객체여야 합니다.");
            JsonNode version = root.path("form").path("version");
            tag = version.isIntegralNumber() && version.intValue() == 2 ? "v2" : "v1";
            String form = root.path("form").path("id").asText();
            boolean budget = "it-budget".equals(form) || "IT_BUDGET".equals(form);
            boolean envelope = root.has("integrity") || budget && root.has("payload");
            // v2 봉투가 남아 있으면 버전 필드를 변조해도 v1 조회로 분류하지 않는다.
            if (envelope) tag = "v2";
            if (!budget && !envelope && !(version.isIntegralNumber() && version.intValue() == 2))
                return new ParsedSnapshot(root, 1, null);
            if (budget
                    && !envelope
                    && (version.isMissingNode() || version.isInt() && version.intValue() == 1)) {
                recordCounter("approval.it_budget.snapshot.legacy_read");
                log.debug("전산예산 스냅샷 조회: version=v1, outcome=legacy_read");
                return new ParsedSnapshot(root, 1, null);
            }
            if (!"it-budget".equals(form) || !version.isInt() || version.intValue() != 2)
                throw corrupt("unknown", "지원하지 않는 전산예산 스냅샷 버전입니다.");
            tag = "v2";
            ObjectNode document = root.deepCopy();
            JsonNode recall = document.remove("recallInfo");
            if (recall != null) validateRecall(recall);
            shape(document, ItBudgetApprovalDto.ItBudgetSnapshot.class);
            var snapshot = mapper.treeToValue(document, ItBudgetApprovalDto.ItBudgetSnapshot.class);
            validate(snapshot);
            validateRoles(snapshot.approvalLine());
            var integrity = snapshot.integrity();
            if (!"SHA-256".equals(integrity.algorithm())
                    || !"IT_BUDGET_V2".equals(integrity.canonicalization())
                    || !digestFormat(integrity.payloadDigest()))
                throw corrupt(tag, "스냅샷 무결성 메타데이터가 올바르지 않습니다.");
            verifyIdentities(snapshot);
            ItBudgetSnapshot.Payload payload =
                    ItBudgetSnapshotCodec.toInternal(mapper, snapshot.payload());
            if (!MessageDigest.isEqual(
                    canonical.digest(payload).getBytes(StandardCharsets.UTF_8),
                    integrity.payloadDigest().getBytes(StandardCharsets.UTF_8)))
                throw corrupt(tag, "전산예산 스냅샷 payloadDigest가 일치하지 않습니다.");
            return new ParsedSnapshot(root, 2, payload);
        } catch (SnapshotCorruptionException exception) {
            recordIntegrityFailure("v2".equals(tag) || "v2".equals(exception.versionTag()));
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException | DateTimeException exception) {
            // Jackson 예외의 메시지·cause에는 JSON 토큰/본문이 포함될 수 있어 외부 예외에 연결하지 않는다.
            recordIntegrityFailure("v2".equals(tag));
            throw corrupt(tag, "신청서 상세 JSON 또는 필수 값이 손상되었습니다.");
        }
    }

    private void recordIntegrityFailure(boolean v2) {
        if (!v2) return;
        recordCounter("approval.it_budget.snapshot.integrity_failure");
        log.warn("전산예산 스냅샷 무결성 검증 실패: version=v2, outcome=integrity_failure");
    }

    private void recordCounter(String name) {
        try {
            meterRegistry.counter(name).increment();
        } catch (RuntimeException exception) {
            log.warn("전산예산 메트릭 기록 실패: operation=snapshot_read, stage=counter");
        }
    }

    private void validate(Object value) {
        if (!validator.validate(value).isEmpty()) throw corrupt("v2", "스냅샷 필수 값 또는 형식이 올바르지 않습니다.");
    }

    private void validateRoles(ItBudgetApprovalDto.SnapshotApprovalLine line) {
        Set<ItBudgetApprovalDto.ApproverRole> fixedRoles = new HashSet<>();
        int previousRole = -1;
        for (var person : line.approvers()) {
            var role = person.role();
            if (role.ordinal() < previousRole
                    || role != ItBudgetApprovalDto.ApproverRole.ADDITIONAL && !fixedRoles.add(role))
                throw corrupt("v2", "결재 역할 또는 순서가 올바르지 않습니다.");
            previousRole = role.ordinal();
        }
    }

    /** 모든 record 필드를 요구하되 null 허용 여부는 DTO의 Bean Validation을 따른다. */
    private void shape(JsonNode node, Type type) {
        if (node.isNull()) return;
        if (type instanceof ParameterizedType parameterized) {
            if (!node.isArray()) throw corrupt("v2", "스냅샷 배열 형식이 올바르지 않습니다.");
            for (JsonNode child : node) shape(child, parameterized.getActualTypeArguments()[0]);
            return;
        }
        Class<?> target = (Class<?>) type;
        if (target.isRecord()) {
            if (!node.isObject()) throw corrupt("v2", "스냅샷 객체 형식이 올바르지 않습니다.");
            var components = target.getRecordComponents();
            if (node.size() != components.length) throw corrupt("v2", "스냅샷 필드가 누락되었거나 추가되었습니다.");
            for (var component : components) {
                JsonNode child = node.get(component.getName());
                if (child == null || component.getType().isPrimitive() && child.isNull())
                    throw corrupt("v2", "스냅샷 필수 필드가 없습니다.");
                shape(child, component.getGenericType());
            }
        } else if (target == int.class) {
            if (!node.isIntegralNumber() || !node.canConvertToInt())
                throw corrupt("v2", "스냅샷 정수 형식이 올바르지 않습니다.");
        } else {
            if (!node.isTextual()) throw corrupt("v2", "스냅샷 문자열 형식이 올바르지 않습니다.");
            if (target == LocalDate.class && !node.textValue().matches("\\d{4}-\\d{2}-\\d{2}"))
                throw corrupt("v2", "스냅샷 날짜 형식이 올바르지 않습니다.");
            if (target == Instant.class) OffsetDateTime.parse(node.textValue());
        }
    }

    private void verifyIdentities(ItBudgetApprovalDto.ItBudgetSnapshot snapshot) {
        Set<SourceIdentity> expected = new HashSet<>();
        for (var project : snapshot.payload().projects()) {
            addIdentity(expected, new SourceIdentity("PROJECT", project.id(), project.revision()));
            Set<ChildIdentity> children = new HashSet<>();
            for (var item : project.items()) {
                if (item.revision() != project.revision()
                        || !children.add(new ChildIdentity(item.id(), item.sequence())))
                    throw corrupt("v2", "품목의 원장 식별자가 올바르지 않습니다.");
            }
        }
        for (var cost : snapshot.payload().costs()) {
            addIdentity(expected, new SourceIdentity("COST", cost.id(), cost.revision()));
            Set<ChildIdentity> children = new HashSet<>();
            for (var terminal : cost.terminals()) {
                if (terminal.revision() != cost.revision()
                        || !children.add(new ChildIdentity(terminal.id(), terminal.sequence())))
                    throw corrupt("v2", "단말기의 원장 식별자가 올바르지 않습니다.");
            }
        }
        Set<SourceIdentity> actual = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (var source : snapshot.integrity().sources()) {
            addIdentity(
                    actual,
                    new SourceIdentity(source.kind().name(), source.id(), source.revision()));
            if (!orders.add(source.order()) || !digestFormat(source.digest()))
                throw corrupt("v2", "원장 무결성 메타데이터가 올바르지 않습니다.");
        }
        if (!actual.equals(expected)) throw corrupt("v2", "payload와 무결성 원장 식별자가 다릅니다.");
    }

    private void addIdentity(Set<SourceIdentity> identities, SourceIdentity identity) {
        if (!identities.add(identity)) throw corrupt("v2", "스냅샷 원장 식별자가 중복되었습니다.");
    }

    private static boolean digestFormat(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private record SourceIdentity(String kind, String id, int revision) {}

    private record ChildIdentity(String id, int sequence) {}

    private void validateRecall(JsonNode recall) {
        if (!recall.isObject()
                || recall.size() != 3
                || !recall.path("recallerEno").isTextual()
                || recall.path("recallerEno").asText().isBlank()
                || !recall.path("recallDtm").isTextual()
                || !(recall.has("recallOpnn")
                        && (recall.get("recallOpnn").isNull()
                                || recall.get("recallOpnn").isTextual())))
            throw corrupt("v2", "회수 정보 형식이 올바르지 않습니다.");
        LocalDateTime.parse(recall.path("recallDtm").textValue());
    }

    private static SnapshotCorruptionException corrupt(String tag, String message) {
        return new SnapshotCorruptionException(tag, message);
    }

    /** JSON 원문을 담지 않는 고정 카디널리티 진단 정보다. */
    public static final class SnapshotCorruptionException extends DataCorruptionException {
        private final String versionTag;

        private SnapshotCorruptionException(String versionTag, String message) {
            super(message);
            this.versionTag = versionTag;
        }

        public String versionTag() {
            return versionTag;
        }
    }

    /** 검증한 원본 payload·integrity를 보존하며 결재선과 명시적 회수 정보만 수정할 수 있다. */
    public final class ParsedSnapshot {
        private final ObjectNode root;
        private final int version;
        private final ItBudgetSnapshot.Payload payload;

        private ParsedSnapshot(ObjectNode root, int version, ItBudgetSnapshot.Payload payload) {
            this.root = root;
            this.version = version;
            this.payload = payload;
        }

        public int version() {
            return version;
        }

        public ItBudgetSnapshot.Payload payload() {
            return payload;
        }

        /** v1은 기존의 얕은 DTO 규칙으로 이미 파싱된 트리에서 읽는다. 변환 오류는 데이터 손상이다. */
        public <T> T legacyValue(Class<T> type) {
            if (version != 1) throw new IllegalStateException("v1 문서에만 legacy 변환을 사용할 수 있습니다.");
            try {
                return mapper.treeToValue(root.deepCopy(), type);
            } catch (JsonProcessingException | IllegalArgumentException ex) {
                throw corrupt("v1", "기존 신청서 요약 값이 손상되었습니다.");
            }
        }

        /** required인 진행 문서는 결재선 누락·형식 손상을 실패시킨다. */
        public ObjectNode approvalLine(boolean required) {
            if (root.get("approvalLine") instanceof ObjectNode line) return line;
            if (required || version == 2) throw corrupt("v" + version, "필수 결재선이 손상되었습니다.");
            return null;
        }

        /** 회수 정보는 payload 해시 바깥의 기존 mutable metadata다. */
        public void recall(String eno, String opinion) {
            ObjectNode info = mapper.createObjectNode();
            info.put("recallerEno", eno);
            info.put("recallDtm", LocalDateTime.now().toString());
            info.put("recallOpnn", opinion);
            if (version == 2) validateRecall(info);
            root.set("recallInfo", info);
        }

        /** 수정된 v2 결재선의 깊은 계약을 재검증한 뒤 원래 payload·integrity를 그대로 직렬화한다. */
        public String write() {
            try {
                if (version == 2) {
                    shape(root.get("approvalLine"), ItBudgetApprovalDto.SnapshotApprovalLine.class);
                    var line =
                            mapper.treeToValue(
                                    root.get("approvalLine"),
                                    ItBudgetApprovalDto.SnapshotApprovalLine.class);
                    validate(line);
                    validateRoles(line);
                }
                return mapper.writeValueAsString(root);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                throw corrupt("v" + version, "신청서 상세 JSON을 저장할 수 없습니다.");
            }
        }
    }
}
