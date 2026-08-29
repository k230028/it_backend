package com.kdb.it.domain.migration.commondata;

import com.kdb.it.common.code.repository.CodeRepository;
import com.kdb.it.common.i18n.repository.ClangmRepository;
import com.kdb.it.domain.menu.repository.CmenuaRepository;
import com.kdb.it.domain.menu.repository.CmenudRepository;
import com.kdb.it.domain.menu.repository.CmenumRepository;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공통 데이터 5개 테이블(메뉴·메뉴권한·경로·공통코드·다국어)의 활성 행 전량을 이관용 JSON으로 내보냅니다. */
@Service
@RequiredArgsConstructor
public class CommonDataExportService {

    private final CmenumRepository cmenumRepository;
    private final CmenuaRepository cmenuaRepository;
    private final CmenudRepository cmenudRepository;
    private final CodeRepository codeRepository;
    private final ClangmRepository clangmRepository;

    /** 활성(DEL_YN='N') 행 전량을 조회해 행 record 묶음으로 반환합니다. excelRow는 0으로 채웁니다. */
    @Transactional(readOnly = true)
    public CommonDataMigrationDto.ExportResponse export() {
        return new CommonDataMigrationDto.ExportResponse(
                cmenumRepository.findAllActive().stream()
                        .map(
                                m ->
                                        new CommonDataMigrationDto.MenuRow(
                                                0,
                                                m.getMnuId(),
                                                m.getHrkMnuId(),
                                                m.getMnuNm(),
                                                m.getMnuTpC(),
                                                m.getImkNm(),
                                                m.getSrePth(),
                                                m.getMnuSotSqnSno(),
                                                m.getHidYn(),
                                                m.getMnuDep(),
                                                m.getWhlMnuPth()))
                        .toList(),
                cmenuaRepository.findAllActive().stream()
                        .map(
                                a ->
                                        new CommonDataMigrationDto.MenuAuthRow(
                                                0, a.getMnuId(), a.getAthId()))
                        .toList(),
                cmenudRepository.findAllActive().stream()
                        .map(
                                r ->
                                        new CommonDataMigrationDto.RouteRow(
                                                0,
                                                r.getSrePth(),
                                                r.getSreMnuNm(),
                                                r.getUseYn(),
                                                r.getRmk()))
                        .toList(),
                codeRepository.findAllActiveOrdered().stream()
                        .map(
                                c ->
                                        new CommonDataMigrationDto.CodeRow(
                                                0,
                                                c.getCId(),
                                                c.getCdva(),
                                                c.getSttDt(),
                                                c.getEndDt(),
                                                c.getCNm(),
                                                c.getCdvaNm(),
                                                c.getCdvaDes(),
                                                c.getCdvaDtl(),
                                                c.getCdvaDtlC(),
                                                c.getCTp(),
                                                c.getCTpDes(),
                                                c.getHrkC(),
                                                c.getCSqn()))
                        .toList(),
                clangmRepository.findAllActive().stream()
                        .map(
                                t ->
                                        new CommonDataMigrationDto.TranslationRow(
                                                0,
                                                t.getTcIdCone(),
                                                t.getTcColNm(),
                                                t.getDttLanC(),
                                                t.getTcDes(),
                                                t.getDttNm()))
                        .toList());
    }
}
