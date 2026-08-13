package com.kdb.it.domain.migration.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 비목 계층 인덱스가 <b>불완전한 공통코드 행</b>을 만났을 때의 동작을 봅니다.
 *
 * <p>운영 코드표는 사람이 유지하므로 계층 문자열이 비었거나 형식이 다른 행이 언제든 들어올 수 있습니다. 그때 인덱스가 예외로 죽으면 반입 기능 전체가 멈추므로, 그런 행을
 * 조용히 건너뛰고 나머지로 동작하는지 고정합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IoeHierarchyIndexEdgeTest {

    @Mock private CodeRepository codeRepository;

    private IoeHierarchyIndex.Snapshot snapshotOf(List<Ccodem> codes) {
        when(codeRepository.findByCIdAndDelYn(CommonCodeGroups.IOE, "N")).thenReturn(codes);
        return new IoeHierarchyIndex(codeRepository).snapshot();
    }

    private static Ccodem code(String cdva, String name, String hierarchy) {
        return Ccodem.builder()
                .cId(CommonCodeGroups.IOE)
                .cdva(cdva)
                .cdvaNm(name)
                .cdvaDtl(hierarchy)
                .build();
    }

    @Test
    @DisplayName("코드값이 없는 행은 건너뛴다")
    void skipsRowsWithoutCodeValue() {
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(
                        List.of(
                                code(null, "코드없음", "일반관리비 - 전산제비 - 회선사용료"),
                                code("010", "회선사용료", "일반관리비 - 전산제비 - 회선사용료")));

        assertThat(snapshot.resolveByDetail("전산제비", "회선사용료").code()).isEqualTo("010");
        assertThat(snapshot.exists("010")).isTrue();
    }

    @Test
    @DisplayName("계층이 없거나 2단 이하인 행은 인덱스에 넣지 않는다")
    void skipsRowsWithShallowHierarchy() {
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(
                        List.of(
                                code("900", "계층없음", null),
                                code("901", "2단만", "일반관리비 - 전산제비"),
                                code("010", "회선사용료", "일반관리비 - 전산제비 - 회선사용료")));

        // 계층이 얕은 행도 코드 존재 확인에는 잡히지만 매칭 후보로는 쓰이지 않는다
        assertThat(snapshot.exists("900")).isTrue();
        assertThat(snapshot.exists("901")).isTrue();
        assertThat(snapshot.resolveByGroup("전산제비", true).code()).isEqualTo("010");
    }

    @Test
    @DisplayName("국내·국외로 좁혀지지 않으면 원래 후보 전체로 되돌아간다")
    void fallsBackWhenDomesticFilterRemovesEverything() {
        // 세부가 전부 `국외`라 원화(국내) 요청에서는 필터가 모두 걸러낸다
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(
                        List.of(
                                code("013", "국외회선사용료", "일반관리비 - 전산제비 - 국외회선사용료"),
                                code("014", "국외유지보수료", "일반관리비 - 전산제비 - 국외유지보수료")));

        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByGroup("전산제비", true);

        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.candidates()).hasSize(2);
    }

    @Test
    @DisplayName("후보가 하나로 좁혀지면 기본값 규칙 없이 확정한다")
    void resolvesWhenNarrowedToOne() {
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(
                        List.of(
                                code("101", "국내기계장치", "자본예산 - 기계장치 - 국내"),
                                code("102", "국외기계장치", "자본예산 - 기계장치 - 국외")));

        assertThat(snapshot.resolveByGroup("기계장치(HW)", true).code()).isEqualTo("101");
        assertThat(snapshot.resolveByGroup("기계장치(HW)", true).candidates()).isEmpty();
    }

    @Test
    @DisplayName("기본값 코드가 후보에 없으면 중의적으로 남긴다")
    void marksAmbiguousWhenDefaultIsMissing() {
        // 개발비의 기본값은 103인데 코드표에 없다 — 임의로 하나를 고르지 않는다
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(
                        List.of(
                                code("104", "개발비(감리/컨설팅)", "자본예산 - 개발비 - 감리/컨설팅"),
                                code("105", "개발비(기타)", "자본예산 - 개발비 - 기타")));

        IoeHierarchyIndex.Resolution resolution = snapshot.resolveByGroup("개발비", true);

        assertThat(resolution.isAmbiguous()).isTrue();
        assertThat(resolution.code()).isNull();
    }

    @Test
    @DisplayName("입력이 null이면 미해석 라벨을 빈 문자열로 둔다")
    void handlesNullInputs() {
        IoeHierarchyIndex.Snapshot snapshot = snapshotOf(new ArrayList<>());

        assertThat(snapshot.resolveByDetail(null, null).label()).isEmpty();
        assertThat(snapshot.resolveByGroup(null, true).label()).isEmpty();
        assertThat(snapshot.resolveByDetail(null, null).isUnresolved()).isTrue();
    }

    @Test
    @DisplayName("코드 존재 확인은 앞뒤 공백을 무시한다")
    void existsIgnoresSurroundingWhitespace() {
        IoeHierarchyIndex.Snapshot snapshot =
                snapshotOf(List.of(code("010", "회선사용료", "일반관리비 - 전산제비 - 회선사용료")));

        assertThat(snapshot.exists(" 010 ")).isTrue();
        assertThat(snapshot.exists("011")).isFalse();
    }
}
