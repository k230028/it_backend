package com.kdb.it.common.approval.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalDetailPolicyTest {
    private final ApplicationMapRepository maps = mock(ApplicationMapRepository.class);
    private final ApprovalDetailPolicy policy = new ApprovalDetailPolicy(maps);

    @Test
    void classificationDeduplicatesAndBoundsOracleInClauseWithoutPerDocumentQueries() {
        List<String> ids = IntStream.range(0, 1001).mapToObj(i -> "APF-" + i).toList();
        List<String> repeated = new ArrayList<>(ids);
        repeated.addAll(ids);
        assertThat(policy.findJsonlessCouncilIds(repeated)).isEmpty();
        ArgumentCaptor<List<String>> batches = ArgumentCaptor.captor();
        verify(maps, times(3)).findDetailSourcesByApplicationIds(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(500, 500, 1);
        assertThat(batches.getAllValues().stream().flatMap(List::stream).toList())
                .containsExactlyElementsOf(ids);
    }

    @Test
    void emptyBatchAndExistingJsonNeedNoSourceQuery() {
        assertThat(policy.findJsonlessCouncilIds(List.of())).isEmpty();
        for (String raw : List.of("", " ", "{}", "{bad")) {
            Capplm app = Capplm.builder().apfMngNo("APF-1").dcdReqInf(raw).build();
            assertThat(policy.resolve(app))
                    .isEqualTo(ApprovalDetailPolicy.DetailMode.SNAPSHOT_REQUIRED);
            assertThat(policy.resolve(app, Set.of("APF-1")))
                    .isEqualTo(ApprovalDetailPolicy.DetailMode.SNAPSHOT_REQUIRED);
        }
        verifyNoInteractions(maps);
    }
}
