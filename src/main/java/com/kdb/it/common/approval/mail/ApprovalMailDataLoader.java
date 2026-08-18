package com.kdb.it.common.approval.mail;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결재요청 메일에 필요한 신청자명·부서명을 독립 트랜잭션에서 조회하는 로더.
 *
 * <p>{@code REQUIRES_NEW}는 Spring AOP 프록시를 거쳐야만 적용되므로 별도 빈으로 둔다. 이 클래스는 예외를 잡지 않는다 — 조회가 실패하면 예외가
 * 그대로 호출자({@link ApprovalMailPayloadProvider})로 전파된다. 실패를 삼켜 {@code null}로 접는 책임은 이 트랜잭션 경계 바깥에 있는
 * {@code ApprovalMailPayloadProvider#render}가 진다 — 이 메서드 안에서 예외를 잡으면 Hibernate가 이미 rollback-only로
 * 표시한 트랜잭션을 프록시가 커밋 시점에 다시 검사해 {@code UnexpectedRollbackException}으로 터뜨리므로, 잡아도 실패를 가리지 못한다.
 */
@Component
@RequiredArgsConstructor
public class ApprovalMailDataLoader {

    /** 사용자(TPRMPP_CUSERI) 리포지토리: 신청자명 조회용 */
    private final UserRepository userRepository;

    /** 조직코드→조직명 해석기: 메일 개요의 작성부서명 표시용 */
    private final OrgNameResolver orgNameResolver;

    /**
     * 신청자명·작성부서명을 조회합니다.
     *
     * <p>독립 트랜잭션({@code REQUIRES_NEW}, 읽기전용)에서 실행합니다. 조회 중 발생한 {@link RuntimeException}은 잡지 않고 그대로
     * 호출자에게 전파합니다.
     *
     * @param capplm 신청서 마스터
     * @return 신청자명·작성부서명. 각 값은 매칭되는 데이터가 없으면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ApprovalMailParties loadParties(Capplm capplm) {
        String requesterName =
                userRepository
                        .findNameViewByEno(safeText(capplm.getDcdReqUsid()))
                        .map(user -> user.getUsrNm())
                        .orElse(null);
        String deptName = orgNameResolver.resolveName(capplm.getDcdReqBbrC());
        return new ApprovalMailParties(requesterName, deptName);
    }

    private static String safeText(String s) {
        return s == null ? "" : s;
    }
}
