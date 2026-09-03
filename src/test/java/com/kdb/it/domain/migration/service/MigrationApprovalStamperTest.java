package com.kdb.it.domain.migration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
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
class MigrationApprovalStamperTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private ApplicationMapRepository applicationMapRepository;
    @InjectMocks private MigrationApprovalStamper stamper;

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
    @DisplayName("호출자가 지정한 수기등록 상태('0')로 신청서를 만든다")
    void 지정한_수기등록상태로_생성한다() {
        when(applicationRepository.getNextVal()).thenReturn(2L);
        when(applicationRepository.save(any(Capplm.class))).thenAnswer(i -> i.getArgument(0));

        stamper.stamp(
                "BPROJM", "PRJ-2026-0002", 1, "편성요청서 반입", "999999", "2026", ApprovalStatus.MANUAL);

        org.mockito.Mockito.verify(applicationRepository).save(capplmCaptor.capture());
        assertThat(capplmCaptor.getValue().getItPtlApfPrgStsC()).isEqualTo("0");
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
}
