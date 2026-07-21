package com.kdb.it.domain.council.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.entity.Bschdm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.ScheduleRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * 협의회 일정 서비스 (Step 2 — 일정 취합/확정)
 *
 * <p>
 * 평가위원이 가능한 일정을 입력하면 IT관리자가 최종 일정을 확정합니다.
 * </p>
 *
 * <p>
 * 일정 확정 흐름:
 * </p>
 * 
 * <pre>
 *   PREPARING
 *     │  평가위원들이 각자 일정 입력 (POST /schedule)
 *     │  전원 입력 완료 → IT관리자가 일정 확정 (PUT /schedule/confirm)
 *     ↓
 *   SCHEDULED
 *     (BASCTM.CNRC_DT / CNRC_TM / CNRC_PLC 반영)
 * </pre>
 *
 * <p>
 * 일정 허용 시간대: 10:00 / 14:00 / 15:00 / 16:00
 * </p>
 *
 * <p>
 * 설계 참조: §2.1 ScheduleService — 2단계 담당
 * </p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

        /** 일정 리포지토리 (TPRMPP_BSCHDM) */
        private final ScheduleRepository scheduleRepository;

        /**
         * JPA EntityManager — 신규 일정 INSERT 전용 persist() 호출용.
         *
         * <p>
         * JpaRepository.save()는 ID 채워진 detached entity에 대해 merge()를 호출해
         * BaseEntity 필드(특히 delYn)를 null로 덮어쓰는 회귀가 있어 직접 persist를 사용합니다(PRD §15/§17).
         * </p>
         */
        @PersistenceContext
        private EntityManager entityManager;

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
         * <p>
         * 전체 위원 목록과 각 위원의 일정 응답 현황을 반환합니다.
         * 미응답 위원이 없을 때 일정확정 버튼이 활성화됩니다.
         * </p>
         *
         * @param asctId 협의회ID
         * @return 일정 현황 (전체/응답/미응답 위원 수 + 위원별 상세)
         */
        public CouncilDto.ScheduleStatusResponse getScheduleStatus(String asctId) {
                councilService.findActiveCouncil(asctId);

                // 일정 취합 대상 위원 목록 — 간사(03)는 회의 진행 담당이라 일정/대면희망 응답 대상에서 제외
                List<Bcmmtm> members = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                                .filter(m -> !"03".equals(m.getItPtlAsctMebTc()))
                                .collect(Collectors.toList());

                // 전체 일정 응답 목록
                List<Bschdm> allSchedules = scheduleRepository.findByItPtlAsctIdAndDelYn(asctId, "N");

                // 응답한 위원 사번 Set
                Set<String> respondedEnos = allSchedules.stream()
                                .map(value -> value.getEno())
                                .collect(Collectors.toSet());

                // 위원별 응답용 사용자 정보 Map
                Map<String, UserRepository.CouncilMemberUserRow> userMap = buildUserMap(members);

                // 위원별 일정 응답 목록 Map (eno → slots)
                Map<String, List<Bschdm>> scheduleByEno = allSchedules.stream()
                                .collect(Collectors.groupingBy(value -> value.getEno()));

                // 위원별 현황 생성
                List<CouncilDto.MemberScheduleStatus> memberStatuses = members.stream()
                                .map(m -> {
                                        boolean responded = respondedEnos.contains(m.getEno());
                                        UserRepository.CouncilMemberUserRow user = userMap.get(m.getEno());

                                        List<CouncilDto.ScheduleSlotResponse> slots = scheduleByEno
                                                        .getOrDefault(m.getEno(), List.of()).stream()
                                                        .map(s -> new CouncilDto.ScheduleSlotResponse(
                                                                        s.getCnrcDt(), s.getCnrcSttTm(),
                                                                        s.getUsePsbYn()))
                                                        .toList();

                                        return new CouncilDto.MemberScheduleStatus(
                                                        m.getEno(),
                                                        user != null ? user.getUsrNm() : null,
                                                        user != null ? user.getBbrNm() : null,
                                                        user != null ? user.getPtCNm() : null,
                                                        m.getItPtlAsctMebTc(),
                                                        responded,
                                                        m.getCsfHpYn(),
                                                        slots);
                                })
                                .toList();

                // 대면희망 위원 존재 여부 (한 명이라도 Y면 대면개최) (PRD_c_20260620 #1)
                boolean anyFaceToFaceHope = members.stream()
                                .anyMatch(m -> "Y".equals(m.getCsfHpYn()));

                // 응답/미응답 위원 수 — 간사 제외된 취합 대상(members) 기준으로 계산
                int respondedCount = (int) members.stream()
                                .filter(m -> respondedEnos.contains(m.getEno()))
                                .count();
                long pendingCount = (long) members.size() - respondedCount;

                // 일정 확정 가능 여부 계산: 심의유형과 무관하게 위원 전원 응답 시 true (PRD_c_20260620 #1)
                boolean allRequiredResponded = calcAllRequiredResponded(members, respondedEnos);

                return new CouncilDto.ScheduleStatusResponse(
                                members.size(),
                                respondedCount,
                                pendingCount,
                                memberStatuses,
                                allRequiredResponded,
                                anyFaceToFaceHope);
        }

        /**
         * 내 일정 응답 조회 (평가위원 본인)
         *
         * <p>
         * 로그인한 평가위원이 이미 제출한 일정 슬롯 목록을 반환합니다.
         * 제출 이력이 없으면 빈 목록을 반환합니다.
         * </p>
         *
         * @param asctId 협의회ID
         * @param eno    로그인한 사번
         * @return 본인이 제출한 슬롯 목록 (dsdDt, dsdTm, psbYn)
         */
        public List<CouncilDto.ScheduleSlotResponse> getMySchedule(String asctId, String eno) {
                councilService.findActiveCouncil(asctId);
                return scheduleRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N").stream()
                                .map(s -> new CouncilDto.ScheduleSlotResponse(s.getCnrcDt(), s.getCnrcSttTm(),
                                                s.getUsePsbYn()))
                                .toList();
        }

        /**
         * 일정 확정 가능 여부 계산 (PRD_c_20260620 #1)
         *
         * <p>
         * 심의유형과 무관하게 평가위원 '전원'이 가능 일정을 제출해야 일정 확정이 가능합니다.
         * (이전에는 INFO_SYS의 경우 필수 팀장(예산팀장 12004 · IT기획팀장 18001)만 응답하면
         * 확정 가능했으나, 전원 응답 기준으로 통일했습니다.)
         * </p>
         *
         * @param members       전체 위원 목록
         * @param respondedEnos 응답 완료한 위원 사번 Set
         * @return 위원 전원이 응답했으면 true
         */
        private boolean calcAllRequiredResponded(
                        List<Bcmmtm> members,
                        Set<String> respondedEnos) {
                return !members.isEmpty()
                                && members.stream().allMatch(m -> respondedEnos.contains(m.getEno()));
        }

        // =========================================================================
        // 저장
        // =========================================================================

        /**
         * 일정 입력 (평가위원)
         *
         * <p>
         * 평가위원이 날짜×시간대별 가능 여부를 입력합니다.
         * 기존 응답이 있으면 update(respond), 없으면 신규 INSERT합니다.
         * </p>
         *
         * <p>
         * Plan SC: 허용 시간대(10:00/14:00/15:00/16:00)만 저장
         * </p>
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

                // 가능 일정은 해당 협의회 평가위원 본인만 입력 가능 (비위원 데이터 주입 차단, 리뷰 1-4)
                if (committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N").isEmpty()) {
                        throw new AccessDeniedException("해당 협의회의 평가위원만 일정을 입력할 수 있습니다.");
                }

                for (CouncilDto.ScheduleItem item : request.availableSlots()) {
                        // 허용 시간대 검증
                        if (!ALLOWED_TIMES.contains(item.dsdTm())) {
                                throw new IllegalArgumentException(
                                                "허용되지 않은 시간대입니다: " + item.dsdTm() + ". 허용값: " + ALLOWED_TIMES);
                        }

                        /*
                         * PRD §17 — DT 도메인 정규화
                         * - 백엔드 DSD_DT 표준: VARCHAR2(8), yyyyMMdd
                         * - 프론트가 yyyy-MM-dd(10자)로 전송하면 BSCHDL(로그 테이블) INSERT에서 ORA-12899 발생
                         * - 하이픈 제거로 8자 정규화
                         */
                        String dsdDtNorm = normalizeYyyymmdd(item.dsdDt());

                        // upsert: 기존 데이터 있으면 update, 없으면 신규 INSERT
                        final String dsdDtFinal = dsdDtNorm;
                        scheduleRepository
                                        .findByItPtlAsctIdAndEnoAndCnrcDtAndCnrcSttTmAndDelYn(
                                                        asctId, eno, dsdDtFinal, item.dsdTm(), "N")
                                        .ifPresentOrElse(
                                                        // 기존 응답 update
                                                        existing -> existing.respond(item.psbYn()),
                                                        // 신규 INSERT — persist()로 직접 @PrePersist 발화 (PRD §15 회귀 방지)
                                                        () -> {
                                                                Bschdm schedule = Bschdm.builder()
                                                                                .itPtlAsctId(asctId)
                                                                                .eno(eno)
                                                                                .cnrcDt(dsdDtFinal)
                                                                                .cnrcSttTm(item.dsdTm())
                                                                                .usePsbYn(item.psbYn())
                                                                                .build();
                                                                entityManager.persist(schedule);
                                                        });
                }

                /*
                 * 대면희망여부 저장 (PRD_c_20260620 #1)
                 * - 위원 본인의 BCMMTM.CSF_HP_YN에 응답을 반영한다.
                 * - 값이 없으면(null) 변경하지 않는다(기존 응답 유지).
                 */
                if (request.csfHopeYn() != null) {
                        committeeRepository.findByItPtlAsctIdAndEnoAndDelYn(asctId, eno, "N")
                                        .ifPresent(member -> member.respondFaceToFace(request.csfHopeYn()));
                }
        }

        /**
         * yyyy-MM-dd / yyyy/MM/dd 등 구분자 포함 날짜 문자열 → yyyyMMdd 8자 정규화 (PRD §17)
         *
         * <p>
         * 이미 8자 yyyyMMdd 형식이거나 null이면 그대로 반환합니다.
         * </p>
         */
        private String normalizeYyyymmdd(String dt) {
                if (dt == null)
                        return null;
                String digits = dt.replaceAll("[^0-9]", "");
                return digits.length() >= 8 ? digits.substring(0, 8) : digits;
        }

        /**
         * 일정 확정 (IT관리자)
         *
         * <p>
         * 최종 회의 일정을 BASCTM에 반영하고 협의회 상태를 SCHEDULED로 전이합니다.
         * </p>
         *
         * <p>
         * Plan SC: 확정 후 SCHEDULED 상태 전이, BASCTM.CNRC_DT/TM/PLC 업데이트
         * </p>
         *
         * @param asctId  협의회ID
         * @param request 일정 확정 요청 (회의일자, 회의시간, 회의장소)
         * @throws IllegalArgumentException 허용되지 않은 시간대 입력 시
         */
        @Transactional
        public void confirmSchedule(String asctId, CouncilDto.ScheduleConfirmRequest request) {
                // 회의시간 검증
                if (!ALLOWED_TIMES.contains(request.cnrcTm())) {
                        throw new IllegalArgumentException(
                                        "허용되지 않은 회의시간입니다: " + request.cnrcTm() + ". 허용값: " + ALLOWED_TIMES);
                }

                Basctm council = councilService.findActiveCouncil(asctId);

                // 개최준비(PREPARING=05) 상태에서만 일정 확정 가능 (비정상 상태 전이 차단, 리뷰 1-3)
                if (!"05".equals(council.getItPtlAsctPrgStsTc())) {
                        throw new IllegalStateException(
                                        "일정 확정은 개최준비(05) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
                }

                // BASCTM.CNRC_DT / CNRC_TM / CNRC_PLC 업데이트
                council.confirmSchedule(request.cnrcDt(), request.cnrcTm(), request.cnrcPlc());

                // 협의회 상태 전이: PREPARING(05) → SCHEDULED(06)
                councilService.changeStatus(asctId, "06");
        }

        /**
         * 서면개최 확정 (IT관리자) (PRD_c_20260620 #1)
         *
         * <p>
         * 위원 전원이 대면을 희망하지 않을 때, IT관리자가 서면개최로 확정합니다.
         * 회의일자/시간/장소 없이 BASCTM.CSF_HELD_YN='N'으로 설정하고,
         * 일정확정(SCHEDULED)·개최(개최시작) 단계를 건너뛰어 상태를 바로 진행중(07)으로 전이합니다.
         * 이후 서면질의응답 → 평가의견 작성 흐름은 대면과 동일합니다.
         * </p>
         *
         * @param asctId 협의회ID
         * @throws IllegalStateException 현재 상태가 개최준비(05)가 아닌 경우
         */
        @Transactional
        public void confirmWrittenMeeting(String asctId) {
                Basctm council = councilService.findActiveCouncil(asctId);

                // 개최준비(PREPARING=05) 상태에서만 서면개최 확정 가능
                if (!"05".equals(council.getItPtlAsctPrgStsTc())) {
                        throw new IllegalStateException(
                                        "서면개최 확정은 개최준비(05) 상태에서만 가능합니다. 현재 상태: " + council.getItPtlAsctPrgStsTc());
                }

                /*
                 * 서면개최는 평가위원 '전원'이 대면을 희망하지 않을 때만 가능 (PRD_c_20260620 #1)
                 * - 전원 응답 + 전원 N: 위원의 CSF_HP_YN이 모두 'N'이어야 한다.
                 * - 미응답(null) 또는 한 명이라도 'Y'이면 서면 확정 불가(대면으로 진행).
                 */
                // 간사(03) 제외 — 일정/대면희망 응답 대상이 아니므로 전원-서면 판정에서도 제외
                List<Bcmmtm> members = committeeRepository.findByItPtlAsctIdAndDelYn(asctId, "N").stream()
                                .filter(m -> !"03".equals(m.getItPtlAsctMebTc()))
                                .collect(Collectors.toList());
                if (members.isEmpty() || members.stream().anyMatch(m -> !"N".equals(m.getCsfHpYn()))) {
                        throw new IllegalStateException(
                                        "서면개최는 평가위원 전원이 대면을 희망하지 않을 때(전원 응답 + 전원 서면)만 확정할 수 있습니다.");
                }

                // 대면개최여부 N + 회의 일정 비우기
                council.markWrittenMeeting();
                // 일정확정/개최 단계 생략하고 진행중으로 직접 전이
                council.changeStatus("07");
        }

        // =========================================================================
        // 내부 헬퍼
        // =========================================================================

        /**
         * 위원 목록의 사번으로 응답용 사용자 정보 Map 생성.
         *
         * <p>사번 집합을 모아 위원 응답 프로젝션으로 일괄 조회합니다.</p>
         */
        private Map<String, UserRepository.CouncilMemberUserRow> buildUserMap(List<Bcmmtm> members) {
                List<String> enos = members.stream().map(member -> member.getEno()).distinct().toList();
                if (enos.isEmpty()) {
                        return Map.of();
                }
                return userRepository.findCouncilMemberUserRowsByEnoIn(enos).stream()
                                .collect(Collectors.toMap(user -> user.getEno(), user -> user, (a, b) -> a));
        }
}
