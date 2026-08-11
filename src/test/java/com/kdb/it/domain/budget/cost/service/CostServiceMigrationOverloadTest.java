package com.kdb.it.domain.budget.cost.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.code.service.CodeService;
import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.service.OrgNameResolver;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.repository.BtermmRepository;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.util.XcrLookupService;
import com.kdb.it.exception.CustomGeneralException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 마이그레이션 전용 기간 검증 생략 오버로드의 동작을 고정합니다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostServiceMigrationOverloadTest {

    @Mock private CostRepository costRepository;
    @Mock private BtermmRepository btermmRepository;
    @Mock private UserRepository cuserIRepository;
    @Mock private OrgNameResolver orgNameResolver;
    @Mock private CodeService codeService;
    @Mock private XcrLookupService xcrLookupService;
    @Mock private CostQueryService queryService;

    @InjectMocks private CostService costService;

    /**
     * 생략 플래그가 true면 기간 검증을 아예 호출하지 않는다.
     *
     * <p>이후 채번·조회 단계는 lenient mock의 기본값(0 등)만으로도 정상 완료될 수 있어 예외가 나지 않을 수도 있다. 핵심 단언은 "기간 검증에서 예외가
     * 나오지 않는다"이므로, 예외가 발생하든 안 하든 {@code CustomGeneralException}이 아니면 통과시키고 실제 검증 호출 여부는 {@code
     * verify}로 고정한다.
     */
    @Test
    @DisplayName("skipBudgetPeriodValidation=true면 validateBudgetPeriod를 호출하지 않는다")
    void 생략플래그가_참이면_기간검증을_호출하지_않는다() {
        doThrow(new CustomGeneralException("예산 신청 기간이 아닙니다."))
                .when(codeService)
                .validateBudgetPeriod();

        Throwable thrown =
                Assertions.catchThrowable(
                        () -> costService.createCost(new CostDto.CreateRequest(), true));

        if (thrown != null) {
            Assertions.assertThat(thrown).isNotInstanceOf(CustomGeneralException.class);
        }
        verify(codeService, never()).validateBudgetPeriod();
    }

    /** 기존 1-인자 시그니처는 종전대로 기간을 검증한다. */
    @Test
    @DisplayName("1-인자 createCost는 기간 검증을 그대로 수행한다")
    void 기존시그니처는_기간검증을_유지한다() {
        doThrow(new CustomGeneralException("예산 신청 기간이 아닙니다."))
                .when(codeService)
                .validateBudgetPeriod();

        Assertions.assertThatThrownBy(() -> costService.createCost(new CostDto.CreateRequest()))
                .isInstanceOf(CustomGeneralException.class);

        verify(codeService, times(1)).validateBudgetPeriod();
    }
}
