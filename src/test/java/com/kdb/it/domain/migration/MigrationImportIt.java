package com.kdb.it.domain.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bitemm;
import com.kdb.it.domain.budget.project.repository.ProjectItemRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.budget.work.repository.BbugtmRepository;
import com.kdb.it.domain.migration.dto.MigrationDto;
import com.kdb.it.domain.migration.dto.SheetKind;
import com.kdb.it.domain.migration.service.MigrationImportService;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * <p>목(Mock)으로는 증명할 수 없는 세 가지를 확인합니다.
 *
 * <ul>
 *   <li>BLOCKER가 남은 요청은 트랜잭션 전체가 롤백되어 아무 원장도 남기지 않는다.
 *   <li>{@link com.kdb.it.domain.migration.service.MigrationApprovalStamper}가 붙인 결재완료 받이가 실제로
 *       {@code BbugtmRepositoryImpl}의 집계 조인(결재완료 서브쿼리)을 통과해 예산 집계에 잡힌다.
 *   <li>부문계획 조정이 자본예산이 만든 품목을 버전 교체하면서도, 편성률 단일 적용({@code applyItemRates}의 연도 전체 재작성)이 다른 원천의 기존
 *       편성행을 지우지 않는다.
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

    @Autowired private MigrationImportService service;
    @Autowired private CostRepository costRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectItemRepository projectItemRepository;
    @Autowired private BbugtmRepository bbugtmRepository;
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
     */
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
        MigrationDto.CommitResponse response =
                service.commit(
                        new MigrationDto.CommitRequest(
                                List.of(sheet(SheetKind.COST, "cost.json")), List.of()),
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
                        List.of(sheet(SheetKind.COST, "cost.json")), List.of()),
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

    @Test
    @DisplayName("자본예산과 부문계획을 함께 반영하면 품목이 조정 금액으로 교체되고 편성요청 원값은 이력으로 남는다")
    void 부문계획_조정이_품목을_교체한다() {
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(
                                sheet(SheetKind.CAPITAL_PROJECT, "capital.json"),
                                sheet(SheetKind.PLAN_ADJUSTMENT, "plan.json")),
                        List.of()),
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

    @Test
    @DisplayName("같은 파일을 다시 반영하면 중복으로 거부한다")
    void 재업로드는_중복으로_거부된다() {
        MigrationDto.CommitRequest request =
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.COST, "cost.json")), List.of());
        service.commit(request, ACTOR_ENO);

        assertThatThrownBy(() -> service.commit(request, ACTOR_ENO))
                .hasMessageContaining("반영할 수 없습니다");
    }

    @Test
    @DisplayName("이관 대상이 아닌 기존 연도 편성행은 편성률이 유지된다")
    void 기존_편성행의_편성률이_유지된다() {
        // 1차 반영으로 편성행을 만들고
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.COST, "cost.json")), List.of()),
                ACTOR_ENO);
        int before = bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N").size();
        assertThat(before).isPositive();

        // 다른 시트를 추가 반영해도 앞서 만든 편성행이 applyItemRates의 연도 전체 재작성에 사라지지 않는다 (§3.5)
        service.commit(
                new MigrationDto.CommitRequest(
                        List.of(sheet(SheetKind.CAPITAL_PROJECT, "capital.json")), List.of()),
                ACTOR_ENO);

        assertThat(bbugtmRepository.findByBseYyAndDelYn(BSE_YY, "N"))
                .hasSizeGreaterThanOrEqualTo(before);
        // 앞서 만든 BCOSTM 원천 편성행 자체가 여전히 남아 있는지도 함께 확인한다 (연도 전체 재작성이 다른 원천을 지우지 않는다는 직접 증거)
        assertThat(bbugtmRepository.findByBseYyAndFntTbNmAndDelYn(BSE_YY, "BCOSTM", "N"))
                .hasSize(before);
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
