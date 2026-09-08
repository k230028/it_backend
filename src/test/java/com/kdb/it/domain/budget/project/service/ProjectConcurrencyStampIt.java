package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.common.approval.service.ApprovalStamper;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.exception.ProjectConflictException;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 정보화사업 조회에서 받은 스탬프가 실제 저장 검증과 맞물리는지 실 DB로 확인합니다.
 *
 * <p>단위 테스트는 스탬프 계산과 저장 로직을 모두 mock으로 갈라놓으므로 둘이 실제로 같은 입력을 보는지는 확인하지 못합니다. 전산업무비의 {@code
 * CostConcurrencyStampIt}와 같은 순차 시나리오만 씁니다.
 */
@Import({
    JacksonConfig.class,
    ProjectService.class,
    ProjectQueryService.class,
    ProjectQueryAssembler.class,
    ProjectBudgetSummaryService.class,
    ProjectAmountCalculator.class,
    ProjectConcurrencyStamper.class,
    ProjectConcurrencyGuard.class,
    ItBudgetCanonicalJson.class,
    CodeNameMapBuilder.class
})
@DisplayName("정보화사업 동시성 스탬프 왕복")
class ProjectConcurrencyStampIt extends AbstractOracleRepositoryTest {

    @Autowired ProjectService projectService;
    @Autowired ProjectQueryService queryService;
    @Autowired EntityManager entityManager;

    /** 예산 신청 기간 검증은 이 테스트의 관심사가 아니므로 통과시킨다. */
    @MockitoBean CodeService codeService;

    /** 원화 품목만 시드하므로 환율 조회는 호출되어도 값을 쓰지 않는다. */
    @MockitoBean XcrLookupService xcrLookupService;

    /** 작성완료 신청서 스탬프는 complete 플래그가 없으면 호출되지 않는다. */
    @MockitoBean ApprovalStamper approvalStamper;

    /** 조직명 스냅샷은 이 시나리오의 검증 대상이 아니다. */
    @MockitoBean OrgNameResolver orgNameResolver;

    /** 예산편성 단계 적재는 생성 경로에서만 쓰인다. */
    @MockitoBean BprojaSyncService bprojaSyncService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("조회한 스탬프로 저장하면 성공하고, 같은 스탬프로 다시 저장하면 409다")
    void staleStampIsRejectedOnSecondSave() {
        authenticateAsAdmin();
        String abusMngNo = seedUnsubmittedProject(BigDecimal.ZERO);

        ProjectDto.Response loaded = queryService.getProject(abusMngNo);
        String stamp = loaded.getConcurrencyStamp();
        assertThat(stamp).matches("[a-f0-9]{64}");

        ProjectDto.UpdateRequest first = updateRequestFrom(loaded);
        first.setAbusNm("첫 번째 저장");
        first.setConcurrencyStamp(stamp);
        projectService.updateProject(abusMngNo, first);

        // 같은 스탬프를 다시 쓰는 것이 곧 "오래된 화면으로 저장"이다.
        ProjectDto.UpdateRequest second = updateRequestFrom(loaded);
        second.setAbusNm("두 번째 저장");
        second.setConcurrencyStamp(stamp);

        assertThatThrownBy(() -> projectService.updateProject(abusMngNo, second))
                .isInstanceOf(ProjectConflictException.class)
                .satisfies(
                        e -> {
                            ProjectConflictException conflict = (ProjectConflictException) e;
                            assertThat(conflict.code()).isEqualTo("PROJECT_SOURCE_CHANGED");
                            assertThat(conflict.currentStamp()).isNotEqualTo(stamp);
                            assertThat(conflict.current()).isNotNull();
                        });
        assertThat(queryService.getProject(abusMngNo).getAbusNm()).isEqualTo("첫 번째 저장");
    }

    @Test
    @DisplayName("품목이 있는 건도 조회 스탬프로 저장되고, 품목만 바뀌어도 충돌로 잡힌다")
    void itemStampRoundTripsAndDetectsItemOnlyChange() {
        authenticateAsAdmin();
        // 저장 경로가 총소요금액 스냅샷을 품목 합계(1000+2000)로 다시 쓰므로 같은 값으로 시드해 무변경 저장이 값을 바꾸지 않게 한다.
        String abusMngNo = seedUnsubmittedProject(new BigDecimal("3000"));
        seedItem(abusMngNo, "GCL-IT-0001", 1, "서버", "1000");
        seedItem(abusMngNo, "GCL-IT-0002", 2, "스토리지", "2000");

        ProjectDto.Response loaded = queryService.getProject(abusMngNo);
        assertThat(loaded.getItems()).hasSize(2);
        String stamp = loaded.getConcurrencyStamp();
        assertThat(stamp).matches("[a-f0-9]{64}");

        // (a) 품목을 그대로 둔 저장이 조회 스탬프로 통과해야 한다.
        ProjectDto.UpdateRequest unchanged = updateRequestFrom(loaded);
        unchanged.setConcurrencyStamp(stamp);
        projectService.updateProject(abusMngNo, unchanged);

        // 내용이 그대로면 스탬프도 그대로여야 한다 — 읽는 쪽과 쓰는 쪽이 같은 품목 집합을 본다는 뜻이다.
        ProjectDto.Response afterNoop = queryService.getProject(abusMngNo);
        assertThat(afterNoop.getConcurrencyStamp()).isEqualTo(stamp);
        assertThat(afterNoop.getItems()).hasSize(2);

        // (c) 부모 필드는 그대로 두고 품목 한 건만 바꾼다. 부모만 보는 스탬프였다면 놓쳤을 변경이다.
        ProjectDto.UpdateRequest itemOnly = updateRequestFrom(afterNoop);
        itemOnly.setConcurrencyStamp(stamp);
        itemOnly.getItems().get(0).setGclNm("서버-수정");
        projectService.updateProject(abusMngNo, itemOnly);
        assertThat(queryService.getProject(abusMngNo).getAbusNm()).isEqualTo(loaded.getAbusNm());

        // (b) 품목이 바뀐 뒤 예전 스탬프를 다시 쓰면 409여야 한다.
        ProjectDto.UpdateRequest stale = updateRequestFrom(loaded);
        stale.setConcurrencyStamp(stamp);
        stale.getItems().get(0).setGclNm("서버-덮어쓰기");

        assertThatThrownBy(() -> projectService.updateProject(abusMngNo, stale))
                .isInstanceOf(ProjectConflictException.class)
                .satisfies(
                        e -> {
                            ProjectConflictException conflict = (ProjectConflictException) e;
                            assertThat(conflict.code()).isEqualTo("PROJECT_SOURCE_CHANGED");
                            assertThat(conflict.currentStamp()).isNotEqualTo(stamp);
                            assertThat(conflict.current()).isNotNull();
                            assertThat(conflict.current().getItems()).hasSize(2);
                        });
        assertThat(queryService.getProject(abusMngNo).getItems())
                .extracting(ProjectDto.BitemmDto::getGclNm)
                .containsExactlyInAnyOrder("서버-수정", "스토리지");
    }

    /**
     * 신청서가 연결되지 않은(미상신) 정보화사업 한 건을 만들고 관리번호를 돌려준다.
     *
     * <p>총소요금액 스냅샷은 저장 경로가 품목 합계로 다시 쓰므로 호출자가 품목 합계와 같은 값을 넘겨 무변경 저장이 값을 바꾸지 않게 한다.
     *
     * @param totRqmAmt 시드할 총소요금액 스냅샷 (품목 당해 요청금액 합계와 같아야 한다)
     * @return 새로 만든 사업관리번호
     */
    private String seedUnsubmittedProject(BigDecimal totRqmAmt) {
        String abusMngNo =
                ("PRJ-STAMP-" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 12, 0);
        entityManager.persist(
                Bprojm.builder()
                        .abusMngNo(abusMngNo)
                        .sno(1)
                        .lstYn("Y")
                        .abusNm("최초 사업명")
                        .abusTc("10")
                        .bseYy("2026")
                        .svnDpmC("BBR001")
                        .sttDtm(LocalDate.of(2026, 1, 1))
                        .endDtm(LocalDate.of(2026, 12, 31))
                        .dplYn("N")
                        .totRqmAmt(totRqmAmt)
                        .mplAmt(BigDecimal.ZERO)
                        .dfrAmt(BigDecimal.ZERO)
                        .delYn("N")
                        .fstEnrDtm(now)
                        .fstEnrUsid("10001")
                        .lstChgDtm(now)
                        .lstChgUsid("10001")
                        .build());
        entityManager.flush();
        entityManager.clear();
        return abusMngNo;
    }

    /**
     * 대상 개정본에 활성 원화 품목 한 건을 만든다.
     *
     * @param abusMngNo 사업관리번호
     * @param gclMngNo 품목관리번호
     * @param sno 품목 일련번호
     * @param name 품목명
     * @param amount 당해 요청금액
     */
    private void seedItem(String abusMngNo, String gclMngNo, int sno, String name, String amount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 12, 0);
        entityManager.persist(
                Bitemm.builder()
                        .gclMngNo(gclMngNo)
                        .sno(sno)
                        .abusMngNo(abusMngNo)
                        .fntTbCrySno(1)
                        .gclNm(name)
                        .qty(BigDecimal.ONE)
                        .curC("KRW")
                        .amt(new BigDecimal(amount))
                        .mplAmt(BigDecimal.ZERO)
                        .dfrCleC("0")
                        .lstYn("Y")
                        .delYn("N")
                        .fstEnrDtm(now)
                        .fstEnrUsid("10001")
                        .lstChgDtm(now)
                        .lstChgUsid("10001")
                        .build());
        entityManager.flush();
        entityManager.clear();
    }

    /** 관리자 인증 컨텍스트를 설정한다. 저장 경로의 소유권·결재 가드가 인증 주체를 요구한다. */
    private void authenticateAsAdmin() {
        CustomUserDetails admin =
                new CustomUserDetails("10001", List.of(CustomUserDetails.ATH_ADMIN), "BBR001");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                admin, null, admin.getAuthorities()));
    }

    /**
     * 조회 응답을 같은 내용의 수정 요청으로 옮긴다.
     *
     * @param loaded 조회 응답
     * @return 같은 내용의 수정 요청 (스탬프는 호출자가 채운다)
     */
    private ProjectDto.UpdateRequest updateRequestFrom(ProjectDto.Response loaded) {
        return ProjectDto.UpdateRequest.builder()
                .abusNm(loaded.getAbusNm())
                .bzTpC(loaded.getBzTpC())
                .svnDpmC(loaded.getSvnDpmC())
                .dvmDpmC(loaded.getDvmDpmC())
                .sttDtm(loaded.getSttDtm())
                .endDtm(loaded.getEndDtm())
                .usid(loaded.getUsid())
                .dvmUsid(loaded.getDvmUsid())
                .prlmHrkOgzCCone(loaded.getPrlmHrkOgzCCone())
                .tlrUsid(loaded.getTlrUsid())
                .dvmTlrUsid(loaded.getDvmTlrUsid())
                .edrtTc(loaded.getEdrtTc())
                .abusPulConeInf(loaded.getAbusPulConeInf())
                .cpnSafCone(loaded.getCpnSafCone())
                .abusPulNcsInf(loaded.getAbusPulNcsInf())
                .abusXptEffInf(loaded.getAbusXptEffInf())
                .plmDes(loaded.getPlmDes())
                .abusPulDrcnInf(loaded.getAbusPulDrcnInf())
                .mnPrgCone(loaded.getMnPrgCone())
                .hrfPlnCone(loaded.getHrfPlnCone())
                .bzDttNm(loaded.getBzDttNm())
                .sklTpTc(loaded.getSklTpTc())
                .cstTpTc(loaded.getCstTpTc())
                .dplYn(loaded.getDplYn())
                .flfFsgDt(loaded.getFlfFsgDt())
                .rprStsTc(loaded.getRprStsTc())
                .exePttYn(loaded.getExePttYn())
                .bseYy(loaded.getBseYy())
                .odnYn(loaded.getOdnYn())
                .abusTc(loaded.getAbusTc())
                .cncdRfrNo(loaded.getCncdRfrNo())
                .dfrAmt(loaded.getDfrAmt())
                .items(copyItems(loaded.getItems()))
                .build();
    }

    /**
     * 조회된 품목 목록을 수정 요청용으로 복사한다. 응답 객체를 그대로 넘기면 저장 경로가 그 객체를 수정해 원본 스냅샷이 오염된다.
     *
     * @param items 조회된 품목 목록 (null 허용)
     * @return 같은 값을 가진 새 목록
     */
    private List<ProjectDto.BitemmDto> copyItems(List<ProjectDto.BitemmDto> items) {
        if (items == null) {
            return new ArrayList<>();
        }
        return items.stream()
                .map(
                        item ->
                                ProjectDto.BitemmDto.builder()
                                        .gclMngNo(item.getGclMngNo())
                                        .sno(item.getSno())
                                        .ioeC(item.getIoeC())
                                        .gclNm(item.getGclNm())
                                        .qty(item.getQty())
                                        .curC(item.getCurC())
                                        .xcrBseDt(item.getXcrBseDt())
                                        .cncdFdtnCone(item.getCncdFdtnCone())
                                        .bseYm(item.getBseYm())
                                        .dfrCleC(item.getDfrCleC())
                                        .sectSysUtzYn(item.getSectSysUtzYn())
                                        .itrInfrYn(item.getItrInfrYn())
                                        .amt(item.getAmt())
                                        .fcAmt(item.getFcAmt())
                                        .mplAmt(item.getMplAmt())
                                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
