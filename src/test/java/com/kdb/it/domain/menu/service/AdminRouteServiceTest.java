package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminRouteServiceTest {

    @Mock CmenudRepository cmenudRepository;
    @Mock CmenumRepository cmenumRepository;
    @InjectMocks AdminRouteService service;

    /** 테스트용 Cmenud 엔티티 생성 헬퍼. */
    private Cmenud route(String srePth) {
        return Cmenud.builder().srePth(srePth).sreMnuNm("테스트화면")
                .sysHrkMnuId("01").useYn("Y").delYn("N").build();
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
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("create: http로 시작하는 경로이면 예외를 던진다")
    void create_http경로_예외() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("http://example.com").sreMnuNm("외부").build();
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class);
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
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
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
        given(cmenudRepository.findAllActive()).willReturn(List.of(
                route("/budget/list"), route("/project/list")
        ));

        // when
        List<Cmenud> result = service.listAll();

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("create: 유효한 경로이면 라우트를 저장한다")
    void create_유효한경로_저장() {
        // given
        MenuDto.Route r = MenuDto.Route.builder()
                .srePth("/new/route").sreMnuNm("새화면").sysHrkMnuId("01").useYn("Y")
                .build();
        given(cmenudRepository.findBySrePthAndDelYn("/new/route", "N")).willReturn(Optional.empty());

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
        MenuDto.Route r = MenuDto.Route.builder()
                .srePth("/budget/list").sreMnuNm("예산목록(수정)").useYn("Y")
                .build();
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(existing));

        // when
        service.update(r);

        // then
        verify(cmenudRepository).save(any(Cmenud.class));
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
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N")).willReturn(Optional.of(c));
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
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N")).willReturn(Optional.of(c));
        Cmenum menu = Cmenum.builder().mnuId("M1").mnuNm("예산목록").mnuTpC("LNK")
                .srePth("/budget/list").mnuSotSqnSno(10)
                .hidYn("N").mnuDep(1).whlMnuPth("/M1").delYn("N").build();
        given(cmenumRepository.findAllActive()).willReturn(List.of(menu));

        // when & then
        assertThatThrownBy(() -> service.delete("/budget/list"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("참조");
    }
}
