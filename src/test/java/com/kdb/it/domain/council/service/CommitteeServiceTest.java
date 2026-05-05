package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Basctm;
import com.kdb.it.domain.council.entity.Bcmmtm;
import com.kdb.it.domain.council.repository.CommitteeRepository;

/**
 * CommitteeService 단위 테스트
 *
 * <p>
 * 평가위원 서비스의 후보 조회·목록 조회·위원 선정(전체 교체) 메서드를 검증합니다.
 * Basctm·Bcmmtm·CuserI 엔티티는 protected 생성자를 우회하기 위해 Mockito.mock()으로 생성합니다.
 * CouncilService·CommitteeRepository·UserRepository는 @Mock으로 교체합니다.
 * Oracle DB 없이 실행됩니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommitteeServiceTest {

    @Mock
    private CommitteeRepository committeeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CouncilService councilService;

    @InjectMocks
    private CommitteeService committeeService;

    private static final String ASCT_ID = "ASCT-2026-0001";

    private CuserI mockUser(String eno, String temC, String ptCNm) {
        CuserI user = mock(CuserI.class);
        given(user.getEno()).willReturn(eno);
        given(user.getTemC()).willReturn(temC);
        given(user.getPtCNm()).willReturn(ptCNm);
        given(user.getUsrNm()).willReturn("홍길동");
        given(user.getBbrNm()).willReturn("IT본부");
        return user;
    }

    private Bcmmtm mockMember(String eno, String vlrTp) {
        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(eno);
        given(member.getVlrTp()).willReturn(vlrTp);
        return member;
    }

    // ───────────────────────────────────────────────────────
    // getDefaultCommittee
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDefaultCommittee: INFO_SYS 타입이면 당연위원(MAND) 4명과 간사(SECR) 1명 후보를 반환한다")
    void getDefaultCommittee_INFO_SYS타입_당연위원후보반환() {
        Basctm council = mock(Basctm.class);
        given(council.getDbrTp()).willReturn("INFO_SYS");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // mockUser 내부에도 given()이 있으므로 변수에 먼저 생성 후 willReturn에 전달
        CuserI u12004 = mockUser("E10001", "12004", "팀장");
        CuserI u18010 = mockUser("E10002", "18010", "팀장");
        CuserI u18501 = mockUser("E10003", "18501", "팀장");
        CuserI u18301 = mockUser("E10004", "18301", "팀장");
        CuserI u18001 = mockUser("E10005", "18001", "팀장");

        given(userRepository.findByTemC("12004")).willReturn(List.of(u12004));
        given(userRepository.findByTemC("18010")).willReturn(List.of(u18010));
        given(userRepository.findByTemC("18501")).willReturn(List.of(u18501));
        given(userRepository.findByTemC("18301")).willReturn(List.of(u18301));
        given(userRepository.findByTemC("18001")).willReturn(List.of(u18001));

        List<CouncilDto.CommitteeMemberResponse> result =
                committeeService.getDefaultCommittee(ASCT_ID);

        assertThat(result).hasSize(5);
        assertThat(result).filteredOn(r -> "MAND".equals(r.vlrTp())).hasSize(4);
        assertThat(result).filteredOn(r -> "SECR".equals(r.vlrTp())).hasSize(1);
    }

    @Test
    @DisplayName("getDefaultCommittee: 팀장이 없으면 첫 번째 사용자를 후보로 선택한다")
    void getDefaultCommittee_팀장없음_첫번째사용자선택() {
        Basctm council = mock(Basctm.class);
        given(council.getDbrTp()).willReturn("ETC");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        CuserI member = mockUser("E20001", "12004", "과장");
        given(userRepository.findByTemC("12004")).willReturn(List.of(member));
        given(userRepository.findByTemC("18010")).willReturn(List.of());
        given(userRepository.findByTemC("18501")).willReturn(List.of());
        given(userRepository.findByTemC("18001")).willReturn(List.of());

        List<CouncilDto.CommitteeMemberResponse> result =
                committeeService.getDefaultCommittee(ASCT_ID);

        assertThat(result).filteredOn(r -> "E20001".equals(r.eno())).hasSize(1);
    }

    // ───────────────────────────────────────────────────────
    // getCommittee
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCommittee: 위원 목록을 유형(MAND/CALL/SECR)별로 분류하여 반환한다")
    void getCommittee_유형별위원분류반환() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm mand = mockMember("E10001", "MAND");
        Bcmmtm call = mockMember("E10002", "CALL");
        Bcmmtm secr = mockMember("E10003", "SECR");
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(mand, call, secr));

        CuserI ue1 = mockUser("E10001", "18001", "팀장");
        CuserI ue2 = mockUser("E10002", "18010", "대리");
        CuserI ue3 = mockUser("E10003", "18301", "과장");
        given(userRepository.findByEno("E10001")).willReturn(Optional.of(ue1));
        given(userRepository.findByEno("E10002")).willReturn(Optional.of(ue2));
        given(userRepository.findByEno("E10003")).willReturn(Optional.of(ue3));

        CouncilDto.CommitteeListResponse result = committeeService.getCommittee(ASCT_ID);

        assertThat(result.mandatory()).hasSize(1);
        assertThat(result.call()).hasSize(1);
        assertThat(result.secretary()).hasSize(1);
        assertThat(result.mandatory().get(0).eno()).isEqualTo("E10001");
    }

    // ───────────────────────────────────────────────────────
    // saveCommittee
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("saveCommittee: 기존 위원을 Soft Delete하고 신규 위원을 저장한다")
    void saveCommittee_기존위원삭제후신규저장() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm existing = mock(Bcmmtm.class);
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(existing));

        CouncilDto.CommitteeRequest request = new CouncilDto.CommitteeRequest(
                "INFO_SYS",
                List.of(new CouncilDto.CommitteeMemberRequest("E20001", "MAND")));

        committeeService.saveCommittee(ASCT_ID, request);

        verify(existing).delete();
        verify(committeeRepository).save(any(Bcmmtm.class));
    }

    @Test
    @DisplayName("saveCommittee: 위원 선정 후 PREPARING으로 상태를 전이한다")
    void saveCommittee_위원선정후PREPARING전이() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        committeeService.saveCommittee(ASCT_ID, new CouncilDto.CommitteeRequest("ETC", List.of()));

        verify(councilService).changeStatus(ASCT_ID, "PREPARING");
    }
}
