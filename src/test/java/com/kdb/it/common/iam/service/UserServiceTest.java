package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * UserService 단위 테스트
 *
 * <p>사용자 조회 서비스의 3개 메서드(부점별 목록, 사번별 상세, 이름 검색)를 검증합니다. CuserI 엔티티는 protected 생성자를 우회하기 위해
 * Mockito.mock()으로 생성합니다. Oracle DB 없이 실행됩니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    @Captor private ArgumentCaptor<Integer> limitCaptor;

    /** 테스트용 CuserI Mock 생성 — 목록 조회에 필요한 필드만 스텁 */
    private UserDto.ListRow userRow(String eno, String bbrC, String usrNm) {
        return new UserDto.ListRow(eno, bbrC, "IT본부", "18001", "IT기획팀", usrNm, "과장");
    }

    private UserDto.DetailRow detailRow(String eno, String usrNm) {
        return new UserDto.DetailRow(
                eno,
                "001",
                "IT본부",
                "18001",
                "IT기획팀",
                usrNm,
                "과장",
                "hong@bank.co.kr",
                "1234",
                "02-787-1234",
                "010-1234-5678",
                "IT 기획 담당",
                "001",
                "경영지원본부");
    }

    // ───────────────────────────────────────────────────────
    // getUsersByOrganization
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUsersByOrganization: 부점코드로 사용자 목록을 DTO로 변환하여 반환한다")
    void getUsersByOrganization_부점코드전달_DTO목록반환() {
        // given
        String orgCode = "001";
        UserDto.ListRow user1 = userRow("E10001", orgCode, "홍길동");
        UserDto.ListRow user2 = userRow("E10002", orgCode, "김철수");
        given(userRepository.findListRowsByBbrC(orgCode, null)).willReturn(List.of(user1, user2));

        // when
        List<UserDto.ListResponse> result = userService.getUsersByOrganization(orgCode, null);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEno()).isEqualTo("E10001");
        assertThat(result.get(0).getUsrNm()).isEqualTo("홍길동");
        assertThat(result.get(1).getEno()).isEqualTo("E10002");
        verify(userRepository).findListRowsByBbrC(orgCode, null);
    }

    @Test
    @DisplayName("getUsersByOrganization: 해당 부점에 사용자가 없으면 빈 목록을 반환한다")
    void getUsersByOrganization_사용자없음_빈목록반환() {
        // given
        given(userRepository.findListRowsByBbrC("999", null)).willReturn(List.of());

        // when
        List<UserDto.ListResponse> result = userService.getUsersByOrganization("999", null);

        // then
        assertThat(result).isEmpty();
    }

    // ───────────────────────────────────────────────────────
    // getUser
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUser: 사번으로 사용자를 조회하여 상세 DTO를 반환한다")
    void getUser_존재하는사번_상세DTO반환() {
        // given
        String eno = "E12345";
        given(userRepository.findDetailRowByEno(eno))
                .willReturn(Optional.of(detailRow(eno, "홍길동")));
        given(userRepository.findActiveQualificationGradeNamesByEno(eno))
                .willReturn(List.of("시스템관리자", "정보보호관리자"));
        CustomUserDetails admin = mock(CustomUserDetails.class);
        given(admin.isAdmin()).willReturn(true);

        // when — 관리자 권한으로 조회
        UserDto.DetailResponse result = userService.getUser(eno, admin);

        // then
        assertThat(result.getEno()).isEqualTo(eno);
        assertThat(result.getUsrNm()).isEqualTo("홍길동");
        assertThat(result.getInleNo()).isEqualTo("1234");
        assertThat(result.getCpnTpn()).isEqualTo("02-787-1234");
        assertThat(result.getBbrNm()).isEqualTo("IT본부");
        assertThat(result.getDtsDtlCone()).isEqualTo("IT 기획 담당");
        assertThat(result.getQlfGrNms()).containsExactly("시스템관리자", "정보보호관리자");
        verify(userRepository).findDetailRowByEno(eno);
        verify(userRepository).findActiveQualificationGradeNamesByEno(eno);
    }

    @Test
    @DisplayName("getUser: 존재하지 않는 사번이면 IllegalArgumentException을 던진다")
    void getUser_존재하지않는사번_IllegalArgumentException발생() {
        // given
        String eno = "E99999";
        given(userRepository.findDetailRowByEno(eno)).willReturn(Optional.empty());
        CustomUserDetails admin = mock(CustomUserDetails.class);
        given(admin.isAdmin()).willReturn(true);

        // when & then — 권한 통과 후 미존재로 IllegalArgumentException
        assertThatThrownBy(() -> userService.getUser(eno, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E99999");
    }

    // ───────────────────────────────────────────────────────
    // getUser 권한 검증 — 인증된 사용자는 직원 정보 다이얼로그에서 상세 조회 가능
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUser: 본인 사번 상세 조회는 허용된다")
    void getUser_owner_allowed() {
        // given
        given(userRepository.findDetailRowByEno("E0001"))
                .willReturn(Optional.of(detailRow("E0001", "홍길동")));
        CustomUserDetails me = mock(CustomUserDetails.class);
        given(me.isAdmin()).willReturn(false);
        given(me.getEno()).willReturn("E0001");

        // when & then
        assertThatCode(() -> userService.getUser("E0001", me)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getUser: 관리자는 타인 상세 조회가 허용된다")
    void getUser_admin_allowed() {
        // given
        given(userRepository.findDetailRowByEno("E0002"))
                .willReturn(Optional.of(detailRow("E0002", "홍길동")));
        CustomUserDetails admin = mock(CustomUserDetails.class);
        given(admin.isAdmin()).willReturn(true);

        // when & then
        assertThat(userService.getUser("E0002", admin).getEno()).isEqualTo("E0002");
    }

    @Test
    @DisplayName("getUser: 일반 사용자는 다른 직원 상세 조회가 허용된다")
    void getUser_authenticatedUserCanReadOtherEmployee() {
        // given
        given(userRepository.findDetailRowByEno("E0002"))
                .willReturn(Optional.of(detailRow("E0002", "홍길동")));
        CustomUserDetails other = mock(CustomUserDetails.class);
        given(other.isAdmin()).willReturn(false);
        given(other.getEno()).willReturn("E9999");

        // when & then
        assertThat(userService.getUser("E0002", other).getEno()).isEqualTo("E0002");
        verify(userRepository).findDetailRowByEno("E0002");
    }

    // ───────────────────────────────────────────────────────
    // searchUsers (이름·팀명·사번 검색)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchUsers: orgCode가 null이면 키워드 매칭 전체 결과를 반환한다")
    void searchUsers_orgCode없음_전체결과반환() {
        // given
        UserDto.ListRow user1 = userRow("E10001", "001", "홍길동");
        UserDto.ListRow user2 = userRow("E10002", "002", "홍철수");
        given(userRepository.searchListRowsByKeyword(eq("홍길"), any(), anyInt()))
                .willReturn(List.of(user1, user2));

        // when
        List<UserDto.ListResponse> result = userService.searchUsers("홍길", null, null);

        // then
        assertThat(result).hasSize(2);
        verify(userRepository).searchListRowsByKeyword(eq("홍길"), any(), anyInt());
    }

    @Test
    @DisplayName("searchUsers: 검색어 앞뒤 공백을 제거하고 결과 상한을 함께 전달한다")
    void searchUsers_검색어정리_상한전달() {
        // given
        given(userRepository.searchListRowsByKeyword(anyString(), any(), anyInt()))
                .willReturn(List.of());

        // when — 팀명 검색어도 같은 경로로 전달된다
        userService.searchUsers("  IT기획팀  ", null, null);

        // then
        verify(userRepository).searchListRowsByKeyword(eq("IT기획팀"), any(), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isPositive();
    }

    @Test
    @DisplayName("searchUsers: 검색어가 2자 미만이면 예외를 던지고 조회하지 않는다")
    void searchUsers_검색어2자미만_예외() {
        assertThatThrownBy(() -> userService.searchUsers("홍", null, null))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("2자 이상");

        verify(userRepository, never()).searchListRowsByKeyword(anyString(), any(), anyInt());
    }

    @Test
    @DisplayName("searchUsers: orgCode가 지정되면 해당 부점 사용자만 반환한다")
    void searchUsers_orgCode지정_해당부점만반환() {
        // given
        UserDto.ListRow user1 = userRow("E10001", "001", "홍길동");
        UserDto.ListRow user2 = userRow("E10002", "002", "홍철수");
        given(userRepository.searchListRowsByKeyword(eq("홍길"), any(), anyInt()))
                .willReturn(List.of(user1, user2));

        // when — 부점코드 "001"만 통과
        List<UserDto.ListResponse> result = userService.searchUsers("홍길", "001", null);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEno()).isEqualTo("E10001");
    }

    @Test
    @DisplayName("searchUsers: orgCode가 공백이면 필터 없이 전체 결과를 반환한다")
    void searchUsers_orgCode공백_필터없이전체반환() {
        // given
        UserDto.ListRow user1 = userRow("E10001", "001", "홍길동");
        UserDto.ListRow user2 = userRow("E10002", "002", "홍철수");
        given(userRepository.searchListRowsByKeyword(eq("홍길"), any(), anyInt()))
                .willReturn(List.of(user1, user2));

        // when — 공백 orgCode는 isBlank() 판정으로 필터 미적용
        List<UserDto.ListResponse> result = userService.searchUsers("홍길", "   ", null);

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("searchUsers: 검색 결과가 없으면 빈 목록을 반환한다")
    void searchUsers_검색결과없음_빈목록반환() {
        // given
        given(userRepository.searchListRowsByKeyword(eq("없는이름"), any(), anyInt()))
                .willReturn(List.of());

        // when
        List<UserDto.ListResponse> result = userService.searchUsers("없는이름", null, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchUsers: 키워드와 부점코드가 모두 비어 있으면 조회 없이 빈 목록을 반환한다")
    void searchUsers_키워드부점모두없음_빈목록반환() {
        List<UserDto.ListResponse> result = userService.searchUsers(" ", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchUsers: 키워드가 없고 부점코드가 있으면 부점 목록을 반환한다")
    void searchUsers_키워드없고부점있음_부점목록반환() {
        UserDto.ListRow user = userRow("E10001", "001", "홍길동");
        given(userRepository.findListRowsByBbrC("001", null)).willReturn(List.of(user));

        List<UserDto.ListResponse> result = userService.searchUsers(null, "001", null);

        assertThat(result)
                .singleElement()
                .satisfies(item -> assertThat(item.getEno()).isEqualTo("E10001"));
        verify(userRepository).findListRowsByBbrC("001", null);
    }

    // ───────────────────────────────────────────────────────
    // enoPrefix (행번 접두사 필터)
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUsersByOrganization: 행번 접두사를 리포지토리에 그대로 전달한다")
    void getUsersByOrganization_행번접두사전달() {
        given(userRepository.findListRowsByBbrC("001", "K")).willReturn(List.of());

        userService.getUsersByOrganization("001", "K");

        verify(userRepository).findListRowsByBbrC("001", "K");
    }

    @Test
    @DisplayName("searchUsers: 행번 접두사를 리포지토리 검색에 그대로 전달한다")
    void searchUsers_행번접두사전달() {
        given(userRepository.searchListRowsByKeyword(eq("홍길"), eq("K"), anyInt()))
                .willReturn(List.of());

        userService.searchUsers("홍길", null, "K");

        verify(userRepository).searchListRowsByKeyword(eq("홍길"), eq("K"), anyInt());
    }

    @Test
    @DisplayName("searchUsers: 키워드가 없으면 행번 접두사를 부점 목록 조회로 넘긴다")
    void searchUsers_키워드없음_행번접두사부점조회전달() {
        given(userRepository.findListRowsByBbrC("001", "K")).willReturn(List.of());

        userService.searchUsers(null, "001", "K");

        verify(userRepository).findListRowsByBbrC("001", "K");
    }
}
