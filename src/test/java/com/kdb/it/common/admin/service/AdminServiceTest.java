package com.kdb.it.common.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.kdb.it.common.admin.dto.AdminDto;
import com.kdb.it.common.util.LabeledCountRow;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * AdminService 단위 테스트
 *
 * <p>
 * Mockito로 모든 Repository를 Mock 처리하여 Oracle DB 없이
 * 관리자 CRUD 비즈니스 로직(공통코드, 자격등급, 역할, 사용자, 조직)을 검증합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminServiceTest {

    private record NameView(String eno, String usrNm) implements UserRepository.UserNameView {
        @Override public String getEno() { return eno; }
        @Override public String getUsrNm() { return usrNm; }
    }

    private record AdminUserView(String eno, String usrNm) implements UserRepository.AdminUserView {
        @Override public String getEno() { return eno; }
        @Override public String getUsrNm() { return usrNm; }
        @Override public String getPtCNm() { return null; }
        @Override public String getTemC() { return null; }
        @Override public String getTemNm() { return null; }
        @Override public String getBbrC() { return null; }
        @Override public String getEtrMilAddrNm() { return null; }
        @Override public String getInleNo() { return null; }
        @Override public String getCpnTpn() { return null; }
        @Override public LocalDateTime getFstEnrDtm() { return null; }
        @Override public LocalDateTime getLstChgDtm() { return null; }
    }

    @Mock
    private CodeRepository codeRepository;
    @Mock
    private AuthRepository authRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationRepository orgRepository;
    @Mock
    private LoginHistoryRepository loginHistoryRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private FileRepository fileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    // =========================================================================
    // 공통코드 (Ccodem)
    // =========================================================================

    @Test
    @DisplayName("createCode - 중복 코드ID 존재 시 IllegalArgumentException 발생")
    void createCode_중복코드ID_예외발생() {
        // given: 이미 존재하는 코드ID
        String sttDt = "20260101";
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt, null, 1);
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE001", "001", sttDt)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> adminService.createCode(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 코드입니다");
    }

    @Test
    @DisplayName("createCode - 정상 요청 시 codeRepository.save() 호출")
    void createCode_정상요청_저장호출() {
        // given
        String sttDt = "20260101";
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE002", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt, null, 1);
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE002", "001", sttDt)).willReturn(false);

        // when
        adminService.createCode(req);

        // then
        verify(codeRepository, times(1)).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("updateCode - 미존재 코드ID 수정 시 IllegalArgumentException 발생")
    void updateCode_미존재코드ID_예외발생() {
        // given
        String sttDt = "20260101";
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "NONE", "001", "코드명", "코드값명", "설명", "값", "구분", "구분설명", null, null, sttDt, null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("NONE", "001", sttDt, "N")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminService.updateCode("NONE", "001", sttDt, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 코드입니다");
    }

    @Test
    @DisplayName("deleteCode - 정상 삭제 시 code.delete() 호출 (Soft Delete)")
    void deleteCode_정상삭제_SoftDelete() {
        // given
        String sttDt = "20260101";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N")).willReturn(Optional.of(code));

        // when
        adminService.deleteCode("CODE001", "001", sttDt);

        // then: DEL_YN='Y' 처리 검증
        assertThat(code.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("bulkUpsertCodes - 신규/수정 건수를 정확히 반환한다")
    void bulkUpsertCodes_신규수정건수반환() {
        // given: CODE001은 기존 존재, CODE002는 신규
        String sttDt = "20260101";
        AdminDto.CodeRequest req1 = new AdminDto.CodeRequest("CODE001", "001", "코드1", null, null, null, null, null, null, null, sttDt, null, 1);
        AdminDto.CodeRequest req2 = new AdminDto.CodeRequest("CODE002", "002", "코드2", null, null, null, null, null, null, null, sttDt, null, 2);
        AdminDto.BulkCodeRequest bulkReq = new AdminDto.BulkCodeRequest(List.of(req1, req2));

        Ccodem existingCode = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE001", "001", sttDt)).willReturn(true);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N")).willReturn(Optional.of(existingCode));
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE002", "002", sttDt)).willReturn(false);

        // when
        var result = adminService.bulkUpsertCodes(bulkReq);

        // then
        assertThat(result.get("updated")).isEqualTo(1);
        assertThat(result.get("created")).isEqualTo(1);
        verify(codeRepository, times(1)).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("updateCode - 복합키가 같으면 기존 코드의 일반 필드만 수정한다")
    void updateCode_동일키_기존항목수정() {
        String sttDt = "20260101";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).cNm("기존").build();
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE001", "001", "수정", "코드값명", "설명", "값", "상세", "타입", "타입설명", null, sttDt, null, 2);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        adminService.updateCode("CODE001", "001", sttDt, req);

        assertThat(code.getCNm()).isEqualTo("수정");
    }

    @Test
    @DisplayName("updateCode - 복합키 변경 시 기존 코드를 삭제하고 새 코드를 저장한다")
    void updateCode_키변경_새항목저장() {
        String sttDt = "20260101";
        String newSttDt = "20260201";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE002", "002", "신규키", null, null, null, null, null, null, null, newSttDt, null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));

        adminService.updateCode("CODE001", "001", sttDt, req);

        assertThat(code.getDelYn()).isEqualTo("Y");
        verify(codeRepository).save(any(Ccodem.class));
    }

    @Test
    @DisplayName("updateCode - 변경 대상 복합키가 이미 있으면 저장을 거절한다")
    void updateCode_변경키중복_예외발생() {
        String sttDt = "20260101";
        String newSttDt = "20260201";
        Ccodem code = Ccodem.builder().cId("CODE001").cdva("001").sttDt(sttDt).build();
        AdminDto.CodeRequest req = new AdminDto.CodeRequest(
                "CODE002", "002", "신규키", null, null, null, null, null, null, null, newSttDt, null, 1);
        given(codeRepository.findByCIdAndCdvaAndSttDtAndDelYn("CODE001", "001", sttDt, "N"))
                .willReturn(Optional.of(code));
        given(codeRepository.existsByCIdAndCdvaAndSttDt("CODE002", "002", newSttDt)).willReturn(true);

        assertThatThrownBy(() -> adminService.updateCode("CODE001", "001", sttDt, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 존재하는 코드입니다");
    }

    @Test
    @DisplayName("createCode - 코드 키 필수값이 없으면 각각 예외를 반환한다")
    void createCode_필수키누락_예외발생() {
        String date = "20260101";
        AdminDto.CodeRequest noId = new AdminDto.CodeRequest(
                " ", "001", null, null, null, null, null, null, null, null, date, null, 1);
        AdminDto.CodeRequest noValue = new AdminDto.CodeRequest(
                "CODE", null, null, null, null, null, null, null, null, null, date, null, 1);
        AdminDto.CodeRequest noDate = new AdminDto.CodeRequest(
                "CODE", "001", null, null, null, null, null, null, null, null, null, null, 1);

        assertThatThrownBy(() -> adminService.createCode(noId)).hasMessageContaining("코드ID");
        assertThatThrownBy(() -> adminService.createCode(noValue)).hasMessageContaining("코드값");
        assertThatThrownBy(() -> adminService.createCode(noDate)).hasMessageContaining("시작일자");
    }

    // =========================================================================
    // 자격등급 (CauthI)
    // =========================================================================

    @Test
    @DisplayName("createAuthGrade - 중복 ATH_ID 존재 시 IllegalArgumentException 발생")
    void createAuthGrade_중복ID_예외발생() {
        // given
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPAD001", "관리자", "관리자 자격", "Y");
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
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPNEW", "신규등급", "신규 자격", "Y");
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

    // =========================================================================
    // 사용자 (CuserI)
    // =========================================================================

    @Test
    @DisplayName("createUser - 중복 ENO 존재 시 IllegalArgumentException 발생")
    void createUser_중복ENO_예외발생() {
        // given
        AdminDto.UserRequest req = new AdminDto.UserRequest(
                "10001", "홍길동", null, null, null, null, null, null, null);
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
        AdminDto.UserRequest req = new AdminDto.UserRequest(
                "10002", "김테스트", null, null, null, null, null, null, null);
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
    @DisplayName("updateUser - password 포함 시 비밀번호도 함께 변경")
    void updateUser_password포함_비밀번호변경() {
        // given
        CuserI user = CuserI.builder().eno("10001").delYn("N").build();
        AdminDto.UserRequest req = new AdminDto.UserRequest(
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

    @Test
    @DisplayName("getCodes - 활성 코드 목록을 반환하며 감사 필드를 이름으로 변환한다")
    void getCodes_활성코드목록반환() {
        // given
        Ccodem code = Ccodem.builder().cId("CODE001").cNm("코드1").build();
        given(codeRepository.findAllActive()).willReturn(List.of(code));
        given(userRepository.findNameViewsByEnoIn(any())).willReturn(Collections.emptyList());

        // when
        List<AdminDto.CodeResponse> result = adminService.getCodes();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).cId()).isEqualTo("CODE001");
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
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPZZ001", "수정된등급명", "수정된사항", "Y");
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
    @DisplayName("getRoles: 삭제되지 않은 역할 목록을 반환한다")
    void getRoles_삭제되지않은목록반환() {
        // given: DEL_YN='N'/'Y' 혼합
        CroleIId activeId = new CroleIId("ITPAD001", "10001");
        CroleIId deletedId = new CroleIId("ITPAD001", "99999");
        CroleI active = CroleI.builder().id(activeId).useYn("Y").delYn("N").build();
        CroleI deleted = CroleI.builder().id(deletedId).useYn("N").delYn("Y").build();
        given(roleRepository.findAll()).willReturn(List.of(active, deleted));
        given(userRepository.findByEno(any())).willReturn(java.util.Optional.empty());

        // when
        List<AdminDto.RoleResponse> result = adminService.getRoles();

        // then: DEL_YN='Y' 항목은 제외하고 1건만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).athId()).isEqualTo("ITPAD001");
        assertThat(result.get(0).eno()).isEqualTo("10001");
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
    @DisplayName("getUsers: 삭제되지 않은 사용자 목록을 반환한다")
    void getUsers_삭제되지않은목록반환() {
        // given: DEL_YN='N'/'Y' 혼합
        given(userRepository.findAdminUserViewsByDelYn("N"))
                .willReturn(List.of(new AdminUserView("10001", "홍길동")));

        // when
        List<AdminDto.UserResponse> result = adminService.getUsers();

        // then: DEL_YN='Y' 항목 제외하여 1건만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).eno()).isEqualTo("10001");
        assertThat(result.get(0).usrNm()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("getUsers: 사용자가 없으면 빈 목록을 반환한다")
    void getUsers_빈목록반환() {
        // given
        given(userRepository.findAdminUserViewsByDelYn("N")).willReturn(Collections.emptyList());

        // when
        List<AdminDto.UserResponse> result = adminService.getUsers();

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // 조직 조회 및 수정 (CorgnI) — 추가 테스트
    // =========================================================================

    @Test
    @DisplayName("getOrganizations: 삭제되지 않은 조직 목록을 반환한다")
    void getOrganizations_삭제되지않은목록반환() {
        // given: DEL_YN='N'/'Y' 혼합
        CorgnI active = CorgnI.builder().prlmOgzCCone("BBR001").bbrNm("IT부문").delYn("N").build();
        CorgnI deleted = CorgnI.builder().prlmOgzCCone("BBR999").bbrNm("폐지부서").delYn("Y").build();
        given(orgRepository.findAll()).willReturn(List.of(active, deleted));
        given(userRepository.findByEno(any())).willReturn(java.util.Optional.empty());

        // when
        List<AdminDto.OrgResponse> result = adminService.getOrganizations();

        // then: DEL_YN='Y' 항목 제외하여 1건만 반환
        assertThat(result).hasSize(1);
        assertThat(result.get(0).prlmOgzCCone()).isEqualTo("BBR001");
        assertThat(result.get(0).bbrNm()).isEqualTo("IT부문");
    }

    @Test
    @DisplayName("updateOrganization: 정상 요청 시 조직 정보가 수정된다")
    void updateOrganization_정상요청_수정성공() {
        // given
        CorgnI org = CorgnI.builder().prlmOgzCCone("BBR001").bbrNm("IT부문").delYn("N").build();
        AdminDto.OrgRequest req = new AdminDto.OrgRequest("BBR001", "수정된부문명", "Updated Division", 2, null);
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
        // given
        Clognh log = Clognh.builder()
                .eno("10001")
                .itPtlLgnTc("1")
                .ipAddr("127.0.0.1")
                .lgnDtm(java.time.LocalDateTime.of(2026, 4, 1, 9, 0))
                .build();
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 10);
        Page<Clognh> page = new PageImpl<>(List.of(log), pageable, 1);
        given(loginHistoryRepository.findAllByOrderByLgnDtmDesc(pageable)).willReturn(page);
        // resolveUserName 조회 mock
        given(userRepository.findByEno("10001")).willReturn(java.util.Optional.empty());

        // when
        Page<AdminDto.LoginHistoryResponse> result = adminService.getLoginHistory(pageable);

        // then: 1건 반환, ENO와 로그인 타입 검증
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).eno()).isEqualTo("10001");
        assertThat(result.getContent().get(0).itPtlLgnTc()).isEqualTo("1");
    }

    @Test
    @DisplayName("getLoginHistory: 이력이 없으면 빈 페이지를 반환한다")
    void getLoginHistory_이력없음_빈페이지반환() {
        // given
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 10);
        Page<Clognh> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        given(loginHistoryRepository.findAllByOrderByLgnDtmDesc(pageable)).willReturn(emptyPage);

        // when
        Page<AdminDto.LoginHistoryResponse> result = adminService.getLoginHistory(pageable);

        // then
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("createAuthGrade: useYn이 null이면 기본값 Y로 저장한다")
    void createAuthGrade_useYnNull_기본값Y저장() {
        AdminDto.AuthGradeRequest req = new AdminDto.AuthGradeRequest("ITPNEW", "신규", null, null);
        given(authRepository.existsById("ITPNEW")).willReturn(false);

        adminService.createAuthGrade(req);

        org.mockito.ArgumentCaptor<CauthI> captor = org.mockito.ArgumentCaptor.forClass(CauthI.class);
        verify(authRepository).save(captor.capture());
        assertThat(captor.getValue().getUseYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("createRole: useYn이 null이면 기본값 Y로 저장한다")
    void createRole_useYnNull_기본값Y저장() {
        AdminDto.RoleRequest req = new AdminDto.RoleRequest("ITPAD001", "10001", null);
        given(roleRepository.existsById(new CroleIId("ITPAD001", "10001"))).willReturn(false);

        adminService.createRole(req);

        org.mockito.ArgumentCaptor<CroleI> captor = org.mockito.ArgumentCaptor.forClass(CroleI.class);
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
        AdminDto.UserRequest req = new AdminDto.UserRequest(
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
        AdminDto.UserRequest req = new AdminDto.UserRequest(
                "10001", "홍길동", null, null, null, null, null, null, " ");
        given(userRepository.findByEno("10001")).willReturn(Optional.of(user));

        adminService.updateUser("10001", req);

        verify(passwordEncoder, times(0)).encode(" ");
    }

    @Test
    @DisplayName("getTokens: 긴 SHA-256 조회값은 마스킹하고 짧은 값은 그대로 반환한다")
    void getTokens_토큰마스킹반환() {
        Crtokm longToken = Crtokm.builder()
                .eno("10001")
                .ecyRnwPubTokCone("1234567890123456789012345")
                .endDtm(java.time.LocalDateTime.now().plusDays(1))
                .build();
        Crtokm shortToken = Crtokm.builder()
                .eno("10002")
                .ecyRnwPubTokCone("short")
                .endDtm(java.time.LocalDateTime.now().plusDays(1))
                .build();
        given(refreshTokenRepository.findAll()).willReturn(List.of(longToken, shortToken));
        given(userRepository.findByEno(any())).willReturn(Optional.empty());

        List<AdminDto.TokenResponse> result = adminService.getTokens();

        assertThat(result).extracting(value -> value.tokMasked())
                .containsExactly("12345678901234567890...", "short");
    }

    @Test
    @DisplayName("getFiles: 삭제되지 않은 파일만 사용자명과 함께 반환한다")
    void getFiles_삭제되지않은파일만반환() {
        Cfilem active = Cfilem.builder()
                .flMpnId("FL_00000001")
                .flNm("문서.pdf")
                .flTpCone("첨부파일")
                .pkColNm("문서")
                .fstEnrUsid("10001")
                .delYn("N")
                .build();
        Cfilem deleted = Cfilem.builder()
                .flMpnId("FL_00000002")
                .flNm("삭제.pdf")
                .delYn("Y")
                .build();
        given(fileRepository.findAll()).willReturn(List.of(active, deleted));
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
                .willReturn(Collections.singletonList(
                        LabeledCountRow.fromRow(new Object[]{"2026-05-09", 3L})));

        List<AdminDto.LoginStatResponse> result = adminService.getLoginStats();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 5, 9));
        assertThat(result.get(0).count()).isEqualTo(3L);
    }
}
