package com.kdb.it.domain.migration.service;

import com.kdb.it.domain.migration.service.MigrationYearSnapshot.RequestItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 비목그룹 목표 편성액을 요청 품목에 배분해 실효 편성률을 만듭니다 (설계 §3).
 *
 * <p>종합본은 사업 단위로 비목그룹 총액을 주고 {@code BBUGTM}은 품목 단위입니다. 이 간극을 실효 편성률 하나로 메우면 (a) 조정비율을 그대로 쓰는 경우,
 * (b) 예산담당자가 취합하며 금액을 조정한 경우, (c) 하반기 조정의 확정금액까지 같은 식으로 표현되고 {@code 요청금액 × 편성률 = 편성금액} 불변식이 세 경우 모두
 * 성립합니다.
 *
 * <p>그룹 합계는 목표액과 정확히 일치시키고 반올림 잔차는 요청금액이 가장 큰 품목이 흡수합니다. 그 품목 하나만 개별 곱과 0.001원 어긋나지만, 예산 집계는 그룹 합계로
 * 이뤄지므로 합계 정확성을 택합니다.
 */
@Component
public class MigrationAllocationPlanner {

    /** 편성금액 스케일. {@code BG_DUP_AMT}의 물리 스케일과 같습니다. */
    private static final int AMOUNT_SCALE = 3;

    /** 편성률 스케일. {@code ASG_RT}의 물리 스케일과 같습니다. */
    private static final int RATE_SCALE = 5;

    private static final BigDecimal PERCENT_BASE = BigDecimal.valueOf(100);

    /** 개발비 그룹 — 개발비(일반) + 감리/컨설팅. */
    public static final Set<String> GROUP_DEV = new LinkedHashSet<>(List.of("103", "104"));

    /** 기계장치 그룹 — 국내 + 국외. */
    public static final Set<String> GROUP_HW = new LinkedHashSet<>(List.of("101", "102"));

    /** 기타무형자산 그룹 — 국외 + 국내(일반) + SW라이선스. */
    public static final Set<String> GROUP_SW = new LinkedHashSet<>(List.of("105", "106", "107"));

    /**
     * 품목별 편성 배분 하나입니다.
     *
     * @param gclMngNo 품목관리번호 ({@code BBUGTM.PK_COL_NM})
     * @param sno 품목 일련번호 ({@code BBUGTM.FNT_TB_CRY_SNO})
     * @param ioeC 비목코드
     * @param amount 편성금액 ({@code BG_DUP_AMT}, 스케일 3)
     * @param rate 편성률 ({@code ASG_RT}, 스케일 5)
     */
    public record ItemAllocation(
            String gclMngNo, Integer sno, String ioeC, BigDecimal amount, BigDecimal rate) {}

    /** 배분 결과입니다. 실패를 예외가 아니라 값으로 돌려 검증기가 진단으로 바꿉니다. */
    public sealed interface Allocation {

        /**
         * 배분에 성공한 결과입니다.
         *
         * @param items 품목별 배분
         * @param effectiveRate 그룹 공통 실효 편성률
         */
        record Allocated(List<ItemAllocation> items, BigDecimal effectiveRate)
                implements Allocation {}

        /**
         * 요청 품목 합계가 0인데 목표 편성액이 0보다 커서 배분할 대상이 없는 결과입니다.
         *
         * @param targetAmount 배분하려던 목표 편성액
         */
        record BaseZero(BigDecimal targetAmount) implements Allocation {}
    }

    /**
     * 목표 편성액을 요청 품목에 비례 배분합니다.
     *
     * @param items 그 비목그룹의 요청 품목. 빈 목록 허용
     * @param targetAmount 목표 편성액. null이면 0원으로 봅니다
     * @return 배분 결과. 요청 합계가 0인데 목표액이 0보다 크면 {@link Allocation.BaseZero}
     */
    public Allocation allocate(List<RequestItem> items, BigDecimal targetAmount) {
        BigDecimal target = targetAmount == null ? BigDecimal.ZERO : targetAmount;
        BigDecimal base = BigDecimal.ZERO;
        for (RequestItem item : items) {
            base = base.add(item.amount() == null ? BigDecimal.ZERO : item.amount());
        }

        if (base.compareTo(BigDecimal.ZERO) == 0) {
            if (target.compareTo(BigDecimal.ZERO) != 0) {
                return new Allocation.BaseZero(target);
            }
            List<ItemAllocation> zeros = new ArrayList<>();
            for (RequestItem item : items) {
                zeros.add(
                        new ItemAllocation(
                                item.gclMngNo(),
                                item.sno(),
                                item.ioeC(),
                                BigDecimal.ZERO.setScale(AMOUNT_SCALE),
                                BigDecimal.ZERO.setScale(RATE_SCALE)));
            }
            return new Allocation.Allocated(zeros, BigDecimal.ZERO.setScale(RATE_SCALE));
        }

        BigDecimal rate =
                target.multiply(PERCENT_BASE).divide(base, RATE_SCALE, RoundingMode.HALF_UP);

        List<ItemAllocation> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (RequestItem item : items) {
            BigDecimal requested = item.amount() == null ? BigDecimal.ZERO : item.amount();
            BigDecimal amount =
                    requested
                            .multiply(rate)
                            .divide(PERCENT_BASE, AMOUNT_SCALE, RoundingMode.HALF_UP);
            allocations.add(
                    new ItemAllocation(item.gclMngNo(), item.sno(), item.ioeC(), amount, rate));
            allocated = allocated.add(amount);
        }

        BigDecimal residual = target.subtract(allocated);
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            int largest = indexOfLargest(items);
            ItemAllocation targetAllocation = allocations.get(largest);
            allocations.set(
                    largest,
                    new ItemAllocation(
                            targetAllocation.gclMngNo(),
                            targetAllocation.sno(),
                            targetAllocation.ioeC(),
                            targetAllocation
                                    .amount()
                                    .add(residual)
                                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                            targetAllocation.rate()));
        }
        return new Allocation.Allocated(allocations, rate);
    }

    /** 반올림 잔차를 흡수할 품목의 인덱스입니다. 요청금액이 가장 큰 품목을 고릅니다. */
    private int indexOfLargest(List<RequestItem> items) {
        int best = 0;
        BigDecimal bestAmount = null;
        for (int i = 0; i < items.size(); i++) {
            BigDecimal amount =
                    items.get(i).amount() == null ? BigDecimal.ZERO : items.get(i).amount();
            if (bestAmount == null || amount.compareTo(bestAmount) > 0) {
                bestAmount = amount;
                best = i;
            }
        }
        return best;
    }

    /**
     * 종합본 금액 컬럼에 대응하는 비목그룹을 반환합니다.
     *
     * @param amountColumn 정규 컬럼 id (`devAmount`·`hwAmount`·`swAmount`)
     * @return 그 그룹의 비목코드 집합. 대응하는 그룹이 없으면 빈 집합
     */
    public static Set<String> groupOf(String amountColumn) {
        return switch (amountColumn == null ? "" : amountColumn) {
            case "devAmount" -> GROUP_DEV;
            case "hwAmount" -> GROUP_HW;
            case "swAmount" -> GROUP_SW;
            default -> Set.of();
        };
    }

    /**
     * 그 그룹에 드는 품목만 골라냅니다.
     *
     * @param all 사업의 활성 요청 품목 전체
     * @param group 비목그룹
     * @return 그룹에 드는 품목. 순서는 입력 순서를 유지합니다
     */
    public static List<RequestItem> itemsInGroup(List<RequestItem> all, Set<String> group) {
        List<RequestItem> out = new ArrayList<>();
        for (RequestItem item : all) {
            if (item.ioeC() != null && group.contains(item.ioeC().trim())) {
                out.add(item);
            }
        }
        return out;
    }

    /**
     * 자본예산 세 그룹 어디에도 들지 않는 품목을 골라냅니다.
     *
     * <p>편성요청서 반입이 1-2 시트의 일반관리비 품목도 {@code BITEMM}으로 보내므로, 정보화사업 하나에 자본 계열이 아닌 품목이 섞여 있습니다. 이들은
     * 종합본의 `일반관리비` 열을 목표액으로 하는 네 번째 그룹이 됩니다.
     *
     * @param all 사업의 활성 요청 품목 전체
     * @return 자본 계열이 아닌 품목
     */
    public static List<RequestItem> itemsOutsideCapitalGroups(List<RequestItem> all) {
        List<RequestItem> out = new ArrayList<>();
        for (RequestItem item : all) {
            String ioeC = item.ioeC() == null ? "" : item.ioeC().trim();
            if (!GROUP_DEV.contains(ioeC) && !GROUP_HW.contains(ioeC) && !GROUP_SW.contains(ioeC)) {
                out.add(item);
            }
        }
        return out;
    }
}
