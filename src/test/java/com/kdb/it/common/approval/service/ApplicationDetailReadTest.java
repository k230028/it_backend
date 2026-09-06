package com.kdb.it.common.approval.service;

import static com.kdb.it.common.approval.itbudget.service.StoredSnapshotFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.dto.ApplicationDto;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationRepository;
import com.kdb.it.common.approval.repository.ApproverRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.exception.DataCorruptionException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationDetailReadTest {
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ApplicationMapRepository maps = mock(ApplicationMapRepository.class);
    private final ApplicationService service =
            new ApplicationService(
                    applications,
                    mock(ApproverRepository.class),
                    maps,
                    null,
                    null,
                    mock(UserRepository.class),
                    mock(OrganizationRepository.class),
                    null,
                    null,
                    null,
                    null,
                    null,
                    new ApprovalDetailPolicy(maps),
                    reader());

    @Test
    void validLegacyAndV2ReturnExactStoredBytesWithoutPolicyQueries() throws Exception {
        for (String raw :
                List.of(
                        " {\"form\":{\"id\":\"it-budget\",\"version\":1},\"custom\":true} ",
                        "{\"form\":{\"id\":\"council\"}}",
                        v2().toString())) {
            view(raw);
            assertThat(service.getApfDtlCone("A1").getApfDtlCone()).isEqualTo(raw);
        }
        verifyNoInteractions(maps);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASCTM", "BASKPM"})
    void explicitCouncilMayHaveNullDetail(String table) {
        view(null);
        source(table);
        assertThat(service.getApfDtlCone("A1").getApfDtlCone()).isNull();
        verify(maps).findDetailSourcesByApplicationIds(List.of("A1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BPROJM", "BCOSTM", "OTHER"})
    void otherSourcesCannotReturnNullDetail(String table) {
        view(null);
        source(table);
        assertThatThrownBy(() -> service.getApfDtlCone("A1"))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void missingSourceAndBlankDetailFailClosed() {
        view(null);
        assertThatThrownBy(() -> service.getApfDtlCone("A1"))
                .isInstanceOf(DataCorruptionException.class);
        view(" ");
        assertThatThrownBy(() -> service.getApfDtlCone("A1"))
                .isInstanceOf(DataCorruptionException.class);
    }

    @Test
    void nullDetailListsUseOneSourceBatchQuery() {
        var first = view(null);
        var second = mock(ApplicationRepository.ApplicationReadView.class);
        when(second.getApfMngNo()).thenReturn("A2");
        when(applications.findTop500ByItPtlApfPrgStsCNotInOrderByApfMngNoDesc(any()))
                .thenReturn(List.of(first, second));
        var sources =
                java.util.stream.Stream.of("A1", "A2")
                        .map(
                                id -> {
                                    var source =
                                            mock(ApplicationMapRepository.DetailSourceView.class);
                                    when(source.getApfDcmNo()).thenReturn(id);
                                    when(source.getFntTbNm()).thenReturn("BASCTM");
                                    when(source.getPkColNm()).thenReturn("C1");
                                    when(source.getFntTbCrySno()).thenReturn(null);
                                    return source;
                                })
                        .toList();
        when(maps.findDetailSourcesByApplicationIds(List.of("A1", "A2"))).thenReturn(sources);
        assertThat(service.getApplications())
                .extracting(ApplicationDto.Response::getApfMngNo)
                .containsExactly("A1", "A2");
        verify(maps).findDetailSourcesByApplicationIds(List.of("A1", "A2"));
        verifyNoMoreInteractions(maps);
    }

    @Test
    void allRawDetailEndpointsRejectCorruptPayload() throws Exception {
        var root = v2();
        object(root, "/payload/summary").put("total", "999.000");
        var view = view(root.toString());
        when(applications.findTop500ByItPtlApfPrgStsCNotInOrderByApfMngNoDesc(any()))
                .thenReturn(List.of(view));
        when(applications.findPendingApfMngNosByEno("U1")).thenReturn(List.of("A1"));
        when(applications.findReadViewsByApfMngNoIn(any())).thenReturn(List.of(view));
        var request = new ApplicationDto.BulkGetRequest();
        request.setApfMngNos(List.of("A1"));
        for (org.assertj.core.api.ThrowableAssert.ThrowingCallable call :
                List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                        () -> service.getApfDtlCone("A1"),
                        () -> service.getApplication("A1"),
                        () -> service.getApplications(),
                        () -> service.getPendingApplications("U1"),
                        () -> service.getApplicationsByIds(request))) {
            assertThatThrownBy(call).isInstanceOf(DataCorruptionException.class).hasNoCause();
        }
        verifyNoInteractions(maps);
    }

    private ApplicationRepository.ApplicationReadView view(String raw) {
        var view = mock(ApplicationRepository.ApplicationReadView.class);
        when(view.getApfMngNo()).thenReturn("A1");
        when(view.getDcdReqInf()).thenReturn(raw);
        when(applications.findReadViewByApfMngNo("A1")).thenReturn(Optional.of(view));
        return view;
    }

    private void source(String table) {
        var source = mock(ApplicationMapRepository.DetailSourceView.class);
        when(source.getApfDcmNo()).thenReturn("A1");
        when(source.getFntTbNm()).thenReturn(table);
        when(source.getPkColNm()).thenReturn("SOURCE1");
        when(source.getFntTbCrySno()).thenReturn(null);
        when(maps.findDetailSourcesByApplicationIds(List.of("A1"))).thenReturn(List.of(source));
    }
}
