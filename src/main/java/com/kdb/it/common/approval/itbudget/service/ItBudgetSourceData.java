package com.kdb.it.common.approval.itbudget.service;

import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.entity.BaseEntity;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;

/** 감사·GUID·연관 엔티티를 제외한 저장 업무 컬럼만 명시적으로 해시에 담는다. */
@RequiredArgsConstructor
final class ItBudgetSourceData {
    private final ItBudgetCanonicalJson canonical;

    Map<String, Object> capture(BaseEntity row) {
        return switch (row) {
            case Bprojm p -> project(p);
            case Bitemm i -> item(i);
            case Bcostm c -> cost(c);
            case Btermm t -> terminal(t);
            default -> throw ItBudgetSourceLoader.invalid("지원하지 않는 원장입니다.");
        };
    }

    private Map<String, Object> project(Bprojm p) {
        Map<String, Object> values = new TreeMap<>();
        values.put("abusMngNo", p.getAbusMngNo());
        values.put("sno", p.getSno());
        values.put("abusNm", p.getAbusNm());
        values.put("bzTpC", p.getBzTpC());
        values.put("svnDpmC", p.getSvnDpmC());
        values.put("svnTemC", p.getSvnTemC());
        values.put("svnDpmNm", p.getSvnDpmNm());
        values.put("svnTemNm", p.getSvnTemNm());
        values.put("dvmDpmC", p.getDvmDpmC());
        values.put("dvmTemC", p.getDvmTemC());
        values.put("sttDtm", p.getSttDtm());
        values.put("endDtm", p.getEndDtm());
        values.put("totRqmAmt", canonical.money(p.getTotRqmAmt()));
        values.put("mplAmt", canonical.money(p.getMplAmt()));
        values.put("dfrAmt", canonical.money(p.getDfrAmt()));
        values.put("usid", p.getUsid());
        values.put("dvmUsid", p.getDvmUsid());
        values.put("tlrUsid", p.getTlrUsid());
        values.put("tlrNm", p.getTlrNm());
        values.put("usrNm", p.getUsrNm());
        values.put("dvmTlrUsid", p.getDvmTlrUsid());
        values.put("edrtTc", p.getEdrtTc());
        values.put("abusPulConeInf", p.getAbusPulConeInf());
        values.put("cpnSafCone", p.getCpnSafCone());
        values.put("abusPulNcsInf", p.getAbusPulNcsInf());
        values.put("abusXptEffInf", p.getAbusXptEffInf());
        values.put("plmDes", p.getPlmDes());
        values.put("abusPulDrcnInf", p.getAbusPulDrcnInf());
        values.put("mnPrgCone", p.getMnPrgCone());
        values.put("hrfPlnCone", p.getHrfPlnCone());
        values.put("bzDttNm", p.getBzDttNm());
        values.put("sklTpTc", p.getSklTpTc());
        values.put("cstTpTc", p.getCstTpTc());
        values.put("dplYn", p.getDplYn());
        values.put("flfFsgDt", p.getFlfFsgDt());
        values.put("rprStsTc", p.getRprStsTc());
        values.put("lstYn", p.getLstYn());
        values.put("exePttYn", p.getExePttYn());
        values.put("bseYy", p.getBseYy());
        values.put("prlmHrkOgzCCone", p.getPrlmHrkOgzCCone());
        values.put("odnYn", p.getOdnYn());
        values.put("abusTc", p.getAbusTc());
        values.put("cncdRfrNo", p.getCncdRfrNo());
        values.put("delYn", p.getDelYn());
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> item(Bitemm p) {
        Map<String, Object> values = new TreeMap<>();
        values.put("gclMngNo", p.getGclMngNo());
        values.put("sno", p.getSno());
        values.put("abusMngNo", p.getAbusMngNo());
        values.put("fntTbCrySno", p.getFntTbCrySno());
        values.put("ioeC", p.getIoeC());
        values.put("gclNm", p.getGclNm());
        values.put("qty", canonical.quantity(p.getQty()));
        values.put("curC", p.getCurC());
        values.put("xcr", canonical.exchangeRate(p.getXcr()));
        values.put("xcrBseDt", p.getXcrBseDt());
        values.put("cncdFdtnCone", p.getCncdFdtnCone());
        values.put("bseYm", p.getBseYm());
        values.put("dfrCleC", p.getDfrCleC());
        values.put("sectSysUtzYn", p.getSectSysUtzYn());
        values.put("itrInfrYn", p.getItrInfrYn());
        values.put("lstYn", p.getLstYn());
        values.put("amt", canonical.money(p.getAmt()));
        values.put("mplAmt", canonical.money(p.getMplAmt()));
        values.put("fcAmt", canonical.money(p.getFcAmt()));
        values.put("delYn", p.getDelYn());
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> cost(Bcostm p) {
        Map<String, Object> values = new TreeMap<>();
        values.put("costBgNo", p.getCostBgNo());
        values.put("bgSno", p.getBgSno());
        values.put("lstYn", p.getLstYn());
        values.put("ioeC", p.getIoeC());
        values.put("cttNm", p.getCttNm());
        values.put("cttOppNm", p.getCttOppNm());
        values.put("costTotXpAmt", canonical.money(p.getCostTotXpAmt()));
        values.put("dfrCleC", p.getDfrCleC());
        values.put("fstDfrDt", p.getFstDfrDt());
        values.put("curC", p.getCurC());
        values.put("xcr", canonical.exchangeRate(p.getXcr()));
        values.put("xcrBseDt", p.getXcrBseDt());
        values.put("sectSysUtzYn", p.getSectSysUtzYn());
        values.put("indRsn", p.getIndRsn());
        values.put("cgprId", p.getCgprId());
        values.put("cgprNm", p.getCgprNm());
        values.put("prlmHrkOgzCCone", p.getPrlmHrkOgzCCone());
        values.put("costSvnDpmC", p.getCostSvnDpmC());
        values.put("svnTemC", p.getSvnTemC());
        values.put("svnDpmNm", p.getSvnDpmNm());
        values.put("svnTemNm", p.getSvnTemNm());
        values.put("bseYy", p.getBseYy());
        values.put("bgUntAbusC", p.getBgUntAbusC());
        values.put("tmnYn", p.getTmnYn());
        values.put("abusTc", p.getAbusTc());
        values.put("cncdRfrNo", p.getCncdRfrNo());
        values.put("fcAmt", canonical.money(p.getFcAmt()));
        values.put("delYn", p.getDelYn());
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> terminal(Btermm p) {
        Map<String, Object> values = new TreeMap<>();
        values.put("tmnMngNo", p.getTmnMngNo());
        values.put("sno", p.getSno());
        values.put("termBgNo", p.getTermBgNo());
        values.put("termBgSno", p.getTermBgSno());
        values.put("spfTmnNm", p.getSpfTmnNm());
        values.put("tmnKdTc", p.getTmnKdTc());
        values.put("nsfUsgCone", p.getNsfUsgCone());
        values.put("tmnClsfC", p.getTmnClsfC());
        values.put("termRqmBgAmt", canonical.money(p.getTermRqmBgAmt()));
        values.put("curC", p.getCurC());
        values.put("xcr", canonical.exchangeRate(p.getXcr()));
        values.put("xcrBseDt", p.getXcrBseDt());
        values.put("dfrCleC", p.getDfrCleC());
        values.put("indRsn", p.getIndRsn());
        values.put("cgprId", p.getCgprId());
        values.put("cgprNm", p.getCgprNm());
        values.put("termSvnTemC", p.getTermSvnTemC());
        values.put("svnTemNm", p.getSvnTemNm());
        values.put("termSvnDpmC", p.getTermSvnDpmC());
        values.put("svnDpmNm", p.getSvnDpmNm());
        values.put("rmk", p.getRmk());
        values.put("fcAmt", canonical.money(p.getFcAmt()));
        values.put("delYn", p.getDelYn());
        return Collections.unmodifiableMap(values);
    }
}
