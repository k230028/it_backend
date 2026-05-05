package com.kdb.it.common.admin.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.kdb.it.common.system.entity.Clognh;
import com.kdb.it.common.system.entity.Crtokm;
import com.kdb.it.common.system.repository.LoginHistoryRepository;
import com.kdb.it.common.system.repository.RefreshTokenRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 기능 서비스
 *
 * <p>
 * 기존 리포지토리를 DI 받아 관리자 전용 로직을 처리합니다.
 * 엔티티/리포지토리는 신규 생성 없이 기존 패키지를 재사용합니다.
 * </p>
 *
 * <p>
 * 의존 패키지:
 * </p>
 * <ul>
 * <li>{@code common/code} — 공통코드(Ccodem, CodeRepository)</li>
 * <li>{@code common/iam} — 사용자(CuserI, UserRepository) — 이름 변환용</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

        // Design Ref: §2.3 — 기존 리포지토리 DI 재사용, 신규 리포지토리 생성 없음
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
        // 공통코드 (TAAABB_CCODEM)
        // =========================================================================

        /**
         * 삭제되지 않은 전체 공통코드 목록을 조회합니다.
         * 최초생성자·마지막수정자 사원번호를 이름으로 일괄 변환하여 반환합니다.
         *
         * @return 공통코드 응답 DTO 목록
         */
        public List<AdminDto.CodeResponse> getCodes() {
                List<Ccodem> codes = codeRepository.findAllActive();

                // 감사 필드의 고유 ENO를 한 번의 배치 쿼리로 이름 조회 (N+1 방지)
                Set<String> enos = codes.stream()
                                .flatMap(c -> Stream.of(c.getFstEnrUsid(), c.getLstChgUsid()))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());
                Map<String, String> userNameMap = userRepository.findByEnoIn(enos).stream()
                                .collect(Collectors.toMap(u -> u.getEno(), u -> u.getUsrNm()));

                return codes.stream()
                                .map(c -> toCodeResponse(c, userNameMap))
                                .toList();
        }

        /**
         * 신규 공통코드를 추가합니다.
         * C_ID와 시작일자가 모두 중복되면 예외를 발생시킵니다.
         *
         * @param req 공통코드 생성 요청 DTO
         * @throws IllegalArgumentException 코드ID/시작일자 중복 시
         */
        @Transactional
        public void createCode(AdminDto.CodeRequest req) {
                validateCodeKey(req.cdId(), req.sttDt());
                if (codeRepository.existsByCdIdAndSttDt(req.cdId(), req.sttDt())) {
                        throw new IllegalArgumentException("이미 존재하는 코드ID/시작일자입니다: " + req.cdId() + ", " + req.sttDt());
                }
                Ccodem code = Ccodem.builder()
                                .cdId(req.cdId())
                                .cdNm(req.cdNm())
                                .cdva(req.cdva())
                                .cdDes(req.cdDes())
                                .cttTp(req.cttTp())
                                .cttTpDes(req.cttTpDes())
                                .sttDt(req.sttDt())
                                .endDt(req.endDt())
                                .cdSqn(req.cdSqn())
                                .build();
                codeRepository.save(code);
        }

        /**
         * 공통코드 정보를 수정합니다.
         * Dirty Checking을 활용하여 별도 save() 호출 없이 변경사항을 반영합니다.
         *
         * @param cdId 코드ID
         * @param sttDt 시작일자
         * @param req  공통코드 수정 요청 DTO
         * @throws IllegalArgumentException 코드를 찾을 수 없는 경우
         */
        @Transactional
        public void updateCode(String cdId, LocalDate sttDt, AdminDto.CodeRequest req) {
                validateCodeKey(cdId, sttDt);
                Ccodem code = codeRepository.findByCdIdAndSttDtAndDelYn(cdId, sttDt, "N")
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코드ID/시작일자입니다: " + cdId + ", " + sttDt));
                if (req.sttDt() != null && !req.sttDt().equals(sttDt)) {
                        throw new IllegalArgumentException("시작일자는 기본키이므로 수정할 수 없습니다.");
                }
                // Dirty Checking — save() 불필요
                code.update(req.cdNm(), req.cdva(), req.cdDes(), req.cttTp(),
                                req.cttTpDes(), req.cdSqn(), sttDt, req.endDt());
        }

        /**
         * 공통코드를 논리 삭제(Soft Delete)합니다.
         * DEL_YN='Y' 처리 — 물리 삭제 금지.
         *
         * @param cdId 코드ID
         * @param sttDt 시작일자
         * @throws IllegalArgumentException 코드를 찾을 수 없는 경우
         */
        @Transactional
        public void deleteCode(String cdId, LocalDate sttDt) {
                // Plan SC: Soft Delete 요구사항 (C-08)
                validateCodeKey(cdId, sttDt);
                Ccodem code = codeRepository.findByCdIdAndSttDtAndDelYn(cdId, sttDt, "N")
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 코드ID/시작일자입니다: " + cdId + ", " + sttDt));
                code.delete();
        }

        /**
         * 공통코드 일괄 업로드(Upsert) 처리합니다.
         * 코드ID가 이미 존재하면 수정, 없으면 신규 생성합니다.
         *
         * @param req 일괄 업로드 요청 DTO (코드 목록)
         * @return 처리 결과 (created: 신규 건수, updated: 수정 건수)
         */
        @Transactional
        public Map<String, Integer> bulkUpsertCodes(AdminDto.BulkCodeRequest req) {
                int created = 0;
                int updated = 0;
                for (AdminDto.CodeRequest item : req.codes()) {
                        validateCodeKey(item.cdId(), item.sttDt());
                        if (codeRepository.existsByCdIdAndSttDt(item.cdId(), item.sttDt())) {
                                Ccodem code = codeRepository.findByCdIdAndSttDtAndDelYn(item.cdId(), item.sttDt(), "N")
                                                .orElse(null);
                                if (code != null) {
                                        code.update(item.cdNm(), item.cdva(), item.cdDes(), item.cttTp(),
                                                        item.cttTpDes(), item.cdSqn(), item.sttDt(), item.endDt());
                                        updated++;
                                }
                        } else {
                                Ccodem code = Ccodem.builder()
                                                .cdId(item.cdId())
                                                .cdNm(item.cdNm())
                                                .cdva(item.cdva())
                                                .cdDes(item.cdDes())
                                                .cttTp(item.cttTp())
                                                .cttTpDes(item.cttTpDes())
                                                .sttDt(item.sttDt())
                                                .endDt(item.endDt())
                                                .cdSqn(item.cdSqn())
                                                .build();
                                codeRepository.save(code);
                                created++;
                        }
                }
                return Map.of("created", created, "updated", updated);
        }

        /**
         * 공통코드 복합키 필수값을 검증합니다.
         */
        private void validateCodeKey(String cdId, LocalDate sttDt) {
                if (cdId == null || cdId.isBlank()) {
                        throw new IllegalArgumentException("코드ID는 필수입니다.");
                }
                if (sttDt == null) {
                        throw new IllegalArgumentException("시작일자는 필수입니다.");
                }
        }

        /**
         * Ccodem 엔티티를 CodeResponse DTO로 변환합니다.
         *
         * @param c           공통코드 엔티티
         * @param userNameMap ENO → 사용자명 매핑 (배치 조회 결과)
         */
        private AdminDto.CodeResponse toCodeResponse(Ccodem c, Map<String, String> userNameMap) {
                return new AdminDto.CodeResponse(
                                c.getCdId(),
                                c.getCdNm(),
                                c.getCdva(),
                                c.getCdDes(),
                                c.getCttTp(),
                                c.getCttTpDes(),
                                c.getSttDt(),
                                c.getEndDt(),
                                c.getCdSqn(),
                                c.getFstEnrDtm(),
                                c.getFstEnrUsid(),
                                userNameMap.getOrDefault(c.getFstEnrUsid(), c.getFstEnrUsid()),
                                c.getLstChgDtm(),
                                c.getLstChgUsid(),
                                userNameMap.getOrDefault(c.getLstChgUsid(), c.getLstChgUsid()));
        }

        // =========================================================================
        // 자격등급 (TAAABB_CAUTHI) — M3
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
         * 신규 자격등급을 추가합니다.
         * ATH_ID 중복 시 예외를 발생시킵니다.
         *
         * @param req 자격등급 생성 요청 DTO
         */
        @Transactional
        public void createAuthGrade(AdminDto.AuthGradeRequest req) {
                if (authRepository.existsById(req.athId())) {
                        throw new IllegalArgumentException("이미 존재하는 자격등급ID입니다: " + req.athId());
                }
                authRepository.save(CauthI.builder()
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
         * @param req   자격등급 수정 요청 DTO
         */
        @Transactional
        public void updateAuthGrade(String athId, AdminDto.AuthGradeRequest req) {
                CauthI auth = authRepository.findById(athId)
                                .filter(a -> "N".equals(a.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자격등급ID입니다: " + athId));
                auth.update(req.qlfGrNm(), req.qlfGrMat(), req.useYn());
        }

        /**
         * 자격등급을 논리 삭제합니다.
         *
         * @param athId 자격등급ID
         */
        @Transactional
        public void deleteAuthGrade(String athId) {
                CauthI auth = authRepository.findById(athId)
                                .filter(a -> "N".equals(a.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 자격등급ID입니다: " + athId));
                auth.delete();
        }

        /**
         * CauthI 엔티티를 AuthGradeResponse DTO로 변환합니다.
         */
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
        // 역할 (TAAABB_CROLEI) — M4
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
                roleRepository.save(CroleI.builder()
                                .id(id)
                                .useYn(req.useYn() != null ? req.useYn() : "Y")
                                .build());
        }

        /**
         * 역할 사용여부를 수정합니다. Dirty Checking 활용.
         *
         * @param athId 자격등급ID
         * @param eno   사원번호
         * @param req   역할 수정 요청 DTO
         */
        @Transactional
        public void updateRole(String athId, String eno, AdminDto.RoleRequest req) {
                CroleI role = roleRepository.findById(new CroleIId(athId, eno))
                                .filter(r -> "N".equals(r.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "존재하지 않는 역할입니다: athId=" + athId + ", eno=" + eno));
                role.updateUseYn(req.useYn());
        }

        /**
         * 역할을 논리 삭제합니다.
         *
         * @param athId 자격등급ID
         * @param eno   사원번호
         */
        @Transactional
        public void deleteRole(String athId, String eno) {
                CroleI role = roleRepository.findById(new CroleIId(athId, eno))
                                .filter(r -> "N".equals(r.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "존재하지 않는 역할입니다: athId=" + athId + ", eno=" + eno));
                role.delete();
        }

        /**
         * CroleI 엔티티를 RoleResponse DTO로 변환합니다.
         */
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
        // 사용자 (TAAABB_CUSERI) — M5
        // =========================================================================

        /**
         * 삭제되지 않은 전체 사용자 목록을 조회합니다.
         * DEL_YN='N' 조건으로 필터링합니다.
         *
         * @return 사용자 응답 DTO 목록
         */
        public List<AdminDto.UserResponse> getUsers() {
                return userRepository.findAll().stream()
                                .filter(u -> "N".equals(u.getDelYn()))
                                .map(this::toUserResponse)
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
                String encodedPwd = req.password() != null
                                ? passwordEncoder.encode(req.password())
                                : passwordEncoder.encode("changeme"); // 초기 비밀번호 기본값
                userRepository.save(CuserI.builder()
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
         * 사용자 기본정보를 수정합니다. Dirty Checking 활용.
         * password 필드가 있으면 비밀번호도 함께 변경합니다.
         *
         * @param eno 사원번호
         * @param req 사용자 수정 요청 DTO
         */
        @Transactional
        public void updateUser(String eno, AdminDto.UserRequest req) {
                CuserI user = userRepository.findByEno(eno)
                                .filter(u -> "N".equals(u.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호입니다: " + eno));
                user.update(req.usrNm(), req.ptCNm(), req.temC(), req.bbrC(),
                                req.etrMilAddrNm(), req.inleNo(), req.cpnTpn());
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
                CuserI user = userRepository.findByEno(eno)
                                .filter(u -> "N".equals(u.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원번호입니다: " + eno));
                user.delete();
        }

        /**
         * CuserI 엔티티를 UserResponse DTO로 변환합니다.
         */
        private AdminDto.UserResponse toUserResponse(CuserI u) {
                return new AdminDto.UserResponse(
                                u.getEno(),
                                u.getUsrNm(),
                                u.getPtCNm(),
                                u.getTemC(),
                                u.getTemNm(),
                                u.getBbrC(),
                                u.getBbrNm(),
                                u.getEtrMilAddrNm(),
                                u.getInleNo(),
                                u.getCpnTpn(),
                                u.getFstEnrDtm(),
                                u.getLstChgDtm());
        }

        // =========================================================================
        // 조직 (TAAABB_CORGNI) — M6
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
                orgRepository.save(CorgnI.builder()
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
         * @param req  조직 수정 요청 DTO
         */
        @Transactional
        public void updateOrganization(String orgC, AdminDto.OrgRequest req) {
                CorgnI org = orgRepository.findById(orgC)
                                .filter(o -> "N".equals(o.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 조직코드입니다: " + orgC));
                org.update(req.bbrNm(), req.bbrWrenNm(), req.itmSqnSno(), req.prlmHrkOgzCCone());
        }

        /**
         * 조직을 논리 삭제합니다.
         *
         * @param orgC 조직코드
         */
        @Transactional
        public void deleteOrganization(String orgC) {
                CorgnI org = orgRepository.findById(orgC)
                                .filter(o -> "N".equals(o.getDelYn()))
                                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 조직코드입니다: " + orgC));
                org.delete();
        }

        /**
         * CorgnI 엔티티를 OrgResponse DTO로 변환합니다.
         */
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
        // 로그인 이력 (TAAABB_CLOGNH) — M7
        // =========================================================================

        /**
         * 전체 로그인 이력을 페이지네이션으로 조회합니다.
         * ENO → 사용자명 변환을 포함합니다.
         *
         * @param pageable 페이지 정보 (최신순 정렬)
         * @return 페이지네이션된 로그인 이력 응답
         */
        public Page<AdminDto.LoginHistoryResponse> getLoginHistory(Pageable pageable) {
                Page<Clognh> page = loginHistoryRepository.findAllByOrderByLgnDtmDesc(pageable);
                List<AdminDto.LoginHistoryResponse> content = page.getContent().stream()
                                .map(this::toLoginHistoryResponse)
                                .toList();
                return new PageImpl<>(content, pageable, page.getTotalElements());
        }

        /**
         * Clognh 엔티티를 LoginHistoryResponse DTO로 변환합니다.
         */
        private AdminDto.LoginHistoryResponse toLoginHistoryResponse(Clognh h) {
                return new AdminDto.LoginHistoryResponse(
                                h.getEno(),
                                resolveUserName(h.getEno()),
                                h.getLgnDtm(),
                                h.getLgnTp(),
                                h.getIpAddr(),
                                h.getFlurRsn(),
                                h.getUstAgt(),
                                h.getFstEnrDtm());
        }

        // =========================================================================
        // JWT 토큰 (TAAABB_CRTOKM) — M7
        // =========================================================================

        /**
         * 전체 갱신토큰 목록을 조회합니다.
         * 토큰값은 앞 20자 + "..." 마스킹 처리합니다.
         *
         * @return 갱신토큰 응답 DTO 목록
         */
        public List<AdminDto.TokenResponse> getTokens() {
                return refreshTokenRepository.findAll().stream()
                                .map(this::toTokenResponse)
                                .toList();
        }

        /**
         * Crtokm 엔티티를 TokenResponse DTO로 변환합니다.
         * Plan SC: JWT 토큰값 마스킹 — 보안 요구사항
         */
        private AdminDto.TokenResponse toTokenResponse(Crtokm t) {
                // 토큰값 마스킹: 앞 20자 + "..."
                String raw = t.getTok();
                String masked = (raw != null && raw.length() > 20) ? raw.substring(0, 20) + "..." : raw;
                return new AdminDto.TokenResponse(
                                t.getEno(),
                                resolveUserName(t.getEno()),
                                t.getEndDtm(),
                                masked,
                                t.getFstEnrDtm());
        }

        // =========================================================================
        // 첨부파일 (TAAABB_CFILEM) — M7
        // =========================================================================

        /**
         * 삭제되지 않은 전체 첨부파일 목록을 조회합니다.
         *
         * @return 첨부파일 응답 DTO 목록
         */
        public List<AdminDto.FileResponse> getFiles() {
                return fileRepository.findAll().stream()
                                .filter(f -> "N".equals(f.getDelYn()))
                                .map(this::toFileResponse)
                                .toList();
        }

        /**
         * Cfilem 엔티티를 FileResponse DTO로 변환합니다.
         */
        private AdminDto.FileResponse toFileResponse(Cfilem f) {
                return new AdminDto.FileResponse(
                                f.getFlMngNo(),
                                f.getOrcFlNm(),
                                f.getFlDtt(),
                                f.getOrcDtt(),
                                f.getFstEnrDtm(),
                                f.getFstEnrUsid(),
                                resolveUserName(f.getFstEnrUsid()));
        }

        // =========================================================================
        // 내부 유틸 메서드
        // =========================================================================

        /**
         * 사원번호(ENO)로 사용자명을 조회합니다.
         * 존재하지 않으면 ENO 값을 그대로 반환합니다.
         *
         * @param eno 사원번호
         * @return 사용자명 또는 ENO
         */
        private String resolveUserName(String eno) {
                if (eno == null)
                        return null;
                return userRepository.findByEno(eno)
                                .map(CuserI::getUsrNm)
                                .orElse(eno);
        }

        // =========================================================================
        // 대시보드 통계 — M8
        // =========================================================================

        /**
         * 최근 30일 일별 로그인 성공 건수를 집계하여 반환합니다.
         * Oracle TRUNC 함수를 사용하여 날짜 단위로 그룹화합니다.
         *
         * @return 일별 로그인 통계 DTO 목록 (날짜 오름차순)
         */
        public List<AdminDto.LoginStatResponse> getLoginStats() {
                // Design Ref: §3.7 — 대시보드 차트 데이터 (최근 30일 일별 집계)
                return loginHistoryRepository.findDailyLoginStats().stream()
                                .map(row -> new AdminDto.LoginStatResponse(
                                                LocalDate.parse((String) row[0]),
                                                ((Number) row[1]).longValue()))
                                .toList();
        }
}
