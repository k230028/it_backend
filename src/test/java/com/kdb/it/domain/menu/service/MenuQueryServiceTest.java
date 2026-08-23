package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.common.i18n.model.SupportedLanguage;
import com.kdb.it.common.i18n.model.TranslationColumns;
import com.kdb.it.common.i18n.model.TranslationTarget;
import com.kdb.it.common.i18n.service.TranslationCatalogService;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.menu.repository.MenuTreeRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenuQueryServiceTest {

    @Mock CmenumRepository cmenumRepository;
    // 권한 매핑은 별도 캐시 빈(MenuAuthMapProvider)에서 제공받으므로 provider를 모킹한다(self-invocation 회피, T13-C).
    @Mock MenuAuthMapProvider menuAuthMapProvider;
    // 게시판 PGE 경로가 가리키는 게시판이 아직 살아 있는지 판정하는 원천.
    @Mock BoardMetaService boardMetaService;
    @Mock TranslationCatalogService translationCatalogService;
    // 준비중 안내 문구(비고)의 원천인 라우트 카탈로그.
    @Mock CmenudRepository cmenudRepository;

    MenuQueryService service;

    @BeforeEach
    void setUp() {
        service =
                new MenuQueryService(
                        cmenumRepository,
                        menuAuthMapProvider,
                        boardMetaService,
                        translationCatalogService,
                        cmenudRepository);
        // 기본은 컬럼이 있는 정상 환경. 부재 시나리오 테스트만 이 스텁을 뒤집는다.
        lenient().when(cmenumRepository.isIconColumnPresent()).thenReturn(true);
    }

    private MenuTreeRow node(String id, String parent, String type, int dep, String path) {
        return row(id, parent, type, dep, path, null, null);
    }

    private MenuTreeRow row(
            String id,
            String parent,
            String type,
            int dep,
            String path,
            String srePth,
            String imkNm) {
        return new MenuTreeRow(id, parent, id, type, srePth, 10, "N", dep, path, imkNm);
    }

    @Test
    void buildsTree_andFiltersByRole_pruningEmptyGroups() {
        // GRP 'G' (admin-only) with one PGE child 'C'; and public PGE 'P'
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("G", null, "GRP", 1, "/G"),
                                node("C", "G", "PGE", 2, "/G/C"),
                                node("P", null, "PGE", 1, "/P")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of("G", Set.of("ITPAD001")));

        // non-admin user: only 'P' visible (G+C pruned because G requires ITPAD001)
        List<MenuDto.Node> userTree = service.getMenuTree(List.of("ITPZZ001"));
        assertThat(userTree).extracting(value -> value.getMnuId()).containsExactly("P");

        // admin: G (with child C) + P
        List<MenuDto.Node> adminTree = service.getMenuTree(List.of("ITPAD001"));
        assertThat(adminTree)
                .extracting(value -> value.getMnuId())
                .containsExactlyInAnyOrder("G", "P");
        MenuDto.Node g =
                adminTree.stream().filter(n -> n.getMnuId().equals("G")).findFirst().orElseThrow();
        assertThat(g.getChildren()).extracting(value -> value.getMnuId()).containsExactly("C");
    }

    @Test
    void tree_carriesMenuIcon() {
        // 아이콘은 프론트 하드코딩 맵이 아니라 메뉴 행이 단일 출처다 — 트리에 실려 나가야 한다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                row("A", null, "PGE", 1, "/A", null, "pi pi-home"),
                                node("B", null, "PGE", 1, "/B")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree)
                .extracting(MenuDto.Node::getMnuId, MenuDto.Node::getImkNm)
                .containsExactly(tuple("A", "pi pi-home"), tuple("B", null));
    }

    @Test
    void userTree_carriesAthIds_forCrownIndicator() {
        // 사용자 트리도 노드별 athIds를 실어야 사이드바/헤더가 관리자(왕관) 메뉴를 표시할 수 있다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(node("A", null, "PGE", 1, "/A"), node("P", null, "PGE", 1, "/P")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of("A", Set.of("ITPAD001")));

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPAD001"));

        MenuDto.Node a =
                tree.stream().filter(n -> n.getMnuId().equals("A")).findFirst().orElseThrow();
        MenuDto.Node p =
                tree.stream().filter(n -> n.getMnuId().equals("P")).findFirst().orElseThrow();
        assertThat(a.getAthIds()).containsExactly("ITPAD001");
        // 권한 매핑이 없는 공개 메뉴는 빈 목록(전체 공개)으로 내려간다.
        assertThat(p.getAthIds()).isEmpty();
    }

    @Test
    void adminTree_returnsEverything_withoutPruning() {
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(List.of(node("H", null, "PGE", 1, "/H")));
        // 관리 트리는 가지치기 없이 전체를 반환하고, 편집 폼용으로 노드별 athIds를 함께 싣는다.
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of("H", Set.of("ITPAD001")));
        List<MenuDto.Node> all = service.getAdminMenuTree();
        assertThat(all).extracting(value -> value.getMnuId()).containsExactly("H");
        // Bug 2 회귀 방지: 관리 트리 노드가 기존 권한ID를 실어야 편집 화면 체크박스가 복원된다.
        assertThat(all.get(0).getAthIds()).containsExactly("ITPAD001");
    }

    // =========================================================================
    // 게시판 PGE 경로 — 연결된 게시판 상태에 따른 노출
    // =========================================================================

    /** 게시판 메뉴 노드. 화면경로가 게시판을 가리키는 유일한 연결 고리다. */
    private MenuTreeRow boardNode(String id, String blbMngNo) {
        return row(
                id, "MBRD0001", "PGE", 3, "/MHED0006/MBRD0001/" + id, "/board/" + blbMngNo, null);
    }

    private BoardMetaDto.Response activeBoard(String blbMngNo) {
        return BoardMetaDto.Response.builder()
                .blbMngNo(blbMngNo)
                .blbNm(blbMngNo)
                .useYn("Y")
                .build();
    }

    @Test
    void boardMenu_isHiddenFromUserTree_whenLinkedBoardIsInactive() {
        // 게시판이 삭제·미사용으로 바뀌어도 메뉴 행은 남는다 — 사용자 트리에서만 감춰야 한다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("MBRD0001", null, "GRP", 1, "/MBRD0001"),
                                boardNode("B1", "BLBM-0001"),
                                boardNode("B2", "BLBM-0002")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());
        given(boardMetaService.getAllActive()).willReturn(List.of(activeBoard("BLBM-0001")));

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree).extracting(MenuDto.Node::getMnuId).containsExactly("MBRD0001");
        assertThat(tree.get(0).getChildren())
                .extracting(MenuDto.Node::getMnuId)
                .containsExactly("B1");
    }

    @Test
    void boardMenu_staysInAdminTree_evenWhenLinkedBoardIsInactive() {
        // 관리자는 끊어진 연결을 보고 고쳐야 하므로 관리 트리에서는 감추지 않는다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("MBRD0001", null, "GRP", 1, "/MBRD0001"),
                                boardNode("B2", "BLBM-0002")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getAdminMenuTree();

        assertThat(tree.get(0).getChildren())
                .extracting(MenuDto.Node::getMnuId)
                .containsExactly("B2");
    }

    @Test
    void boardListIsNotQueried_whenTreeHasNoBoardMenu() {
        // 게시판 메뉴가 없는 트리에서까지 게시판을 조회하면 메뉴 조회마다 불필요한 쿼리가 는다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("P", null, "PGE", 1, "/P"),
                                row(
                                        "L",
                                        null,
                                        "LNK",
                                        1,
                                        "/L",
                                        "https://docs.example.com/manual",
                                        null)));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree).extracting(MenuDto.Node::getMnuId).containsExactly("P", "L");
        verifyNoInteractions(boardMetaService);
    }

    @Test
    void malformedBoardPath_isHiddenFromUserTree() {
        // /board/ 접두사로 시작한 값은 형식이 깨져도 게시판 후보이므로 사용자에게 노출하지 않는다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("MBRD0001", null, "GRP", 1, "/MBRD0001"),
                                row(
                                        "B0",
                                        "MBRD0001",
                                        "PGE",
                                        3,
                                        "/MHED0006/MBRD0001/B0",
                                        "/board/BLBM-0001/posts",
                                        null)));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());
        given(boardMetaService.getAllActive()).willReturn(List.of(activeBoard("BLBM-0001")));

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        // 자식이 모두 사라진 GRP는 가지치기된다.
        assertThat(tree).isEmpty();
    }

    @Test
    void rootGroup_isPruned_whenAllChildrenUnauthorized_butKept_whenPlaceholderVisible() {
        // 관리자 그룹 H1: admin 전용 자식 A. CDP 그룹 H2: 공개 플레이스홀더 P.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                node("H1", null, "GRP", 1, "/H1"),
                                node("A", "H1", "PGE", 2, "/H1/A"),
                                node("H2", null, "GRP", 1, "/H2"),
                                node("P", "H2", "PGE", 2, "/H2/P")));
        given(menuAuthMapProvider.getMenuAuthMap())
                .willReturn(
                        Map.of(
                                "H1", Set.of("ITPAD001"),
                                "A", Set.of("ITPAD001")));

        // 비관리자: H1(관리자 그룹) 숨김, H2(CDP)는 플레이스홀더 P 덕분에 유지
        List<MenuDto.Node> userTree = service.getMenuTree(List.of("ITPZZ001"));
        assertThat(userTree).extracting(value -> value.getMnuId()).containsExactly("H2");

        // 관리자: H1 + H2 모두 노출
        List<MenuDto.Node> adminTree = service.getMenuTree(List.of("ITPAD001"));
        assertThat(adminTree)
                .extracting(value -> value.getMnuId())
                .containsExactlyInAnyOrder("H1", "H2");
    }

    @Test
    void grp_keepsStaticChildrenFromMenuTable() {
        MenuTreeRow grp = node("G1", null, "GRP", 1, "/G1");
        MenuTreeRow child = node("C1", "G1", "PGE", 2, "/G1/C1");
        given(cmenumRepository.findActiveMenuTreeRows(true)).willReturn(List.of(grp, child));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree).extracting(MenuDto.Node::getMnuId).containsExactly("G1");
        assertThat(tree.get(0).getChildren())
                .extracting(MenuDto.Node::getMnuId)
                .containsExactly("C1");
    }

    // =========================================================================
    // IMK_NM 컬럼 부재 환경
    // =========================================================================

    @Test
    @DisplayName("컬럼이 없으면 사용자 트리에 메뉴별 기본 아이콘이 채워진다")
    void userTree_fillsDefaultIcons_whenIconColumnMissing() {
        given(cmenumRepository.isIconColumnPresent()).willReturn(false);
        given(cmenumRepository.findActiveMenuTreeRows(false))
                .willReturn(
                        List.of(
                                // 스냅샷에 있는 메뉴 → 기본 아이콘
                                node("MHED0001", null, "PGE", 1, "/MHED0001"),
                                // 스냅샷 이후 생성된 메뉴 → null (프론트가 DEFAULT_MENU_ICON으로 받는다)
                                node("MNU9999999", null, "PGE", 1, "/N")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree)
                .extracting(MenuDto.Node::getMnuId, MenuDto.Node::getImkNm)
                .containsExactly(tuple("MHED0001", "pi pi-file-check"), tuple("MNU9999999", null));
    }

    @Test
    @DisplayName("컬럼이 없으면 관리 트리에도 같은 기본 아이콘이 채워진다")
    void adminTree_fillsDefaultIcons_whenIconColumnMissing() {
        given(cmenumRepository.isIconColumnPresent()).willReturn(false);
        given(cmenumRepository.findActiveMenuTreeRows(false))
                .willReturn(List.of(node("MADM0001", null, "PGE", 1, "/MADM0001")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getAdminMenuTree();

        assertThat(tree).extracting(MenuDto.Node::getImkNm).containsExactly("pi pi-sitemap");
    }

    @Test
    @DisplayName("컬럼이 있으면 DB의 null을 기본 아이콘으로 되살리지 않는다")
    void iconColumnPresent_keepsNullAsNull() {
        // 관리자가 일부러 비운 아이콘을 서버가 되살리면 '아이콘 단일 출처 = 메뉴 행'이 깨진다.
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(List.of(node("MHED0001", null, "PGE", 1, "/MHED0001")));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree).extracting(MenuDto.Node::getImkNm).containsOnlyNulls();
    }

    @Test
    void 영어메뉴는_번역된_필드만_덮어쓰고_나머지는_한국어를_유지한다() {
        given(cmenumRepository.findActiveMenuTreeRows(true))
                .willReturn(
                        List.of(
                                row("ROOT", null, "GRP", 1, "/ROOT", null, null),
                                row("CHILD", "ROOT", "PGE", 2, "/ROOT/CHILD", null, null)));
        given(menuAuthMapProvider.getMenuAuthMap()).willReturn(Map.of());
        given(
                        translationCatalogService.findActive(
                                TranslationTarget.MENU,
                                SupportedLanguage.EN,
                                List.of("ROOT", "CHILD")))
                .willReturn(Map.of("ROOT", Map.of(TranslationColumns.MNU_NM, "Administration")));

        List<MenuDto.Node> tree = service.getMenuTree(List.of(), SupportedLanguage.EN);

        assertThat(tree.getFirst().getMnuNm()).isEqualTo("Administration");
        assertThat(tree.getFirst().getChildren().getFirst().getMnuNm()).isEqualTo("CHILD");
    }

    // =========================================================================
    // 준비중 안내 문구(getPreparingNotice)
    // =========================================================================

    /** 라우트 카탈로그 행. 준비중 안내 조회는 비고만 읽으므로 나머지는 최소값으로 채운다. */
    private Cmenud route(String srePth, String rmk) {
        return Cmenud.builder()
                .srePth(srePth)
                .sreMnuNm("준비중 (준비중)")
                .useYn("Y")
                .rmk(rmk)
                .delYn("N")
                .build();
    }

    @Test
    @DisplayName("관리자가 적은 비고는 준비중 안내 문구로 그대로 나간다")
    void 준비중안내_사람이쓴비고_그대로반환() {
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mcdp0001", "N"))
                .willReturn(Optional.of(route("/preparing/mcdp0001", "2027년 1월 오픈 예정")));

        MenuDto.PreparingNotice notice = service.getPreparingNotice("/preparing/mcdp0001");

        assertThat(notice.getSrePth()).isEqualTo("/preparing/mcdp0001");
        assertThat(notice.getRmk()).isEqualTo("2027년 1월 오픈 예정");
    }

    @Test
    @DisplayName("자동 등록 표시뿐인 비고는 안내 문구가 아니므로 내보내지 않는다")
    void 준비중안내_자동등록표시_null반환() {
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mcdp0001", "N"))
                .willReturn(
                        Optional.of(
                                route("/preparing/mcdp0001", MenuPathPolicy.PREPARING_ROUTE_RMK)));

        assertThat(service.getPreparingNotice("/preparing/mcdp0001").getRmk()).isNull();
    }

    @Test
    @DisplayName("비고가 공백뿐이거나 카탈로그에 행이 없으면 안내 문구가 없다")
    void 준비중안내_빈비고와_행없음_null반환() {
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/blank", "N"))
                .willReturn(Optional.of(route("/preparing/blank", "   ")));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/missing", "N"))
                .willReturn(Optional.empty());

        assertThat(service.getPreparingNotice("/preparing/blank").getRmk()).isNull();
        assertThat(service.getPreparingNotice("/preparing/missing").getRmk()).isNull();
    }

    @Test
    @DisplayName("준비중이 아닌 경로는 카탈로그를 조회하지 않는다 — 임의 경로로 비고를 훑을 수 없다")
    void 준비중안내_준비중경로가_아니면_조회하지않는다() {
        MenuDto.PreparingNotice notice = service.getPreparingNotice("/admin/menus");

        assertThat(notice.getRmk()).isNull();
        verifyNoInteractions(cmenudRepository);
    }
}
