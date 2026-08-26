package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormPersonNamesTest {

    private final List<RequestFormDto.FormDiagnostic> diagnostics = new ArrayList<>();

    private String fit(String name) {
        return FormPersonNames.fit(name, "작성자", FormSheetKind.GENERAL_EXPENSE, diagnostics);
    }

    @Test
    @DisplayName("이름 뒤에 붙은 직책을 떼어 낸다")
    void stripsTrailingTitle() {
        assertThat(fit("최민호 대리")).isEqualTo("최민호");
        assertThat(fit("허인선 팀장")).isEqualTo("허인선");
        assertThat(fit("김준영 차장")).isEqualTo("김준영");
        assertThat(fit("박은지 부부장")).isEqualTo("박은지");
        assertThat(fit("이서연 행원")).isEqualTo("이서연");
        assertThat(fit("Luke Buckingham-Brown 과장")).isEqualTo("Luke Buckingham-Brown");
        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("직책 앞에 수식이 붙은 표기도 떼어 낸다")
    void stripsQualifiedTitle() {
        assertThat(fit("정하윤 IT팀장")).isEqualTo("정하윤");
        assertThat(fit("오세훈 수석부부장")).isEqualTo("오세훈");
    }

    @Test
    @DisplayName("붙여 쓴 표기는 건드리지 않는다")
    void keepsTitleAttachedWithoutSpace() {
        // 공백이 없으면 직책인지 이름 끝 글자인지 가릴 근거가 없다. 추측해 자르면 진짜 이름이 훼손된다
        assertThat(fit("최민호대리")).isEqualTo("최민호대리");
    }

    @Test
    @DisplayName("직책만 적혀 있으면 그대로 둔다")
    void keepsTitleOnlyValue() {
        // 떼면 남는 것이 없다. 이름이 없다는 사실보다 적힌 값을 넘기는 편이 낫다
        assertThat(fit("팀장")).isEqualTo("팀장");
    }

    @Test
    @DisplayName("직책이 없는 이름은 그대로 담는다")
    void keepsPlainName() {
        assertThat(fit("윤소정")).isEqualTo("윤소정");
        assertThat(fit("허진성/장준호")).isEqualTo("허진성/장준호");
        assertThat(diagnostics).isEmpty();
    }

    @Test
    @DisplayName("두 글자 이상 한글 이름 뒤의 공백 표기는 제거한다")
    void keepsOnlyFirstTokenAfterKoreanName() {
        assertThat(fit("김민수 책임자")).isEqualTo("김민수");
        assertThat(fit("홍 길동")).isEqualTo("홍 길동");
        assertThat(fit("Luke Buckingham-Brown")).isEqualTo("Luke Buckingham-Brown");
    }

    @Test
    @DisplayName("직책을 떼고도 길이를 넘으면 잘라 담고 알린다")
    void truncatesAfterStrippingTitle() {
        assertThat(fit("A".repeat(101) + " 과장")).hasSize(FormPersonNames.LIMIT);
        assertThat(diagnostics)
                .extracting(RequestFormDto.FormDiagnostic::code)
                .containsExactly(RequestFormDiagnosticCode.SUBSTITUTE_DROPPED);
        // 진단 문구에는 직책을 뗀 이름이 실린다 — 화면에서 실제 저장 대상과 대조할 수 있어야 한다
        assertThat(diagnostics.get(0).message())
                .contains("A".repeat(101))
                .doesNotContain("과장");
    }

    @Test
    @DisplayName("비어 있으면 null을 돌려준다")
    void returnsNullForBlank() {
        assertThat(fit(null)).isNull();
        assertThat(fit("   ")).isNull();
    }
}
