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
        return Cmenum.builder().mnuId(id).hrkMnuId(parent).sreTc("01").mnuNm(id)
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
    void adminTree_returnsEverything_withoutPruning() {
        given(cmenumRepository.findAllActive()).willReturn(List.of(
                node("H", null, "LNK", 1, "/H")
        ));
        // getAdminMenuTree does not consult permissions
        List<MenuDto.Node> all = service.getAdminMenuTree();
        assertThat(all).extracting(MenuDto.Node::getMnuId).containsExactly("H");
    }

    @Test
    void dynNode_getsChildrenFromMatchingResolver() {
        Cmenum dyn = Cmenum.builder().mnuId("MBRD0001").hrkMnuId(null).sreTc("04").mnuNm("게시판")
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
}
