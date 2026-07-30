package com.kdb.it.domain.budget.project.service;

import com.kdb.it.domain.budget.project.entity.Bprojm;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** 같은 사업관리번호의 BPROJM 버전 행에서 대표 행 선택 규칙을 공유하는 유틸리티. (BE-17 결정 #3) */
@Slf4j
public final class ProjectRepresentativeSelector {

    private ProjectRepresentativeSelector() {}

    /**
     * 같은 사업관리번호의 버전 행 목록에서 최신 대표 행을 결정적으로 선택합니다.
     *
     * <p>선택 규칙: {@code LST_YN='Y'} 행만 대표로 인정합니다(단건 조회 {@code findByAbusMngNoAndLstYnAndDelYn}과 동일
     * 의미). 해당 행이 없으면 empty를 반환하며 호출부는 관리번호 폴백을 적용합니다. {@code LST_YN='Y'} 행이 2건 이상이면 데이터 정합성 이상이므로
     * WARN 로그를 남기고 {@code SNO} 내림차순 tie-break 결과를 사용합니다(장애 없이 동작).
     *
     * @param projects 같은 {@code abusMngNo}의 미삭제 버전 행 목록
     * @return {@code LST_YN='Y'} 대표 행, 없으면 empty
     */
    public static Optional<Bprojm> pickLatest(List<Bprojm> projects) {
        List<Bprojm> latestRows =
                projects.stream().filter(project -> "Y".equals(project.getLstYn())).toList();
        if (latestRows.isEmpty()) {
            return Optional.empty();
        }
        Optional<Bprojm> primary =
                latestRows.stream()
                        .max(
                                Comparator.comparing(
                                        (Bprojm project) -> project.getSno(),
                                        Comparator.nullsFirst(Comparator.naturalOrder())));
        if (latestRows.size() > 1) {
            log.warn(
                    "BPROJM LST_YN='Y' 행이 {}건입니다 (abusMngNo={}, 선택 sno={})",
                    latestRows.size(),
                    primary.get().getAbusMngNo(),
                    primary.get().getSno());
        }
        return primary;
    }
}
