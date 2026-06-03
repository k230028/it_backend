package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 메뉴 트리 조회 + 서버 단 권한 필터링. 권한 판단은 전적으로 여기서 수행한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuQueryService {

    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    /** Plan 2에서 게시판 resolver 주입. Plan 1에서는 빈 리스트(하드코딩). */
    private final List<MenuChildrenResolver> resolvers = List.of();

    /** 사용자용: ROLE 필터 + HID_YN='N' + 빈 GRP/DYN 가지치기. */
    public List<MenuDto.Node> getMenuTree(List<String> athIds) {
        List<Cmenum> all = cmenumRepository.findAllActive();
        Map<String, Set<String>> athByMenu = athByMenu();
        Set<String> userAths = new HashSet<>(athIds == null ? List.of() : athIds);

        List<Cmenum> visible = all.stream()
                .filter(m -> !"Y".equals(m.getHidYn()))
                .filter(m -> isAllowed(m.getMnuId(), athByMenu, userAths))
                .collect(Collectors.toList());

        return prune(buildTree(visible, athIds), true);
    }

    /** 관리화면용: 숨김/권한/빈 그룹 무관하게 전체 트리. */
    public List<MenuDto.Node> getAdminMenuTree() {
        return buildTree(cmenumRepository.findAllActive(), null);
    }

    // ---- helpers ----

    private Map<String, Set<String>> athByMenu() {
        Map<String, Set<String>> map = new HashMap<>();
        for (Cmenua a : cmenuaRepository.findAllActive()) {
            map.computeIfAbsent(a.getMnuId(), k -> new HashSet<>()).add(a.getAthId());
        }
        return map;
    }

    /** 매핑 0건이면 전체 공개, 1건 이상이면 교집합 필요. */
    private boolean isAllowed(String mnuId, Map<String, Set<String>> athByMenu, Set<String> userAths) {
        Set<String> required = athByMenu.get(mnuId);
        if (required == null || required.isEmpty()) return true;
        return required.stream().anyMatch(userAths::contains);
    }

    private List<MenuDto.Node> buildTree(List<Cmenum> rows, List<String> athIds) {
        Map<String, MenuDto.Node> byId = new HashMap<>();
        for (Cmenum m : rows) byId.put(m.getMnuId(), toNode(m));
        List<MenuDto.Node> roots = new ArrayList<>();
        for (Cmenum m : rows) {
            MenuDto.Node nodeDto = byId.get(m.getMnuId());
            if ("DYN".equals(m.getMnuTpC()) && athIds != null) {
                nodeDto.setChildren(resolveDyn(m.getMnuId(), athIds));
            }
            if (m.getHrkMnuId() == null) {
                roots.add(nodeDto);
            } else {
                MenuDto.Node parent = byId.get(m.getHrkMnuId());
                // 부모가 권한 필터로 제외되어 보이지 않으면 자식(고아 노드)도 노출하지 않는다.
                if (parent == null) continue;
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(nodeDto);
            }
        }
        sortRecursive(roots);
        return roots;
    }

    private List<MenuDto.Node> resolveDyn(String mnuId, List<String> athIds) {
        return resolvers.stream()
                .filter(r -> r.mnuId().equals(mnuId))
                .findFirst()
                .map(r -> r.resolveChildren(athIds))
                .orElseGet(ArrayList::new);
    }

    private void sortRecursive(List<MenuDto.Node> nodes) {
        if (nodes == null) return;
        nodes.sort(Comparator.comparingInt(n -> n.getMnuSotSqnSno() == null ? 0 : n.getMnuSotSqnSno()));
        for (MenuDto.Node n : nodes) sortRecursive(n.getChildren());
    }

    /** 사용자 트리에서 children 0개가 된 GRP/DYN 노드 제거. */
    private List<MenuDto.Node> prune(List<MenuDto.Node> nodes, boolean isUserTree) {
        if (!isUserTree || nodes == null) return nodes;
        List<MenuDto.Node> kept = new ArrayList<>();
        for (MenuDto.Node n : nodes) {
            n.setChildren(prune(n.getChildren(), true));
            boolean container = "GRP".equals(n.getMnuTpC()) || "DYN".equals(n.getMnuTpC());
            boolean empty = n.getChildren() == null || n.getChildren().isEmpty();
            if (container && empty) continue;
            kept.add(n);
        }
        return kept;
    }

    private MenuDto.Node toNode(Cmenum m) {
        return MenuDto.Node.builder()
                .mnuId(m.getMnuId()).hrkMnuId(m.getHrkMnuId()).sreTc(m.getSreTc())
                .mnuNm(m.getMnuNm()).mnuTpC(m.getMnuTpC()).srePth(m.getSrePth())
                .mnuSotSqnSno(m.getMnuSotSqnSno()).hidYn(m.getHidYn())
                .mnuDep(m.getMnuDep()).whlMnuPth(m.getWhlMnuPth())
                .children(new ArrayList<>())
                .build();
    }
}
