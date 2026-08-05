package com.kdb.it.domain.budget.work.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.budget.work.dto.BudgetWorkDto;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** 분해된 예산작업 서비스의 트랜잭션 경계를 검증합니다. */
class BudgetServiceTransactionContractTest {

    @Test
    @DisplayName("조회 서비스는 클래스 수준 읽기 전용 트랜잭션을 선언한다")
    void 조회서비스_읽기전용트랜잭션() {
        assertReadOnly(BudgetSummaryService.class);
        assertReadOnly(BudgetProjectSummaryService.class);
    }

    @Test
    @DisplayName("편성 적용 서비스의 공개 변경 메서드는 쓰기 트랜잭션을 선언한다")
    void 편성적용서비스_쓰기트랜잭션() throws NoSuchMethodException {
        Method applyRates =
                BudgetRateApplicationService.class.getMethod(
                        "applyRates", BudgetWorkDto.ApplyRequest.class);
        Method applyItemRates =
                BudgetRateApplicationService.class.getMethod(
                        "applyItemRates", BudgetWorkDto.ItemApplyRequest.class);

        assertThat(applyRates.getAnnotation(Transactional.class)).isNotNull();
        assertThat(applyItemRates.getAnnotation(Transactional.class)).isNotNull();
    }

    private void assertReadOnly(Class<?> serviceType) {
        Transactional transactional = serviceType.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
