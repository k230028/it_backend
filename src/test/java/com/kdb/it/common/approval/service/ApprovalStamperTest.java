package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 이관용 결재완료 받이 생성 규칙을 고정합니다 (§3.6). */
@ExtendWith(MockitoExtension.class)
class ApprovalStamperTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;
    @InjectMocks private ApprovalStamper stamper;

    @Captor private ArgumentCaptor<Capplm> capplmCaptor;
    @Captor private ArgumentCaptor<Cappla> capplaCaptor;

    @Test
    @DisplayName("신청서 번호는 기존 APF-{연도}-{8자리} 형식을 그대로 쓴다")
    void 신청서번호는_기존형식을_쓴다() {
        when(applicationRepository.getNextVal()).thenReturn(42L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        String apfNo = stamper.stamp("BCOSTM", "COST-2026-0001", 1, "이관", "999999", "2026");

        assertThat(apfNo).isEqualTo("APF-2026-00000042");
    }

    @Test
    @DisplayName("상태는 결재완료('2')이고 요청자는 업로드 사용자다")
    void 결재완료상태로_생성한다() {
        when(applicationRepository.getNextVal()).thenReturn(1L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp("BCOSTM", "COST-2026-0001", 1, "2026년 전산업무비 이관", "999999", "2026");

        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        Capplm saved = capplmCaptor.getValue();
        assertThat(saved.getItPtlApfPrgStsC()).isEqualTo(ApprovalStatus.COMPLETED.code());
        assertThat(saved.getDcdReqUsid()).isEqualTo("999999");
        assertThat(saved.getDcdReqTtl()).contains("이관");
    }

    @Test
    @DisplayName("호출자가 지정한 수기등록 상태('9')로 신청서를 만든다")
    void 지정한_수기등록상태로_생성한다() {
        when(applicationRepository.getNextVal()).thenReturn(2L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp(
                "BPROJM", "PRJ-2026-0002", 1, "편성요청서 반입", "999999", "2026", ApprovalStatus.MANUAL);

        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        assertThat(capplmCaptor.getValue().getItPtlApfPrgStsC()).isEqualTo("9");
    }

    @Test
    @DisplayName("원천 연결 CAPPLA를 같은 신청서번호로 만든다")
    void 원천연결을_만든다() {
        when(applicationRepository.getNextVal()).thenReturn(7L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp("BPROJM", "PRJ-2026-0001", 1, "이관", "999999", "2026");

        org.mockito.Mockito.verify(applicationMapRepository).save(capplaCaptor.capture());
        Cappla saved = capplaCaptor.getValue();
        assertThat(saved.getApfDcmNo()).isEqualTo("APF-2026-00000007");
        assertThat(saved.getFntTbNm()).isEqualTo("BPROJM");
        assertThat(saved.getPkColNm()).isEqualTo("PRJ-2026-0001");
        assertThat(saved.getFntTbCrySno()).isEqualTo(1);
    }

    @Test
    @DisplayName("이관 표시를 등록자결재요청내용에 남긴다")
    void 이관표시를_남긴다() {
        when(applicationRepository.getNextVal()).thenReturn(1L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp("BCOSTM", "COST-2026-0001", 1, "이관", "999999", "2026");

        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        assertThat(capplmCaptor.getValue().getRgprDcdReqCone())
                .isEqualTo(MigrationApprovalMarker.NOTE)
                .isEqualTo("수기등록")
                .doesNotContain("MIG-");
    }

    @Test
    @DisplayName("인증 컨텍스트 없이도 감사자 필드를 업로드 사용자 사번으로 직접 채운다")
    void 감사자필드를_업로드사용자로_채운다() {
        when(applicationRepository.getNextVal()).thenReturn(3L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp("BPROJM", "PRJ-2026-0002", 1, "이관", "999999", "2026");

        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        org.mockito.Mockito.verify(applicationMapRepository).save(capplaCaptor.capture());

        // SecurityContext가 없는 배치 실행에서도 TPRMPP_CAPPLM/TPRMPP_CAPPLA의 물리 NOT NULL인
        // 최초등록자·최종변경자가 비어 ORA-01400이 나지 않도록, JPA Auditing에 기대지 않고
        // actorEno로 직접 채웠는지 고정한다.
        Capplm savedApplication = capplmCaptor.getValue();
        assertThat(savedApplication.getFstEnrUsid()).isEqualTo("999999");
        assertThat(savedApplication.getLstChgUsid()).isEqualTo("999999");

        Cappla savedApplicationMap = capplaCaptor.getValue();
        assertThat(savedApplicationMap.getFstEnrUsid()).isEqualTo("999999");
        assertThat(savedApplicationMap.getLstChgUsid()).isEqualTo("999999");
    }

    @Test
    @DisplayName("작성완료 스탬프: 연결된 신청서가 없으면 결재선 없는 0 신청서를 새로 만든다")
    void 작성완료_신규생성() {
        when(applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BPROJM", "PRJ-2026-0001", 1))
                .thenReturn(List.of());
        when(applicationRepository.getNextVal()).thenReturn(5L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        String apfNo =
                stamper.stampDrafted("BPROJM", "PRJ-2026-0001", 1, "사업A", "K10001", "D001", "2026");

        assertThat(apfNo).isEqualTo("APF-2026-00000005");
        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        Capplm saved = capplmCaptor.getValue();
        assertThat(saved.getItPtlApfPrgStsC()).isEqualTo(ApprovalStatus.DRAFTED.code());
        assertThat(saved.getDcdReqTtl()).isEqualTo("사업A");
        assertThat(saved.getDcdReqBbrC()).isEqualTo("D001");
        assertThat(saved.getDcdReqDtm()).isNull();
        assertThat(saved.getRgprDcdReqCone()).isNull();
        assertThat(saved.getFstEnrUsid()).isEqualTo("K10001");
    }

    @Test
    @DisplayName("작성완료 스탬프: 최신 신청서가 이미 0이면 새로 만들지 않고 제목만 갱신한다")
    void 작성완료_멱등갱신() {
        Cappla link = Cappla.builder().apfDcmNo("APF-2026-00000003").fntTbNm("BPROJM").build();
        Capplm existing =
                Capplm.builder()
                        .apfMngNo("APF-2026-00000003")
                        .itPtlApfPrgStsC(ApprovalStatus.DRAFTED.code())
                        .dcdReqTtl("옛 제목")
                        .build();
        when(applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BPROJM", "PRJ-2026-0001", 1))
                .thenReturn(List.of(link));
        when(applicationRepository.findById("APF-2026-00000003")).thenReturn(Optional.of(existing));

        String apfNo =
                stamper.stampDrafted(
                        "BPROJM", "PRJ-2026-0001", 1, "새 제목", "K10001", "D001", "2026");

        assertThat(apfNo).isEqualTo("APF-2026-00000003");
        assertThat(existing.getDcdReqTtl()).isEqualTo("새 제목");
        org.mockito.Mockito.verify(applicationRepository, org.mockito.Mockito.never())
                .save(any(Capplm.class));
    }

    @Test
    @DisplayName("작성완료 스탬프: 최신 신청서가 결재중이면 저장을 거부한다")
    void 작성완료_결재중_거부() {
        Cappla link = Cappla.builder().apfDcmNo("APF-2026-00000004").fntTbNm("BPROJM").build();
        Capplm inProgress =
                Capplm.builder()
                        .apfMngNo("APF-2026-00000004")
                        .itPtlApfPrgStsC(ApprovalStatus.IN_PROGRESS.code())
                        .build();
        when(applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BPROJM", "PRJ-2026-0001", 1))
                .thenReturn(List.of(link));
        when(applicationRepository.findById("APF-2026-00000004"))
                .thenReturn(Optional.of(inProgress));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                stamper.stampDrafted(
                                        "BPROJM",
                                        "PRJ-2026-0001",
                                        1,
                                        "제목",
                                        "K10001",
                                        "D001",
                                        "2026"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("결재중");
    }

    @Test
    @DisplayName("작성완료 스탬프: 최신 신청서가 반려면 새 0 신청서를 만든다")
    void 작성완료_반려후_신규생성() {
        Cappla link = Cappla.builder().apfDcmNo("APF-2026-00000002").fntTbNm("BCOSTM").build();
        Capplm rejected =
                Capplm.builder()
                        .apfMngNo("APF-2026-00000002")
                        .itPtlApfPrgStsC(ApprovalStatus.REJECTED.code())
                        .build();
        when(applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BCOSTM", "COST-2026-0001", 2))
                .thenReturn(List.of(link));
        when(applicationRepository.findById("APF-2026-00000002")).thenReturn(Optional.of(rejected));
        when(applicationRepository.getNextVal()).thenReturn(9L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        String apfNo =
                stamper.stampDrafted("BCOSTM", "COST-2026-0001", 2, null, "K10001", "D001", null);

        assertThat(apfNo).startsWith("APF-").endsWith("00000009");
        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        // 제목이 비면 관리번호로 대신하고, 연도가 비면 올해를 쓴다
        assertThat(capplmCaptor.getValue().getDcdReqTtl()).isEqualTo("COST-2026-0001");
    }

    @Test
    @DisplayName("작성완료 스탬프: 원천 예산연도가 내년이어도 신청서번호 연도부는 항상 현재 연도다(이후 상신 번호와의 시간순 보장)")
    void 작성완료_예산연도가_미래여도_현재연도로_채번한다() {
        when(applicationMapRepository.findByFntTbNmAndPkColNmAndFntTbCrySnoOrderByApfDcmNoDesc(
                        "BPROJM", "PRJ-미래-0001", 1))
                .thenReturn(List.of());
        when(applicationRepository.getNextVal()).thenReturn(11L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        // 9월 이후 기본 예산연도는 내년이 된다. bseYy에 (현재 연도+1)을 실어도 채번 연도부는
        // 현재 연도여야 한다 — 그래야 이후 ApplicationService#submit이 현재 연도로 채번하는
        // 상신 번호가 이 저장 번호보다 사전식으로 더 커진다.
        String futureBseYy = String.valueOf(java.time.LocalDate.now().getYear() + 1);

        String apfNo =
                stamper.stampDrafted(
                        "BPROJM", "PRJ-미래-0001", 1, "사업B", "K10001", "D001", futureBseYy);

        String currentYear = String.valueOf(java.time.LocalDate.now().getYear());
        assertThat(apfNo).isEqualTo("APF-" + currentYear + "-00000011");
    }
}
