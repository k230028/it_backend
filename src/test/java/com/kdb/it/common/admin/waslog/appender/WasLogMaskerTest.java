package com.kdb.it.common.admin.waslog.appender;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 링버퍼 마스킹 대상 계약 (BE-55).
 *
 * <p>대상은 <b>토큰류와 주민등록번호 둘뿐</b>이다. 과하게 가리면 장애 조사가 불가능해지므로 범위를 넓히는 변경은 이 테스트를 먼저 고쳐야 한다.
 * 특히 <b>사번이 살아남는지</b>를 지키는 음성 케이스가 이 파일의 핵심이다 — 어느 사용자의 요청에서 난 오류인지가 추적의 출발점이다.
 */
class WasLogMaskerTest {

    @Test
    @DisplayName("JWT를 가린다")
    void masksJwt() {
        String jwt =
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJFMTAwMDEiLCJpYXQiOjE3MDB9.s1gnatureV4lue_abc";

        String masked = WasLogMasker.mask("토큰 검증 실패: " + jwt);

        assertThat(masked).doesNotContain(jwt).contains(WasLogMasker.tokenMark());
    }

    @Test
    @DisplayName("Authorization 헤더의 토큰 값을 가리되 스킴은 남긴다")
    void masksBearerValueKeepingScheme() {
        String masked = WasLogMasker.mask("Authorization: Bearer abcdef0123456789ABCDEF");

        assertThat(masked).isEqualTo("Authorization: Bearer " + WasLogMasker.tokenMark());
    }

    @Test
    @DisplayName("주민등록번호를 하이픈 유무와 무관하게 가린다")
    void masksResidentRegistrationNumber() {
        assertThat(WasLogMasker.mask("신청자 900101-1234567 확인"))
                .isEqualTo("신청자 " + WasLogMasker.rrnMark() + " 확인");
        assertThat(WasLogMasker.mask("신청자 9001011234567 확인"))
                .isEqualTo("신청자 " + WasLogMasker.rrnMark() + " 확인");
    }

    @Test
    @DisplayName("사번은 가리지 않는다 — 장애 추적의 출발점이다")
    void keepsEmployeeNumber() {
        String message = "사용자 E10001 의 결재 상신 실패 (사번 K140024)";

        assertThat(WasLogMasker.mask(message)).isEqualTo(message);
    }

    @Test
    @DisplayName("평범한 식별자와 숫자는 그대로 둔다")
    void keepsOrdinaryText() {
        // 점으로 이어진 클래스명·패키지명은 JWT가 아니다.
        String logger = "com.kdb.it.common.admin.waslog.appender.RingBufferAppender 시작";
        assertThat(WasLogMasker.mask(logger)).isEqualTo(logger);

        // 관리번호·금액·전화번호는 주민번호가 아니다.
        String ids = "APF-2026-00000001 금액 1234567 전화 02-1234-5678";
        assertThat(WasLogMasker.mask(ids)).isEqualTo(ids);
    }

    @Test
    @DisplayName("가릴 것이 없으면 원본 객체를 그대로 돌려준다")
    void returnsSameInstanceWhenNothingToMask() {
        String message = "예산 편성 요청 처리 완료";

        // 대부분의 로그가 이 경로다 — 새 문자열을 만들지 않아야 폭주 시 비용이 붙지 않는다.
        assertThat(WasLogMasker.mask(message)).isSameAs(message);
    }

    @Test
    @DisplayName("null과 빈 문자열을 그대로 돌려준다")
    void passesThroughNullAndEmpty() {
        assertThat(WasLogMasker.mask(null)).isNull();
        assertThat(WasLogMasker.mask("")).isEmpty();
    }
}
