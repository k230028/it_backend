package com.kdb.it.common.approval.itbudget.service;

import com.kdb.it.common.approval.itbudget.dto.ItBudgetApprovalDto.SourceKind;
import com.kdb.it.common.approval.itbudget.model.ItBudgetLedgerSnapshot;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.entity.BaseEntity;
import com.kdb.it.infra.file.entity.Cfilem;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 상신 시점의 전산예산 원장 엔티티를 물리 컬럼명 기반 스냅샷으로 변환한다. */
@Component
public final class ItBudgetLedgerCapture {

    private static final Set<String> BASE_COLUMNS =
            Set.of(
                    "DEL_YN",
                    "GUID",
                    "GUID_PRG_SNO",
                    "FST_ENR_DTM",
                    "FST_ENR_USID",
                    "LST_CHG_DTM",
                    "LST_CHG_USID");

    private static final Set<String> BPROJM_COLUMNS =
            columns(
                    "ABUS_MNG_NO",
                    "SNO",
                    "ABUS_NM",
                    "ABUS_PPO_CONE",
                    "SVN_DPM_C",
                    "SVN_TEM_C",
                    "SVN_DPM_NM",
                    "SVN_TEM_NM",
                    "DVM_DPM_C",
                    "DVM_TEM_C",
                    "STT_DTM",
                    "END_DTM",
                    "TOT_RQM_AMT",
                    "MPL_AMT",
                    "DFR_AMT",
                    "USID",
                    "DVM_USID",
                    "TLR_USID",
                    "TLR_NM",
                    "USR_NM",
                    "DVM_TLR_USID",
                    "IT_PTL_EDRT_TC",
                    "ABUS_PUL_CONE_INF",
                    "CPN_SAF_CONE",
                    "ABUS_PUL_NCS_INF",
                    "ABUS_XPT_EFF_INF",
                    "PLM_DES",
                    "ABUS_PUL_DRCN_INF",
                    "MN_PRG_CONE",
                    "HRF_PLN_CONE",
                    "BZ_DTT_NM",
                    "SKL_FLD_NM",
                    "CST_TP_TC_NM",
                    "DPL_YN",
                    "FLF_FSG_DT",
                    "IT_PTL_RPR_STS_TC",
                    "LST_YN",
                    "EXE_PTT_YN",
                    "BSE_YY",
                    "PRLM_HRK_OGZ_C_CONE",
                    "ODN_YN",
                    "ABUS_TC",
                    "CNCD_RFR_NO");

    private static final Set<String> BITEMM_COLUMNS =
            columns(
                    "GCL_MNG_NO",
                    "SNO",
                    "ABUS_MNG_NO",
                    "FNT_TB_CRY_SNO",
                    "IOE_C",
                    "GCL_NM",
                    "QTY",
                    "CUR_C",
                    "XCR",
                    "XCR_BSE_DT",
                    "CNCD_FDTN_CONE",
                    "BSE_YM",
                    "DFR_CLE_C",
                    "SECT_SYS_UTZ_YN",
                    "ITR_INFR_YN",
                    "LST_YN",
                    "AMT",
                    "MPL_AMT",
                    "FC_AMT");

    private static final Set<String> BCOSTM_COLUMNS =
            columns(
                    "BG_NO",
                    "BG_SNO",
                    "LST_YN",
                    "IOE_C",
                    "CTT_NM",
                    "CTT_OPP_NM",
                    "AMT",
                    "DFR_CLE_C",
                    "FST_DFR_DT",
                    "CUR_C",
                    "XCR",
                    "XCR_BSE_DT",
                    "SECT_SYS_UTZ_YN",
                    "IND_RSN",
                    "CGPR_ID",
                    "CGPR_NM",
                    "PRLM_HRK_OGZ_C_CONE",
                    "SVN_DPM_C",
                    "SVN_TEM_C",
                    "SVN_DPM_NM",
                    "SVN_TEM_NM",
                    "BSE_YY",
                    "BG_UNT_ABUS_C",
                    "TMN_YN",
                    "ABUS_TC",
                    "CNCD_RFR_NO",
                    "FC_AMT");

    private static final Set<String> BTERMM_COLUMNS =
            columns(
                    "TMN_MNG_NO",
                    "SNO",
                    "BG_NO",
                    "BG_SNO",
                    "SPF_TMN_NM",
                    "IT_PTL_TMN_KD_TC",
                    "NSF_USG_CONE",
                    "IT_PTL_TMN_SVC_TC",
                    "AMT",
                    "CUR_C",
                    "XCR",
                    "XCR_BSE_DT",
                    "DFR_CLE_C",
                    "IND_RSN",
                    "CGPR_ID",
                    "CGPR_NM",
                    "SVN_TEM_C",
                    "SVN_TEM_NM",
                    "SVN_DPM_C",
                    "SVN_DPM_NM",
                    "RMK",
                    "FC_AMT");

    private static final Set<String> CFILEM_COLUMNS =
            columns(
                    "FL_MPN_ID",
                    "FL_NM",
                    "FL_PYS_NM",
                    "FL_KPN_PTH",
                    "FL_TP_CONE",
                    "APG_FL_SZ",
                    "APG_FL_PTH",
                    "APG_FL_KD_NM",
                    "APG_FL_LNK_CTZ_NM");

    private final ItBudgetCanonicalJson canonical;

    public ItBudgetLedgerCapture(ItBudgetCanonicalJson canonical) {
        this.canonical = canonical;
    }

    /** 적재 순서를 유지하며 사업·전산업무비 aggregate를 원장 스냅샷으로 변환한다. */
    public ItBudgetLedgerSnapshot capture(List<ItBudgetSourceLoader.SourceAggregate> aggregates) {
        return new ItBudgetLedgerSnapshot(
                "IT_BUDGET_LEDGER_V1", aggregates.stream().map(this::captureAggregate).toList());
    }

    /** 구조 계약 테스트가 엔티티의 영속 컬럼 집합과 대조할 테이블별 선언을 반환한다. */
    Set<String> declaredColumns(Class<? extends BaseEntity> entityType) {
        if (entityType == Bprojm.class) return BPROJM_COLUMNS;
        if (entityType == Bitemm.class) return BITEMM_COLUMNS;
        if (entityType == Bcostm.class) return BCOSTM_COLUMNS;
        if (entityType == Btermm.class) return BTERMM_COLUMNS;
        if (entityType == Cfilem.class) return CFILEM_COLUMNS;
        throw new IllegalArgumentException("지원하지 않는 원장 엔티티입니다: " + entityType.getName());
    }

    private ItBudgetLedgerSnapshot.Aggregate captureAggregate(
            ItBudgetSourceLoader.SourceAggregate aggregate) {
        return aggregate.ref().kind() == SourceKind.PROJECT
                ? captureProject(aggregate)
                : captureCost(aggregate);
    }

    private ItBudgetLedgerSnapshot.Aggregate captureProject(
            ItBudgetSourceLoader.SourceAggregate aggregate) {
        Bprojm parent = (Bprojm) aggregate.parent();
        List<ItBudgetLedgerSnapshot.Row> children =
                aggregate.children().stream().map(Bitemm.class::cast).map(this::capture).toList();
        return new ItBudgetLedgerSnapshot.Aggregate(
                "PROJECT",
                aggregate.ref().id(),
                aggregate.ref().revision(),
                capture(parent),
                children,
                captureAttachments(aggregate));
    }

    private ItBudgetLedgerSnapshot.Aggregate captureCost(
            ItBudgetSourceLoader.SourceAggregate aggregate) {
        Bcostm parent = (Bcostm) aggregate.parent();
        List<ItBudgetLedgerSnapshot.Row> children =
                aggregate.children().stream().map(Btermm.class::cast).map(this::capture).toList();
        return new ItBudgetLedgerSnapshot.Aggregate(
                "COST",
                aggregate.ref().id(),
                aggregate.ref().revision(),
                capture(parent),
                children,
                captureAttachments(aggregate));
    }

    private List<ItBudgetLedgerSnapshot.Row> captureAttachments(
            ItBudgetSourceLoader.SourceAggregate aggregate) {
        return aggregate.attachments().stream().map(this::capture).toList();
    }

    private ItBudgetLedgerSnapshot.Row capture(Bprojm project) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ABUS_MNG_NO", project.getAbusMngNo());
        values.put("SNO", project.getSno());
        values.put("ABUS_NM", project.getAbusNm());
        values.put("ABUS_PPO_CONE", project.getBzTpC());
        values.put("SVN_DPM_C", project.getSvnDpmC());
        values.put("SVN_TEM_C", project.getSvnTemC());
        values.put("SVN_DPM_NM", project.getSvnDpmNm());
        values.put("SVN_TEM_NM", project.getSvnTemNm());
        values.put("DVM_DPM_C", project.getDvmDpmC());
        values.put("DVM_TEM_C", project.getDvmTemC());
        values.put("STT_DTM", project.getSttDtm());
        values.put("END_DTM", project.getEndDtm());
        values.put("TOT_RQM_AMT", money(project.getTotRqmAmt()));
        values.put("MPL_AMT", money(project.getMplAmt()));
        values.put("DFR_AMT", money(project.getDfrAmt()));
        values.put("USID", project.getUsid());
        values.put("DVM_USID", project.getDvmUsid());
        values.put("TLR_USID", project.getTlrUsid());
        values.put("TLR_NM", project.getTlrNm());
        values.put("USR_NM", project.getUsrNm());
        values.put("DVM_TLR_USID", project.getDvmTlrUsid());
        values.put("IT_PTL_EDRT_TC", project.getEdrtTc());
        values.put("ABUS_PUL_CONE_INF", project.getAbusPulConeInf());
        values.put("CPN_SAF_CONE", project.getCpnSafCone());
        values.put("ABUS_PUL_NCS_INF", project.getAbusPulNcsInf());
        values.put("ABUS_XPT_EFF_INF", project.getAbusXptEffInf());
        values.put("PLM_DES", project.getPlmDes());
        values.put("ABUS_PUL_DRCN_INF", project.getAbusPulDrcnInf());
        values.put("MN_PRG_CONE", project.getMnPrgCone());
        values.put("HRF_PLN_CONE", project.getHrfPlnCone());
        values.put("BZ_DTT_NM", project.getBzDttNm());
        values.put("SKL_FLD_NM", project.getSklTpTc());
        values.put("CST_TP_TC_NM", project.getCstTpTc());
        values.put("DPL_YN", project.getDplYn());
        values.put("FLF_FSG_DT", project.getFlfFsgDt());
        values.put("IT_PTL_RPR_STS_TC", project.getRprStsTc());
        values.put("LST_YN", project.getLstYn());
        values.put("EXE_PTT_YN", project.getExePttYn());
        values.put("BSE_YY", project.getBseYy());
        values.put("PRLM_HRK_OGZ_C_CONE", project.getPrlmHrkOgzCCone());
        values.put("ODN_YN", project.getOdnYn());
        values.put("ABUS_TC", project.getAbusTc());
        values.put("CNCD_RFR_NO", project.getCncdRfrNo());
        putBaseColumns(values, project);
        return row("BPROJM", BPROJM_COLUMNS, values);
    }

    private ItBudgetLedgerSnapshot.Row capture(Bitemm item) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("GCL_MNG_NO", item.getGclMngNo());
        values.put("SNO", item.getSno());
        values.put("ABUS_MNG_NO", item.getAbusMngNo());
        values.put("FNT_TB_CRY_SNO", item.getFntTbCrySno());
        values.put("IOE_C", item.getIoeC());
        values.put("GCL_NM", item.getGclNm());
        values.put("QTY", quantity(item.getQty()));
        values.put("CUR_C", item.getCurC());
        values.put("XCR", exchangeRate(item.getXcr()));
        values.put("XCR_BSE_DT", item.getXcrBseDt());
        values.put("CNCD_FDTN_CONE", item.getCncdFdtnCone());
        values.put("BSE_YM", item.getBseYm());
        values.put("DFR_CLE_C", item.getDfrCleC());
        values.put("SECT_SYS_UTZ_YN", item.getSectSysUtzYn());
        values.put("ITR_INFR_YN", item.getItrInfrYn());
        values.put("LST_YN", item.getLstYn());
        values.put("AMT", money(item.getAmt()));
        values.put("MPL_AMT", money(item.getMplAmt()));
        values.put("FC_AMT", money(item.getFcAmt()));
        putBaseColumns(values, item);
        return row("BITEMM", BITEMM_COLUMNS, values);
    }

    private ItBudgetLedgerSnapshot.Row capture(Bcostm cost) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("BG_NO", cost.getCostBgNo());
        values.put("BG_SNO", cost.getBgSno());
        values.put("LST_YN", cost.getLstYn());
        values.put("IOE_C", cost.getIoeC());
        values.put("CTT_NM", cost.getCttNm());
        values.put("CTT_OPP_NM", cost.getCttOppNm());
        values.put("AMT", money(cost.getCostTotXpAmt()));
        values.put("DFR_CLE_C", cost.getDfrCleC());
        values.put("FST_DFR_DT", cost.getFstDfrDt());
        values.put("CUR_C", cost.getCurC());
        values.put("XCR", exchangeRate(cost.getXcr()));
        values.put("XCR_BSE_DT", cost.getXcrBseDt());
        values.put("SECT_SYS_UTZ_YN", cost.getSectSysUtzYn());
        values.put("IND_RSN", cost.getIndRsn());
        values.put("CGPR_ID", cost.getCgprId());
        values.put("CGPR_NM", cost.getCgprNm());
        values.put("PRLM_HRK_OGZ_C_CONE", cost.getPrlmHrkOgzCCone());
        values.put("SVN_DPM_C", cost.getCostSvnDpmC());
        values.put("SVN_TEM_C", cost.getSvnTemC());
        values.put("SVN_DPM_NM", cost.getSvnDpmNm());
        values.put("SVN_TEM_NM", cost.getSvnTemNm());
        values.put("BSE_YY", cost.getBseYy());
        values.put("BG_UNT_ABUS_C", cost.getBgUntAbusC());
        values.put("TMN_YN", cost.getTmnYn());
        values.put("ABUS_TC", cost.getAbusTc());
        values.put("CNCD_RFR_NO", cost.getCncdRfrNo());
        values.put("FC_AMT", money(cost.getFcAmt()));
        putBaseColumns(values, cost);
        return row("BCOSTM", BCOSTM_COLUMNS, values);
    }

    private ItBudgetLedgerSnapshot.Row capture(Btermm terminal) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("TMN_MNG_NO", terminal.getTmnMngNo());
        values.put("SNO", terminal.getSno());
        values.put("BG_NO", terminal.getTermBgNo());
        values.put("BG_SNO", terminal.getTermBgSno());
        values.put("SPF_TMN_NM", terminal.getSpfTmnNm());
        values.put("IT_PTL_TMN_KD_TC", terminal.getTmnKdTc());
        values.put("NSF_USG_CONE", terminal.getNsfUsgCone());
        values.put("IT_PTL_TMN_SVC_TC", terminal.getTmnClsfC());
        values.put("AMT", money(terminal.getTermRqmBgAmt()));
        values.put("CUR_C", terminal.getCurC());
        values.put("XCR", exchangeRate(terminal.getXcr()));
        values.put("XCR_BSE_DT", terminal.getXcrBseDt());
        values.put("DFR_CLE_C", terminal.getDfrCleC());
        values.put("IND_RSN", terminal.getIndRsn());
        values.put("CGPR_ID", terminal.getCgprId());
        values.put("CGPR_NM", terminal.getCgprNm());
        values.put("SVN_TEM_C", terminal.getTermSvnTemC());
        values.put("SVN_TEM_NM", terminal.getSvnTemNm());
        values.put("SVN_DPM_C", terminal.getTermSvnDpmC());
        values.put("SVN_DPM_NM", terminal.getSvnDpmNm());
        values.put("RMK", terminal.getRmk());
        values.put("FC_AMT", money(terminal.getFcAmt()));
        putBaseColumns(values, terminal);
        return row("BTERMM", BTERMM_COLUMNS, values);
    }

    private ItBudgetLedgerSnapshot.Row capture(Cfilem file) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("FL_MPN_ID", file.getFlMpnId());
        values.put("FL_NM", file.getFlNm());
        values.put("FL_PYS_NM", file.getFlPysNm());
        values.put("FL_KPN_PTH", file.getFlKpnPth());
        values.put("FL_TP_CONE", file.getFlTpCone());
        values.put("APG_FL_SZ", file.getApgFlSz());
        values.put("APG_FL_PTH", file.getApgFlPth());
        values.put("APG_FL_KD_NM", file.getApgFlKdNm());
        values.put("APG_FL_LNK_CTZ_NM", file.getApgFlLnkCtzNm());
        putBaseColumns(values, file);
        return row("CFILEM", CFILEM_COLUMNS, values);
    }

    private void putBaseColumns(Map<String, Object> values, BaseEntity entity) {
        values.put("DEL_YN", entity.getDelYn());
        values.put("GUID", entity.getGuid());
        values.put("GUID_PRG_SNO", entity.getGuidPrgSno());
        values.put("FST_ENR_DTM", entity.getFstEnrDtm());
        values.put("FST_ENR_USID", entity.getFstEnrUsid());
        values.put("LST_CHG_DTM", entity.getLstChgDtm());
        values.put("LST_CHG_USID", entity.getLstChgUsid());
    }

    private ItBudgetLedgerSnapshot.Row row(
            String table, Set<String> declaredColumns, Map<String, Object> values) {
        if (!values.keySet().equals(declaredColumns)) {
            throw new IllegalStateException(table + " 원장 컬럼 캡처 선언과 실제 값이 일치하지 않습니다.");
        }
        return new ItBudgetLedgerSnapshot.Row(table, values);
    }

    private String money(BigDecimal value) {
        return plain(canonical.money(value));
    }

    private String exchangeRate(BigDecimal value) {
        return plain(canonical.exchangeRate(value));
    }

    private String quantity(BigDecimal value) {
        return plain(canonical.quantity(value));
    }

    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static Set<String> columns(String... entityColumns) {
        LinkedHashSet<String> columns = new LinkedHashSet<>(BASE_COLUMNS);
        columns.addAll(Arrays.asList(entityColumns));
        return Set.copyOf(columns);
    }
}
