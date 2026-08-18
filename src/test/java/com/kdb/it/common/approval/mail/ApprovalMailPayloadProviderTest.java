package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * ApprovalMailPayloadProvider 단위 테스트.
 *
 * <p>{@code REQUIRES_NEW} 트랜잭션 경계 자체는 Spring 컨테이너 없이는 검증할 수 없으므로, 여기서는 {@link
 * ApprovalMailPayloadProvider#render}가 스스로의 실패를 삼켜 항상 정상 반환(null 또는 JSON)한다는 계약만 검증한다. 트랜잭션 전파 검증은
 * {@code ApplicationServiceTest}의 submit 시나리오(Finding 1 회귀 테스트)가 대신한다.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalMailPayloadProviderTest {

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
    @Mock private ApprovalMailRenderer approvalMailRenderer;

    private ApprovalMailPayloadProvider provider;

    private Capplm capplm() {
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn("APF-2026-00000001");
        given(capplm.getDcdReqTtl()).willReturn("테스트 신청서");
        given(capplm.getDcdReqDtm()).willReturn(LocalDate.of(2026, 8, 18));
        given(capplm.getDcdReqUsid()).willReturn("10001");
        given(capplm.getDcdReqBbrC()).willReturn("BBR001");
        given(capplm.getDcdReqInf()).willReturn(null);
        return capplm;
    }

    @BeforeEach
    void setUp() {
        provider =
                new ApprovalMailPayloadProvider(
                        userRepository, orgNameResolver, approvalMailRenderer);
        ReflectionTestUtils.setField(provider, "frontendUrl", "https://it.kdb.co.kr");
    }

    @Test
    @DisplayName("정상 조회·렌더링이면 렌더러가 만든 JSON을 그대로 반환한다")
    void render_정상흐름_렌더러결과반환() {
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "홍길동")));
        given(orgNameResolver.resolveName("BBR001")).willReturn("IT기획부");
        given(approvalMailRenderer.renderPayloadJson(any()))
                .willReturn("{\"subject\":\"제목\",\"html\":\"본문\"}");

        String result = provider.render(capplm());

        assertThat(result).isEqualTo("{\"subject\":\"제목\",\"html\":\"본문\"}");
    }

    @Test
    @DisplayName("렌더러가 null을 반환하면(예산 초과 등) 그대로 null을 돌려준다")
    void render_렌더러가null반환_null그대로전달() {
        given(userRepository.findNameViewByEno("10001"))
                .willReturn(Optional.of(new NameView("10001", "홍길동")));
        given(orgNameResolver.resolveName("BBR001")).willReturn("IT기획부");
        given(approvalMailRenderer.renderPayloadJson(any())).willReturn(null);

        String result = provider.render(capplm());

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("신청자명 조회에서 예외가 나도 삼키고 null을 반환한다 — 호출자에 전파하지 않는다")
    void render_사용자조회예외_예외전파없이null반환() {
        // 예외가 조회 초입에서 발생해 이후 필드는 쓰이지 않으므로, 불필요한 스터빙 경고를 피하기 위해
        // 이 케이스에 필요한 필드만 최소로 스텁한다.
        Capplm capplm = mock(Capplm.class);
        given(capplm.getApfMngNo()).willReturn("APF-2026-00000001");
        given(capplm.getDcdReqUsid()).willReturn("10001");
        given(userRepository.findNameViewByEno("10001"))
                .willThrow(new RuntimeException("DB 조회 실패(테스트)"));

        String result = provider.render(capplm);

        assertThat(result).isNull();
        verify(approvalMailRenderer, never()).renderPayloadJson(any());
    }
}
