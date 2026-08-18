package com.kdb.it.common.approval.mail;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재요청 메일 페이로드를 독립 트랜잭션에서 만드는 제공자.
 *
 * <p>{@code ApplicationService.submit}/{@code approve}는 결재요청 알림을 발행하기 전에 메일 본문을 렌더링합니다. 렌더링에 필요한
 * 신청자명·부서명 조회(DB 접근)가 실패하면, 그 예외를 호출부에서 잡아도 영속성 제공자가 이미 원 트랜잭션을 rollback-only로 표시해 신청서 등록·결재 자체가
 * {@code UnexpectedRollbackException}으로 실패할 수 있습니다. 그래서 이 조회와 렌더링을 {@code REQUIRES_NEW}로 분리된 별도의 물리
 * 트랜잭션에서 수행하고, 그 트랜잭션 안에서 예외를 직접 잡아 호출자의 트랜잭션에는 어떤 실패도 전파하지 않습니다.
 *
 * <p>{@code REQUIRES_NEW}는 Spring AOP 프록시를 거쳐야만 적용되므로, {@code ApplicationService}의 private 메서드가 아니라
 * 별도 빈으로 둡니다(자기 자신 호출 시 프록시 우회 문제 회피).
 */
@Component
@RequiredArgsConstructor
public class ApprovalMailPayloadProvider {

    private static final Logger log = LoggerFactory.getLogger(ApprovalMailPayloadProvider.class);

    /** 사용자(TPRMPP_CUSERI) 리포지토리: 신청자명 조회용 */
    private final UserRepository userRepository;

    /** 조직코드→조직명 해석기: 메일 개요의 작성부서명 표시용 */
    private final OrgNameResolver orgNameResolver;

    /** 결재요청 메일 본문 렌더러 */
    private final ApprovalMailRenderer approvalMailRenderer;

    /** 프론트 기준 URL: 메일의 신청서 상세 링크 조립용 */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * 결재요청 메일 페이로드를 만듭니다.
     *
     * <p>독립 트랜잭션({@code REQUIRES_NEW}, 읽기전용)에서 신청자명·부서명을 조회하고 렌더링합니다. 조회·렌더링 중 발생한 어떤 {@link
     * RuntimeException}도 호출자에게 전파하지 않고 {@code null}을 반환합니다. {@code null}을 반환하면 발송 계층이 기존 기본 본문으로
     * 폴백합니다.
     *
     * @param capplm 신청서 마스터
     * @return 메일 페이로드 JSON. 실패 시 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String render(Capplm capplm) {
        try {
            String requesterName =
                    userRepository
                            .findNameViewByEno(safeText(capplm.getDcdReqUsid()))
                            .map(user -> user.getUsrNm())
                            .orElse(null);
            String deptName = orgNameResolver.resolveName(capplm.getDcdReqBbrC());
            return approvalMailRenderer.renderPayloadJson(
                    ApprovalMailContextFactory.create(
                            capplm, requesterName, deptName, frontendUrl));
        } catch (RuntimeException e) {
            log.warn(
                    "결재요청 메일 페이로드 생성 실패 — 기본 본문으로 발송합니다: apfMngNo={}, 사유={}",
                    capplm.getApfMngNo(),
                    e.toString());
            return null;
        }
    }

    private static String safeText(String s) {
        return s == null ? "" : s;
    }
}
