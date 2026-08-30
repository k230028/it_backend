package com.kdb.it.domain.migration.commondata;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.code.entity.Ccodem;
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
                2, mnuId, hrkMnuId, "메뉴명", "PGE", null, "/admin/menus", 1, "N", 1, "/" + mnuId);
    }

    @Test
    void 스냅샷에없는행은_added로_분류한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
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
                        List.of(),
                        List.of(),
                        List.of(deleted, active),
                        List.of(),
                        List.of(),
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
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000002", "ITPAD001")),
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
        Cmenum existing =
                Cmenum.builder()
                        .mnuId("MNU0000001")
                        .mnuNm("다른이름")
                        .mnuTpC("PGE")
                        .srePth("/other")
                        .mnuSotSqnSno(1)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/MNU0000001")
                        .build();
        existing.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(existing),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
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
                        2,
                        "MNU0000001",
                        null,
                        "메뉴명",
                        "XXX",
                        null,
                        "/admin/menus",
                        1,
                        "N",
                        1,
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
                        2,
                        "MNU0000001",
                        null,
                        "메뉴명",
                        "PGE",
                        null,
                        "/admin/menus",
                        1,
                        "X",
                        1,
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
                        2,
                        "MNU0000001",
                        null,
                        "메뉴명",
                        "PGE",
                        null,
                        "/admin/menus",
                        1,
                        "N",
                        5,
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
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU9999999", "ITPAD001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("MNU9999999"));
    }

    @Test
    void 공통코드는_added_restored_updated를_센다() {
        Ccodem deleted =
                Ccodem.builder().cId("CUR").cdva("001").sttDt("20200101").cNm("통화").build();
        deleted.delete();
        Ccodem active = Ccodem.builder().cId("CUR").cdva("002").sttDt("20200101").cNm("통화").build();
        active.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(deleted, active),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2,
                                        "CUR",
                                        "003",
                                        "20200101",
                                        null,
                                        "통화",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                new CommonDataMigrationDto.CodeRow(
                                        3,
                                        "CUR",
                                        "001",
                                        "20200101",
                                        null,
                                        "통화",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                new CommonDataMigrationDto.CodeRow(
                                        4,
                                        "CUR",
                                        "002",
                                        "20200101",
                                        null,
                                        "통화",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)),
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
        // Arrays.asList(...)를 키로 쓰면 원소 단위 비교라 이런 충돌이 구조적으로 불가능하다.
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2,
                                        "CUR",
                                        "A::B",
                                        "20200101",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                new CommonDataMigrationDto.CodeRow(
                                        3,
                                        "CUR::A",
                                        "B",
                                        "20200101",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).noneMatch(e -> e.contains("중복"));
        assertThat(plan.codes().added()).isEqualTo(2);
    }

    @Test
    void 복합키필드가null이어도_NPE없이_필수값오류로_분류한다() {
        // List.of(...)를 복합키로 쓰면 null 원소에서 NPE를 던져, checkDuplicates/classify가
        // validateCodeRow(필수값 검사)보다 먼저 실행되므로 오류 ①이 이 값을 잡기도 전에
        // planner 자체가 죽는다. cId가 null인 행이 NPE 없이 "필수" 오류로 분류되는지 검증한다.
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.CodeRow(
                                        2,
                                        null,
                                        "001",
                                        "20200101",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null)),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("필수"));
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
    void 다국어대상키가_공통코드에있으면_경고하지않는다() {
        Ccodem code = Ccodem.builder().cId("ABUS_TC").cdva("10").sttDt("20260101").build();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(), List.of(), List.of(), List.of(code), List.of(), Set.of());
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2,
                                        "7:ABUS_TC2:108:20260101",
                                        "CDVA_NM",
                                        "en",
                                        "New",
                                        "공통코드")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.warnings()).noneSatisfy(w -> assertThat(w).contains("대상 공통코드"));
    }

    @Test
    void 다국어대상키가_없는공통코드면_경고한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2,
                                        "7:ABUS_TC2:108:20260101",
                                        "CDVA_NM",
                                        "en",
                                        "New",
                                        "공통코드")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("대상 공통코드가 존재하지"));
    }

    @Test
    void 화면경로를_운영의다른메뉴가사용중이면_경고한다() {
        // 시드가 서버별 시퀀스로 메뉴를 채번해 dev/prod 메뉴ID가 어긋나면, 파일이 dev 기준
        // 메뉴ID로 새 메뉴를 만들면서 운영에 이미 있는 화면경로를 그대로 쓰는 시나리오가 생긴다.
        Cmenum other =
                Cmenum.builder()
                        .mnuId("MNU0000042")
                        .mnuNm("운영메뉴")
                        .mnuTpC("PGE")
                        .srePth("/admin/menus")
                        .mnuSotSqnSno(1)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/MNU0000042")
                        .build();
        other.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(other),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings())
                .anySatisfy(w -> assertThat(w).contains("MNU0000042").contains("/admin/menus"));
    }

    @Test
    void 정렬순서와깊이가null이어도_NPE없이_필수오류2건으로_분류한다() {
        // mnuSotSqnSno·mnuDep는 DTO 필드 레벨 검증 제거로 planner가 유일한 방어선이다.
        // else-if로 null 검사를 먼저 하므로 범위 비교(mnuDep < MIN)에서 auto-unboxing NPE가 나지 않는다.
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2,
                        "MNU0000001",
                        null,
                        "메뉴명",
                        "PGE",
                        null,
                        "/admin/menus",
                        null,
                        "N",
                        null,
                        "/MNU0000001");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row), List.of(), List.of(), List.of(), List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴정렬순서"));
        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴깊이"));
    }

    @Test
    void PGE메뉴경로가_카탈로그에없으면_경고한다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings()).anySatisfy(w -> assertThat(w).contains("경로"));
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * 필수·참조 검증의 "건너뛰는 쪽" 분기 보강 (TEST.md 커버리지 70% 기준)
     * 기존 테스트는 위반을 만들어 오류가 생기는 쪽만 확인한다. 여기서는 빈 값·null이
     * 들어와 참조 검증을 건너뛰는 경로와, 경고를 내지 않는 정상 경로를 고정한다.
     * ─────────────────────────────────────────────────────────────────────────
     */

    @Test
    void 상위메뉴ID가_공백이면_참조검증을_건너뛴다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", "   ")),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).noneSatisfy(e -> assertThat(e).contains("상위메뉴ID"));
    }

    @Test
    void 상위메뉴가_운영스냅샷에만있어도_오류가아니다() {
        Cmenum parent =
                Cmenum.builder()
                        .mnuId("MNU0000009")
                        .mnuNm("상위")
                        .mnuTpC("GRP")
                        .mnuSotSqnSno(1)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/MNU0000009")
                        .build();
        parent.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(parent),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", "MNU0000009")),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.errors()).noneSatisfy(e -> assertThat(e).contains("상위메뉴ID"));
    }

    @Test
    void 메뉴권한의_메뉴ID와자격등급ID가_공백이면_참조오류를_중복으로내지않는다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(new CommonDataMigrationDto.MenuAuthRow(2, "  ", "  ")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).noneSatisfy(e -> assertThat(e).contains("존재하지 않습니다"));
        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴ID"));
        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("자격등급ID"));
    }

    @Test
    void 메뉴유형과_숨김여부와_사용여부가_null이면_필수오류다() {
        CommonDataMigrationDto.MenuRow row =
                new CommonDataMigrationDto.MenuRow(
                        2,
                        "MNU0000001",
                        null,
                        "메뉴명",
                        null,
                        null,
                        "/admin/menus",
                        1,
                        null,
                        1,
                        "/MNU0000001");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(row),
                        List.of(),
                        List.of(new CommonDataMigrationDto.RouteRow(2, "/a", "가", null, null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("메뉴유형은 필수"));
        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("숨김여부는 필수"));
        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("사용여부는 필수"));
    }

    @Test
    void 다국어_언어코드가_null이면_필수오류다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "MNU_NM", null, "번역", "메뉴")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("언어코드는 필수"));
    }

    /* 구분명이 비면 대상 판정 자체를 건너뛰므로 "메뉴/공통코드만 허용" 오류가 겹쳐 나오지 않는다. */
    @Test
    void 다국어_구분명이비면_대상판정을_건너뛴다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "MNU_NM", "ko", "번역", "  ")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).anySatisfy(e -> assertThat(e).contains("구분명"));
        assertThat(plan.errors()).noneSatisfy(e -> assertThat(e).contains("메뉴/공통코드만 허용"));
    }

    @Test
    void 다국어_대상키가_파일메뉴에있으면_경고하지않는다() {
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of(
                                new CommonDataMigrationDto.TranslationRow(
                                        2, "MNU0000001", "MNU_NM", "en", "Menu", "메뉴")));

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.errors()).isEmpty();
        assertThat(plan.warnings()).noneSatisfy(w -> assertThat(w).contains("대상 메뉴가 존재하지"));
    }

    /* 이름·경로가 운영과 같으면 경고가 없어야 한다(경고 ① 오탐 방지). */
    @Test
    void 운영메뉴와_이름과경로가같으면_경고하지않는다() {
        Cmenum existing =
                Cmenum.builder()
                        .mnuId("MNU0000001")
                        .mnuNm("메뉴명")
                        .mnuTpC("PGE")
                        .srePth("/admin/menus")
                        .mnuSotSqnSno(1)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/MNU0000001")
                        .build();
        existing.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(existing),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.warnings()).noneSatisfy(w -> assertThat(w).contains("이름 또는 경로가 다릅니다"));
    }

    /* PGE가 아니거나 화면경로가 비면 경로 카탈로그 경고를 건너뛴다. */
    @Test
    void PGE가아닌메뉴나_화면경로가빈메뉴는_경로경고를내지않는다() {
        CommonDataMigrationDto.MenuRow group =
                new CommonDataMigrationDto.MenuRow(
                        2, "MNU0000001", null, "그룹", "GRP", null, null, 1, "N", 1, "/MNU0000001");
        CommonDataMigrationDto.MenuRow pageWithoutPath =
                new CommonDataMigrationDto.MenuRow(
                        3, "MNU0000002", null, "화면", "PGE", null, "   ", 2, "N", 1, "/MNU0000002");
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(group, pageWithoutPath),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(2, "MNU0000001", "ITPAD001"),
                                new CommonDataMigrationDto.MenuAuthRow(
                                        3, "MNU0000002", "ITPAD001")),
                        List.of(),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, emptySnapshot());

        assertThat(plan.warnings()).noneSatisfy(w -> assertThat(w).contains("경로 카탈로그에 없습니다"));
    }

    /* 같은 메뉴ID가 그 경로를 이미 쓰고 있으면 자기 자신 갱신이라 경고 대상이 아니다. */
    @Test
    void 같은메뉴가_같은경로를이미쓰고있으면_경로충돌경고를내지않는다() {
        Cmenum existing =
                Cmenum.builder()
                        .mnuId("MNU0000001")
                        .mnuNm("메뉴명")
                        .mnuTpC("PGE")
                        .srePth("/admin/menus")
                        .mnuSotSqnSno(1)
                        .hidYn("N")
                        .mnuDep(1)
                        .whlMnuPth("/MNU0000001")
                        .build();
        existing.restore();
        CommonDataMigrationPlanner.Snapshot snapshot =
                new CommonDataMigrationPlanner.Snapshot(
                        List.of(existing),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Set.of("ITPAD001"));
        CommonDataMigrationDto.Request request =
                new CommonDataMigrationDto.Request(
                        List.of(menuRow("MNU0000001", null)),
                        List.of(
                                new CommonDataMigrationDto.MenuAuthRow(
                                        2, "MNU0000001", "ITPAD001")),
                        List.of(
                                new CommonDataMigrationDto.RouteRow(
                                        2, "/admin/menus", "메뉴 관리", "Y", null)),
                        List.of(),
                        List.of());

        CommonDataMigrationPlanner.Plan plan = planner.plan(request, snapshot);

        assertThat(plan.warnings()).noneSatisfy(w -> assertThat(w).contains("사용 중입니다"));
    }
}
