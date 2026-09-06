package com.kdb.it.common.approval.itbudget.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kdb.it.common.approval.itbudget.model.ItBudgetSnapshot;
import com.kdb.it.config.JacksonConfig;

/** 저장 wire 계약과 기존 canonical 숫자 해시를 함께 사용하는 테스트 문서다. */
public final class StoredSnapshotFixture {
    public static final ObjectMapper MAPPER = new JacksonConfig().objectMapper();
    private static final jakarta.validation.Validator VALIDATOR =
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

    public static ItBudgetSnapshotReader reader() {
        return new ItBudgetSnapshotReader(
                MAPPER,
                VALIDATOR,
                new ItBudgetCanonicalJson(MAPPER),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public static com.kdb.it.common.approval.service.ApprovalLineDelegate delegate(
            ObjectMapper mapper) {
        return new com.kdb.it.common.approval.service.ApprovalLineDelegate(mapper, reader());
    }

    private StoredSnapshotFixture() {}

    public static ObjectNode v2() throws Exception {
        ObjectNode root =
                (ObjectNode)
                        MAPPER.readTree(
                                """
                {"form":{"id":"it-budget","version":2},
                 "payload":{"projects":[{
                   "id":"P1","revision":2,"ordinaryYn":"N","name":"사업 요약","baseYear":"2026",
                   "projectBudget":"999.000","currentRequestAmount":"100.000",
                   "editType":{"code":null,"label":null},"progressStatus":{"code":null,"label":null},
                   "startDate":"2026-09-06","endDate":null,"feasibilityDate":null,
                   "outline":null,"scope":null,"security":null,"purpose":null,"necessity":null,
                   "expectedEffect":null,"mainProgress":null,"workforcePlan":null,
                   "supervisingOrganization":{"code":null,"name":null},
                   "supervisingDepartment":{"code":"D1","name":"부서"},
                   "manager":{"eno":"U1","name":"담당자","rank":null},
                   "teamLeader":{"eno":null,"name":null,"rank":null},
                   "developmentDepartment":{"code":null,"name":null},
                   "developmentManager":{"eno":null,"name":null,"rank":null},
                   "developmentTeamLeader":{"eno":null,"name":null,"rank":null},
                   "businessType":{"code":null,"label":null},"businessDetail":{"code":null,"label":null},
                   "costType":{"code":null,"label":null},"skillType":{"code":null,"label":null},
                   "executionPattern":{"code":null,"label":null},"deploymentYn":null,
                   "assetBudget":"40.000","costBudget":"60.000",
                   "items":[{"id":"I1","revision":2,"sequence":1,"budgetType":{"code":null,"label":null},
                     "goodsName":null,"quantity":"2","currency":"KRW","amount":"100.000","calculationBasis":null}]
                 }],"costs":[{
                   "id":"C1","revision":1,"baseYear":"2026","name":"계약 요약","counterparty":null,
                   "business":{"code":null,"label":null},"budgetType":{"code":null,"label":null},
                   "totalAmount":"25.000","currency":"USD","exchangeRate":"1.2500",
                   "exchangeRateBaseDate":null,"deferralType":{"code":null,"label":null},
                   "firstDeferralDate":null,"reason":null,"supervisingDepartment":{"code":null,"name":null},
                   "manager":{"eno":null,"name":null,"rank":null},"securitySystemUseYn":null,
                   "assetBudget":"5.000","costBudget":"20.000","terminals":[{
                     "id":"T1","revision":1,"sequence":1,"classification":{"code":null,"label":null},
                     "kind":{"code":null,"label":null},"usage":null,"specification":null,"currency":"USD",
                     "exchangeRate":"1.2500","foreignAmount":"20.000","budgetAmount":"25.000"}]
                 }],"summary":{"total":"125.000","asset":"45.000","cost":"80.000"}},
                 "approvalLine":{"requester":{"eno":"U1","name":"신청자","rank":null},
                   "approvers":[{"role":"TEAM_LEAD","eno":"E1","name":"결재자 1","rank":"팀장","date":null},
                                {"role":"DEPT_HEAD","eno":"E2","name":"결재자 2","rank":"부장","date":null}]},
                 "integrity":{"algorithm":"SHA-256","canonicalization":"IT_BUDGET_V2",
                   "payloadDigest":"", "capturedAt":"2026-09-06T05:30:00Z",
                   "sources":[{"kind":"PROJECT","id":"P1","revision":2,"order":1,"digest":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                              {"kind":"COST","id":"C1","revision":1,"order":2,"digest":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}}
                """);
        resign(root);
        return root;
    }

    public static void resign(ObjectNode root) throws Exception {
        var payload = MAPPER.treeToValue(root.get("payload"), ItBudgetSnapshot.Payload.class);
        ((ObjectNode) root.get("integrity"))
                .put("payloadDigest", new ItBudgetCanonicalJson(MAPPER).digest(payload));
    }

    public static ObjectNode object(ObjectNode root, String pointer) {
        return (ObjectNode) root.at(pointer);
    }
}
