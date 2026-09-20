package com.kdb.it.domain.council.service;

import com.kdb.it.common.system.exception.LockTimeouts;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.repository.CommitteeRepository;
import com.kdb.it.domain.council.repository.CouncilRepository;
import java.util.Set;
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
    private final ProjectItemRepository projectItemRepository;

    /** 시스템관리자가 정보보호 항목 판정 없이 신청할 수 있는 심의유형. 프론트 `roleDbrTcSet`과 같다. */
    private static final Set<String> ADMIN_CREATABLE_TYPES = Set.of("01", "02", "03", "05");

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

    /**
     * 관리자(시스템관리자, 심의유형 04의 정보보호관리자)만 허용합니다. 평가위원 편성 같은 관리 액션에 사용합니다.
     *
     * @param council 활성 협의회 원장
     * @throws AccessDeniedException 인증 주체가 없거나 관리 권한이 없는 경우
     */
    public void verifyManageable(Basctm council) {
        CustomUserDetails user = currentUser();
        if (!canManage(council, user)) {
            throw new AccessDeniedException("협의회 관리 권한이 없습니다.");
        }
    }

    /**
     * 관리자 또는 활성 연결 사업의 주관부서를 허용합니다. 배정위원 자격만으로는 허용하지 않습니다.
     *
     * @param council 활성 협의회 원장
     * @throws AccessDeniedException 인증 주체가 없거나 주관부서·관리 권한이 없는 경우
     */
    public void verifyOwningOrManageable(Basctm council) {
        CustomUserDetails user = currentUser();
        if (!canManage(council, user) && !isOwningDepartment(council, user)) {
            throw new AccessDeniedException("협의회 주관부서 또는 관리자만 수행할 수 있습니다.");
        }
    }

    /**
     * 관리자 또는 해당 협의회의 활성 배정위원을 허용합니다. 사전 Q&A 질문 등록에 사용합니다.
     *
     * @param council 활성 협의회 원장
     * @throws AccessDeniedException 인증 주체가 없거나 위원·관리 권한이 없는 경우
     */
    public void verifyCommitteeOrManageable(Basctm council) {
        CustomUserDetails user = currentUser();
        if (canManage(council, user)) {
            return;
        }
        if (committeeRepository
                .findByItPtlAsctIdAndEnoAndDelYn(council.getItPtlAsctId(), user.getEno(), "N")
                .isPresent()) {
            return;
        }
        throw new AccessDeniedException("해당 협의회의 평가위원 또는 관리자만 수행할 수 있습니다.");
    }

    /**
     * 협의회 신청 가능 여부를 심의유형과 대상 사업으로 판정합니다. 원장이 아직 없으므로 키를 직접 받습니다.
     *
     * <p>시스템관리자는 01·02·03·05를 조건 없이, 04는 대상 사업에 정보보호 소요자원이 있을 때 신청합니다. 정보보호관리자는 04만 신청합니다. 그 밖의
     * 사용자는 대상 사업의 주관부서일 때 03을, 정보보호 소요자원까지 있을 때 04를 신청합니다.
     *
     * @param dbrTc 심의유형 코드
     * @param abusMngNo 대상 사업 관리번호(계획협의회는 계획관리번호)
     * @param sno 대상 사업 순번(계획협의회는 null)
     * @throws AccessDeniedException 인증 주체가 없거나 위 규칙에 맞지 않는 경우
     */
    public void verifyCreatable(String dbrTc, String abusMngNo, Integer sno) {
        CustomUserDetails user = currentUser();
        if (user.isAdmin() && ADMIN_CREATABLE_TYPES.contains(dbrTc)) {
            return;
        }
        if (user.isInfoSecAdmin() && "04".equals(dbrTc)) {
            return;
        }
        if (!"03".equals(dbrTc) && !"04".equals(dbrTc)) {
            throw new AccessDeniedException("신청할 수 없는 심의유형입니다: " + dbrTc);
        }
        if (user.isInfoSecAdmin() && !user.isAdmin()) {
            throw new AccessDeniedException("정보보호관리자는 정보보호시스템(04) 협의회만 신청할 수 있습니다.");
        }
        if (!user.isAdmin() && !isOwningProject(abusMngNo, sno, user)) {
            throw new AccessDeniedException("대상 사업의 주관부서만 협의회를 신청할 수 있습니다.");
        }
        if ("04".equals(dbrTc)
                && !projectItemRepository.existsByAbusMngNoAndSectSysUtzYnAndDelYn(
                        abusMngNo, "Y", "N")) {
            throw new AccessDeniedException("정보보호 소요자원이 없는 사업은 정보보호시스템(04) 협의회를 신청할 수 없습니다.");
        }
    }

    private boolean canManage(Basctm council, CustomUserDetails user) {
        return user.isAdmin()
                || (user.isInfoSecAdmin() && "04".equals(council.getItPtlAsctDbrTc()));
    }

    private boolean isOwningDepartment(Basctm council, CustomUserDetails user) {
        if ("02".equals(council.getItPtlAsctDbrTc())) {
            return false;
        }
        return isOwningProject(council.getAbusMngNo(), council.getSno(), user);
    }

    private boolean isOwningProject(String abusMngNo, Integer sno, CustomUserDetails user) {
        if (!StringUtils.hasText(user.getBbrC())
                || !StringUtils.hasText(abusMngNo)
                || sno == null) {
            return false;
        }
        return projectRepository
                .findById(new BprojmId(abusMngNo, sno))
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
