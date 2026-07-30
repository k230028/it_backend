package com.kdb.it.common.approval.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.entity.Cdecim;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApplicationReadProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired ApplicationMapRepository applicationMapRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApproverRepository approverRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 결재응답프로젝션의매핑정렬과대표행선택계약을검증한다() {
        persistApplication("APF-2026-00000001", "1");
        persistApplication("APF-2026-00000002", "2");
        persistApplication("APF-2026-00000003", "3");
        persistApplication("APF-2026-00000004", "1");
        persistApplication("APF-2026-00000005", "2");
        persistApplication("APF-2026-00000006", "3");

        persistMap("APF-2026-00000001", "BPROJM", "A", 1);
        persistMap("APF-2026-00000003", "BPROJM", "A", 2);
        persistMap("APF-2026-00000002", "BPROJM", "B", 7);
        persistMap("APF-2026-00000004", "BCOSTM", "BG-A", 1);
        persistMap("APF-2026-00000006", "BCOSTM", "BG-A", 1);
        persistMap("APF-2026-00000005", "BCOSTM", "BG-A", 2);

        entityManager.persist(decision("APF-2026-00000003", 2, "E0002", "2"));
        entityManager.persist(decision("APF-2026-00000003", 1, "E0001", "1"));
        entityManager.flush();
        entityManager.clear();

        List<ApplicationMapRepository.ApplicationMapView> projectDetail =
                applicationMapRepository
                        .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                "BPROJM", "A", 2);
        List<ApplicationMapRepository.ApplicationMapView> projectBatch =
                applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BPROJM", List.of("A", "B"));
        List<ApplicationMapRepository.ApplicationMapView> costBatch =
                applicationMapRepository.findViewsByFntTbNmAndPkColNmInOrderByApfDcmNoDesc(
                        "BCOSTM", List.of("BG-A"));
        List<ApplicationMapRepository.ApplicationMapView> costDetail =
                applicationMapRepository
                        .findViewsByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                                "BCOSTM", "BG-A", 2);

        assertThat(projectDetail)
                .extracting(view -> view.getApfDcmNo())
                .containsExactly("APF-2026-00000003");
        assertThat(projectBatch)
                .extracting(view -> view.getApfDcmNo())
                .containsExactly("APF-2026-00000003", "APF-2026-00000002", "APF-2026-00000001");
        assertThat(costBatch)
                .extracting(view -> view.getApfDcmNo(), view -> view.getFntTbCrySno())
                .containsExactly(
                        tuple("APF-2026-00000006", 1),
                        tuple("APF-2026-00000005", 2),
                        tuple("APF-2026-00000004", 1));
        assertThat(costDetail)
                .extracting(view -> view.getApfDcmNo())
                .containsExactly("APF-2026-00000005");

        Map<String, String> latestProjectByPk = new LinkedHashMap<>();
        projectBatch.forEach(
                view -> latestProjectByPk.putIfAbsent(view.getPkColNm(), view.getApfDcmNo()));
        assertThat(latestProjectByPk)
                .containsExactly(
                        org.assertj.core.data.MapEntry.entry("A", "APF-2026-00000003"),
                        org.assertj.core.data.MapEntry.entry("B", "APF-2026-00000002"));

        Map<String, String> latestCostByPkAndSno = new LinkedHashMap<>();
        costBatch.forEach(
                view ->
                        latestCostByPkAndSno.putIfAbsent(
                                view.getPkColNm() + "_" + view.getFntTbCrySno(),
                                view.getApfDcmNo()));
        assertThat(latestCostByPkAndSno)
                .containsExactly(
                        org.assertj.core.data.MapEntry.entry("BG-A_1", "APF-2026-00000006"),
                        org.assertj.core.data.MapEntry.entry("BG-A_2", "APF-2026-00000005"));

        List<ApplicationRepository.ApplicationSummaryView> summaries =
                applicationRepository.findSummaryViewsByApfMngNoIn(
                        List.of("APF-2026-00000003", "APF-2026-00000002"));
        assertThat(summaries)
                .extracting(view -> view.getApfMngNo(), view -> view.getItPtlApfPrgStsC())
                .containsExactlyInAnyOrder(
                        tuple("APF-2026-00000003", "3"), tuple("APF-2026-00000002", "2"));

        List<ApproverRepository.ApproverReadView> single =
                approverRepository.findReadViewsByDcdMngNoOrderByDcrSqnSnoAsc("APF-2026-00000003");
        List<ApproverRepository.ApproverReadView> batch =
                approverRepository.findReadViewsByDcdMngNoInOrderByDcrSqnSnoAsc(
                        List.of("APF-2026-00000003"));
        assertThat(single).extracting(view -> view.getDcrSqnSno()).containsExactly(1, 2);
        assertThat(batch).extracting(view -> view.getDcrSqnSno()).containsExactly(1, 2);

        assertThat(ApplicationMapRepository.ApplicationMapView.class.getDeclaredMethods())
                .hasSize(3);
        assertThat(ApplicationRepository.ApplicationSummaryView.class.getDeclaredMethods())
                .hasSize(6);
        assertThat(ApproverRepository.ApproverReadView.class.getDeclaredMethods()).hasSize(7);
        assertThat(ApplicationRepository.ApplicationReadView.class.getDeclaredMethods()).hasSize(8);
    }

    @Test
    void 신청서읽기뷰가엔티티조회와동일한필드를반환한다() {
        entityManager.persist(
                Capplm.builder()
                        .apfMngNo("APF-2026-00000101")
                        .itPtlApfPrgStsC("1")
                        .dcdReqTtl("제목-APF-2026-00000101")
                        .dcdReqInf("{\"projects\":[{\"prjMngNo\":\"PRJ-1\"}]}") // CLOB 필드 포함 검증
                        .dcdReqUsid("E0001")
                        .dcdReqDtm(LocalDate.of(2026, 7, 21))
                        .rgprDcdReqCone("요청-APF-2026-00000101")
                        .dcdReqBbrC("180")
                        .fstEnrDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build());
        entityManager.persist(
                Capplm.builder()
                        .apfMngNo("APF-2026-00000102")
                        .itPtlApfPrgStsC("2")
                        .dcdReqTtl("제목-APF-2026-00000102")
                        .dcdReqInf(null) // CLOB null 케이스
                        .dcdReqUsid("E0002")
                        .dcdReqDtm(LocalDate.of(2026, 7, 22))
                        .rgprDcdReqCone("요청-APF-2026-00000102")
                        .dcdReqBbrC("181")
                        .fstEnrDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build());
        entityManager.flush();
        entityManager.clear();

        // 단건: entity findById vs view findReadViewByApfMngNo 8필드 동등성(CLOB 포함)
        Capplm entity = applicationRepository.findById("APF-2026-00000101").orElseThrow();
        ApplicationRepository.ApplicationReadView view =
                applicationRepository.findReadViewByApfMngNo("APF-2026-00000101").orElseThrow();
        assertThat(view.getApfMngNo()).isEqualTo(entity.getApfMngNo());
        assertThat(view.getItPtlApfPrgStsC()).isEqualTo(entity.getItPtlApfPrgStsC());
        assertThat(view.getDcdReqTtl()).isEqualTo(entity.getDcdReqTtl());
        assertThat(view.getDcdReqInf()).isEqualTo(entity.getDcdReqInf());
        assertThat(view.getDcdReqUsid()).isEqualTo(entity.getDcdReqUsid());
        assertThat(view.getDcdReqDtm()).isEqualTo(entity.getDcdReqDtm());
        assertThat(view.getRgprDcdReqCone()).isEqualTo(entity.getRgprDcdReqCone());
        assertThat(view.getDcdReqBbrC()).isEqualTo(entity.getDcdReqBbrC());

        // 미존재 신청관리번호는 empty 계약을 유지한다
        assertThat(applicationRepository.findReadViewByApfMngNo("APF-NONE-EXIST")).isEmpty();

        // 전체목록: findAll() vs findAllProjectedBy() 건수·필드 동등성 (CLOB 포함, 정렬 없음 동일 의미)
        List<Capplm> allEntities = applicationRepository.findAll();
        List<ApplicationRepository.ApplicationReadView> allViews =
                applicationRepository.findAllProjectedBy();
        assertThat(allViews).hasSameSizeAs(allEntities);

        Map<String, Capplm> entityByPk =
                allEntities.stream()
                        .collect(java.util.stream.Collectors.toMap(Capplm::getApfMngNo, e -> e));
        for (ApplicationRepository.ApplicationReadView v : allViews) {
            Capplm matching = entityByPk.get(v.getApfMngNo());
            assertThat(matching).isNotNull();
            assertThat(v.getItPtlApfPrgStsC()).isEqualTo(matching.getItPtlApfPrgStsC());
            assertThat(v.getDcdReqTtl()).isEqualTo(matching.getDcdReqTtl());
            assertThat(v.getDcdReqInf()).isEqualTo(matching.getDcdReqInf());
            assertThat(v.getDcdReqUsid()).isEqualTo(matching.getDcdReqUsid());
            assertThat(v.getDcdReqDtm()).isEqualTo(matching.getDcdReqDtm());
            assertThat(v.getRgprDcdReqCone()).isEqualTo(matching.getRgprDcdReqCone());
            assertThat(v.getDcdReqBbrC()).isEqualTo(matching.getDcdReqBbrC());
        }
    }

    private void persistApplication(String apfMngNo, String status) {
        entityManager.persist(
                Capplm.builder()
                        .apfMngNo(apfMngNo)
                        .itPtlApfPrgStsC(status)
                        .dcdReqTtl("제목-" + apfMngNo)
                        .dcdReqUsid("E0001")
                        .dcdReqDtm(LocalDate.of(2026, 7, 21))
                        .rgprDcdReqCone("요청-" + apfMngNo)
                        .fstEnrDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build());
    }

    private void persistMap(String apfMngNo, String table, String pk, int sno) {
        entityManager.persist(
                Cappla.builder()
                        .apfDcmNo(apfMngNo)
                        .fntTbNm(table)
                        .pkColNm(pk)
                        .fntTbCrySno(sno)
                        .fstEnrDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build());
    }

    private Cdecim decision(String apfMngNo, int sequence, String eno, String status) {
        return Cdecim.builder()
                .dcdMngNo(apfMngNo)
                .dcrSqnSno(sequence)
                .dcrEno(eno)
                .itPtlDcdStsC(status)
                .dcdTpC(Cdecim.DECISION_TYPE_REQUEST)
                .dcdDtm(LocalDate.of(2026, 7, 21))
                .dcrOpnnCone("의견-" + sequence)
                .lstDcdYn(sequence == 2 ? "Y" : "N")
                .fstEnrDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                .fstEnrUsid("BE03-TEST")
                .lstChgDtm(LocalDateTime.of(2026, 7, 21, 9, 0))
                .lstChgUsid("BE03-TEST")
                .delYn("N")
                .build();
    }
}
