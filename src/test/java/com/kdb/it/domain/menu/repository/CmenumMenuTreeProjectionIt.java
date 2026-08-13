package com.kdb.it.domain.menu.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("메뉴 트리 경량 프로젝션의 엔티티 조회 동등성")
class CmenumMenuTreeProjectionIt extends AbstractOracleRepositoryTest {

    private static final String ICON_ID = "ZZPRJ00001";
    private static final String PLAIN_ID = "ZZPRJ00002";
    private static final String DELETED_ID = "ZZPRJ00003";

    @Autowired CmenumRepository menuRepository;

    @Test
    @DisplayName("로컬 스키마에는 IMK_NM이 있으므로 판정이 true다")
    void iconColumnIsPresentOnLocalSchema() {
        assertThat(menuRepository.isIconColumnPresent()).isTrue();
    }

    @Test
    @DisplayName("findActiveMenuTreeRows는 findAllActive와 동일한 행 집합·필드값·null 계약을 반환한다")
    void menuTreeRows_matchEntityQuery() {
        menuRepository.saveAllAndFlush(
                List.of(
                        menu(ICON_ID, "pi pi-home", "N"),
                        menu(PLAIN_ID, null, "N"),
                        menu(DELETED_ID, "pi pi-cog", "Y")));

        Map<String, Cmenum> entities =
                menuRepository.findAllActive().stream()
                        .filter(e -> e.getMnuId().startsWith("ZZPRJ"))
                        .collect(Collectors.toMap(Cmenum::getMnuId, Function.identity()));
        Map<String, MenuTreeRow> rows =
                menuRepository.findActiveMenuTreeRows().stream()
                        .filter(r -> r.mnuId().startsWith("ZZPRJ"))
                        .collect(Collectors.toMap(MenuTreeRow::mnuId, Function.identity()));

        // DEL_YN='Y'는 양쪽 모두에서 제외된다
        assertThat(entities.keySet()).containsExactlyInAnyOrder(ICON_ID, PLAIN_ID);
        assertThat(rows.keySet()).containsExactlyInAnyOrder(ICON_ID, PLAIN_ID);

        for (Map.Entry<String, Cmenum> entry : entities.entrySet()) {
            Cmenum e = entry.getValue();
            MenuTreeRow r = rows.get(entry.getKey());
            assertThat(r.hrkMnuId()).isEqualTo(e.getHrkMnuId());
            assertThat(r.mnuNm()).isEqualTo(e.getMnuNm());
            assertThat(r.mnuTpC()).isEqualTo(e.getMnuTpC());
            assertThat(r.srePth()).isEqualTo(e.getSrePth());
            assertThat(r.mnuSotSqnSno()).isEqualTo(e.getMnuSotSqnSno());
            assertThat(r.hidYn()).isEqualTo(e.getHidYn());
            assertThat(r.mnuDep()).isEqualTo(e.getMnuDep());
            assertThat(r.whlMnuPth()).isEqualTo(e.getWhlMnuPth());
            // null 계약: 아이콘이 없는 행은 프로젝션에서도 null이어야 한다
            assertThat(r.imkNm()).isEqualTo(e.getImkNm());
        }

        assertThat(rows.get(ICON_ID).imkNm()).isEqualTo("pi pi-home");
        assertThat(rows.get(PLAIN_ID).imkNm()).isNull();
    }

    @Test
    @DisplayName("전체 활성 메뉴 건수가 엔티티 조회와 일치한다")
    void rowCount_matchesEntityQuery() {
        assertThat(menuRepository.findActiveMenuTreeRows())
                .hasSameSizeAs(menuRepository.findAllActive());
    }

    private Cmenum menu(String mnuId, String imkNm, String delYn) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 13, 12, 0);
        return Cmenum.builder()
                .mnuId(mnuId)
                .hrkMnuId(null)
                .mnuNm("프로젝션테스트-" + mnuId)
                .mnuTpC("PGE")
                .srePth("/projection-test/" + mnuId)
                .mnuSotSqnSno(900)
                .hidYn("N")
                .mnuDep(1)
                .whlMnuPth("/" + mnuId)
                .imkNm(imkNm)
                .delYn(delYn)
                .fstEnrUsid("TEST")
                .fstEnrDtm(now)
                .lstChgUsid("TEST")
                .lstChgDtm(now)
                .guid(UUID.randomUUID().toString())
                .guidPrgSno(1)
                .build();
    }
}
