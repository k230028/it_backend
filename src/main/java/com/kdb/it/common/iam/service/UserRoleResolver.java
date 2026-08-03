package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 자격등급 공통 해석기.
 *
 * <p>활성·미삭제 역할만 조회하고 역할이 없는 사용자는 일반 사용자 자격등급으로 보정합니다. 로그인, 세션 복원, 개발 사용자 전환, SSO 토큰 발급, Refresh Token
 * 회전이 모두 이 해석기를 통해 동일한 자격등급 정책을 사용합니다.
 */
@Component
@RequiredArgsConstructor
public class UserRoleResolver {

    /** 역할관리(사용자↔자격등급 매핑) 데이터 접근 리포지토리 (TPRMPP_CROLEI) */
    private final RoleRepository roleRepository;

    /**
     * 사용자의 현재 활성 자격등급 목록을 해석합니다.
     *
     * @param eno 역할을 조회할 사용자 사번
     * @return 조회 순서를 유지하는 변경 불가 자격등급 목록. 활성 역할이 없으면 {@code ATH_USER} 단일 목록
     * @throws org.springframework.dao.DataAccessException 역할 조회에 실패한 경우 원본 예외를 그대로 전파합니다
     */
    @Transactional(readOnly = true)
    public List<String> resolveAthIds(String eno) {
        List<String> athIds =
                roleRepository.findAllByIdEnoAndUseYnAndDelYn(eno, "Y", "N").stream()
                        .map(CroleI::getAthId)
                        .toList();
        return athIds.isEmpty() ? List.of(CustomUserDetails.ATH_USER) : athIds;
    }
}
