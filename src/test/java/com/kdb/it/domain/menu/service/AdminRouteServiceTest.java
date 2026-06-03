package com.kdb.it.domain.menu.service;

import com.kdb.it.domain.menu.dto.MenuDto;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminRouteServiceTest {

    @Mock CmenudRepository cmenudRepository;
    @Mock CmenumRepository cmenumRepository;
    @InjectMocks AdminRouteService service;

    @Test
    void create_rejectsNonRootedPath() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("budget/list").sreMnuNm("예산").build();
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("/");
    }

    @Test
    void create_rejectsDuplicate() {
        MenuDto.Route r = MenuDto.Route.builder().srePth("/budget/list").sreMnuNm("예산").build();
        given(cmenudRepository.findBySrePthAndDelYn("/budget/list", "N"))
                .willReturn(Optional.of(Cmenud.builder().srePth("/budget/list").build()));
        assertThatThrownBy(() -> service.create(r))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("중복");
    }
}
