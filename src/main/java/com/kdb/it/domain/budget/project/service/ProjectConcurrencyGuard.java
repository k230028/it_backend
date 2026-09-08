package com.kdb.it.domain.budget.project.service;

import com.kdb.it.common.system.exception.LockTimeouts;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.exception.ProjectConflictException;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 정보화사업 저장 경로의 동시성 방어를 담당합니다.
 *
 * <p>스탬프 대조와 잠금 대기 초과 변환을 한곳에 모아 {@link ProjectService}의 저장 흐름과 분리합니다. 사용자 화면이 없는 편성요청서 반입·이관 경로는 이
 * 가드를 거치지 않습니다. 전산업무비의 {@code CostConcurrencyGuard}와 같은 규약입니다.
 */
@Component
@RequiredArgsConstructor
public class ProjectConcurrencyGuard {

    /** 동시성 스탬프 형식: 소문자 SHA-256 64자리 */
    private static final Pattern STAMP_FORMAT = Pattern.compile("[a-f0-9]{64}");

    private final ProjectConcurrencyStamper concurrencyStamper;
    private final ProjectItemRepository itemRepository;
    private final ProjectQueryAssembler queryAssembler;

    /**
     * 잠근 개정본의 현재 스탬프와 요청 스탬프를 비교합니다.
     *
     * <p>호출 시점은 행 잠금 획득 이후이고 {@code target}·{@code request}를 처음 수정하기 직전이어야 합니다. 잠금 전에 검사하면 검사와 저장
     * 사이에 다른 트랜잭션이 끼어들 수 있고, 수정 이후에 검사하면 다시 계산한 스탬프가 절대 일치하지 않습니다.
     *
     * @param request 사용자 저장 요청
     * @param target 잠금이 걸린 대상 개정본
     * @param nameResolver 사번을 표시명으로 바꾸는 해석기. 충돌이 확정된 경우에만 호출합니다. 해석하지 못하면 사번을 그대로 씁니다.
     * @throws ProjectConflictException 스탬프가 없거나 형식이 어긋나면 {@code PROJECT_STAMP_REQUIRED}(400), 현재
     *     상태와 다르면 {@code PROJECT_SOURCE_CHANGED}(409)
     */
    public void verifyStamp(
            ProjectDto.UpdateRequest request, Bprojm target, UnaryOperator<String> nameResolver) {
        String submitted = request.getConcurrencyStamp();
        if (submitted == null || !STAMP_FORMAT.matcher(submitted).matches()) {
            throw new ProjectConflictException(
                    HttpStatus.BAD_REQUEST,
                    "PROJECT_STAMP_REQUIRED",
                    "동시성 스탬프가 필요합니다. 화면을 다시 조회한 뒤 저장하세요.",
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        // 상세 조회 조립과 같은 활성 품목 집합(DEL_YN='N', 개정본 순번 일치)을 읽는다.
        List<Bitemm> items =
                itemRepository.findAllByAbusMngNoAndFntTbCrySnoAndDelYn(
                        target.getAbusMngNo(), target.getSno(), "N");
        String current = concurrencyStamper.stamp(target, items);
        if (current.equals(submitted)) {
            return;
        }
        LastChange lastChange = lastChange(target, items);
        throw new ProjectConflictException(
                HttpStatus.CONFLICT,
                "PROJECT_SOURCE_CHANGED",
                "다른 사용자가 이 사업을 수정했습니다.",
                changedBy(lastChange.usid(), nameResolver),
                lastChange.usid(),
                lastChange.at(),
                current,
                queryAssembler.assembleDetail(target));
    }

    /** 충돌을 알릴 때 표시할 최종 변경 주체. */
    private record LastChange(String usid, LocalDateTime at) {}

    /**
     * 원장과 품목 가운데 가장 나중에 바뀐 쪽을 고릅니다.
     *
     * <p>스탬프는 부모와 품목을 함께 덮으므로, 품목만 수정된 충돌에서 부모의 감사 정보를 쓰면 바꾸지 않은 사람을 변경자로 지목하게 됩니다. 스탬프 계산에 이미 읽어 둔
     * 품목 목록을 그대로 사용하므로 추가 조회는 없습니다.
     *
     * @param target 잠금이 걸린 대상 개정본
     * @param items 같은 개정본의 {@code DEL_YN='N'} 품목 목록
     * @return 더 나중에 바뀐 쪽의 사번과 일시. 수정일시가 없는 품목은 비교에서 제외한다.
     */
    private static LastChange lastChange(Bprojm target, List<Bitemm> items) {
        LastChange latest = new LastChange(target.getLstChgUsid(), target.getLstChgDtm());
        for (Bitemm item : items) {
            LocalDateTime changedAt = item.getLstChgDtm();
            if (changedAt == null) {
                continue;
            }
            if (latest.at() == null || changedAt.isAfter(latest.at())) {
                latest = new LastChange(item.getLstChgUsid(), changedAt);
            }
        }
        return latest;
    }

    /**
     * 최종 변경자 표시값을 정합니다. 해석에 실패하면 사번을 그대로 노출해 "누가 바꿨는지"가 비지 않게 합니다.
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
     * <p>반입·이관 경로는 이 래퍼를 쓰지 않고 원래 예외를 그대로 전파합니다.
     *
     * @param update 실제 수정 로직
     * @return 수정된 관리번호
     * @throws ProjectConflictException 잠금 대기 시간을 넘긴 경우 {@code PROJECT_CONCURRENT_UPDATE}
     */
    public String runUserUpdate(Supplier<String> update) {
        try {
            return update.get();
        } catch (RuntimeException exception) {
            if (LockTimeouts.isLockTimeout(exception)) {
                throw new ProjectConflictException(
                        HttpStatus.CONFLICT,
                        "PROJECT_CONCURRENT_UPDATE",
                        "다른 작업이 이 사업을 수정 중입니다. 잠시 후 다시 시도하세요.",
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
