package com.kdb.it.domain.migration.commondata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.admin.service.AdminCodeService;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.common.iam.entity.CauthI;
import com.kdb.it.common.iam.repository.AuthRepository;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonDataMigrationServiceTest {

    @Mock private CmenumRepository cmenumRepository;
    @Mock private CmenuaRepository cmenuaRepository;
    @Mock private CmenudRepository cmenudRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ClangmRepository clangmRepository;
    @Mock private AuthRepository authRepository;
    @Mock private AdminCodeService adminCodeService;

    private CommonDataMigrationService service() {
        return new CommonDataMigrationService(
                new CommonDataMigrationPlanner(),
                cmenumRepository,
                cmenuaRepository,
                cmenudRepository,
                codeRepository,
                clangmRepository,
                authRepository,
                adminCodeService);
    }

    private static CommonDataMigrationDto.Request routeOnlyRequest() {
        return new CommonDataMigrationDto.Request(
                List.of(),
                List.of(),
                List.of(new CommonDataMigrationDto.RouteRow(2, "/admin/menus", "메뉴 관리", "Y", null)),
                List.of(),
                List.of());
    }

    @Test
    void dryRun은_저장하지않고_요약만돌려준다() {
        stubEmptySnapshot();

        CommonDataMigrationDto.Response response = service().dryRun(routeOnlyRequest());

        assertThat(response.committed()).isFalse();
        assertThat(summaryOf(response, "경로").added()).isEqualTo(1);
        verify(cmenudRepository, never()).saveAll(any());
        verify(adminCodeService, never()).bulkUpsertCodes(any());
    }

    @Test
    void commit은_신규경로를_저장한다() {
        stubEmptySnapshot();

        CommonDataMigrationDto.Response response = service().commit(routeOnlyRequest());

        assertThat(response.committed()).isTrue();
        verify(cmenudRepository).saveAll(any());
    }

    @Test
    void commit은_삭제된경로를_부활시킨다() {
        Cmenud deleted = Cmenud.builder().srePth("/admin/menus").sreMnuNm("옛이름").useYn("N").build();
        deleted.delete();
        stubEmptySnapshot();
        when(cmenudRepository.findAll()).thenReturn(List.of(deleted));

        service().commit(routeOnlyRequest());

        assertThat(deleted.getDelYn()).isEqualTo("N");
        assertThat(deleted.getSreMnuNm()).isEqualTo("메뉴 관리");
        assertThat(deleted.getUseYn()).isEqualTo("Y");
    }

    @Test
    void commit은_공통코드를_AdminCodeService에위임한다() {
        stubEmptySnapshot();
        when(adminCodeService.bulkUpsertCodes(any()))
                .thenReturn(Map.of("created", 1, "updated", 0));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2,
                                        "PRJ_TP",
                                        "001",
                                        "20250101",
                                        null,
                                        "사업유형",
                                        "신규",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        1)),
                        List.of());

        service().commit(request);

        verify(adminCodeService).bulkUpsertCodes(any(AdminDto.BulkCodeRequest.class));
    }

    @Test
    void 오류가있으면_commit은_거부한다() {
        stubEmptySnapshot();
        CommonDataMigrationDto.Request menusWithoutAuths =
                new CommonDataMigrationDto.Request(
                        List.of(
                                new CommonDataMigrationDto.MenuRow(
                                        2,
                                        "MNU0000001",
                                        null,
                                        "메뉴",
                                        "GRP",
                                        null,
                                        null,
                                        1,
                                        "N",
                                        1,
                                        "/MNU0000001")),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());

        assertThatThrownBy(() -> service().commit(menusWithoutAuths))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cmenumRepository, never()).saveAll(any());
    }

    @Test
    void commit은_신규메뉴와_메뉴권한을_저장한다() {
        stubEmptySnapshot();
        CauthI auth = CauthI.builder().athId("ITPZZ001").build();
        when(authRepository.findAll()).thenReturn(List.of(auth));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(
                                new CommonDataMigrationDto.MenuRow(
                                        2,
                                        "MNU0000001",
                                        null,
                                        "메뉴",
                                        "GRP",
                                        null,
                                        null,
                                        1,
                                        "N",
                                        1,
                                        "/MNU0000001")),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPZZ001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationDto.Response response = service().commit(request);

        assertThat(response.committed()).isTrue();
        assertThat(response.errors()).isEmpty();
        verify(cmenumRepository).saveAll(any());
        verify(cmenuaRepository).saveAll(any());
    }

    @Test
    void commit은_다국어를_update로_갱신하고_부활시킨다() {
        Clangm deleted =
                Clangm.builder()
                        .tcIdCone("MNU0000001")
                        .tcColNm("MNU_NM")
                        .dttLanC("en")
                        .tcDes("Old Name")
                        .dttNm("메뉴")
                        .build();
        deleted.delete();
        stubEmptySnapshot();
        when(clangmRepository.findAllByTcIdConeIn(anyCollection())).thenReturn(List.of(deleted));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "MNU_NM", "en", "New Name", "메뉴")));

        service().commit(request);

        assertThat(deleted.getTcDes()).isEqualTo("New Name");
        assertThat(deleted.getDelYn()).isEqualTo("N");
    }

    @Test
    void 빈요청은_아무것도저장하지않는다() {
        stubEmptySnapshot();
        CommonDataMigrationDto.Request emptyRequest =
                new CommonDataMigrationDto.Request(
                        List.of(), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationDto.Response response = service().commit(emptyRequest);

        assertThat(response.committed()).isTrue();
        assertThat(response.errors()).isEmpty();
        verify(cmenudRepository).saveAll(List.of());
        verify(cmenumRepository).saveAll(List.of());
        verify(cmenuaRepository).saveAll(List.of());
        verify(clangmRepository).saveAll(List.of());
        verify(adminCodeService, never()).bulkUpsertCodes(any());
    }

    private void stubEmptySnapshot() {
        // 코드·번역 조회는 파일에 해당 행이 있을 때만 호출되므로 lenient로 선언해
        // Mockito strict stubbing(UnnecessaryStubbingException)을 피한다.
        lenient().when(cmenumRepository.findAll()).thenReturn(List.of());
        lenient().when(cmenuaRepository.findAll()).thenReturn(List.of());
        lenient().when(cmenudRepository.findAll()).thenReturn(List.of());
        lenient().when(codeRepository.findAllByCIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(clangmRepository.findAllByTcIdConeIn(anyCollection())).thenReturn(List.of());
        lenient().when(authRepository.findAll()).thenReturn(List.of());
    }

    private static CommonDataMigrationDto.TableSummary summaryOf(
            CommonDataMigrationDto.Response response, String table) {
        return response.summaries().stream()
                .filter(s -> s.table().equals(table))
                .findFirst()
                .orElseThrow();
    }
}
