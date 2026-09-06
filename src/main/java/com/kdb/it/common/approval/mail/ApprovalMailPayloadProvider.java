package com.kdb.it.common.approval.mail;

import com.kdb.it.common.approval.entity.Capplm;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader;
import com.kdb.it.common.approval.itbudget.service.ItBudgetSnapshotReader.SnapshotCorruptionException;
import com.kdb.it.exception.DataCorruptionException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 결재요청 메일 페이로드를 만드는 제공자.
 *
 * <p>{@code ApplicationService.submit}/{@code approve}는 결재요청 알림을 발행하기 전에 메일 본문을 렌더링합니다. 렌더링에 필요한
 * 신청자명·부서명 조회(DB 접근)는 {@link ApprovalMailDataLoader}가 {@code REQUIRES_NEW}로 분리된 별도의 물리 트랜잭션에서
 * 수행합니다. 이 클래스 자신은 {@code @Transactional}이 아닙니다 — 트랜잭션 경계 밖에서 로더를 호출하고 그 결과(정상 반환 또는 예외)를 받으므로, 로더
 * 호출에서 발생한 {@link RuntimeException}을 여기서 잡아도 이미 rollback-only로 표시된 트랜잭션이 없어 {@code
 * UnexpectedRollbackException}으로 되돌아올 여지가 없습니다. 이 경계 밖 catch가 호출자의 트랜잭션에 실패를 전파하지 않는 유일한 방어선입니다.
 */
@Component
@RequiredArgsConstructor
public class ApprovalMailPayloadProvider {

    private static final Logger log = LoggerFactory.getLogger(ApprovalMailPayloadProvider.class);

    /** 신청자명·부서명을 독립 트랜잭션에서 조회하는 로더 */
    private final ApprovalMailDataLoader approvalMailDataLoader;

    /** 결재요청 메일 본문 렌더러 */
    private final ApprovalMailRenderer approvalMailRenderer;

    private final ItBudgetSnapshotReader snapshotReader;
    private final MeterRegistry meterRegistry;

    /** 프론트 기준 URL: 메일의 신청서 상세 링크 조립용 */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * 결재요청 메일 페이로드를 만듭니다.
     *
     * <p>{@link ApprovalMailDataLoader#loadParties}로 신청자명·부서명을 조회한 뒤 렌더링합니다. 조회·렌더링 중 발생한 어떤 {@link
     * RuntimeException}도 호출자에게 전파하지 않고 {@code null}을 반환합니다. {@code null}을 반환하면 발송 계층이 기존 기본 본문으로
     * 폴백합니다. 저장 스냅샷 손상은 업무 요약만 제외하고 신청서 개요·결재 링크가 있는 안내 메일을 유지합니다.
     *
     * @param capplm 신청서 마스터
     * @return 메일 페이로드 JSON. 실패 시 null
     */
    public String render(Capplm capplm) {
        try {
            ApprovalMailParties parties = approvalMailDataLoader.loadParties(capplm);
            return approvalMailRenderer.renderPayloadJson(
                    ApprovalMailContextFactory.create(
                            capplm, parties.requesterName(), parties.deptName(), frontendUrl),
                    summary(capplm));
        } catch (RuntimeException e) {
            log.warn("결재요청 메일 페이로드 생성 실패 — 기본 본문으로 발송합니다: apfMngNo={}", capplm.getApfMngNo());
            return null;
        }
    }

    private ApprovalMailSnapshot summary(Capplm capplm) {
        String raw = capplm.getDcdReqInf();
        if (raw == null || raw.isBlank()) return ApprovalMailSnapshot.empty();
        try {
            return ApprovalMailSnapshot.from(snapshotReader.read(raw));
        } catch (DataCorruptionException exception) {
            String version =
                    exception instanceof SnapshotCorruptionException corruption
                            ? corruption.versionTag()
                            : "unknown";
            meterRegistry
                    .counter("approval.snapshot.mail.degraded", "version", version)
                    .increment();
            // 파서 예외·원문·PII를 로그에 전달하지 않는다. 메일 개요와 결재 링크는 계속 발송한다.
            log.warn("신청서 스냅샷 요약을 생략합니다. apfMngNo={}", capplm.getApfMngNo());
            return ApprovalMailSnapshot.empty();
        }
    }
}
