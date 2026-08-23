package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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

    /** 게시판 PGE 경로는 라우트 카탈로그가 아니라 활성 게시판 목록으로 검증한다. */
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

    private Cmenud route(String path, String useYn) {
        return Cmenud.builder().srePth(path).sreMnuNm("테스트 경로").useYn(useYn).delYn("N").build();
    }

    /** 활성 게시판 한 건을 돌려주는 목록 대역. */
    private BoardMetaDto.Response board(String blbMngNo, String blbNm) {
        return BoardMetaDto.Response.builder().blbMngNo(blbMngNo).blbNm(blbNm).useYn("Y").build();
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
                .hasMessageContaining("GRP 메뉴는 경로를 가질 수 없습니다");
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
                .hasMessageContaining("메뉴 경로가 필수입니다.");
    }

    @Test
    @DisplayName("create: LNK는 사용 중인 외부 URL 카탈로그를 참조하면 저장한다")
    void create_LNK외부Url_저장() {
        String url = "https://docs.example.com/manual";
        given(cmenudRepository.findBySrePthAndDelYn(url, "N"))
                .willReturn(Optional.of(route(url, "Y")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000001");
        given(cmenumRepository.findByMnuIdAndDelYn("PARENT", "N"))
                .willReturn(Optional.of(node("PARENT", null, 1, "/PARENT")));
        MenuDto.UpsertRequest request =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("업무매뉴얼")
                        .mnuTpC("LNK")
                        .hrkMnuId("PARENT")
                        .srePth(url)
                        .build();

        service.create(request);

        verify(cmenumRepository).save(argThat(menu -> "LNK".equals(menu.getMnuTpC())));
    }

    @Test
    @DisplayName("create: PGE에 외부 URL을 지정하면 거부한다")
    void create_PGE외부Url_예외() {
        String url = "https://docs.example.com/manual";
        given(cmenudRepository.findBySrePthAndDelYn(url, "N"))
                .willReturn(Optional.of(route(url, "Y")));
        MenuDto.UpsertRequest request =
                MenuDto.UpsertRequest.builder().mnuNm("잘못된 화면").mnuTpC("PGE").srePth(url).build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PGE 메뉴는 내부 화면경로");
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
    // 게시판 PGE 경로 검증
    // =========================================================================

    private MenuDto.UpsertRequest boardMenuRequest(String srePth) {
        return MenuDto.UpsertRequest.builder()
                .mnuNm("공지사항")
                .mnuTpC("PGE")
                .hrkMnuId("MBRD0001")
                .srePth(srePth)
                .hidYn("N")
                .athIds(List.of())
                .build();
    }

    @Test
    @DisplayName("create: PGE 게시판 경로는 활성 게시판이면 저장한다")
    void create_PGE게시판_저장() {
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0001", "공지사항")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0000002");
        given(cmenumRepository.findByMnuIdAndDelYn("PARENT", "N"))
                .willReturn(Optional.of(node("PARENT", null, 1, "/PARENT")));
        MenuDto.UpsertRequest request =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("공지사항")
                        .mnuTpC("PGE")
                        .hrkMnuId("PARENT")
                        .srePth("/board/BLBM-0001")
                        .build();

        service.create(request);

        verify(cmenumRepository).save(argThat(menu -> "PGE".equals(menu.getMnuTpC())));
        verifyNoInteractions(cmenudRepository);
    }

    @Test
    @DisplayName("create: 사용 중이 아닌 게시판을 가리키면 예외를 던진다")
    void create_PGE게시판_없는게시판_예외() {
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0001", "공지사항")));

        assertThatThrownBy(() -> service.create(boardMenuRequest("/board/BLBM-9999")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사용 중인 게시판이 아닙니다");
    }

    @Test
    @DisplayName("create: 게시판 PGE 메뉴를 루트로 생성하면 예외를 던진다")
    void create_PGE게시판_루트_예외() {
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0001", "공지사항")));
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("공지사항")
                        .mnuTpC("PGE")
                        .hrkMnuId(null)
                        .srePth("/board/BLBM-0001")
                        .build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("최상위(루트) 메뉴는 메뉴그룹(GRP)만 가능합니다.");
    }

    @Test
    @DisplayName("update: 일반 PGE를 게시판 PGE 경로로 바꾸면 게시판으로 검증한다")
    void update_PGE게시판경로로변경_게시판검증() {
        Cmenum menu = node("M1", "MBRD0001", 3, "/MHED0006/MBRD0001/M1");
        menu.setMnuTpC("PGE");
        given(cmenumRepository.findByMnuIdAndDelYn("M1", "N")).willReturn(Optional.of(menu));
        given(boardMetaService.getAllActive()).willReturn(List.of(board("BLBM-0003", "질의응답")));
        given(cmenuaRepository.findByMnuId("M1")).willReturn(List.of());

        service.update("M1", boardMenuRequest("/board/BLBM-0003"));

        assertThat(menu.getMnuTpC()).isEqualTo("PGE");
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
                .willReturn(Optional.of(route("/budget/list", "Y")));

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
                .willReturn(Optional.of(route("/budget/list", "Y")));
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

    @Test
    @DisplayName("create: 준비중이면 메뉴 ID 기반 경로를 만들고 카탈로그에 등록한다")
    void create_준비중_경로자동생성및카탈로그등록() {
        // given
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("사업계획서 작성")
                        .mnuTpC("PGE")
                        .hrkMnuId("P1")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("P1", "N"))
                .willReturn(Optional.of(node("P1", "MHED0002", 2, "/MHED0002/P1")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0001018");
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001018", "N"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(route("/preparing/mnu0001018", "Y")));
        given(cmenuaRepository.findByMnuId("MNU0001018")).willReturn(List.of());

        // when
        String result = service.create(req);

        // then
        assertThat(result).isEqualTo("MNU0001018");
        ArgumentCaptor<Cmenud> catalog = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(catalog.capture());
        assertThat(catalog.getValue().getSrePth()).isEqualTo("/preparing/mnu0001018");
        assertThat(catalog.getValue().getSreMnuNm()).isEqualTo("사업계획서 작성 (준비중)");
        assertThat(catalog.getValue().getUseYn()).isEqualTo("Y");
        ArgumentCaptor<Cmenum> menu = ArgumentCaptor.forClass(Cmenum.class);
        verify(cmenumRepository).save(menu.capture());
        assertThat(menu.getValue().getSrePth()).isEqualTo("/preparing/mnu0001018");
    }

    @Test
    @DisplayName("create: 준비중은 페이지화면만 가능하다")
    void create_준비중인데GRP_400() {
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("새 그룹")
                        .mnuTpC("GRP")
                        .hrkMnuId("P1")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();
        given(cmenumRepository.nextMnuId()).willReturn("MNU0001019");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("준비중은 페이지화면만 가능합니다");
        verifyNoInteractions(cmenudRepository);
    }

    @Test
    @DisplayName("create: 메뉴명이 길어도 카탈로그 경로명은 100자를 넘지 않는다")
    void create_준비중_긴메뉴명_경로명절단() {
        String longName = "가".repeat(100);
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm(longName)
                        .mnuTpC("PGE")
                        .hrkMnuId("P1")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("P1", "N"))
                .willReturn(Optional.of(node("P1", "MHED0002", 2, "/MHED0002/P1")));
        given(cmenumRepository.nextMnuId()).willReturn("MNU0001020");
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001020", "N"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(route("/preparing/mnu0001020", "Y")));
        given(cmenuaRepository.findByMnuId("MNU0001020")).willReturn(List.of());

        service.create(req);

        ArgumentCaptor<Cmenud> catalog = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(catalog.capture());
        assertThat(catalog.getValue().getSreMnuNm()).hasSize(100);
        assertThat(catalog.getValue().getSreMnuNm()).endsWith(" (준비중)");
    }

    @Test
    @DisplayName("update: 준비중을 해제하면 그 메뉴의 자동 경로만 카탈로그에서 회수한다")
    void update_준비중해제_자동경로회수() {
        // given
        Cmenum menu = node("MNU0001018", "P1", 3, "/MHED0002/P1/MNU0001018");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001018");
        Cmenud generated = route("/preparing/mnu0001018", "Y");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001018", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(route("/budget/list", "Y")));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001018", "N"))
                .willReturn(Optional.of(generated));
        given(cmenuaRepository.findByMnuId("MNU0001018")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("예산 목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .preparingYn("N")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        // when
        service.update("MNU0001018", req);

        // then
        assertThat(menu.getSrePth()).isEqualTo("/budget/list");
        assertThat(generated.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("update: 수동 등록 준비중 경로는 해제해도 카탈로그에 남긴다")
    void update_준비중해제_수동경로보존() {
        Cmenum menu = node("MNU0001021", "P1", 3, "/MHED0002/P1/MNU0001021");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/cdp");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001021", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(route("/budget/list", "Y")));
        given(cmenuaRepository.findByMnuId("MNU0001021")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("예산 목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .preparingYn("N")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        service.update("MNU0001021", req);

        assertThat(menu.getSrePth()).isEqualTo("/budget/list");
        // 수동 경로는 조회조차 하지 않는다 — 자동 경로 이름과 다르기 때문이다
        verify(cmenudRepository, never()).findBySrePthAndDelYn("/preparing/cdp", "N");
    }

    @Test
    @DisplayName("update: 준비중 해제 요청이어도 새 경로가 여전히 자동 경로면 회수하지 않는다(C1 회귀 방지)")
    void update_준비중해제_새경로가여전히자동경로면_회수안함() {
        // given: 프론트가 preparingYn만 N으로 바꾸고 srePth를 비우지 않은 채(버그) 보낸 상황을 흉내낸다
        Cmenum menu = node("MNU0001018", "P1", 3, "/MHED0002/P1/MNU0001018");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001018");
        Cmenud generated = route("/preparing/mnu0001018", "Y");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001018", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001018", "N"))
                .willReturn(Optional.of(generated));
        given(cmenuaRepository.findByMnuId("MNU0001018")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("사업계획서 작성")
                        .mnuTpC("PGE")
                        .srePth("/preparing/mnu0001018") // 클라이언트가 비우지 않음
                        .preparingYn("N")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        // when
        service.update("MNU0001018", req);

        // then: 메뉴가 여전히 이 경로를 가리키므로 카탈로그 행이 살아 있어야 한다
        assertThat(menu.getSrePth()).isEqualTo("/preparing/mnu0001018");
        assertThat(generated.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("update: 자동 경로를 다른 활성 메뉴가 쓰고 있으면 회수하지 않는다(I1 회귀 방지)")
    void update_준비중해제_다른메뉴가자동경로참조중이면_회수안함() {
        // given: MNU0001018의 자동 경로를 MNU9999999가 화면경로로 선택해 쓰고 있다
        Cmenum menu = node("MNU0001018", "P1", 3, "/MHED0002/P1/MNU0001018");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001018");
        Cmenum otherMenu = node("MNU9999999", "P1", 3, "/MHED0002/P1/MNU9999999");
        otherMenu.setMnuTpC("PGE");
        otherMenu.setSrePth("/preparing/mnu0001018");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001018", "N"))
                .willReturn(Optional.of(menu));
        given(cmenumRepository.findAllActive()).willReturn(List.of(menu, otherMenu));
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(route("/budget/list", "Y")));
        given(cmenuaRepository.findByMnuId("MNU0001018")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("예산 목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .preparingYn("N")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        // when
        service.update("MNU0001018", req);

        // then: MNU0001018은 새 경로로 옮겨 갔지만 다른 메뉴가 아직 쓰므로 카탈로그 행은 건드리지 않는다
        // (조회조차 하지 않는다 — referencedByOtherActiveMenu에서 이미 걸러진다)
        assertThat(menu.getSrePth()).isEqualTo("/budget/list");
        verify(cmenudRepository, never()).findBySrePthAndDelYn("/preparing/mnu0001018", "N");
    }

    @Test
    @DisplayName("delete: 준비중 메뉴를 삭제하면 자동 등록 경로도 함께 회수한다(M1 회귀 방지)")
    void delete_준비중메뉴삭제_자동경로도회수() {
        Cmenum menu = node("MNU0001018", "P1", 3, "/MHED0002/P1/MNU0001018");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001018");
        Cmenud generated = route("/preparing/mnu0001018", "Y");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001018", "N"))
                .willReturn(Optional.of(menu));
        given(cmenumRepository.countActiveChildren("MNU0001018")).willReturn(0L);
        given(cmenuaRepository.findActiveByMnuId("MNU0001018")).willReturn(List.of());
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001018", "N"))
                .willReturn(Optional.of(generated));

        service.delete("MNU0001018");

        assertThat(menu.getDelYn()).isEqualTo("Y");
        assertThat(generated.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("delete: 준비중 메뉴를 삭제해도 다른 활성 메뉴가 그 자동 경로를 쓰면 회수하지 않는다")
    void delete_준비중메뉴삭제_다른메뉴가참조중이면_회수안함() {
        Cmenum menu = node("MNU0001018", "P1", 3, "/MHED0002/P1/MNU0001018");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001018");
        Cmenum otherMenu = node("MNU9999999", "P1", 3, "/MHED0002/P1/MNU9999999");
        otherMenu.setMnuTpC("PGE");
        otherMenu.setSrePth("/preparing/mnu0001018");
        Cmenud generated = route("/preparing/mnu0001018", "Y");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001018", "N"))
                .willReturn(Optional.of(menu));
        given(cmenumRepository.countActiveChildren("MNU0001018")).willReturn(0L);
        given(cmenuaRepository.findActiveByMnuId("MNU0001018")).willReturn(List.of());
        given(cmenumRepository.findAllActive()).willReturn(List.of(otherMenu));

        service.delete("MNU0001018");

        assertThat(menu.getDelYn()).isEqualTo("Y");
        assertThat(generated.getDelYn()).isEqualTo("N");
        verify(cmenudRepository, never()).findBySrePthAndDelYn("/preparing/mnu0001018", "N");
    }

    @Test
    @DisplayName("update: 기존 준비중 카탈로그 행을 재사용할 때 GUID를 그대로 옮겨 담는다(I2 회귀 방지)")
    void update_준비중유지_기존GUID보존() {
        // given: 이미 사용 중(useYn=Y)인 자동 등록 행을 같은 메뉴가 다시 저장으로 건드리는 상황
        Cmenum menu = node("MNU0001023", "P1", 3, "/MHED0002/P1/MNU0001023");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001023");
        Cmenud existingRoute =
                Cmenud.builder()
                        .srePth("/preparing/mnu0001023")
                        .sreMnuNm("옛 이름 (준비중)")
                        .useYn("Y")
                        .rmk("준비중 메뉴 자동 등록")
                        .guid("11111111-1111-1111-1111-111111111111")
                        .guidPrgSno(3)
                        .delYn("N")
                        .build();
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001023", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001023", "N"))
                .willReturn(Optional.of(existingRoute));
        given(cmenuaRepository.findByMnuId("MNU0001023")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("새 이름")
                        .mnuTpC("PGE")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        // when
        service.update("MNU0001023", req);

        // then: 새로 build한 detached 엔티티가 merge(UPDATE)될 때 기존 GUID가 그대로 실려야
        // NOT NULL 제약(ORA-01407)에 걸리지 않는다.
        ArgumentCaptor<Cmenud> catalog = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(catalog.capture());
        assertThat(catalog.getValue().getGuid()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(catalog.getValue().getGuidPrgSno()).isEqualTo(3);
    }

    @Test
    @DisplayName("update: 관리자가 미사용 처리한 자동 경로를 저장 한 번으로 되살리지 않는다(M4 회귀 방지)")
    void update_준비중유지_미사용경로되살리지않음() {
        // given: 관리자가 /admin/routes에서 이 자동 등록 행을 미사용(useYn=N) 처리해 두었다
        Cmenum menu = node("MNU0001023", "P1", 3, "/MHED0002/P1/MNU0001023");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/mnu0001023");
        Cmenud existingRoute = route("/preparing/mnu0001023", "N");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001023", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/mnu0001023", "N"))
                .willReturn(Optional.of(existingRoute));
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("새 이름")
                        .mnuTpC("PGE")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        // when & then: useYn을 몰래 'Y'로 되돌리지 않으므로, 미사용 경로에 대한 통상 검증에 걸려
        // 저장이 그대로 거부된다(관리자의 미사용 처리를 저장 한 번으로 무력화하지 않는다).
        assertThatThrownBy(() -> service.update("MNU0001023", req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("사용 가능한 라우트 카탈로그 경로가 아닙니다");

        // 저장을 시도한 카탈로그 행에도 useYn='N'이 그대로 실려 있어야 한다(몰래 'Y'로 되돌리지 않음).
        ArgumentCaptor<Cmenud> catalog = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(catalog.capture());
        assertThat(catalog.getValue().getUseYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("update: 이미 준비중 경로를 쓰는 메뉴는 저장해도 같은 경로를 유지한다")
    void update_준비중유지_기존경로보존() {
        Cmenum menu = node("MNU0001022", "P1", 3, "/MHED0002/P1/MNU0001022");
        menu.setMnuTpC("PGE");
        menu.setSrePth("/preparing/cdp");
        given(cmenumRepository.findByMnuIdAndDelYn("MNU0001022", "N"))
                .willReturn(Optional.of(menu));
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/cdp", "N"))
                .willReturn(Optional.of(route("/preparing/cdp", "Y")));
        given(cmenuaRepository.findByMnuId("MNU0001022")).willReturn(List.of());
        MenuDto.UpsertRequest req =
                MenuDto.UpsertRequest.builder()
                        .mnuNm("IT/AI CDP")
                        .mnuTpC("PGE")
                        .srePth(null)
                        .preparingYn("Y")
                        .hidYn("N")
                        .athIds(List.of())
                        .build();

        service.update("MNU0001022", req);

        assertThat(menu.getSrePth()).isEqualTo("/preparing/cdp");
        ArgumentCaptor<Cmenud> catalog = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(catalog.capture());
        assertThat(catalog.getValue().getSrePth()).isEqualTo("/preparing/cdp");
        assertThat(catalog.getValue().getSreMnuNm()).isEqualTo("IT/AI CDP (준비중)");
    }
}
