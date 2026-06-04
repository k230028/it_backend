package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 메뉴 마스터 관리(CRUD) + 정렬/이동 + WHL_MNU_PTH·MNU_DEP 재계산 책임. */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminMenuService {

    private static final int MAX_DEPTH = 3;
    private static final int SORT_STEP = 10;

    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    private final CmenudRepository cmenudRepository;

    public String create(MenuDto.UpsertRequest req) {
        validateTypePath(req.getMnuTpC(), req.getSrePth());
        String mnuId = cmenumRepository.nextMnuId();

        int depth = 1;
        String whlPth = "/" + mnuId;
        if (req.getHrkMnuId() != null) {
            Cmenum parent = load(req.getHrkMnuId());
            depth = parent.getMnuDep() + 1;
            if (depth > MAX_DEPTH) throw badRequest("메뉴 깊이는 최대 " + MAX_DEPTH + "단입니다.");
            whlPth = parent.getWhlMnuPth() + "/" + mnuId;
        }

        Cmenum menu = Cmenum.builder()
                .mnuId(mnuId).hrkMnuId(req.getHrkMnuId()).sysHrkMnuId(req.getSysHrkMnuId())
                .mnuNm(req.getMnuNm()).mnuTpC(req.getMnuTpC()).srePth(req.getSrePth())
                .mnuSotSqnSno(SORT_STEP).hidYn(req.getHidYn() == null ? "N" : req.getHidYn())
                .mnuDep(depth).whlMnuPth(whlPth).delYn("N")
                .build();
        cmenumRepository.save(menu);
        replaceRoles(mnuId, req.getAthIds());
        return mnuId;
    }

    public void update(String mnuId, MenuDto.UpsertRequest req) {
        validateTypePath(req.getMnuTpC(), req.getSrePth());
        Cmenum menu = load(mnuId);
        menu.setMnuNm(req.getMnuNm());
        menu.setSysHrkMnuId(req.getSysHrkMnuId());
        menu.setMnuTpC(req.getMnuTpC());
        menu.setSrePth(req.getSrePth());
        menu.setHidYn(req.getHidYn() == null ? "N" : req.getHidYn());
        // dirty checking flushes; @LogTarget snapshots automatically on @PreUpdate
        replaceRoles(mnuId, req.getAthIds());
    }

    public void delete(String mnuId) {
        Cmenum menu = load(mnuId);
        if (cmenumRepository.countActiveChildren(mnuId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "하위 메뉴가 있어 삭제할 수 없습니다.");
        }
        menu.delete(); // DEL_YN='Y'
        for (Cmenua a : cmenuaRepository.findActiveByMnuId(mnuId)) a.delete();
    }

    public void reorder(List<String> orderedMnuIds) {
        int sort = SORT_STEP;
        for (String id : orderedMnuIds) {
            load(id).setMnuSotSqnSno(sort);
            sort += SORT_STEP;
        }
    }

    public void move(String mnuId, String newHrkMnuId) {
        Cmenum target = load(mnuId);
        String oldPrefix = target.getWhlMnuPth();

        int baseDepth = 0;
        String newParentPath = "";
        if (newHrkMnuId != null) {
            Cmenum newParent = load(newHrkMnuId);
            if (newParent.getWhlMnuPth().startsWith(oldPrefix)) {
                throw badRequest("순환 참조: 자기 자신 또는 후손을 부모로 지정할 수 없습니다.");
            }
            baseDepth = newParent.getMnuDep();
            newParentPath = newParent.getWhlMnuPth();
        }
        String newPrefix = newParentPath + "/" + mnuId;
        int depthDelta = (baseDepth + 1) - target.getMnuDep();

        List<Cmenum> subtree = cmenumRepository.findSubtreeByPathPrefix(oldPrefix);
        for (Cmenum n : subtree) {
            if (n.getMnuDep() + depthDelta > MAX_DEPTH) {
                throw badRequest("이동 시 메뉴 깊이가 " + MAX_DEPTH + "단을 초과합니다.");
            }
        }
        for (Cmenum n : subtree) {
            n.setWhlMnuPth(newPrefix + n.getWhlMnuPth().substring(oldPrefix.length()));
            n.setMnuDep(n.getMnuDep() + depthDelta);
        }
        target.setHrkMnuId(newHrkMnuId);
    }

    // ---- helpers ----

    private Cmenum load(String mnuId) {
        return cmenumRepository.findByMnuIdAndDelYn(mnuId, "N")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 메뉴: " + mnuId));
    }

    private void validateTypePath(String mnuTpC, String srePth) {
        if (!List.of("LNK", "GRP", "DYN").contains(mnuTpC)) throw badRequest("잘못된 메뉴유형코드: " + mnuTpC);
        if ("LNK".equals(mnuTpC)) {
            if (srePth == null || srePth.isBlank()) throw badRequest("LNK 메뉴는 화면경로가 필수입니다.");
            cmenudRepository.findBySrePthAndDelYn(srePth, "N")
                    .orElseThrow(() -> badRequest("라우트 카탈로그에 없는 경로: " + srePth));
        } else if (srePth != null) {
            throw badRequest(mnuTpC + " 메뉴는 화면경로를 가질 수 없습니다.");
        }
    }

    /** 권한 매핑 전체 교체: 기존 활성 매핑 soft-delete 후 새 목록 저장. */
    private void replaceRoles(String mnuId, List<String> athIds) {
        for (Cmenua a : cmenuaRepository.findActiveByMnuId(mnuId)) a.delete();
        if (athIds == null) return;
        for (String athId : athIds) {
            cmenuaRepository.save(Cmenua.builder().mnuId(mnuId).athId(athId).delYn("N").build());
        }
    }

    private ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
