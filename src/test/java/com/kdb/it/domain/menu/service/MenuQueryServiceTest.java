package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MenuQueryServiceTest {

    @Mock CmenumRepository cmenumRepository;
    @Mock CmenuaRepository cmenuaRepository;
    MenuQueryService service;   // resolvers가 테스트마다 달라 per-test로 생성

    @BeforeEach
    void setUp() {
        service = new MenuQueryService(cmenumRepository, cmenuaRepository, List.of());
    }

    private Cmenum node(String id, String parent, String type, int dep, String path) {
        return Cmenum.builder().mnuId(id).hrkMnuId(parent).mnuNm(id)
                .mnuTpC(type).mnuSotSqnSno(10).hidYn("N").mnuDep(dep).whlMnuPth(path).delYn("N").build();
    }

    @Test
    void buildsTree_andFiltersByRole_pruningEmptyGroups() {
        // GRP 'G' (admin-only) with one LNK child 'C'; and public LNK 'P'
        given(cmenumRepository.findAllActive()).willReturn(List.of(
                node("G", null, "GRP", 1, "/G"),
                node("C", "G", "LNK", 2, "/G/C"),
                node("P", null, "LNK", 1, "/P")
        ));
        given(cmenuaRepository.findAllActive()).willReturn(List.of(
                Cmenua.builder().mnuId("G").athId("ITPAD001").delYn("N").build()
        ));

        // non-admin user: only 'P' visible (G+C pruned because G requires ITPAD001)
        List<MenuDto.Node> userTree = service.getMenuTree(List.of("ITPZZ001"));
        assertThat(userTree).extracting(MenuDto.Node::getMnuId).containsExactly("P");

        // admin: G (with child C) + P
        List<MenuDto.Node> adminTree = service.getMenuTree(List.of("ITPAD001"));
        assertThat(adminTree).extracting(MenuDto.Node::getMnuId).containsExactlyInAnyOrder("G", "P");
        MenuDto.Node g = adminTree.stream().filter(n -> n.getMnuId().equals("G")).findFirst().orElseThrow();
        assertThat(g.getChildren()).extracting(MenuDto.Node::getMnuId).containsExactly("C");
    }

    @Test
    void userTree_carriesAthIds_forCrownIndicator() {
        // 사용자 트리도 노드별 athIds를 실어야 사이드바/헤더가 관리자(왕관) 메뉴를 표시할 수 있다.
        given(cmenumRepository.findAllActive()).willReturn(List.of(
                node("A", null, "LNK", 1, "/A"),
                node("P", null, "LNK", 1, "/P")
        ));
        given(cmenuaRepository.findAllActive()).willReturn(List.of(
                Cmenua.builder().mnuId("A").athId("ITPAD001").delYn("N").build()
        ));

        List<MenuDto.Node> tree = service.getMenuTree(List.of("ITPAD001"));

        MenuDto.Node a = tree.stream().filter(n -> n.getMnuId().equals("A")).findFirst().orElseThrow();
        MenuDto.Node p = tree.stream().filter(n -> n.getMnuId().equals("P")).findFirst().orElseThrow();
        assertThat(a.getAthIds()).containsExactly("ITPAD001");
        // 권한 매핑이 없는 공개 메뉴는 빈 목록(전체 공개)으로 내려간다.
        assertThat(p.getAthIds()).isEmpty();
    }

    @Test
    void adminTree_returnsEverything_withoutPruning() {
        given(cmenumRepository.findAllActive()).willReturn(List.of(
                node("H", null, "LNK", 1, "/H")
        ));
        // 관리 트리는 가지치기 없이 전체를 반환하고, 편집 폼용으로 노드별 athIds를 함께 싣는다.
        given(cmenuaRepository.findAllActive()).willReturn(List.of(
                Cmenua.builder().mnuId("H").athId("ITPAD001").delYn("N").build()
        ));
        List<MenuDto.Node> all = service.getAdminMenuTree();
        assertThat(all).extracting(MenuDto.Node::getMnuId).containsExactly("H");
        // Bug 2 회귀 방지: 관리 트리 노드가 기존 권한ID를 실어야 편집 화면 체크박스가 복원된다.
        assertThat(all.get(0).getAthIds()).containsExactly("ITPAD001");
    }

    @Test
    void dynNode_getsChildrenFromMatchingResolver() {
        Cmenum dyn = Cmenum.builder().mnuId("MBRD0001").hrkMnuId(null).mnuNm("게시판")
                .mnuTpC("DYN").mnuSotSqnSno(10).hidYn("N").mnuDep(1).whlMnuPth("/MBRD0001").delYn("N").build();
        given(cmenumRepository.findAllActive()).willReturn(List.of(dyn));
        given(cmenuaRepository.findAllActive()).willReturn(List.of());

        MenuChildrenResolver fake = new MenuChildrenResolver() {
            public String mnuId() { return "MBRD0001"; }
            public List<MenuDto.Node> resolveChildren(List<String> athIds) {
                // 실제 BoardListMenuResolver처럼 자식 노드의 children을 불변 빈 리스트로 설정해
                // sortRecursive의 in-place 정렬이 UnsupportedOperationException을 던지지 않는지 회귀 검증.
                return List.of(MenuDto.Node.builder().mnuId("MBRD-B1").mnuNm("공지").mnuTpC("LNK")
                        .srePth("/board/BLBM-0001").children(List.of()).build());
            }
        };
        MenuQueryService svc = new MenuQueryService(cmenumRepository, cmenuaRepository, List.of(fake));
        List<MenuDto.Node> tree = svc.getMenuTree(List.of("ITPZZ001"));

        assertThat(tree).extracting(MenuDto.Node::getMnuId).containsExactly("MBRD0001");
        assertThat(tree.get(0).getChildren()).extracting(MenuDto.Node::getMnuId).containsExactly("MBRD-B1");
    }

    @Test
    void hedHeader_isPruned_whenAllChildrenUnauthorized_butKept_whenPlaceholderVisible() {
        // 관리자 헤더 H1: admin 전용 자식 A. CDP 헤더 H2: 공개 플레이스홀더 P.
        given(cmenumRepository.findAllActive()).willReturn(List.of(
                node("H1", null, "HED", 1, "/H1"),
                node("A",  "H1", "LNK", 2, "/H1/A"),
                node("H2", null, "HED", 1, "/H2"),
                node("P",  "H2", "LNK", 2, "/H2/P")
        ));
        given(cmenuaRepository.findAllActive()).willReturn(List.of(
                Cmenua.builder().mnuId("H1").athId("ITPAD001").delYn("N").build(),
                Cmenua.builder().mnuId("A").athId("ITPAD001").delYn("N").build()
        ));

        // 비관리자: H1(관리자 헤더) 숨김, H2(CDP)는 플레이스홀더 P 덕분에 유지
        List<MenuDto.Node> userTree = service.getMenuTree(List.of("ITPZZ001"));
        assertThat(userTree).extracting(MenuDto.Node::getMnuId).containsExactly("H2");

        // 관리자: H1 + H2 모두 노출
        List<MenuDto.Node> adminTree = service.getMenuTree(List.of("ITPAD001"));
        assertThat(adminTree).extracting(MenuDto.Node::getMnuId).containsExactlyInAnyOrder("H1", "H2");
    }
}
