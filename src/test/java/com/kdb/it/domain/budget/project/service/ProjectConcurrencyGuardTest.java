package com.kdb.it.domain.budget.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.exception.ProjectConflictException;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

/**
 * ProjectConcurrencyGuard 단위 테스트
 *
 * <p>스탬프 누락·불일치·일치 각각의 판정과, 충돌 응답의 변경자가 부모·품목 중 더 나중에 바뀐 쪽을 가리키는지, 잠금 대기 초과가 409로 바뀌는지 검증합니다. DB 없이
 * 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectConcurrencyGuardTest {

    private static final String CURRENT = "c".repeat(64);

    @Mock private ProjectConcurrencyStamper stamper;
    @Mock private ProjectItemRepository itemRepository;
    @Mock private ProjectQueryAssembler queryAssembler;

    private ProjectConcurrencyGuard guard;
    private Bprojm target;

    @BeforeEach
    void setUp() {
        guard = new ProjectConcurrencyGuard(stamper, itemRepository, queryAssembler);
        target =
                Bprojm.builder()
                        .abusMngNo("PRJ-2026-0001")
                        .sno(1)
                        .abusTc("10")
                        .lstChgUsid("10001")
                        .lstChgDtm(LocalDateTime.of(2026, 9, 8, 10, 0))
                        .build();
        given(stamper.stamp(any(), anyList())).willReturn(CURRENT);
        given(queryAssembler.assembleDetail(target))
                .willReturn(ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").build());
    }

    private static ProjectDto.UpdateRequest request(String stamp) {
        return ProjectDto.UpdateRequest.builder().concurrencyStamp(stamp).build();
    }

    @Test
    @DisplayName("스탬프가 없으면 400 PROJECT_STAMP_REQUIRED로 막고 품목을 읽지 않는다")
    void missingStampIsRejected() {
        assertThatThrownBy(() -> guard.verifyStamp(request(null), target, id -> id))
                .isInstanceOf(ProjectConflictException.class)
                .satisfies(
                        e -> {
                            ProjectConflictException conflict = (ProjectConflictException) e;
                            assertThat(conflict.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(conflict.code()).isEqualTo("PROJECT_STAMP_REQUIRED");
                            assertThat(conflict.current()).isNull();
                        });
        verify(itemRepository, never())
                .findAllByAbusMngNoAndFntTbCrySnoAndDelYn(any(), any(), any());
    }

    @Test
    @DisplayName("형식이 어긋난 스탬프도 400으로 막는다")
    void malformedStampIsRejected() {
        assertThatThrownBy(() -> guard.verifyStamp(request("ABC"), target, id -> id))
                .isInstanceOf(ProjectConflictException.class)
                .extracting("code")
                .isEqualTo("PROJECT_STAMP_REQUIRED");
    }

    @Test
    @DisplayName("현재 스탬프와 같으면 통과한다")
    void matchingStampPasses() {
        given(itemRepository.findAllByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of());

        assertThatCode(() -> guard.verifyStamp(request(CURRENT), target, id -> id))
                .doesNotThrowAnyException();
        verify(queryAssembler, never()).assembleDetail(any());
    }

    @Test
    @DisplayName("스탬프가 다르면 409와 현재 상태·최신 스탬프를 돌려준다")
    void staleStampConflicts() {
        given(itemRepository.findAllByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of());

        assertThatThrownBy(() -> guard.verifyStamp(request("a".repeat(64)), target, id -> "홍길동"))
                .isInstanceOf(ProjectConflictException.class)
                .satisfies(
                        e -> {
                            ProjectConflictException conflict = (ProjectConflictException) e;
                            assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(conflict.code()).isEqualTo("PROJECT_SOURCE_CHANGED");
                            assertThat(conflict.currentStamp()).isEqualTo(CURRENT);
                            assertThat(conflict.changedBy()).isEqualTo("홍길동");
                            assertThat(conflict.changedByEno()).isEqualTo("10001");
                            assertThat(conflict.changedAt())
                                    .isEqualTo(LocalDateTime.of(2026, 9, 8, 10, 0));
                            assertThat(conflict.current().getAbusMngNo())
                                    .isEqualTo("PRJ-2026-0001");
                        });
    }

    @Test
    @DisplayName("품목이 부모보다 나중에 바뀌었으면 품목 변경자를 알린다")
    void latestItemChangeWins() {
        Bitemm item =
                Bitemm.builder()
                        .gclMngNo("GCL-1")
                        .sno(1)
                        .lstChgUsid("20002")
                        .lstChgDtm(LocalDateTime.of(2026, 9, 8, 11, 0))
                        .build();
        given(itemRepository.findAllByAbusMngNoAndFntTbCrySnoAndDelYn("PRJ-2026-0001", 1, "N"))
                .willReturn(List.of(item));

        assertThatThrownBy(() -> guard.verifyStamp(request("a".repeat(64)), target, id -> null))
                .isInstanceOf(ProjectConflictException.class)
                .satisfies(
                        e -> {
                            ProjectConflictException conflict = (ProjectConflictException) e;
                            // 이름을 해석하지 못하면 사번을 그대로 노출한다.
                            assertThat(conflict.changedBy()).isEqualTo("20002");
                            assertThat(conflict.changedByEno()).isEqualTo("20002");
                            assertThat(conflict.changedAt())
                                    .isEqualTo(LocalDateTime.of(2026, 9, 8, 11, 0));
                        });
    }

    @Test
    @DisplayName("잠금 대기 초과는 409 PROJECT_CONCURRENT_UPDATE로 바뀌고 다른 예외는 그대로 전파된다")
    void lockTimeoutBecomesConflict() {
        assertThatThrownBy(
                        () ->
                                guard.runUserUpdate(
                                        () -> {
                                            throw new org.springframework.dao
                                                    .CannotAcquireLockException("timeout");
                                        }))
                .isInstanceOf(ProjectConflictException.class)
                .extracting("code")
                .isEqualTo("PROJECT_CONCURRENT_UPDATE");
        assertThatThrownBy(
                        () ->
                                guard.runUserUpdate(
                                        () -> {
                                            throw new IllegalStateException("결재중");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(guard.runUserUpdate(() -> "PRJ-2026-0001")).isEqualTo("PRJ-2026-0001");
    }
}
