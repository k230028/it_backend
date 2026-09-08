package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 정보화사업 개정본의 업무 내용을 SHA-256 스탬프로 요약한다.
 *
 * <p>조회 응답과 저장 검증이 같은 입력 집합을 쓰도록 이 컴포넌트 하나만 사용한다. 공통 감사 필드(LST_CHG_DTM 등)와 결재 상태 필드(LST_YN)는 입력에서
 * 제외하므로, 결재 진행이나 의미상 동일한 원복은 충돌로 판정되지 않는다. 전산업무비의 {@code CostConcurrencyStamper}와 같은 규약이다.
 */
@Component
@RequiredArgsConstructor
public class ProjectConcurrencyStamper {

    private final ItBudgetCanonicalJson canonical;

    /**
     * 사업 원장과 활성 품목 목록으로 개정본 스탬프를 계산한다.
     *
     * @param project 대상 사업 개정본
     * @param items 같은 개정본의 {@code DEL_YN='N'} 품목 목록. 입력 순서는 결과에 영향을 주지 않는다. null은 빈 목록으로 취급한다.
     * @return 64자리 소문자 SHA-256 다이제스트
     * @throws IllegalArgumentException 금액·환율 소수 자릿수가 계약을 벗어난 경우
     */
    public String stamp(Bprojm project, List<Bitemm> items) {
        return canonical.digest(new StampInput(parent(project), children(items)));
    }

    private ParentView parent(Bprojm project) {
        return new ParentView(
                project.getAbusMngNo(),
                project.getSno(),
                project.getAbusNm(),
                project.getBzTpC(),
                project.getSvnDpmC(),
                project.getSvnTemC(),
                project.getSvnDpmNm(),
                project.getSvnTemNm(),
                project.getDvmDpmC(),
                project.getDvmTemC(),
                project.getSttDtm(),
                project.getEndDtm(),
                canonical.money(project.getTotRqmAmt()),
                canonical.money(project.getMplAmt()),
                canonical.money(project.getDfrAmt()),
                project.getUsid(),
                project.getDvmUsid(),
                project.getTlrUsid(),
                project.getTlrNm(),
                project.getUsrNm(),
                project.getDvmTlrUsid(),
                project.getEdrtTc(),
                project.getAbusPulConeInf(),
                project.getCpnSafCone(),
                project.getAbusPulNcsInf(),
                project.getAbusXptEffInf(),
                project.getPlmDes(),
                project.getAbusPulDrcnInf(),
                project.getMnPrgCone(),
                project.getHrfPlnCone(),
                project.getBzDttNm(),
                project.getSklTpTc(),
                project.getCstTpTc(),
                project.getDplYn(),
                project.getFlfFsgDt(),
                project.getRprStsTc(),
                project.getExePttYn(),
                project.getBseYy(),
                project.getPrlmHrkOgzCCone(),
                project.getOdnYn(),
                project.getAbusTc(),
                project.getCncdRfrNo());
    }

    private List<ItemView> children(List<Bitemm> items) {
        return items == null
                ? List.of()
                : items.stream()
                        .map(this::child)
                        .sorted(
                                Comparator.comparing(
                                                ItemView::gclMngNo,
                                                Comparator.nullsFirst(Comparator.naturalOrder()))
                                        .thenComparing(
                                                ItemView::sno,
                                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
    }

    private ItemView child(Bitemm item) {
        return new ItemView(
                item.getGclMngNo(),
                item.getSno(),
                item.getIoeC(),
                item.getGclNm(),
                plainQuantity(item.getQty()),
                item.getCurC(),
                canonical.exchangeRate(item.getXcr()),
                item.getXcrBseDt(),
                item.getCncdFdtnCone(),
                item.getBseYm(),
                item.getDfrCleC(),
                item.getSectSysUtzYn(),
                item.getItrInfrYn(),
                canonical.money(item.getAmt()),
                canonical.money(item.getFcAmt()),
                canonical.money(item.getMplAmt()));
    }

    /**
     * 수량의 후행 0을 제거해 스케일 차이를 없앤다.
     *
     * <p>{@code QTY}는 소수 자릿수 계약이 없는 {@code NUMBER(10)}이라 {@link ItBudgetCanonicalJson#quantity}의 정수
     * 강제를 쓰면 과거 소수 수량 행의 상세 조회 자체가 실패한다. 조회와 검증 모두 DB 값을 그대로 읽으므로 후행 0만 맞추면 충분하다.
     */
    private static BigDecimal plainQuantity(BigDecimal qty) {
        return qty == null ? null : qty.stripTrailingZeros();
    }

    private record StampInput(ParentView parent, List<ItemView> items) {}

    private record ParentView(
            String abusMngNo,
            Integer sno,
            String abusNm,
            String bzTpC,
            String svnDpmC,
            String svnTemC,
            String svnDpmNm,
            String svnTemNm,
            String dvmDpmC,
            String dvmTemC,
            LocalDate sttDtm,
            LocalDate endDtm,
            BigDecimal totRqmAmt,
            BigDecimal mplAmt,
            BigDecimal dfrAmt,
            String usid,
            String dvmUsid,
            String tlrUsid,
            String tlrNm,
            String usrNm,
            String dvmTlrUsid,
            String edrtTc,
            String abusPulConeInf,
            String cpnSafCone,
            String abusPulNcsInf,
            String abusXptEffInf,
            String plmDes,
            String abusPulDrcnInf,
            String mnPrgCone,
            String hrfPlnCone,
            String bzDttNm,
            String sklTpTc,
            String cstTpTc,
            String dplYn,
            String flfFsgDt,
            String rprStsTc,
            String exePttYn,
            String bseYy,
            String prlmHrkOgzCCone,
            String odnYn,
            String abusTc,
            String cncdRfrNo) {}

    private record ItemView(
            String gclMngNo,
            Integer sno,
            String ioeC,
            String gclNm,
            BigDecimal qty,
            String curC,
            BigDecimal xcr,
            String xcrBseDt,
            String cncdFdtnCone,
            String bseYm,
            String dfrCleC,
            String sectSysUtzYn,
            String itrInfrYn,
            BigDecimal amt,
            BigDecimal fcAmt,
            BigDecimal mplAmt) {}
}
