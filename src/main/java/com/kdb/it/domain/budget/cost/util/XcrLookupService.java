package com.kdb.it.domain.budget.cost.util;

import com.kdb.it.common.code.CommonCodeGroups;
import com.kdb.it.common.code.entity.Ccodem;
import com.kdb.it.common.code.repository.CodeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외화 환율 표준 조회 서비스.
 *
 * <p>REQUIREMENTS.md R3.7 / CONTEXT.md 결정 E 에 따라, 외화 저장 시 클라이언트가 보낸 {@code xcr} 값을 신뢰하지 않고 서버가
 * {@code Ccodem}(공통코드마스터)을 단일 진실 원천으로 조회해 환율을 결정한다.
 *
 * <p>조회 규약 (Ccodem):
 *
 * <ul>
 *   <li>{@code C_ID = CommonCodeGroups.CURRENCY}
 *   <li>{@code CDVA = 통화코드} (예: USD, JPY)
 *   <li>{@code C_TP = "XCR"}
 *   <li>유효 기간 {@code STT_DT ~ END_DT} 내 행만 매칭 ({@link CodeRepository#findByCIdAndCdvaWithValidDate}
 *       가 필터 처리)
 *   <li>환율 수치는 {@code cdvaDtlC} 필드(CO_CDVA_NM) (예: "1400") — {@code new BigDecimal(cNm)} 로 파싱
 * </ul>
 *
 * <p>적용 지점은 {@code CostService} (Bcostm + Btermm), {@code ProjectService} (Bitemm), {@code
 * BudgetWorkService} (편성 재집계) 의 외화 처리 경로다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class XcrLookupService {

    /** 환율 코드 식별자 — Ccodem.C_ID 값 */
    private static final String CUR_C_ID = CommonCodeGroups.CURRENCY;

    /** 공통코드 리포지토리 — 유효 기간 필터링 단건 조회용 */
    private final CodeRepository codeRepository;

    /**
     * 통화코드 + 기준일로 환율을 조회한다.
     *
     * <p>동작:
     *
     * <ol>
     *   <li>{@code curC} 가 {@code null} 또는 {@code "KRW"} 이면 조회를 수행하지 않고 {@code null} 을 반환한다 (호출 측에서
     *       KRW 분기 판단에 사용).
     *   <li>{@code baseDate} 가 {@code null} 이면 {@link LocalDate#now()} 를 사용한다.
     *   <li>{@code Ccodem(C_ID=CommonCodeGroups.CURRENCY, CDVA=curC, 유효일=baseDate)} 단건 조회. 존재하지 않으면
     *       {@link IllegalStateException} 을 던져 트랜잭션을 롤백시킨다.
     *   <li>존재하면 {@code ccodem.getCdvaDtlC()} 을 {@link BigDecimal} 로 파싱해 반환한다.
     * </ol>
     *
     * @param curC 통화코드 (예: "USD", "JPY", "KRW", {@code null})
     * @param baseDate 환율 기준일 — {@code null} 이면 현재 일자
     * @return 외화면 Ccodem.CO_CDVA_NM 을 {@link BigDecimal} 로 파싱한 환율, KRW/null 이면 {@code null}
     * @throws IllegalStateException 외화 통화인데 유효한 Ccodem 행이 없을 때 (한글 메시지)
     * @throws NumberFormatException Ccodem.C_NM 이 숫자로 파싱 불가할 때 (운영 데이터 오류)
     */
    public BigDecimal resolveXcr(String curC, LocalDate baseDate) {
        // 1. 원화/미지정 → 조회 우회
        if (curC == null || "KRW".equals(curC)) {
            return null;
        }

        // 2. 기준일 보정
        LocalDate target = (baseDate != null) ? baseDate : LocalDate.now();

        // 3. 단일 진실 원천 조회 — 미등록 시 한글 메시지로 즉시 실패
        Ccodem ccodem =
                codeRepository
                        .findByCIdAndCdvaWithValidDate(CUR_C_ID, curC, target)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "환율 미등록: " + curC + " (기준일: " + target + ")"));

        // 4. 환율 수치 파싱 (Ccodem.java L57-59 규약: CO_CDVA_NM 에 수치 저장)
        return new BigDecimal(ccodem.getCdvaDtlC());
    }
}
