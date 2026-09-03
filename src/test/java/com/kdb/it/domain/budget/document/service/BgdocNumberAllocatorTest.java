package com.kdb.it.domain.budget.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BgdocNumberAllocatorTest {

    @Mock private GuideDocRepository guideDocRepository;

    @Test
    @DisplayName("접두사와 무관하게 BGDOC 공유 시퀀스로 문서관리번호를 발급한다")
    void next_서로다른접두사_공유시퀀스로채번() {
        given(guideDocRepository.getNextSequenceValue()).willReturn(17L, 18L);
        BgdocNumberAllocator allocator =
                new BgdocNumberAllocator(
                        guideDocRepository,
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThat(allocator.next("CDOC-")).isEqualTo("CDOC-2026-0017");
        assertThat(allocator.next("PDOC-")).isEqualTo("PDOC-2026-0018");
    }

    @Test
    @DisplayName("주입된 Clock의 연도로 문서관리번호를 발급한다")
    void next_주입Clock연도_문서관리번호에반영() {
        given(guideDocRepository.getNextSequenceValue()).willReturn(1L);
        BgdocNumberAllocator allocator =
                new BgdocNumberAllocator(
                        guideDocRepository,
                        Clock.fixed(Instant.parse("2031-12-31T15:00:00Z"), ZoneOffset.UTC));

        assertThat(allocator.next("GDOC-")).isEqualTo("GDOC-2031-0001");
    }
}
