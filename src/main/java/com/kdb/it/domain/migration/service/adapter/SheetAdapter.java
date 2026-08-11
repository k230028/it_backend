package com.kdb.it.domain.migration.service.adapter;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;

/**
 * 정규화 엑셀 행을 기존 도메인 서비스의 생성요청으로 바꿉니다.
 *
 * <p>어댑터는 순수 변환만 합니다. 채번·환율 조회·금액 재계산·조직명 스냅샷·감사로그는 {@code CostService}·{@code ProjectService}가
 * 담당하며, 편성행({@code BBUGTM})은 어댑터가 쓰지 않고 편성률 의도만 남깁니다.
 */
public interface SheetAdapter {

    /**
     * 이 어댑터가 담당하는 시트 종류를 반환합니다.
     *
     * @return 시트 종류
     */
    SheetKind supports();

    /**
     * 시트 하나를 생성요청 묶음으로 변환합니다.
     *
     * <p>검증은 {@code MigrationValidator}가 이미 통과시킨 상태를 전제합니다. 어댑터는 미해석 값을 만나면 예외를 던지지 않고 null·기본값으로
     * 둡니다.
     *
     * @param sheet 시트 페이로드
     * @param ctx 예산연도·조회 인덱스·스냅샷·보정값·업로드 사용자
     * @return 변환 결과
     */
    AdapterOutput adapt(MigrationDto.SheetPayload sheet, AdapterContext ctx);
}
