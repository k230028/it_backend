package com.kdb.it.common.iam.service;

import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.exception.CustomGeneralException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 Brute-force 감지 서비스 — SEC-03
 *
 * <p>TPRMPP_CLOGNH 이력을 조회하여 10분 이내 로그인 실패 횟수가 5회 이상이면 계정 잠금 예외를 발생시킵니다. 별도 DDL 없이 기존 로그인 이력 테이블을
 * 활용합니다.
 *
 * <p>잠금 기준: 직전 10분 내 LOGIN_FAILURE 이력 5회 이상
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final int WINDOW_MINUTES = 10;

    private final LoginHistoryRepository loginHistoryRepository;

    /**
     * 계정 잠금 여부를 확인합니다.
     *
     * <p>직전 {@value WINDOW_MINUTES}분 이내 로그인 실패 횟수가 {@value MAX_FAILURES}회 이상이면 {@link
     * CustomGeneralException}을 발생시켜 인증을 차단합니다.
     *
     * @param eno 확인할 사용자의 사번
     * @throws CustomGeneralException 실패 횟수 초과로 계정이 잠긴 경우
     */
    public void checkLocked(String eno) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(WINDOW_MINUTES);
        long failureCount =
                loginHistoryRepository.countByEnoAndItPtlLgnTcAndLgnDtmAfter(
                        eno, Clognh.LOGIN_FAILURE, windowStart);

        if (failureCount >= MAX_FAILURES) {
            throw new CustomGeneralException(
                    "계정 잠금: "
                            + WINDOW_MINUTES
                            + "분 내 로그인 실패가 "
                            + MAX_FAILURES
                            + "회 이상입니다. 잠시 후 다시 시도하세요.");
        }
    }
}
