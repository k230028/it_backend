package com.kdb.it.domain.migration.commondata;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.i18n.entity.Clangm;
import com.kdb.it.domain.menu.entity.Cmenua;
import com.kdb.it.domain.menu.entity.Cmenud;
import com.kdb.it.domain.menu.entity.Cmenum;
import com.kdb.it.domain.migration.commondata.dto.CommonDataMigrationDto;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommonDataMigrationPlannerTest {

    private final CommonDataMigrationPlanner planner = new CommonDataMigrationPlanner();

    private static CommonDataMigrationPlanner.Snapshot emptySnapshot() {
        return new CommonDataMigrationPlanner.Snapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), Set.of("ITPAD001"));
    }

    private static CommonDataMigrationDto.MenuRow menuRow(String mnuId, String hrkMnuId) {
        return new CommonDataMigrationDto.MenuRow(
                2, mnuId, hrkMnuId, "메뉴명", "PGE", null, "/admin/menus", 1, "N", 1,
                "/" + mnuId);
    }

    @Test
    void 스냅샷에없는행은_added로_분류한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.menus().added()).isEqualTo(1);
        assertThat(plan.menuAuths().added()).isEqualTo(1);
        assertThat(plan.routes().added()).isEqualTo(1);
    }

    @Test
    void 논리삭제행은_restored_활성행은_updated로_분류한다() {
        Cmenud deleted =
                Cmenud.builder().srePth("/admin/menus").sreMnuNm("메뉴 관리").useYn("Y").build();
        deleted.delete();
        Cmenud active =
                Cmenud.builder().srePth("/admin/codes").sreMnuNm("코드 관리").useYn("Y").build();
        active.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(), List.of(), List.of(deleted, active), List.of(), List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null),
                                new CommonDataMigrationDto.RouteRow(
                                        3, "/admin/codes", "코드 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.routes().restored()).isEqualTo(1);
        assertThat(plan.routes().updated()).isEqualTo(1);
    }

    @Test
    void 메뉴만있고_메뉴권한이비면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴권한"));
    }

    @Test
    void 상위메뉴가_파일과운영어디에도없으면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000002", "MNU9999999")),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000002", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("상위메뉴"));
    }

    @Test
    void 다국어_구분명과컬럼명조합이_규칙위반이면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "CO_C_NM", "en", "Admin", "메뉴")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("컬럼"));
    }

    @Test
    void 다국어구분명이_메뉴도공통코드도아니면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "BRD0000001", "BRD_NM", "ko", "게시판 설명", "게시판")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("구분명"));
    }

    @Test
    void 같은메뉴ID인데_경로나이름이다르면_경고한다() {
        Cmenum existing = Cmenum.builder()
                .mnuId("MNU0000001").mnuNm("다른이름").mnuTpC("PGE").srePth("/other")
                .mnuSotSqnSno(1).hidYn("N").mnuDep(1).whlMnuPth("/MNU0000001").build();
        existing.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(existing), List.of(), List.of(), List.of(), List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("MNU0000001"));
    }

    @Test
    void 시트내PK중복은_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(2, "/a", "가", "Y", null),
                                new CommonDataMigrationDto.RouteRow(3, "/a", "나", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("중복"));
    }

    @Test
    void 필수값이비면_어노테이션검증과별개로_planner도오류를낸다() {
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2, "", null, "메뉴명", "PGE", null, "/admin/menus", 1, "N", 1, "/MNU");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("필수"));
    }

    @Test
    void 메뉴유형이_허용값밖이면_오류다() {
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2, "MNU0000001", null, "메뉴명", "XXX", null, "/admin/menus", 1, "N", 1,
                        "/MNU0000001");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴유형"));
    }

    @Test
    void 숨김여부가_YN이아니면_오류다() {
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2, "MNU0000001", null, "메뉴명", "PGE", null, "/admin/menus", 1, "X", 1,
                        "/MNU0000001");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("숨김여부"));
    }

    @Test
    void 사용여부가_YN이아니면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(new CommonDataMigrationDto.RouteRow(2, "/a", "가", "X", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("사용여부"));
    }

    @Test
    void 메뉴깊이가_범위밖이면_오류다() {
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2, "MNU0000001", null, "메뉴명", "PGE", null, "/admin/menus", 1, "N", 5,
                        "/MNU0000001");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴깊이"));
    }

    @Test
    void 언어코드가_2자가아니면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "MNU_NM", "eng", "Admin", "메뉴")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("언어코드"));
    }

    @Test
    void 메뉴권한의_자격등급이없으면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000001", "NOPE")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("자격등급"));
    }

    @Test
    void 메뉴권한의_메뉴가없으면_오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU9999999", "ITPAD001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("MNU9999999"));
    }

    @Test
    void 공통코드는_added_restored_updated를_센다() {
        Ccodem deleted = Ccodem.builder().cId("CUR").cdva("001").sttDt("20200101").cNm("통화").build();
        deleted.delete();
        Ccodem active = Ccodem.builder().cId("CUR").cdva("002").sttDt("20200101").cNm("통화").build();
        active.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(), List.of(), List.of(), List.of(deleted, active), List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2, "CUR", "003", "20200101", null, "통화", null, null,
                                        null, null, null, null, null, null),
                                new CommonDataMigrationDto.CodeRow(
                                        3, "CUR", "001", "20200101", null, "통화", null, null,
                                        null, null, null, null, null, null),
                                new CommonDataMigrationDto.CodeRow(
                                        4, "CUR", "002", "20200101", null, "통화", null, null,
                                        null, null, null, null, null, null)),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.codes().added()).isEqualTo(1);
        assertThat(plan.codes().restored()).isEqualTo(1);
        assertThat(plan.codes().updated()).isEqualTo(1);
    }

    @Test
    void 복합키필드에구분자문자열이섞여도_다른행을_같은키로오판하지않는다() {
        // cId="CUR", cdva="A::B" 행과 cId="CUR::A", cdva="B" 행은 필드 경계가 다른데,
        // "::"로 이어붙이면 둘 다 "CUR::A::B::20200101"이 되어 중복으로 오판될 수 있었다.
        // List.of(...)를 키로 쓰면 원소 단위 비교라 이런 충돌이 구조적으로 불가능하다.
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2, "CUR", "A::B", "20200101", null, null, null, null,
                                        null, null, null, null, null, null),
                                new CommonDataMigrationDto.CodeRow(
                                        3, "CUR::A", "B", "20200101", null, null, null, null,
                                        null, null, null, null, null, null)),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).noneMatch(e -> e.contains("중복"));
        assertThat(plan.codes().added()).isEqualTo(2);
    }

    @Test
    void 다국어대상키가_메뉴에없으면_경고한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU9999999", "MNU_NM", "en", "Admin", "메뉴")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("MNU9999999"));
    }

    @Test
    void PGE메뉴경로가_카탈로그에없으면_경고한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000001", "ITPAD001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("경로"));
    }
}
