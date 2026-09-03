package com.kdb.it.domain.budget.common.repository;

import com.kdb.it.common.approval.domain.ApprovalStatus;
import java.util.Set;

/**
 * 예산 목록의 결재상태 스코프별 버전 노출 규칙입니다.
 *
 * <p>재상신 도입 이후 한 관리번호에는 최종본({@code LST_YN='Y'})과 미결 개정본({@code LST_YN='N'})이 함께 존재합니다. 목록 술어가 무조건
 * 최종본만 노출하면 상신된 재상신 초안이 미상신·결재중 어느 스코프에도 나타나지 않아 결재 완료 시까지 화면에서 사라집니다. 반대로 모든 스코프에서 초안을 열면 결재완료 목록에
 * 과거 승인 버전이 중복 노출됩니다. 그래서 스코프별로 판정합니다.
 *
 * <p>정보화사업·경상사업({@code BPROJM})과 전산업무비({@code BCOSTM}) 목록이 같은 규칙을 공유하도록 두 리포지토리 구현이 이 클래스를 함께
 * 사용합니다.
 */
public final class BudgetListVersionScope {

    /** 결재 신청이 없는 항목을 조회하는 스코프 키워드입니다. */
    public static final String SCOPE_NONE = "none";

    /**
     * 재상신 초안까지 노출해야 하는 결재상태 코드입니다.
     *
     * <p>작성완료는 저장한 초안 자신이 그 상태이고, 결재중은 상신된 초안, 반려·회수는 최종본으로 승격되지 못한 채 남은 초안이라 모두
     * {@code LST_YN='N'}일 수 있습니다.
     */
    private static final Set<String> DRAFT_VISIBLE_CODES =
            Set.of(
                    ApprovalStatus.DRAFTED.code(),
                    ApprovalStatus.IN_PROGRESS.code(),
                    ApprovalStatus.REJECTED.code(),
                    ApprovalStatus.RECALLED.code());

    private BudgetListVersionScope() {}

    /**
     * 라벨로 들어온 결재상태를 저장 코드로 정규화합니다.
     *
     * <p>목록 API는 코드({@code "1"})와 라벨({@code "결재중"})을 모두 받아 왔습니다. 술어 조립 앞단에서 한 번 정규화해 이후 비교가 코드 하나만
     * 다루게 합니다.
     *
     * @param apfSts 결재상태 코드·라벨 또는 {@link #SCOPE_NONE}. null·공백 허용
     * @return 라벨이면 대응 코드, 그 밖에는 입력 그대로
     */
    public static String normalize(String apfSts) {
        if (apfSts == null || apfSts.isBlank()) {
            return apfSts;
        }
        return ApprovalStatus.hasLabel(apfSts) ? ApprovalStatus.ofLabel(apfSts).code() : apfSts;
    }

    /**
     * 해당 스코프가 재상신 초안({@code LST_YN='N'})까지 노출해야 하는지 판정합니다.
     *
     * <p>미상신·작성완료·결재중·반려·회수는 초안을 포함하고, 필터가 없는 일반 목록과 결재완료·수기등록은 최종본만 노출합니다. 알 수 없는 값도 최종본만 노출하는 쪽으로 처리해
     * 목록이 과거 버전으로 오염되지 않게 합니다.
     *
     * @param apfSts 결재상태 코드·라벨 또는 {@link #SCOPE_NONE}. null·공백 허용
     * @return 초안까지 노출해야 하면 true
     */
    public static boolean includesDrafts(String apfSts) {
        String normalized = normalize(apfSts);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return SCOPE_NONE.equals(normalized) || DRAFT_VISIBLE_CODES.contains(normalized);
    }
}
