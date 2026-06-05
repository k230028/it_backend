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

    /**
     * 메뉴를 생성하고 권한 매핑을 저장한다.
     *
     * @param req 메뉴명, 유형, 부모, 화면 경로, 권한 목록
     * @return 신규 메뉴 ID
     * @throws ResponseStatusException 메뉴 유형/경로가 유효하지 않거나 깊이가 3단을 초과하는 경우
     */
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

    /**
     * 메뉴 기본 정보와 권한 매핑을 수정한다.
     *
     * @param mnuId 수정할 메뉴 ID
     * @param req 변경할 메뉴 속성
     * @throws ResponseStatusException 메뉴가 없거나 LNK/GRP/DYN 경로 규칙을 위반하는 경우
     */
    public void update(String mnuId, MenuDto.UpsertRequest req) {
        validateTypePath(req.getMnuTpC(), req.getSrePth());
        Cmenum menu = load(mnuId);
        menu.setMnuNm(req.getMnuNm());
        menu.setSysHrkMnuId(req.getSysHrkMnuId());
        menu.setMnuTpC(req.getMnuTpC());
        menu.setSrePth(req.getSrePth());
        menu.setHidYn(req.getHidYn() == null ? "N" : req.getHidYn());
        // JPA dirty checking으로 flush되며, @LogTarget 스냅샷은 @PreUpdate에서 자동 생성된다.
        replaceRoles(mnuId, req.getAthIds());
    }

    /**
     * 하위 메뉴가 없는 메뉴와 권한 매핑을 Soft Delete 처리한다.
     *
     * @param mnuId 삭제할 메뉴 ID
     * @throws ResponseStatusException 메뉴가 없거나 활성 하위 메뉴가 남아 있는 경우
     */
    public void delete(String mnuId) {
        Cmenum menu = load(mnuId);
        if (cmenumRepository.countActiveChildren(mnuId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "하위 메뉴가 있어 삭제할 수 없습니다.");
        }
        menu.delete(); // DEL_YN='Y'
        for (Cmenua a : cmenuaRepository.findActiveByMnuId(mnuId)) a.delete();
    }

    /**
     * 같은 부모 아래 메뉴 표시 순서를 재배치한다.
     *
     * @param orderedMnuIds 화면에서 확정한 메뉴 ID 순서
     * @throws ResponseStatusException 목록에 존재하지 않는 메뉴 ID가 포함된 경우
     */
    public void reorder(List<String> orderedMnuIds) {
        int sort = SORT_STEP;
        for (String id : orderedMnuIds) {
            load(id).setMnuSotSqnSno(sort);
            sort += SORT_STEP;
        }
    }

    /**
     * 메뉴를 새 부모 아래로 이동하고 하위 트리의 전체 경로와 깊이를 재계산한다.
     *
     * @param mnuId 이동할 메뉴 ID
     * @param newHrkMnuId 새 부모 메뉴 ID. null이면 루트로 이동한다.
     * @throws ResponseStatusException 순환 참조가 발생하거나 이동 후 깊이가 3단을 초과하는 경우
     */
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
