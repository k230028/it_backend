package com.kdb.it.domain.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
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
class AdminRouteServiceTest {

    @Mock CmenudRepository cmenudRepository;
    @Mock CmenumRepository cmenumRepository;
    @InjectMocks AdminRouteService service;

    /** 테스트용 Cmenud 엔티티 생성 헬퍼. */
    private Cmenud route(String srePth) {
        return Cmenud.builder().srePth(srePth).sreMnuNm("테스트화면").useYn("Y").delYn("N").build();
    }

    @Test
    void create_rejectsNonRootedPath() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("budget/list").sreMnuNm("예산").build();
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("/");
    }

    @Test
    @DisplayName("create: 공백이 포함된 경로이면 예외를 던진다")
    void create_공백포함경로_예외() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("/budget list").sreMnuNm("예산").build();
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("create: 안전한 HTTPS 외부 URL을 경로로 저장한다")
    void create_외부Url_저장() {
        MenuDto.Route route =
                MenuDto.Route.builder()
                        .srePth("https://docs.example.com/manual")
                        .sreMnuNm("업무매뉴얼")
                        .useYn("Y")
                        .build();
        given(cmenudRepository.findById(route.getSrePth())).willReturn(Optional.empty());

        service.create(route);

        verify(cmenudRepository).save(any(Cmenud.class));
    }

    @Test
    @DisplayName("create: javascript URL은 거부한다")
    void create_javascriptUrl_예외() {
        MenuDto.Route route =
                MenuDto.Route.builder().srePth("javascript:alert(1)").sreMnuNm("위험").build();

        assertThatThrownBy(() -> service.create(route))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("내부 경로 또는 안전한 http(s) URL");
    }

    @Test
    @DisplayName("delete: 존재하지 않는 경로이면 예외를 던진다")
    void delete_존재하지않는경로_예외() {
        // given
        given(cmenudRepository.findBySrePthAndDelYn("/no/path", "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.delete("/no/path"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("없는 경로");
    }

    @Test
    void create_rejectsDuplicate() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("/budget/list").sreMnuNm("예산").build();
        given(cmenudRepository.findById("/budget/list"))
                .willReturn(Optional.of(route("/budget/list")));
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("listUsable: useYn='Y'이고 삭제되지 않은 라우트 목록을 반환한다")
    void listUsable_활성라우트_반환() {
        // given
        given(cmenudRepository.findAllUsable()).willReturn(List.of(route("/budget/list")));

        // when
        List<Cmenud> result = service.listUsable();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSrePth()).isEqualTo("/budget/list");
    }

    @Test
    @DisplayName("listAll: 삭제되지 않은 전체 라우트 목록을 반환한다")
    void listAll_전체라우트_반환() {
        // given
        given(cmenudRepository.findAllActive())
                .willReturn(List.of(route("/budget/list"), route("/project/list")));

        // when
        List<Cmenud> result = service.listAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("create: 유효한 경로이면 라우트를 저장한다")
    void create_유효한경로_저장() {
        // given
        MenuDto.Route r =
                MenuDto.Route.builder().srePth("/new/route").sreMnuNm("새화면").useYn("Y").build();
        given(cmenudRepository.findById("/new/route")).willReturn(Optional.empty());

        // when
        service.create(r);

        // then
        verify(cmenudRepository).save(any(Cmenud.class));
    }

    @Test
    @DisplayName("update: 존재하는 경로이면 라우트 정보를 수정한다")
    void update_존재하는경로_수정() {
        // given
        Cmenud existing = route("/budget/list");
        MenuDto.Route r =
                MenuDto.Route.builder()
                        .srePth("/budget/list")
                        .sreMnuNm("예산목록(수정)")
                        .useYn("Y")
                        .build();
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(existing));

        // when
        service.update(r);

        // then
        verify(cmenudRepository).save(any(Cmenud.class));
    }

    @Test
    @DisplayName("create: 논리삭제된 같은 경로가 남아 있으면 그 행을 되살리며 GUID를 유지한다")
    void create_삭제된경로_되살리고_GUID유지() {
        /*
         * 화면경로가 기본키라 이 저장은 INSERT가 아니라 merge(UPDATE)다. GUID를 비워 두면
         * @PrePersist가 돌지 않아 NULL이 나가고 NOT NULL 제약(ORA-01407)에 걸린다.
         */
        Cmenud deleted =
                Cmenud.builder()
                        .srePth("/budget/list")
                        .sreMnuNm("예산목록")
                        .useYn("Y")
                        .guid("11111111-2222-3333-4444-555555555555")
                        .guidPrgSno(1)
                        .delYn("Y")
                        .build();
        given(cmenudRepository.findById("/budget/list")).willReturn(Optional.of(deleted));

        service.create(
                MenuDto.Route.builder().srePth("/budget/list").sreMnuNm("예산목록(재등록)").build());

        ArgumentCaptor<Cmenud> saved = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(saved.capture());
        assertThat(saved.getValue().getGuid()).isEqualTo(deleted.getGuid());
        assertThat(saved.getValue().getGuidPrgSno()).isEqualTo(deleted.getGuidPrgSno());
        assertThat(saved.getValue().getDelYn()).isEqualTo("N");
        assertThat(saved.getValue().getSreMnuNm()).isEqualTo("예산목록(재등록)");
    }

    @Test
    @DisplayName("update: 비고를 고쳐도 GUID·GUID진행일련번호를 기존 행에서 옮겨 담는다")
    void update_기존GUID를_유지한다() {
        /*
         * setter가 없어 같은 PK로 새 엔티티를 저장(merge)하는데, GUID를 비워 두면 UPDATE 경로에서
         * @PrePersist가 돌지 않아 NULL이 나가고 NOT NULL 제약(ORA-01407)에 걸린다.
         */
        Cmenud existing =
                Cmenud.builder()
                        .srePth("/preparing/minf0017")
                        .sreMnuNm("준비중 (준비중)")
                        .useYn("Y")
                        .rmk(MenuPathPolicy.PREPARING_ROUTE_RMK)
                        .guid("11111111-2222-3333-4444-555555555555")
                        .guidPrgSno(1)
                        .delYn("N")
                        .build();
        given(cmenudRepository.findBySrePthAndDelYn("/preparing/minf0017", "N"))
                .willReturn(Optional.of(existing));

        service.update(
                MenuDto.Route.builder()
                        .srePth("/preparing/minf0017")
                        .sreMnuNm("준비중 (준비중)")
                        .useYn("Y")
                        .rmk("2027년 1월 오픈 예정")
                        .build());

        ArgumentCaptor<Cmenud> saved = ArgumentCaptor.forClass(Cmenud.class);
        verify(cmenudRepository).save(saved.capture());
        assertThat(saved.getValue().getGuid()).isEqualTo(existing.getGuid());
        assertThat(saved.getValue().getGuidPrgSno()).isEqualTo(existing.getGuidPrgSno());
        assertThat(saved.getValue().getRmk()).isEqualTo("2027년 1월 오픈 예정");
    }

    @Test
    @DisplayName("update: 존재하지 않는 경로이면 예외를 던진다")
    void update_존재하지않는경로_예외() {
        // given
        given(cmenudRepository.findBySrePthAndDelYn("/no/path", "N")).willReturn(Optional.empty());
        MenuDto.Route r = MenuDto.Route.builder().srePth("/no/path").sreMnuNm("없는화면").build();

        // when & then
        assertThatThrownBy(() -> service.update(r))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("없는 경로");
    }

    @Test
    @DisplayName("delete: 메뉴 참조 없는 경로이면 Soft Delete 처리한다")
    void delete_메뉴참조없음_소프트딜리트() {
        // given
        Cmenud c = route("/budget/list");
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(c));
        given(cmenumRepository.findAllActive()).willReturn(List.of());

        // when
        service.delete("/budget/list");

        // then: Soft Delete 확인 (delYn='Y')
        assertThat(c.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("delete: 메뉴에서 참조 중인 경로이면 예외를 던진다")
    void delete_메뉴참조중_예외() {
        // given
        Cmenud c = route("/budget/list");
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(c));
        Cmenum menu =
                Cmenum.builder()
                        .mnuId("M1")
                        .mnuNm("예산목록")
                        .mnuTpC("PGE")
                        .srePth("/budget/list")
                        .mnuSotSqnSno(10)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/M1")
                        .delYn("N")
                        .build();
        given(cmenumRepository.findAllActive()).willReturn(List.of(menu));

        // when & then
        assertThatThrownBy(() -> service.delete("/budget/list"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("참조");
    }
}
