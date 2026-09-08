package com.kdb.it.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.project.dto.ProjectConflictResponse;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.exception.ProjectConflictException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 정보화사업 저장 충돌 예외가 코드·현재 상태를 담은 응답으로 변환되는지 검증합니다. */
class ProjectConflictExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("충돌 예외는 409와 현재 상태·스탬프를 반환한다")
    void conflictCarriesCurrentState() {
        ProjectDto.Response current =
                ProjectDto.Response.builder().abusMngNo("PRJ-2026-0001").build();
        LocalDateTime changedAt = LocalDateTime.of(2026, 9, 8, 14, 25);
        ProjectConflictException exception =
                new ProjectConflictException(
                        HttpStatus.CONFLICT,
                        "PROJECT_SOURCE_CHANGED",
                        "다른 사용자가 이 사업을 수정했습니다.",
                        "홍길동",
                        "10002",
                        changedAt,
                        "b".repeat(64),
                        current);

        ResponseEntity<ProjectConflictResponse> response = handler.handleProjectConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo("PROJECT_SOURCE_CHANGED");
        assertThat(response.getBody().changedBy()).isEqualTo("홍길동");
        assertThat(response.getBody().changedByEno()).isEqualTo("10002");
        assertThat(response.getBody().changedAt()).isEqualTo(changedAt);
        assertThat(response.getBody().currentStamp()).isEqualTo("b".repeat(64));
        assertThat(response.getBody().current()).isSameAs(current);
    }

    @Test
    @DisplayName("스탬프 누락은 400과 빈 현재 상태를 반환한다")
    void missingStampIsBadRequest() {
        ProjectConflictException exception =
                new ProjectConflictException(
                        HttpStatus.BAD_REQUEST,
                        "PROJECT_STAMP_REQUIRED",
                        "동시성 스탬프가 필요합니다.",
                        null,
                        null,
                        null,
                        null,
                        null);

        ResponseEntity<ProjectConflictResponse> response = handler.handleProjectConflict(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().current()).isNull();
    }
}
