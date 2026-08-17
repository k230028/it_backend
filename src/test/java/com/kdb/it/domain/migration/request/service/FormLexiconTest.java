package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormLexiconTest {

    @Test
    @DisplayName("국문 정본으로 국문·영문 표기를 모두 얻는다")
    void resolvesLabelAliases() {
        assertThat(FormLexicon.labelAliases("연간")).contains("연간", "Annual");
        assertThat(FormLexicon.labelAliases("상대처")).contains("상대처", "Counterparty");
        assertThat(FormLexicon.labelAliases("정보보호 관련여부")).contains("정보보호 관련여부", "InfoSec. Related");
    }

    @Test
    @DisplayName("대조표에 없는 라벨은 자기 자신만 별칭으로 돌려준다")
    void unknownLabelFallsBackToItself() {
        assertThat(FormLexicon.labelAliases("존재하지 않는 라벨")).containsExactly("존재하지 않는 라벨");
    }

    @Test
    @DisplayName("대조표 등록 여부로 확인된 대응과 우연한 일치를 가른다")
    void reportsWhetherIoeNameIsVetted() {
        // 중분류로만 좁힌 해석에 확인 경고를 붙일지 정하는 기준이다
        assertThat(FormLexicon.hasIoeAlias("Machinery")).isTrue();
        // 공백만 지우고 대소문자는 그대로 본다 — `canonicalIoeName`과 같은 정규화라 판정이 어긋나지 않는다
        assertThat(FormLexicon.hasIoeAlias(" Machinery ")).isTrue();
        assertThat(FormLexicon.hasIoeAlias("machinery")).isFalse();
        assertThat(FormLexicon.hasIoeAlias("국외전산유지보수료")).isTrue();
        // 코드표의 중분류 이름이지만 대조표에 넣은 적은 없다
        assertThat(FormLexicon.hasIoeAlias("기계장치")).isFalse();
        assertThat(FormLexicon.hasIoeAlias(null)).isFalse();
    }

    @Test
    @DisplayName("컬럼 id별 정본을 별칭 목록으로 펼친다")
    void expandsColumnAliases() {
        Map<String, List<String>> aliases =
                FormLexicon.columnAliases(Map.of("annual", "연간", "currency", "통화 구분"));

        assertThat(aliases.get("annual")).contains("연간", "Annual");
        assertThat(aliases.get("currency")).contains("통화 구분", "Currency");
    }

    @Test
    @DisplayName("Y 표기 여러 종을 모두 Y로 접는다")
    void normalizesAffirmativeMarks() {
        for (String raw : new String[] {"O", "○", "◯", "０", "√", "∨", "V", "Y", "y", "●"}) {
            assertThat(FormLexicon.toYn(raw)).as("raw=%s", raw).contains("Y");
        }
    }

    @Test
    @DisplayName("N 표기와 로마숫자 X를 모두 N으로 접는다")
    void normalizesNegativeMarks() {
        // 런던 제출본의 Ⅹ는 알파벳 X가 아니라 로마숫자 10(U+2169)이다
        for (String raw : new String[] {"X", "x", "Ⅹ", "✕", "N", "n", "", "  "}) {
            assertThat(FormLexicon.toYn(raw)).as("raw=%s", raw).contains("N");
        }
    }

    @Test
    @DisplayName("정의되지 않은 표기는 빈 Optional로 남겨 진단에 넘긴다")
    void leavesUnknownMarkUnresolved() {
        assertThat(FormLexicon.toYn("해당없음")).isEmpty();
        assertThat(FormLexicon.toYn("?")).isEmpty();
    }

    @Test
    @DisplayName("비목 별칭을 공통코드 표기로 되돌린다")
    void canonicalizesIoeAliases() {
        // 양식은 `국외전산유지보수료`, 공통코드 014는 `국외유지보수료`
        assertThat(FormLexicon.canonicalIoeName("국외전산유지보수료")).isEqualTo("국외유지보수료");
        assertThat(FormLexicon.canonicalIoeName("Foreign branch line usage fees"))
                .isEqualTo("국외회선사용료");
        assertThat(FormLexicon.canonicalIoeName("Foreign branch IT service")).isEqualTo("국외전산용역비");
        assertThat(FormLexicon.canonicalIoeName("Foreign branch IT maintenance fees"))
                .isEqualTo("국외유지보수료");
        assertThat(FormLexicon.canonicalIoeName("IT Expenses")).isEqualTo("전산제비");
    }

    @Test
    @DisplayName("대조표에 없는 비목명은 원문 그대로 넘겨 미해석 진단으로 이어지게 한다")
    void keepsUnknownIoeNameAsIs() {
        assertThat(FormLexicon.canonicalIoeName("국외전산기타제비")).isEqualTo("국외전산기타제비");
        assertThat(FormLexicon.canonicalIoeName("Cloud Subscription"))
                .isEqualTo("Cloud Subscription");
        assertThat(FormLexicon.canonicalIoeName(null)).isEmpty();
    }

    @Test
    @DisplayName("영문 양식의 자본예산 중분류 `Machinery`를 기계장치로 되돌린다")
    void mapsMachineryToCapitalGroup() {
        // 런던 제출본은 시트 ②의 `구분` 열과 시트 ③의 세부비목 열 양쪽에 이 표기를 씁니다.
        assertThat(FormLexicon.canonicalIoeName("Machinery")).isEqualTo("기계장치(HW)");
    }

    @Test
    @DisplayName("체크박스·전결권자 문구를 공통코드 코드값명으로 되돌린다")
    void canonicalizesOptionNames() {
        // 양식은 설명을 덧붙이거나 통칭을 쓰고, 코드표는 짧은 직명·코드값명을 쓴다
        assertThat(FormLexicon.canonicalOptionName("부문(본부장) 보고")).isEqualTo("부문(본부)장");
        assertThat(FormLexicon.canonicalOptionName("확정(변동가능성 無)")).isEqualTo("확정");
        assertThat(FormLexicon.canonicalOptionName("수석부행장")).isEqualTo("전무이사");
        // 대조표에 없으면 원문 그대로 넘겨 코드 조회에서 걸러지게 한다
        assertThat(FormLexicon.canonicalOptionName("이사회")).isEqualTo("이사회");
        assertThat(FormLexicon.canonicalOptionName(null)).isEmpty();
    }

    @Test
    @DisplayName("계속·신규 표시를 사업구분 코드로 바꾼다")
    void mapsContinuedAndNewToAbusTc() {
        assertThat(FormLexicon.toAbusTc("○", "")).contains(FormLexicon.ABUS_TC_CONTINUED);
        assertThat(FormLexicon.toAbusTc("", "√")).contains(FormLexicon.ABUS_TC_NEW);
        assertThat(FormLexicon.toAbusTc("", "")).isEmpty();
        assertThat(FormLexicon.toAbusTc("○", "○")).isEmpty();
    }
}
