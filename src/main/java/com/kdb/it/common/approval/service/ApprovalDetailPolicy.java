package com.kdb.it.common.approval.service;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.approval.repository.ApplicationMapRepository.DetailSourceView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** CAPPLA 원본 연결로 상세 JSON이 원래 없는 협의회 양식만 명시적으로 구별한다. */
@Component
@RequiredArgsConstructor
public class ApprovalDetailPolicy {
    private static final int QUERY_BATCH_SIZE = 500;
    private static final Set<String> JSONLESS_COUNCIL_TABLES = Set.of("BASCTM", "BASKPM");
    private final ApplicationMapRepository applicationMapRepository;

    public enum DetailMode {
        SNAPSHOT_REQUIRED,
        JSONLESS_COUNCIL
    }

    /** JSON이 존재하면 원본 종류와 무관하게 검증하며 추가 조회를 하지 않는다. */
    public DetailMode resolve(Capplm application) {
        if (application.getDcdReqInf() != null) return DetailMode.SNAPSHOT_REQUIRED;
        return resolve(application, findJsonlessCouncilIds(List.of(application.getApfMngNo())));
    }

    /** 일괄 결재에서 한 번 분류한 원본 집합을 재사용한다. 빈 문자열/손상 JSON은 JSON-less 허용 대상이 아니다. */
    public DetailMode resolve(Capplm application, Set<String> jsonlessCouncilIds) {
        return application.getDcdReqInf() == null
                        && jsonlessCouncilIds.contains(application.getApfMngNo())
                ? DetailMode.JSONLESS_COUNCIL
                : DetailMode.SNAPSHOT_REQUIRED;
    }

    /** 단일 활성 협의회 원본만 허용한다. 혼합·중복·누락·개정 순번 오류는 필수 상세로 취급한다. */
    public Set<String> findJsonlessCouncilIds(List<String> applicationIds) {
        List<String> ids = applicationIds.stream().distinct().toList();
        Set<String> eligible = new HashSet<>();
        for (int start = 0; start < ids.size(); start += QUERY_BATCH_SIZE) {
            var byApplication =
                    applicationMapRepository
                            .findDetailSourcesByApplicationIds(
                                    ids.subList(
                                            start, Math.min(start + QUERY_BATCH_SIZE, ids.size())))
                            .stream()
                            .collect(Collectors.groupingBy(DetailSourceView::getApfDcmNo));
            byApplication.forEach(
                    (id, sources) -> {
                        if (sources.size() != 1) return;
                        DetailSourceView source = sources.getFirst();
                        if (source.getFntTbNm() != null
                                && JSONLESS_COUNCIL_TABLES.contains(source.getFntTbNm())
                                && source.getPkColNm() != null
                                && !source.getPkColNm().isBlank()
                                && source.getFntTbCrySno() == null) eligible.add(id);
                    });
        }
        return Set.copyOf(eligible);
    }
}
