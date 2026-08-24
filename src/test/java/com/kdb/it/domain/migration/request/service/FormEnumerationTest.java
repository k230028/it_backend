package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormEnumerationTest {

    @Test
    @DisplayName("원문자 번호로 항목을 가른다")
    void splitsByCircledNumbers() {
        FormEnumeration items =
                FormEnumeration.of("① 도서관리 서비스 이용 계약\n② Eviews 유지보수 계약\n③ STATA 라이선스 사용 계약")
                        .orElseThrow();

        assertThat(items.size()).isEqualTo(3);
        assertThat(items.split("① 도서관리 서비스 이용 계약\n② Eviews 유지보수 계약\n③ STATA 라이선스 사용 계약"))
                .containsExactly("도서관리 서비스 이용 계약", "Eviews 유지보수 계약", "STATA 라이선스 사용 계약");
    }

    @Test
    @DisplayName("원문자는 줄 중간에 있어도 번호로 본다")
    void splitsCircledNumbersMidLine() {
        // 실측: 미래전략개발부 ③ 14행의 연간 칸은 ②와 ③ 사이에 줄바꿈이 없다
        FormEnumeration items = FormEnumeration.of("① 가\n② 나\n③ 다").orElseThrow();

        assertThat(items.split("① 44,267\n② 73,723③ 8,065"))
                .containsExactly("44,267", "73,723", "8,065");
    }

    @Test
    @DisplayName("아라비아 숫자 번호는 줄머리에서만 본다")
    void splitsByLineLeadingNumbers() {
        // 실측: 발행시장실 ③ 13행
        FormEnumeration items =
                FormEnumeration.of(
                                "1. ㈜코스콤 Check Expert 단말기 서비스\n"
                                        + "2. 블룸버그(Bloomberg Anywhere) 서비스\n"
                                        + "3. 블룸버그(Open Bloomberg) 서비스")
                        .orElseThrow();

        assertThat(items.size()).isEqualTo(3);
        assertThat(items.split("1. 20,027\n2. 43,170\n3. 45,948"))
                .containsExactly("20,027", "43,170", "45,948");
        assertThat(items.split("1. X\n2. X\n3. X")).containsExactly("X", "X", "X");
    }

    @Test
    @DisplayName("번호가 없는 칸은 모든 항목이 같은 값을 쓴다")
    void sharesUnnumberedCellAcrossItems() {
        FormEnumeration items = FormEnumeration.of("① 가\n② 나").orElseThrow();

        assertThat(items.split("KRW")).containsExactly("KRW", "KRW");
        assertThat(items.split("")).containsExactly("", "");
        assertThat(items.split(null)).containsExactly("", "");
    }

    @Test
    @DisplayName("일부 번호만 적힌 칸은 빠진 번호를 빈 값으로 둔다")
    void leavesMissingNumbersEmpty() {
        // 실측: 미래전략개발부 ③ 6행은 계속 칸에 ②③만, 신규 칸에 ①만 적혀 있다
        FormEnumeration items = FormEnumeration.of("① 가\n② 나\n③ 다").orElseThrow();

        assertThat(items.split("②  O \n ③  O")).containsExactly("", "O", "O");
        assertThat(items.split("①  O")).containsExactly("O", "", "");
    }

    @Test
    @DisplayName("번호가 하나뿐이거나 없으면 열거로 보지 않는다")
    void ignoresSingleOrAbsentNumbering() {
        assertThat(FormEnumeration.of("thinkwise(협업툴) 유지보수")).isEmpty();
        assertThat(FormEnumeration.of("① 단일 계약")).isEmpty();
        assertThat(FormEnumeration.of("")).isEmpty();
        assertThat(FormEnumeration.of(null)).isEmpty();
    }

    @Test
    @DisplayName("소수점과 금액 표기를 번호로 잘못 읽지 않는다")
    void doesNotMistakeDecimalsForNumbering() {
        // `28.6`의 `8.`이나 `1,432`가 번호로 잡히면 정상 행이 통째로 쪼개진다
        assertThat(FormEnumeration.of("환율 1USD = 1,432원 적용, 인상률 3.5% 반영")).isEmpty();
        Optional<FormEnumeration> multiline = FormEnumeration.of("계약 갱신\n28.6백만원 기준\n3.5% 인상");
        assertThat(multiline).isEmpty();
    }
}
