package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import java.util.List;

/**
 * 비목 진단에 붙일 후보를 고릅니다.
 *
 * <p>해석기가 좁혀 준 후보가 있으면 그것을 쓰고, 없으면 비목 전체를 냅니다. 후보가 빈 진단은 화면에 드롭다운이 그려지지 않아 "골라 주세요"라고 해놓고 고를 수단이 없는
 * 상태가 됩니다 — 좁히지 못한 것과 고를 수 없는 것은 다릅니다.
 */
final class IoeCandidates {

    private IoeCandidates() {
        throw new UnsupportedOperationException("유틸리티 — 인스턴스화 금지");
    }

    /**
     * 후보를 고릅니다.
     *
     * @param resolution 비목 해석 결과
     * @param context 어댑터 실행 맥락 (비목 인덱스를 꺼냅니다)
     * @return 좁혀진 후보. 없으면 비목 전체
     */
    static List<MigrationDto.Candidate> orAll(
            IoeHierarchyIndex.Resolution resolution, FormAdapterContext context) {
        return resolution.candidates().isEmpty()
                ? context.ioeIndex().allCandidates()
                : resolution.candidates();
    }
}
