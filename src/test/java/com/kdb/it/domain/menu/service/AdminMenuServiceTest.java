package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminMenuServiceTest {

    @Mock CmenumRepository cmenumRepository;
    @Mock CmenuaRepository cmenuaRepository;
    @Mock CmenudRepository cmenudRepository;
    @InjectMocks AdminMenuService service;

    private Cmenum node(String id, String parent, int dep, String path) {
        return Cmenum.builder().mnuId(id).hrkMnuId(parent).sysHrkMnuId("01").mnuNm(id)
                .mnuTpC("GRP").mnuSotSqnSno(10).hidYn("N").mnuDep(dep).whlMnuPth(path).delYn("N").build();
    }

    @Test
    void move_recalculatesPathAndDepthForNodeAndDescendants() {
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum child  = node("C", "B", 3, "/A/B/C");
        Cmenum newParent = node("X", null, 1, "/X");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("X", "N")).willReturn(Optional.of(newParent));
        given(cmenumRepository.findSubtreeByPathPrefix("/A/B")).willReturn(List.of(target, child));

        service.move("B", "X");

        assertThat(target.getHrkMnuId()).isEqualTo("X");
        assertThat(target.getMnuDep()).isEqualTo(2);          // /X (1) + B = 2
        assertThat(target.getWhlMnuPth()).isEqualTo("/X/B");
        assertThat(child.getMnuDep()).isEqualTo(3);
        assertThat(child.getWhlMnuPth()).isEqualTo("/X/B/C");
    }

    @Test
    void move_rejectsCycle_whenNewParentIsDescendant() {
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum desc   = node("C", "B", 3, "/A/B/C");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("C", "N")).willReturn(Optional.of(desc));

        assertThatThrownBy(() -> service.move("B", "C"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("순환");
    }

    @Test
    void move_rejectsWhenResultingDepthExceedsThree() {
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum child  = node("C", "B", 3, "/A/B/C");        // moving B under depth-2 parent -> C becomes depth 4
        Cmenum newParent = node("P", "Q", 2, "/Q/P");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("P", "N")).willReturn(Optional.of(newParent));
        given(cmenumRepository.findSubtreeByPathPrefix("/A/B")).willReturn(List.of(target, child));

        assertThatThrownBy(() -> service.move("B", "P"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("깊이");
    }

    @Test
    void delete_rejectsWhenChildrenExist() {
        given(cmenumRepository.findByMnuIdAndDelYn("A", "N")).willReturn(Optional.of(node("A", null, 1, "/A")));
        given(cmenumRepository.countActiveChildren("A")).willReturn(2L);

        assertThatThrownBy(() -> service.delete("A"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("하위");
    }

    @Test
    void reorder_assignsIncrementingSortNumbers() {
        Cmenum a = node("A", "P", 2, "/P/A");
        Cmenum b = node("B", "P", 2, "/P/B");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(b));
        given(cmenumRepository.findByMnuIdAndDelYn("A", "N")).willReturn(Optional.of(a));

        service.reorder(List.of("B", "A"));

        assertThat(b.getMnuSotSqnSno()).isEqualTo(10);
        assertThat(a.getMnuSotSqnSno()).isEqualTo(20);
    }
}
