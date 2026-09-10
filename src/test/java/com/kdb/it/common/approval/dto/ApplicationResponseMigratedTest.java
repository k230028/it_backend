package com.kdb.it.common.approval.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kdb.it.common.approval.domain.MigrationApprovalMarker;
import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationResponseMigratedTest {

    /** 이 테스트가 보는 것은 migrated 판정뿐이라 신청자 표시 정보는 해석하지 않은 상태로 둡니다. */
    private static final ApplicationRequesterInfo REQUESTER =
            new ApplicationRequesterInfo(null, null, null, null);

    private ApplicationRepository.ApplicationReadView view(
            String statusCode, String rgprDcdReqCone) {
        ApplicationRepository.ApplicationReadView view =
                mock(ApplicationRepository.ApplicationReadView.class);
        when(view.getApfMngNo()).thenReturn("APF-2026-00000001");
        when(view.getItPtlApfPrgStsC()).thenReturn(statusCode);
        when(view.getRgprDcdReqCone()).thenReturn(rgprDcdReqCone);
        return view;
    }

    private ApplicationRepository.ApplicationReadView view(String rgprDcdReqCone) {
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
    @DisplayName("수기등록 상태는 과거 표식 문구가 없어도 migrated=true")
    void migrated_whenManualStatusWithoutMigrationNote() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromReadViews(
                        view("9", null), List.of(), REQUESTER, Map.of());

        assertThat(response.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("엔티티 변환도 수기등록 상태를 우선해 migrated=true")
    void migrated_whenManualStatusWithoutMigrationNoteFromEntity() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromEntity(application("9", null), List.of());

        assertThat(response.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("이관 표식 문구가 들어 있으면 migrated=true")
    void migrated_whenMigrationNote() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromReadViews(
                        view(MigrationApprovalMarker.NOTE), List.of(), REQUESTER, Map.of());

        assertThat(response.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("일반 신청서는 migrated=false")
    void notMigrated_whenOrdinaryOpinion() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromReadViews(
                        view("검토 부탁드립니다."), List.of(), REQUESTER, Map.of());

        assertThat(response.isMigrated()).isFalse();
    }

    @Test
    @DisplayName("등록자결재요청내용이 null이어도 예외 없이 false")
    void notMigrated_whenNullOpinion() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromReadViews(view(null), List.of(), REQUESTER, Map.of());

        assertThat(response.isMigrated()).isFalse();
    }

    @Test
    @DisplayName("엔티티 변환도 이관 표식 문구가 있으면 migrated=true")
    void migrated_whenMigrationNoteFromEntity() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromEntity(
                        application(MigrationApprovalMarker.NOTE), List.of());

        assertThat(response.isMigrated()).isTrue();
    }

    @Test
    @DisplayName("엔티티 변환도 일반 신청서는 migrated=false")
    void notMigrated_whenOrdinaryOpinionFromEntity() {
        ApplicationDto.Response response =
                ApplicationDto.Response.fromEntity(application("검토 부탁드립니다."), List.of());

        assertThat(response.isMigrated()).isFalse();
    }
}
