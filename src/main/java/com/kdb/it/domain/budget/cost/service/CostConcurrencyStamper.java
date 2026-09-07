package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 전산업무비 개정본의 업무 내용을 SHA-256 스탬프로 요약한다.
 *
 * <p>조회 응답과 저장 검증이 같은 입력 집합을 쓰도록 이 컴포넌트 하나만 사용한다. 공통 감사 필드(LST_CHG_DTM 등)와 결재 상태 필드(LST_YN)는 입력에서
 * 제외하므로, 결재 진행이나 의미상 동일한 원복은 충돌로 판정되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CostConcurrencyStamper {

    private final ItBudgetCanonicalJson canonical;

    /**
     * 부모 원장과 활성 단말 목록으로 개정본 스탬프를 계산한다.
     *
     * @param cost 대상 전산업무비 개정본
     * @param terminals 같은 개정본의 {@code DEL_YN='N'} 단말 목록. 입력 순서는 결과에 영향을 주지 않는다. null은 빈 목록으로 취급한다.
     * @return 64자리 소문자 SHA-256 다이제스트
     * @throws IllegalArgumentException 금액·환율 소수 자릿수가 계약을 벗어난 경우
     */
    public String stamp(Bcostm cost, List<Btermm> terminals) {
        return canonical.digest(new StampInput(parent(cost), children(terminals)));
    }

    private ParentView parent(Bcostm cost) {
        return new ParentView(
                cost.getCostBgNo(),
                cost.getBgSno(),
                cost.getIoeC(),
                cost.getCttNm(),
                cost.getCttOppNm(),
                canonical.money(cost.getCostTotXpAmt()),
                canonical.money(cost.getFcAmt()),
                cost.getDfrCleC(),
                cost.getFstDfrDt(),
                cost.getCurC(),
                canonical.exchangeRate(cost.getXcr()),
                cost.getXcrBseDt(),
                cost.getSectSysUtzYn(),
                cost.getIndRsn(),
                cost.getCgprId(),
                cost.getCgprNm(),
                cost.getPrlmHrkOgzCCone(),
                cost.getCostSvnDpmC(),
                cost.getSvnTemC(),
                cost.getSvnDpmNm(),
                cost.getSvnTemNm(),
                cost.getBseYy(),
                cost.getBgUntAbusC(),
                cost.getTmnYn(),
                cost.getAbusTc(),
                cost.getCncdRfrNo());
    }

    private List<TerminalView> children(List<Btermm> terminals) {
        return terminals == null
                ? List.of()
                : terminals.stream()
                        .map(this::child)
                        .sorted(
                                Comparator.comparing(
                                                TerminalView::tmnMngNo,
                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                        .thenComparing(
                                                TerminalView::sno,
                                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
    }

    private TerminalView child(Btermm terminal) {
        return new TerminalView(
                terminal.getTmnMngNo(),
                terminal.getSno(),
                terminal.getSpfTmnNm(),
                terminal.getTmnKdTc(),
                terminal.getNsfUsgCone(),
                terminal.getTmnClsfC(),
                canonical.money(terminal.getTermRqmBgAmt()),
                canonical.money(terminal.getFcAmt()),
                terminal.getCurC(),
                canonical.exchangeRate(terminal.getXcr()),
                terminal.getXcrBseDt(),
                terminal.getDfrCleC(),
                terminal.getIndRsn(),
                terminal.getCgprId(),
                terminal.getCgprNm(),
                terminal.getTermSvnDpmC(),
                terminal.getSvnDpmNm(),
                terminal.getTermSvnTemC(),
                terminal.getSvnTemNm(),
                terminal.getRmk());
    }

    private record StampInput(ParentView parent, List<TerminalView> terminals) {}

    private record ParentView(
            String costBgNo,
            Integer bgSno,
            String ioeC,
            String cttNm,
            String cttOppNm,
            BigDecimal costTotXpAmt,
            BigDecimal fcAmt,
            String dfrCleC,
            String fstDfrDt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            String sectSysUtzYn,
            String indRsn,
            String cgprId,
            String cgprNm,
            String prlmHrkOgzCCone,
            String costSvnDpmC,
            String svnTemC,
            String svnDpmNm,
            String svnTemNm,
            String bseYy,
            String bgUntAbusC,
            String tmnYn,
            String abusTc,
            String cncdRfrNo) {}

    private record TerminalView(
            String tmnMngNo,
            Integer sno,
            String spfTmnNm,
            String tmnKdTc,
            String nsfUsgCone,
            String tmnClsfC,
            BigDecimal termRqmBgAmt,
            BigDecimal fcAmt,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            String dfrCleC,
            String indRsn,
            String cgprId,
            String cgprNm,
            String termSvnDpmC,
            String svnDpmNm,
            String termSvnTemC,
            String svnTemNm,
            String rmk) {}
}
