package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AdminService 단위 테스트
 *
 * <p>Mockito로 모든 Repository를 Mock 처리하여 Oracle DB 없이 관리자 CRUD 비즈니스 로직(자격등급, 역할, 사용자, 조직)을 검증합니다.
 *
 * <p>공통코드 CRUD는 {@link AdminCodeService}로 분리되어 {@code AdminCodeServiceTest}가 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    private record NameView(String eno, String usrNm, String ptCNm)
            implements UserRepository.UserNameView {
        /** 직위명이 검증 대상이 아닌 기존 케이스용 축약 생성자. */
        private NameView(String eno, String usrNm) {
            this(eno, usrNm, null);
        }

        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return ptCNm;
        }
    }

    private record AdminUserView(String eno, String usrNm) implements UserRepository.AdminUserView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getPtCNm() {
            return null;
        }

        @Override
        public String getTemC() {
            return null;
        }

        @Override
        public String getTemNm() {
            return null;
        }

        @Override
        public String getBbrC() {
            return null;
        }

        @Override
        public String getEtrMilAddrNm() {
            return null;
        }

        @Override
        public String getInleNo() {
            return null;
        }

        @Override
        public String getCpnTpn() {
            return null;
        }

        @Override
        public LocalDateTime getFstEnrDtm() {
            return null;
        }

        @Override
        public LocalDateTime getLstChgDtm() {
            return null;
        }
    }

    /** 로그인 이력 응답의 이름·부서명·팀명 프로젝션 테스트 더블. */
    private record OrgNameView(String eno, String usrNm, String bbrNm, String temNm)
            implements UserRepository.UserOrgNameView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public String getUsrNm() {
            return usrNm;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }

        @Override
        public String getTemNm() {
            return temNm;
        }
    }

    private record LoginHistoryView(
            String eno,
            LocalDateTime lgnDtm,
            String itPtlLgnTc,
            String ipAddr,
            String lgnErrRsn,
            String agtVrsCone,
            LocalDateTime fstEnrDtm)
            implements LoginHistoryRepository.LoginHistoryView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public LocalDateTime getLgnDtm() {
            return lgnDtm;
        }

        @Override
        public String getItPtlLgnTc() {
            return itPtlLgnTc;
        }

        @Override
        public String getIpAddr() {
            return ipAddr;
        }

        @Override
        public String getLgnErrRsn() {
            return lgnErrRsn;
        }

        @Override
        public String getAgtVrsCone() {
            return agtVrsCone;
        }

        @Override
        public LocalDateTime getFstEnrDtm() {
            return fstEnrDtm;
        }
    }

    private record AdminTokenView(
            String eno, LocalDateTime endDtm, String ecyRnwPubTokCone, LocalDateTime fstEnrDtm)
            implements RefreshTokenRepository.AdminTokenView {
        @Override
        public String getEno() {
            return eno;
        }

        @Override
        public LocalDateTime getEndDtm() {
            return endDtm;
        }

        @Override
        public String getEcyRnwPubTokCone() {
            return ecyRnwPubTokCone;
        }

        @Override
        public LocalDateTime getFstEnrDtm() {
            return fstEnrDtm;
        }
    }

    private record AdminFileView(
            String flMpnId,
            String flNm,
            String flTpCone,
            String apgFlKdNm,
            LocalDateTime fstEnrDtm,
            String fstEnrUsid)
            implements FileRepository.AdminFileView {
        @Override
        public String getFlMpnId() {
            return flMpnId;
        }

        @Override
        public String getFlNm() {
            return flNm;
        }

        @Override
        public String getFlTpCone() {
            return flTpCone;
        }

        @Override
        public String getApgFlKdNm() {
            return apgFlKdNm;
        }

        @Override
        public LocalDateTime getFstEnrDtm() {
            return fstEnrDtm;
        }

        @Override
        public String getFstEnrUsid() {
            return fstEnrUsid;
        }
    }

    private record OrganizationAdminView(String prlmOgzCCone, String bbrNm)
            implements OrganizationRepository.OrganizationAdminView {
        @Override
        public String getPrlmOgzCCone() {
            return prlmOgzCCone;
        }

        @Override
        public String getBbrNm() {
            return bbrNm;
        }

        @Override
        public String getBbrWrenNm() {
            return null;
        }

        @Override
        public Integer getItmSqnSno() {
            return null;
        }

        @Override
        public String getPrlmHrkOgzCCone() {
            return null;
        }

        @Override
        public LocalDateTime getFstEnrDtm() {
            return null;
        }

        @Override
        public String getFstEnrUsid() {
            return null;
        }

        @Override
        public LocalDateTime getLstChgDtm() {
            return null;
        }

        @Override
        public String getLstChgUsid() {
            return null;
        }
    }

    @Mock private AuthRepository authRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository orgRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private FileRepository fileRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AdminService adminService;

    // =========================================================================
    // 자격등급 (CauthI)
    // =========================================================================

    @Test
    @DisplayName("createAuthGrade - 중복 ATH_ID 존재 시 IllegalArgumentException 발생")
    void createAuthGrade_중복ID_예외발생() {
        // given
        AdminDto.AuthGradeRequest req =
                new AdminDto.AuthGradeRequest("ITPAD001", "관리자", "관리자 자격", "Y");
        given(authRepository.existsById("ITPAD001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminService.createAuthGrade(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 자격등급ID입니다");
    }

    @Test
    @DisplayName("createAuthGrade - 정상 요청 시 authRepository.save() 호출")
    void createAuthGrade_정상요청_저장호출() {
        // given
        AdminDto.AuthGradeRequest req =
                new AdminDto.AuthGradeRequest("ITPNEW", "신규등급", "신규 자격", "Y");
        given(authRepository.existsById("ITPNEW")).willReturn(false);

        // when
        adminService.createAuthGrade(req);

        // then
        verify(authRepository, times(1)).save(any(CauthI.class));
    }

    @Test
    @DisplayName("deleteAuthGrade - 정상 삭제 시 Soft Delete (DEL_YN='Y')")
    void deleteAuthGrade_정상삭제_SoftDelete() {
        // given
        CauthI auth = CauthI.builder().athId("ITPZZ001").delYn("N").build();
        given(authRepository.findById("ITPZZ001")).willReturn(Optional.of(auth));

        // when
        adminService.deleteAuthGrade("ITPZZ001");

        // then
        assertThat(auth.getDelYn()).isEqualTo("Y");
    }

    // =========================================================================
    // 역할 (CroleI)
    // =========================================================================

    @Test
    @DisplayName("createRole - 복합키 중복 시 IllegalArgumentException 발생")
    void createRole_복합키중복_예외발생() {
        // given
        AdminDto.RoleRequest req = new AdminDto.RoleRequest("ITPAD001", "10001", "Y");
        CroleIId id = new CroleIId("ITPAD001", "10001");
        given(roleRepository.existsById(id)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminService.createRole(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 역할입니다");
    }

    @Test
    @DisplayName("deleteRole - 미존재 역할 삭제 시 IllegalArgumentException 발생")
    void deleteRole_미존재역할_예외발생() {
        // given
        CroleIId id = new CroleIId("ITPAD001", "99999");
        given(roleRepository.findById(id)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.deleteRole("ITPAD001", "99999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 역할입니다");
    }

    @Test
    @DisplayName("deleteRole - 정상 삭제 시 Soft Delete (DEL_YN='Y')")
    void deleteRole_정상삭제_SoftDelete() {
        // given
        CroleIId id = new CroleIId("ITPAD001", "10001");
        CroleI role = CroleI.builder().id(id).useYn("Y").delYn("N").build();
        given(roleRepository.findById(id)).willReturn(Optional.of(role));

        // when
        adminService.deleteRole("ITPAD001", "10001");

        // then
        assertThat(role.getDelYn()).isEqualTo("Y");
        verify(roleRepository, never()).delete(any(CroleI.class));
    }

    @Test
    @DisplayName("deleteRole - 이미 삭제된 역할은 미존재로 취급해 IllegalArgumentException 발생")
    void deleteRole_이미삭제된역할_예외발생() {
        // given
        CroleIId id = new CroleIId("ITPAD001", "10001");
        CroleI deleted = CroleI.builder().id(id).useYn("Y").delYn("Y").build();
        given(roleRepository.findById(id)).willReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> adminService.deleteRole("ITPAD001", "10001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 역할입니다");
        assertThat(deleted.getDelYn()).isEqualTo("Y");
    }

    // =========================================================================
    // 사용자 (CuserI)
    // =========================================================================

    @Test
    @DisplayName("createUser - 중복 ENO 존재 시 IllegalArgumentException 발생")
    void createUser_중복ENO_예외발생() {
        // given
        AdminDto.UserRequest req =
                new AdminDto.UserRequest("10001", "홍길동", null, null, null, null, null, null, null);
        given(userRepository.existsByEno("10001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminService.createUser(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 사원번호입니다");
    }

    @Test
    @DisplayName("createUser - password가 null이면 기본값 'changeme'로 인코딩 후 저장")
    void createUser_passwordNull_기본값인코딩() {
        // given: password 없는 요청
        AdminDto.UserRequest req =
                new AdminDto.UserRequest("10002", "김테스트", null, null, null, null, null, null, null);
        given(userRepository.existsByEno("10002")).willReturn(false);
        given(passwordEncoder.encode("changeme")).willReturn("encodedDefault");

        // when
        adminService.createUser(req);

        // then
        verify(passwordEncoder, times(1)).encode("changeme");
        verify(userRepository, times(1)).save(any(CuserI.class));
    }

    @Test
    @DisplayName("deleteUser - 미존재 사용자 삭제 시 IllegalArgumentException 발생")
    void deleteUser_미존재사용자_예외발생() {
        // given
        given(userRepository.findByEno("99999")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.deleteUser("99999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 사원번호입니다");
    }

    @Test
    @DisplayName("deleteUser - 정상 삭제 시 Soft Delete (DEL_YN='Y')")
    void deleteUser_정상삭제_SoftDelete() {
        // given
        CuserI user = CuserI.builder().eno("10001").delYn("N").build();
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));

        // when
        adminService.deleteUser("10001");

        // then
        assertThat(user.getDelYn()).isEqualTo("Y");
        verify(userRepository, never()).delete(any(CuserI.class));
    }

    @Test
    @DisplayName("deleteUser - 이미 삭제된 사용자는 미존재로 취급해 IllegalArgumentException 발생")
    void deleteUser_이미삭제된사용자_예외발생() {
        // given
        CuserI deleted = CuserI.builder().eno("10001").delYn("Y").build();
        given(userRepository.findByEno("10001")).willReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> adminService.deleteUser("10001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 사원번호입니다");
    }

    @Test
    @DisplayName("updateUser - password 포함 시 비밀번호도 함께 변경")
    void updateUser_password포함_비밀번호변경() {
        // given
        CuserI user = CuserI.builder().eno("10001").delYn("N").build();
        AdminDto.UserRequest req =
                new AdminDto.UserRequest(
                        "10001", "홍길동", null, null, null, null, null, null, "newPassword");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("newPassword")).willReturn("encodedNew");

        // when
        adminService.updateUser("10001", req);

        // then: 비밀번호 인코딩 호출 확인
        verify(passwordEncoder, times(1)).encode("newPassword");
    }

    // =========================================================================
    // 조직 (CorgnI)
    // =========================================================================

    @Test
    @DisplayName("createOrganization - 중복 조직코드 존재 시 IllegalArgumentException 발생")
    void createOrganization_중복조직코드_예외발생() {
        // given
        AdminDto.OrgRequest req = new AdminDto.OrgRequest("BBR001", "IT부문", "IT Division", 1, null);
        given(orgRepository.existsById("BBR001")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminService.createOrganization(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 조직코드입니다");
    }

    @Test
    @DisplayName("createOrganization - 정상 요청 시 요청값 그대로 조직을 저장한다")
    void createOrganization_정상요청_저장호출() {
        // given
        AdminDto.OrgRequest req =
                new AdminDto.OrgRequest("BBR002", "IT기획부", "IT Planning", 3, "BBR001");
        given(orgRepository.existsById("BBR002")).willReturn(false);

        // when
        adminService.createOrganization(req);

        // then
        ArgumentCaptor<CorgnI> captor = ArgumentCaptor.forClass(CorgnI.class);
        verify(orgRepository, times(1)).save(captor.capture());
        CorgnI saved = captor.getValue();
        assertThat(saved.getPrlmOgzCCone()).isEqualTo("BBR002");
        assertThat(saved.getBbrNm()).isEqualTo("IT기획부");
        assertThat(saved.getBbrWrenNm()).isEqualTo("IT Planning");
        assertThat(saved.getItmSqnSno()).isEqualTo(3);
        assertThat(saved.getPrlmHrkOgzCCone()).isEqualTo("BBR001");
    }

    @Test
    @DisplayName("deleteOrganization - 이미 삭제된 조직은 미존재로 취급해 IllegalArgumentException 발생")
    void deleteOrganization_이미삭제된조직_예외발생() {
        // given
        CorgnI deleted = CorgnI.builder().prlmOgzCCone("BBR001").delYn("Y").build();
        given(orgRepository.findById("BBR001")).willReturn(Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> adminService.deleteOrganization("BBR001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 조직코드입니다");
    }

    @Test
    @DisplayName("deleteOrganization - 정상 삭제 시 Soft Delete (DEL_YN='Y')")
    void deleteOrganization_정상삭제_SoftDelete() {
        // given
        // delYn("N") 명시: @PrePersist는 실제 JPA 영속 시에만 호출되므로 테스트에서 직접 설정
        CorgnI org = CorgnI.builder().prlmOgzCCone("BBR001").delYn("N").build();
        given(orgRepository.findById("BBR001")).willReturn(Optional.of(org));

        // when
        adminService.deleteOrganization("BBR001");

        // then
        assertThat(org.getDelYn()).isEqualTo("Y");
    }

    // =========================================================================
    // 자격등급 조회 및 수정 (CauthI) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getAuthGrades: 삭제되지 않은 자격등급 목록을 반환한다")
    void getAuthGrades_삭제되지않은목록반환() {
        // given: DEL_YN='N' 인 항목과 DEL_YN='Y' 인 항목 혼합
        CauthI active = CauthI.builder().athId("ITPZZ001").qlfGrNm("일반사용자").delYn("N").build();
        CauthI deleted = CauthI.builder().athId("ITPZZ999").qlfGrNm("삭제등급").delYn("Y").build();
        given(authRepository.findAll()).willReturn(List.of(active, deleted));
        // resolveUserName 호출 시 사용자명 조회 mock
        given(userRepository.findByEno(any())).willReturn(java.util.Optional.empty());

        // when
        List<AdminDto.AuthGradeResponse> result = adminService.getAuthGrades();

        // then: DEL_YN='Y' 항목은 필터링되어 1건만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).athId()).isEqualTo("ITPZZ001");
    }

    @Test
    @DisplayName("getAuthGrades: 전체 목록이 비어있으면 빈 목록을 반환한다")
    void getAuthGrades_빈목록반환() {
        // given
        given(authRepository.findAll()).willReturn(Collections.emptyList());

        // when
        List<AdminDto.AuthGradeResponse> result = adminService.getAuthGrades();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateAuthGrade: 정상 요청 시 자격등급 정보가 수정된다")
    void updateAuthGrade_정상요청_수정성공() {
        // given
        CauthI auth = CauthI.builder().athId("ITPZZ001").qlfGrNm("일반사용자").delYn("N").build();
        AdminDto.AuthGradeRequest req =
                new AdminDto.AuthGradeRequest("ITPZZ001", "수정된등급명", "수정된사항", "Y");
        given(authRepository.findById("ITPZZ001")).willReturn(java.util.Optional.of(auth));

        // when
        adminService.updateAuthGrade("ITPZZ001", req);

        // then: Dirty Checking — update() 호출 후 필드 변경 확인
        assertThat(auth.getQlfGrNm()).isEqualTo("수정된등급명");
        assertThat(auth.getQlfGrMat()).isEqualTo("수정된사항");
    }

    @Test
    @DisplayName("updateAuthGrade: 미존재 자격등급 수정 시 IllegalArgumentException 발생")
    void updateAuthGrade_미존재ID_예외발생() {
        // given
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("NONE", "수정명", "수정사항", "Y");
        given(authRepository.findById("NONE")).willReturn(java.util.Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.updateAuthGrade("NONE", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 자격등급ID입니다");
    }

    @Test
    @DisplayName("updateAuthGrade: DEL_YN='Y' 인 항목 수정 시 IllegalArgumentException 발생")
    void updateAuthGrade_삭제된항목_예외발생() {
        // given: 이미 삭제된 자격등급
        CauthI deleted = CauthI.builder().athId("ITPZZ999").delYn("Y").build();
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPZZ999", "수정명", null, "Y");
        given(authRepository.findById("ITPZZ999")).willReturn(java.util.Optional.of(deleted));

        // when & then
        assertThatThrownBy(() -> adminService.updateAuthGrade("ITPZZ999", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 자격등급ID입니다");
    }

    // =========================================================================
    // 역할 조회 및 수정 (CroleI) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getRoles: DB 페이징 결과를 반환하고 사용자명은 일괄 조회한다")
    void getRoles_DB페이징과사용자명일괄조회() {
        // given
        CroleIId activeId = new CroleIId("ITPAD001", "10001");
        CroleI active =
                CroleI.builder()
                        .id(activeId)
                        .useYn("Y")
                        .delYn("N")
                        .fstEnrUsid("90001")
                        .lstChgUsid("90002")
                        .build();
        PageRequest pageable = PageRequest.of(1, 20, Sort.by("eno"));
        given(roleRepository.findAdminRolePage(eq("홍"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(active), pageable, 41));
        given(userRepository.findNameViewsByEnoIn(anySet()))
                .willReturn(
                        List.of(
                                new NameView("10001", "홍길동"),
                                new NameView("90001", "등록자"),
                                new NameView("90002", "수정자")));

        // when
        Page<AdminDto.RoleResponse> result = adminService.getRoles(" 홍 ", pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(41);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().usrNm()).isEqualTo("홍길동");
        assertThat(result.getContent().getFirst().fstEnrUsNm()).isEqualTo("등록자");
        assertThat(result.getContent().getFirst().lstChgUsNm()).isEqualTo("수정자");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(roleRepository).findAdminRolePage(eq("홍"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("id.eno")).isNotNull();
        verify(userRepository, times(1)).findNameViewsByEnoIn(anySet());
    }

    @Test
    @DisplayName("getRoles: 정렬 미지정이면 복합키(athId, eno) 오름차순의 안정 정렬을 적용한다")
    void getRoles_정렬미지정_기본안정정렬() {
        // given
        PageRequest unsorted = PageRequest.of(0, 20);
        given(roleRepository.findAdminRolePage(eq(null), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(), unsorted, 0));

        // when
        Page<AdminDto.RoleResponse> result = adminService.getRoles(null, unsorted);

        // then
        assertThat(result.getContent()).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(roleRepository).findAdminRolePage(eq(null), pageableCaptor.capture());
        Sort applied = pageableCaptor.getValue().getSort();
        assertThat(applied.getOrderFor("id.athId")).isNotNull();
        assertThat(applied.getOrderFor("id.athId").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(applied.getOrderFor("id.eno")).isNotNull();
        assertThat(applied.getOrderFor("id.eno").getDirection()).isEqualTo(Sort.Direction.ASC);
        verify(userRepository, never()).findNameViewsByEnoIn(anySet());
    }

    @Test
    @DisplayName("getRolesForExport: 검색 결과 전체를 사용자명 일괄 조회로 변환한다")
    void getRolesForExport_전체검색결과반환() {
        CroleI role =
                CroleI.builder()
                        .id(new CroleIId("ITPAD001", "10001"))
                        .useYn("Y")
                        .delYn("N")
                        .build();
        given(roleRepository.findAdminRolesForExport(eq("홍"), any(Sort.class)))
                .willReturn(List.of(role));
        given(userRepository.findNameViewsByEnoIn(anySet()))
                .willReturn(List.of(new NameView("10001", "홍길동")));

        List<AdminDto.RoleResponse> result =
                adminService.getRolesForExport(" 홍 ", Sort.by("eno").ascending());

        assertThat(result)
                .singleElement()
                .extracting(AdminDto.RoleResponse::usrNm)
                .isEqualTo("홍길동");
        verify(userRepository, times(1)).findNameViewsByEnoIn(anySet());
    }

    @Test
    @DisplayName("updateRole: 정상 요청 시 역할 사용여부가 수정된다")
    void updateRole_정상요청_수정성공() {
        // given
        CroleIId id = new CroleIId("ITPAD001", "10001");
        CroleI role = CroleI.builder().id(id).useYn("Y").delYn("N").build();
        AdminDto.RoleRequest req = new AdminDto.RoleRequest("ITPAD001", "10001", "N");
        given(roleRepository.findById(id)).willReturn(java.util.Optional.of(role));

        // when
        adminService.updateRole("ITPAD001", "10001", req);

        // then: useYn 변경 확인
        assertThat(role.getUseYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("updateRole: 미존재 역할 수정 시 IllegalArgumentException 발생")
    void updateRole_미존재역할_예외발생() {
        // given
        CroleIId id = new CroleIId("ITPAD001", "99999");
        AdminDto.RoleRequest req = new AdminDto.RoleRequest("ITPAD001", "99999", "N");
        given(roleRepository.findById(id)).willReturn(java.util.Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.updateRole("ITPAD001", "99999", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 역할입니다");
    }

    // =========================================================================
    // 사용자 조회 (CuserI) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getUsers: 검색어를 정리하고 페이지 크기를 최대 200건으로 제한한다")
    void getUsers_검색어정리와페이지크기제한() {
        given(userRepository.findAdminUserPage(eq("홍"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(new AdminUserView("10001", "홍길동"))));

        Page<AdminDto.UserResponse> result =
                adminService.getUsers(
                        "  홍  ", PageRequest.of(0, 999, Sort.by("unknown").descending()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAdminUserPage(eq("홍"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by("eno").ascending());
        assertThat(result.getContent())
                .extracting(AdminDto.UserResponse::eno)
                .containsExactly("10001");
    }

    @Test
    @DisplayName("getUsersForExport: 검색어와 허용된 정렬을 전체 결과 조회에 전달한다")
    void getUsersForExport_검색어와정렬전달() {
        given(userRepository.findAdminUsersForExport("kim", Sort.by(Sort.Direction.DESC, "usrNm")))
                .willReturn(List.of(new AdminUserView("10001", "김사원")));

        List<AdminDto.UserResponse> result =
                adminService.getUsersForExport(" kim ", Sort.by(Sort.Direction.DESC, "usrNm"));

        assertThat(result).extracting(AdminDto.UserResponse::eno).containsExactly("10001");
    }

    // =========================================================================
    // 조직 조회 및 수정 (CorgnI) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getOrganizations: 삭제되지 않은 조직 목록을 반환한다")
    void getOrganizations_삭제되지않은목록반환() {
        // given: delYn='N' 쿼리 필터로 활성 조직만 조회됨(폐지 조직은 리포지토리 조회 결과에서 이미 제외)
        given(orgRepository.findAdminViewsByDelYn("N"))
                .willReturn(List.of(new OrganizationAdminView("BBR001", "IT부문")));

        // when
        List<AdminDto.OrgResponse> result = adminService.getOrganizations();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).prlmOgzCCone()).isEqualTo("BBR001");
        assertThat(result.get(0).bbrNm()).isEqualTo("IT부문");
        verify(orgRepository).findAdminViewsByDelYn("N");
    }

    @Test
    @DisplayName("getOrganizations: 등록자·변경자명을 배치 1회로 조회한다 (BE-26 N+1 방지)")
    void getOrganizations_사용자명_배치조회_1회() {
        OrganizationRepository.OrganizationAdminView first =
                mock(OrganizationRepository.OrganizationAdminView.class);
        given(first.getPrlmOgzCCone()).willReturn("120");
        given(first.getFstEnrUsid()).willReturn("E001");
        given(first.getLstChgUsid()).willReturn("E002");
        OrganizationRepository.OrganizationAdminView second =
                mock(OrganizationRepository.OrganizationAdminView.class);
        given(second.getPrlmOgzCCone()).willReturn("130");
        given(second.getFstEnrUsid()).willReturn("E003");
        given(second.getLstChgUsid()).willReturn("E004");

        given(orgRepository.findAdminViewsByDelYn("N")).willReturn(List.of(first, second));
        given(userRepository.findNameViewsByEnoIn(anySet())).willReturn(List.of());

        adminService.getOrganizations();

        // 배치 조회는 정확히 1회, 단건 조회는 0회여야 한다
        verify(userRepository, times(1)).findNameViewsByEnoIn(anySet());
        verify(userRepository, never()).findNameViewByEno(anyString());
    }

    @Test
    @DisplayName("updateOrganization: 정상 요청 시 조직 정보가 수정된다")
    void updateOrganization_정상요청_수정성공() {
        // given
        CorgnI org = CorgnI.builder().prlmOgzCCone("BBR001").bbrNm("IT부문").delYn("N").build();
        AdminDto.OrgRequest req =
                new AdminDto.OrgRequest("BBR001", "수정된부문명", "Updated Division", 2, null);
        given(orgRepository.findById("BBR001")).willReturn(java.util.Optional.of(org));

        // when
        adminService.updateOrganization("BBR001", req);

        // then: Dirty Checking — update() 호출 후 필드 변경 확인
        assertThat(org.getBbrNm()).isEqualTo("수정된부문명");
        assertThat(org.getBbrWrenNm()).isEqualTo("Updated Division");
    }

    @Test
    @DisplayName("updateOrganization: 미존재 조직코드 수정 시 IllegalArgumentException 발생")
    void updateOrganization_미존재조직코드_예외발생() {
        // given
        AdminDto.OrgRequest req = new AdminDto.OrgRequest("NONE", "부문명", null, null, null);
        given(orgRepository.findById("NONE")).willReturn(java.util.Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.updateOrganization("NONE", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 조직코드입니다");
    }

    // =========================================================================
    // 로그인 이력 (Clognh) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getLoginHistory: 페이지네이션으로 로그인 이력 목록을 반환한다")
    void getLoginHistory_페이지네이션목록반환() {
        // 준비
        LocalDateTime base = LocalDateTime.of(2026, 4, 1, 9, 0);
        LoginHistoryView known =
                new LoginHistoryView(
                        "KNOWN", base.plusMinutes(2), "1", "127.0.0.1", null, "known-agent", base);
        LoginHistoryView unknown =
                new LoginHistoryView(
                        "UNKNOWN",
                        base.plusMinutes(1),
                        "2",
                        "127.0.0.2",
                        "실패",
                        "unknown-agent",
                        base);
        LoginHistoryView nullEno =
                new LoginHistoryView(null, base, "3", "127.0.0.3", null, "null-agent", base);
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 10);
        Page<LoginHistoryRepository.LoginHistoryView> page =
                new PageImpl<>(List.of(known, unknown, nullEno), pageable, 3);
        given(loginHistoryRepository.findPageViewsByOrderByLgnDtmDesc(pageable)).willReturn(page);
        given(userRepository.findOrgNameViewsByEnoIn(any()))
                .willReturn(List.of(new OrgNameView("KNOWN", "사용자명", "정보기술부", "포탈팀")));

        // 실행
        Page<AdminDto.LoginHistoryResponse> result = adminService.getLoginHistory(pageable);

        // 검증: 등록 사번은 이름·부서명·팀명, 미등록 사번은 이름 자리에 사번 원문과 null 부서·팀
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent())
                .extracting(
                        response -> response.eno(),
                        response -> response.usrNm(),
                        response -> response.bbrNm(),
                        response -> response.temNm())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("KNOWN", "사용자명", "정보기술부", "포탈팀"),
                        org.assertj.core.groups.Tuple.tuple("UNKNOWN", "UNKNOWN", null, null),
                        org.assertj.core.groups.Tuple.tuple(null, null, null, null));
        verify(userRepository, times(1)).findOrgNameViewsByEnoIn(Set.of("KNOWN", "UNKNOWN"));
        verify(userRepository, times(0)).findNameViewsByEnoIn(any());
        verify(userRepository, times(0)).findNameViewByEno(any());
    }

    @Test
    @DisplayName("getLoginHistory: 이력이 없으면 빈 페이지를 반환한다")
    void getLoginHistory_이력없음_빈페이지반환() {
        // 준비
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 10);
        Page<LoginHistoryRepository.LoginHistoryView> emptyPage =
                new PageImpl<>(Collections.emptyList(), pageable, 0);
        given(loginHistoryRepository.findPageViewsByOrderByLgnDtmDesc(pageable))
                .willReturn(emptyPage);

        // 실행
        Page<AdminDto.LoginHistoryResponse> result = adminService.getLoginHistory(pageable);

        // 검증: 빈 페이지에서는 사용자·조직 조회 자체를 하지 않는다
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(userRepository, times(0)).findOrgNameViewsByEnoIn(any());
    }

    @Test
    @DisplayName("createAuthGrade: useYn이 null이면 기본값 Y로 저장한다")
    void createAuthGrade_useYnNull_기본값Y저장() {
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPNEW", "신규", null, null);
        given(authRepository.existsById("ITPNEW")).willReturn(false);

        adminService.createAuthGrade(req);

        org.mockito.ArgumentCaptor<CauthI> captor =
                org.mockito.ArgumentCaptor.forClass(CauthI.class);
        verify(authRepository).save(captor.capture());
        assertThat(captor.getValue().getUseYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("createRole: useYn이 null이면 기본값 Y로 저장한다")
    void createRole_useYnNull_기본값Y저장() {
        AdminDto.RoleRequest req = new AdminDto.RoleRequest("ITPAD001", "10001", null);
        given(roleRepository.existsById(new CroleIId("ITPAD001", "10001"))).willReturn(false);

        adminService.createRole(req);

        org.mockito.ArgumentCaptor<CroleI> captor =
                org.mockito.ArgumentCaptor.forClass(CroleI.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getUseYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteAuthGrade: 삭제된 자격등급이면 예외가 발생한다")
    void deleteAuthGrade_삭제된항목_예외발생() {
        CauthI deleted = CauthI.builder().athId("ITPZZ999").delYn("Y").build();
        given(authRepository.findById("ITPZZ999")).willReturn(Optional.of(deleted));

        assertThatThrownBy(() -> adminService.deleteAuthGrade("ITPZZ999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 자격등급ID");
    }

    @Test
    @DisplayName("createUser: password가 있으면 입력 비밀번호를 인코딩한다")
    void createUser_password있음_입력비밀번호인코딩() {
        AdminDto.UserRequest req =
                new AdminDto.UserRequest(
                        "10003", "박테스트", null, null, null, null, null, null, "secret");
        given(userRepository.existsByEno("10003")).willReturn(false);
        given(passwordEncoder.encode("secret")).willReturn("encodedSecret");

        adminService.createUser(req);

        verify(passwordEncoder).encode("secret");
    }

    @Test
    @DisplayName("updateUser: password가 공백이면 비밀번호를 변경하지 않는다")
    void updateUser_password공백_비밀번호변경안함() {
        CuserI user = CuserI.builder().eno("10001").delYn("N").build();
        AdminDto.UserRequest req =
                new AdminDto.UserRequest("10001", "홍길동", null, null, null, null, null, null, " ");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));

        adminService.updateUser("10001", req);

        verify(passwordEncoder, times(0)).encode(" ");
    }

    @Test
    @DisplayName("getTokens: SHA-256 조회값의 null과 20자 경계 및 긴 값을 정확히 마스킹한다")
    void getTokens_토큰마스킹반환() {
        LocalDateTime endDtm = LocalDateTime.of(2026, 7, 28, 9, 0);
        given(refreshTokenRepository.findAllProjectedBy())
                .willReturn(
                        List.of(
                                new AdminTokenView("E-NULL", endDtm, null, endDtm.minusDays(1)),
                                new AdminTokenView(
                                        "E-20",
                                        endDtm,
                                        "12345678901234567890",
                                        endDtm.minusDays(1)),
                                new AdminTokenView(
                                        "E-21",
                                        endDtm,
                                        "123456789012345678901",
                                        endDtm.minusDays(1)),
                                new AdminTokenView(
                                        "E-64",
                                        endDtm,
                                        "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                                        endDtm.minusDays(1))));
        given(userRepository.findNameViewByEno(any())).willReturn(Optional.empty());

        List<AdminDto.TokenResponse> result = adminService.getTokens();

        assertThat(result)
                .extracting(value -> value.tokMasked())
                .containsExactly(
                        null,
                        "12345678901234567890",
                        "12345678901234567890...",
                        "abcdef0123456789abcd...");
    }

    @Test
    @DisplayName("getFiles: 삭제되지 않은 파일만 사용자명과 함께 반환한다")
    void getFiles_삭제되지않은파일만반환() {
        AdminFileView active =
                new AdminFileView(
                        "FL_00000001",
                        "문서.pdf",
                        "첨부파일",
                        "문서",
                        LocalDateTime.of(2026, 7, 21, 9, 0),
                        "10001");
        given(fileRepository.findAdminFileViewsByDelYn("N")).willReturn(List.of(active));
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "홍길동")));

        List<AdminDto.FileResponse> result = adminService.getFiles();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).flMpnId()).isEqualTo("FL_00000001");
        assertThat(result.get(0).fstEnrUsNm()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("getLoginStats: 일별 로그인 통계를 날짜와 건수로 변환한다")
    void getLoginStats_일별통계반환() {
        given(loginHistoryRepository.findDailyLoginStatRows())
                .willReturn(
                        Collections.singletonList(
                                LoginHistoryRepository.DailyLoginStatRow.fromRow(
                                        new Object[] {"2026-05-09", 3L, 2L})));

        List<AdminDto.LoginStatResponse> result = adminService.getLoginStats();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(result.get(0).count()).isEqualTo(3L);
        assertThat(result.get(0).uniqueUserCount()).isEqualTo(2L);
    }
}
