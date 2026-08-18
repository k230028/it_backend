package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ApprovalMailDataLoader 단위 테스트.
 *
 * <p>{@code REQUIRES_NEW} 트랜잭션 경계 자체는 Spring 컨테이너 없이 검증할 수 없으므로, 여기서는 {@link
 * ApprovalMailDataLoader#loadParties}가 조회 실패를 스스로 삼키지 않고 그대로 전파한다는 계약만 검증한다. 이 전파가 바로 {@link
 * ApprovalMailPayloadProvider}의 트랜잭션 경계 밖 catch가 실제로 예외를 붙잡을 수 있는 전제다 — 로더가 여기서 삼켜버리면 그 catch는 아무
 * 것도 잡을 게 없다.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalMailDataLoaderTest {

    private record NameView(String eno, String usrNm) implements UserRepository.UserNameView {
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
    }

    @Mock private UserRepository userRepository;
    @Mock private OrgNameResolver orgNameResolver;

    private ApprovalMailDataLoader loader;

    private Capplm capplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqUsid()).willReturn("10001");
        given(capplm.getDcdReqBbrC()).willReturn("BBR001");
        return capplm;
    }

    private void setUp() {
        loader = new ApprovalMailDataLoader(userRepository, orgNameResolver);
    }

    @Test
    @DisplayName("정상 조회면 신청자명·부서명을 담아 반환한다")
    void loadParties_정상조회_이름과부서명반환() {
        setUp();
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "홍길동")));
        given(orgNameResolver.resolveName("BBR001")).willReturn("IT기획부");

        ApprovalMailParties result = loader.loadParties(capplm());

        assertThat(result.requesterName()).isEqualTo("홍길동");
        assertThat(result.deptName()).isEqualTo("IT기획부");
    }

    @Test
    @DisplayName("사용자 조회에서 예외가 나면 삼키지 않고 그대로 전파한다")
    void loadParties_사용자조회예외_예외전파() {
        setUp();
        // 예외가 사용자 조회 초입에서 발생해 이후 필드는 쓰이지 않으므로, 불필요한 스터빙 경고를 피하기 위해
        // 이 케이스에 필요한 필드만 최소로 스텁한다.
        Capplm capplm = mock(Capplm.class);
        given(capplm.getDcdReqUsid()).willReturn("10001");
        given(userRepository.findNameViewByEno("10001"))
                .willThrow(new RuntimeException("DB 조회 실패(테스트)"));

        assertThatThrownBy(() -> loader.loadParties(capplm))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 조회 실패(테스트)");
    }
}
