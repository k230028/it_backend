package com.kdb.it.common.system.service;

import com.kdb.it.common.system.dto.LoginHistoryDto;
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 이력 서비스
 *
 * <p>사용자의 로그인/로그아웃 이력 조회 비즈니스 로직을 처리합니다.
 *
 * <p>이력 생성(기록)은 {@link AuthService}에서 담당하며, 이 서비스는 이력 조회만 담당합니다.
 *
 * <p>{@code @Transactional(readOnly = true)}: 기본적으로 읽기 전용 트랜잭션으로 실행하여 불필요한 변경 감지(Dirty Checking)를
 * 방지하고 성능을 최적화합니다.
 */
@Service // Spring 서비스 빈으로 등록
@RequiredArgsConstructor // final 필드 생성자 자동 주입 (Lombok)
@Transactional(readOnly = true) // 읽기 전용 트랜잭션 (기본값)
public class LoginHistoryService {

    /** 로그인 이력 데이터 접근 리포지토리 */
    private final LoginHistoryRepository loginHistoryRepository;

    /**
     * 사용자의 로그인 이력 조회 (최신순, 최대 50건)
     *
     * <p>특정 사용자의 로그인 이력을 최신 순으로 조회하되 최대 50건으로 제한합니다. 컨트롤러에서 현재 로그인한 사용자의 사번으로 본인 이력만 조회됩니다.
     *
     * <p>이력 유형:
     *
     * <ul>
     *   <li>{@code LOGIN_SUCCESS}: 로그인 성공
     *   <li>{@code LOGIN_FAILURE}: 로그인 실패
     *   <li>{@code LOGOUT}: 로그아웃
     * </ul>
     *
     * @param eno 조회할 사용자의 사번
     * @return 로그인 이력 DTO 목록 (최신순, 최대 50건)
     */
    public List<LoginHistoryDto.Response> getLoginHistory(String eno) {
        // DB 레벨에서 최신순 50건만 조회 (메모리 처리 불필요)
        List<Clognh> histories = loginHistoryRepository.findTop50ByEnoOrderByLgnDtmDesc(eno);

        // 엔티티 목록을 DTO 목록으로 변환하여 반환
        return LoginHistoryDto.Response.fromEntities(histories);
    }

    /**
     * 최근 10개 로그인 이력 조회
     *
     * <p>사용자 대시보드 등에서 간략한 접속 이력 표시에 사용됩니다. DB에서 최초부터 10개만 조회하므로 {@link #getLoginHistory(String)}보다
     * 효율적입니다.
     *
     * @param eno 조회할 사용자의 사번
     * @return 최근 10개의 로그인 이력 DTO 목록
     */
    public List<LoginHistoryDto.Response> getRecentLoginHistory(String eno) {
        // Repository의 findTop10By 메서드로 DB에서 직접 10건만 조회 (LIMIT 10)
        List<Clognh> histories = loginHistoryRepository.findTop10ByEnoOrderByLgnDtmDesc(eno);
        return LoginHistoryDto.Response.fromEntities(histories);
    }
}
