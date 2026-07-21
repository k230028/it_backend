package com.kdb.it.common.iam.service;

import com.kdb.it.common.iam.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조직코드 → 조직명 공통 해석기.
 *
 * <p>
 * 부서/팀 코드 모두 조직 마스터({@code TPRMPP_CORGNI})의 {@code PRLM_OGZ_C_CONE}을
 * 키로 사용하므로 단일 메서드로 해석합니다. 마스터 레코드에 주관부서명/주관팀명
 * 스냅샷(SVN_DPM_NM/SVN_TEM_NM)을 저장할 때 사용합니다.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class OrgNameResolver {

    /** 조직(부점) 마스터 리포지토리: 조직코드→조직명 조회용 */
    private final OrganizationRepository organizationRepository;

    /**
     * 조직코드에 해당하는 조직명을 해석합니다.
     *
     * @param orgCode 조직코드 (부서코드 또는 팀코드)
     * @return 조직명. 코드가 null/공백이거나 CORGNI에 없으면 {@code null}
     */
    @Transactional(readOnly = true)
    public String resolveName(String orgCode) {
        if (orgCode == null || orgCode.isBlank()) {
            return null;
        }
        return organizationRepository.findById(orgCode)
                .map(organization -> organization.getBbrNm())
                .orElse(null);
    }
}
