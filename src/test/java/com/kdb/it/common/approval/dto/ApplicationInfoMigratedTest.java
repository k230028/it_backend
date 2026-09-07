package com.kdb.it.common.approval.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 정보화사업·경상사업·전산업무비 상세 응답의 이관 표식 노출을 검증합니다.
 *
 * <p>상세 화면의 수기등록 안내 다이얼로그가 이 플래그 하나에 걸려 있으므로, 플래그가 빠지면 안내가 조용히 사라집니다.
 */
class ApplicationInfoMigratedTest {

    private ApplicationRepository.ApplicationSummaryView view(
            String statusCode, String rgprDcdReqCone) {
        ApplicationRepository.ApplicationSummaryView view =
                mock(ApplicationRepository.ApplicationSummaryView.class);
        when(view.getApfMngNo()).thenReturn("APF-2026-00000001");
        when(view.getItPtlApfPrgStsC()).thenReturn(statusCode);
        when(view.getRgprDcdReqCone()).thenReturn(rgprDcdReqCone);
        return view;
    }

    private ApplicationRepository.ApplicationSummaryView view(String rgprDcdReqCone) {
        return view("2", rgprDcdReqCone);
    }

    private Capplm application(String statusCode, String rgprDcdReqCone) {
        return Capplm.builder()
                .apfMngNo("APF-2026-00000001")
                .itPtlApfPrgStsC(statusCode)
                .rgprDcdReqCone(rgprDcdReqCone)
                .build();
    }

    private Capplm application(String rgprDcdReqCone) {
        return application("2", rgprDcdReqCone);
    }

    @Test
    @DisplayName("요약 view 변환: 수기등록 상태는 과거 표식 문구가 없어도 migrated=true")
    void migrated_whenManualStatusWithoutMigrationNote() {
        ApplicationInfoDto info =
                ApplicationInfoDto.fromReadViews(view("9", null), List.of());

        assertThat(info.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("엔티티 변환: 수기등록 상태는 과거 표식 문구가 없어도 migrated=true")
    void migrated_whenManualStatusWithoutMigrationNoteFromEntity() {
        ApplicationInfoDto info =
                ApplicationInfoDto.fromEntities(application("9", null), List.of());

        assertThat(info.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("요약 view 변환: 이관 표식 문구가 있으면 migrated=true")
    void migrated_whenMigrationNote() {
        ApplicationInfoDto info =
                ApplicationInfoDto.fromReadViews(view(MigrationApprovalMarker.NOTE), List.of());

        assertThat(info.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("요약 view 변환: 일반 신청서는 migrated=false")
    void notMigrated_whenOrdinaryOpinion() {
        ApplicationInfoDto info = ApplicationInfoDto.fromReadViews(view("검토 부탁드립니다."), List.of());

        assertThat(info.isMigrated()).isFalse();
    }

    @Test
    @DisplayName("요약 view 변환: 등록자결재요청내용이 null이어도 예외 없이 false")
    void notMigrated_whenNullOpinion() {
        ApplicationInfoDto info = ApplicationInfoDto.fromReadViews(view(null), List.of());

        assertThat(info.isMigrated()).isFalse();
    }

    @Test
    @DisplayName("엔티티 변환: 이관 표식 문구가 있으면 migrated=true")
    void migrated_whenMigrationNoteFromEntity() {
        ApplicationInfoDto info =
                ApplicationInfoDto.fromEntities(
                        application(MigrationApprovalMarker.NOTE), List.of());

        assertThat(info.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("엔티티 변환: 일반 신청서는 migrated=false")
    void notMigrated_whenOrdinaryOpinionFromEntity() {
        ApplicationInfoDto info =
                ApplicationInfoDto.fromEntities(application("검토 부탁드립니다."), List.of());

        assertThat(info.isMigrated()).isFalse();
    }
}
