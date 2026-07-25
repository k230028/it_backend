package com.kdb.it.common.system.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Crtokm 단위 테스트
 *
 * <p>신규 갱신토큰 생성 시 소유자 사번이 최초/최종 감사자로 명시 기록되는지, {@link Crtokm#markRotated()} 호출 시 최종 변경 감사자가
 * 소유자 사번으로 갱신되는지, 공백 감사자가 거부되는지 검증합니다.
 */
class CrtokmTest {

    @Test
    @DisplayName("신규 갱신토큰은 소유자 사번을 최초/최종 감사자로 기록한다")
    void create_소유자사번_감사자기록() {
        Crtokm token =
                Crtokm.create(
                        "hash-api-tok",
                        "hash-lookup-tok",
                        "E0001",
                        "family-1",
                        LocalDateTime.now().plusDays(7));

        assertThat(token.getFstEnrUsid()).isEqualTo("E0001");
        assertThat(token.getLstChgUsid()).isEqualTo("E0001");
    }

    @Test
    @DisplayName("markRotated 호출 시 최종 변경 감사자를 소유자 사번으로 갱신한다")
    void markRotated_최종변경감사자_소유자사번갱신() {
        Crtokm token =
                Crtokm.create(
                        "hash-api-tok",
                        "hash-lookup-tok",
                        "E0002",
                        "family-2",
                        LocalDateTime.now().plusDays(7));

        token.markRotated();

        assertThat(token.getLstChgUsid()).isEqualTo("E0002");
        assertThat(token.isRotated()).isTrue();
    }

    @Test
    @DisplayName("공백 사번으로 생성하면 IllegalArgumentException을 던진다")
    void create_공백사번_예외발생() {
        assertThatThrownBy(
                        () ->
                                Crtokm.create(
                                        "hash-api-tok",
                                        "hash-lookup-tok",
                                        "   ",
                                        "family-3",
                                        LocalDateTime.now().plusDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
