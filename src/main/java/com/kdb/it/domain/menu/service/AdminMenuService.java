package com.kdb.it.domain.menu.service;

import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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

    /** 카탈로그 화면메뉴명(SRE_MNU_NM) 컬럼 길이. 초과분은 잘라 넣는다. */
    private static final int CATALOG_NAME_MAX = 100;

    /** 자동 등록 카탈로그 경로명 접미. */
    private static final String PREPARING_NAME_SUFFIX = " (준비중)";

    /** 아이콘 클래스 허용 문자 — 화면에서 class 속성으로 쓰이므로 클래스명 문자만 통과시킨다. */
    private static final Pattern ICON_CLASS = Pattern.compile("^[a-z0-9 -]{1,100}$");

    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    private final CmenudRepository cmenudRepository;

    /** 게시판 PGE 경로가 가리키는 게시판이 실제로 사용 중인지 확인하는 원천. */
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
        /* 준비중이 아니면 종전 순서 그대로 경로부터 검증한다. 준비중 경로는 채번한 mnuId에서
        나오므로 nextMnuId() 뒤에 확정하고, 그 확정 경로로 같은 검증을 통과시킨다. */
        boolean preparing = isPreparingRequest(req);
        String srePth = req.getSrePth();
        if (!preparing) validateTypePath(req.getMnuTpC(), srePth);
        validateHierarchy(req.getMnuTpC(), req.getHrkMnuId());
        String mnuId = cmenumRepository.nextMnuId();
        if (preparing) {
            srePth = resolvePreparingPath(mnuId, null, req.getMnuNm(), req.getMnuTpC());
            validateTypePath(req.getMnuTpC(), srePth);
        }

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
                        .srePth(srePth)
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
        /* 준비중 경로는 저장된 메뉴의 현재 경로를 봐야 정해지므로 load()가 검증보다 앞선다. */
        Cmenum menu = load(mnuId);
        String previousPath = menu.getSrePth();
        boolean preparing = isPreparingRequest(req);
        String srePth =
                preparing
                        ? resolvePreparingPath(mnuId, previousPath, req.getMnuNm(), req.getMnuTpC())
                        : req.getSrePth();
        validateTypePath(req.getMnuTpC(), srePth);
        // 대상의 현재 계층 위치를 기준으로 다시 검증한다. 이 호출이 없으면 루트 메뉴의 유형만 바꿔 "루트는 GRP" 규칙을 우회할 수 있다.
        validateHierarchy(req.getMnuTpC(), menu.getHrkMnuId());
        menu.setMnuNm(req.getMnuNm());
        menu.setMnuTpC(req.getMnuTpC());
        menu.setSrePth(srePth);
        menu.setHidYn(req.getHidYn() == null ? "N" : req.getHidYn());
        menu.setImkNm(normalizeIcon(req.getImkNm()));
        // JPA dirty checking으로 flush되며, @LogTarget 스냅샷은 @PreUpdate에서 자동 생성된다.
        replaceRoles(mnuId, req.getAthIds());
        // C1: 확정된 새 경로(srePth)가 여전히 그 자동 경로면 아직 참조 중이므로 회수하지 않는다.
        if (!preparing) releaseGeneratedPreparingPath(mnuId, previousPath, srePth);
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
        String previousPath = menu.getSrePth();
        menu.delete(); // DEL_YN='Y'
        for (Cmenua a : cmenuaRepository.findActiveByMnuId(mnuId)) a.delete();
        // M1: 삭제된 메뉴는 더 이상 어떤 경로도 가리키지 않으므로 자동 등록 경로를 회수한다.
        releaseGeneratedPreparingPath(mnuId, previousPath, null);
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

    /** 준비중 요청인지 판정한다. null은 N으로 본다. */
    private boolean isPreparingRequest(MenuDto.UpsertRequest req) {
        return "Y".equals(req.getPreparingYn());
    }

    /** 카탈로그 화면메뉴명을 만든다. 컬럼 길이(100자)를 넘지 않도록 메뉴명을 자른다. */
    private String preparingCatalogName(String mnuNm) {
        int room = CATALOG_NAME_MAX - PREPARING_NAME_SUFFIX.length();
        String base = mnuNm.length() > room ? mnuNm.substring(0, room) : mnuNm;
        return base + PREPARING_NAME_SUFFIX;
    }

    /**
     * 준비중 경로를 확정하고 라우트 카탈로그 행을 준비한다.
     *
     * <p>저장된 메뉴가 이미 준비중 경로를 쓰고 있으면 그 경로를 유지한다. 사람이 등록한 {@code /preparing/cdp} 같은 경로를 자동 경로로 갈아치우지
     * 않기 위해서다. 그 외에는 {@code /preparing/{mnuId 소문자}}를 쓴다. 카탈로그 행이 없으면 만들고, 있으면 재사용하며 화면메뉴명만 현재 메뉴명
     * 기준으로 맞춘다(같은 메뉴를 다시 저장해도 행이 늘지 않는다).
     *
     * @param mnuId 대상 메뉴 ID. 생성이면 채번 직후 값
     * @param currentPath 저장된 메뉴의 현재 화면경로. 생성이면 null
     * @param mnuNm 카탈로그 화면메뉴명에 쓸 메뉴명
     * @param mnuTpC 메뉴유형코드
     * @return 확정된 준비중 경로
     * @throws ResponseStatusException 메뉴유형이 PGE가 아닌 경우
     */
    private String resolvePreparingPath(
            String mnuId, String currentPath, String mnuNm, String mnuTpC) {
        if (!"PGE".equals(mnuTpC)) throw badRequest("준비중은 페이지화면만 가능합니다.");
        String path =
                MenuPathPolicy.isPreparing(currentPath)
                        ? currentPath
                        : MenuPathPolicy.PREPARING_PATH_PREFIX + mnuId.toLowerCase();
        String catalogName = preparingCatalogName(mnuNm);
        Optional<Cmenud> existing = cmenudRepository.findBySrePthAndDelYn(path, "N");
        String rmk = existing.map(Cmenud::getRmk).orElse(MenuPathPolicy.PREPARING_ROUTE_RMK);
        // M4: 관리자가 /admin/routes에서 미사용 처리했을 수 있으므로 기존 사용여부를 유지한다.
        // 행이 없을 때(신규 등록)만 'Y'로 시작한다.
        String useYn = existing.map(Cmenud::getUseYn).orElse("Y");
        // I2: GUID·GUID진행일련번호는 기존 행 값을 그대로 옮겨 담는다. 비워 두면 merge(UPDATE)
        // 경로에서 @PrePersist가 돌지 않아 NULL로 저장되어 NOT NULL 제약(ORA-01407)에 걸린다.
        // 신규 행(existing 없음)은 지금처럼 비워 두면 @PrePersist가 채운다.
        String guid = existing.map(Cmenud::getGuid).orElse(null);
        Integer guidPrgSno = existing.map(Cmenud::getGuidPrgSno).orElse(null);
        // Cmenud는 setter가 없으므로 같은 PK로 새 엔티티를 저장해 JPA merge로 갱신한다.
        cmenudRepository.save(
                Cmenud.builder()
                        .srePth(path)
                        .sreMnuNm(catalogName)
                        .useYn(useYn)
                        .rmk(rmk)
                        .guid(guid)
                        .guidPrgSno(guidPrgSno)
                        .delYn("N")
                        .build());
        return path;
    }

    /**
     * 이 메뉴가 쓰던 자동 생성 준비중 경로를 카탈로그에서 논리삭제한다.
     *
     * <p>회수 대상은 {@code /preparing/{이 메뉴의 mnuId 소문자}}와 정확히 같은 경로뿐이다. 사람이 등록한 준비중 경로는 다른 메뉴가 쓸 수 있으므로
     * 건드리지 않는다.
     *
     * <p>다음 두 경우는 조용히 건너뛴다(예외를 던지지 않는다 — 회수 실패로 메뉴 저장 자체를 막지 않는다).
     *
     * <ul>
     *   <li>이 메뉴의 새 경로({@code newPath})가 여전히 그 자동 경로인 경우 — 아직 이 메뉴가 참조 중이다(C1). 클라이언트가 체크만 해제하고 새
     *       경로를 고르지 않은 채 보낸 요청이 대표적이다.
     *   <li>다른 활성 메뉴가 같은 자동 경로를 참조 중인 경우 — {@link AdminRouteService#delete}와 같은 참조 보호다(I1). 자동 등록
     *       행도 {@code USE_YN='Y'}인 평범한 경로라 다른 메뉴가 고를 수 있다.
     * </ul>
     *
     * @param mnuId 대상 메뉴 ID
     * @param previousPath 저장(또는 삭제) 직전 화면경로. null이면 아무것도 하지 않는다
     * @param newPath 이 메뉴에 확정된 새 화면경로. 메뉴를 삭제하는 호출이면 null(더 이상 어떤 경로도 가리키지 않음)
     */
    private void releaseGeneratedPreparingPath(String mnuId, String previousPath, String newPath) {
        String generated = MenuPathPolicy.PREPARING_PATH_PREFIX + mnuId.toLowerCase();
        if (!generated.equals(previousPath)) return;
        if (generated.equals(newPath)) return;
        if (referencedByOtherActiveMenu(mnuId, generated)) return;
        cmenudRepository.findBySrePthAndDelYn(generated, "N").ifPresent(Cmenud::delete);
    }

    /** 이 메뉴가 아닌 다른 활성 메뉴가 같은 화면경로를 쓰고 있는지 확인한다. */
    private boolean referencedByOtherActiveMenu(String mnuId, String srePth) {
        return cmenumRepository.findAllActive().stream()
                .anyMatch(m -> srePth.equals(m.getSrePth()) && !mnuId.equals(m.getMnuId()));
    }

    /**
     * 메뉴유형코드와 화면경로의 조합을 검증한다.
     *
     * <p>유효한 유형은 GRP·LNK·PGE다. GRP는 경로를 가질 수 없고, LNK는 사용 중인 카탈로그의 외부 http(s) URL만 허용한다. PGE는 카탈로그의
     * 내부 화면경로 또는 활성 게시판의 {@code /board/{게시판관리번호}} 경로를 허용한다.
     *
     * @param mnuTpC 메뉴유형코드
     * @param srePth 화면경로 (없으면 null)
     * @throws ResponseStatusException 유형이 목록 밖이거나 유형·경로 조합이 규칙에 어긋나는 경우
     */
    private void validateTypePath(String mnuTpC, String srePth) {
        if (!List.of("GRP", "LNK", "PGE").contains(mnuTpC)) {
            throw badRequest("잘못된 메뉴유형코드: " + mnuTpC);
        }
        if ("GRP".equals(mnuTpC)) {
            if (srePth != null) throw badRequest("GRP 메뉴는 경로를 가질 수 없습니다.");
            return;
        }
        if ("LNK".equals(mnuTpC)) {
            Cmenud route = requireUsableCatalogPath(srePth);
            if (!MenuPathPolicy.isExternalHttpUrl(route.getSrePth())) {
                throw badRequest("LNK 메뉴는 외부 http(s) URL이 필수입니다.");
            }
            return;
        }
        if (BoardScreenPath.isBoardPath(srePth)) {
            validateBoardPath(srePth);
            return;
        }
        Cmenud route = requireUsableCatalogPath(srePth);
        if (!MenuPathPolicy.isInternal(route.getSrePth())) {
            throw badRequest("PGE 메뉴는 내부 화면경로가 필수입니다.");
        }
    }

    /** 사용 중인 라우트 카탈로그 경로를 조회한다. */
    private Cmenud requireUsableCatalogPath(String srePth) {
        if (srePth == null || srePth.isBlank()) throw badRequest("메뉴 경로가 필수입니다.");
        Cmenud route =
                cmenudRepository
                        .findBySrePthAndDelYn(srePth, "N")
                        .orElseThrow(() -> badRequest("사용 가능한 라우트 카탈로그 경로가 아닙니다: " + srePth));
        if (!"N".equals(route.getDelYn()) || !"Y".equals(route.getUseYn())) {
            throw badRequest("사용 가능한 라우트 카탈로그 경로가 아닙니다: " + srePth);
        }
        return route;
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
        String blbMngNo = BoardScreenPath.boardNoOf(srePth);
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
