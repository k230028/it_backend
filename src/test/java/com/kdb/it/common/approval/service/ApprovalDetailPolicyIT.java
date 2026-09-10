package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.*;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 실제 Oracle CLOB null 조건·활성 원본 projection을 검증하며 테스트 트랜잭션으로 모두 롤백한다. */
@Tag("it")
@SpringBootTest(
        properties = {"jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"})
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
@Transactional
@WithMockUser(username = "ITEST15")
class ApprovalDetailPolicyIT {
    private static final CustomUserDetails ADMIN =
            new CustomUserDetails("ITEST15", List.of(CustomUserDetails.ATH_ADMIN), null);
    @Autowired private ApplicationRepository applications;
    @Autowired private ApplicationMapRepository maps;
    @Autowired private ApprovalDetailPolicy policy;
    @Autowired private ApplicationService service;

    @Test
    void detailReadValidatesOracleClobAndCouncilNullPolicy() throws Exception {
        String raw =
                com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.v2().toString();
        String valid = application(raw);
        String council = application(null);
        source(council, "BASCTM", "C1", null, "N");
        String invalidNull = application(null);
        source(invalidNull, "BCOSTM", "C1", 1, "N");
        String corrupt = application(raw.replace("\"125.000\"", "\"999.000\""));
        applications.flush();
        maps.flush();
        assertThat(service.getApfDtlCone(valid, ADMIN).getApfDtlCone()).isEqualTo(raw);
        assertThat(service.getApfDtlCone(council, ADMIN).getApfDtlCone()).isNull();
        assertThatThrownBy(() -> service.getApfDtlCone(invalidNull, ADMIN))
                .isInstanceOf(com.kdb.it.exception.DataCorruptionException.class);
        assertThatThrownBy(() -> service.getApfDtlCone(corrupt, ADMIN))
                .isInstanceOf(com.kdb.it.exception.DataCorruptionException.class);
    }

    @Test
    void onlyUnambiguousActiveCouncilSourcesAllowAbsentDetail() {
        String review = application(null);
        String skip = application(null);
        String deleted = application(null);
        String mixed = application(null);
        String present = application("{}");
        String missing = application(null);
        String budget = application(null);
        String revision = application(null);
        String missingKey = application(null);
        String duplicate = application(null);
        source(review, "BASCTM", "C1", null, "N");
        source(skip, "BASKPM", "C1", null, "N");
        source(deleted, "BASCTM", "C1", null, "Y");
        source(mixed, "BASCTM", "C1", null, "N");
        source(mixed, "BPROJM", "P1", 1, "N");
        source(present, "BASCTM", "C1", null, "N");
        source(budget, "BCOSTM", "B1", 1, "N");
        source(revision, "BASCTM", "C1", 1, "N");
        source(missingKey, "BASCTM", null, null, "N");
        source(duplicate, "BASCTM", "C1", null, "N");
        source(duplicate, "BASCTM", "C2", null, "N");
        maps.flush();

        assertThat(
                        policy.findJsonlessCouncilIds(
                                List.of(
                                        review,
                                        skip,
                                        deleted,
                                        mixed,
                                        present,
                                        missing,
                                        budget,
                                        revision,
                                        missingKey,
                                        duplicate)))
                .containsExactlyInAnyOrder(review, skip);
    }

    private String application(String detail) {
        String id = "APF-POLICY-" + UUID.randomUUID();
        applications.save(
                Capplm.builder().apfMngNo(id).dcdReqInf(detail).itPtlApfPrgStsC("1").build());
        return id;
    }

    /** 신청서일련번호(APF_SNO)는 신청서 안에서 1부터 매기는 순번이라 신청서별로 센다. */
    private final java.util.Map<String, Long> nextApfSno = new java.util.HashMap<>();

    private void source(String id, String table, String key, Integer revision, String deleted) {
        maps.save(
                Cappla.builder()
                        .apfSno(nextApfSno.merge(id, 1L, Long::sum))
                        .apfDcmNo(id)
                        .fntTbNm(table)
                        .pkColNm(key)
                        .fntTbCrySno(revision)
                        .delYn(deleted)
                        .build());
    }
}
