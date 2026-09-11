package com.kdb.it.common.admin.service;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.iam.entity.CauthI;
import com.kdb.it.common.iam.entity.CorgnI;
import com.kdb.it.common.iam.entity.CroleI;
import com.kdb.it.common.iam.entity.CroleIId;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.AuthRepository;
import com.kdb.it.common.iam.repository.OrganizationRepository;
import com.kdb.it.common.iam.repository.RoleRepository;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.infra.file.repository.FileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기능 서비스
 *
 * <p>기존 리포지토리를 DI 받아 관리자 전용 로직을 처리합니다. 엔티티/리포지토리는 신규 생성 없이 기존 패키지를 재사용합니다.
 *
 * <p>공통코드(TPRMPP_CCODEM) 관리는 캐시 무효화 책임과 함께 {@link AdminCodeService}로 분리되어 있습니다.
 *
 * <p>의존 패키지:
 *
 * <ul>
 *   <li>{@code common/iam} — 자격등급·역할·사용자·조직(CauthI, CroleI, CuserI, CorgnI)
 *   <li>{@code common/system} — 로그인 이력, Refresh 토큰
 *   <li>{@code infra/file} — 첨부파일
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private static final int MAX_USER_PAGE_SIZE = 200;
    private static final int MAX_ROLE_PAGE_SIZE = 200;
    private static final Map<String, String> ROLE_SORT_FIELDS =
            Map.of(
                    "athId", "id.athId",
                    "eno", "id.eno",
                    "useYn", "useYn",
                    "fstEnrDtm", "fstEnrDtm",
                    "lstChgDtm", "lstChgDtm");
    private static final Set<String> USER_SORT_FIELDS =
            Set.of(
                    "eno",
                    "usrNm",
                    "ptCNm",
                    "bbrNm",
                    "temNm",
                    "temC",
                    "inleNo",
                    "cpnTpn",
                    "etrMilAddrNm",
                    "fstEnrDtm",
                    "lstChgDtm");

    // 각 관리 기능은 기존 도메인 리포지토리를 생성자 주입으로 재사용합니다.
    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository orgRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FileRepository fileRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================================
    // 자격등급 (TPRMPP_CAUTHI) — M3
    // =========================================================================

    /**
     * 삭제되지 않은 전체 자격등급 목록을 조회합니다.
     *
     * @return 자격등급 응답 DTO 목록
     */
    public List<AdminDto.AuthGradeResponse> getAuthGrades() {
        return authRepository.findAll().stream()
                .filter(a -> "N".equals(a.getDelYn()))
                .map(this::toAuthGradeResponse)
                .toList();
    }

    /**
     * 신규 자격등급을 추가합니다. ATH_ID 중복 시 예외를 발생시킵니다.
     *
     * @param req 자격등급 생성 요청 DTO
     */
    @Transactional
    public void createAuthGrade(AdminDto.AuthGradeRequest req) {
        if (authRepository.existsById(req.athId())) {
            throw new IllegalArgumentException("이미 존재하는 자격등급ID입니다: " + req.athId());
        }
        authRepository.save(
                CauthI.builder()
                        .athId(req.athId())
                        .qlfGrNm(req.qlfGrNm())
                        .qlfGrMat(req.qlfGrMat())
                        .useYn(req.useYn() != null ? req.useYn() : "Y")
                        .build());
    }

    /**
     * 자격등급 정보를 수정합니다. Dirty Checking 활용.
     *
     * @param athId 자격등급ID
     * @param req 자격등급 수정 요청 DTO
     */
    @Transactional
    public void updateAuthGrade(String athId, AdminDto.AuthGradeRequest req) {
        CauthI auth =
                authRepository
                        .findById(athId)
                        .filter(a -> "N".equals(a.getDelYn()))
                        .orElseThrow(
                                () -> new IllegalArgumentException("존재하지 않는 자격등급ID입니다: " + athId));
        auth.update(req.qlfGrNm(), req.qlfGrMat(), req.useYn());
    }

    /**
     * 자격등급을 논리 삭제합니다.
     *
     * @param athId 자격등급ID
     */
    @Transactional
    public void deleteAuthGrade(String athId) {
        CauthI auth =
                authRepository
                        .findById(athId)
                        .filter(a -> "N".equals(a.getDelYn()))
                        .orElseThrow(
                                () -> new IllegalArgumentException("존재하지 않는 자격등급ID입니다: " + athId));
        auth.delete();
    }

    /** CauthI 엔티티를 AuthGradeResponse DTO로 변환합니다. */
    private AdminDto.AuthGradeResponse toAuthGradeResponse(CauthI a) {
        return new AdminDto.AuthGradeResponse(
                a.getAthId(),
                a.getQlfGrNm(),
                a.getQlfGrMat(),
                a.getUseYn(),
                a.getFstEnrDtm(),
                a.getFstEnrUsid(),
                resolveUserName(a.getFstEnrUsid()),
                a.getLstChgDtm(),
                a.getLstChgUsid(),
                resolveUserName(a.getLstChgUsid()));
    }

    // =========================================================================
    // 역할 (TPRMPP_CROLEI) — M4
    // =========================================================================

    /**
     * 삭제되지 않은 전체 역할(사용자↔자격등급 매핑) 목록을 조회합니다.
     *
     * @return 역할 응답 DTO 목록
     */
    public Page<AdminDto.RoleResponse> getRoles(String search, Pageable pageable) {
        Pageable safePageable = normalizeRolePageable(pageable);
        Page<CroleI> roles =
                roleRepository.findAdminRolePage(normalizeSearch(search), safePageable);
        Map<String, String> userNameMap =
                loadUserNameMap(
                        roles.getContent().stream()
                                .flatMap(
                                        role ->
                                                Stream.of(
                                                        role.getEno(),
                                                        role.getFstEnrUsid(),
                                                        role.getLstChgUsid())));
        return roles.map(role -> toRoleResponse(role, userNameMap));
    }

    /** 현재 검색·정렬 조건에 맞는 역할 전체를 엑셀 내보내기용으로 반환합니다. */
    public List<AdminDto.RoleResponse> getRolesForExport(String search, Sort sort) {
        List<CroleI> roles =
                roleRepository.findAdminRolesForExport(
                        normalizeSearch(search), normalizeRoleSort(sort));
        Map<String, String> userNameMap =
                loadUserNameMap(
                        roles.stream()
                                .flatMap(
                                        role ->
                                                Stream.of(
                                                        role.getEno(),
                                                        role.getFstEnrUsid(),
                                                        role.getLstChgUsid())));
        return roles.stream().map(role -> toRoleResponse(role, userNameMap)).toList();
    }

    private Pageable normalizeRolePageable(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_ROLE_PAGE_SIZE);
        Sort sort = normalizeRoleSort(pageable.getSort());
        if (sort.isUnsorted()) {
            sort = Sort.by("id.athId").ascending().and(Sort.by("id.eno").ascending());
        }
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }

    private Sort normalizeRoleSort(Sort sort) {
        return Sort.by(
                sort.stream()
                        .map(
                                order ->
                                        new Sort.Order(
                                                order.getDirection(),
                                                ROLE_SORT_FIELDS.getOrDefault(
                                                        order.getProperty(), "id.athId")))
                        .toList());
    }

    /**
     * 신규 역할을 추가합니다. 복합키(athId+eno) 중복 시 예외를 발생시킵니다.
     *
     * @param req 역할 생성 요청 DTO
     */
    @Transactional
    public void createRole(AdminDto.RoleRequest req) {
        CroleIId id = new CroleIId(req.athId(), req.eno());
        if (roleRepository.existsById(id)) {
            throw new IllegalArgumentException(
                    "이미 존재하는 역할입니다: athId=" + req.athId() + ", eno=" + req.eno());
        }
        roleRepository.save(
                CroleI.builder().id(id).useYn(req.useYn() != null ? req.useYn() : "Y").build());
    }

    /**
     * 역할 사용여부를 수정합니다. Dirty Checking 활용.
     *
     * @param athId 자격등급ID
     * @param eno 사원번호
     * @param req 역할 수정 요청 DTO
     */
    @Transactional
    public void updateRole(String athId, String eno, AdminDto.RoleRequest req) {
        CroleI role =
                roleRepository
                        .findById(new CroleIId(athId, eno))
                        .filter(r -> "N".equals(r.getDelYn()))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 역할입니다: athId=" + athId + ", eno=" + eno));
        role.updateUseYn(req.useYn());
    }

    /**
     * 역할을 논리 삭제합니다.
     *
     * @param athId 자격등급ID
     * @param eno 사원번호
     */
    @Transactional
    public void deleteRole(String athId, String eno) {
        CroleI role =
                roleRepository
                        .findById(new CroleIId(athId, eno))
                        .filter(r -> "N".equals(r.getDelYn()))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 역할입니다: athId=" + athId + ", eno=" + eno));
        role.delete();
    }

    /** CroleI 엔티티를 RoleResponse DTO로 변환합니다. */
    private AdminDto.RoleResponse toRoleResponse(CroleI r) {
        return new AdminDto.RoleResponse(
                r.getAthId(),
                r.getEno(),
                resolveUserName(r.getEno()),
                r.getUseYn(),
                r.getFstEnrDtm(),
                r.getFstEnrUsid(),
                resolveUserName(r.getFstEnrUsid()),
                r.getLstChgDtm(),
                r.getLstChgUsid(),
                resolveUserName(r.getLstChgUsid()));
    }

    private AdminDto.RoleResponse toRoleResponse(CroleI r, Map<String, String> userNameMap) {
        return new AdminDto.RoleResponse(
                r.getAthId(),
                r.getEno(),
                resolveUserName(r.getEno(), userNameMap),
                r.getUseYn(),
                r.getFstEnrDtm(),
                r.getFstEnrUsid(),
                resolveUserName(r.getFstEnrUsid(), userNameMap),
                r.getLstChgDtm(),
                r.getLstChgUsid(),
                resolveUserName(r.getLstChgUsid(), userNameMap));
    }

    // =========================================================================
    // 사용자 (TPRMPP_CUSERI) — M5
    // =========================================================================

    /**
     * 삭제되지 않은 전체 사용자 목록을 조회합니다. DEL_YN='N' 조건으로 필터링합니다.
     *
     * @return 사용자 응답 DTO 목록
     */
    public Page<AdminDto.UserResponse> getUsers(String search, Pageable pageable) {
        Pageable safePageable = normalizeUserPageable(pageable);
        return userRepository
                .findAdminUserPage(normalizeSearch(search), safePageable)
                .map(user -> toUserResponse(user, user.getBbrNm()));
    }

    public List<AdminDto.UserResponse> getUsersForExport(String search, Sort sort) {
        Sort safeSort = normalizeUserSort(sort);
        return userRepository.findAdminUsersForExport(normalizeSearch(search), safeSort).stream()
                .map(user -> toUserResponse(user, user.getBbrNm()))
                .toList();
    }

    private Pageable normalizeUserPageable(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_USER_PAGE_SIZE);
        return PageRequest.of(
                pageable.getPageNumber(), size, normalizeUserSort(pageable.getSort()));
    }

    private Sort normalizeUserSort(Sort sort) {
        List<Sort.Order> allowed =
                sort.stream()
                        .filter(order -> USER_SORT_FIELDS.contains(order.getProperty()))
                        .toList();
        return allowed.isEmpty() ? Sort.by("eno").ascending() : Sort.by(allowed);
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank() ? null : search.trim();
    }

    /**
     * 신규 사용자를 추가합니다. ENO 중복 시 예외를 발생시킵니다.
     *
     * @param req 사용자 생성 요청 DTO
     */
    @Transactional
    public void createUser(AdminDto.UserRequest req) {
        if (userRepository.existsByEno(req.eno())) {
            throw new IllegalArgumentException("이미 존재하는 사원번호입니다: " + req.eno());
        }
        String encodedPwd =
                req.password() != null
                        ? passwordEncoder.encode(req.password())
                        : passwordEncoder.encode("changeme"); // 초기 비밀번호 기본값
        userRepository.save(
                CuserI.builder()
                        .eno(req.eno())
                        .usrNm(req.usrNm())
                        .ptCNm(req.ptCNm())
                        .temC(req.temC())
                        .bbrC(req.bbrC())
                        .etrMilAddrNm(req.etrMilAddrNm())
                        .inleNo(req.inleNo())
                        .cpnTpn(req.cpnTpn())
                        .usrEcyPwd(encodedPwd)
                        .build());
    }

    /**
     * 사용자 기본정보를 수정합니다. Dirty Checking 활용. password 필드가 있으면 비밀번호도 함께 변경합니다.
     *
     * @param eno 사원번호
     * @param req 사용자 수정 요청 DTO
     */
    @Transactional
    public void updateUser(String eno, AdminDto.UserRequest req) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .filter(u -> "N".equals(u.getDelYn()))
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호입니다: " + eno));
        user.update(
                req.usrNm(),
                req.ptCNm(),
                req.temC(),
                req.bbrC(),
                req.etrMilAddrNm(),
                req.inleNo(),
                req.cpnTpn());
        if (req.password() != null && !req.password().isBlank()) {
            user.updatePassword(passwordEncoder.encode(req.password()));
        }
    }

    /**
     * 사용자를 논리 삭제합니다.
     *
     * @param eno 사원번호
     */
    @Transactional
    public void deleteUser(String eno) {
        CuserI user =
                userRepository
                        .findByEno(eno)
                        .filter(u -> "N".equals(u.getDelYn()))
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호입니다: " + eno));
        user.delete();
    }

    /** 관리자 사용자 프로젝션을 UserResponse DTO로 변환합니다. */
    private AdminDto.UserResponse toUserResponse(UserRepository.AdminUserView u, String bbrNm) {
        return new AdminDto.UserResponse(
                u.getEno(),
                u.getUsrNm(),
                u.getPtCNm(),
                u.getTemC(),
                u.getTemNm(),
                u.getBbrC(),
                bbrNm,
                u.getEtrMilAddrNm(),
                u.getInleNo(),
                u.getCpnTpn(),
                u.getFstEnrDtm(),
                u.getLstChgDtm());
    }

    // =========================================================================
    // 조직 (TPRMPP_CORGNI) — M6
    // =========================================================================

    /**
     * 삭제되지 않은 전체 조직 목록을 조회합니다. 등록자·변경자명은 배치 조회로 일괄 변환합니다.
     *
     * @return 조직 응답 DTO 목록
     */
    public List<AdminDto.OrgResponse> getOrganizations() {
        List<OrganizationRepository.OrganizationAdminView> organizations =
                orgRepository.findAdminViewsByDelYn("N");

        // 감사 필드(등록자·변경자)의 고유 ENO를 한 번의 배치 쿼리로 이름 조회 (N+1 방지 — BE-26)
        Map<String, String> userNameMap =
                loadUserNameMap(
                        organizations.stream()
                                .flatMap(o -> Stream.of(o.getFstEnrUsid(), o.getLstChgUsid())));

        return organizations.stream().map(o -> toOrgResponse(o, userNameMap)).toList();
    }

    /**
     * 신규 조직을 추가합니다. 조직코드 중복 시 예외를 발생시킵니다.
     *
     * @param req 조직 생성 요청 DTO
     */
    @Transactional
    public void createOrganization(AdminDto.OrgRequest req) {
        if (orgRepository.existsById(req.prlmOgzCCone())) {
            throw new IllegalArgumentException("이미 존재하는 조직코드입니다: " + req.prlmOgzCCone());
        }
        orgRepository.save(
                CorgnI.builder()
                        .prlmOgzCCone(req.prlmOgzCCone())
                        .bbrNm(req.bbrNm())
                        .bbrWrenNm(req.bbrWrenNm())
                        .itmSqnSno(req.itmSqnSno())
                        .prlmHrkOgzCCone(req.prlmHrkOgzCCone())
                        .build());
    }

    /**
     * 조직 정보를 수정합니다. Dirty Checking 활용.
     *
     * @param orgC 조직코드
     * @param req 조직 수정 요청 DTO
     */
    @Transactional
    public void updateOrganization(String orgC, AdminDto.OrgRequest req) {
        CorgnI org =
                orgRepository
                        .findById(orgC)
                        .filter(o -> "N".equals(o.getDelYn()))
                        .orElseThrow(
                                () -> new IllegalArgumentException("존재하지 않는 조직코드입니다: " + orgC));
        org.update(req.bbrNm(), req.bbrWrenNm(), req.itmSqnSno(), req.prlmHrkOgzCCone());
    }

    /**
     * 조직을 논리 삭제합니다.
     *
     * @param orgC 조직코드
     */
    @Transactional
    public void deleteOrganization(String orgC) {
        CorgnI org =
                orgRepository
                        .findById(orgC)
                        .filter(o -> "N".equals(o.getDelYn()))
                        .orElseThrow(
                                () -> new IllegalArgumentException("존재하지 않는 조직코드입니다: " + orgC));
        org.delete();
    }

    /** 관리자 조직 프로젝션을 OrgResponse DTO로 변환합니다. 등록자·변경자명은 배치 조회된 맵에서 찾습니다. */
    private AdminDto.OrgResponse toOrgResponse(
            OrganizationRepository.OrganizationAdminView o, Map<String, String> userNameMap) {
        return new AdminDto.OrgResponse(
                o.getPrlmOgzCCone(),
                o.getBbrNm(),
                o.getBbrWrenNm(),
                o.getItmSqnSno(),
                o.getPrlmHrkOgzCCone(),
                o.getFstEnrDtm(),
                o.getFstEnrUsid(),
                resolveUserName(o.getFstEnrUsid(), userNameMap),
                o.getLstChgDtm(),
                o.getLstChgUsid(),
                resolveUserName(o.getLstChgUsid(), userNameMap));
    }

    // =========================================================================
    // 로그인 이력 (TPRMPP_CLOGNH) — M7
    // =========================================================================

    /**
     * 전체 로그인 이력을 페이지네이션으로 조회합니다. ENO → 사용자명·부서명·팀명 변환을 포함합니다.
     *
     * @param pageable 페이지 정보 (최신순 정렬)
     * @return 페이지네이션된 로그인 이력 응답
     */
    public Page<AdminDto.LoginHistoryResponse> getLoginHistory(Pageable pageable) {
        Page<LoginHistoryRepository.LoginHistoryView> page =
                loginHistoryRepository.findPageViewsByOrderByLgnDtmDesc(pageable);
        Map<String, UserRepository.UserOrgNameView> userOrgMap =
                loadUserOrgNameMap(page.getContent().stream().map(history -> history.getEno()));
        List<AdminDto.LoginHistoryResponse> content =
                page.getContent().stream()
                        .map(history -> toLoginHistoryResponse(history, userOrgMap))
                        .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 로그인 이력 프로젝션을 LoginHistoryResponse DTO로 변환합니다.
     *
     * <p>미등록 사번은 이름 자리에 사번 원문을 두고(기존 계약 유지) 부서명·팀명은 null로 둡니다.
     */
    private AdminDto.LoginHistoryResponse toLoginHistoryResponse(
            LoginHistoryRepository.LoginHistoryView h,
            Map<String, UserRepository.UserOrgNameView> userOrgMap) {
        UserRepository.UserOrgNameView user =
                h.getEno() == null ? null : userOrgMap.get(h.getEno());
        return new AdminDto.LoginHistoryResponse(
                h.getEno(),
                user != null && user.getUsrNm() != null ? user.getUsrNm() : h.getEno(),
                user == null ? null : user.getBbrNm(),
                user == null ? null : user.getTemNm(),
                h.getLgnDtm(),
                h.getItPtlLgnTc(),
                h.getIpAddr(),
                h.getLgnErrRsn(),
                h.getAgtVrsCone(),
                h.getFstEnrDtm());
    }

    // =========================================================================
    // JWT 토큰 (TPRMPP_CRTOKM) — M7
    // =========================================================================

    /**
     * 전체 갱신토큰 목록을 조회합니다. 원문 대신 HMAC-SHA256 지문의 앞 20자만 마스킹해 표시합니다.
     *
     * @return 갱신토큰 응답 DTO 목록
     */
    public List<AdminDto.TokenResponse> getTokens() {
        return refreshTokenRepository.findAllProjectedBy().stream()
                .map(this::toTokenResponse)
                .toList();
    }

    /** 갱신토큰 프로젝션을 TokenResponse DTO로 변환합니다. DB에는 원문이 없으므로 HMAC-SHA256 지문만 마스킹합니다. */
    private AdminDto.TokenResponse toTokenResponse(RefreshTokenRepository.AdminTokenView t) {
        String lookupValue = t.getEcyRnwPubTokCone();
        String masked =
                (lookupValue != null && lookupValue.length() > 20)
                        ? lookupValue.substring(0, 20) + "..."
                        : lookupValue;
        return new AdminDto.TokenResponse(
                t.getEno(), resolveUserName(t.getEno()), t.getEndDtm(), masked, t.getFstEnrDtm());
    }

    // =========================================================================
    // 첨부파일 (TPRMPP_CFILEM) — M7
    // =========================================================================

    /**
     * 삭제되지 않은 전체 첨부파일 목록을 조회합니다.
     *
     * @return 첨부파일 응답 DTO 목록
     */
    public List<AdminDto.FileResponse> getFiles() {
        return fileRepository.findAdminFileViewsByDelYn("N").stream()
                .map(this::toFileResponse)
                .toList();
    }

    /** 파일 프로젝션을 FileResponse DTO로 변환합니다. */
    private AdminDto.FileResponse toFileResponse(FileRepository.AdminFileView f) {
        return new AdminDto.FileResponse(
                f.getFlMpnId(),
                f.getFlNm(),
                f.getFlTpCone(),
                f.getApgFlKdNm(),
                f.getFstEnrDtm(),
                f.getFstEnrUsid(),
                resolveUserName(f.getFstEnrUsid()));
    }

    // =========================================================================
    // 내부 유틸 메서드
    // =========================================================================

    /**
     * 사원번호(ENO)로 사용자명을 조회합니다. 존재하지 않으면 ENO 값을 그대로 반환합니다.
     *
     * @param eno 사원번호
     * @return 사용자명 또는 ENO
     */
    private String resolveUserName(String eno) {
        if (eno == null) return null;
        return userRepository.findNameViewByEno(eno).map(value -> value.getUsrNm()).orElse(eno);
    }

    /**
     * 사번 스트림을 한 번의 조회로 사용자명 맵으로 변환합니다.
     *
     * @param enos 사용자명을 조회할 사번 스트림
     * @return 사번별 사용자명 맵
     */
    private Map<String, String> loadUserNameMap(Stream<String> enos) {
        Set<String> enoSet = enos.filter(Objects::nonNull).collect(Collectors.toSet());
        if (enoSet.isEmpty()) {
            return Map.of();
        }
        return userRepository.findNameViewsByEnoIn(enoSet).stream()
                .filter(user -> user.getUsrNm() != null)
                .collect(Collectors.toMap(user -> user.getEno(), user -> user.getUsrNm()));
    }

    /**
     * 사번 스트림을 IN 배치 1회로 조회해 사번별 사용자명·부서명·팀명 프로젝션 맵을 만듭니다.
     *
     * @param enos 조회할 사번 스트림 (null 항목은 무시)
     * @return 사번별 프로젝션 맵 (미등록 사번은 키 없음, 입력이 비면 빈 맵이며 DB 조회 없음)
     */
    private Map<String, UserRepository.UserOrgNameView> loadUserOrgNameMap(Stream<String> enos) {
        Set<String> enoSet = enos.filter(Objects::nonNull).collect(Collectors.toSet());
        if (enoSet.isEmpty()) {
            return Map.of();
        }
        return userRepository.findOrgNameViewsByEnoIn(enoSet).stream()
                .collect(Collectors.toMap(user -> user.getEno(), user -> user, (a, b) -> a));
    }

    /**
     * 배치 조회된 사용자명 맵에서 이름을 찾고 미등록 사번은 원문을 반환합니다.
     *
     * @param eno 사번
     * @param userNameMap 사번별 사용자명 맵
     * @return 사용자명, 미등록 사번 원문 또는 null
     */
    private String resolveUserName(String eno, Map<String, String> userNameMap) {
        if (eno == null) {
            return null;
        }
        return userNameMap.getOrDefault(eno, eno);
    }

    // =========================================================================
    // 대시보드 통계 — M8
    // =========================================================================

    /**
     * 최근 30일 일별 로그인 성공 건수(접속 횟수)와 행번 중복을 제거한 접속자 수를 집계하여 반환합니다. Oracle TRUNC 함수를 사용하여 날짜 단위로
     * 그룹화합니다.
     *
     * @return 일별 로그인 통계 DTO 목록 (날짜 오름차순)
     */
    public List<AdminDto.LoginStatResponse> getLoginStats() {
        // 대시보드 차트에 사용할 최근 30일 일별 집계를 조회합니다.
        // 일자 라벨이 null/공백이면 LocalDate.parse가 예외를 던지므로 방어적으로 제외한다.
        return loginHistoryRepository.findDailyLoginStatRows().stream()
                .filter(row -> row.label() != null && !row.label().isBlank())
                .map(
                        row ->
                                new AdminDto.LoginStatResponse(
                                        LocalDate.parse(row.label()),
                                        row.count(),
                                        row.uniqueUserCount()))
                .toList();
    }
}
