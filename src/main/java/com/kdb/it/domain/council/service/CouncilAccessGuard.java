package com.kdb.it.domain.council.service;

import com.kdb.it.common.system.exception.LockTimeouts;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/** 협의회 상세·검토표의 조회 범위와 검토표 저장 경계를 검증합니다. */
@Component
@RequiredArgsConstructor
public class CouncilAccessGuard {
    private final CouncilRepository councilRepository;
    private final ProjectRepository projectRepository;
    private final CommitteeRepository committeeRepository;

    /**
     * 관리자·해당 심의유형의 정보보호관리자·주관부서·배정위원에게 상세 조회를 허용합니다.
     *
     * @param council 활성 협의회 원장
     * @throws AccessDeniedException 인증 주체 또는 대상 접근 권한이 없는 경우
     */
    public void verifyReadable(Basctm council) {
        CustomUserDetails user = currentUser();
        if (canManage(council, user) || isOwningDepartment(council, user)) {
            return;
        }
        if (StringUtils.hasText(user.getEno())
                && committeeRepository
                        .findByItPtlAsctIdAndEnoAndDelYn(
                                council.getItPtlAsctId(), user.getEno(), "N")
                        .isPresent()) {
            return;
        }
        throw new AccessDeniedException("협의회 조회 권한이 없습니다.");
    }

    /**
     * 쓰기 트랜잭션 안에서 원장을 잠근 뒤 검토표 수정 권한과 작성중 상태를 검사합니다. 배정위원 자격만으로는 검토표를 수정할 수 없습니다. 계획협의회는 검토표 작성 대상이
     * 아닙니다.
     *
     * @param asctId 협의회 ID
     * @return 잠금과 권한·상태 검증을 마친 활성 원장
     * @throws AccessDeniedException 수정 권한이 없는 경우
     * @throws ResponseStatusException 삭제·미존재는 404, 상태 충돌·잠금 초과는 409
     */
    public Basctm lockWritableDraft(String asctId) {
        CustomUserDetails user = currentUser();
        Basctm council;
        try {
            council =
                    councilRepository
                            .findByIdForUpdate(asctId)
                            .filter(value -> "N".equals(value.getDelYn()))
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "협의회를 찾을 수 없습니다."));
        } catch (RuntimeException exception) {
            if (LockTimeouts.isLockTimeout(exception)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "다른 작업이 협의회를 변경하고 있습니다. 잠시 후 다시 시도해 주세요.", exception);
            }
            throw exception;
        }
        if (!canManage(council, user) && !isOwningDepartment(council, user)) {
            throw new AccessDeniedException("타당성검토표 수정 권한이 없습니다.");
        }
        if (!"01".equals(council.getItPtlAsctPrgStsTc())
                || "02".equals(council.getItPtlAsctDbrTc())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "타당성검토표를 작성할 수 있는 상태가 아닙니다. 협의회를 다시 조회해 주세요.");
        }
        return council;
    }

    private boolean canManage(Basctm council, CustomUserDetails user) {
        return user.isAdmin()
                || (user.isInfoSecAdmin() && "04".equals(council.getItPtlAsctDbrTc()));
    }

    private boolean isOwningDepartment(Basctm council, CustomUserDetails user) {
        if ("02".equals(council.getItPtlAsctDbrTc())
                || !StringUtils.hasText(user.getBbrC())
                || !StringUtils.hasText(council.getAbusMngNo())
                || council.getSno() == null) {
            return false;
        }
        return projectRepository
                .findById(new BprojmId(council.getAbusMngNo(), council.getSno()))
                .filter(project -> "N".equals(project.getDelYn()))
                .map(project -> OwnershipVerifier.canModify(null, project.getSvnDpmC(), user))
                .orElse(false);
    }

    private CustomUserDetails currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            throw new AccessDeniedException("인증 정보가 없습니다.");
        }
        if (!StringUtils.hasText(user.getEno())) {
            throw new AccessDeniedException("인증 사번이 없습니다.");
        }
        return user;
    }
}
