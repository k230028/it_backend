package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.service.BoardMetaService;
import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminMenuServiceTest {

    @Mock CmenumRepository cmenumRepository;
    @Mock CmenuaRepository cmenuaRepository;
    @Mock CmenudRepository cmenudRepository;

    /** BRD(게시판) 메뉴는 라우트 카탈로그가 아니라 활성 게시판 목록으로 검증한다. */
    @Mock BoardMetaService boardMetaService;

    @InjectMocks AdminMenuService service;

    private Cmenum node(String id, String parent, int dep, String path) {
        return Cmenum.builder()
                .mnuId(id)
                .hrkMnuId(parent)
                .mnuNm(id)
                .mnuTpC("GRP")
                .mnuSotSqnSno(10)
                .hidYn("N")
                .mnuDep(dep)
                .whlMnuPth(path)
                .delYn("N")
                .build();
    }

    @Test
    void move_recalculatesPathAndDepthForNodeAndDescendants() {
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum child = node("C", "B", 3, "/A/B/C");
        Cmenum newParent = node("X", null, 1, "/X");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("X", "N")).willReturn(Optional.of(newParent));
        given(cmenumRepository.findSubtreeByPathPrefix("/A/B")).willReturn(List.of(target, child));

        service.move("B", "X");

        assertThat(target.getHrkMnuId()).isEqualTo("X");
        assertThat(target.getMnuDep()).isEqualTo(2); // /X (1) + B = 2
        assertThat(target.getWhlMnuPth()).isEqualTo("/X/B");
        assertThat(child.getMnuDep()).isEqualTo(3);
        assertThat(child.getWhlMnuPth()).isEqualTo("/X/B/C");
    }

    @Test
    void move_rejectsCycle_whenNewParentIsDescendant() {
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum desc = node("C", "B", 3, "/A/B/C");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("C", "N")).willReturn(Optional.of(desc));

        assertThatThrownBy(() -> service.move("B", "C"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("순환");
    }

    @Test
    void move_rejectsWhenResultingDepthExceedsFour() {
        // B(depth2)+자식 C(depth3)를 depth3 부모 아래로 이동 → C가 depth5가 되어 거부
        Cmenum target = node("B", "A", 2, "/A/B");
        Cmenum child = node("C", "B", 3, "/A/B/C");
        Cmenum newParent = node("P", "O", 3, "/N/O/P");
        given(cmenumRepository.findByMnuIdAndDelYn("B", "N")).willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("P", "N")).willReturn(Optional.of(newParent));
        given(cmenumRepository.findSubtreeByPathPrefix("/A/B")).willReturn(List.of(target, child));

        assertThatThrownBy(() -> service.move("B", "P"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("깊이");
    }

    @Test
    void delete_rejectsWhenChildrenExist() {
        given(cmenumRepository.findByMnuIdAndDelYn("A", "N"))
                .willReturn(Optional.of(node("A", null, 1, "/A")));
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

    // =========================================================================
    // create 메서드 검증
    // =========================================================================

    @Test
    @DisplayName("create: PGE 유형이고 유효한 화면경로이면 메뉴를 저장하고 ID를 반환한다")
    void create_PGE유형_메뉴저장및ID반환() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("예산목록")
                        .mnuTpC("PGE")
                        .hrkMnuId("P1")
                        .srePth("/budget/list")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(
                        Optional.of(
                                Cmenud.builder()
                                        .srePth("/budget/list")
                                        .sreMnuNm("예산목록")
                                        .useYn("Y")
                                        .delYn("N")
                                        .build()));
        given(cmenumRepository.findByMnuIdAndDelYn("P1", "N"))
                .willReturn(Optional.of(node("P1", "MHED0002", 2, "/MHED0002/P1")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000001");
        given(cmenuaRepository.findByMnuId("MNU0000001")).willReturn(List.of());

        // when
        String result = service.create(req);

        // then
        assertThat(result).isEqualTo("MNU0000001");
        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getMnuTpC()).isEqualTo("PGE");
        assertThat(captor.getValue().getSrePth()).isEqualTo("/budget/list");
    }

    @Test
    @DisplayName("create: GRP 유형이고 화면경로가 없으면 루트 그룹을 저장한다")
    void create_GRP유형_루트그룹저장() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("새 그룹")
                        .mnuTpC("GRP")
                        .hrkMnuId(null)
                        .srePth(null)
                        .hidYn("N")
                        .athIds(null)
                        .build();
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000002");
        given(cmenuaRepository.findByMnuId("MNU0000002")).willReturn(List.of());

        // when
        String result = service.create(req);

        // then
        assertThat(result).isEqualTo("MNU0000002");
        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getMnuDep()).isEqualTo(1);
        assertThat(captor.getValue().getWhlMnuPth()).isEqualTo("/MNU0000002");
    }

    @Test
    @DisplayName("create: 부모 메뉴가 있으면 depth를 부모+1로 설정한다")
    void create_부모메뉴있음_depth부모플러스1() {
        // given
        Cmenum parent = node("PAR", null, 1, "/PAR");
        parent.setMnuTpC("GRP");
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("하위메뉴")
                        .mnuTpC("GRP")
                        .hrkMnuId("PAR")
                        .srePth(null)
                        .hidYn("N")
                        .athIds(List.of())
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("PAR", "N")).willReturn(Optional.of(parent));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000003");
        given(cmenuaRepository.findByMnuId("MNU0000003")).willReturn(List.of());

        // when
        String result = service.create(req);

        // then
        assertThat(result).isEqualTo("MNU0000003");
        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getMnuDep()).isEqualTo(2);
        assertThat(captor.getValue().getWhlMnuPth()).isEqualTo("/PAR/MNU0000003");
    }

    @Test
    @DisplayName("create: GRP 메뉴에 화면경로가 있으면 예외를 던진다")
    void create_GRP유형_화면경로있음_예외() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("잘못된메뉴")
                        .mnuTpC("GRP")
                        .srePth("/some/path")
                        .build();

        // when & then
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("화면경로를 가질 수 없습니다");
    }

    @Test
    @DisplayName("create: PGE 메뉴에 화면경로가 없으면 예외를 던진다")
    void create_PGE유형_화면경로없음_예외() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("페이지").mnuTpC("PGE").srePth(null).build();

        // when & then
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PGE 메뉴는 화면경로가 필수입니다.");
    }

    @Test
    @DisplayName("create: LNK 메뉴는 아직 지원하지 않으므로 예외를 던진다")
    void create_LNK유형_미지원_예외() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("외부링크")
                        .mnuTpC("LNK")
                        .srePth("https://example.com")
                        .hrkMnuId("P1")
                        .build();

        // when & then
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("외부링크 메뉴는 아직 지원하지 않습니다.");
    }

    @Test
    @DisplayName("create: 알 수 없는 메뉴유형코드이면 예외를 던진다")
    void create_알수없는유형_예외() {
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("알 수 없는 유형")
                        .mnuTpC("XXX")
                        .hrkMnuId(null)
                        .build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("잘못된 메뉴유형코드: XXX");
    }

    @Test
    @DisplayName("create: 깊이가 4단을 초과하면 예외를 던진다")
    void create_깊이초과_예외() {
        // given: depth=4인 부모에 추가하면 depth=5 → 예외
        Cmenum parent =
                Cmenum.builder()
                        .mnuId("D4")
                        .hrkMnuId("D3")
                        .mnuNm("4단메뉴")
                        .mnuTpC("GRP")
                        .mnuSotSqnSno(10)
                        .hidYn("N")
                        .mnuDep(4)
                        .whlMnuPth("/D1/D2/D3/D4")
                        .delYn("N")
                        .build();
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("5단메뉴")
                        .mnuTpC("GRP")
                        .hrkMnuId("D4")
                        .srePth(null)
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("D4", "N")).willReturn(Optional.of(parent));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000004");

        // when & then
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("깊이");
    }

    // =========================================================================
    // IMK_NM(아이콘) 저장 검증
    // =========================================================================

    private MenuDto.UpsertRequest groupRequestWithIcon(String imkNm) {
        return MenuDto.UpsertRequest.builder()
                .mnuNm("아이콘그룹")
                .mnuTpC("GRP")
                .hrkMnuId(null)
                .srePth(null)
                .hidYn("N")
                .imkNm(imkNm)
                .athIds(List.of())
                .build();
    }

    @Test
    @DisplayName("create: 아이콘 클래스를 그대로 저장한다")
    void create_아이콘저장() {
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000020");
        given(cmenuaRepository.findByMnuId("MNU0000020")).willReturn(List.of());

        service.create(groupRequestWithIcon("pi pi-home"));

        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getImkNm()).isEqualTo("pi pi-home");
    }

    @Test
    @DisplayName("create: 공백뿐인 아이콘은 미지정(null)으로 저장한다")
    void create_공백아이콘_null저장() {
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000021");
        given(cmenuaRepository.findByMnuId("MNU0000021")).willReturn(List.of());

        service.create(groupRequestWithIcon("   "));

        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getImkNm()).isNull();
    }

    @Test
    @DisplayName("create: 아이콘 클래스에 쓸 수 없는 문자가 있으면 예외를 던진다")
    void create_잘못된아이콘_예외() {
        // 이 값은 프론트에서 class 속성으로 바인딩된다. 따옴표·꺾쇠는 서버에서 막는다.
        assertThatThrownBy(() -> service.create(groupRequestWithIcon("pi pi-home\" onload=x")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("아이콘");
    }

    @Test
    @DisplayName("update: 아이콘을 비우면 미지정으로 되돌린다")
    void update_아이콘해제() {
        Cmenum menu = node("M9", null, 1, "/M9");
        menu.setImkNm("pi pi-home");
        given(cmenumRepository.findByMnuIdAndDelYn("M9", "N")).willReturn(Optional.of(menu));
        given(cmenuaRepository.findByMnuId("M9")).willReturn(List.of());

        service.update("M9", groupRequestWithIcon(null));

        assertThat(menu.getImkNm()).isNull();
    }

    // =========================================================================
    // BRD(게시판) 메뉴 검증
    // =========================================================================

    /** 활성 게시판 한 건을 돌려주는 목록 대역. */
    private BoardMetaDto.Response board(String blbMngNo, String blbNm) {
        return BoardMetaDto.Response.builder().blbMngNo(blbMngNo).blbNm(blbNm).useYn("Y").build();
    }

    private MenuDto.UpsertRequest boardMenuRequest(String srePth) {
        return MenuDto.UpsertRequest.builder()
                .mnuNm("공지사항")
                .mnuTpC("BRD")
                .hrkMnuId("MBRD0001")
                .srePth(srePth)
                .hidYn("N")
                .athIds(List.of())
                .build();
    }

    @Test
    @DisplayName("create: BRD 메뉴는 활성 게시판 경로면 저장한다 (라우트 카탈로그는 조회하지 않는다)")
    void create_BRD유형_활성게시판_저장() {
        given(boardMetaService.getAllActive())
                .willReturn(List.of(board("BLBM-0001", "공지사항"), board("BLBM-0002", "자료실")));
        given(cmenumRepository.findByMnuIdAndDelYn("MBRD0001", "N"))
                .willReturn(Optional.of(node("MBRD0001", "MHED0006", 2, "/MHED0006/MBRD0001")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000010");
        given(cmenuaRepository.findByMnuId("MNU0000010")).willReturn(List.of());

        String result = service.create(boardMenuRequest("/board/BLBM-0001"));

        assertThat(result).isEqualTo("MNU0000010");
        ArgumentCaptor<Cmenum> captor = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(captor.capture());
        assertThat(captor.getValue().getMnuTpC()).isEqualTo("BRD");
        assertThat(captor.getValue().getSrePth()).isEqualTo("/board/BLBM-0001");
        // 게시판 경로는 라우트 카탈로그에 없다 — 카탈로그로 검증하면 저장이 항상 실패한다.
        verifyNoInteractions(cmenudRepository);
    }

    @Test
    @DisplayName("create: BRD 메뉴에 화면경로가 없으면 예외를 던진다")
    void create_BRD유형_경로없음_예외() {
        assertThatThrownBy(() -> service.create(boardMenuRequest(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("게시판을 선택해야 합니다");
    }

    @Test
    @DisplayName("create: BRD 메뉴의 화면경로가 게시판 경로 형식이 아니면 예외를 던진다")
    void create_BRD유형_형식위반_예외() {
        assertThatThrownBy(() -> service.create(boardMenuRequest("/budget/list")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("게시판 화면경로 형식이 아닙니다");
    }

    @Test
    @DisplayName("create: 사용 중이 아닌 게시판을 가리키면 예외를 던진다")
    void create_BRD유형_없는게시판_예외() {
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0001", "공지사항")));

        assertThatThrownBy(() -> service.create(boardMenuRequest("/board/BLBM-9999")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사용 중인 게시판이 아닙니다");
    }

    @Test
    @DisplayName("create: BRD 메뉴를 루트로 생성하면 예외를 던진다")
    void create_BRD유형_루트_예외() {
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0001", "공지사항")));
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("공지사항")
                        .mnuTpC("BRD")
                        .hrkMnuId(null)
                        .srePth("/board/BLBM-0001")
                        .build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다.");
    }

    @Test
    @DisplayName("update: PGE 메뉴를 BRD로 바꾸면 게시판 경로로 검증한다")
    void update_PGE를BRD로변경_게시판검증() {
        Cmenum menu = node("M1", "MBRD0001", 3, "/MHED0006/MBRD0001/M1");
        menu.setMnuTpC("PGE");
        given(cmenumRepository.findByMnuIdAndDelYn("M1", "N")).willReturn(Optional.of(menu));
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0003", "질의응답")));
        given(cmenuaRepository.findByMnuId("M1")).willReturn(List.of());

        service.update("M1", boardMenuRequest("/board/BLBM-0003"));

        assertThat(menu.getMnuTpC()).isEqualTo("BRD");
        assertThat(menu.getSrePth()).isEqualTo("/board/BLBM-0003");
    }

    // =========================================================================
    // update 메서드 검증
    // =========================================================================

    @Test
    @DisplayName("update: 존재하는 메뉴의 속성을 수정한다")
    void update_존재하는메뉴_속성수정() {
        // given
        Cmenum menu = node("M1", null, 1, "/M1");
        menu.setMnuTpC("GRP");
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("수정메뉴")
                        .mnuTpC("GRP")
                        .srePth(null)
                        .hidYn("Y")
                        .athIds(List.of("ITPAD001"))
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("M1", "N")).willReturn(Optional.of(menu));
        given(cmenuaRepository.findByMnuId("M1")).willReturn(List.of());

        // when
        service.update("M1", req);

        // then: JPA dirty checking으로 필드가 수정됨
        assertThat(menu.getMnuNm()).isEqualTo("수정메뉴");
        assertThat(menu.getHidYn()).isEqualTo("Y");
        // 권한 매핑 저장 확인
        verify(cmenuaRepository).save(any(Cmenua.class));
    }

    @Test
    @DisplayName("update: 기존 권한은 복원·재사용하고 동일 PK 재INSERT를 하지 않는다(ORA-01407 회귀 방지)")
    void update_권한재조정_기존행복원_재INSERT없음() {
        // given: 이미 ITPAD001 매핑이 존재하는 메뉴를 ITPAD001 유지 + ITPZZ002 추가로 저장
        Cmenum menu = node("M1", null, 1, "/M1");
        menu.setMnuTpC("GRP");
        Cmenua existingAdmin = Cmenua.builder().mnuId("M1").athId("ITPAD001").delYn("N").build();
        Cmenua removedManager = Cmenua.builder().mnuId("M1").athId("ITPZZ001").delYn("N").build();
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("권한조정")
                        .mnuTpC("GRP")
                        .srePth(null)
                        .hidYn("N")
                        .athIds(List.of("ITPAD001", "ITPZZ002"))
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("M1", "N")).willReturn(Optional.of(menu));
        given(cmenuaRepository.findByMnuId("M1"))
                .willReturn(List.of(existingAdmin, removedManager));

        // when
        service.update("M1", req);

        // then: 유지 권한은 활성 그대로(복원), 빠진 권한은 soft-delete, 신규 권한만 INSERT
        assertThat(existingAdmin.getDelYn())
                .isEqualTo("N"); // 동일 PK 재INSERT 없이 재사용 → GUID NULL UPDATE 회피
        assertThat(removedManager.getDelYn()).isEqualTo("Y"); // 더 이상 필요 없는 매핑 삭제
        ArgumentCaptor<Cmenua> captor = ArgumentCaptor.forClass(Cmenua.class);
        verify(cmenuaRepository).save(captor.capture()); // 신규 ITPZZ002 1건만 저장
        assertThat(captor.getValue().getAthId()).isEqualTo("ITPZZ002");
    }

    @Test
    @DisplayName("update: 존재하지 않는 메뉴면 예외를 던진다")
    void update_존재하지않는메뉴_예외() {
        // given
        given(cmenumRepository.findByMnuIdAndDelYn("NONE", "N")).willReturn(Optional.empty());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder().mnuNm("없는메뉴").mnuTpC("GRP").build();

        // when & then
        assertThatThrownBy(() -> service.update("NONE", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("존재하지 않는 메뉴");
    }

    // =========================================================================
    // delete 메서드 검증
    // =========================================================================

    @Test
    @DisplayName("delete: 하위 메뉴가 없으면 Soft Delete 처리한다")
    void delete_하위메뉴없음_소프트딜리트() {
        // given
        Cmenum menu = node("D1", null, 1, "/D1");
        given(cmenumRepository.findByMnuIdAndDelYn("D1", "N")).willReturn(Optional.of(menu));
        given(cmenumRepository.countActiveChildren("D1")).willReturn(0L);
        given(cmenuaRepository.findActiveByMnuId("D1")).willReturn(List.of());

        // when
        service.delete("D1");

        // then
        assertThat(menu.getDelYn()).isEqualTo("Y");
    }

    // =========================================================================
    // move: 루트로 이동 검증
    // =========================================================================

    @Test
    @DisplayName("move: GRP를 루트로 이동하면 depth=1로 재계산한다")
    void move_GRP를루트로이동() {
        // given: 루트에는 GRP만 놓을 수 있으므로 GRP를 대상으로 루트 이동 재계산을 검증한다.
        Cmenum target =
                Cmenum.builder()
                        .mnuId("MHED0009")
                        .hrkMnuId("X")
                        .mnuNm("그룹")
                        .mnuTpC("GRP")
                        .mnuSotSqnSno(10)
                        .hidYn("N")
                        .mnuDep(2)
                        .whlMnuPth("/X/MHED0009")
                        .delYn("N")
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("MHED0009", "N"))
                .willReturn(Optional.of(target));
        given(cmenumRepository.findSubtreeByPathPrefix("/X/MHED0009")).willReturn(List.of(target));

        // when
        service.move("MHED0009", null);

        // then: 루트(depth=1), 경로=/MHED0009
        assertThat(target.getHrkMnuId()).isNull();
        assertThat(target.getMnuDep()).isEqualTo(1);
        assertThat(target.getWhlMnuPth()).isEqualTo("/MHED0009");
    }

    @Test
    @DisplayName("create: GRP가 아닌 메뉴를 루트로 생성하면 예외를 던진다")
    void create_비GRP루트_예외() {
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("루트페이지")
                        .mnuTpC("PGE")
                        .hrkMnuId(null)
                        .srePth("/budget/list")
                        .build();
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(
                        Optional.of(Cmenud.builder().srePth("/budget/list").delYn("N").build()));

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다.");
    }

    @Test
    @DisplayName("update: 루트 메뉴의 유형을 PGE로 바꾸면 예외를 던진다")
    void update_루트메뉴를PGE로변경_예외() {
        Cmenum root = node("MHED0009", null, 1, "/MHED0009");
        given(cmenumRepository.findByMnuIdAndDelYn("MHED0009", "N")).willReturn(Optional.of(root));
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(
                        Optional.of(Cmenud.builder().srePth("/budget/list").delYn("N").build()));
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("루트")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .build();

        assertThatThrownBy(() -> service.update("MHED0009", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다.");
    }

    @Test
    @DisplayName("move: PGE 메뉴를 루트로 이동하면 예외를 던진다")
    void move_PGE를루트로이동_예외() {
        Cmenum target =
                Cmenum.builder()
                        .mnuId("M0002")
                        .hrkMnuId("P1")
                        .mnuNm("예산목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .mnuSotSqnSno(10)
                        .hidYn("N")
                        .mnuDep(3)
                        .whlMnuPth("/H/P1/M0002")
                        .delYn("N")
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("M0002", "N")).willReturn(Optional.of(target));

        assertThatThrownBy(() -> service.move("M0002", null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다.");
    }

    @Test
    @DisplayName("move: GRP 메뉴를 다른 메뉴 하위로 이동하면 허용한다")
    void move_GRP를하위로이동_허용() {
        Cmenum target = node("MHED0009", null, 1, "/MHED0009");
        Cmenum newParent = node("MHED0001", null, 1, "/MHED0001");
        given(cmenumRepository.findByMnuIdAndDelYn("MHED0009", "N"))
                .willReturn(Optional.of(target));
        given(cmenumRepository.findByMnuIdAndDelYn("MHED0001", "N"))
                .willReturn(Optional.of(newParent));
        given(cmenumRepository.findSubtreeByPathPrefix("/MHED0009")).willReturn(List.of(target));

        service.move("MHED0009", "MHED0001");

        assertThat(target.getHrkMnuId()).isEqualTo("MHED0001");
        assertThat(target.getMnuDep()).isEqualTo(2);
        assertThat(target.getWhlMnuPth()).isEqualTo("/MHED0001/MHED0009");
    }
}
