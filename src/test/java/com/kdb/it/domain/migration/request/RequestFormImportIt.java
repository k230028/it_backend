package com.kdb.it.domain.migration.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.kdb.it.domain.migration.request.dto.AmountUnit;
import com.kdb.it.domain.migration.request.dto.FormSheetKind;
import com.kdb.it.domain.migration.request.dto.RequestFormDiagnosticCode;
import com.kdb.it.domain.migration.request.dto.RequestFormDto;
import com.kdb.it.domain.migration.request.service.RequestFormImportService;
import com.kdb.it.domain.migration.request.support.RequestFormFixtures;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 편성요청서 반입을 실제 로컬 Oracle에서 검증하는 통합 테스트입니다.
 *
 * <p>목(Mock)으로는 증명할 수 없는 세 가지를 확인합니다.
 *
 * <ul>
 *   <li>파일 1건이 {@code REQUIRES_NEW} 트랜잭션 1개라, 깨진 파일이 섞여도 정상 파일은 커밋된다.
 *   <li>같은 파일을 두 번 반영하면 자연키 중복으로 차단되고 원장이 늘지 않는다.
 *   <li>반입이 편성행({@code BBUGTM})을 만들지 않는다 — {@code applyItemRates}는 연도 전량을 논리삭제한 뒤 재삽입하므로 이 경로에서 절대
 *       부르면 안 된다.
 * </ul>
 *
 * <p>구성은 {@code MigrationImportIt}과 같습니다. 전체 스프링 컨텍스트가 필요하고({@code CostService}·{@code
 * ProjectService} 빈 그래프에 의존), 테스트 트랜잭션 자동 롤백이 없어 각 반영이 실제로 커밋되므로 원자성 증명에 적합합니다. 대신 앞뒤로 테스트 연도 행을 물리
 * 삭제해 다음 실행이 깨끗한 연도에서 시작하게 합니다(운영 코드는 논리삭제만 씁니다).
 */
@Tag("it")
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더를 대체
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class RequestFormImportIt {

    /** 실 데이터와 섞이지 않는 테스트 연도. {@code MigrationImportIt}(2999)과도 겹치지 않게 둡니다. */
    private static final String BSE_YY = "2998";

    /** 로컬 DB에 실재하는 사번(부서 180=IT기획부). 업로드 사용자 및 SecurityContext 인증 주체로 함께 씁니다. */
    private static final String ACTOR_ENO = "K140024";

    /** 로컬 `CORGNI`에 실재하는 조직명. 폴더명 → 부서 해석이 성공해야 파일이 차단되지 않습니다. */
    private static final String DEPT_NAME = "기획관리부문";

    /** 로컬 `BPROJM`에 실제로 쓰인 부서·팀 조합(IT기획부/IT기획팀). 보정값이 실재 코드여야 원장이 정상입니다. */
    private static final String DEPT_CODE = "180";

    private static final String DEPT_TEAM_CODE = "18001";

    /** 전결권 자본예산 계열 코드(전무이사). */
    private static final String EDRT_TC = "21";

    @Autowired private RequestFormImportService service;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

    /** JPA Auditing이 채우는 감사자 필드(NOT NULL)를 위해 인증된 SecurityContext를 심습니다. */
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
     * 테스트 연도 행을 물리 삭제로 정리합니다. 앞뒤 모두 정리해 중간에 죽은 실행이 남긴 행이 다음 실행을 깨뜨리지 않게 합니다.
     *
     * <p>FK 참조 방향을 고려해 자식 테이블부터 지웁니다. {@code CAPPLM}·{@code CAPPLA}는 연도 컬럼이 없어 이관 전용 신청서번호
     * 패턴({@code APF-2998-%})으로 매칭합니다.
     */
    @BeforeEach
    @AfterEach
    void cleanUpTestYearData() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            execute(
                                    "DELETE FROM TPRMPP_CAPPLA WHERE APF_DCM_NO LIKE 'APF-"
                                            + BSE_YY
                                            + "-%'");
                            execute(
                                    "DELETE FROM TPRMPP_CAPPLM WHERE APF_DCM_NO LIKE 'APF-"
                                            + BSE_YY
                                            + "-%'");
                            executeForYear("DELETE FROM TPRMPP_BBUGTM WHERE BSE_YY = :yy");
                            executeForYear(
                                    "DELETE FROM TPRMPP_BITEMM WHERE ABUS_MNG_NO IN (SELECT"
                                            + " ABUS_MNG_NO FROM TPRMPP_BPROJM WHERE BSE_YY = :yy)");
                            executeForYear("DELETE FROM TPRMPP_BPROJM WHERE BSE_YY = :yy");
                            executeForYear("DELETE FROM TPRMPP_BCOSTM WHERE BSE_YY = :yy");
                        });
    }

    @Test
    @DisplayName("파일 1건을 반영하면 사업·품목·전산업무비가 결재완료 받이와 함께 생긴다")
    void createsLedgerWithCompletedApproval() {
        RequestFormDto.ImportResponse response = commit(fullForm("요청서.xls"));

        assertThat(response.files()).hasSize(1);
        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(response.summary().created().capitalProjects()).isPositive();
        assertThat(response.summary().created().costs()).isPositive();

        assertThat(countOf("TPRMPP_BPROJM")).isPositive();
        assertThat(countOf("TPRMPP_BCOSTM")).isPositive();
        assertThat(countCompletedApprovals()).isPositive();
    }

    @Test
    @DisplayName("깨진 파일이 섞여도 정상 파일은 반영된다")
    void appliesHealthyFileWhenAnotherFails() {
        RequestFormDto.ImportResponse response =
                commit(
                        file("깨진.xlsx", "엑셀 아님".getBytes(StandardCharsets.UTF_8)),
                        fullForm("요청서.xls"));

        assertThat(response.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.FAILED);
        assertThat(response.files().get(1).status()).isEqualTo(RequestFormDto.FileStatus.APPLIED);
        assertThat(countOf("TPRMPP_BCOSTM")).isPositive();
    }

    @Test
    @DisplayName("같은 파일을 두 번 반영하면 중복으로 차단되고 원장이 늘지 않는다")
    void rejectsReupload() {
        commit(fullForm("요청서.xls"));
        int afterFirst = countOf("TPRMPP_BCOSTM");

        RequestFormDto.ImportResponse second = commit(fullForm("요청서.xls"));

        assertThat(second.files().get(0).status()).isEqualTo(RequestFormDto.FileStatus.BLOCKED);
        assertThat(second.files().get(0).diagnostics())
                .extracting(RequestFormDto.FormDiagnostic::code)
                .contains(RequestFormDiagnosticCode.DUPLICATE_EXISTS);
        assertThat(countOf("TPRMPP_BCOSTM")).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("반입은 편성행을 만들지 않는다")
    void neverCreatesBudgetRows() {
        commit(fullForm("요청서.xls"));

        // applyItemRates는 연도 전량을 논리삭제한 뒤 재삽입하므로 이 경로에서 절대 부르면 안 된다
        assertThat(countOf("TPRMPP_BBUGTM")).isZero();
    }

    @Test
    @DisplayName("사전검증은 원장을 만들지 않는다")
    void dryRunWritesNothing() {
        RequestFormDto.ImportResponse response =
                service.importBatch(
                        List.of(fullForm("요청서.xls")), manifest("요청서.xls"), ACTOR_ENO, true);

        assertThat(response.dryRun()).isTrue();
        assertThat(countOf("TPRMPP_BPROJM")).isZero();
        assertThat(countOf("TPRMPP_BCOSTM")).isZero();
    }

    private RequestFormDto.ImportResponse commit(MultipartFile... files) {
        List<MultipartFile> list = Arrays.asList(files);
        String[] names =
                list.stream().map(MultipartFile::getOriginalFilename).toArray(String[]::new);
        return service.importBatch(list, manifest(names), ACTOR_ENO, false);
    }

    private static MultipartFile fullForm(String name) {
        return file(name, RequestFormFixtures.fullFormXls());
    }

    private static MultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("files", name, "application/vnd.ms-excel", bytes);
    }

    private static RequestFormDto.ImportManifest manifest(String... fileNames) {
        List<RequestFormDto.FileEntry> entries =
                Arrays.stream(fileNames)
                        .map(
                                name ->
                                        new RequestFormDto.FileEntry(
                                                DEPT_NAME + "/" + name,
                                                DEPT_NAME,
                                                null,
                                                AmountUnit.WON,
                                                null))
                        .toList();
        List<RequestFormDto.CellOverride> overrides =
                entries.stream().flatMap(entry -> overridesFor(entry.fileKey()).stream()).toList();
        return new RequestFormDto.ImportManifest(BSE_YY, entries, overrides);
    }

    /**
     * 픽스처의 조직·담당자·전결권을 로컬 DB에 실재하는 값으로 보정합니다.
     *
     * <p>픽스처는 실 제출본의 <b>레이아웃</b>을 재현한 것이라 이름·사번까지 로컬 DB와 맞지는 않습니다. 이 테스트의 관심사는 조직 해석 정확도가 아니라 파일 단위
     * 트랜잭션 경계이므로, 미리보기에서 사람이 고르는 것과 같은 경로(보정값)로 해소하고 반영 자체를 검증합니다. 조직·사용자 해석 자체는 {@code
     * OrgIdentityResolver} 단위 테스트가 담당합니다.
     */
    private static List<RequestFormDto.CellOverride> overridesFor(String fileKey) {
        return Stream.of("svnDpmC", "svnTemC", "tlrUsid", "usid", "dvmTlrUsid", "dvmUsid", "edrtTc")
                .map(
                        field ->
                                new RequestFormDto.CellOverride(
                                        fileKey,
                                        FormSheetKind.CAPITAL_OVERVIEW,
                                        null,
                                        field,
                                        overrideValue(field)))
                .toList();
    }

    private static String overrideValue(String field) {
        if (field.endsWith("Usid")) return ACTOR_ENO;
        if (field.equals("edrtTc")) return EDRT_TC;
        return field.equals("svnTemC") ? DEPT_TEAM_CODE : DEPT_CODE;
    }

    private int countOf(String table) {
        Number count =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        "SELECT COUNT(*) FROM "
                                                + table
                                                + " WHERE BSE_YY = :yy AND DEL_YN = 'N'")
                                .setParameter("yy", BSE_YY)
                                .getSingleResult();
        return count.intValue();
    }

    private int countCompletedApprovals() {
        Number count =
                (Number)
                        entityManager
                                .createNativeQuery(
                                        "SELECT COUNT(*) FROM TPRMPP_CAPPLM m JOIN TPRMPP_CAPPLA a"
                                                + " ON a.APF_DCM_NO = m.APF_DCM_NO WHERE m.APF_DCM_NO"
                                                + " LIKE 'APF-"
                                                + BSE_YY
                                                + "-%' AND m.IT_PTL_APF_PRG_STS_C = '2'")
                                .getSingleResult();
        return count.intValue();
    }

    private void execute(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private void executeForYear(String sql) {
        entityManager.createNativeQuery(sql).setParameter("yy", BSE_YY).executeUpdate();
    }
}
