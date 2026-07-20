package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
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

import jakarta.persistence.EntityManager;

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

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private CommitteeService committeeService;

    @BeforeEach
    void injectEntityManager() {
        ReflectionTestUtils.setField(committeeService, "entityManager", entityManager);
    }

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

    private UserRepository.CommitteeUserRow mockCommitteeUser(String eno, String temC, String ptCNm) {
        UserRepository.CommitteeUserRow user = mock(UserRepository.CommitteeUserRow.class);
        given(user.getEno()).willReturn(eno);
        given(user.getTemC()).willReturn(temC);
        given(user.getPtCNm()).willReturn(ptCNm);
        given(user.getUsrNm()).willReturn("홍길동");
        given(user.getBbrNm()).willReturn("IT본부");
        return user;
    }

    /** findByTemCInAndDelYn 배치 스텁 — 요청된 팀코드에 속한 활성 사용자만 반환한다. */
    private void stubUsersByTeam(UserRepository.CommitteeUserRow... users) {
        given(userRepository.findCommitteeUserRowsByTemCInAndDelYn(anyCollection(), eq("N"))).willAnswer(invocation -> {
            Collection<String> temCs = invocation.getArgument(0);
            return Arrays.stream(users)
                    .filter(user -> temCs.contains(user.getTemC()))
                    .toList();
        });
    }

    private Bcmmtm mockMember(String eno, String vlrTc) {
        Bcmmtm member = mock(Bcmmtm.class);
        given(member.getEno()).willReturn(eno);
        given(member.getItPtlAsctMebTc()).willReturn(vlrTc);
        return member;
    }

    // ───────────────────────────────────────────────────────
    // getDefaultCommittee
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDefaultCommittee: INFO_SYS 타입이면 당연위원(MAND) 4명과 간사(SECR) 1명 후보를 반환한다")
    void getDefaultCommittee_INFO_SYS타입_당연위원후보반환() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("03");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        // mockUser 내부에도 given()이 있으므로 변수에 먼저 생성 후 willReturn에 전달
        UserRepository.CommitteeUserRow u12004 = mockCommitteeUser("E10001", "12004", "팀장");
        UserRepository.CommitteeUserRow u18010 = mockCommitteeUser("E10002", "18010", "팀장");
        UserRepository.CommitteeUserRow u18501 = mockCommitteeUser("E10003", "18501", "팀장");
        UserRepository.CommitteeUserRow u18301 = mockCommitteeUser("E10004", "18301", "팀장");
        UserRepository.CommitteeUserRow u18001 = mockCommitteeUser("E10005", "18001", "팀장");

        stubUsersByTeam(u12004, u18010, u18501, u18301, u18001);

        List<CouncilDto.CommitteeMemberResponse> result =
                committeeService.getDefaultCommittee(ASCT_ID);

        assertThat(result).hasSize(5);
        assertThat(result).filteredOn(r -> "01".equals(r.vlrTc())).hasSize(4);
        assertThat(result).filteredOn(r -> "03".equals(r.vlrTc())).hasSize(1);
    }

    @Test
    @DisplayName("getDefaultCommittee: 팀장이 없으면 사번이 가장 빠른 사용자를 후보로 선택한다")
    void getDefaultCommittee_팀장없음_사번오름차순대표선택() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("05");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        UserRepository.CommitteeUserRow laterEnoMember = mockCommitteeUser("E20002", "12004", "과장");
        UserRepository.CommitteeUserRow earlierEnoMember = mockCommitteeUser("E20001", "12004", "대리");
        stubUsersByTeam(laterEnoMember, earlierEnoMember);

        List<CouncilDto.CommitteeMemberResponse> result =
                committeeService.getDefaultCommittee(ASCT_ID);

        assertThat(result)
                .extracting(CouncilDto.CommitteeMemberResponse::eno)
                .containsExactly("E20001");
    }

    @Test
    @DisplayName("getDefaultCommittee: dbrTc='02'(정보기술부문계획)이면 IT기획팀장을 겸직('04') 단일 위원으로 병합한다")
    void getDefaultCommittee_정보기술부문계획_IT기획팀장겸직04() {
        Basctm council = mock(Basctm.class);
        given(council.getItPtlAsctDbrTc()).willReturn("02");
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        UserRepository.CommitteeUserRow u14011 = mockCommitteeUser("E30001", "14011", "팀장");  // 미래전략팀장 → 당연위원
        UserRepository.CommitteeUserRow u18001 = mockCommitteeUser("E30002", "18001", "팀장");  // IT기획팀장 → 당연위원 겸 간사

        stubUsersByTeam(u14011, u18001);

        List<CouncilDto.CommitteeMemberResponse> result =
                committeeService.getDefaultCommittee(ASCT_ID);

        // IT기획팀장은 당연위원+간사 두 목록에 있지만 겸직('04') 1명으로만 배정된다(중복 없음)
        assertThat(result).hasSize(2);
        assertThat(result)
                .filteredOn(r -> "E30001".equals(r.eno()))
                .extracting(CouncilDto.CommitteeMemberResponse::vlrTc)
                .containsExactly("01");
        assertThat(result)
                .filteredOn(r -> "E30002".equals(r.eno()))
                .extracting(CouncilDto.CommitteeMemberResponse::vlrTc)
                .containsExactly("04");
    }

    // ───────────────────────────────────────────────────────
    // getCommittee
    // ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getCommittee: 위원 목록을 유형(MAND/CALL/SECR)별로 분류하여 반환한다")
    void getCommittee_유형별위원분류반환() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm mand = mockMember("E10001", "01");
        Bcmmtm call = mockMember("E10002", "02");
        Bcmmtm secr = mockMember("E10003", "03");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(mand, call, secr));

        CuserI ue1 = mockUser("E10001", "18001", "팀장");
        CuserI ue2 = mockUser("E10002", "18010", "대리");
        CuserI ue3 = mockUser("E10003", "18301", "과장");
        given(userRepository.findByEnoIn(anyCollection())).willReturn(List.of(ue1, ue2, ue3));

        CouncilDto.CommitteeListResponse result = committeeService.getCommittee(ASCT_ID);

        assertThat(result.mandatory()).hasSize(1);
        assertThat(result.call()).hasSize(1);
        assertThat(result.secretary()).hasSize(1);
        assertThat(result.mandatory().get(0).eno()).isEqualTo("E10001");
    }

    @Test
    @DisplayName("getCommittee: '04'(당연위원 겸 간사) 위원은 당연위원 목록에 노출된다")
    void getCommittee_겸직04_당연위원목록노출() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);

        Bcmmtm dual = mockMember("E30002", "04");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N"))
                .willReturn(List.of(dual));

        CuserI u18001 = mockUser("E30002", "18001", "팀장");
        given(userRepository.findByEnoIn(anyCollection())).willReturn(List.of(u18001));

        CouncilDto.CommitteeListResponse result = committeeService.getCommittee(ASCT_ID);

        assertThat(result.mandatory()).hasSize(1);
        assertThat(result.mandatory().get(0).eno()).isEqualTo("E30002");
        assertThat(result.secretary()).isEmpty();
    }

    @Test
    @DisplayName("getCommittee: 사용자명은 findByEnoIn 1회 배치 — findByEno 미호출")
    void getCommittee_findByEnoIn_1회() {
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        Bcmmtm mand = mockMember("E10001", "01");
        Bcmmtm call = mockMember("E10002", "02");
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(mand, call));
        // mockUser 내부에도 given()이 있으므로 변수에 먼저 생성 후 willReturn에 전달
        CuserI ue1 = mockUser("E10001", "18001", "팀장");
        CuserI ue2 = mockUser("E10002", "18010", "대리");
        given(userRepository.findByEnoIn(anyCollection())).willReturn(List.of(ue1, ue2));

        committeeService.getCommittee(ASCT_ID);

        then(userRepository).should(times(1)).findByEnoIn(anyCollection());
        then(userRepository).should(never()).findByEno(anyString());
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
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of(existing));

        CouncilDto.CommitteeRequest request = new CouncilDto.CommitteeRequest(
                "03",
                List.of(new CouncilDto.CommitteeMemberRequest("E20001", "01")));

        committeeService.saveCommittee(ASCT_ID, request);

        verify(existing).delete();
        verify(entityManager).persist(any(Bcmmtm.class));
    }

    @Test
    @DisplayName("saveCommittee: 위원 저장은 더 이상 협의회 상태를 전이하지 않는다 (PRD_c_20260620 #2 — '개최준비 진행' 버튼이 명시적으로 수행)")
    void saveCommittee_상태전이없음() {
        // Arrange: 활성 협의회 존재, 기존 위원 없음
        Basctm council = mock(Basctm.class);
        given(councilService.findActiveCouncil(ASCT_ID)).willReturn(council);
        given(committeeRepository.findByItPtlAsctIdAndDelYn(ASCT_ID, "N")).willReturn(List.of());

        // Act
        committeeService.saveCommittee(ASCT_ID, new CouncilDto.CommitteeRequest("05", List.of()));

        // Assert: 04→05 전이는 saveCommittee의 부수효과로 더 이상 처리되지 않는다.
        verify(councilService, never()).changeStatus(anyString(), anyString());
    }
}
