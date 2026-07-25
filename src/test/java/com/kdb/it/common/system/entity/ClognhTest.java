package com.kdb.it.common.system.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Clognh 단위 테스트
 *
 * <p>로그인 성공/실패/로그아웃 이력 생성 시 감사자(FST_ENR_USID/LST_CHG_USID)가 "SYSTEM"으로 명시 기록되는지 검증합니다. 로그인 관련
 * 이력은 비인증 흐름에서 생성되므로 {@link com.kdb.it.config.JpaAuditConfig}의 AuditorAware가 개입하지 않고, 엔티티 팩토리가
 * 감사자를 직접 채웁니다.
 */
class ClognhTest {

    @Test
    @DisplayName("로그인 성공 이력은 감사자를 SYSTEM으로 기록한다")
    void createLoginSuccess_감사자SYSTEM기록() {
        Clognh clognh = Clognh.createLoginSuccess("E0001", "127.0.0.1", "Mozilla/5.0");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("로그인 실패 이력은 감사자를 SYSTEM으로 기록한다")
    void createLoginFailure_감사자SYSTEM기록() {
        Clognh clognh =
                Clognh.createLoginFailure("E0001", "127.0.0.1", "Mozilla/5.0", "비밀번호 불일치");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }

    @Test
    @DisplayName("로그아웃 이력은 감사자를 SYSTEM으로 기록한다")
    void createLogout_감사자SYSTEM기록() {
        Clognh clognh = Clognh.createLogout("E0001", "127.0.0.1", "Mozilla/5.0");

        assertThat(clognh.getFstEnrUsid()).isEqualTo("SYSTEM");
        assertThat(clognh.getLstChgUsid()).isEqualTo("SYSTEM");
    }
}
