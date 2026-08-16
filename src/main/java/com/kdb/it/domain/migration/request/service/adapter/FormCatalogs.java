package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.dto.MigrationDto;
import java.util.List;
import java.util.Map;

/**
 * 1-1 시트 해석에 필요한 공통코드 묶음입니다.
 *
 * <p>맵을 하나씩 인자로 늘리면 같은 타입 인자가 줄지어 서서 순서를 잘못 넘겨도 컴파일이 통과합니다. 배치 시작에 한 번 만들어 파일마다 재사용하는 값들이라 묶어 두는 편이
 * 수명도 맞습니다.
 *
 * @param exePttCodeByName 추진가능성 코드값명 → 코드
 * @param edrtCapitalCodeByName 전결권 자본예산 계열 코드값명 → 코드
 * @param reportStatusCodeByName 보고상태 코드값명 → 코드
 * @param optionCandidatesByField 선택 항목 필드 id → 고를 수 있는 후보. 미기재 안내에 붙여 화면에서 바로 고르게 합니다
 */
public record FormCatalogs(
        Map<String, String> exePttCodeByName,
        Map<String, String> edrtCapitalCodeByName,
        Map<String, String> reportStatusCodeByName,
        Map<String, List<MigrationDto.Candidate>> optionCandidatesByField) {

    /** 코드가 하나도 없는 묶음입니다. 코드 해석을 보지 않는 테스트가 씁니다. */
    public static FormCatalogs empty() {
        return new FormCatalogs(Map.of(), Map.of(), Map.of(), Map.of());
    }

    /**
     * 선택 항목의 후보를 찾습니다.
     *
     * @param field 필드 id
     * @return 후보 목록. 없으면 빈 목록
     */
    public List<MigrationDto.Candidate> optionCandidates(String field) {
        return optionCandidatesByField.getOrDefault(field, List.of());
    }
}
