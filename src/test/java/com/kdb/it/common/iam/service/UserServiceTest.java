package com.kdb.it.common.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.kdb.it.common.iam.dto.UserDto;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;

/**
 * UserService 단위 테스트
 *
 * <p>
 * 사용자 조회 서비스의 3개 메서드(부점별 목록, 사번별 상세, 이름 검색)를 검증합니다.
 * CuserI 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    /**
     * 테스트용 CuserI Mock 생성 — 목록 조회에 필요한 필드만 스텁
     */
    private CuserI mockUserEntity(String eno, String bbrC, String usrNm) {
        CuserI user = mock(CuserI.class);
        given(user.getEno()).willReturn(eno);
        given(user.getBbrC()).willReturn(bbrC);
        given(user.getBbrNm()).willReturn("IT본부");
        given(user.getTemC()).willReturn("18001");
        given(user.getTemNm()).willReturn("IT기획팀");
        given(user.getUsrNm()).willReturn(usrNm);
        given(user.getPtCNm()).willReturn("과장");
        return user;
    }

    // ───────────────────────────────────────────────────────
    // getUsersByOrganization
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUsersByOrganization: 부점코드로 사용자 목록을 DTO로 변환하여 반환한다")
    void getUsersByOrganization_부점코드전달_DTO목록반환() {
        // given
        String orgCode = "001";
        CuserI user1 = mockUserEntity("E10001", orgCode, "홍길동");
        CuserI user2 = mockUserEntity("E10002", orgCode, "김철수");
        given(userRepository.findByBbrC(orgCode)).willReturn(List.of(user1, user2));

        // when
        List<UserDto.ListResponse> result = userService.getUsersByOrganization(orgCode);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEno()).isEqualTo("E10001");
        assertThat(result.get(0).getUsrNm()).isEqualTo("홍길동");
        assertThat(result.get(1).getEno()).isEqualTo("E10002");
        verify(userRepository).findByBbrC(orgCode);
    }

    @Test
    @DisplayName("getUsersByOrganization: 해당 부점에 사용자가 없으면 빈 목록을 반환한다")
    void getUsersByOrganization_사용자없음_빈목록반환() {
        // given
        given(userRepository.findByBbrC("999")).willReturn(List.of());

        // when
        List<UserDto.ListResponse> result = userService.getUsersByOrganization("999");

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
        CuserI user = mock(CuserI.class);
        given(user.getEno()).willReturn(eno);
        given(user.getBbrC()).willReturn("001");
        given(user.getBbrNm()).willReturn("IT본부");
        given(user.getTemC()).willReturn("18001");
        given(user.getTemNm()).willReturn("IT기획팀");
        given(user.getUsrNm()).willReturn("홍길동");
        given(user.getPtCNm()).willReturn("과장");
        given(user.getEtrMilAddrNm()).willReturn("hong@bank.co.kr");
        given(user.getInleNo()).willReturn("1234");
        given(user.getCpnTpn()).willReturn("010-1234-5678");
        given(user.getDtsDtlCone()).willReturn("IT 기획 담당");
        given(user.getPrlmHrkOgzCCone()).willReturn("001");
        given(user.getPrlmHrkOgzCNm()).willReturn("경영지원본부");
        given(userRepository.findByEno(eno)).willReturn(Optional.of(user));

        // when
        UserDto.DetailResponse result = userService.getUser(eno);

        // then
        assertThat(result.getEno()).isEqualTo(eno);
        assertThat(result.getUsrNm()).isEqualTo("홍길동");
        assertThat(result.getInleNo()).isEqualTo("1234");
        assertThat(result.getBbrNm()).isEqualTo("IT본부");
        verify(userRepository).findByEno(eno);
    }

    @Test
    @DisplayName("getUser: 존재하지 않는 사번이면 IllegalArgumentException을 던진다")
    void getUser_존재하지않는사번_IllegalArgumentException발생() {
        // given
        String eno = "E99999";
        given(userRepository.findByEno(eno)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUser(eno))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("E99999");
    }

    // ───────────────────────────────────────────────────────
    // searchUsersByName
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("searchUsersByName: orgCode가 null이면 키워드 매칭 전체 결과를 반환한다")
    void searchUsersByName_orgCode없음_전체결과반환() {
        // given
        CuserI user1 = mockUserEntity("E10001", "001", "홍길동");
        CuserI user2 = mockUserEntity("E10002", "002", "홍철수");
        given(userRepository.searchByName("홍")).willReturn(List.of(user1, user2));

        // when
        List<UserDto.ListResponse> result = userService.searchUsersByName("홍", null);

        // then
        assertThat(result).hasSize(2);
        verify(userRepository).searchByName("홍");
    }

    @Test
    @DisplayName("searchUsersByName: orgCode가 지정되면 해당 부점 사용자만 반환한다")
    void searchUsersByName_orgCode지정_해당부점만반환() {
        // given
        CuserI user1 = mockUserEntity("E10001", "001", "홍길동");
        CuserI user2 = mockUserEntity("E10002", "002", "홍철수");
        given(userRepository.searchByName("홍")).willReturn(List.of(user1, user2));

        // when — 부점코드 "001"만 통과
        List<UserDto.ListResponse> result = userService.searchUsersByName("홍", "001");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEno()).isEqualTo("E10001");
    }

    @Test
    @DisplayName("searchUsersByName: orgCode가 공백이면 필터 없이 전체 결과를 반환한다")
    void searchUsersByName_orgCode공백_필터없이전체반환() {
        // given
        CuserI user1 = mockUserEntity("E10001", "001", "홍길동");
        CuserI user2 = mockUserEntity("E10002", "002", "홍철수");
        given(userRepository.searchByName("홍")).willReturn(List.of(user1, user2));

        // when — 공백 orgCode는 isBlank() 판정으로 필터 미적용
        List<UserDto.ListResponse> result = userService.searchUsersByName("홍", "   ");

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("searchUsersByName: 검색 결과가 없으면 빈 목록을 반환한다")
    void searchUsersByName_검색결과없음_빈목록반환() {
        // given
        given(userRepository.searchByName("없는이름")).willReturn(List.of());

        // when
        List<UserDto.ListResponse> result = userService.searchUsersByName("없는이름", null);

        // then
        assertThat(result).isEmpty();
    }
}
