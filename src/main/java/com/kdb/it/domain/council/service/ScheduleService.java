package com.kdb.it.domain.council.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bschdm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 협의회 일정 서비스 (Step 2 — 일정 취합/확정)
 *
 * <p>평가위원이 가능한 일정을 입력하면 IT관리자가 최종 일정을 확정합니다.</p>
 *
 * <p>일정 확정 흐름:</p>
 * <pre>
 *   PREPARING
 *     │  평가위원들이 각자 일정 입력 (POST /schedule)
 *     │  전원 입력 완료 → IT관리자가 일정 확정 (PUT /schedule/confirm)
 *     ↓
 *   SCHEDULED
 *     (BASCTM.CNRC_DT / CNRC_TM / CNRC_PLC 반영)
 * </pre>
 *
 * <p>일정 허용 시간대: 10:00 / 14:00 / 15:00 / 16:00</p>
 *
 * <p>Design Ref: §2.1 ScheduleService — Step 2 담당</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    /** 일정 리포지토리 (TAAABB_BSCHDM) */
    private final ScheduleRepository scheduleRepository;

    /** 평가위원 리포지토리 — 위원 목록 조회용 */
    private final CommitteeRepository committeeRepository;

    /** 사용자 리포지토리 — 위원 이름 조회용 */
    private final UserRepository userRepository;

    /** 협의회 기본 서비스 — 상태 전이용 */
    private final CouncilService councilService;

    // 허용 시간대
    private static final List<String> ALLOWED_TIMES = List.of("10:00", "14:00", "15:00", "16:00");

    // =========================================================================
    // 조회
    // =========================================================================

    /**
     * 일정 입력 현황 조회 (IT관리자용)
     *
     * <p>전체 위원 목록과 각 위원의 일정 응답 현황을 반환합니다.
     * 미응답 위원이 없을 때 일정확정 버튼이 활성화됩니다.</p>
     *
     * @param asctId 협의회ID
     * @return 일정 현황 (전체/응답/미응답 위원 수 + 위원별 상세)
     */
    public CouncilDto.ScheduleStatusResponse getScheduleStatus(String asctId) {
        Basctm council = councilService.findActiveCouncil(asctId);

        // 전체 위원 목록
        List<Bcmmtm> members = committeeRepository.findByAsctIdAndDelYn(asctId, "N");

        // 전체 일정 응답 목록
        List<Bschdm> allSchedules = scheduleRepository.findByAsctIdAndDelYn(asctId, "N");

        // 응답한 위원 사번 Set
        Set<String> respondedEnos = allSchedules.stream()
                .map(Bschdm::getEno)
                .collect(Collectors.toSet());

        // 위원별 사용자 정보 Map
        Map<String, CuserI> userMap = buildUserMap(members);

        // 위원별 일정 응답 목록 Map (eno → slots)
        Map<String, List<Bschdm>> scheduleByEno = allSchedules.stream()
                .collect(Collectors.groupingBy(Bschdm::getEno));

        // 위원별 현황 생성
        List<CouncilDto.MemberScheduleStatus> memberStatuses = members.stream()
                .map(m -> {
                    boolean responded = respondedEnos.contains(m.getEno());
                    CuserI user = userMap.get(m.getEno());

                    List<CouncilDto.ScheduleSlotResponse> slots =
                            scheduleByEno.getOrDefault(m.getEno(), List.of()).stream()
                                    .map(s -> new CouncilDto.ScheduleSlotResponse(
                                            s.getDsdDt(), s.getDsdTm(), s.getPsbYn()))
                                    .collect(Collectors.toList());

                    return new CouncilDto.MemberScheduleStatus(
                            m.getEno(),
                            user != null ? user.getUsrNm() : null,
                            user != null ? user.getBbrNm() : null,
                            user != null ? user.getPtCNm() : null,
                            m.getVlrTp(),
                            responded,
                            slots
                    );
                })
                .collect(Collectors.toList());

        // 미응답 위원 수
        long pendingCount = scheduleRepository.countPendingMembers(asctId);
        int respondedCount = (int) respondedEnos.size();

        // 일정 확정 가능 여부 계산
        // INFO_SYS: 필수 위원(예산팀장:12004, IT기획팀장:18001) 응답 완료 시 true
        // 기타 타입: 전원 응답 완료 시 true
        boolean allRequiredResponded = calcAllRequiredResponded(
                council.getDbrTp(), members, userMap, respondedEnos);

        return new CouncilDto.ScheduleStatusResponse(
                members.size(),
                respondedCount,
                pendingCount,
                memberStatuses,
                allRequiredResponded
        );
    }

    /**
     * 내 일정 응답 조회 (평가위원 본인)
     *
     * <p>로그인한 평가위원이 이미 제출한 일정 슬롯 목록을 반환합니다.
     * 제출 이력이 없으면 빈 목록을 반환합니다.</p>
     *
     * @param asctId 협의회ID
     * @param eno    로그인한 사번
     * @return 본인이 제출한 슬롯 목록 (dsdDt, dsdTm, psbYn)
     */
    public List<CouncilDto.ScheduleSlotResponse> getMySchedule(String asctId, String eno) {
        councilService.findActiveCouncil(asctId);
        return scheduleRepository.findByAsctIdAndEnoAndDelYn(asctId, eno, "N").stream()
                .map(s -> new CouncilDto.ScheduleSlotResponse(s.getDsdDt(), s.getDsdTm(), s.getPsbYn()))
                .collect(Collectors.toList());
    }

    /**
     * 일정 확정 가능 여부 계산
     *
     * <p>INFO_SYS 타입: 예산팀장(TEM_C=12004), IT기획팀장(TEM_C=18001)이 모두 응답했는지 확인
     * <br>기타 타입: 전원이 응답했는지 확인</p>
     *
     * @param dbrTp         심의유형
     * @param members       전체 위원 목록
     * @param userMap       위원 사번 → 사용자 정보 Map
     * @param respondedEnos 응답 완료한 위원 사번 Set
     * @return 일정 확정 가능 여부
     */
    private boolean calcAllRequiredResponded(
            String dbrTp,
            List<Bcmmtm> members,
            Map<String, CuserI> userMap,
            Set<String> respondedEnos) {

        if (!"003".equals(dbrTp)) {  // INFO_SYS
            // INFO_SYS 외 타입: 전원 응답 기준
            return !members.isEmpty() && respondedEnos.size() >= members.size();
        }

        // INFO_SYS: 필수 팀코드 팀장의 응답 여부만 확인
        for (String requiredTemC : CommitteeService.INFO_SYS_REQUIRED_TEM_CODES) {
            // 해당 팀코드(TEM_C)에 속한 위원 중 한 명이라도 응답했는지 확인
            boolean hasResponse = members.stream()
                    .filter(m -> {
                        CuserI user = userMap.get(m.getEno());
                        return user != null && requiredTemC.equals(user.getTemC());
                    })
                    .anyMatch(m -> respondedEnos.contains(m.getEno()));

            if (!hasResponse) return false;
        }
        return true;
    }

    // =========================================================================
    // 저장
    // =========================================================================

    /**
     * 일정 입력 (평가위원)
     *
     * <p>평가위원이 날짜×시간대별 가능 여부를 입력합니다.
     * 기존 응답이 있으면 update(respond), 없으면 신규 INSERT합니다.</p>
     *
     * <p>Plan SC: 허용 시간대(10:00/14:00/15:00/16:00)만 저장</p>
     *
     * @param asctId      협의회ID
     * @param request     일정 응답 요청 (날짜×시간대 목록)
     * @param userDetails 로그인한 평가위원
     * @throws IllegalArgumentException 허용되지 않은 시간대 포함 시
     */
    @Transactional
    public void submitSchedule(String asctId, CouncilDto.ScheduleRequest request,
                               CustomUserDetails userDetails) {
        councilService.findActiveCouncil(asctId);

        String eno = userDetails.getEno();

        for (CouncilDto.ScheduleItem item : request.availableSlots()) {
            // 허용 시간대 검증
            if (!ALLOWED_TIMES.contains(item.dsdTm())) {
                throw new IllegalArgumentException(
                    "허용되지 않은 시간대입니다: " + item.dsdTm() + ". 허용값: " + ALLOWED_TIMES);
            }

            // upsert: 기존 데이터 있으면 update, 없으면 신규 INSERT
            scheduleRepository
                    .findByAsctIdAndEnoAndDsdDtAndDsdTmAndDelYn(
                            asctId, eno, item.dsdDt(), item.dsdTm(), "N")
                    .ifPresentOrElse(
                        // 기존 응답 update
                        existing -> existing.respond(item.psbYn()),
                        // 신규 INSERT
                        () -> {
                            Bschdm schedule = Bschdm.builder()
                                    .asctId(asctId)
                                    .eno(eno)
                                    .dsdDt(item.dsdDt())
                                    .dsdTm(item.dsdTm())
                                    .psbYn(item.psbYn())
                                    .build();
                            scheduleRepository.save(schedule);
                        }
                    );
        }
    }

    /**
     * 일정 확정 (IT관리자)
     *
     * <p>최종 회의 일정을 BASCTM에 반영하고 협의회 상태를 SCHEDULED로 전이합니다.</p>
     *
     * <p>Plan SC: 확정 후 SCHEDULED 상태 전이, BASCTM.CNRC_DT/TM/PLC 업데이트</p>
     *
     * @param asctId  협의회ID
     * @param request 일정 확정 요청 (회의일자, 회의시간, 회의장소)
     * @throws IllegalArgumentException 허용되지 않은 시간대 입력 시
     */
    @Transactional
    public void confirmSchedule(String asctId, CouncilDto.ScheduleConfirmRequest request) {
        // 협의회 존재 및 회의시간 검증
        if (!ALLOWED_TIMES.contains(request.cnrcTm())) {
            throw new IllegalArgumentException(
                "허용되지 않은 회의시간입니다: " + request.cnrcTm() + ". 허용값: " + ALLOWED_TIMES);
        }

        // BASCTM.CNRC_DT / CNRC_TM / CNRC_PLC 업데이트
        councilService.findActiveCouncil(asctId)
                .confirmSchedule(request.cnrcDt(), request.cnrcTm(), request.cnrcPlc());

        // 협의회 상태 전이: PREPARING → SCHEDULED
        councilService.changeStatus(asctId, "006");
    }

    // =========================================================================
    // 내부 헬퍼
    // =========================================================================

    /**
     * 위원 목록의 사번으로 사용자 정보 Map 생성.
     *
     * TODO: 현재는 사번별 findByEno()를 반복하므로 사용자 일괄 조회로 N+1을 제거해야 합니다.
     */
    private Map<String, CuserI> buildUserMap(List<Bcmmtm> members) {
        return members.stream()
                .map(m -> userRepository.findByEno(m.getEno()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(CuserI::getEno, u -> u, (a, b) -> a));
    }
}
