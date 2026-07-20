package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 팀 사용자 목록에서 대표자를 결정적으로 선택하는 공용 유틸. (BE-10)
 *
 * <p>선택 규칙: ① 직위명({@code PT_C_NM}) '팀장' 우선 → ② 사번({@code ENO}) 오름차순.
 * 사전협의 검토자 선정({@code ReviewerService})과 협의회 당연위원 후보 선정
 * ({@code CommitteeService})이 공유합니다.</p>
 */
public final class UserRepresentativeSelector {

    /** 대표자로 우선 선택하는 직위명 */
    private static final String TEAM_LEAD_TITLE = "팀장";

    private UserRepresentativeSelector() {
    }

    /**
     * 대표자 1명을 결정적으로 선택합니다.
     *
     * @param users 같은 팀의 사용자 목록 (null 불가, 빈 목록 허용)
     * @return 대표자 (빈 목록이면 {@link Optional#empty()})
     */
    public static Optional<CuserI> pick(List<CuserI> users) {
        return users.stream()
                .min(Comparator.comparing((CuserI u) -> TEAM_LEAD_TITLE.equals(u.getPtCNm()) ? 0 : 1)
                        .thenComparing(CuserI::getEno,
                                Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 팀 사용자 프로젝션에서 대표자 1명을 결정적으로 선택합니다.
     *
     * @param users 같은 팀의 사용자 프로젝션 목록
     * @return 대표자 프로젝션, 빈 목록이면 빈 값
     */
    public static Optional<UserRepository.CommitteeUserRow> pickView(
            List<UserRepository.CommitteeUserRow> users) {
        return users.stream()
                .min(Comparator.comparing((UserRepository.CommitteeUserRow u) ->
                                TEAM_LEAD_TITLE.equals(u.getPtCNm()) ? 0 : 1)
                        .thenComparing(UserRepository.CommitteeUserRow::getEno,
                                Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
