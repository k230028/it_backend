package com.kdb.it.domain.menu.service;

import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 메뉴 마스터 관리(CRUD) + 정렬/이동 + WHL_MNU_PTH·MNU_DEP 재계산 책임. */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminMenuService {

    private static final int MAX_DEPTH = 4;
    private static final int SORT_STEP = 10;

    /** 아이콘 클래스 허용 문자 — 화면에서 class 속성으로 쓰이므로 클래스명 문자만 통과시킨다. */
    private static final Pattern ICON_CLASS = Pattern.compile("^[a-z0-9 -]{1,100}$");

    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    private final CmenudRepository cmenudRepository;

    /** BRD 메뉴가 가리키는 게시판이 실제로 사용 중인지 확인하는 원천. */
    private final BoardMetaService boardMetaService;

    /**
     * 메뉴를 생성하고 권한 매핑을 저장한다.
     *
     * @param req 메뉴명, 유형, 부모, 화면 경로, 권한 목록
     * @return 신규 메뉴 ID
     * @throws ResponseStatusException 메뉴 유형·경로·계층이 유효하지 않거나 깊이가 4단을 초과하는 경우
     */
    // 권한 매핑(Cmenua)이 신규 생성되므로 menuAuthMap 캐시를 전체 무효화한다(정합 보장).
    @CacheEvict(value = "menuAuthMap", allEntries = true)
    public String create(MenuDto.UpsertRequest req) {
        validateTypePath(req.getMnuTpC(), req.getSrePth());
        validateHierarchy(req.getMnuTpC(), req.getHrkMnuId());
        String mnuId = cmenumRepository.nextMnuId();

        int depth = 1;
        String whlPth = "/" + mnuId;
        if (req.getHrkMnuId() != null) {
            Cmenum parent = load(req.getHrkMnuId());
            depth = parent.getMnuDep() + 1;
            if (depth > MAX_DEPTH) throw badRequest("메뉴 깊이는 최대 " + MAX_DEPTH + "단입니다.");
            whlPth = parent.getWhlMnuPth() + "/" + mnuId;
        }

        Cmenum menu =
                Cmenum.builder()
                        .mnuId(mnuId)
                        .hrkMnuId(req.getHrkMnuId())
                        .mnuNm(req.getMnuNm())
                        .mnuTpC(req.getMnuTpC())
                        .srePth(req.getSrePth())
                        .mnuSotSqnSno(SORT_STEP)
                        .hidYn(req.getHidYn() == null ? "N" : req.getHidYn())
                        .mnuDep(depth)
                        .whlMnuPth(whlPth)
                        .imkNm(normalizeIcon(req.getImkNm()))
                        .delYn("N")
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
     * @throws ResponseStatusException 메뉴가 없거나 유형·경로·계층 규칙을 위반하는 경우
     */
    // replaceRoles로 권한 매핑이 변경되므로 menuAuthMap 캐시를 전체 무효화한다.
    @CacheEvict(value = "menuAuthMap", allEntries = true)
    public void update(String mnuId, MenuDto.UpsertRequest req) {
        validateTypePath(req.getMnuTpC(), req.getSrePth());
        Cmenum menu = load(mnuId);
        // 대상의 현재 계층 위치를 기준으로 다시 검증한다. 이 호출이 없으면 루트 메뉴의 유형만 바꿔 "루트는 GRP" 규칙을 우회할 수 있다.
        validateHierarchy(req.getMnuTpC(), menu.getHrkMnuId());
        menu.setMnuNm(req.getMnuNm());
        menu.setMnuTpC(req.getMnuTpC());
        menu.setSrePth(req.getSrePth());
        menu.setHidYn(req.getHidYn() == null ? "N" : req.getHidYn());
        menu.setImkNm(normalizeIcon(req.getImkNm()));
        // JPA dirty checking으로 flush되며, @LogTarget 스냅샷은 @PreUpdate에서 자동 생성된다.
        replaceRoles(mnuId, req.getAthIds());
    }

    /**
     * 하위 메뉴가 없는 메뉴와 권한 매핑을 Soft Delete 처리한다.
     *
     * @param mnuId 삭제할 메뉴 ID
     * @throws ResponseStatusException 메뉴가 없거나 활성 하위 메뉴가 남아 있는 경우
     */
    // 메뉴와 권한 매핑이 soft-delete되므로 menuAuthMap 캐시를 전체 무효화한다.
    @CacheEvict(value = "menuAuthMap", allEntries = true)
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
     * @throws ResponseStatusException 계층 규칙을 위반하거나 순환 참조가 발생하거나 이동 후 깊이가 4단을 초과하는 경우
     */
    // move/reorder는 Cmenua(권한 매핑)를 변경하지 않으므로 menuAuthMap 캐시 evict 불필요.
    public void move(String mnuId, String newHrkMnuId) {
        Cmenum target = load(mnuId);
        validateHierarchy(target.getMnuTpC(), newHrkMnuId);
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

    // ---- 내부 헬퍼 ----

    private Cmenum load(String mnuId) {
        return cmenumRepository
                .findByMnuIdAndDelYn(mnuId, "N")
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "존재하지 않는 메뉴: " + mnuId));
    }

    /**
     * 메뉴유형코드와 화면경로의 조합을 검증한다.
     *
     * <p>유효한 유형은 공통코드 MNU_TP_C가 정의한 GRP·LNK·PGE·BRD 넷뿐이다. PGE(페이지화면)는 내부 화면이므로 화면경로가 필수이고 라우트 카탈로그에
     * 등록돼 있어야 한다. BRD(게시판)는 게시판마다 경로가 만들어져 라우트 카탈로그에 없으므로, 카탈로그 대신 활성 게시판 목록으로 검증한다. GRP(메뉴그룹)는
     * 컨테이너라 화면경로를 가질 수 없다. LNK(링크메뉴)는 외부 링크 전용 값으로 신설했으나 아직 렌더링·URL 검증을 구현하지 않아 저장을 막는다.
     *
     * @param mnuTpC 메뉴유형코드
     * @param srePth 화면경로 (없으면 null)
     * @throws ResponseStatusException 유형이 목록 밖이거나, LNK이거나, 유형·경로 조합이 규칙에 어긋나는 경우
     */
    private void validateTypePath(String mnuTpC, String srePth) {
        if (!List.of("GRP", "LNK", "PGE", BoardMenuLink.MENU_TYPE).contains(mnuTpC))
            throw badRequest("잘못된 메뉴유형코드: " + mnuTpC);
        if ("LNK".equals(mnuTpC)) throw badRequest("외부링크 메뉴는 아직 지원하지 않습니다.");
        if ("PGE".equals(mnuTpC)) {
            if (srePth == null || srePth.isBlank()) throw badRequest("PGE 메뉴는 화면경로가 필수입니다.");
            cmenudRepository
                    .findBySrePthAndDelYn(srePth, "N")
                    .orElseThrow(() -> badRequest("라우트 카탈로그에 없는 경로: " + srePth));
        } else if (BoardMenuLink.isBoardMenu(mnuTpC)) {
            validateBoardPath(srePth);
        } else if (srePth != null) {
            throw badRequest(mnuTpC + " 메뉴는 화면경로를 가질 수 없습니다.");
        }
    }

    /**
     * 아이콘 클래스를 저장 가능한 형태로 정규화한다.
     *
     * <p>이 값은 화면에서 요소의 class 속성으로 바인딩되므로 클래스명에 쓰이는 문자만 허용한다. 공백뿐인 값은 "아이콘 미지정"과 같은 뜻이므로 null로 접는다.
     *
     * @param imkNm 아이콘 클래스 (없으면 null)
     * @return 앞뒤 공백을 제거한 값. 비어 있으면 null
     * @throws ResponseStatusException 허용하지 않는 문자가 포함된 경우
     */
    private String normalizeIcon(String imkNm) {
        if (imkNm == null) return null;
        String trimmed = imkNm.trim();
        if (trimmed.isEmpty()) return null;
        if (!ICON_CLASS.matcher(trimmed).matches()) {
            throw badRequest("아이콘 값에는 영문 소문자·숫자·하이픈·공백만 쓸 수 있습니다: " + imkNm);
        }
        return trimmed;
    }

    /**
     * 게시판 메뉴의 화면경로가 사용 중인 게시판을 가리키는지 검증한다.
     *
     * @param srePth 화면경로 ({@code /board/{게시판관리번호}})
     * @throws ResponseStatusException 경로가 없거나 형식이 아니거나 사용 중인 게시판이 아닌 경우
     */
    private void validateBoardPath(String srePth) {
        if (srePth == null || srePth.isBlank()) throw badRequest("게시판 메뉴는 게시판을 선택해야 합니다.");
        String blbMngNo = BoardMenuLink.boardNoOf(srePth);
        if (blbMngNo == null) throw badRequest("게시판 화면경로 형식이 아닙니다: " + srePth);
        boolean usable =
                boardMetaService.getAllActive().stream()
                        .anyMatch(b -> blbMngNo.equals(b.getBlbMngNo()));
        if (!usable) throw badRequest("사용 중인 게시판이 아닙니다: " + blbMngNo);
    }

    /**
     * 최상위(루트) 메뉴는 메뉴그룹(GRP)만 허용한다.
     *
     * <p>종전에는 헤더 유형이 "루트 전용"을 뜻해 유형과 계층 위치가 같은 사실을 이중으로 표현했다. 헤더 유형을 GRP로 흡수하면서 판정 근거를 계층 위치 하나로
     * 합쳤다. 그 결과 GRP는 루트와 하위 어디에도 놓일 수 있고, 기존 헤더를 다른 메뉴 하위로 이동하는 것도 허용된다.
     *
     * @param mnuTpC 메뉴유형코드
     * @param hrkMnuId 상위메뉴ID (루트면 null)
     * @throws ResponseStatusException 루트인데 GRP가 아닌 경우
     */
    private void validateHierarchy(String mnuTpC, String hrkMnuId) {
        if (hrkMnuId == null && !"GRP".equals(mnuTpC)) {
            throw badRequest("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다. 상위 메뉴를 지정하세요.");
        }
    }

    /**
     * 권한 매핑을 목표 목록(athIds)에 맞춰 재조정한다.
     *
     * <p>기존 행(삭제분 포함)을 모두 로드해 목표에 있으면 복원(restore), 없으면 soft-delete 하고, 어느 상태로도 존재하지 않는 권한만 신규
     * INSERT 한다. 활성 매핑을 일괄 삭제 후 동일 복합 PK로 재INSERT하면 {@code save()}가 {@code merge()} 경로로 빠지면서
     * {@code @PrePersist} 미발화로 GUID가 NULL이 되어 {@code ORA-01407}이 발생하므로(§5.12.1.1) 복원·재사용 방식을 사용한다.
     *
     * @param mnuId 대상 메뉴 ID
     * @param athIds 노출 권한ID 목록. null·빈 목록이면 모든 매핑을 삭제하여 전체 공개로 만든다.
     */
    private void replaceRoles(String mnuId, List<String> athIds) {
        Set<String> wanted = athIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(athIds);
        Set<String> existing = new HashSet<>();
        for (Cmenua a : cmenuaRepository.findByMnuId(mnuId)) {
            if (wanted.contains(a.getAthId())) {
                a.restore(); // 삭제분은 'N'으로 복원, 이미 활성이면 변화 없음
                existing.add(a.getAthId());
            } else {
                a.delete(); // 더 이상 필요 없는 매핑은 soft delete
            }
        }
        for (String athId : wanted) {
            if (!existing.contains(athId)) {
                // 신규 PK는 merge SELECT가 비어 INSERT로 가며 @PrePersist가 GUID를 채운다.
                cmenuaRepository.save(
                        Cmenua.builder().mnuId(mnuId).athId(athId).delYn("N").build());
            }
        }
    }

    private ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
