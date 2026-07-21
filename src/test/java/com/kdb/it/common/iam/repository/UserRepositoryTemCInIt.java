package com.kdb.it.common.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.support.AbstractOracleRepositoryTest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * UserRepository.findByTemCInAndDelYn 활성 사용자 배치 조회 통합 테스트 (BE-10)
 *
 * <p>실 로컬 Oracle(ITPOWN)에 @DataJpaTest로 연결하며, 픽스처는 트랜잭션 롤백으로
 * 정리된다. 존재하지 않는 팀코드 대역(T999x)을 사용해 실 데이터와 격리한다.</p>
 */
@DisplayName("UserRepository.findByTemCInAndDelYn 활성 사용자 배치 조회 (BE-10)")
class UserRepositoryTemCInIt extends AbstractOracleRepositoryTest {

    private static final String TEAM_A = "T9991";
    private static final String TEAM_B = "T9992";
    private static final String TEAM_EMPTY = "T9993";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager em;

    /**
     * 테스트 픽스처 사용자 생성.
     *
     * <p>{@code @DataJpaTest} 슬라이스에는 SecurityContext가 없어 감사컬럼을 직접 세팅한다.
     * (실행 시 다른 NOT NULL 컬럼으로 ORA-01400이 발생하면 해당 필드에 픽스처 값을 보강한다.)</p>
     */
    private CuserI insertUser(String eno, String temC, String ptCNm) {
        CuserI user = CuserI.builder()
                .eno(eno)
                .usrNm("테스트" + eno)
                .temC(temC)
                .ptCNm(ptCNm)
                .delYn("N")
                .fstEnrUsid("FIXTURE")
                .fstEnrDtm(LocalDateTime.now())
                .lstChgUsid("FIXTURE")
                .lstChgDtm(LocalDateTime.now())
                .build();
        return em.persist(user);
    }

    @Test
    @DisplayName("여러 팀코드의 사용자를 1회 조회로 모두 반환한다")
    void findByTemCIn_returnsUsersOfAllTeams() {
        insertUser("TENO9001", TEAM_A, "팀장");
        insertUser("TENO9002", TEAM_A, "과장");
        insertUser("TENO9003", TEAM_B, "차장");
        CuserI deleted = insertUser("TENO9004", TEAM_A, "팀장");
        deleted.delete();
        em.flush();
        em.clear();

        List<CuserI> result = userRepository.findByTemCInAndDelYn(
                List.of(TEAM_A, TEAM_B, TEAM_EMPTY), "N");

        assertThat(result).extracting(u -> u.getEno())
                .containsExactlyInAnyOrder("TENO9001", "TENO9002", "TENO9003");
        assertThat(result).allSatisfy(u -> assertThat(u.getTemC()).isIn(TEAM_A, TEAM_B));
    }

    @Test
    @DisplayName("해당 팀 사용자가 없으면 빈 목록을 반환한다")
    void findByTemCIn_noUsers_returnsEmpty() {
        assertThat(userRepository.findByTemCInAndDelYn(List.of(TEAM_EMPTY), "N")).isEmpty();
    }
}
