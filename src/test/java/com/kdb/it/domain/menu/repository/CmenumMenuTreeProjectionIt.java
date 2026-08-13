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
                menuRepository.findActiveMenuTreeRows(menuRepository.isIconColumnPresent()).stream()
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
        assertThat(menuRepository.findActiveMenuTreeRows(menuRepository.isIconColumnPresent()))
                .hasSameSizeAs(menuRepository.findAllActive());
    }

    @Test
    @DisplayName("IMK_NM이 있는 로컬 스키마에서도 false 인자는 컬럼 부재 환경과 같은 행·필드를 반환하고 아이콘만 null로 접는다")
    void findActiveMenuTreeRows_falseBranch_matchesTrueBranchExceptIcon() {
        // 로컬 스키마는 IMK_NM 컬럼이 있어 findActiveMenuTreeRows(false)를 호출해도 컬럼이 없는
        // 환경을 실제로 재현할 수는 없다. 하지만 false를 넘기면 QueryDSL이 IMK_NM을 select 목록에서
        // 아예 빼고 9인자 보조 생성자로 행을 만드는 것은 이 스키마에서도 동일하게 유효한 SQL이므로,
        // 컬럼 부재 환경이 받게 될 select문과 결과 조립 경로를 이 스키마에서 그대로 실 DB로 검증할 수 있다.
        String iconId = "ZZCOL00001";
        String plainId = "ZZCOL00002";

        menuRepository.saveAllAndFlush(
                List.of(menu(iconId, "pi pi-star", "N"), menu(plainId, null, "N")));

        Map<String, MenuTreeRow> withIcon =
                menuRepository.findActiveMenuTreeRows(true).stream()
                        .filter(r -> r.mnuId().startsWith("ZZCOL"))
                        .collect(Collectors.toMap(MenuTreeRow::mnuId, Function.identity()));
        Map<String, MenuTreeRow> withoutIcon =
                menuRepository.findActiveMenuTreeRows(false).stream()
                        .filter(r -> r.mnuId().startsWith("ZZCOL"))
                        .collect(Collectors.toMap(MenuTreeRow::mnuId, Function.identity()));

        // 컬럼 부재 분기라고 해서 행이 빠지거나 늘어나서는 안 된다
        assertThat(withoutIcon.keySet()).containsExactlyInAnyOrderElementsOf(withIcon.keySet());
        assertThat(withoutIcon).hasSameSizeAs(withIcon);

        for (Map.Entry<String, MenuTreeRow> entry : withIcon.entrySet()) {
            MenuTreeRow trueRow = entry.getValue();
            MenuTreeRow falseRow = withoutIcon.get(entry.getKey());
            // imkNm을 제외한 나머지 필드는 9인자 보조 생성자가 인자 순서를 그대로 옮겨 채운다는 증거다
            assertThat(falseRow.mnuId()).isEqualTo(trueRow.mnuId());
            assertThat(falseRow.hrkMnuId()).isEqualTo(trueRow.hrkMnuId());
            assertThat(falseRow.mnuNm()).isEqualTo(trueRow.mnuNm());
            assertThat(falseRow.mnuTpC()).isEqualTo(trueRow.mnuTpC());
            assertThat(falseRow.srePth()).isEqualTo(trueRow.srePth());
            assertThat(falseRow.mnuSotSqnSno()).isEqualTo(trueRow.mnuSotSqnSno());
            assertThat(falseRow.hidYn()).isEqualTo(trueRow.hidYn());
            assertThat(falseRow.mnuDep()).isEqualTo(trueRow.mnuDep());
            assertThat(falseRow.whlMnuPth()).isEqualTo(trueRow.whlMnuPth());
        }

        // DB 값이 non-null인 행(iconId)도 false 분기에서는 무조건 null이어야 한다 — 이 분기의 핵심 계약
        assertThat(withIcon.get(iconId).imkNm()).isEqualTo("pi pi-star");
        assertThat(withoutIcon.values()).extracting(MenuTreeRow::imkNm).containsOnlyNulls();
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
