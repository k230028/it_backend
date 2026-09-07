package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kdb.it.common.approval.itbudget.service.ItBudgetCanonicalJson;
import com.kdb.it.common.approval.service.ApprovalStamper;
import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.util.CodeNameMapBuilder;
import com.kdb.it.config.JacksonConfig;
import com.kdb.it.domain.budget.common.security.ApprovalWriteGuard;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.exception.CostConflictException;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
 * 조회에서 받은 스탬프가 실제 저장 검증과 맞물리는지 실 DB로 확인합니다.
 *
 * <p>단위 테스트는 스탬프 계산과 저장 로직을 모두 mock으로 갈라놓으므로 둘이 실제로 같은 입력을 보는지는 확인하지 못합니다. 스레드 두 개로 잠금 경합을 재현하지 않고
 * 순차 시나리오만 씁니다 — 이 계획이 막으려는 결함은 "잠금이 겹치는 순간"이 아니라 저장 요청 사이에 벌어진 변경이기 때문입니다.
 */
@Import({
    JacksonConfig.class,
    CostService.class,
    CostWriteTargetLoader.class,
    CostQueryService.class,
    CostQueryAssembler.class,
    CostTerminalAssembler.class,
    CostConcurrencyStamper.class,
    CostConcurrencyGuard.class,
    ItBudgetCanonicalJson.class,
    CodeNameMapBuilder.class,
    ApprovalWriteGuard.class
})
@DisplayName("전산업무비 동시성 스탬프 왕복")
class CostConcurrencyStampIt extends AbstractOracleRepositoryTest {

    @Autowired CostService costService;
    @Autowired CostQueryService queryService;
    @Autowired EntityManager entityManager;

    /** 예산 신청 기간 검증은 이 테스트의 관심사가 아니므로 통과시킨다. */
    @MockitoBean CodeService codeService;

    /** 원화 시나리오라 환율 조회는 사용하지 않는다. */
    @MockitoBean XcrLookupService xcrLookupService;

    /** 작성완료 신청서 스탬프는 complete 플래그가 없으면 호출되지 않는다. */
    @MockitoBean ApprovalStamper approvalStamper;

    /** 조직명 스냅샷은 이 시나리오의 검증 대상이 아니다. */
    @MockitoBean OrgNameResolver orgNameResolver;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("조회한 스탬프로 저장하면 성공하고, 같은 스탬프로 다시 저장하면 409다")
    void staleStampIsRejectedOnSecondSave() {
        authenticateAsAdmin();
        String costBgNo = seedUnsubmittedCost();

        CostDto.Response loaded = queryService.getCost(costBgNo);
        String stamp = loaded.getConcurrencyStamp();
        assertThat(stamp).matches("[a-f0-9]{64}");

        CostDto.UpdateRequest first = updateRequestFrom(loaded);
        first.setCttNm("첫 번째 저장");
        first.setConcurrencyStamp(stamp);
        costService.updateCost(costBgNo, first);

        // 같은 스탬프를 다시 쓰는 것이 곧 "오래된 화면으로 저장"이다.
        CostDto.UpdateRequest second = updateRequestFrom(loaded);
        second.setCttNm("두 번째 저장");
        second.setConcurrencyStamp(stamp);

        assertThatThrownBy(() -> costService.updateCost(costBgNo, second))
                .isInstanceOf(CostConflictException.class)
                .satisfies(
                        e -> {
                            CostConflictException conflict = (CostConflictException) e;
                            assertThat(conflict.code()).isEqualTo("COST_SOURCE_CHANGED");
                            assertThat(conflict.currentStamp()).isNotEqualTo(stamp);
                            assertThat(conflict.current()).isNotNull();
                        });
        assertThat(queryService.getCost(costBgNo).getCttNm()).isEqualTo("첫 번째 저장");
    }

    @Test
    @DisplayName("단말기가 있는 건도 조회 스탬프로 저장되고, 단말기만 바뀌어도 충돌로 잡힌다")
    void terminalStampRoundTripsAndDetectsTerminalOnlyChange() {
        authenticateAsAdmin();
        String costBgNo = seedUnsubmittedCost();
        seedTerminal(costBgNo, "TER-IT-0001", 1, "단말A", "1000");
        seedTerminal(costBgNo, "TER-IT-0002", 2, "단말B", "2000");

        CostDto.Response loaded = queryService.getCost(costBgNo);
        assertThat(loaded.getTerminals()).hasSize(2);
        String stamp = loaded.getConcurrencyStamp();
        assertThat(stamp).matches("[a-f0-9]{64}");

        // (a) 단말기를 그대로 둔 저장이 조회 스탬프로 통과해야 한다.
        //     여기서 409가 나면 단말기가 있는 모든 건의 저장이 막히는 장애다.
        CostDto.UpdateRequest unchanged = updateRequestFrom(loaded);
        unchanged.setConcurrencyStamp(stamp);
        costService.updateCost(costBgNo, unchanged);

        // 내용이 그대로면 스탬프도 그대로여야 한다 — 읽는 쪽과 쓰는 쪽이 같은 단말기 집합을 본다는 뜻이다.
        CostDto.Response afterNoop = queryService.getCost(costBgNo);
        assertThat(afterNoop.getConcurrencyStamp()).isEqualTo(stamp);
        assertThat(afterNoop.getTerminals()).hasSize(2);

        // (c) 부모 필드는 그대로 두고 단말기 한 건만 바꾼다. 부모만 보는 스탬프였다면 놓쳤을 변경이다.
        CostDto.UpdateRequest terminalOnly = updateRequestFrom(afterNoop);
        terminalOnly.setConcurrencyStamp(stamp);
        terminalOnly.getTerminals().get(0).setSpfTmnNm("단말A-수정");
        costService.updateCost(costBgNo, terminalOnly);
        assertThat(queryService.getCost(costBgNo).getCttNm()).isEqualTo(loaded.getCttNm());

        // (b) 단말기가 바뀐 뒤 예전 스탬프를 다시 쓰면 409여야 한다.
        CostDto.UpdateRequest stale = updateRequestFrom(loaded);
        stale.setConcurrencyStamp(stamp);
        stale.getTerminals().get(0).setSpfTmnNm("단말A-덮어쓰기");

        assertThatThrownBy(() -> costService.updateCost(costBgNo, stale))
                .isInstanceOf(CostConflictException.class)
                .satisfies(
                        e -> {
                            CostConflictException conflict = (CostConflictException) e;
                            assertThat(conflict.code()).isEqualTo("COST_SOURCE_CHANGED");
                            assertThat(conflict.currentStamp()).isNotEqualTo(stamp);
                            assertThat(conflict.current()).isNotNull();
                            assertThat(conflict.current().getTerminals()).hasSize(2);
                        });
        assertThat(queryService.getCost(costBgNo).getTerminals())
                .extracting(CostDto.TerminalDto::getSpfTmnNm)
                .containsExactlyInAnyOrder("단말A-수정", "단말B");
    }

    /**
     * 신청서가 연결되지 않은(미상신) 전산업무비 한 건을 만들고 관리번호를 돌려준다.
     *
     * <p>시드 데이터는 다른 테스트의 결재 상태에 좌우되므로 이 테스트가 직접 만든다. {@code @DataJpaTest}의 기본 롤백으로 정리된다.
     *
     * @return 새로 만든 전산업무비 관리번호
     */
    private String seedUnsubmittedCost() {
        String costBgNo =
                ("BG-STAMP-" + UUID.randomUUID().toString().replace("-", "")).substring(0, 15);
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 12, 0);
        entityManager.persist(
                Bcostm.builder()
                        .costBgNo(costBgNo)
                        .bgSno(1)
                        .lstYn("Y")
                        .cttNm("최초 계약")
                        .bseYy("2026")
                        .dfrCleC("0")
                        .abusTc("0")
                        .curC("KRW")
                        .costSvnDpmC("BBR001")
                        .delYn("N")
                        .fstEnrDtm(now)
                        .fstEnrUsid("10001")
                        .lstChgDtm(now)
                        .lstChgUsid("10001")
                        .build());
        entityManager.flush();
        entityManager.clear();
        return costBgNo;
    }

    /**
     * 대상 개정본에 활성 단말기 한 건을 만든다.
     *
     * <p>원화·환율 없음·담당자 없음으로 두어 저장 경로의 금액 보정·이름 스냅샷이 값을 바꾸지 않게 한다. 스탬프가 바뀌는 원인을 테스트가 의도한 변경으로만 한정하기
     * 위함이다.
     *
     * @param costBgNo 부모 전산업무비 관리번호
     * @param tmnMngNo 단말관리번호
     * @param sno 단말 일련번호
     * @param name 단말기명
     * @param amount 단말기 금액
     */
    private void seedTerminal(
            String costBgNo, String tmnMngNo, int sno, String name, String amount) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 12, 0);
        entityManager.persist(
                Btermm.builder()
                        .tmnMngNo(tmnMngNo)
                        .sno(sno)
                        .termBgNo(costBgNo)
                        .termBgSno(1)
                        .spfTmnNm(name)
                        .termRqmBgAmt(new BigDecimal(amount))
                        .curC("KRW")
                        .dfrCleC("0")
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
     * <p>NOT NULL 컬럼을 비우면 저장 검증이 아니라 flush에서 실패하므로 원장 필드를 그대로 복사한다.
     *
     * @param loaded 조회 응답
     * @return 같은 내용의 수정 요청 (스탬프는 호출자가 채운다)
     */
    private CostDto.UpdateRequest updateRequestFrom(CostDto.Response loaded) {
        return CostDto.UpdateRequest.builder()
                .ioeC(loaded.getIoeC())
                .cttNm(loaded.getCttNm())
                .cttOppNm(loaded.getCttOppNm())
                .costTotXpAmt(loaded.getCostTotXpAmt())
                .dfrCleC(loaded.getDfrCleC())
                .fstDfrDt(loaded.getFstDfrDt())
                .curC(loaded.getCurC())
                .xcrBseDt(loaded.getXcrBseDt())
                .sectSysUtzYn(loaded.getSectSysUtzYn())
                .indRsn(loaded.getIndRsn())
                .cgprId(loaded.getCgprId())
                .costSvnDpmC(loaded.getCostSvnDpmC())
                .svnTemC(loaded.getSvnTemC())
                .bgUntAbusC(loaded.getBgUntAbusC())
                .tmnYn(loaded.getTmnYn())
                .abusTc(loaded.getAbusTc())
                .bseYy(loaded.getBseYy())
                .cncdRfrNo(loaded.getCncdRfrNo())
                .terminals(copyTerminals(loaded.getTerminals()))
                .build();
    }

    /**
     * 조회된 단말기 목록을 수정 요청용으로 복사한다.
     *
     * <p>조회 응답 DTO를 그대로 넘기면 저장 경로가 그 객체를 수정해 원본 스냅샷이 오염되므로, 테스트가 "예전에 조회한 화면"을 재사용할 수 없게 된다.
     *
     * @param terminals 조회된 단말기 목록 (null 허용)
     * @return 같은 값을 가진 새 목록
     */
    private List<CostDto.TerminalDto> copyTerminals(List<CostDto.TerminalDto> terminals) {
        if (terminals == null) {
            return new ArrayList<>();
        }
        return terminals.stream()
                .map(
                        terminal ->
                                CostDto.TerminalDto.builder()
                                        .tmnMngNo(terminal.getTmnMngNo())
                                        .sno(terminal.getSno())
                                        .spfTmnNm(terminal.getSpfTmnNm())
                                        .tmnKdTc(terminal.getTmnKdTc())
                                        .nsfUsgCone(terminal.getNsfUsgCone())
                                        .tmnClsfC(terminal.getTmnClsfC())
                                        .termRqmBgAmt(terminal.getTermRqmBgAmt())
                                        .fcAmt(terminal.getFcAmt())
                                        .curC(terminal.getCurC())
                                        .xcr(terminal.getXcr())
                                        .xcrBseDt(terminal.getXcrBseDt())
                                        .dfrCleC(terminal.getDfrCleC())
                                        .indRsn(terminal.getIndRsn())
                                        .cgprId(terminal.getCgprId())
                                        .cgprNm(terminal.getCgprNm())
                                        .termSvnTemC(terminal.getTermSvnTemC())
                                        .termSvnTemNm(terminal.getTermSvnTemNm())
                                        .termSvnDpmC(terminal.getTermSvnDpmC())
                                        .termSvnDpmNm(terminal.getTermSvnDpmNm())
                                        .rmk(terminal.getRmk())
                                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
