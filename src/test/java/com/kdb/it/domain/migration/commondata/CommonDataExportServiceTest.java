package com.kdb.it.domain.migration.commondata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommonDataExportServiceTest {

    @Mock private CmenumRepository cmenumRepository;
    @Mock private CmenuaRepository cmenuaRepository;
    @Mock private CmenudRepository cmenudRepository;
    @Mock private CodeRepository codeRepository;
    @Mock private ClangmRepository clangmRepository;

    @Test
    void 활성행전량을_행record로_변환해_내려준다() {
        when(cmenumRepository.findAllActive())
                .thenReturn(
                        List.of(
                                Cmenum.builder()
                                        .mnuId("MNU0000001")
                                        .mnuNm("관리자")
                                        .mnuTpC("GRP")
                                        .mnuSotSqnSno(1)
                                        .hidYn("N")
                                        .mnuDep(1)
                                        .whlMnuPth("/MNU0000001")
                                        .build()));
        when(cmenuaRepository.findAllActive())
                .thenReturn(List.of(Cmenua.builder().mnuId("MNU0000001").athId("ITPAD001").build()));
        when(cmenudRepository.findAllActive())
                .thenReturn(
                        List.of(
                                Cmenud.builder()
                                        .srePth("/admin/menus")
                                        .sreMnuNm("메뉴 관리")
                                        .useYn("Y")
                                        .build()));
        when(codeRepository.findAllActiveOrdered())
                .thenReturn(
                        List.of(
                                Ccodem.builder()
                                        .cId("PRJ_TP")
                                        .cdva("001")
                                        .sttDt("20250101")
                                        .cNm("사업유형")
                                        .build()));
        when(clangmRepository.findAllActive())
                .thenReturn(
                        List.of(
                                Clangm.builder()
                                        .tcIdCone("MNU0000001")
                                        .tcColNm("MNU_NM")
                                        .dttLanC("en")
                                        .tcDes("Admin")
                                        .dttNm("메뉴")
                                        .build()));

        CommonDataExportService service =
                new CommonDataExportService(
                        cmenumRepository,
                        cmenuaRepository,
                        cmenudRepository,
                        codeRepository,
                        clangmRepository);

        CommonDataMigrationDto.ExportResponse response = service.export();

        assertThat(response.menus()).hasSize(1);
        assertThat(response.menus().get(0).mnuId()).isEqualTo("MNU0000001");
        assertThat(response.menuAuths().get(0).athId()).isEqualTo("ITPAD001");
        assertThat(response.routes().get(0).srePth()).isEqualTo("/admin/menus");
        assertThat(response.codes().get(0).cId()).isEqualTo("PRJ_TP");
        assertThat(response.translations().get(0).dttLanC()).isEqualTo("en");
    }
}
