package com.kdb.it.domain.budget.project.dto;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.util.Utf8ByteLimit;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;

/** 정보화사업 DTO의 엔티티 매핑과 DB BYTE 상한 판정을 담당합니다. */
final class ProjectDtoSupport {

    private ProjectDtoSupport() {}

    static Bprojm toEntity(ProjectDto.CreateRequest request) {
        return Bprojm.builder()
                .abusMngNo(request.getAbusMngNo())
                .sno(1)
                .abusNm(request.getAbusNm())
                .bzTpC(request.getBzTpC())
                .svnDpmC(request.getSvnDpmC())
                .svnTemC(request.getSvnTemC())
                .dvmDpmC(request.getDvmDpmC())
                .dvmTemC(request.getDvmTemC())
                .sttDtm(request.getSttDtm())
                .endDtm(request.getEndDtm())
                .usid(request.getUsid())
                .dvmUsid(request.getDvmUsid())
                .prlmHrkOgzCCone(request.getPrlmHrkOgzCCone())
                .tlrUsid(request.getTlrUsid())
                .dvmTlrUsid(request.getDvmTlrUsid())
                .edrtTc(request.getEdrtTc())
                .abusCone(request.getAbusCone())
                .cpnSafCone(request.getCpnSafCone())
                .abusNcsCone(request.getAbusNcsCone())
                .dgogPpoCone(request.getDgogPpoCone())
                .plmDes(request.getPlmDes())
                .abusRngCone(request.getAbusRngCone())
                .mnPrgCone(request.getMnPrgCone())
                .hrfPlnCone(request.getHrfPlnCone())
                .bzDttNm(request.getBzDttNm())
                .sklTpTc(request.getSklTpTc())
                .cstTpTc(request.getCstTpTc())
                .dplYn(request.getDplYn() == null ? "N" : request.getDplYn())
                .flfFsgDt(request.getFlfFsgDt())
                .rprStsTc(request.getRprStsTc())
                .lstYn("Y")
                .exePttYn(request.getExePttYn())
                .bseYy(request.getBseYy())
                .odnYn(request.getOdnYn())
                .abusTc(CodeDefaults.orNotApplicable(request.getAbusTc()))
                .cncdRfrNo(request.getCncdRfrNo())
                .build();
    }

    static ProjectDto.BitemmDto fromEntity(Bitemm item) {
        return ProjectDto.BitemmDto.builder()
                .gclMngNo(item.getGclMngNo())
                .sno(item.getSno())
                .ioeC(item.getIoeC())
                .gclNm(item.getGclNm())
                .qty(item.getQty())
                .curC(item.getCurC())
                .xcr(item.getXcr())
                .xcrBseDt(item.getXcrBseDt())
                .cncdFdtnCone(item.getCncdFdtnCone())
                .bseYm(item.getBseYm())
                .dfrCleC(item.getDfrCleC())
                .sectSysUtzYn(item.getSectSysUtzYn())
                .itrInfrYn(item.getItrInfrYn())
                .lstYn(item.getLstYn())
                .amt(item.getAmt())
                .fcAmt(item.getFcAmt())
                .mplAmt(item.getMplAmt())
                .build();
    }

    static boolean isTextWithinByteLimit(ProjectDto.CreateRequest request) {
        return isProjectTextWithinByteLimit(
                request.getAbusNm(),
                request.getAbusCone(),
                request.getCpnSafCone(),
                request.getAbusNcsCone(),
                request.getDgogPpoCone(),
                request.getPlmDes(),
                request.getAbusRngCone(),
                request.getMnPrgCone(),
                request.getHrfPlnCone());
    }

    static boolean isTextWithinByteLimit(ProjectDto.UpdateRequest request) {
        return isProjectTextWithinByteLimit(
                request.getAbusNm(),
                request.getAbusCone(),
                request.getCpnSafCone(),
                request.getAbusNcsCone(),
                request.getDgogPpoCone(),
                request.getPlmDes(),
                request.getAbusRngCone(),
                request.getMnPrgCone(),
                request.getHrfPlnCone());
    }

    static boolean isTextWithinByteLimit(ProjectDto.BitemmDto item) {
        return isWithinByteLimit(item.getGclNm(), 100)
                && isWithinByteLimit(item.getCncdFdtnCone(), 600);
    }

    static boolean isEmpty(ProjectDto.SearchCondition condition) {
        return isBlank(condition.getApfSts())
                && isBlank(condition.getBseYy())
                && isBlank(condition.getStsTc())
                && isBlank(condition.getBzTpC())
                && isBlank(condition.getDvmDpmC())
                && isBlank(condition.getSvnDpmC())
                && isBlank(condition.getOdnYn());
    }

    private static boolean isProjectTextWithinByteLimit(
            String abusNm,
            String abusCone,
            String cpnSafCone,
            String abusNcsCone,
            String dgogPpoCone,
            String plmDes,
            String abusRngCone,
            String mnPrgCone,
            String hrfPlnCone) {
        return isWithinByteLimit(abusNm, 100)
                && isWithinByteLimit(abusCone, 1000)
                && isWithinByteLimit(cpnSafCone, 1000)
                && isWithinByteLimit(abusNcsCone, 300)
                && isWithinByteLimit(dgogPpoCone, 4000)
                && isWithinByteLimit(plmDes, 4000)
                && isWithinByteLimit(abusRngCone, 600)
                && isWithinByteLimit(mnPrgCone, 2000)
                && isWithinByteLimit(hrfPlnCone, 300);
    }

    private static boolean isWithinByteLimit(String value, int maxBytes) {
        return Utf8ByteLimit.length(value) <= maxBytes;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
