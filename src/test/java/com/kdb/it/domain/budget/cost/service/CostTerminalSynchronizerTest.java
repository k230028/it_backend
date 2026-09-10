package com.kdb.it.domain.budget.cost.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.Btermm;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CostTerminalSynchronizer 이관(반입) 경로 단위 테스트
 *
 * <p>{@code preserveSubmittedAmounts=true}인 이관 경로는 기본키 대신 업무 필드로 기존 단말을 찾고, 요청에 없는 기존 행을 지우지 않습니다.
 * 사용자 수정 경로는 {@code CostServiceTest} 계열이 이미 다루므로 여기서는 이관 경로의 매칭 규칙과 빈 값 보존만 검증합니다. DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
class CostTerminalSynchronizerTest {

    private static final String COST_BG_NO = "COST-2026-001";
    private static final int BG_SNO = 2;

    @Mock private BtermmRepository btermmRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private OrgNameResolver orgNameResolver;
    @Mock private XcrLookupService xcrLookupService;
    @Mock private CostNameSnapshotResolver nameResolver;

    @InjectMocks private CostTerminalSynchronizer synchronizer;

    private static Bcostm parent() {
        return Bcostm.builder().costBgNo(COST_BG_NO).bgSno(BG_SNO).build();
    }

    private static Btermm.BtermmBuilder<?, ?> existingBuilder() {
        return Btermm.builder()
                .tmnMngNo("TER-2026-0001")
                .sno(1)
                .termBgNo(COST_BG_NO)
                .termBgSno(BG_SNO)
                .spfTmnNm("대면업무용 단말기")
                .tmnKdTc("01")
                .tmnClsfC("S1")
                .cgprId("10001")
                .cgprNm("홍길동")
                .termSvnDpmC("001")
                .svnDpmNm("정보화부")
                .termSvnTemC("00101")
                .svnTemNm("기획팀")
                .delYn("N");
    }

    private static CostDto.TerminalDto.TerminalDtoBuilder requestedBuilder() {
        return CostDto.TerminalDto.builder()
                .spfTmnNm("대면업무용 단말기")
                .tmnKdTc("01")
                .tmnClsfC("S1")
                .curC("KRW");
    }

    private static CostDto.UpdateRequest request(String bseYy, CostDto.TerminalDto... terminals) {
        return CostDto.UpdateRequest.builder().bseYy(bseYy).terminals(List.of(terminals)).build();
    }

    private void givenExisting(Btermm... terminals) {
        when(btermmRepository.findByTermBgNoAndTermBgSnoAndDelYn(COST_BG_NO, BG_SNO, "N"))
                .thenReturn(new java.util.ArrayList<>(List.of(terminals)));
    }

    @Test
    @DisplayName("이관 요청은 담당자 코드·조직명으로 기본키 없는 행을 기존 단말에 이어 붙인다")
    void sync_migration_matchesExistingByCodeAndName() {
        when(cuserIRepository.findByEnoIn(anyCollection())).thenReturn(List.of());
        Btermm existing = existingBuilder().build();
        givenExisting(existing);

        /* 담당자는 코드로, 담당부서는 코드 없이 이름으로, 담당팀은 코드·이름이 모두 없어 비교를 건너뛴다. */
        CostDto.TerminalDto requested =
                requestedBuilder().cgprId("10001").termSvnDpmNm("정보화부").nsfUsgCone("창구 업무").build();

        synchronizer.sync(parent(), request("2026", requested), true);

        verify(btermmRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getNsfUsgCone()).isEqualTo("창구 업무");
        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("이관 요청의 빈 담당자·조직 코드는 기존 단말 값을 유지한다")
    void sync_migration_keepsExistingIdentityWhenRequestIsBlank() {
        Btermm existing = existingBuilder().build();
        givenExisting(existing);

        CostDto.TerminalDto requested =
                requestedBuilder().cgprNm("홍길동").termSvnDpmNm("정보화부").termSvnTemNm("기획팀").build();

        synchronizer.sync(parent(), request("2026", requested), true);

        verify(btermmRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(existing.getCgprId()).isEqualTo("10001");
        assertThat(existing.getTermSvnDpmC()).isEqualTo("001");
        assertThat(existing.getTermSvnTemC()).isEqualTo("00101");
        assertThat(existing.getCgprNm()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("단말기명·이용방법·서비스가 하나라도 다르면 기존 행에 이어 붙이지 않는다")
    void sync_migration_doesNotMatchWhenBusinessFieldDiffers() {
        Btermm existing = existingBuilder().build();
        givenExisting(existing);
        when(btermmRepository.getNextSequenceValue()).thenReturn(7L, 8L, 9L);

        CostDto.TerminalDto otherName = requestedBuilder().spfTmnNm("비대면 단말기").build();
        CostDto.TerminalDto otherKind = requestedBuilder().tmnKdTc("02").build();
        CostDto.TerminalDto otherService = requestedBuilder().tmnClsfC("S2").build();

        synchronizer.sync(parent(), request("2027", otherName, otherKind, otherService), true);

        ArgumentCaptor<Btermm> saved = ArgumentCaptor.forClass(Btermm.class);
        verify(btermmRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(Btermm::getTmnMngNo)
                .containsExactly("TER-2027-0007", "TER-2027-0008", "TER-2027-0009");
        /* 이관 경로는 요청에 없는 기존 행을 논리 삭제하지 않는다. */
        assertThat(existing.getDelYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("같은 업무 필드가 두 건이면 기존 단말을 한 번씩만 나눠 쓴다")
    void sync_migration_doesNotReuseAlreadyMatchedTerminal() {
        Btermm first = existingBuilder().build();
        Btermm second = existingBuilder().tmnMngNo("TER-2026-0002").sno(2).build();
        givenExisting(first, second);

        CostDto.TerminalDto left = requestedBuilder().nsfUsgCone("첫 번째").build();
        CostDto.TerminalDto right = requestedBuilder().nsfUsgCone("두 번째").build();

        synchronizer.sync(parent(), request("2026", left, right), true);

        verify(btermmRepository, never()).save(org.mockito.ArgumentMatchers.any());
        assertThat(first.getNsfUsgCone()).isEqualTo("첫 번째");
        assertThat(second.getNsfUsgCone()).isEqualTo("두 번째");
    }
}
