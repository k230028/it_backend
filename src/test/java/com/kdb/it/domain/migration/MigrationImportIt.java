package com.kdb.it.domain.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.domain.budget.cost.dto.CostDto;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.cost.service.CostService;
import com.kdb.it.domain.budget.project.dto.ProjectDto;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.project.service.ProjectService;
import com.kdb.it.domain.budget.work.entity.Bbugtm;
import com.kdb.it.domain.budget.work.entity.BbugtmId;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.RowDecision;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationApprovalStamper;
import com.kdb.it.domain.migration.service.MigrationImportService;
import com.kdb.it.domain.migration.service.MigrationIoeCodes;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수기 엑셀 이관 반영({@link MigrationImportService#commit})을 실제 로컬 Oracle에서 검증하는 통합 테스트(Task 13).
 *
 * <p>목(Mock)으로는 증명할 수 없는 것들을 확인합니다.
 *
 * <ul>
 *   <li>BLOCKER가 남은 요청은 트랜잭션 전체가 롤백되어 아무 원장도 남기지 않는다.
 *   <li>{@link com.kdb.it.domain.migration.service.MigrationApprovalStamper}가 붙인 결재완료 받이가 실제로
 *       {@code BbugtmRepositoryImpl}의 집계 조인(결재완료 서브쿼리)을 통과해 예산 집계에 잡힌다.
 *   <li>편성률 단일 적용({@code applyItemRates}의 연도 전체 재작성)이 서로 다른 원천(자본예산·전산업무비)의 기존 편성행을 지우지 않는다.
 *   <li>편성요청서 반입(1단계)이 만든 원장에 종합본·하반기 조정(2·3단계)을 매칭으로 반영해도 차단되지 않고, 요청 품목({@code BITEMM})은 그대로 활성으로
 *       남는다(Task 10). 부문계획 조정은 더 이상 품목을 버전 교체하지 않는다 — {@code
 *       ProjectService.replaceItemsForMigration}는 재설계 이후 호출자가 없어 삭제했다.
 *   <li>위임예산 시트가 기존 경상사업에 매칭되면 그 사업의 <b>모든</b> 품목에 배분돼 편성행이 생긴다. 그 품목들은 전부 자본 계열({@code 102}·{@code
 *       105})이라, 배분 대상을 "자본 계열 밖 품목"으로 잡으면 매칭에 성공한 전 행이 {@code ITEM_BASE_ZERO}로 막힌다.
 *   <li>정보화사업에 자본 계열이 아닌 품목(일반관리비 계열)이 섞여 있어도 종합본의 `일반관리비` 열을 기준으로 같은 조정비율을 받는다 — 기본 편성률 100%로 조용히
 *       편성되지 않는다.
 *   <li>재업로드는 더 이상 {@code DUPLICATE_EXISTS}로 차단되지 않는다(설계 문서 §5.1이 그 BLOCKER를 명시적으로 삭제했다 — "존재가 이제
 *       매칭 성공 조건"). 대신 보정값 없이 같은 종합본을 다시 올리면 매칭으로 흘러 원장을 다시 만들지 않고 편성만 멱등하게 다시 적용한다.
 * </ul>
 *
 * <p>예산연도는 실 데이터와 섞이지 않도록 {@code 2999}를 씁니다. {@code MigrationImportService}는 {@code
 * CostService}·{@code ProjectService}·{@code PlanService}·{@code BudgetRateApplicationService} 등
 * 서비스 빈 전체 그래프에 의존하므로, 리포지토리·QueryDSL만 올리는 {@code @DataJpaTest} 슬라이스({@code
 * AbstractOracleRepositoryTest})로는 그 빈들을 찾지 못해 기동에 실패한다({@code NoSuchBeanDefinitionException}). 그래서
 * 이 테스트는 {@code AuditFailureIsolationIT}와 같이 전체 스프링 컨텍스트를 올리는 {@code @SpringBootTest}를 쓴다. 부수 효과로
 * {@code @DataJpaTest}의 테스트 트랜잭션 자동 롤백이 없어 각 {@code commit()} 호출이 실제로 커밋된다 — BLOCKER 롤백 테스트가 "테스트
 * 프레임워크가 어차피 다 되돌린다"가 아니라 서비스 자신의 {@code @Transactional} 경계가 실제로 롤백하는지를 증명하게 되므로, 원자성 증명 목적에는 이쪽이 더
 * 정확하다. 실제 커밋되는 만큼 {@link #cleanUpTestYearData()}의 물리 삭제가 다음 실행의 중복 판정을 막는 데 필수적이다(운영 코드는 논리삭제만
 * 사용하며, 이 물리 삭제는 테스트 전용이다).
 *
 * <p>이관 원장 쓰기({@code CostService.createCost}·{@code ProjectService.createProject})는 JPA Auditing이
 * 채우는 {@code FST_ENR_USID}/{@code LST_CHG_USID}가 물리 NOT NULL이라 인증된 {@code SecurityContext}가 필요합니다.
 * {@link #authenticate()}가 로컬 DB에 실재하는 사번으로 이를 채웁니다({@code AuditFailureIsolationIT}와 같은 패턴).
 *
 * <p>{@code MfaConfig.mfaProviderRegistry}는 {@code local-ext}·{@code local-int}·{@code dev}·{@code
 * prod} 프로파일에서만 만들어져 {@code test-it}로 전체 컨텍스트를 올리면 {@code AuthService}가 요구하는 그 빈을 찾지 못해 기동에
 * 실패합니다(같은 프로파일의 {@code AuditFailureIsolationIT}도 동일하게 실패해 이 태스크와 무관한 선행 결함임을 확인했습니다). 이관 경로는 MFA를
 * 전혀 타지 않으므로, 컨텍스트 기동만을 위해 빈 레지스트리를 공급하는 {@link com.kdb.it.support.MfaTestSupportConfig}를 가져옵니다.
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를
            // 대체(EnvironmentValidator 통과).
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class MigrationImportIt {

    private static final String BSE_YY = "2999"; // 실 데이터와 섞이지 않는 테스트 연도

    /** 로컬 DB에 실재하는 사번(부서 180=IT기획부). 이관 업로드 사용자 및 SecurityContext 인증 주체로 함께 씁니다. */
    private static final String ACTOR_ENO = "K140024";

    /** 로컬 CORGNI에 실재하는 부서코드(IT기획부). capital.json 픽스처가 이미 검증한 값과 같습니다(§ 짧은_코드컬럼이_코드값으로_저장된다). */
    private static final String DEPT_CODE = "180";

    /** "유지보수료"의 실제 비목코드. cost.json 픽스처 1행과 같은 값입니다(§ 전산업무비를_반영하면_결재받이가_예산집계에_잡힌다). */
    private static final String COST_IOE_C = "011";

    /** 로컬 BG_UNT_ABUS_C 공통코드에 실재하는 사업코드(cost.json 픽스처 1행과 같은 값). */
    private static final String REGISTERED_ABUS_CODE = "571";

    /** 로컬 BG_UNT_ABUS_C 공통코드에 등록되지 않은 값(실측: 501~570·571·802만 등록). */
    private static final String UNREGISTERED_ABUS_CODE = "999";

    /**
     * 자본 계열이 아닌 비목코드 하나(일반관리비 계열). {@code MigrationIoeCodes.CAPITAL_CODES}에 들지 않아 세 비목그룹 어디에도 속하지
     * 않으므로, 종합본의 `일반관리비` 열을 기준으로 하는 네 번째 그룹의 대상이 됩니다 (설계 §3.4).
     */
    private static final String GENERAL_IOE_C = "001";

    /**
     * 그룹 합계 허용오차(원). {@link com.kdb.it.domain.migration.service.MigrationAllocationPlanner}가 계산한
     * 반올림 잔차는 <b>금액</b>에 흡수되지만, {@code MigrationImportService}가 원장 반영 단계로 넘기는 계약은 그 금액이 아니라
     * <b>편성률</b>(비목코드 → {@code ASG_RT})뿐입니다. {@code BudgetRateApplicationService.applyItemRates}는 그
     * 편성률로 각 품목의 편성금액을 "요청금액 × 편성률"로 독립적으로 다시 계산하므로, 편성률이 물리 스케일 5(소수 다섯째 자리, 0.00001%포인트)로 반올림된
     * 오차가 금액으로 되곱해질 때 그대로 전파됩니다. 이론상 최대 오차는 "목표 편성액 × 0.000005%"이며, 이 테스트들의 기준액(약 14억원) 규모에서는 최대 약
     * 70원까지 벌어질 수 있습니다(실측: 29.58748%로 반올림되는 경우 약 31.2원). 여유를 두고 200원으로 잡습니다.
     */
    private static final BigDecimal GROUP_AMOUNT_TOLERANCE = new BigDecimal("200");

    @Autowired private MigrationImportService service;
    @Autowired private CostRepository costRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectItemRepository projectItemRepository;
    @Autowired private BbugtmRepository bbugtmRepository;
    @Autowired private ProjectService projectService;
    @Autowired private CostService costService;
    @Autowired private MigrationApprovalStamper approvalStamper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    /**
     * JPA Auditing이 채우는 감사자 필드(NOT NULL)를 위해 인증된 SecurityContext를 심습니다.
     *
     * <p>이관 오케스트레이션은 {@code CostService.createCost}·{@code ProjectService.createProject}를 그대로
     * 호출하는데, 두 서비스의 엔티티는 {@code MigrationApprovalStamper}처럼 감사자 필드를 직접 채우지 않고 {@code
     * JpaAuditConfig.auditorProvider()}(=SecurityContext 기반)에 의존합니다. 인증 컨텍스트 없이 실행하면 {@code
     * ORA-01400}(NOT NULL 위반)으로 실패합니다.
     */
    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                ACTOR_ENO,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 테스트 연도({@code BSE_YY=2999}) 행을 물리 삭제로 정리합니다.
     *
     * <p>남아 있으면 다음 실행의 중복 판정({@code DUPLICATE_EXISTS})이 걸려 테스트가 실패합니다. FK 참조 방향을 고려해 자식 테이블부터 지웁니다.
     * {@code CAPPLM}·{@code CAPPLA}는 연도 컬럼이 없어 이관 전용 신청서번호 패턴({@code APF-2999-%})으로 매칭합니다.
     *
     * <p>이 테스트는 {@code @SpringBootTest}라 {@code @DataJpaTest}의 테스트 트랜잭션이 없어 {@link EntityManager}로
     * 직접 실행하는 네이티브 UPDATE/DELETE에는 활성 트랜잭션이 없습니다({@code TransactionRequiredException}). {@link
     * TransactionTemplate}으로 정리 전용 트랜잭션을 열어 커밋합니다.
     *
     * <p><b>실행 전에도 정리합니다.</b> 뒤 정리만 걸면 JVM이 테스트 중간에 죽은 실행(빌드 취소·세션 중단)이 남긴 {@code BSE_YY=2999} 행이
     * 다음 실행의 첫 테스트에 그대로 보여, 행 수를 세는 단정이 실제 회귀 없이 실패합니다(관측된 증상: 기대 2행에 3행). 앞뒤 모두 정리하면 어느 쪽으로 중단돼도
     * 다음 실행이 깨끗한 연도에서 시작합니다.
     */
    @BeforeEach
    @AfterEach
    void cleanUpTestYearData() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_CAPPLA WHERE APF_DCM_NO LIKE 'APF-"
                                                    + BSE_YY
                                                    + "-%'")
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_CAPPLM WHERE APF_DCM_NO LIKE 'APF-"
                                                    + BSE_YY
                                                    + "-%'")
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BBUGTM WHERE BSE_YY = :yy")
                                    .setParameter("yy", BSE_YY)
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BPLANA WHERE REQ_DOC_NO LIKE 'PLN-"
                                                    + BSE_YY
                                                    + "-%'")
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BPLANM WHERE BSE_YY = :yy")
                                    .setParameter("yy", BSE_YY)
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BITEMM WHERE ABUS_MNG_NO IN"
                                                    + " (SELECT ABUS_MNG_NO FROM TPRMPP_BPROJM WHERE"
                                                    + " BSE_YY = :yy)")
                                    .setParameter("yy", BSE_YY)
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BPROJM WHERE BSE_YY = :yy")
                                    .setParameter("yy", BSE_YY)
                                    .executeUpdate();
                            entityManager
                                    .createNativeQuery(
                                            "DELETE FROM TPRMPP_BCOSTM WHERE BSE_YY = :yy")
                                    .setParameter("yy", BSE_YY)
                                    .executeUpdate();
                        });
    }

    @Test
    @DisplayName("전산업무비 픽스처를 반영하면 BCOSTM과 결재완료 받이가 함께 생기고 예산 집계에 실제로 잡힌다")
    void 전산업무비를_반영하면_결재받이가_예산집계에_잡힌다() {
        // 매칭되는 기존 원장이 없는 행은 더 이상 자동으로 CREATE_NEW가 되지 않는다(§Task 10 발견 사항).
        // 관리자가 __decision 보정값으로 명시해야 원장을 새로 만든다.
        MigrationDto.CommitResponse response =
                service.commit(
                        new MigrationDto.CommitRequest(
                                List.of(sheet(SheetKind.COST, "cost.json")),
                                신규_결정(SheetKind.COST, 2, 3)),
                        ACTOR_ENO);

        assertThat(response.costCount()).isEqualTo(2);
        assertThat(costRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N")).hasSize(2);
        // 결재완료 받이가 붙어야 예산 집계에 잡힌다 (§3.6)
        assertThat(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BCOSTM", "N"))
                .isNotEmpty();

        // 행 개수가 아니라 BbugtmRepositoryImpl.sumApprovedCostAmountByIoeCValues의 결재완료 서브쿼리(CAPPLA
        // ⋈ CAPPLM, 상태=COMPLETED)가 실제로 통과하는지 금액으로 증명한다. 스탬프가 fntTbCrySno를 잘못 넘기거나 신청서 상태가
        // COMPLETED가 아니면 이 합계는 0으로 나온다.
        // KRW 행 15,000,000 + GBP 행 1,000×1,924=1,924,000
        assertThat(bbugtmRepository.sumApprovedAmountByIoeCValues(Set.of("011", "002"), BSE_YY))
                .isEqualByComparingTo(new BigDecimal("16924000"));
    }

    @Test
    @DisplayName("외화 행 금액은 서버가 FC_AMT × Ccodem 환율로 재계산해 저장한다")
    void 외화행은_서버가_재계산한다() {
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.COST, "cost.json")), 신규_결정(SheetKind.COST, 2, 3)),
                ACTOR_ENO);

        assertThat(costRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N"))
                .filteredOn(c -> "GBP".equals(c.getCurC()))
                .singleElement()
                .satisfies(
                        c -> {
                            // 픽스처 외화 1,000 GBP × 시드 환율 1,924 = 1,924,000원 (스프레드시트의 원화열 1,924
                            // 그대로가 아니라 서버 재계산값이 저장된다)
                            assertThat(c.getCostTotXpAmt())
                                    .isEqualByComparingTo(new BigDecimal("1924000"));
                            assertThat(c.getFcAmt()).isEqualByComparingTo(new BigDecimal("1000"));
                        });
    }

    @Test
    @DisplayName("BLOCKER가 남은 요청은 아무 행도 남기지 않고 실패한다")
    void 블로커가_있으면_전량_롤백된다() {
        MigrationDto.SheetPayload broken =
                new MigrationDto.SheetPayload(
                        SheetKind.COST,
                        BSE_YY,
                        List.of(
                                new MigrationDto.NormalizedRow(
                                        2,
                                        Map.of(
                                                "deptName", "존재하지않는부서",
                                                "requestDetail", "테스트",
                                                "ioeName", "유지보수료",
                                                "currency", "KRW",
                                                "krwAmount", "1000"))));

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        new MigrationDto.CommitRequest(List.of(broken), List.of()),
                                        ACTOR_ENO))
                .hasMessageContaining("반영할 수 없습니다");

        assertThat(costRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N")).isEmpty();
        assertThat(bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N")).isEmpty();
    }

    /**
     * Task 10에서 실 Oracle로 확인한 회귀: 이 테스트의 전제("부문계획 조정이 품목을 버전 교체한다")는 Task 9 재설계 이전 동작입니다. 재설계된
     * {@code MigrationImportService.commit}은 4단계 주석에 명시된 대로 "하반기 조정 계획 문서만 만들고 {@code BITEMM}은 건드리지
     * 않습니다" — 그 경로였던 {@code ProjectService.replaceItemsForMigration}는 호출자가 없어 삭제했습니다. 그 결과 활성 품목은
     * 조정 금액(8,000,000)이 아니라 자본예산 원값 (5,000,000)에 그대로 남아 이 테스트의 {@code active} 단정이 깨집니다. 새 동작("요청
     * 품목이 활성으로 남는다")은 {@link #하반기_조정_후에도_요청_품목이_활성으로_남는다}가 이미 고정하므로, 이 테스트는 중복이자 오래된 전제라 비활성화합니다.
     */
    @Disabled("Task 9 재설계로 품목 버전 교체가 폐지됨 — 새 동작은 하반기_조정_후에도_요청_품목이_활성으로_남는다가 고정")
    @Test
    @DisplayName("자본예산과 부문계획을 함께 반영하면 품목이 조정 금액으로 교체되고 편성요청 원값은 이력으로 남는다")
    void 부문계획_조정이_품목을_교체한다() {
        // 두 시트를 한 commit()에 함께 올리지 않는다 — PLAN_ADJUSTMENT 어댑터는 원장 생성요청을 내지 않아
        // (PlanAdjustmentSheetAdapter의 projects는 항상 빈 목록) CREATE_NEW 결정을 줘도 매칭 대상에서
        // 제외될 뿐이다(hasCreateRequest=false). 부문계획 행이 성립하려면 대상 사업이 그 commit()의
        // "시작 시점" 스냅샷에 이미 있어야 하므로, 자본예산으로 먼저 사업을 만든 뒤 별도 commit()으로
        // 부문계획을 반영한다 — 이 태스크가 고정하는 실제 업무 순서(1단계 반입 → 2단계 종합 → 3단계
        // 하반기 조정)와도 일치한다.
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.CAPITAL_PROJECT, "capital.json")),
                        신규_결정(SheetKind.CAPITAL_PROJECT, 2)),
                ACTOR_ENO);
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.PLAN_ADJUSTMENT, "plan.json")), List.of()),
                ACTOR_ENO);

        String projectNo =
                projectRepository
                        .findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N")
                        .get(0)
                        .getAbusMngNo();

        // 활성 품목은 조정 금액(부문계획 devAmount=8 → 8,000,000)만 남는다 (§5.4)
        List<Bitemm> active =
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y");
        assertThat(active).isNotEmpty();
        assertThat(active)
                .allSatisfy(
                        item ->
                                assertThat(item.getAmt())
                                        .isEqualByComparingTo(new BigDecimal("8000000")));

        // 편성요청 시점 품목(자본예산 devAmount=5 → 5,000,000)은 논리삭제로 보존된다
        List<Bitemm> history = projectItemRepository.findByAbusMngNoAndDelYn(projectNo, "Y");
        assertThat(history).as("편성요청 시점 품목이 논리삭제로 보존된다").isNotEmpty();
        assertThat(history)
                .anySatisfy(
                        item ->
                                assertThat(item.getAmt())
                                        .isEqualByComparingTo(new BigDecimal("5000000")));
    }

    /**
     * 재업로드의 새 안전망은 "차단"이 아니라 "매칭으로 흘러 원장을 다시 만들지 않는 것"임을 확인합니다.
     *
     * <p>설계 문서(§5.1, {@code
     * docs/superpowers/specs/2026-08-16-migration-allocation-redesign-design.md})가
     * `DUPLICATE_EXISTS`(사업명·전산업무비 자연키) 두 BLOCKER를 명시적으로 삭제하라고 정했다 — "존재가 이제 매칭 성공 조건"이기 때문이다.
     * 재업로드를 막던 옛 방어가 사라진 자리를, 매칭이 재현 가능한 원장을 다시 찾아 편성만 갱신하는 것으로 대신한다({@code applyItemRates}가 연도 전체를
     * 재작성하므로 같은 입력이면 편성 결과도 같아야 한다 — 멱등).
     *
     * <p>1차는 원장이 없으므로 {@code __decision=CREATE_NEW}로 명시해 만든다. 2차는 <b>보정값 없이</b> 같은 종합본을 그대로 다시 올린다
     * — 사용자가 dry-run 없이 실수로 같은 파일을 두 번 올리는 실제 시나리오와 같다. 2차가 정말 MATCH로 흐르는지는 가정하지 않고 결과로 확인한다: 원장 수가
     * 늘지 않고, 응답의 생성 건수가 0이며, 편성률이 1차와 같아야 한다.
     */
    @Test
    @DisplayName("재업로드는 매칭으로 흘러 원장을 다시 만들지 않는다")
    void 재업로드는_매칭으로_흘러_원장을_다시_만들지_않는다() {
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.COST, "cost.json")), 신규_결정(SheetKind.COST, 2, 3)),
                ACTOR_ENO);
        int costRowsAfterFirst =
                costRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N").size();
        Map<String, BigDecimal> ratesAfterFirst = ratesByPkColNm();

        MigrationDto.CommitResponse second =
                service.commit(
                        new MigrationDto.CommitRequest(
                                List.of(sheet(SheetKind.COST, "cost.json")), List.of()),
                        ACTOR_ENO);

        assertThat(second.costCount()).as("재업로드는 새 전산업무비를 만들지 않는다").isZero();
        assertThat(second.projectCount()).isZero();
        assertThat(costRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N"))
                .as("원장 건수가 늘지 않는다(매칭으로 흘렀다는 뜻)")
                .hasSize(costRowsAfterFirst);

        Map<String, BigDecimal> ratesAfterSecond = ratesByPkColNm();
        assertThat(ratesAfterSecond.keySet())
                .as("같은 원장 키에 다시 편성된다")
                .containsExactlyInAnyOrderElementsOf(ratesAfterFirst.keySet());
        ratesAfterFirst.forEach(
                (pk, rate) ->
                        assertThat(ratesAfterSecond.get(pk))
                                .as("재업로드 후에도 편성률이 그대로다(멱등)")
                                .isEqualByComparingTo(rate));
    }

    /** 그 연도 {@code BCOSTM} 편성행을 원장 키(품목관리번호) → 편성률로 모읍니다. */
    private Map<String, BigDecimal> ratesByPkColNm() {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (Bbugtm budget :
                bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BCOSTM", "N")) {
            out.put(budget.getPkColNm(), budget.getAsgRt());
        }
        return out;
    }

    /**
     * 이관 대상이 아닌 기존 편성행의 **편성률 값**이 유지되는지 확인합니다.
     *
     * <p>행 수만 세면 아무것도 증명하지 못합니다 — 편성률이 전부 100%로 리셋돼도 행 수는 같습니다. 그래서 먼저 사업 하나를 편성률 70으로 만들어 두고, 그 뒤
     * 무관한 사업의 전산업무비를 반영한 다음 그 70이 그대로 남아 있는지 {@code ASG_RT}로 직접 읽어 확인합니다.
     *
     * <p>사업의 편성률은 {@code BBUGTM}에 사업관리번호로 걸린 행이 없어(키가 품목관리번호) 스냅샷이 품목 편성행에서 역산해야 얻어집니다. {@code
     * 'BPROJM|사업관리번호'} 키를 찾던 구 구현은 항상 null을 받아 기본값 100으로 채웠고, {@code applyItemRates}가 연도 전체를 재작성하므로
     * 두 번째 반영에서 이 사업의 편성률이 조용히 100으로 바뀝니다. 벌크 논리삭제라 {@code BBUGT_L}에도 흔적이 남지 않습니다.
     *
     * <p><b>Task 10에서 매칭 경로로 다시 조립했습니다.</b> 원래(재설계 이전)는 두 반영 모두 CAPITAL_PROJECT·COST 시트를 자동
     * CREATE_NEW로 올렸습니다. 지금도 {@code __decision=CREATE_NEW} 보정값만 붙이면 기계적으로는 통과하지만, 이 화면이 이제 원장을 만드는
     * 화면이 아니라는 재설계 전제를 생각하면 "이관 대상이 아닌 기존 사업"은 실제로는 <b>편성요청서 반입이 이미 만들어 둔 사업</b>일 때가 정상 시나리오다 —
     * CREATE_NEW는 관리자가 드물게 쓰는 예외 경로다. 그래서 두 반영 모두 {@link #요청사업을_만든다}·{@link #요청비용을_만든다}로 원장을 먼저 조립한
     * 뒤 매칭 경로로 편성한다. 증명하려는 것(무관한 두 번째 반영이 첫 번째 반영이 만든 편성률을 지우지 않는다)은 원장이 어떻게 생겼는지와 무관하므로 바뀌지 않는다 —
     * {@code applyItemRates}의 연도 전체 재작성·{@code existingItemRateByItemNo} 보존 로직은 매칭 경로에서도 똑같이 탄다.
     */
    @Test
    @DisplayName("이관 대상이 아닌 기존 연도 편성행은 편성률 값까지 유지된다")
    void 기존_편성행의_편성률이_유지된다() {
        // 1차 반영: 웹한글 기안기 도입류 사업을 매칭 경로로 조정비율 0.7에 편성 → ASG_RT=70
        요청사업을_만든다("보존 대상 사업", MigrationIoeCodes.IOE_SW, new BigDecimal("1000000000"));
        service.commit(자본예산_커밋요청("보존 대상 사업", "1000", "0.7"), ACTOR_ENO);

        assertThat(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N"))
                .as("자본예산 반영이 편성률 70의 품목 편성행을 만든다")
                .isNotEmpty()
                .allSatisfy(
                        budget ->
                                assertThat(budget.getAsgRt())
                                        .isEqualByComparingTo(BigDecimal.valueOf(70)));
        int itemBudgetRows =
                bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N").size();

        // 2차 반영: 무관한 사업의 전산업무비를 매칭 경로로 편성한다. 1차 사업은 이번 반영에 들어 있지
        // 않으므로 편성률이 그대로여야 한다
        요청비용을_만든다("무관벤더", "무관 계약", new BigDecimal("15000000"), null);
        service.commit(전산업무비_커밋요청("무관벤더", "무관 계약", REGISTERED_ABUS_CODE), ACTOR_ENO);

        assertThat(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N"))
                .as("무관한 반영 후에도 기존 사업의 편성률 70이 유지된다")
                .hasSize(itemBudgetRows)
                .allSatisfy(
                        budget ->
                                assertThat(budget.getAsgRt())
                                        .isEqualByComparingTo(BigDecimal.valueOf(70)));
        // 전산업무비 원천 편성행도 함께 생겨 있어야 한다 (연도 전체 재작성이 서로를 지우지 않는다)
        assertThat(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BCOSTM", "N"))
                .isNotEmpty();
    }

    /**
     * 자본예산 픽스처의 짧은 코드 컬럼이 실제 물리 폭 안에서 저장되는지 확인합니다.
     *
     * <p>{@code EXE_PTT_YN}은 {@code VARCHAR2(1)}, {@code IT_PTL_EDRT_TC}는 {@code VARCHAR2(2)}입니다.
     * 엑셀 라벨(`미정(검토중)`·`부문장`)을 그대로 대입하면 dry-run이 초록인 채 commit에서 {@code ORA-12899}가 나고 전량 롤백됩니다. 픽스처가
     * 이 필드들을 빈 문자열로 비워 두면 그 경로를 한 번도 지나가지 않습니다.
     */
    @Test
    @DisplayName("자본예산 반영이 추진가능성·전결권·팀코드를 코드값으로 저장한다")
    void 짧은_코드컬럼이_코드값으로_저장된다() {
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.CAPITAL_PROJECT, "capital.json")),
                        신규_결정(SheetKind.CAPITAL_PROJECT, 2)),
                ACTOR_ENO);

        assertThat(projectRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N"))
                .singleElement()
                .satisfies(
                        project -> {
                            assertThat(project.getExePttYn()).isEqualTo("2"); // 미정(검토중)
                            assertThat(project.getEdrtTc()).isEqualTo("22"); // 부문장(자본 계열)
                            assertThat(project.getSvnTemC()).isEqualTo("180");
                            assertThat(project.getDvmTemC()).isEqualTo("180");
                        });
    }

    // ===== Task 10: 편성요청서 반입 후 종합본 반영이 통과하는 회귀 고정 =====
    //
    // 위 테스트들은 MigrationImportService가 스스로 CREATE_NEW로 원장을 만드는 경로를 검증한다. 아래
    // 테스트들은 그 앞 단계(편성요청서 반입)가 이미 만들어 둔 원장에 종합본·하반기 조정을 "매칭"으로
    // 반영하는 경로를 검증한다 — 이 태스크 이전에는 같은 사업명이 이미 있다는 이유로 DUPLICATE_EXISTS
    // BLOCKER가 나 전량 차단됐던 바로 그 흐름이다.

    @Test
    @Tag("it")
    @DisplayName("요청서_반입_후_종합본을_올리면_차단되지_않고_편성된다")
    void 요청서_반입_후_종합본을_올리면_차단되지_않고_편성된다() {
        // 1단계: 편성요청서 반입이 만든 상태를 직접 조립한다(RequestFormImportService를 부르지 않고
        // 같은 결과만 만든다 — 두 기능의 결합을 테스트에 끌어들이지 않는다).
        요청사업을_만든다("웹한글 기안기 도입", MigrationIoeCodes.IOE_SW, new BigDecimal("1406000000"));

        // 2단계: 종합본 자본예산 시트 — 같은 사업명, 조정비율 0.7
        MigrationDto.CommitResponse response =
                service.commit(자본예산_커밋요청("웹한글 기안기 도입", "1406", "0.7"), ACTOR_ENO);

        // 원장을 새로 만들지 않는다
        assertThat(response.projectCount()).isZero();
        assertThat(projectRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N")).hasSize(1);

        // 편성행이 실효 편성률 70%로 생긴다. 조정비율 0.7은 분모(1,406,000,000)와 정확히 나누어떨어지는
        // 값이라 편성률에 반올림이 관여하지 않고, 금액도 예외 없이 정확히 일치한다(§GROUP_AMOUNT_TOLERANCE
        // 는 이 경우처럼 반올림이 없는 케이스에는 필요 없다).
        List<Bbugtm> budgets = bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N");
        assertThat(budgets).hasSize(1);
        assertThat(budgets.get(0).getAsgRt()).isEqualByComparingTo("70.00000");
        assertThat(budgets.get(0).getBgDupAmt()).isEqualByComparingTo("984200000.000");
    }

    @Test
    @Tag("it")
    @DisplayName("하반기_조정_후에도_요청_품목이_활성으로_남는다")
    void 하반기_조정_후에도_요청_품목이_활성으로_남는다() {
        String projectNo =
                요청사업을_만든다("웹한글 기안기 도입", MigrationIoeCodes.IOE_SW, new BigDecimal("1406000000"));

        service.commit(부문계획_커밋요청("웹한글 기안기 도입", "416"), ACTOR_ENO);

        // 요청 원장이 그대로 살아 있다 — BITEMM 버전 교체를 폐지했다
        assertThat(projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y"))
                .hasSize(1)
                .allSatisfy(item -> assertThat(item.getAmt()).isEqualByComparingTo("1406000000"));

        List<Bbugtm> budgets = bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N");
        assertThat(budgets).hasSize(1);
        // 편성률(ASG_RT)은 실제로 저장되는 값이므로 정확히 일치해야 한다.
        assertThat(budgets.get(0).getAsgRt()).isEqualByComparingTo("29.58748");
        // 금액은 정확히 416,000,000과 일치하지 않는다 — 배분기가 그 값에 정확히 맞도록 반올림 잔차를
        // 흡수한 결과는 "편성률"로만 다음 단계에 전달되고, applyItemRates가 "요청금액 × 그 편성률"로
        // 금액을 독립 재계산하기 때문이다. 이유와 허용오차 산정 근거는 GROUP_AMOUNT_TOLERANCE 주석 참고.
        허용오차_내에서_같다(budgets.get(0).getBgDupAmt(), new BigDecimal("416000000"));
    }

    @Test
    @Tag("it")
    @DisplayName("소수_편성률이_Oracle에서_왕복한다")
    void 소수_편성률이_Oracle에서_왕복한다() {
        Bbugtm saved =
                bbugtmRepository.saveAndFlush(
                        Bbugtm.builder()
                                .bgNo("BG-" + BSE_YY + "-9999")
                                .sno(1)
                                .bseYy(BSE_YY)
                                .fntTbNm("BITEMM")
                                .pkColNm("GCL-TEST-0001")
                                .fntTbCrySno(1)
                                .ioeC(MigrationIoeCodes.IOE_SW)
                                .bgDupAmt(new BigDecimal("416000000.000"))
                                .asgRt(new BigDecimal("29.58748"))
                                .build());
        entityManager.clear();

        Bbugtm reloaded =
                bbugtmRepository
                        .findById(new BbugtmId(saved.getBgNo(), saved.getSno()))
                        .orElseThrow();
        assertThat(reloaded.getAsgRt()).isEqualByComparingTo("29.58748");
    }

    @Test
    @Tag("it")
    @DisplayName("종합본에_없는_기존_사업의_편성률이_유지된다")
    void 종합본에_없는_기존_사업의_편성률이_유지된다() {
        요청사업을_만든다("웹한글 기안기 도입", MigrationIoeCodes.IOE_SW, new BigDecimal("1406000000"));
        String otherNo =
                요청사업을_만든다("건드리지 않을 사업", MigrationIoeCodes.IOE_DEV, new BigDecimal("500000000"));
        기존_편성률을_넣는다(otherNo, MigrationIoeCodes.IOE_DEV, new BigDecimal("55"));

        service.commit(자본예산_커밋요청("웹한글 기안기 도입", "1406", "0.7"), ACTOR_ENO);

        Bbugtm kept =
                bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N").stream()
                        .filter(b -> MigrationIoeCodes.IOE_DEV.equals(b.getIoeC()))
                        .findFirst()
                        .orElseThrow();
        assertThat(kept.getAsgRt()).isEqualByComparingTo("55");
    }

    /**
     * 목표액이 그룹 안 여러 품목에 걸쳐 있어도, 각 품목의 편성률은 정확히 같고 그룹 합계는 목표액에 근사함을 확인합니다.
     *
     * <p>같은 비목({@code IOE_SW})의 품목 두 개를 한 사업에 둡니다 — 배분기는 비목그룹 단위로 계산하므로 품목이 여러 개여도 "합계가 여러 원장 행에 걸쳐
     * 있어도 근사가 성립한다"만 단일 품목 사례에 추가로 증명합니다.
     */
    @Test
    @Tag("it")
    @DisplayName("그룹 배분의 반올림 잔차는 금액에서 흡수되지만 편성률은 정확히 일치한다")
    void 그룹_배분의_반올림_잔차는_금액에서_흡수되지만_편성률은_정확히_일치한다() {
        String projectNo =
                요청사업을_만든다(
                        "다품목 조정 테스트 사업",
                        List.of(
                                new 요청품목(MigrationIoeCodes.IOE_SW, new BigDecimal("700000000")),
                                new 요청품목(MigrationIoeCodes.IOE_SW, new BigDecimal("706000000"))));

        service.commit(부문계획_커밋요청("다품목 조정 테스트 사업", "416"), ACTOR_ENO);

        List<String> itemNos =
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y").stream()
                        .map(Bitemm::getGclMngNo)
                        .toList();
        List<Bbugtm> budgets =
                bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N").stream()
                        .filter(b -> itemNos.contains(b.getPkColNm()))
                        .toList();

        assertThat(budgets).hasSize(2);
        assertThat(budgets)
                .as("같은 비목그룹의 품목은 모두 같은 실효 편성률을 정확히 공유한다")
                .allSatisfy(b -> assertThat(b.getAsgRt()).isEqualByComparingTo("29.58748"));

        BigDecimal sum =
                budgets.stream().map(Bbugtm::getBgDupAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
        허용오차_내에서_같다(sum, new BigDecimal("416000000"));
    }

    /**
     * 매칭된 전산업무비의 빈 사업코드({@code BG_UNT_ABUS_C})가 종합본 값으로 채워지는지 정상·비정상 두 경우로 확인합니다 (§4.1).
     *
     * <p>편성요청서 양식에는 사업코드 열이 없어 1단계가 만드는 {@code BCOSTM}은 이 값이 항상 {@code null}입니다({@link #요청비용을_만든다}가
     * 그 전제를 그대로 재현합니다).
     */
    @Test
    @Tag("it")
    @DisplayName("매칭된_전산업무비의_빈_사업코드가_종합본_값으로_채워진다")
    void 매칭된_전산업무비의_빈_사업코드가_종합본_값으로_채워진다() {
        String costNo = 요청비용을_만든다("테스트벤더", "테스트 유지보수", new BigDecimal("15000000"), null);

        service.commit(전산업무비_커밋요청("테스트벤더", "테스트 유지보수", REGISTERED_ABUS_CODE), ACTOR_ENO);

        Bcostm updated = costRepository.findByCostBgNoAndDelYnAndLstYn(costNo, "N", "Y").get(0);
        assertThat(updated.getBgUntAbusC()).isEqualTo(REGISTERED_ABUS_CODE);
    }

    /**
     * 코드표에 없는 사업코드는 반입 전체를 막아 원장에 닿지 못합니다.
     *
     * <p>{@code MigrationValidator.validateCostRowAlways}는 사업코드를 매칭 행 포함 <b>항상</b> 검사하므로, 이 값은
     * {@code MigrationImportService.fillCostBudgetUnitCodes}에 닿기도 전에 {@code CODE_UNRESOLVED}
     * BLOCKER로 커밋 전체를 막습니다. {@code fillCostBudgetUnitCodes} 자신의 카탈로그 방어(코드표에 없으면 채우지 않는다)는 코드 카탈로그
     * 자체가 비어 있을 때만 실제로 열리는 방어선이라, 카탈로그가 채워진 이 로컬 DB 환경에서는 검증기가 이미 더 앞에서 막아 도달하지 않습니다. 그래도 "미등록 값이
     * 원장에 새지 않는다"는 최종 결과는 이 테스트가 고정합니다. {@code BG_UNT_ABUS_C}는 {@code VARCHAR2(3)}이라, 이 방어가 없으면
     * 등록되지 않은 원문이 그대로 저장을 시도해 {@code ORA-12899}로 터질 수 있는 값입니다.
     */
    @Test
    @Tag("it")
    @DisplayName("미등록_사업코드는_검증에서_차단되어_채워지지_않는다")
    void 미등록_사업코드는_검증에서_차단되어_채워지지_않는다() {
        String costNo = 요청비용을_만든다("테스트벤더", "테스트 유지보수", new BigDecimal("15000000"), null);

        assertThatThrownBy(
                        () ->
                                service.commit(
                                        전산업무비_커밋요청("테스트벤더", "테스트 유지보수", UNREGISTERED_ABUS_CODE),
                                        ACTOR_ENO))
                .hasMessageContaining("반영할 수 없습니다");

        Bcostm unchanged = costRepository.findByCostBgNoAndDelYnAndLstYn(costNo, "N", "Y").get(0);
        assertThat(unchanged.getBgUntAbusC()).isNull();
    }

    @Test
    @Tag("it")
    @DisplayName("이미_값이_있는_사업코드는_덮지_않는다")
    void 이미_값이_있는_사업코드는_덮지_않는다() {
        String costNo = 요청비용을_만든다("테스트벤더", "테스트 유지보수", new BigDecimal("15000000"), "501");

        service.commit(전산업무비_커밋요청("테스트벤더", "테스트 유지보수", REGISTERED_ABUS_CODE), ACTOR_ENO);

        Bcostm unchanged = costRepository.findByCostBgNoAndDelYnAndLstYn(costNo, "N", "Y").get(0);
        assertThat(unchanged.getBgUntAbusC()).isEqualTo("501");
    }

    /**
     * 위임예산 시트가 1단계의 경상사업에 매칭되면 배분이 성립해 편성행이 생깁니다.
     *
     * <p><b>회귀 고정.</b> 위임예산의 목표액 컬럼은 {@code costAmount}인데, 종전에는 이 컬럼이 비목그룹을 갖지 않는다는 이유로 배분 대상이 "자본
     * 계열 밖 품목"으로 떨어졌습니다. 그런데 1단계가 만드는 위임예산 경상사업의 품목은 전부 자본 계열({@code 102} 국외기계장치·{@code 105}
     * 국외기타무형자산)이라 대상이 <b>빈 목록</b>이 되고, 목표액이 0보다 크므로 매칭에 성공한 <b>전 행</b>이 {@code ITEM_BASE_ZERO}
     * BLOCKER로 막혔습니다. 관리자가 {@code CREATE_NEW}로 우회하면 같은 부점의 경상사업이 중복 생성됩니다.
     *
     * <p>종합본 합계(40,000,000)를 원장 요청 합계(50,000,000)와 다르게 두어 실효 편성률이 실제로 계산되는지(100%로 떨어지지 않는지) 함께
     * 확인합니다.
     */
    @Test
    @Tag("it")
    @DisplayName("위임예산이_기존_경상사업에_매칭되면_차단되지_않고_편성된다")
    void 위임예산이_기존_경상사업에_매칭되면_차단되지_않고_편성된다() {
        String projectNo =
                요청경상사업을_만든다(
                        List.of(
                                new 요청품목(
                                        MigrationIoeCodes.IOE_HW_OVERSEA,
                                        new BigDecimal("30000000")),
                                new 요청품목(
                                        MigrationIoeCodes.IOE_SW_OVERSEA,
                                        new BigDecimal("20000000"))));

        MigrationDto.CommitRequest request = 위임예산_커밋요청("25000000", "15000000");

        // 사전검증에 ITEM_BASE_ZERO가 남지 않는다 — 매칭에 성공한 행이 배분에서 막히지 않는다는 뜻이다
        assertThat(
                        service.dryRun(
                                        new MigrationDto.DryRunRequest(
                                                request.sheets(), request.overrides()))
                                .diagnostics())
                .extracting(MigrationDto.CellDiagnostic::code)
                .doesNotContain("ITEM_BASE_ZERO");

        MigrationDto.CommitResponse response = service.commit(request, ACTOR_ENO);

        assertThat(response.projectCount()).as("경상사업을 중복 생성하지 않는다").isZero();
        assertThat(projectRepository.findByBseYyAndLstYnAndDelYn(BSE_YY, "Y", "N")).hasSize(1);

        List<String> itemNos =
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y").stream()
                        .map(Bitemm::getGclMngNo)
                        .toList();
        List<Bbugtm> budgets =
                bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N").stream()
                        .filter(b -> itemNos.contains(b.getPkColNm()))
                        .toList();

        assertThat(budgets).as("자본 계열 품목 두 건에 모두 편성행이 생긴다").hasSize(2);
        // 40,000,000 / 50,000,000 × 100 = 80. 위임예산은 사업의 모든 품목이 한 대상이라 실효율이 같다
        assertThat(budgets)
                .allSatisfy(b -> assertThat(b.getAsgRt()).isEqualByComparingTo("80.00000"));
        BigDecimal sum =
                budgets.stream().map(Bbugtm::getBgDupAmt).reduce(BigDecimal.ZERO, BigDecimal::add);
        허용오차_내에서_같다(sum, new BigDecimal("40000000"));
    }

    /**
     * 정보화사업에 섞인 비자본 품목도 종합본의 `일반관리비` 열을 기준으로 같은 조정비율을 받습니다 (설계 §3.4).
     *
     * <p><b>회귀 고정.</b> 자본예산 어댑터가 {@code devAmount}·{@code hwAmount}·{@code swAmount} 셋만 목표액으로 내던
     * 동안, 1단계가 {@code BITEMM}에 함께 담은 일반관리비 계열 품목은 {@code ioeRates}에 키가 없어 {@code
     * BudgetRateApplicationService}의 2버킷 폴백으로 떨어졌고, 이관은 두 버킷을 모두 null로 넘기므로 <b>기본 편성률 100%</b>가
     * 적용됐습니다. 진단이 하나도 나지 않아 조정비율 0.7 사업의 일반관리비만 조용히 100%로 편성됐습니다.
     */
    @Test
    @Tag("it")
    @DisplayName("정보화사업의_비자본_품목도_조정비율로_편성된다")
    void 정보화사업의_비자본_품목도_조정비율로_편성된다() {
        String projectNo =
                요청사업을_만든다(
                        "웹한글 기안기 도입",
                        List.of(
                                new 요청품목(MigrationIoeCodes.IOE_SW, new BigDecimal("1406000000")),
                                new 요청품목(GENERAL_IOE_C, new BigDecimal("100000000"))));

        service.commit(자본예산_커밋요청("웹한글 기안기 도입", "1406", "0.7", "100"), ACTOR_ENO);

        Map<String, BigDecimal> rateByIoeC = new LinkedHashMap<>();
        for (Bitemm item :
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y")) {
            bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BITEMM", "N").stream()
                    .filter(b -> item.getGclMngNo().equals(b.getPkColNm()))
                    .findFirst()
                    .ifPresent(b -> rateByIoeC.put(item.getIoeC(), b.getAsgRt()));
        }

        assertThat(rateByIoeC).containsKeys(MigrationIoeCodes.IOE_SW, GENERAL_IOE_C);
        assertThat(rateByIoeC.get(MigrationIoeCodes.IOE_SW)).isEqualByComparingTo("70.00000");
        assertThat(rateByIoeC.get(GENERAL_IOE_C))
                .as("일반관리비 품목이 기본 편성률 100%로 조용히 편성되면 안 된다")
                .isNotEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(rateByIoeC.get(GENERAL_IOE_C)).isEqualByComparingTo("70.00000");
    }

    /** 요청 품목 하나의 비목코드·금액입니다. {@link #요청사업을_만든다(String, List)}가 여러 품목을 조립할 때 씁니다. */
    private record 요청품목(String ioeC, BigDecimal amount) {}

    /**
     * 편성요청서 반입(1단계)이 만드는 위임예산 경상사업({@code ODN_YN='Y'})을 조립합니다.
     *
     * <p>위임예산 매칭은 사업명이 아니라 `부서코드 + ODN_YN='Y'`로 이뤄지므로({@code
     * MigrationLedgerMatcher.matchOrdinaryProject}) 사업명은 매칭에 관여하지 않습니다.
     *
     * @param items 요청 품목 목록 (위임예산은 국외 계열 {@code 102}·{@code 105})
     * @return 생성된 사업관리번호
     */
    private String 요청경상사업을_만든다(List<요청품목> items) {
        return 요청사업을_만든다(BSE_YY + "년 IT기획부 위임예산(경상)", items, "Y");
    }

    /**
     * 편성요청서 반입(1단계)이 만드는 상태를 직접 조립합니다. {@code RequestFormImportService}를 부르지 않고 실제 반입 경로({@code
     * RequestFormFileImporter.apply})가 쓰는 것과 같은 두 호출({@code ProjectService.createProject} + {@link
     * MigrationApprovalStamper#stamp})만으로 같은 결과(BPROJM·BITEMM 원장 + 결재완료 받이)를 만듭니다 — 두 기능의 결합을 테스트에
     * 끌어들이지 않기 위해서입니다.
     *
     * @param projectName 사업명
     * @param items 요청 품목 목록 (비지 않음)
     * @param odnYn 경상 여부. 위임예산 매칭 대상은 {@code "Y"}입니다
     * @return 생성된 사업관리번호
     */
    private String 요청사업을_만든다(String projectName, List<요청품목> items, String odnYn) {
        ProjectDto.CreateRequest request = new ProjectDto.CreateRequest();
        request.setBseYy(BSE_YY);
        request.setAbusNm(projectName);
        request.setSvnDpmC(DEPT_CODE);
        request.setSvnTemC(DEPT_CODE);
        request.setDvmDpmC(DEPT_CODE);
        request.setDvmTemC(DEPT_CODE);
        request.setUsid(ACTOR_ENO);
        request.setTlrUsid(ACTOR_ENO);
        request.setOdnYn(odnYn);

        List<ProjectDto.BitemmDto> bitemms = new ArrayList<>();
        for (요청품목 spec : items) {
            ProjectDto.BitemmDto item = new ProjectDto.BitemmDto();
            item.setIoeC(spec.ioeC());
            item.setGclNm("테스트 품목");
            item.setCurC("KRW");
            item.setAmt(spec.amount());
            item.setXcrBseDt(BSE_YY + "0101");
            bitemms.add(item);
        }
        request.setItems(bitemms);

        String projectNo = projectService.createProject(request, true);
        Bprojm created = projectRepository.findByAbusMngNoAndDelYn(projectNo, "N").orElseThrow();
        approvalStamper.stamp(
                "BPROJM", projectNo, created.getSno(), "테스트 편성요청서 반입", ACTOR_ENO, BSE_YY);
        return projectNo;
    }

    /** 정보화사업(경상이 아님)을 조립합니다. {@link #요청사업을_만든다(String, List, String)}의 오버로드입니다. */
    private String 요청사업을_만든다(String projectName, List<요청품목> items) {
        return 요청사업을_만든다(projectName, items, "N");
    }

    /** 요청 품목이 하나뿐인 사업을 조립합니다. {@link #요청사업을_만든다(String, List)}의 단일 품목 오버로드입니다. */
    private String 요청사업을_만든다(String projectName, String ioeC, BigDecimal amount) {
        return 요청사업을_만든다(projectName, List.of(new 요청품목(ioeC, amount)));
    }

    /**
     * 이관 대상이 아닌 기존 편성행을 미리 심어 둡니다. {@code MigrationYearSnapshot}이 커밋 시작 시점에 읽는 {@code
     * existingItemRateByItemNo}에 이 값이 잡혀야 {@code applyItemRates}의 연도 전체 재작성 후에도 값이 보존되므로, 반드시
     * {@code service.commit(...)}보다 먼저 호출해야 합니다.
     *
     * @param projectNo 대상 사업관리번호 ({@link #요청사업을_만든다}가 미리 만든 것)
     * @param ioeC 그 사업의 품목 비목코드 (해당 품목을 찾는 키)
     * @param rate 심어 둘 편성률(%)
     */
    private void 기존_편성률을_넣는다(String projectNo, String ioeC, BigDecimal rate) {
        Bitemm item =
                projectItemRepository.findByAbusMngNoAndDelYnAndLstYn(projectNo, "N", "Y").stream()
                        .filter(candidate -> ioeC.equals(candidate.getIoeC()))
                        .findFirst()
                        .orElseThrow();
        BigDecimal amount =
                item.getAmt()
                        .multiply(rate)
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        bbugtmRepository.save(
                Bbugtm.builder()
                        .bgNo("BG-" + BSE_YY + "-EXIST")
                        .sno(1)
                        .bseYy(BSE_YY)
                        .fntTbNm("BITEMM")
                        .pkColNm(item.getGclMngNo())
                        .fntTbCrySno(item.getSno())
                        .ioeC(ioeC)
                        .bgDupAmt(amount)
                        .asgRt(rate)
                        .build());
    }

    /**
     * 편성요청서 반입(1단계)이 만드는 전산업무비 상태를 직접 조립합니다. 편성요청서 양식에는 사업코드({@code BG_UNT_ABUS_C}) 열이 없어 {@code
     * CostService.createCost}가 만드는 원장의 그 값은 항상 {@code null}입니다 — {@code existingAbusCode}로 그 전제(또는
     * 예외적으로 이미 값이 채워진 상태)를 표현합니다.
     *
     * @param vendor 계약상대처명 (부서 기준 자연키 재료)
     * @param contract 계약명 (부서 기준 자연키 재료)
     * @param amount 요청금액(원 단위)
     * @param existingAbusCode 미리 채워 둘 사업코드. {@code null}이면 반입 직후의 빈 상태 그대로 둡니다
     * @return 생성된 전산업무비관리번호
     */
    private String 요청비용을_만든다(
            String vendor, String contract, BigDecimal amount, String existingAbusCode) {
        CostDto.CreateRequest request = new CostDto.CreateRequest();
        request.setBseYy(BSE_YY);
        request.setIoeC(COST_IOE_C);
        request.setCttNm(contract);
        request.setCttOppNm(vendor);
        request.setCostSvnDpmC(DEPT_CODE);
        request.setCurC("KRW");
        request.setCostTotXpAmt(amount);
        request.setXcrBseDt(BSE_YY + "0101");
        request.setCgprId(ACTOR_ENO);
        request.setBgUntAbusC(existingAbusCode);

        String costNo = costService.createCost(request, true);
        Bcostm created = costRepository.findByCostBgNoAndDelYnAndLstYn(costNo, "N", "Y").get(0);
        approvalStamper.stamp(
                "BCOSTM", costNo, created.getBgSno(), "테스트 편성요청서 반입", ACTOR_ENO, BSE_YY);
        return costNo;
    }

    /**
     * 종합본 자본예산 시트 커밋 요청을 만듭니다. 비목 {@code IOE_SW}(106)는 {@code CapitalProjectSheetAdapter}가 {@code
     * swAmount} 열에 기본으로 매기는 비목이라, {@link #요청사업을_만든다}가 만든 품목과 짝짓기 위해 금액을 그 열에 싣습니다.
     *
     * @param projectName 사업명 (매칭 키)
     * @param swAmountMillion 백만원 단위 금액 문자열 (자본예산 시트는 백만원 단위)
     * @param adjustRate 조정비율 (예: "0.7")
     */
    private MigrationDto.CommitRequest 자본예산_커밋요청(
            String projectName, String swAmountMillion, String adjustRate) {
        return 자본예산_커밋요청(projectName, swAmountMillion, adjustRate, "");
    }

    /**
     * 일반관리비 열까지 채운 자본예산 커밋 요청입니다.
     *
     * @param generalAmountMillion 백만원 단위 일반관리비. 빈 문자열이면 그 열을 비운 것으로 봅니다(기존 편성률 유지)
     */
    private MigrationDto.CommitRequest 자본예산_커밋요청(
            String projectName,
            String swAmountMillion,
            String adjustRate,
            String generalAmountMillion) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("projectName", projectName);
        cells.put("swAmount", swAmountMillion);
        cells.put("adjustRate", adjustRate);
        cells.put("generalAmount", generalAmountMillion);
        MigrationDto.SheetPayload sheet =
                new MigrationDto.SheetPayload(
                        SheetKind.CAPITAL_PROJECT,
                        BSE_YY,
                        List.of(new MigrationDto.NormalizedRow(2, cells)));
        return new MigrationDto.CommitRequest(List.of(sheet), List.of());
    }

    /**
     * 위임예산 시트 커밋 요청을 만듭니다. 이 시트의 원화환산액은 이미 원 단위라 백만원 배수를 곱하지 않습니다.
     *
     * <p>부점명은 {@link #요청경상사업을_만든다}가 쓴 부서코드({@link #DEPT_CODE})로 해석되는 이름이어야 매칭됩니다.
     *
     * @param hwKrw HW 원화환산액(원)
     * @param swKrw SW 원화환산액(원)
     */
    private MigrationDto.CommitRequest 위임예산_커밋요청(String hwKrw, String swKrw) {
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("branchName", "IT기획부");
        cells.put("itemName", "테스트 위임예산 품목");
        cells.put("currency", "KRW");
        cells.put("hwQty", "1");
        cells.put("hwFcAmount", "");
        cells.put("hwKrwAmount", hwKrw);
        cells.put("swQty", "1");
        cells.put("swFcAmount", "");
        cells.put("swKrwAmount", swKrw);
        MigrationDto.SheetPayload sheet =
                new MigrationDto.SheetPayload(
                        SheetKind.DELEGATED_BUDGET,
                        BSE_YY,
                        List.of(new MigrationDto.NormalizedRow(2, cells)));
        return new MigrationDto.CommitRequest(List.of(sheet), List.of());
    }

    /**
     * 하반기 조정(부문계획) 커밋 요청을 만듭니다. 자본예산과 같은 이유로 확정금액을 {@code swAmount} 열에 싣습니다.
     *
     * @param projectName 사업명 (매칭 키)
     * @param swAmountMillion 백만원 단위 확정금액 문자열 (부문계획 시트도 백만원 단위)
     */
    private MigrationDto.CommitRequest 부문계획_커밋요청(String projectName, String swAmountMillion) {
        Map<String, String> cells = Map.of("projectName", projectName, "swAmount", swAmountMillion);
        MigrationDto.SheetPayload sheet =
                new MigrationDto.SheetPayload(
                        SheetKind.PLAN_ADJUSTMENT,
                        BSE_YY,
                        List.of(new MigrationDto.NormalizedRow(2, cells)));
        return new MigrationDto.CommitRequest(List.of(sheet), List.of());
    }

    /** 전산업무비 종합본 커밋 요청을 만듭니다. 부서·비목·계약명은 {@link #요청비용을_만든다}가 만든 원장과 정확히 같아야 매칭됩니다. */
    private MigrationDto.CommitRequest 전산업무비_커밋요청(String vendor, String contract, String abusCode) {
        Map<String, String> cells =
                Map.of(
                        "abusCode", abusCode,
                        "ioeName", "유지보수료",
                        "vendorName", vendor,
                        "requestDetail", contract,
                        "deptName", "IT기획부",
                        "currency", "KRW",
                        "krwAmount", "15000");
        MigrationDto.SheetPayload sheet =
                new MigrationDto.SheetPayload(
                        SheetKind.COST, BSE_YY, List.of(new MigrationDto.NormalizedRow(2, cells)));
        return new MigrationDto.CommitRequest(List.of(sheet), List.of());
    }

    /** {@code actual}과 {@code expected}의 차이가 {@link #GROUP_AMOUNT_TOLERANCE} 이내인지 확인합니다. */
    private void 허용오차_내에서_같다(BigDecimal actual, BigDecimal expected) {
        BigDecimal diff = actual.subtract(expected).abs();
        assertThat(diff)
                .as(
                        "실제값 %s과 기대값 %s의 차이(%s)가 허용오차 %s 이내여야 합니다",
                        actual, expected, diff, GROUP_AMOUNT_TOLERANCE)
                .isLessThanOrEqualTo(GROUP_AMOUNT_TOLERANCE);
    }

    /**
     * 지정한 행들을 모두 {@code CREATE_NEW}로 결정하는 보정값 목록을 만듭니다.
     *
     * <p>Task 6/9 재설계 이후 매칭되는 원장이 없는 행은 이 결정이 없으면 항상 {@code LEDGER_NOT_MATCHED} BLOCKER입니다 — 원장을 새로
     * 만드는 것은 더 이상 "매칭 실패 시 기본 동작"이 아니라 관리자가 명시한 경우뿐입니다(§{@code MigrationImportService} 클래스
     * Javadoc). 이 화면 자체를 처음 쓰는(=아직 아무 원장도 없는) 시나리오를 검증하는 위쪽 테스트들이 이 헬퍼로 그 결정을 명시적으로 채웁니다.
     *
     * @param kind 대상 시트
     * @param excelRows 결정을 채울 엑셀 행 번호들
     */
    private List<MigrationDto.CellOverride> 신규_결정(SheetKind kind, int... excelRows) {
        List<MigrationDto.CellOverride> out = new ArrayList<>();
        for (int row : excelRows) {
            out.add(new MigrationDto.CellOverride(kind, row, RowDecision.COLUMN, "CREATE_NEW"));
        }
        return out;
    }

    /** 픽스처 JSON을 읽어 시트 페이로드로 만듭니다. 예산연도는 테스트 연도로 바꿔 실 데이터와 섞이지 않게 합니다. */
    private MigrationDto.SheetPayload sheet(SheetKind kind, String fixture) {
        try {
            List<MigrationDto.NormalizedRow> rows =
                    objectMapper.readValue(
                            new ClassPathResource("fixtures/migration/" + fixture).getInputStream(),
                            new TypeReference<List<MigrationDto.NormalizedRow>>() {});
            return new MigrationDto.SheetPayload(kind, BSE_YY, rows);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("픽스처를 읽지 못했습니다: " + fixture, e);
        }
    }
}
