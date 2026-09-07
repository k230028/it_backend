package com.kdb.it.domain.budget.cost.service;

import com.kdb.it.common.system.exception.LockTimeouts;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
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
     * @param nameResolver 사번을 표시명으로 바꾸는 해석기. 충돌이 확정된 경우에만 호출합니다.
     * @throws CostConflictException 스탬프가 없거나 형식이 어긋나면 {@code COST_STAMP_REQUIRED}(400), 현재 상태와 다르면
     *     {@code COST_SOURCE_CHANGED}(409)
     */
    public void verifyStamp(
            CostDto.UpdateRequest request, Bcostm target, UnaryOperator<String> nameResolver) {
        String submitted = request.getConcurrencyStamp();
        if (submitted == null || !STAMP_FORMAT.matcher(submitted).matches()) {
            throw new CostConflictException(
                    HttpStatus.BAD_REQUEST,
                    "COST_STAMP_REQUIRED",
                    "동시성 스탬프가 필요합니다. 화면을 다시 조회한 뒤 저장하세요.",
                    null,
                    null,
                    null,
                    null);
        }
        String current =
                concurrencyStamper.stamp(
                        target,
                        btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(
                                target.getCostBgNo(), target.getBgSno(), "N"));
        if (current.equals(submitted)) {
            return;
        }
        throw new CostConflictException(
                HttpStatus.CONFLICT,
                "COST_SOURCE_CHANGED",
                "다른 사용자가 이 전산업무비를 수정했습니다.",
                nameResolver.apply(target.getLstChgUsid()),
                target.getLstChgDtm(),
                current,
                queryService.getCost(target.getCostBgNo(), target.getBgSno()));
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
                        null);
            }
            throw exception;
        }
    }
}
