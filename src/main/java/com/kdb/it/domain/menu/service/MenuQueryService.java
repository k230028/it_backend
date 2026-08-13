package com.kdb.it.domain.menu.service;

import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.menu.repository.MenuTreeRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    /** 게시판 PGE 경로가 가리키는 게시판이 아직 사용 중인지 판정하는 원천. */
    private final BoardMetaService boardMetaService;

    /**
     * 사용자용 메뉴 트리를 조회한다.
     *
     * @param athIds JWT 클레임에서 복원한 자격등급 ID 목록. null이면 공개 메뉴만 반환한다.
     * @return 숨김 메뉴, 권한 불일치 메뉴, 사용 중이 아닌 게시판을 가리키는 PGE 메뉴를 제거하고, 빈 GRP 노드를 가지치기한 트리. 각 노드의 {@code
     *     athIds}에는 왕관 아이콘 표시 판정용 권한ID 목록이 채워진다. {@code IMK_NM} 컬럼이 없는 환경에서는 {@link
     *     MenuIconDefaults} 스냅샷으로 아이콘을 채운다.
     */
    public List<MenuDto.Node> getMenuTree(List<String> athIds) {
        boolean iconColumnPresent = cmenumRepository.isIconColumnPresent();
        List<MenuTreeRow> all = cmenumRepository.findActiveMenuTreeRows(iconColumnPresent);
        Map<String, Set<String>> athByMenu = menuAuthMapProvider.getMenuAuthMap();
        Set<String> userAths = new HashSet<>(athIds == null ? List.of() : athIds);
        Set<String> activeBoardPaths = activeBoardPaths(all);

        List<MenuTreeRow> visible =
                all.stream()
                        .filter(m -> !"Y".equals(m.hidYn()))
                        .filter(m -> isAllowed(m.mnuId(), athByMenu, userAths))
                        .filter(m -> isLinkedBoardUsable(m, activeBoardPaths))
                        .toList();

        List<MenuDto.Node> tree = prune(buildTree(visible, iconColumnPresent), true);
        // 사이드바/헤더가 관리자 전용 메뉴에 왕관 아이콘을 표시할 수 있도록 노드별 권한ID를 함께 싣는다.
        applyAthIds(tree, athByMenu);
        return tree;
    }

    /**
     * 관리화면용 전체 메뉴 트리를 조회합니다.
     *
     * @return 숨김·권한·빈 그룹을 제거하지 않고 노드별 권한ID를 포함한 전체 트리. {@code IMK_NM} 컬럼이 없는 환경에서는 {@link
     *     MenuIconDefaults} 스냅샷으로 아이콘을 채운다
     */
    public List<MenuDto.Node> getAdminMenuTree() {
        boolean iconColumnPresent = cmenumRepository.isIconColumnPresent();
        List<MenuDto.Node> tree =
                buildTree(
                        cmenumRepository.findActiveMenuTreeRows(iconColumnPresent),
                        iconColumnPresent);
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

    /**
     * 사용 중인 게시판의 화면경로 집합.
     *
     * <p>게시판 경로가 하나도 없으면 게시판을 조회하지 않는다 — 메뉴 조회는 모든 화면 진입마다 도는 경로라 쓰이지 않을 쿼리를 붙이지 않는다.
     */
    private Set<String> activeBoardPaths(List<MenuTreeRow> rows) {
        boolean hasBoardMenu = rows.stream().anyMatch(m -> BoardScreenPath.isBoardPath(m.srePth()));
        if (!hasBoardMenu) return Set.of();
        return boardMetaService.getAllActive().stream()
                .map(b -> BoardScreenPath.pathOf(b.getBlbMngNo()))
                .collect(Collectors.toSet());
    }

    /**
     * 게시판 메뉴가 아직 열 수 있는 화면을 가리키는지 판정한다.
     *
     * <p>게시판이 삭제·미사용으로 바뀌어도 메뉴 행은 남긴다(관리자가 다른 게시판으로 바꾸거나 지울 수 있어야 한다). 대신 사용자 트리에서만 감춰 죽은 링크가 노출되지
     * 않게 한다.
     */
    private boolean isLinkedBoardUsable(MenuTreeRow m, Set<String> activeBoardPaths) {
        if (!BoardScreenPath.isBoardPath(m.srePth())) return true;
        return activeBoardPaths.contains(m.srePth());
    }

    private List<MenuDto.Node> buildTree(List<MenuTreeRow> rows, boolean iconColumnPresent) {
        Map<String, MenuDto.Node> byId = new HashMap<>();
        for (MenuTreeRow m : rows) byId.put(m.mnuId(), toNode(m, iconColumnPresent));
        List<MenuDto.Node> roots = new ArrayList<>();
        for (MenuTreeRow m : rows) {
            MenuDto.Node nodeDto = byId.get(m.mnuId());
            if (m.hrkMnuId() == null) {
                roots.add(nodeDto);
            } else {
                MenuDto.Node parent = byId.get(m.hrkMnuId());
                // 부모가 권한 필터로 제외되어 보이지 않으면 자식(고아 노드)도 노출하지 않는다.
                if (parent == null) continue;
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(nodeDto);
            }
        }
        sortRecursive(roots);
        return roots;
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

    /**
     * 메뉴 행을 트리 노드로 변환한다.
     *
     * @param m 메뉴 행
     * @param iconColumnPresent {@code TPRMPP_CMENUM.IMK_NM}이 실제 스키마에 있는지 여부
     * @return 트리 노드. 컬럼이 있으면 DB 값을 그대로 싣고(관리자가 비운 null도 그대로), 없으면 {@link MenuIconDefaults} 스냅샷으로
     *     채운다
     */
    private MenuDto.Node toNode(MenuTreeRow m, boolean iconColumnPresent) {
        return MenuDto.Node.builder()
                .mnuId(m.mnuId())
                .hrkMnuId(m.hrkMnuId())
                .mnuNm(m.mnuNm())
                .mnuTpC(m.mnuTpC())
                .srePth(m.srePth())
                .mnuSotSqnSno(m.mnuSotSqnSno())
                .hidYn(m.hidYn())
                .mnuDep(m.mnuDep())
                .whlMnuPth(m.whlMnuPth())
                .imkNm(iconColumnPresent ? m.imkNm() : MenuIconDefaults.iconOf(m.mnuId()))
                .children(new ArrayList<>())
                .build();
    }
}
