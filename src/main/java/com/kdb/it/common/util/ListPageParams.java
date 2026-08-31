package com.kdb.it.common.util;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 목록 API 공통 페이지 파라미터입니다.
 *
 * <p>검색 조건 DTO와 분리해 컨트롤러에서 별도 {@code @ModelAttribute}로 바인딩합니다. 페이지는 검색 조건이 아니고, 도메인마다 같은 규칙을 다시
 * 정의하지 않기 위해서입니다.
 *
 * <p>페이지를 지정하지 않으면 목록은 기존과 같이 상한({@code maxRows})까지 한 번에 조회합니다. 지정하면 그 구간만 조회하고, 컨트롤러가 {@link
 * #TOTAL_COUNT_HEADER}로 조건에 맞는 전체 건수를 함께 돌려줍니다.
 *
 * <p>잘못된 페이지 입력은 기본값으로 되돌리지 않고 예외로 구분합니다. 음수 크기를 상한으로 조용히 바꾸면 호출자는 "전체를 받았다"고 오해합니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ListPageParams {

    /**
     * 페이지 응답이 조건에 맞는 전체 건수를 싣는 헤더명입니다.
     *
     * <p>본문은 배열 그대로 두어 기존 응답 계약을 유지하고 총건수만 헤더로 전달합니다. 다른 출처의 스크립트가 읽으려면 CORS {@code
     * Access-Control-Expose-Headers}에 포함해야 합니다.
     */
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

    /** 페이지 번호(0부터). {@code size} 없이 단독으로 지정할 수 없습니다. */
    @Schema(description = "페이지 번호(0부터). size와 함께 지정. 미입력 시 페이징 없이 상한까지 조회")
    private Integer page;

    /** 페이지 크기. 1 이상, 목록 상한 이하만 허용합니다. */
    @Schema(description = "페이지 크기(1 이상, 목록 상한 이하). 지정 시 X-Total-Count 헤더로 전체 건수를 반환")
    private Integer size;

    /** 페이징 없이 상한까지 조회하는 기본 파라미터입니다. */
    public static ListPageParams unpaged() {
        return new ListPageParams();
    }

    /**
     * 페이지를 명시한 요청인지 여부입니다.
     *
     * @return size가 지정되었으면 true
     */
    public boolean isPaged() {
        return size != null;
    }

    /**
     * 조회에 적용할 DB 슬라이스 범위를 계산합니다.
     *
     * @param maxRows 페이징하지 않을 때의 상한이자 허용 가능한 최대 페이지 크기
     * @return offset·limit 쌍
     * @throws IllegalArgumentException page만 지정했거나 page가 음수이거나 size가 허용 범위 밖인 경우
     */
    public Slice slice(long maxRows) {
        if (size == null) {
            if (page != null) {
                throw new IllegalArgumentException("page는 size와 함께 지정해야 합니다.");
            }
            return new Slice(0L, maxRows);
        }
        if (size < 1 || size > maxRows) {
            throw new IllegalArgumentException("size는 1 이상 " + maxRows + " 이하여야 합니다: " + size);
        }
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("page는 0 이상이어야 합니다: " + pageNumber);
        }
        return new Slice((long) pageNumber * size, size);
    }

    /**
     * 목록 조회에 적용할 구간입니다.
     *
     * <p>페이지 경계에서 행이 겹치거나 빠지지 않으려면 이 구간을 쓰는 쿼리가 안정 정렬을 함께 지정해야 합니다.
     *
     * @param offset 건너뛸 행 수 (0 이상)
     * @param limit 최대 조회 행 수 (1 이상)
     */
    public record Slice(long offset, long limit) {}
}
