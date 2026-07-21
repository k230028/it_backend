package com.kdb.it.domain.bizplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.bizplan.dto.BizplanDto;
import com.kdb.it.domain.bizplan.entity.Bbizcm;
import com.kdb.it.domain.bizplan.entity.Bbizgm;
import com.kdb.it.domain.bizplan.entity.Bbizpm;
import com.kdb.it.domain.bizplan.entity.Bbizsm;
import com.kdb.it.domain.bizplan.repository.BbizcmRepository;
import com.kdb.it.domain.bizplan.repository.BbizgmRepository;
import com.kdb.it.domain.bizplan.repository.BbizsmRepository;
import com.kdb.it.domain.bizplan.repository.BizplanRepository;
import com.kdb.it.domain.budget.plan.repository.BplanaRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bproja;
import com.kdb.it.domain.budget.project.entity.BprojaId;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.BprojaRepository;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.BprojaSyncService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class BizplanServiceTest {

    private static final String PRJ = "PRJ-2026-0001";
    private static final String BIZ_KEY = "BIZ-" + PRJ;

    @Mock BizplanRepository bizplanRepository;
    @Mock BbizsmRepository bbizsmRepository;
    @Mock BbizgmRepository bbizgmRepository;
    @Mock BbizcmRepository bbizcmRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectItemRepository projectItemRepository;
    @Mock BplanaRepository bplanaRepository;
    @Mock BprojaRepository bprojaRepository;
    @Mock BprojaSyncService bprojaSyncService;

    BizplanService service;

    @BeforeEach
    void setUp() {
        service =
                new BizplanService(
                        bizplanRepository,
                        bbizsmRepository,
                        bbizgmRepository,
                        bbizcmRepository,
                        projectRepository,
                        projectItemRepository,
                        bplanaRepository,
                        bprojaRepository,
                        bprojaSyncService);
    }

    /** 주관부서(18001) 사용자 */
    CustomUserDetails deptUser() {
        return new CustomUserDetails("E0001", List.of("ITPZZ001"), "18001");
    }

    /** 타부서(12004) 사용자 */
    CustomUserDetails otherDeptUser() {
        return new CustomUserDetails("E0002", List.of("ITPZZ001"), "12004");
    }

    /** 관리자 */
    CustomUserDetails admin() {
        return new CustomUserDetails("E0099", List.of("ITPAD001"), "99999");
    }

    /** 주관부서 18001의 현재 유효 사업 */
    Bprojm project() {
        return Bprojm.builder()
                .abusMngNo(PRJ)
                .sno(1)
                .abusNm("차세대 시스템 구축")
                .svnDpmC("18001")
                .lstYn("Y")
                .build();
    }

    Bbizpm plan() {
        return Bbizpm.builder().abusMngNo(PRJ).abusNm("차세대 시스템 구축").build();
    }

    /** 공통 스텁: 사업 존재 + BPLANA 포함 */
    void stubEligibleProject() {
        when(projectRepository.findByAbusMngNoAndLstYnAndDelYn(PRJ, "Y", "N"))
                .thenReturn(Optional.of(project()));
        when(bplanaRepository.existsByPrjMngNoAndDelYn(PRJ, "N")).thenReturn(true);
    }

    @Nested
    @DisplayName("getOrCreate — 상세 진입(생성 또는 조회)")
    class GetOrCreateTests {

        @Test
        @DisplayName("BBIZPM이 없으면 생성하고 BPROJA에 21을 upsert하며 BG_NO를 자동 연계한다")
        void createsPlanWithStatus21AndBgNo() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());
            when(bprojaRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(
                            List.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo("BG-2026-0001")
                                            .stsTc("09")
                                            .build()));
            when(bizplanRepository.save(any(Bbizpm.class))).thenAnswer(inv -> inv.getArgument(0));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(Optional.empty());

            BizplanDto.Detail detail = service.getOrCreate(PRJ, deptUser());

            assertThat(detail.abusNm()).isEqualTo("차세대 시스템 구축");
            assertThat(detail.bgNo()).isEqualTo("BG-2026-0001");
            verify(bizplanRepository).save(any(Bbizpm.class));
            verify(bprojaSyncService).upsert(PRJ, BIZ_KEY, "21");
        }

        @Test
        @DisplayName("BBIZPM이 이미 있으면 재생성/상태 upsert 없이 상세만 반환한다(멱등)")
        void idempotentWhenPlanExists() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(
                            Optional.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(BIZ_KEY)
                                            .stsTc("29")
                                            .build()));

            BizplanDto.Detail detail = service.getOrCreate(PRJ, deptUser());

            assertThat(detail.stsTc()).isEqualTo("29");
            verify(bizplanRepository, never()).save(any());
            verify(bprojaSyncService, never()).upsert(any(), any(), any());
        }

        @Test
        @DisplayName("BBIZPM 최초 생성 시 사업(BITEMM 최신·유효본)의 소요예산 품목을 BBIZGM으로 복사한다")
        void seedsItemsFromProjectOnCreate() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());
            // 예산신청 소요예산 상세내용 품목(BITEMM) 최신·유효본 2건
            when(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(PRJ, "N", "Y"))
                    .thenReturn(
                            List.of(
                                    Bitemm.builder()
                                            .gclMngNo("GCL-2026-0001")
                                            .sno(1)
                                            .abusMngNo(PRJ)
                                            .gclNm("서버")
                                            .ioeC("101")
                                            .qty(new BigDecimal("2"))
                                            .curC("KRW")
                                            .amt(new BigDecimal("1000000"))
                                            .build(),
                                    Bitemm.builder()
                                            .gclMngNo("GCL-2026-0001")
                                            .sno(2)
                                            .abusMngNo(PRJ)
                                            .gclNm("SW 라이선스")
                                            .ioeC("201")
                                            .qty(new BigDecimal("1"))
                                            .curC("USD")
                                            .xcr(new BigDecimal("1300"))
                                            .fcAmt(new BigDecimal("500"))
                                            .amt(new BigDecimal("650000"))
                                            .build()));

            service.getOrCreate(PRJ, deptUser());

            // BBIZGM 2건 저장 — SNO는 1부터 순번, 필드는 BITEMM에서 복사(qty는 Long 변환)
            ArgumentCaptor<Bbizgm> itemCaptor = ArgumentCaptor.forClass(Bbizgm.class);
            verify(bbizgmRepository, times(2)).save(itemCaptor.capture());
            List<Bbizgm> saved = itemCaptor.getAllValues();
            assertThat(saved).extracting(item -> item.getSno()).containsExactly(1, 2);
            assertThat(saved).extracting(item -> item.getGclNm()).containsExactly("서버", "SW 라이선스");
            assertThat(saved).extracting(item -> item.getIoeC()).containsExactly("101", "201");
            assertThat(saved.get(0).getQty()).isEqualTo(2L);
            assertThat(saved.get(0).getCurC()).isEqualTo("KRW");
            assertThat(saved.get(1).getCurC()).isEqualTo("USD");
            assertThat(saved.get(1).getFcAmt()).isEqualByComparingTo("500");
            assertThat(saved.get(1).getXcr()).isEqualByComparingTo("1300");

            // 총소요금액 = 복사 품목 amt 합계 (1,000,000 + 650,000)
            ArgumentCaptor<Bbizpm> planCaptor = ArgumentCaptor.forClass(Bbizpm.class);
            verify(bizplanRepository).save(planCaptor.capture());
            assertThat(planCaptor.getValue().getTotRqmAmt()).isEqualByComparingTo("1650000");
        }

        @Test
        @DisplayName("BBIZSM 최초 생성 시 사업(BPROJM)의 시작/종료일자로 기본 일정 1건(일정내용='사업추진')을 시드한다")
        void seedsScheduleFromProjectOnCreate() {
            when(projectRepository.findByAbusMngNoAndLstYnAndDelYn(PRJ, "Y", "N"))
                    .thenReturn(
                            Optional.of(
                                    Bprojm.builder()
                                            .abusMngNo(PRJ)
                                            .sno(1)
                                            .abusNm("차세대 시스템 구축")
                                            .svnDpmC("18001")
                                            .lstYn("Y")
                                            .sttDtm(LocalDate.of(2026, 3, 1))
                                            .endDtm(LocalDate.of(2026, 12, 31))
                                            .build()));
            when(bplanaRepository.existsByPrjMngNoAndDelYn(PRJ, "N")).thenReturn(true);
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());

            service.getOrCreate(PRJ, deptUser());

            ArgumentCaptor<Bbizsm> captor = ArgumentCaptor.forClass(Bbizsm.class);
            verify(bbizsmRepository).save(captor.capture());
            Bbizsm saved = captor.getValue();
            assertThat(saved.getSno()).isEqualTo(1);
            assertThat(saved.getDsdCone()).isEqualTo("사업추진");
            assertThat(saved.getSttDt()).isEqualTo("20260301");
            assertThat(saved.getEndDt()).isEqualTo("20261231");
        }

        @Test
        @DisplayName("BPLANA(정보기술부문 계획)에 포함되지 않은 사업이면 IllegalArgumentException")
        void rejectsProjectNotInPlan() {
            when(projectRepository.findByAbusMngNoAndLstYnAndDelYn(PRJ, "Y", "N"))
                    .thenReturn(Optional.of(project()));
            when(bplanaRepository.existsByPrjMngNoAndDelYn(PRJ, "N")).thenReturn(false);

            assertThatThrownBy(() -> service.getOrCreate(PRJ, deptUser()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("타부서 일반 사용자는 AccessDeniedException, 관리자는 허용")
        void deptGate() {
            stubEligibleProject();
            assertThatThrownBy(() -> service.getOrCreate(PRJ, otherDeptUser()))
                    .isInstanceOf(AccessDeniedException.class);

            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(Optional.empty());
            assertThat(service.getOrCreate(PRJ, admin())).isNotNull();
        }
    }

    @Nested
    @DisplayName("save — 전체 저장(자식 upsert + 합계 재계산)")
    class SaveTests {

        BizplanDto.SaveRequest requestWith(
                List<BizplanDto.ItemRequest> items, List<BizplanDto.ContractRequest> contracts) {
            return new BizplanDto.SaveRequest("<p>보고서</p>", "21", List.of(), items, contracts);
        }

        void stubPlanLoaded() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
        }

        @Test
        @DisplayName("기존 행 갱신 + 요청에 없는 활성 행 soft delete + 총소요금액=활성 품목 합계")
        void mergesRowsAndRecalculatesTotal() {
            stubEligibleProject();
            Bbizpm planRef = plan();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(planRef));
            Bbizgm existing1 =
                    Bbizgm.builder()
                            .abusMngNo(PRJ)
                            .sno(1)
                            .gclNm("서버")
                            .amt(new BigDecimal("100"))
                            .build();
            Bbizgm existing2 =
                    Bbizgm.builder()
                            .abusMngNo(PRJ)
                            .sno(2)
                            .gclNm("삭제될 품목")
                            .amt(new BigDecimal("50"))
                            .build();
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ))
                    .thenReturn(List.of(existing1, existing2));
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());

            BizplanDto.ItemRequest item1 =
                    new BizplanDto.ItemRequest(
                            1,
                            "서버",
                            "101",
                            2L,
                            new BigDecimal("300"),
                            null,
                            "KRW",
                            null,
                            null,
                            null);
            service.save(PRJ, requestWith(List.of(item1), List.of()), deptUser());

            assertThat(existing1.getAmt()).isEqualByComparingTo("300");
            assertThat(existing2.getDelYn()).isEqualTo("Y");
            assertThat(planRef.getTotRqmAmt()).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("신규 행(미존재 SNO)은 INSERT하고 합계에 포함한다")
        void insertsNewRows() {
            stubPlanLoaded();
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());

            BizplanDto.ItemRequest newItem =
                    new BizplanDto.ItemRequest(
                            1,
                            "라이선스",
                            "102",
                            10L,
                            new BigDecimal("500"),
                            null,
                            "KRW",
                            null,
                            null,
                            null);
            service.save(PRJ, requestWith(List.of(newItem), List.of()), deptUser());

            verify(bbizgmRepository).save(any(Bbizgm.class));
        }

        @Test
        @DisplayName("품목 CTT_SNO가 요청 계약 목록에 없으면 IllegalArgumentException")
        void rejectsDanglingContractReference() {
            stubPlanLoaded();
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());

            BizplanDto.ItemRequest bad =
                    new BizplanDto.ItemRequest(
                            1, "서버", "101", 1L, new BigDecimal("100"), null, "KRW", null, null, 9);
            assertThatThrownBy(
                            () ->
                                    service.save(
                                            PRJ, requestWith(List.of(bad), List.of()), deptUser()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("타부서 일반 사용자는 저장할 수 없다")
        void deniedForOtherDept() {
            stubEligibleProject();
            assertThatThrownBy(
                            () ->
                                    service.save(
                                            PRJ,
                                            requestWith(List.of(), List.of()),
                                            otherDeptUser()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("사업계획 미생성 상태에서 저장하면 IllegalArgumentException")
        void rejectsSaveBeforeCreate() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());
            assertThatThrownBy(
                            () -> service.save(PRJ, requestWith(List.of(), List.of()), deptUser()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("complete — 완료(21→29)")
    class CompleteTests {

        void stubPlanLoaded() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
        }

        @Test
        @DisplayName("현재 상태 21이면 29로 upsert한다")
        void completesFrom21() {
            stubPlanLoaded();
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(
                            Optional.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(BIZ_KEY)
                                            .stsTc("21")
                                            .build()));

            service.complete(PRJ, new BizplanDto.StatusRequest("29"), deptUser());

            verify(bprojaSyncService).upsert(PRJ, BIZ_KEY, "29");
        }

        @Test
        @DisplayName("이미 29이면 IllegalStateException")
        void rejectsWhenAlreadyDone() {
            stubPlanLoaded();
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(
                            Optional.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(BIZ_KEY)
                                            .stsTc("29")
                                            .build()));

            assertThatThrownBy(
                            () ->
                                    service.complete(
                                            PRJ, new BizplanDto.StatusRequest("29"), deptUser()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("목표 상태가 29가 아니면 IllegalArgumentException")
        void rejectsInvalidTarget() {
            stubPlanLoaded();
            assertThatThrownBy(
                            () ->
                                    service.complete(
                                            PRJ, new BizplanDto.StatusRequest("21"), deptUser()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("get — 상세 재조회(부수효과 없음)")
    class GetTests {
        @Test
        @DisplayName("사업계획이 있으면 상세를 반환하고 저장/상태 upsert는 하지 않는다")
        void returnsDetailWithoutSideEffects() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(Optional.empty());

            BizplanDto.Detail detail = service.get(PRJ, deptUser());

            assertThat(detail.abusMngNo()).isEqualTo(PRJ);
            verify(bizplanRepository, never()).save(any());
            verify(bprojaSyncService, never()).upsert(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("list — 목록(부서 필터)")
    class ListTests {

        @Test
        @DisplayName("관리자는 전체(bbrC=null), 일반 사용자는 자기 부서로 검색한다")
        void deptFilterByRole() {
            when(bizplanRepository.search(null)).thenReturn(List.of());
            when(bizplanRepository.search("18001")).thenReturn(List.of());

            service.list(admin());
            verify(bizplanRepository).search(null);

            service.list(deptUser());
            verify(bizplanRepository).search("18001");
        }
    }

    @Nested
    @DisplayName("병합·시드·권한 분기 보강")
    class MergeBranchTests {

        BizplanDto.SaveRequest req(
                List<BizplanDto.ScheduleRequest> schedules,
                List<BizplanDto.ItemRequest> items,
                List<BizplanDto.ContractRequest> contracts) {
            return new BizplanDto.SaveRequest("<p>보고서</p>", "01", schedules, items, contracts);
        }

        void stubPlanLoaded() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
        }

        @Test
        @DisplayName("소프트 삭제된 일정·품목·계약 행은 같은 SNO 재요청 시 복원(restore)된다")
        void restoresSoftDeletedRows() {
            stubPlanLoaded();
            Bbizsm delSchedule =
                    Bbizsm.builder().abusMngNo(PRJ).sno(1).dsdCone("old").delYn("Y").build();
            Bbizgm delItem =
                    Bbizgm.builder()
                            .abusMngNo(PRJ)
                            .sno(1)
                            .gclNm("old")
                            .amt(new BigDecimal("10"))
                            .delYn("Y")
                            .build();
            Bbizcm delContract =
                    Bbizcm.builder().abusMngNo(PRJ).sno(1).cttNm("old").delYn("Y").build();
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ))
                    .thenReturn(List.of(delSchedule));
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of(delItem));
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ))
                    .thenReturn(List.of(delContract));

            BizplanDto.ScheduleRequest s =
                    new BizplanDto.ScheduleRequest(1, "구축", "20260301", "20261231");
            BizplanDto.ItemRequest i =
                    new BizplanDto.ItemRequest(
                            1, "서버", "101", 1L, new BigDecimal("100"), null, "KRW", null, null, 1);
            BizplanDto.ContractRequest c = new BizplanDto.ContractRequest(1, "유지보수", "01", 12);
            service.save(PRJ, req(List.of(s), List.of(i), List.of(c)), deptUser());

            assertThat(delSchedule.getDelYn()).isEqualTo("N");
            assertThat(delSchedule.getDsdCone()).isEqualTo("구축");
            assertThat(delItem.getDelYn()).isEqualTo("N");
            assertThat(delContract.getDelYn()).isEqualTo("N");
        }

        @Test
        @DisplayName("일정: 기존 갱신 + 신규 INSERT + 요청 누락 활성행 soft delete")
        void mergesSchedules() {
            stubPlanLoaded();
            Bbizsm existing = Bbizsm.builder().abusMngNo(PRJ).sno(1).dsdCone("old").build();
            Bbizsm toDelete = Bbizsm.builder().abusMngNo(PRJ).sno(9).dsdCone("삭제대상").build();
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ))
                    .thenReturn(List.of(existing, toDelete));
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());

            BizplanDto.ScheduleRequest upd =
                    new BizplanDto.ScheduleRequest(1, "갱신", "20260101", "20260601");
            BizplanDto.ScheduleRequest neo = new BizplanDto.ScheduleRequest(2, "신규", null, null);
            service.save(PRJ, req(List.of(upd, neo), List.of(), List.of()), deptUser());

            assertThat(existing.getDsdCone()).isEqualTo("갱신");
            assertThat(toDelete.getDelYn()).isEqualTo("Y");
            verify(bbizsmRepository).save(any(Bbizsm.class)); // 신규 1건
        }

        @Test
        @DisplayName("계약: 기존 갱신 + 신규 INSERT + 요청 누락 활성행 soft delete")
        void mergesContracts() {
            stubPlanLoaded();
            Bbizcm keep = Bbizcm.builder().abusMngNo(PRJ).sno(1).cttNm("old").build();
            Bbizcm drop = Bbizcm.builder().abusMngNo(PRJ).sno(2).cttNm("삭제").build();
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ))
                    .thenReturn(List.of(keep, drop));

            BizplanDto.ContractRequest upd = new BizplanDto.ContractRequest(1, "유지보수", "01", 24);
            BizplanDto.ContractRequest neo = new BizplanDto.ContractRequest(3, "신규계약", "02", 12);
            service.save(PRJ, req(List.of(), List.of(), List.of(upd, neo)), deptUser());

            assertThat(keep.getCttNm()).isEqualTo("유지보수");
            assertThat(drop.getDelYn()).isEqualTo("Y");
            verify(bbizcmRepository).save(any(Bbizcm.class)); // 신규 1건
        }

        @Test
        @DisplayName("품목 SNO가 중복이면 IllegalArgumentException")
        void rejectsDuplicateItemSno() {
            stubPlanLoaded();
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());

            BizplanDto.ItemRequest a =
                    new BizplanDto.ItemRequest(
                            1, "A", "101", 1L, new BigDecimal("1"), null, "KRW", null, null, null);
            BizplanDto.ItemRequest b =
                    new BizplanDto.ItemRequest(
                            1, "B", "101", 1L, new BigDecimal("1"), null, "KRW", null, null, null);
            assertThatThrownBy(
                            () ->
                                    service.save(
                                            PRJ,
                                            req(List.of(), List.of(a, b), List.of()),
                                            deptUser()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("BPROJA 상태행이 소프트 삭제 상태면 현재 상태를 21(작성중)로 간주한다")
        void currentStatusTreatsDeletedAsInProgress() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(Optional.of(plan()));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(
                            Optional.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(BIZ_KEY)
                                            .stsTc("29")
                                            .delYn("Y")
                                            .build()));

            BizplanDto.Detail detail = service.get(PRJ, deptUser());

            assertThat(detail.stsTc()).isEqualTo("21");
        }

        @Test
        @DisplayName("인증 정보(null)면 AccessDeniedException")
        void nullUserDenied() {
            stubEligibleProject();
            assertThatThrownBy(() -> service.get(PRJ, null))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("주관부서 코드가 없고 비관리자면 AccessDeniedException")
        void nullSvnDpmCDeniedForNonAdmin() {
            when(projectRepository.findByAbusMngNoAndLstYnAndDelYn(PRJ, "Y", "N"))
                    .thenReturn(
                            Optional.of(
                                    Bprojm.builder()
                                            .abusMngNo(PRJ)
                                            .sno(1)
                                            .abusNm("사업")
                                            .lstYn("Y")
                                            .build()));
            when(bplanaRepository.existsByPrjMngNoAndDelYn(PRJ, "N")).thenReturn(true);

            assertThatThrownBy(() -> service.get(PRJ, deptUser()))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("예산편성(BG-) 키가 없으면 생성 시 BG_NO는 null이고 일정 시드 일자는 비운다")
        void resolvesBgNoNullAndSeedsScheduleWithNullDates() {
            stubEligibleProject(); // project()는 시작/종료일자 없음 → toYmd(null) 분기
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());
            when(bprojaRepository.findByAbusMngNoAndDelYn(PRJ, "N"))
                    .thenReturn(
                            List.of(
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(BIZ_KEY)
                                            .stsTc("21")
                                            .build(), // BIZ-(BG- 아님)
                                    Bproja.builder()
                                            .abusMngNo(PRJ)
                                            .cncdRfrNo(null)
                                            .stsTc("09")
                                            .build())); // null 키
            when(bizplanRepository.save(any(Bbizpm.class))).thenAnswer(inv -> inv.getArgument(0));
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(Optional.empty());

            BizplanDto.Detail detail = service.getOrCreate(PRJ, deptUser());

            assertThat(detail.bgNo()).isNull();
            ArgumentCaptor<Bbizsm> captor = ArgumentCaptor.forClass(Bbizsm.class);
            verify(bbizsmRepository).save(captor.capture());
            assertThat(captor.getValue().getSttDt()).isNull();
            assertThat(captor.getValue().getEndDt()).isNull();
        }

        @Test
        @DisplayName("시드 품목의 수량/금액이 null이면 수량은 null로 복사하고 총액은 0으로 설정한다")
        void seedsItemsWithNullQtyAndAmount() {
            stubEligibleProject();
            when(bizplanRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(Optional.empty());
            when(bprojaRepository.findByAbusMngNoAndDelYn(PRJ, "N")).thenReturn(List.of());
            when(bizplanRepository.save(any(Bbizpm.class))).thenAnswer(inv -> inv.getArgument(0));
            when(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(PRJ, "N", "Y"))
                    .thenReturn(
                            List.of(
                                    Bitemm.builder()
                                            .gclMngNo("GCL-1")
                                            .sno(1)
                                            .abusMngNo(PRJ)
                                            .gclNm("무상지원")
                                            .ioeC("101")
                                            .build())); // qty/amt null
            when(bbizgmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizsmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bbizcmRepository.findByAbusMngNoOrderBySnoAsc(PRJ)).thenReturn(List.of());
            when(bprojaRepository.findById(new BprojaId(PRJ, BIZ_KEY)))
                    .thenReturn(Optional.empty());

            service.getOrCreate(PRJ, deptUser());

            ArgumentCaptor<Bbizgm> itemCaptor = ArgumentCaptor.forClass(Bbizgm.class);
            verify(bbizgmRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getQty()).isNull();

            ArgumentCaptor<Bbizpm> planCaptor = ArgumentCaptor.forClass(Bbizpm.class);
            verify(bizplanRepository).save(planCaptor.capture());
            assertThat(planCaptor.getValue().getTotRqmAmt()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("selectLatestBgKey — BG- 키 결정적 선택 (BE-09)")
    class SelectLatestBgKeyTests {

        private Bproja app(String cncdRfrNo) {
            return Bproja.builder().abusMngNo(PRJ).cncdRfrNo(cncdRfrNo).stsTc("09").build();
        }

        @Test
        @DisplayName("BG- 키가 없으면 null을 반환한다")
        void noBgKey_returnsNull() {
            assertThat(BizplanService.selectLatestBgKey(PRJ, List.of(app("BIZ-" + PRJ)))).isNull();
        }

        @Test
        @DisplayName("BG- 키 1건이면 그 키를 반환한다")
        void singleBgKey_returned() {
            assertThat(
                            BizplanService.selectLatestBgKey(
                                    PRJ, List.of(app("BG-2026-0001"), app("BIZ-" + PRJ))))
                    .isEqualTo("BG-2026-0001");
        }

        @Test
        @DisplayName("BG- 키 다건이면 채번 키 내림차순으로 최신 키를 선택한다 (WARN 경로)")
        void multipleBgKeys_latestSelected() {
            assertThat(
                            BizplanService.selectLatestBgKey(
                                    PRJ, List.of(app("BG-2026-0001"), app("BG-2026-0002"))))
                    .isEqualTo("BG-2026-0002");
        }

        @Test
        @DisplayName("입력 순서를 뒤집어도 같은 최신 채번 키를 선택한다")
        void inputOrder_independent() {
            assertThat(
                            BizplanService.selectLatestBgKey(
                                    PRJ, List.of(app("BG-2026-0010"), app("BG-2026-0009"))))
                    .isEqualTo(
                            BizplanService.selectLatestBgKey(
                                    PRJ, List.of(app("BG-2026-0009"), app("BG-2026-0010"))));
        }

        @Test
        @DisplayName("cncdRfrNo가 null인 행은 무시한다")
        void nullKey_ignored() {
            assertThat(
                            BizplanService.selectLatestBgKey(
                                    PRJ, List.of(app(null), app("BG-2026-0001"))))
                    .isEqualTo("BG-2026-0001");
        }
    }
}
