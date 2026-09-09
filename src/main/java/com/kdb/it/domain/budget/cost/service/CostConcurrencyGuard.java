package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.system.exception.LockTimeouts;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 전산업무비 저장 경로의 동시성 방어를 담당합니다.
 *
 * <p>스탬프 대조와 잠금 대기 초과 변환을 한곳에 모아 {@link CostService}의 저장 흐름과 분리합니다. 사용자 화면이 없는 이관·일괄업로드 경로는 이 가드를
 * 거치지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class CostConcurrencyGuard {

    /** 동시성 스탬프 형식: 소문자 SHA-256 64자리 */
    private static final Pattern STAMP_FORMAT = Pattern.compile("[a-f0-9]{64}");

    private final CostConcurrencyStamper concurrencyStamper;
    private final BtermmRepository btermmRepository;
    private final CostQueryService queryService;

    /**
     * 잠근 개정본의 현재 스탬프와 요청 스탬프를 비교합니다.
     *
     * <p>호출 시점은 행 잠금 획득 이후이고 {@code target}·{@code request}를 처음 수정하기 직전이어야 합니다. 잠금 전에 검사하면 검사와 저장
     * 사이에 다른 트랜잭션이 끼어들 수 있고, 수정 이후에 검사하면 다시 계산한 스탬프가 절대 일치하지 않습니다.
     *
     * @param request 사용자 저장 요청
     * @param target 잠금이 걸린 대상 개정본
     * @param nameResolver 사번을 표시명으로 바꾸는 해석기. 충돌이 확정된 경우에만 호출합니다. 해석하지 못하면 사번을 그대로 씁니다.
     * @throws CostConflictException 스탬프가 없거나 형식이 어긋나면 {@code COST_STAMP_REQUIRED}(400), 현재 상태와 다르면
     *     {@code COST_SOURCE_CHANGED}(409)
     */
    public void verifyStamp(
            CostDto.UpdateRequest request, Bcostm target, UnaryOperator<String> nameResolver) {
        verifyStamp(request.getConcurrencyStamp(), target, nameResolver);
    }

    /** 부모 전체가 아닌 좁은 수정 요청도 같은 개정본 스탬프 규칙으로 검증합니다. */
    public void verifyStamp(
            String submitted, Bcostm target, UnaryOperator<String> nameResolver) {
        if (submitted == null || !STAMP_FORMAT.matcher(submitted).matches()) {
            throw new CostConflictException(
                    HttpStatus.BAD_REQUEST,
                    "COST_STAMP_REQUIRED",
                    "동시성 스탬프가 필요합니다. 화면을 다시 조회한 뒤 저장하세요.",
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        List<Btermm> terminals =
                btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(
                        target.getCostBgNo(), target.getBgSno(), "N");
        String current = concurrencyStamper.stamp(target, terminals);
        if (current.equals(submitted)) {
            return;
        }
        LastChange lastChange = lastChange(target, terminals);
        throw new CostConflictException(
                HttpStatus.CONFLICT,
                "COST_SOURCE_CHANGED",
                "다른 사용자가 이 전산업무비를 수정했습니다.",
                changedBy(lastChange.usid(), nameResolver),
                lastChange.usid(),
                lastChange.at(),
                current,
                queryService.getCost(target.getCostBgNo(), target.getBgSno()));
    }

    /** 충돌을 알릴 때 표시할 최종 변경 주체. */
    private record LastChange(String usid, LocalDateTime at) {}

    /**
     * 원장과 단말 가운데 가장 나중에 바뀐 쪽을 고릅니다.
     *
     * <p>스탬프는 부모와 단말을 함께 덮으므로, 단말만 수정된 충돌에서 부모의 감사 정보를 쓰면 바꾸지 않은 사람을 변경자로 지목하게 됩니다. 스탬프 계산에 이미 읽어 둔
     * 단말 목록을 그대로 사용하므로 추가 조회는 없습니다.
     *
     * @param target 잠금이 걸린 대상 개정본
     * @param terminals 같은 개정본의 {@code DEL_YN='N'} 단말 목록
     * @return 더 나중에 바뀐 쪽의 사번과 일시. 수정일시가 없는 단말은 비교에서 제외한다.
     */
    private static LastChange lastChange(Bcostm target, List<Btermm> terminals) {
        LastChange latest = new LastChange(target.getLstChgUsid(), target.getLstChgDtm());
        for (Btermm terminal : terminals) {
            LocalDateTime changedAt = terminal.getLstChgDtm();
            if (changedAt == null) {
                continue;
            }
            if (latest.at() == null || changedAt.isAfter(latest.at())) {
                latest = new LastChange(terminal.getLstChgUsid(), changedAt);
            }
        }
        return latest;
    }

    /**
     * 최종 변경자 표시값을 정합니다.
     *
     * <p>{@code UserNameResolver}는 사번 형태의 값을 조회하지 못하면 이름으로 오인하지 않으려고 null을 돌려줍니다. 그대로 두면 퇴직자나 미등록
     * 사번이 변경한 건에서 안내 문구가 통째로 비어 "누가 바꿨는지" 자체를 알려주지 못하므로, 해석에 실패하면 사번을 그대로 노출합니다.
     *
     * @param lstChgUsid 최종 변경자 사번 ({@code LST_CHG_USID})
     * @param nameResolver 사번을 표시명으로 바꾸는 해석기
     * @return 해석된 표시명. 해석하지 못하면 사번 원값. 사번 자체가 없으면 null
     */
    private static String changedBy(String lstChgUsid, UnaryOperator<String> nameResolver) {
        String resolved = nameResolver.apply(lstChgUsid);
        return resolved == null || resolved.isBlank() ? lstChgUsid : resolved;
    }

    /**
     * 사용자 수정 경로의 잠금 대기 초과를 재시도 가능한 409로 바꿉니다.
     *
     * <p>이관 경로는 이 래퍼를 쓰지 않고 원래 예외를 그대로 전파합니다.
     *
     * @param update 실제 수정 로직
     * @return 수정된 관리번호
     * @throws CostConflictException 잠금 대기 시간을 넘긴 경우 {@code COST_CONCURRENT_UPDATE}
     */
    public String runUserUpdate(Supplier<String> update) {
        try {
            return update.get();
        } catch (RuntimeException exception) {
            if (LockTimeouts.isLockTimeout(exception)) {
                throw new CostConflictException(
                        HttpStatus.CONFLICT,
                        "COST_CONCURRENT_UPDATE",
                        "다른 작업이 이 전산업무비를 수정 중입니다. 잠시 후 다시 시도하세요.",
                        null,
                        null,
                        null,
                        null,
                        null);
            }
            throw exception;
        }
    }
}
