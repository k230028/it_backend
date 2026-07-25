package com.kdb.it.common.admin.service;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기능 서비스
 *
 * <p>기존 리포지토리를 DI 받아 관리자 전용 로직을 처리합니다. 엔티티/리포지토리는 신규 생성 없이 기존 패키지를 재사용합니다.
 *
 * <p>의존 패키지:
 *
 * <ul>
 *   <li>{@code common/code} — 공통코드(Ccodem, CodeRepository)
 *   <li>{@code common/iam} — 사용자(CuserI, UserRepository) — 이름 변환용
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    // 각 관리 기능은 기존 도메인 리포지토리를 생성자 주입으로 재사용합니다.
    private final CodeRepository codeRepository;
    private final AuthRepository authRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository orgRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final FileRepository fileRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================================
    // 공통코드 (TPRMPP_CCODEM)
    // =========================================================================

    /**
     * 삭제되지 않은 전체 공통코드 목록을 조회합니다. 최초생성자·마지막수정자 사원번호를 이름으로 일괄 변환하여 반환합니다.
     *
     * @return 공통코드 응답 DTO 목록
     */
    public List<AdminDto.CodeResponse> getCodes() {
        List<Ccodem> codes = codeRepository.findAllActive();

        // 감사 필드의 고유 ENO를 한 번의 배치 쿼리로 이름 조회 (N+1 방지)
        Set<String> enos =
                codes.stream()
                        .flatMap(c -> Stream.of(c.getFstEnrUsid(), c.getLstChgUsid()))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<String, String> userNameMap =
                userRepository.findNameViewsByEnoIn(enos).stream()
                        .collect(Collectors.toMap(u -> u.getEno(), u -> u.getUsrNm()));

        return codes.stream().map(c -> toCodeResponse(c, userNameMap)).toList();
    }

    /**
     * 신규 공통코드를 추가합니다. C_ID와 시작일자가 모두 중복되면 예외를 발생시킵니다.
     *
     * @param req 공통코드 생성 요청 DTO
     * @throws IllegalArgumentException 코드ID/시작일자 중복 시
     */
    @Transactional
    public void createCode(AdminDto.CodeRequest req) {
        validateCodeKey(req.cId(), req.cdva(), req.sttDt());
        if (codeRepository.existsByCIdAndCdvaAndSttDt(req.cId(), req.cdva(), req.sttDt())) {
            throw new IllegalArgumentException(
                    "이미 존재하는 코드입니다: " + req.cId() + "/" + req.cdva() + ", " + req.sttDt());
        }
        Ccodem code =
                Ccodem.builder()
                        .cId(req.cId())
                        .cNm(req.cNm())
                        .cdvaNm(req.cdvaNm())
                        .cdva(req.cdva())
                        .cdvaDes(req.cdvaDes())
                        .cdvaDtl(req.cdvaDtl())
                        .cdvaDtlC(req.cdvaDtlC())
                        .cTp(req.cTp())
                        .cTpDes(req.cTpDes())
                        .hrkC(req.hrkC())
                        .sttDt(req.sttDt())
                        .endDt(req.endDt())
                        .cSqn(req.cSqn())
                        .build();
        codeRepository.save(code);
    }

    /**
     * 공통코드 정보를 수정합니다.
     *
     * <p>요청의 PK(cId/cdva/sttDt)가 path PK와 동일하면 Dirty Checking으로 비PK 필드만 갱신합니다. PK가 다르면 PK rename 으로
     * 간주하여 다음 절차로 처리합니다:
     *
     * <ol>
     *   <li>새 PK 충돌 검증 — 동일 PK의 활성 행이 이미 있으면 거절
     *   <li>기존 행 soft delete ({@code DEL_YN='Y'})
     *   <li>새 PK + 새 비PK 값으로 신규 행 생성·저장
     * </ol>
     *
     * <p>두 단계 모두 {@code ChangeLogEntityListener}가 자동 기록하므로 변경 이력은 보존됩니다.
     *
     * @param cId path 원본 코드ID
     * @param cdva path 원본 코드값
     * @param sttDt path 원본 시작일자
     * @param req 공통코드 수정 요청 DTO (req 안의 PK는 새 값, path와 다르면 rename)
     * @throws IllegalArgumentException 원본 코드를 찾을 수 없거나, 새 PK가 이미 존재하는 경우
     */
    @Transactional
    public void updateCode(String cId, String cdva, String sttDt, AdminDto.CodeRequest req) {
        validateCodeKey(cId, cdva, sttDt);
        Ccodem code =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 코드입니다: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));

        // 요청 PK 결정: req에 값이 있으면 새 PK, 없으면 path PK 유지
        String newCId = (req.cId() != null && !req.cId().isBlank()) ? req.cId() : cId;
        String newCdva = (req.cdva() != null && !req.cdva().isBlank()) ? req.cdva() : cdva;
        String newSttDt = (req.sttDt() != null && !req.sttDt().isBlank()) ? req.sttDt() : sttDt;

        boolean pkChanged =
                !Objects.equals(newCId, cId)
                        || !Objects.equals(newCdva, cdva)
                        || !Objects.equals(newSttDt, sttDt);

        if (!pkChanged) {
            // PK 동일 — 기존 setter 기반 update (Dirty Checking)
            code.update(
                    req.cNm(),
                    req.cdvaDes(),
                    req.cdvaDtl(),
                    req.cdvaNm(),
                    req.cTp(),
                    req.cTpDes(),
                    req.hrkC(),
                    req.cSqn(),
                    req.endDt(),
                    req.cdvaDtlC());
            return;
        }

        // PK rename — 새 PK 충돌 검증
        validateCodeKey(newCId, newCdva, newSttDt);
        if (codeRepository.existsByCIdAndCdvaAndSttDt(newCId, newCdva, newSttDt)) {
            throw new IllegalArgumentException(
                    "이미 존재하는 코드입니다: " + newCId + "/" + newCdva + ", " + newSttDt);
        }

        // 기존 행 soft delete
        code.delete();

        // 새 PK로 신규 행 생성·저장
        Ccodem renamed =
                Ccodem.builder()
                        .cId(newCId)
                        .cdva(newCdva)
                        .sttDt(newSttDt)
                        .cNm(req.cNm())
                        .cdvaNm(req.cdvaNm())
                        .cdvaDes(req.cdvaDes())
                        .cdvaDtl(req.cdvaDtl())
                        .cdvaDtlC(req.cdvaDtlC())
                        .cTp(req.cTp())
                        .cTpDes(req.cTpDes())
                        .hrkC(req.hrkC())
                        .endDt(req.endDt())
                        .cSqn(req.cSqn())
                        .build();
        codeRepository.save(renamed);
    }

    /**
     * 공통코드를 논리 삭제(Soft Delete)합니다. DEL_YN='Y' 처리 — 물리 삭제 금지.
     *
     * @param cId 코드ID
     * @param cdva 코드값
     * @param sttDt 시작일자
     * @throws IllegalArgumentException 코드를 찾을 수 없는 경우
     */
    @Transactional
    public void deleteCode(String cId, String cdva, String sttDt) {
        validateCodeKey(cId, cdva, sttDt);
        Ccodem code =
                codeRepository
                        .findByCIdAndCdvaAndSttDtAndDelYn(cId, cdva, sttDt, "N")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "존재하지 않는 코드입니다: "
                                                        + cId
                                                        + "/"
                                                        + cdva
                                                        + ", "
                                                        + sttDt));
        code.delete();
    }

    /**
     * 공통코드 일괄 업로드(Upsert) 처리합니다. 코드ID가 이미 존재하면 수정, 없으면 신규 생성합니다.
     *
     * @param req 일괄 업로드 요청 DTO (코드 목록)
     * @return 처리 결과 (created: 신규 건수, updated: 수정 건수)
     */
    @Transactional
    public Map<String, Integer> bulkUpsertCodes(AdminDto.BulkCodeRequest req) {
        for (AdminDto.CodeRequest item : req.codes()) {
            validateCodeKey(item.cId(), item.cdva(), item.sttDt());
        }

        Set<String> cIds =
                req.codes().stream().map(AdminDto.CodeRequest::cId).collect(Collectors.toSet());
        Map<CodeKey, Ccodem> byKey = new LinkedHashMap<>();
        for (Ccodem code : codeRepository.findAllByCIdInAndDelYn(cIds, "N")) {
            byKey.put(new CodeKey(code.getCId(), code.getCdva(), code.getSttDt()), code);
        }

        int created = 0;
        int updated = 0;
        List<Ccodem> newCodes = new ArrayList<>();
        for (AdminDto.CodeRequest item : req.codes()) {
            CodeKey key = new CodeKey(item.cId(), item.cdva(), item.sttDt());
            Ccodem existing = byKey.get(key);
            if (existing != null) {
                existing.update(
                        item.cNm(),
                        item.cdvaDes(),
                        item.cdvaDtl(),
                        item.cdvaNm(),
                        item.cTp(),
                        item.cTpDes(),
                        item.hrkC(),
                        item.cSqn(),
                        item.endDt(),
                        item.cdvaDtlC());
                updated++;
            } else {
                Ccodem code =
                        Ccodem.builder()
                                .cId(item.cId())
                                .cNm(item.cNm())
                                .cdvaNm(item.cdvaNm())
                                .cdva(item.cdva())
                                .cdvaDes(item.cdvaDes())
                                .cdvaDtl(item.cdvaDtl())
                                .cdvaDtlC(item.cdvaDtlC())
                                .cTp(item.cTp())
                                .cTpDes(item.cTpDes())
                                .hrkC(item.hrkC())
                                .sttDt(item.sttDt())
                                .endDt(item.endDt())
                                .cSqn(item.cSqn())
                                .build();
                newCodes.add(code);
                byKey.put(key, code);
                created++;
            }
        }
        codeRepository.saveAll(newCodes);
        return Map.of("created", created, "updated", updated);
    }

    private record CodeKey(String cId, String cdva, String sttDt) {}

    /** 공통코드 복합키 필수값을 검증합니다. */
    private void validateCodeKey(String cId, String cdva, String sttDt) {
        if (cId == null || cId.isBlank()) {
            throw new IllegalArgumentException("코드ID는 필수입니다.");
        }
        if (cdva == null || cdva.isBlank()) {
            throw new IllegalArgumentException("코드값은 필수입니다.");
        }
        if (sttDt == null || sttDt.isBlank()) {
            throw new IllegalArgumentException("시작일자는 필수입니다.");
        }
    }

    /**
     * Ccodem 엔티티를 CodeResponse DTO로 변환합니다.
     *
     * @param c 공통코드 엔티티
     * @param userNameMap ENO → 사용자명 매핑 (배치 조회 결과)
     */
    private AdminDto.CodeResponse toCodeResponse(Ccodem c, Map<String, String> userNameMap) {
        return new AdminDto.CodeResponse(
                c.getCId(),
                c.getCdva(),
                c.getCNm(),
                c.getCdvaNm(),
                c.getCdvaDes(),
                c.getCdvaDtl(),
                c.getCdvaDtlC(),
                c.getCTp(),
                c.getCTpDes(),
                c.getHrkC(),
                c.getSttDt(),
                c.getEndDt(),
                c.getCSqn(),
                c.getFstEnrDtm(),
                c.getFstEnrUsid(),
                userNameMap.getOrDefault(c.getFstEnrUsid(), c.getFstEnrUsid()),
                c.getLstChgDtm(),
                c.getLstChgUsid(),
                userNameMap.getOrDefault(c.getLstChgUsid(), c.getLstChgUsid()));
    }

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
    public List<AdminDto.RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .filter(r -> "N".equals(r.getDelYn()))
                .map(this::toRoleResponse)
                .toList();
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

    // =========================================================================
    // 사용자 (TPRMPP_CUSERI) — M5
    // =========================================================================

    /**
     * 삭제되지 않은 전체 사용자 목록을 조회합니다. DEL_YN='N' 조건으로 필터링합니다.
     *
     * @return 사용자 응답 DTO 목록
     */
    public List<AdminDto.UserResponse> getUsers() {
        List<UserRepository.AdminUserView> users = userRepository.findAdminUserViewsByDelYn("N");
        Set<String> orgCodes =
                users.stream()
                        .map(user -> user.getBbrC())
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<String, String> orgNames =
                orgRepository.findNameViewsByPrlmOgzCConeIn(orgCodes).stream()
                        .filter(organization -> organization.getBbrNm() != null)
                        .collect(
                                Collectors.toMap(
                                        organization -> organization.getPrlmOgzCCone(),
                                        organization -> organization.getBbrNm(),
                                        (left, right) -> left));
        return users.stream()
                .map(user -> toUserResponse(user, orgNames.get(user.getBbrC())))
                .toList();
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
     * 삭제되지 않은 전체 조직 목록을 조회합니다.
     *
     * @return 조직 응답 DTO 목록
     */
    public List<AdminDto.OrgResponse> getOrganizations() {
        return orgRepository.findAll().stream()
                .filter(o -> "N".equals(o.getDelYn()))
                .map(this::toOrgResponse)
                .toList();
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

    /** CorgnI 엔티티를 OrgResponse DTO로 변환합니다. */
    private AdminDto.OrgResponse toOrgResponse(CorgnI o) {
        return new AdminDto.OrgResponse(
                o.getPrlmOgzCCone(),
                o.getBbrNm(),
                o.getBbrWrenNm(),
                o.getItmSqnSno(),
                o.getPrlmHrkOgzCCone(),
                o.getFstEnrDtm(),
                o.getFstEnrUsid(),
                resolveUserName(o.getFstEnrUsid()),
                o.getLstChgDtm(),
                o.getLstChgUsid(),
                resolveUserName(o.getLstChgUsid()));
    }

    // =========================================================================
    // 로그인 이력 (TPRMPP_CLOGNH) — M7
    // =========================================================================

    /**
     * 전체 로그인 이력을 페이지네이션으로 조회합니다. ENO → 사용자명 변환을 포함합니다.
     *
     * @param pageable 페이지 정보 (최신순 정렬)
     * @return 페이지네이션된 로그인 이력 응답
     */
    public Page<AdminDto.LoginHistoryResponse> getLoginHistory(Pageable pageable) {
        Page<LoginHistoryRepository.LoginHistoryView> page =
                loginHistoryRepository.findPageViewsByOrderByLgnDtmDesc(pageable);
        Map<String, String> userNameMap =
                loadUserNameMap(page.getContent().stream().map(history -> history.getEno()));
        List<AdminDto.LoginHistoryResponse> content =
                page.getContent().stream()
                        .map(history -> toLoginHistoryResponse(history, userNameMap))
                        .toList();
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /** 로그인 이력 프로젝션을 LoginHistoryResponse DTO로 변환합니다. */
    private AdminDto.LoginHistoryResponse toLoginHistoryResponse(
            LoginHistoryRepository.LoginHistoryView h, Map<String, String> userNameMap) {
        return new AdminDto.LoginHistoryResponse(
                h.getEno(),
                resolveUserName(h.getEno(), userNameMap),
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
     * 전체 갱신토큰 목록을 조회합니다. 원문 대신 SHA-256 조회값의 앞 20자만 마스킹해 표시합니다.
     *
     * @return 갱신토큰 응답 DTO 목록
     */
    public List<AdminDto.TokenResponse> getTokens() {
        return refreshTokenRepository.findAllProjectedBy().stream()
                .map(this::toTokenResponse)
                .toList();
    }

    /** 갱신토큰 프로젝션을 TokenResponse DTO로 변환합니다. DB에는 원문이 없으므로 SHA-256 조회값만 마스킹합니다. */
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
                f.getPkColNm(),
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
     * 최근 30일 일별 로그인 성공 건수를 집계하여 반환합니다. Oracle TRUNC 함수를 사용하여 날짜 단위로 그룹화합니다.
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
                                        LocalDate.parse(row.label()), row.count()))
                .toList();
    }
}
