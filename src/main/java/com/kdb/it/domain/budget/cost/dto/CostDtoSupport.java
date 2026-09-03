package com.kdb.it.domain.budget.cost.dto;

import com.kdb.it.common.code.CodeDefaults;
import com.kdb.it.common.util.DateFormatUtil;
import com.kdb.it.domain.budget.cost.entity.Bcostm;

/** 전산업무비 DTO의 엔티티 매핑을 담당합니다. */
final class CostDtoSupport {

    private CostDtoSupport() {}

    static Bcostm toEntity(CostDto.CreateRequest request, Integer nextSno) {
        return Bcostm.builder()
                .costBgNo(request.getCostBgNo())
                .bgSno(nextSno)
                .ioeC(request.getIoeC())
                .cttNm(request.getCttNm())
                .cttOppNm(request.getCttOppNm())
                .costTotXpAmt(request.getCostTotXpAmt())
                .dfrCleC(CodeDefaults.orNotApplicable(request.getDfrCleC()))
                .fstDfrDt(DateFormatUtil.toYmd8(request.getFstDfrDt()))
                .curC(request.getCurC())
                .xcr(request.getXcr())
                .xcrBseDt(DateFormatUtil.toYmd8(request.getXcrBseDt()))
                .sectSysUtzYn(request.getSectSysUtzYn() == null ? "N" : request.getSectSysUtzYn())
                .indRsn(request.getIndRsn())
                .cgprId(request.getCgprId())
                .costSvnDpmC(request.getCostSvnDpmC())
                .svnTemC(request.getSvnTemC())
                .bgUntAbusC(request.getBgUntAbusC())
                .tmnYn(request.getTmnYn())
                .abusTc(CodeDefaults.orNotApplicable(request.getAbusTc()))
                .bseYy(request.getBseYy())
                .cncdRfrNo(request.getCncdRfrNo())
                .fcAmt(request.getFcAmt())
                .lstYn("Y")
                .build();
    }
}
