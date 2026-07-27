package com.kdb.it.common.code.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 공통코드 REST 응답 전용 프로젝션({@link CcodemResponseRow}) Oracle 통합 테스트.
 *
 * <p>기존 엔티티 조회(findByCIdWithValidDate/findByCIdAndCdvaWithValidDate/findByCTpWithValidDate)와 신규 view
 * 조회(findResponseRowsByCIdWithValidDate 등)가 (a) 18개 필드 값 동등성, (b) 정렬 계약(cSqn 오름차순·null 마지막), (c) 유효일
 * 경계(만료·미래 코드 제외) 동등성, (d) 단건 empty 계약을 동일하게 지키는지 검증한다.
 */
class CodeResponseProjectionIt extends AbstractOracleRepositoryTest {

    @Autowired private CodeRepository codeRepository;
    @Autowired private EntityManager entityManager;

    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 7, 1);

    @Test
    @DisplayName("cId 기준 view 조회가 엔티티 조회와 18필드·정렬·유효일 경계에서 동등하다")
    void findResponseRowsByCId_equalsEntityQuery_inFieldsSortAndValidDateBoundary() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cId = "BE03-CID-" + suffix;

        // 만료 코드(대상일 이후 종료) — 제외되어야 함
        persistCode(cId, "EXP", "20200101", "20260101", null, "IOE_LEAFE");
        // 미래 코드(대상일 이전 시작) — 제외되어야 함
        persistCode(cId, "FUT", "20261231", "20991231", 3, "IOE_LEAFE");
        // 활성 코드: cSqn 지정
        persistCode(cId, "ACT-A", "20200101", "20991231", 1, "IOE_LEAFE");
        // 활성 코드: cSqn null — 정렬상 마지막이어야 함
        persistCode(cId, "ACT-B", "20200101", "20991231", null, "IOE_LEAFE");
        entityManager.flush();
        entityManager.clear();

        List<Ccodem> entities = codeRepository.findByCIdWithValidDate(cId, TARGET_DATE);
        List<CcodemResponseRow> rows =
                codeRepository.findResponseRowsByCIdWithValidDate(cId, TARGET_DATE);

        // 유효일 경계: 만료·미래 코드 제외, 활성 코드 2건만 포함 (양쪽 동일)
        assertThat(entities).extracting(Ccodem::getCdva).containsExactly("ACT-A", "ACT-B");
        assertThat(rows).extracting(CcodemResponseRow::cdva).containsExactly("ACT-A", "ACT-B");

        // 18필드 동등성 (cdva 기준 매칭)
        var entityByCdva = entities.stream().collect(Collectors.toMap(Ccodem::getCdva, e -> e));
        for (CcodemResponseRow row : rows) {
            Ccodem entity = entityByCdva.get(row.cdva());
            assertThat(entity).isNotNull();
            assertThat(row.cId()).isEqualTo(entity.getCId());
            assertThat(row.cdvaNm()).isEqualTo(entity.getCdvaNm());
            assertThat(row.cNm()).isEqualTo(entity.getCNm());
            assertThat(row.cdvaDes()).isEqualTo(entity.getCdvaDes());
            assertThat(row.cdvaDtl()).isEqualTo(entity.getCdvaDtl());
            assertThat(row.cdvaDtlC()).isEqualTo(entity.getCdvaDtlC());
            assertThat(row.cTp()).isEqualTo(entity.getCTp());
            assertThat(row.cTpDes()).isEqualTo(entity.getCTpDes());
            assertThat(row.hrkC()).isEqualTo(entity.getHrkC());
            assertThat(row.cSqn()).isEqualTo(entity.getCSqn());
            assertThat(row.sttDt()).isEqualTo(entity.getSttDt());
            assertThat(row.endDt()).isEqualTo(entity.getEndDt());
            assertThat(row.delYn()).isEqualTo(entity.getDelYn());
            assertThat(row.fstEnrDtm()).isEqualTo(entity.getFstEnrDtm());
            assertThat(row.fstEnrUsid()).isEqualTo(entity.getFstEnrUsid());
            assertThat(row.lstChgDtm()).isEqualTo(entity.getLstChgDtm());
            assertThat(row.lstChgUsid()).isEqualTo(entity.getLstChgUsid());
        }
    }

    @Test
    @DisplayName("cId+cdva 단건 view 조회가 엔티티 조회와 필드·empty 계약에서 동등하다")
    void findResponseRowByCIdAndCdva_equalsEntityQuery_inFieldsAndEmptyContract() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cId = "BE03-SINGLE-" + suffix;
        persistCode(cId, "ACT", "20200101", "20991231", 5, "IOE_XPN");
        // 만료 코드 — 대상일 기준 단건 조회에서도 empty 계약 동등성 검증용
        persistCode(cId, "EXP", "20200101", "20260101", 1, "IOE_XPN");
        entityManager.flush();
        entityManager.clear();

        Optional<Ccodem> entityOne =
                codeRepository.findByCIdAndCdvaWithValidDate(cId, "ACT", TARGET_DATE);
        Optional<CcodemResponseRow> rowOne =
                codeRepository.findResponseRowByCIdAndCdvaWithValidDate(cId, "ACT", TARGET_DATE);
        assertThat(entityOne).isPresent();
        assertThat(rowOne).isPresent();
        assertThat(rowOne.get().cdva()).isEqualTo(entityOne.get().getCdva());
        assertThat(rowOne.get().cTp()).isEqualTo(entityOne.get().getCTp());
        assertThat(rowOne.get().cSqn()).isEqualTo(entityOne.get().getCSqn());
        assertThat(rowOne.get().sttDt()).isEqualTo(entityOne.get().getSttDt());
        assertThat(rowOne.get().endDt()).isEqualTo(entityOne.get().getEndDt());

        // 만료 코드는 유효일 조건에 걸려 양쪽 모두 empty
        assertThat(codeRepository.findByCIdAndCdvaWithValidDate(cId, "EXP", TARGET_DATE))
                .isEmpty();
        assertThat(codeRepository.findResponseRowByCIdAndCdvaWithValidDate(cId, "EXP", TARGET_DATE))
                .isEmpty();

        // 존재하지 않는 코드값은 양쪽 모두 empty
        assertThat(codeRepository.findByCIdAndCdvaWithValidDate(cId, "NONE", TARGET_DATE))
                .isEmpty();
        assertThat(
                        codeRepository.findResponseRowByCIdAndCdvaWithValidDate(
                                cId, "NONE", TARGET_DATE))
                .isEmpty();
    }

    @Test
    @DisplayName("cTp 기준 view 조회가 엔티티 조회와 정렬·유효일 경계에서 동등하다")
    void findResponseRowsByCTp_equalsEntityQuery_inSortAndValidDateBoundary() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String cTp = "BE03-CTP-" + suffix;
        String cId = "BE03-CTP-CID-" + suffix;

        persistCode(cId, "Z-LOW", "20200101", "20991231", 2, cTp);
        persistCode(cId, "A-HIGH", "20200101", "20991231", 1, cTp);
        persistCode(cId, "N-NULL", "20200101", "20991231", null, cTp);
        // 미래 코드 — cTp 조회에서도 제외되어야 함
        persistCode(cId, "FUTURE", "20261231", "20991231", 0, cTp);
        entityManager.flush();
        entityManager.clear();

        List<Ccodem> entities = codeRepository.findByCTpWithValidDate(cTp, TARGET_DATE);
        List<CcodemResponseRow> rows =
                codeRepository.findResponseRowsByCTpWithValidDate(cTp, TARGET_DATE);

        assertThat(entities)
                .extracting(Ccodem::getCdva)
                .containsExactly("A-HIGH", "Z-LOW", "N-NULL");
        assertThat(rows)
                .extracting(CcodemResponseRow::cdva)
                .containsExactly("A-HIGH", "Z-LOW", "N-NULL");
    }

    private void persistCode(
            String cId, String cdva, String sttDt, String endDt, Integer cSqn, String cTp) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 1, 9, 0);
        entityManager.persist(
                Ccodem.builder()
                        .cId(cId)
                        .cdva(cdva)
                        .sttDt(sttDt)
                        .endDt(endDt)
                        .cSqn(cSqn)
                        .cTp(cTp)
                        .cdvaNm("코드값명-" + cdva)
                        .cNm("코드명-" + cdva)
                        .cdvaDes("약어-" + cdva)
                        .cdvaDtl("적요-" + cdva)
                        .cdvaDtlC("상세코드-" + cdva)
                        .cTpDes("인스턴스내용-" + cdva)
                        .hrkC(null)
                        .fstEnrDtm(now)
                        .fstEnrUsid("BE03-TEST")
                        .lstChgDtm(now)
                        .lstChgUsid("BE03-TEST")
                        .delYn("N")
                        .build());
    }
}
