package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 트리 조회 + 서버 단 권한 필터링. 권한 판단은 전적으로 여기서 수행한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuQueryService {

    private final CmenumRepository cmenumRepository;

    /** 메뉴 권한 매핑(menuAuthMap) 캐시 제공자. self-invocation 회피를 위해 별도 빈으로 분리(§Task T13-C). */
    private final MenuAuthMapProvider menuAuthMapProvider;

    /** Spring이 모든 MenuChildrenResolver 빈을 주입한다. 등록된 메뉴 ID의 자식 노드를 동적으로 생성한다. */
    private final List<MenuChildrenResolver> resolvers;

    /**
     * 사용자용 메뉴 트리를 조회한다.
     *
     * @param athIds JWT 클레임에서 복원한 자격등급 ID 목록. null이면 공개 메뉴만 반환한다.
     * @return 숨김 메뉴와 권한 불일치 메뉴를 제거하고, 빈 GRP 노드를 가지치기한 트리. 각 노드의 {@code athIds}에는 왕관 아이콘 표시 판정용 권한ID
     *     목록이 채워진다.
     */
    public List<MenuDto.Node> getMenuTree(List<String> athIds) {
        List<Cmenum> all = cmenumRepository.findAllActive();
        Map<String, Set<String>> athByMenu = menuAuthMapProvider.getMenuAuthMap();
        Set<String> userAths = new HashSet<>(athIds == null ? List.of() : athIds);

        List<Cmenum> visible =
                all.stream()
                        .filter(m -> !"Y".equals(m.getHidYn()))
                        .filter(m -> isAllowed(m.getMnuId(), athByMenu, userAths))
                        .toList();

        List<MenuDto.Node> tree = prune(buildTree(visible, athIds), true);
        // 사이드바/헤더가 관리자 전용 메뉴에 왕관 아이콘을 표시할 수 있도록 노드별 권한ID를 함께 싣는다.
        applyAthIds(tree, athByMenu);
        return tree;
    }

    /**
     * 관리화면용 전체 메뉴 트리를 조회합니다.
     *
     * @return 숨김·권한·빈 그룹을 제거하지 않고 노드별 권한ID를 포함한 전체 트리
     */
    public List<MenuDto.Node> getAdminMenuTree() {
        List<MenuDto.Node> tree = buildTree(cmenumRepository.findAllActive(), null);
        applyAthIds(tree, menuAuthMapProvider.getMenuAuthMap());
        return tree;
    }

    /** 트리 각 노드에 활성 권한 매핑을 채운다. 매핑 없으면 빈 목록(전체 공개). */
    private void applyAthIds(List<MenuDto.Node> nodes, Map<String, Set<String>> athByMenu) {
        if (nodes == null) return;
        for (MenuDto.Node n : nodes) {
            Set<String> aths = athByMenu.get(n.getMnuId());
            n.setAthIds(aths == null ? new ArrayList<>() : new ArrayList<>(aths));
            applyAthIds(n.getChildren(), athByMenu);
        }
    }

    // ---- 내부 헬퍼 ----

    /** 매핑 0건이면 전체 공개, 1건 이상이면 교집합 필요. */
    private boolean isAllowed(
            String mnuId, Map<String, Set<String>> athByMenu, Set<String> userAths) {
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
            // 동적 확장 여부는 유형이 아니라 resolver 등록 여부로 판단한다. resolveDyn이 이미
            // mnuId 일치로 resolver를 고르므로 유형 게이트는 같은 사실을 중복 표현한 것이었다.
            if (athIds != null && hasResolver(m.getMnuId())) {
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

    /** 이 메뉴 ID를 담당하는 동적 확장 resolver가 등록돼 있는지 확인한다. */
    private boolean hasResolver(String mnuId) {
        return resolvers != null && resolvers.stream().anyMatch(r -> r.mnuId().equals(mnuId));
    }

    /** 등록된 resolver로 동적 하위 노드를 만든다. 담당 resolver가 없으면 빈 목록을 돌려준다. */
    private List<MenuDto.Node> resolveDyn(String mnuId, List<String> athIds) {
        if (resolvers == null) return new ArrayList<>();
        return resolvers.stream()
                .filter(r -> r.mnuId().equals(mnuId))
                .findFirst()
                // resolver가 불변 리스트를 반환해도 이후 정렬/가지치기에서 제자리 변형이 가능하도록 복사한다.
                .map(r -> new ArrayList<>(r.resolveChildren(athIds)))
                .map(list -> (List<MenuDto.Node>) list)
                .orElseGet(ArrayList::new);
    }

    private void sortRecursive(List<MenuDto.Node> nodes) {
        // 빈 리스트는 정렬을 건너뛴다: 불변 빈 리스트(List.of())도 sort() 호출 시 UnsupportedOperationException을 던지므로 방어.
        if (nodes == null || nodes.isEmpty()) return;
        nodes.sort(
                Comparator.comparingInt(
                        n -> n.getMnuSotSqnSno() == null ? 0 : n.getMnuSotSqnSno()));
        for (MenuDto.Node n : nodes) sortRecursive(n.getChildren());
    }

    /** 사용자 트리에서 children 0개가 된 GRP 노드 제거. */
    private List<MenuDto.Node> prune(List<MenuDto.Node> nodes, boolean isUserTree) {
        if (!isUserTree || nodes == null) return nodes;
        List<MenuDto.Node> kept = new ArrayList<>();
        for (MenuDto.Node n : nodes) {
            n.setChildren(prune(n.getChildren(), true));
            boolean container = "GRP".equals(n.getMnuTpC());
            boolean empty = n.getChildren() == null || n.getChildren().isEmpty();
            if (container && empty) continue;
            kept.add(n);
        }
        return kept;
    }

    private MenuDto.Node toNode(Cmenum m) {
        return MenuDto.Node.builder()
                .mnuId(m.getMnuId())
                .hrkMnuId(m.getHrkMnuId())
                .mnuNm(m.getMnuNm())
                .mnuTpC(m.getMnuTpC())
                .srePth(m.getSrePth())
                .mnuSotSqnSno(m.getMnuSotSqnSno())
                .hidYn(m.getHidYn())
                .mnuDep(m.getMnuDep())
                .whlMnuPth(m.getWhlMnuPth())
                .children(new ArrayList<>())
                .build();
    }
}
