package com.kdb.it.common.approval.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결재요청 메일 페이로드 생성이 상신 트랜잭션을 오염시키지 않는지 <b>실제 트랜잭션 매니저</b>로 검증하는 통합 테스트 (BE-48).
 *
 * <p>{@link ApprovalMailPayloadProvider}(비트랜잭션, catch 보유) → {@link ApprovalMailDataLoader}({@code
 * REQUIRES_NEW}, readOnly) 구조는 메일용 조회 실패가 상신 트랜잭션을 rollback-only로 오염시키지 않게 하려는 것이다. 기존 검증({@code
 * ApprovalMailPayloadProviderTest})은 목 기반이라 트랜잭션 매니저가 아예 없고, 따라서 다음 회귀를 <b>초록으로 통과시킨다</b>.
 *
 * <ul>
 *   <li>로더에서 {@code REQUIRES_NEW}를 떼어 상신 트랜잭션에 참여시키는 변경 — 조회 예외가 바깥 트랜잭션을 rollback-only로 표시하는데
 *       프로바이더의 catch가 그 사실을 가려, 상신은 커밋 시점에 {@code UnexpectedRollbackException}으로 뒤늦게 터진다.
 *   <li>프로바이더에 {@code @Transactional}을 다시 붙이는 변경 — 실제로 2026-08-18 구현 중 catch가 경계 안에 있어 한 번 새어나간 전례가
 *       있다.
 * </ul>
 *
 * <p>여기서는 상신 트랜잭션을 {@link TransactionTemplate}(기본 {@code REQUIRED})로 재현하고, 그 안에서 {@code render()}를
 * 부른 뒤 <b>바깥 트랜잭션이 rollback-only로 표시되지 않았는지</b>와 <b>커밋이 실제로 성립하는지</b>를 본다. 신청서 마스터는 렌더링 입력으로만 쓰이므로
 * 영속화하지 않는다 — 이 테스트가 검증하는 것은 저장 내용이 아니라 트랜잭션 경계다.
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties의 ${JWT_SECRET} 플레이스홀더를 대체한다.
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok",
            "app.frontend-url=http://localhost:3000"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class ApprovalMailPayloadTransactionBoundaryIT {

    /** 메일용 조회의 첫 단계. 여기서 던지게 만들어 "조회 실패"를 재현한다. */
    @MockitoBean private UserRepository userRepository;

    @Autowired private ApprovalMailPayloadProvider provider;
    @Autowired private PlatformTransactionManager transactionManager;

    private static Capplm application() {
        return Capplm.builder()
                .apfMngNo("APF-2026-99999999")
                .dcdReqTtl("트랜잭션 경계 검증용 신청서")
                .dcdReqDtm(LocalDate.of(2026, 8, 22))
                .dcdReqUsid("ZZITMAILBND1")
                .dcdReqBbrC("0210")
                .build();
    }

    @Test
    @DisplayName("메일용 조회가 던져도 상신 트랜잭션은 rollback-only가 되지 않고 커밋된다")
    void mailLookupFailure_doesNotPoisonOuterTransaction() {
        given(userRepository.findNameViewByEno(anyString()))
                .willThrow(new QueryTimeoutException("메일용 조회 실패(주입)"));
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        // 상신 트랜잭션 안에서 페이로드를 만든다. 커밋까지 예외 없이 끝나야 한다 —
        // 로더가 REQUIRES_NEW를 잃으면 여기서 UnexpectedRollbackException이 난다.
        String[] payload = new String[1];
        boolean[] rollbackOnly = new boolean[1];
        assertThatCode(
                        () ->
                                outer.executeWithoutResult(
                                        status -> {
                                            payload[0] = provider.render(application());
                                            rollbackOnly[0] = status.isRollbackOnly();
                                        }))
                .doesNotThrowAnyException();

        // 실패를 삼켜 null로 접고(발송 계층이 기본 본문으로 폴백한다),
        assertThat(payload[0]).isNull();
        // 바깥 트랜잭션에는 그 실패가 남지 않는다.
        assertThat(rollbackOnly[0]).isFalse();
    }

    @Test
    @DisplayName("메일용 조회가 정상이면 상신 트랜잭션 안에서 페이로드를 만들어 돌려준다")
    void mailLookupSuccess_rendersPayloadInsideOuterTransaction() {
        // 이름을 찾지 못하는 정상 경로(Optional.empty)로도 페이로드는 만들어진다 —
        // 위 실패 케이스의 null이 "예외를 삼킨 결과"임을 이 대조가 보장한다.
        given(userRepository.findNameViewByEno(anyString())).willReturn(java.util.Optional.empty());
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        String payload = outer.execute(status -> provider.render(application()));

        assertThat(payload).isNotNull().contains("APF-2026-99999999");
    }
}
