package com.kdb.it.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.CapplaId;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.BcmmtmId;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 신청서 관계와 평가위원의 동일 부분키 다중행 격리를 실제 Oracle에서 검증한다. */
class PhysicalCompositeIdIsolationIt extends AbstractOracleRepositoryTest {

    @Autowired ApplicationMapRepository applicationMapRepository;
    @Autowired CommitteeRepository committeeRepository;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("신청서 관계는 동일 일련번호라도 문서번호별로 조회·수정·삭제가 격리된다")
    void applicationMap_isolatedByDocumentAndSequence() {
        String suffix = suffix();
        String documentA = "BE25-APF-A-" + suffix;
        String documentB = "BE25-APF-B-" + suffix;
        long sequence = -Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L) - 1;
        insertApplicationMap(documentA, sequence, "TABLE_A", suffix + "A");
        insertApplicationMap(documentB, sequence, "TABLE_B", suffix + "B");
        entityManager.clear();

        CapplaId idA = new CapplaId(documentA, sequence);
        CapplaId idB = new CapplaId(documentB, sequence);
        assertThat(applicationMapRepository.findById(idA))
                .get()
                .extracting(Cappla::getFntTbNm)
                .isEqualTo("TABLE_A");
        assertThat(applicationMapRepository.findById(idB))
                .get()
                .extracting(Cappla::getFntTbNm)
                .isEqualTo("TABLE_B");

        entityManager
                .createNativeQuery(
                        "UPDATE TPRMPP_CAPPLA SET FNT_TB_NM = :name "
                                + "WHERE APF_DCM_NO = :documentNo AND APF_SNO = :sequence")
                .setParameter("name", "TABLE_A_UPDATED")
                .setParameter("documentNo", documentA)
                .setParameter("sequence", sequence)
                .executeUpdate();
        entityManager.clear();

        assertThat(applicationMapRepository.findById(idA))
                .get()
                .extracting(Cappla::getFntTbNm)
                .isEqualTo("TABLE_A_UPDATED");
        assertThat(applicationMapRepository.findById(idB))
                .get()
                .extracting(Cappla::getFntTbNm)
                .isEqualTo("TABLE_B");

        applicationMapRepository.deleteById(idA);
        flushAndClear();

        assertThat(applicationMapRepository.findById(idA)).isEmpty();
        assertThat(applicationMapRepository.findById(idB)).isPresent();
    }

    @Test
    @DisplayName("평가위원은 동일 협의회·사번이라도 위원유형별로 조회·수정·삭제가 격리된다")
    void committee_isolatedByCouncilTypeAndEmployee() {
        String suffix = suffix();
        String councilId = "BE25-C-" + suffix;
        String employeeNo = "BE25-E-" + suffix;
        committeeRepository.saveAll(
                List.of(
                        committee(councilId, "01", employeeNo),
                        committee(councilId, "02", employeeNo)));
        flushAndClear();

        BcmmtmId idA = new BcmmtmId(councilId, "01", employeeNo);
        BcmmtmId idB = new BcmmtmId(councilId, "02", employeeNo);
        Bcmmtm rowA = committeeRepository.findById(idA).orElseThrow();
        rowA.confirmReview();
        committeeRepository.flush();
        entityManager.clear();

        assertThat(committeeRepository.findById(idA))
                .get()
                .extracting(Bcmmtm::getCnfmYn)
                .isEqualTo("Y");
        assertThat(committeeRepository.findById(idB))
                .get()
                .extracting(Bcmmtm::getCnfmYn)
                .isEqualTo("N");

        committeeRepository.deleteById(idA);
        flushAndClear();

        assertThat(committeeRepository.findById(idA)).isEmpty();
        assertThat(committeeRepository.findById(idB)).isPresent();
    }

    private void insertApplicationMap(
            String documentNo, long sequence, String tableName, String guid) {
        entityManager
                .createNativeQuery(
                        """
                        INSERT INTO TPRMPP_CAPPLA (
                            APF_DCM_NO, APF_SNO, FNT_TB_NM, PK_COL_NM, FNT_TB_CRY_SNO,
                            FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO,
                            LST_CHG_USID, LST_CHG_DTM
                        ) VALUES (
                            :documentNo, :sequence, :tableName, :keyName, 1,
                            'BE25-TEST', SYSDATE, 'N', :guid, 1,
                            'BE25-TEST', SYSDATE
                        )
                        """)
                .setParameter("documentNo", documentNo)
                .setParameter("sequence", sequence)
                .setParameter("tableName", tableName)
                .setParameter("keyName", documentNo)
                .setParameter("guid", guid)
                .executeUpdate();
    }

    private Bcmmtm committee(String councilId, String type, String employeeNo) {
        LocalDateTime now = LocalDateTime.now();
        return Bcmmtm.builder()
                .itPtlAsctId(councilId)
                .itPtlAsctMebTc(type)
                .eno(employeeNo)
                .cnfmYn("N")
                .fstEnrDtm(now)
                .fstEnrUsid("BE25-TEST")
                .lstChgDtm(now)
                .lstChgUsid("BE25-TEST")
                .build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
