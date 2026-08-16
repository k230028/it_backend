package com.kdb.it.domain.migration.request.service.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 체크박스 선택 규칙을 고정합니다.
 *
 * <p>양식 컨트롤이 든 워크북은 POI로 만들 수 없어(생성 API가 없습니다) 좌표·문구만 다루는 이 계층에서 규칙을 검증합니다. 실제 추출은 {@link
 * FormCheckboxReader}가 담당하며 실 제출본으로 확인합니다.
 */
class CheckboxFieldReaderTest {

    /** 실측 배치: 18행 `업무구분`(2열)에 7개, 22행 `중복 여부`(2열)·`법규상 완료시기`(6열)가 한 행에 나란히 놓입니다. */
    private static final List<FormCheckbox> SAMPLE =
            List.of(
                    new FormCheckbox(18, 2, " 여신", false),
                    new FormCheckbox(18, 4, " 수신", false),
                    new FormCheckbox(18, 4, " 국제", true),
                    new FormCheckbox(18, 7, " IT", true),
                    new FormCheckbox(18, 8, " 기타 (       )", false),
                    new FormCheckbox(22, 2, " 비중복(N)", true),
                    new FormCheckbox(22, 6, " 2026년 이내", false),
                    new FormCheckbox(22, 8, " 별도없음", true));

    @Test
    @DisplayName("라벨 행에서 체크된 문구만 왼쪽부터 골라낸다")
    void picksCheckedCaptionsInColumnOrder() {
        List<String> captions =
                CheckboxFieldReader.checkedCaptions(SAMPLE, 18, 2, Integer.MAX_VALUE);

        assertThat(captions).containsExactly("국제", "IT");
    }

    @Test
    @DisplayName("한 행에 항목이 둘이면 다음 라벨 열 직전까지만 가져간다")
    void stopsAtNextLabelColumn() {
        // 22행은 `중복 여부`(2열)와 `법규상 완료시기`(6열)가 나란히 놓인다.
        assertThat(CheckboxFieldReader.checkedCaptions(SAMPLE, 22, 2, 6)).containsExactly("비중복(N)");
        assertThat(CheckboxFieldReader.checkedCaptions(SAMPLE, 22, 6, Integer.MAX_VALUE))
                .containsExactly("별도없음");
    }

    @Test
    @DisplayName("라벨 열에 걸친 체크박스도 그 항목으로 본다")
    void includesBoxAnchoredOnLabelColumn() {
        // 체크박스는 라벨 셀 안쪽에서 오른쪽으로 밀려 그려지는 경우가 있어 시작 경계가 라벨 열을 포함해야 한다.
        List<FormCheckbox> onLabelColumn = List.of(new FormCheckbox(18, 2, " 여신", true));

        assertThat(CheckboxFieldReader.checkedCaptions(onLabelColumn, 18, 2, Integer.MAX_VALUE))
                .containsExactly("여신");
    }

    @Test
    @DisplayName("체크박스가 놓인 항목인지 판정한다")
    void detectsCheckboxPresence() {
        assertThat(CheckboxFieldReader.hasCheckbox(SAMPLE, 18, 2, Integer.MAX_VALUE)).isTrue();
        assertThat(CheckboxFieldReader.hasCheckbox(SAMPLE, 19, 2, Integer.MAX_VALUE)).isFalse();
        assertThat(CheckboxFieldReader.hasCheckbox(SAMPLE, 22, 3, 6)).isFalse();
    }

    @Test
    @DisplayName("꼬리의 빈 괄호를 떼어 코드값명과 맞춘다")
    void stripsTrailingBlankParenthesis() {
        assertThat(CheckboxFieldReader.normalize(" 기타 (       )")).isEqualTo("기타");
        assertThat(CheckboxFieldReader.normalize("기타（　）")).isEqualTo("기타");
        assertThat(CheckboxFieldReader.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("내용이 있는 괄호는 의미의 일부라 남긴다")
    void keepsMeaningfulParenthesis() {
        assertThat(CheckboxFieldReader.normalize(" 비중복(N)")).isEqualTo("비중복(N)");
        assertThat(CheckboxFieldReader.normalize("부문(본부장)  보고")).isEqualTo("부문(본부장) 보고");
    }

    @Test
    @DisplayName("복수 선택은 쉼표로 잇고 미선택은 null로 둔다")
    void joinsMultipleSelections() {
        assertThat(CheckboxFieldReader.joined(List.of("클라우드", "AI"))).isEqualTo("클라우드, AI");
        assertThat(CheckboxFieldReader.joined(List.of())).isNull();
    }

    @Test
    @DisplayName("중복 여부는 괄호 안 Y/N을 따른다")
    void readsDuplicateFlagFromParenthesis() {
        assertThat(CheckboxFieldReader.toDuplicateYn(List.of("비중복(N)"))).isEqualTo("N");
        assertThat(CheckboxFieldReader.toDuplicateYn(List.of("중복(Y)"))).isEqualTo("Y");
        assertThat(CheckboxFieldReader.toDuplicateYn(List.of("해당없음"))).isNull();
        assertThat(CheckboxFieldReader.toDuplicateYn(List.of())).isNull();
    }

    @Test
    @DisplayName("같은 문구가 두 번 체크돼 있어도 한 번만 담는다")
    void deduplicatesIdenticalCaptions() {
        List<FormCheckbox> duplicated =
                List.of(
                        new FormCheckbox(18, 3, " 기타 (  )", true),
                        new FormCheckbox(18, 4, " 기타", true));

        assertThat(CheckboxFieldReader.checkedCaptions(duplicated, 18, 2, Integer.MAX_VALUE))
                .containsExactly("기타");
    }
}
