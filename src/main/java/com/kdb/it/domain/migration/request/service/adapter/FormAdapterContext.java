package com.kdb.it.domain.migration.request.service.adapter;

import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.IoeHierarchyIndex;
import com.kdb.it.domain.migration.service.OrgIdentityResolver;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * 어댑터 1회 실행에 필요한 입력을 묶습니다.
 *
 * <p>시트를 하나가 아니라 맵으로 넘깁니다 — 1-1과 1-2는 같은 사업의 머리와 몸통이라 한 어댑터가 둘을 함께 읽어야 하고, 시트마다 어댑터를 두면 사업관리번호를 어댑터
 * 사이로 넘겨야 해서 경계가 흐려집니다.
 *
 * @param sheets 이 워크북에서 인식된 시트들
 * @param bseYy 예산연도 4자리. 화면에서 사용자가 고른 값
 * @param entry 파일별 부가 정보 (부서명·단위 배수·사업코드)
 * @param resolvedDeptCode 폴더명 또는 보정값에서 확정한 부서코드
 * @param resolvedDeptName 확정한 부서명. 미해석이면 폴더명 원문
 * @param orgIndex 조직·사용자 해석 인덱스 (배치 전체에서 공유)
 * @param ioeIndex 비목 계층 인덱스 (배치 전체에서 공유)
 * @param overrides {@link #overrideKey}로 만든 키별 보정값
 * @param actorEno 업로드 사용자 사번
 */
public record FormAdapterContext(
        Map<FormSheetKind, Sheet> sheets,
        String bseYy,
        RequestFormDto.FileEntry entry,
        String resolvedDeptCode,
        String resolvedDeptName,
        OrgIdentityResolver.Index orgIndex,
        IoeHierarchyIndex.Snapshot ioeIndex,
        Map<String, String> overrides,
        String actorEno) {

    /**
     * 국외 부점인지 판정합니다.
     *
     * <p>비목이 국내·국외로 갈리는 항목(기계장치·회선사용료 등)에서 어느 쪽을 고를지 정하는 기준입니다. <b>부서코드가 `9`로 시작하면 국외</b>입니다(실측:
     * 런던지점 `920`).
     *
     * <p>통화로 가르지 않는 이유는 통화가 부점 소속을 말해 주지 않기 때문입니다 — 국내 부점도 외화 계약을 맺고, 국외 점포도 원화로 적어 내는 행이 있습니다. 비목의
     * 국내·국외는 <b>돈의 단위가 아니라 부점의 소속</b>으로 정해집니다.
     *
     * @return 국외 부점이면 true. 부서코드가 없으면 국내로 봅니다
     */
    public boolean foreignBranch() {
        return com.kdb.it.common.iam.BranchCodes.isForeign(resolvedDeptCode);
    }

    /**
     * 보정값을 찾습니다.
     *
     * @param sheet 대상 시트. 파일 전체 보정이면 null
     * @param excelRow 엑셀 행 번호(1-based). 행에 매이지 않으면 null
     * @param field 필드 id
     * @return 사용자가 고른 값. 없거나 공백이면 빈 Optional
     */
    public Optional<String> override(FormSheetKind sheet, Integer excelRow, String field) {
        String value = overrides.get(overrideKey(sheet, excelRow, field));
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /**
     * 보정값 맵의 키를 만듭니다. 서버와 프론트가 같은 규칙을 써야 보정이 붙습니다.
     *
     * @param sheet 대상 시트 (null 허용)
     * @param excelRow 엑셀 행 번호 (null 허용)
     * @param field 필드 id
     * @return `시트|행|필드` 형태의 키
     */
    public static String overrideKey(FormSheetKind sheet, Integer excelRow, String field) {
        return (sheet == null ? "" : sheet.name())
                + "|"
                + (excelRow == null ? "" : excelRow)
                + "|"
                + field;
    }
}
