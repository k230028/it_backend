package com.kdb.it.common.system.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Clognh 단위 테스트
 *
 * <p>로그인 성공/실패/로그아웃 이력 생성 직후 감사자(FST_ENR_USID/LST_CHG_USID)가 팩토리 폴백값 "SYSTEM"으로 명시 기록되는지 검증합니다. 이
 * 테스트는 엔티티를 실제로 저장(persist)하지 않으므로 {@code AuditingEntityListener}는 전혀 개입하지 않습니다 — 즉 여기서 검증하는 값은
 * "팩토리가 만든 객체의 초기 상태"이지 "DB에 최종 저장되는 값"이 아닙니다.
 *
 * <p>로그인 성공/실패는 인증 성립 이전(비인증) 흐름이므로 실제 저장 시에도 {@link com.kdb.it.config.JpaAuditConfig}의
 * AuditorAware가 항상 빈 값을 반환해 이 SYSTEM 폴백이 그대로 DB에 남습니다. 반면 로그아웃은 {@code /api/auth/logout}의 정상 경로가
 * 유효한 인증을 요구하므로, 실제 저장 시점에는 {@code AuditingEntityListener}가 이 SYSTEM 폴백을 로그아웃한 사용자의 실제 사번으로
 * 덮어씁니다(SYSTEM은 Refresh 쿠키만으로 폐기하는 비인증 로그아웃 경로에서만 그대로 저장됨). 상세는 {@link Clognh#SYSTEM_AUDITOR}
 * Javadoc 참조.
 */
class ClognhTest {

    @Test
    @DisplayName("로그인 성공 이력은 감사자를 SYSTEM으로 기록한다 (비인증 흐름 — 저장 시에도 그대로 유지됨)")
    void createLoginSuccess_감사자SYSTEM기록() {
        Clognh clognh = Clognh.createLoginSuccess("E0001", "127.0.0.1", "Mozilla/5.0");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("로그인 실패 이력은 감사자를 SYSTEM으로 기록한다 (비인증 흐름 — 저장 시에도 그대로 유지됨)")
    void createLoginFailure_감사자SYSTEM기록() {
        Clognh clognh = Clognh.createLoginFailure("E0001", "127.0.0.1", "Mozilla/5.0", "비밀번호 불일치");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("로그아웃 이력 팩토리는 감사자 폴백을 SYSTEM으로 설정한다 (저장 전 상태 — 인증된 로그아웃은 저장 시 실제 사번으로 대체됨)")
    void createLogout_감사자SYSTEM폴백설정() {
        // 이 단언은 "팩토리가 만든 객체"만 검증한다. 실제 /api/auth/logout(인증 필요) 경로로 저장되면
        // AuditingEntityListener가 이 SYSTEM 값을 로그아웃한 사용자의 사번으로 덮어쓴다 — Clognh.SYSTEM_AUDITOR 참조.
        // SYSTEM이 DB에 그대로 남는 경우는 Refresh 쿠키만으로 폐기하는 비인증 로그아웃(logoutByRefreshToken 만료 경로)뿐이다.
        Clognh clognh = Clognh.createLogout("E0001", "127.0.0.1", "Mozilla/5.0");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }
}
