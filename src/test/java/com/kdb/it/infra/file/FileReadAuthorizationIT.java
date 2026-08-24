package com.kdb.it.infra.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.authz.FileReadAuthorizerRegistry;
import com.kdb.it.infra.file.dto.FileDto;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.service.FileService;
import com.kdb.it.support.MfaTestSupportConfig;
import com.kdb.it.support.OracleAvailableCondition;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-05 파일 종류별 읽기 인가를 실제 로컬 Oracle의 부모·부서·위원 조인으로 검증하는 통합 테스트.
 *
 * <p>MockMvc 슬라이스({@code FileControllerTest})가 네 HTTP 경로의 403 계약을 검증하는 것과 달리, 이 테스트는 실제 repository와
 * authorizer 레지스트리를 사용해 {@link FileReadAuthorizerRegistry}, {@link FileOwnershipChecker}, {@link
 * FileService#getFiles}의 판정을 실 데이터로 확인한다.
 *
 * <p><b>데이터 격리 원칙</b>
 *
 * <ul>
 *   <li>모든 픽스처는 {@code SEC05} 접두부 + 테스트별 고유 UUID로 생성하고, {@link #cleanup()}에서 자식(CFILEM) → 부모 순으로
 *       삭제한다. 운영 기준선 행이나 격리 7건은 재사용·수정하지 않는다.
 *   <li>픽스처는 {@link JdbcTemplate}로 직접 INSERT해 {@code @LogTarget} 변경로그 리스너와 감사 채움을 거치지 않고 NOT
 *       NULL·부서·작성자 값을 결정적으로 통제한다. 판정(읽기 경로)은 실제 빈을 사용한다.
 *   <li>격리 행 검증은 read-only다. 운영 격리 행을 조회만 하고 일반 사용자에게 노출되지 않음을 단언한다.
 * </ul>
 *
 * <p>{@code test-it} 프로파일은 {@code ddl-auto=none}·Flyway 비활성으로 실 스키마를 변경하지 않으며, 로컬 Oracle이 꺼져 있으면
 * {@link OracleAvailableCondition}이 컨텍스트 로드 전에 깨끗이 스킵한다.
 */
@Tag("it")
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            // 비-prod 기동 필수값 — application.properties 의 ${JWT_SECRET} 플레이스홀더 대체(EnvironmentValidator
            // 통과).
            "jwt.secret=test-secret-key-for-junit-test-minimum-256-bits-length-ok"
        })
@ActiveProfiles("test-it")
@ExtendWith(OracleAvailableCondition.class)
@Import(MfaTestSupportConfig.class)
class FileReadAuthorizationIT {

    // ─────────────────────────────────────────
    // 파일 종류(APG_FL_KD_NM) 상수
    // ─────────────────────────────────────────
    private static final String KIND_REQUIREMENT = "요구사항정의서";
    private static final String KIND_GUIDE = "가이드문서";
    private static final String KIND_PLAN = "사업계획서";
    private static final String KIND_FEASIBILITY = "타당성검토표";
    private static final String KIND_COUNCIL_DOC = "협의회관련자료";
    private static final String KIND_BOARD = "공통게시판";
    private static final String KIND_REVIEW_COMMENT = "검토의견";
    private static final String KIND_UNREGISTERED = "정보화사업";

    /** 모든 픽스처 네임스페이스 접두부 — 운영 키(FL_/DOC-/ASCT-/NAC- 등)와 충돌하지 않는다. */
    private static final String NS = "SEC05";

    /** 자격등급 상수(관리자/정보보안/일반). */
    private static final List<String> ATH_ADMIN = List.of("ITPAD001");

    private static final List<String> ATH_INFOSEC = List.of("ITPAD002");
    private static final List<String> ATH_USER = List.of("ITPZZ001");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FileReadAuthorizerRegistry registry;
    @Autowired private FileOwnershipChecker fileOwnershipChecker;
    @Autowired private FileService fileService;
    @Autowired private MockMvc mockMvc;

    /** 테스트별 고유 접미부 — 픽스처 키 충돌을 막는다. */
    private String uid;

    @BeforeEach
    void setUp() {
        uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        cleanup(); // 이전 실행이 비정상 종료로 남긴 SEC05 잔여 행 선제 정리
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    /** SEC05 네임스페이스 픽스처를 자식(파일) → 부모 순으로 삭제한다(운영/격리 행 불변). */
    private void cleanup() {
        jdbcTemplate.update("DELETE FROM TPRMPP_CFILEM WHERE FL_MPN_ID LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_BCMMTM WHERE IT_PTL_ASCT_ID LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_BASCTM WHERE IT_PTL_ASCT_ID LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_BPROJM WHERE ABUS_MNG_NO LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_BRIVGM WHERE DOC_MNG_NO LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_BRDOCM WHERE DOC_MNG_NO LIKE '" + NS + "%'");
        jdbcTemplate.update("DELETE FROM TPRMPP_CBLBCM WHERE NAC_NO LIKE '" + NS + "%'");
    }

    // ═════════════════════════════════════════
    // 요구사항정의서 — 관리자 OR 작성자 OR 주관부서
    // ═════════════════════════════════════════

    @Test
    @DisplayName("요구사항정의서: 작성자·주관부서는 허용, 타인·타부서는 거부")
    void requirementDoc_authorAndDept_allowed_otherDenied() {
        String docMngNo = NS + "DOC" + uid;
        String author = NS + "A" + uid;
        String dept = NS + "D" + uid;
        String otherDept = NS + "X" + uid;
        insertRequirementDoc(docMngNo, dept, author);

        Cfilem file = fileOfKind(KIND_REQUIREMENT, docMngNo);

        // 작성자 본인 — 부서가 달라도 허용
        assertThat(registry.canRead(file, user(author, otherDept))).isTrue();
        // 주관부서 동일 — 작성자가 아니어도 허용
        assertThat(registry.canRead(file, user(NS + "Z" + uid, dept))).isTrue();
        // 타인 + 타부서 — 거부
        assertThat(registry.canRead(file, user(NS + "O" + uid, otherDept))).isFalse();
    }

    // ═════════════════════════════════════════
    // 협의회 연계 3종 — 관리자/정보보안 OR 위원 OR 사업 주관부서
    // ═════════════════════════════════════════

    @Test
    @DisplayName("협의회 3종: 관리자·정보보안·위원·사업주관부서 허용, 비위원+타부서 거부")
    void councilThreeKinds_roleMatrix() {
        String asctId = NS + "ASCT" + uid;
        String abusMngNo = NS + "ABUS" + uid;
        int sno = 1;
        String owningDept = NS + "D" + uid;
        String otherDept = NS + "X" + uid;
        String memberEno = NS + "M" + uid;
        String nonMemberEno = NS + "N" + uid;

        insertProject(abusMngNo, sno, owningDept);
        insertCouncil(asctId, abusMngNo, sno);
        insertCommitteeMember(asctId, "01", memberEno);

        for (String kind : List.of(KIND_PLAN, KIND_FEASIBILITY, KIND_COUNCIL_DOC)) {
            Cfilem file = fileOfKind(kind, asctId);
            assertThat(registry.canRead(file, user(NS + "ADM", ATH_ADMIN, otherDept)))
                    .as("관리자 허용 - %s", kind)
                    .isTrue();
            assertThat(registry.canRead(file, user(NS + "SEC", ATH_INFOSEC, otherDept)))
                    .as("정보보안관리자 허용 - %s", kind)
                    .isTrue();
            assertThat(registry.canRead(file, user(memberEno, otherDept)))
                    .as("협의회 위원 허용 - %s", kind)
                    .isTrue();
            assertThat(registry.canRead(file, user(nonMemberEno, owningDept)))
                    .as("사업 주관부서 허용 - %s", kind)
                    .isTrue();
            assertThat(registry.canRead(file, user(nonMemberEno, otherDept)))
                    .as("비위원+타부서 거부 - %s", kind)
                    .isFalse();
        }
    }

    // ═════════════════════════════════════════
    // 가이드문서 — 전사 공개(인증 사용자), 비인증 거부
    // ═════════════════════════════════════════

    @Test
    @DisplayName("가이드문서: 인증 사용자 허용, null 사용자 거부")
    void guideDoc_authenticatedAllowed_nullDenied() {
        Cfilem file = fileOfKind(KIND_GUIDE, NS + "GUIDE" + uid);
        assertThat(registry.canRead(file, user(NS + "U" + uid, NS + "D" + uid))).isTrue();
        assertThat(registry.canRead(file, null)).isFalse();
    }

    // ═════════════════════════════════════════
    // 공통게시판 — 공개기간 내 허용, 숨김·기간 전·기간 후·부모 없음 거부
    // ═════════════════════════════════════════

    @Test
    @DisplayName("공통게시판: 공개중 허용, 숨김·시작전·종료후·부모없음 거부, 관리자 우회")
    void board_visibilityMatrix() {
        LocalDate today = LocalDate.now();
        String visible = NS + "V" + uid;
        String hidden = NS + "H" + uid;
        String notStarted = NS + "S" + uid;
        String expired = NS + "E" + uid;
        String noParent = NS + "P" + uid; // 부모 게시물 미생성

        insertBoardPost(visible, "Y", today.minusDays(1), today.plusDays(1));
        insertBoardPost(hidden, "N", today.minusDays(1), today.plusDays(1));
        insertBoardPost(notStarted, "Y", today.plusDays(1), null);
        insertBoardPost(expired, "Y", null, today.minusDays(1));

        CustomUserDetails normal = user(NS + "U" + uid, NS + "D" + uid);
        assertThat(registry.canRead(fileOfKind(KIND_BOARD, visible), normal)).as("공개중 허용").isTrue();
        assertThat(registry.canRead(fileOfKind(KIND_BOARD, hidden), normal)).as("숨김 거부").isFalse();
        assertThat(registry.canRead(fileOfKind(KIND_BOARD, notStarted), normal))
                .as("시작 전 거부")
                .isFalse();
        assertThat(registry.canRead(fileOfKind(KIND_BOARD, expired), normal))
                .as("종료 후 거부")
                .isFalse();
        assertThat(registry.canRead(fileOfKind(KIND_BOARD, noParent), normal))
                .as("부모 없음 거부")
                .isFalse();
        // 관리자는 게시물 조회 없이 허용(숨김 게시물이라도)
        assertThat(
                        registry.canRead(
                                fileOfKind(KIND_BOARD, hidden),
                                user(NS + "ADM", ATH_ADMIN, NS + "X" + uid)))
                .as("관리자 우회")
                .isTrue();
    }

    // ═════════════════════════════════════════
    // 미등록·null 종류, null 부모, 존재하지 않는 부모 — 일반 거부 / 관리자 허용
    // ═════════════════════════════════════════

    @Test
    @DisplayName("미등록·null 종류/null·부재 부모: 일반 사용자 거부, 관리자 허용")
    void unregisteredNullAndMissingParents_denyNormalAllowAdmin() {
        CustomUserDetails normal = user(NS + "U" + uid, NS + "D" + uid);
        CustomUserDetails admin = user(NS + "ADM", ATH_ADMIN, NS + "X" + uid);

        // 미등록 종류 — 레지스트리 default-deny(관리자만)
        Cfilem unregistered = fileOfKind(KIND_UNREGISTERED, NS + "ANY" + uid);
        assertThat(registry.canRead(unregistered, normal)).as("미등록 종류 일반 거부").isFalse();
        assertThat(registry.canRead(unregistered, admin)).as("미등록 종류 관리자 허용").isTrue();

        // null 종류 — 레지스트리 default-deny(관리자만)
        Cfilem nullKind = fileOfKind(null, NS + "ANY" + uid);
        assertThat(registry.canRead(nullKind, normal)).as("null 종류 일반 거부").isFalse();
        assertThat(registry.canRead(nullKind, admin)).as("null 종류 관리자 허용").isTrue();

        // null 부모(등록 종류) — authorizer가 관리자만 허용
        Cfilem nullParent = fileOfKind(KIND_REQUIREMENT, null);
        assertThat(registry.canRead(nullParent, normal)).as("null 부모 일반 거부").isFalse();
        assertThat(registry.canRead(nullParent, admin)).as("null 부모 관리자 허용").isTrue();

        // 존재하지 않는 부모(등록 종류) — 일반 거부, 관리자 우회 허용
        Cfilem missingParent = fileOfKind(KIND_REQUIREMENT, NS + "NOPE" + uid);
        assertThat(registry.canRead(missingParent, normal)).as("부재 부모 일반 거부").isFalse();
        assertThat(registry.canRead(missingParent, admin)).as("부재 부모 관리자 허용").isTrue();
    }

    // ═════════════════════════════════════════
    // FileService.getFiles — 같은/다른 부모의 허용·거부 집합
    // ═════════════════════════════════════════

    @Test
    @DisplayName("getFiles: 같은 부모 허용 3건은 모두 반환, 거부 부모는 빈 목록")
    void getFiles_allowSetAndDenySet() {
        String docAllow = NS + "DOK" + uid;
        String docDeny = NS + "DNO" + uid;
        String deptA = NS + "DA" + uid;
        String deptB = NS + "DB" + uid;
        String authorA = NS + "AA" + uid;
        String authorB = NS + "AB" + uid;

        insertRequirementDoc(docAllow, deptA, authorA);
        insertRequirementDoc(docDeny, deptB, authorB);
        insertFile(NS + "FLA1" + uid, KIND_REQUIREMENT, docAllow, authorA);
        insertFile(NS + "FLA2" + uid, KIND_REQUIREMENT, docAllow, authorA);
        insertFile(NS + "FLA3" + uid, KIND_REQUIREMENT, docAllow, authorA);
        insertFile(NS + "FLB1" + uid, KIND_REQUIREMENT, docDeny, authorB);

        // authorA: docAllow의 작성자이자 주관부서 → 허용 / docDeny와는 무관 → 거부
        CustomUserDetails viewer = user(authorA, deptA);

        List<FileDto.Response> allowed =
                fileService.getFiles(condition(KIND_REQUIREMENT, docAllow), viewer);
        assertThat(allowed).hasSize(3);

        List<FileDto.Response> denied =
                fileService.getFiles(condition(KIND_REQUIREMENT, docDeny), viewer);
        assertThat(denied).isEmpty();
    }

    // ═════════════════════════════════════════
    // FileOwnershipChecker.checkReadAccess — 허용 통과 / 거부 예외
    // ═════════════════════════════════════════

    @Test
    @DisplayName("checkReadAccess: 허용은 통과, 거부는 AccessDeniedException")
    void checkReadAccess_allowPasses_denyThrows() {
        String docMngNo = NS + "DOC" + uid;
        String author = NS + "A" + uid;
        String dept = NS + "D" + uid;
        String flMpnId = NS + "FLOK" + uid;
        insertRequirementDoc(docMngNo, dept, author);
        insertFile(flMpnId, KIND_REQUIREMENT, docMngNo, author);

        assertThatCode(() -> fileOwnershipChecker.checkReadAccess(flMpnId, user(author, dept)))
                .doesNotThrowAnyException();

        assertThatThrownBy(
                        () ->
                                fileOwnershipChecker.checkReadAccess(
                                        flMpnId, user(NS + "O" + uid, NS + "X" + uid)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("파일 읽기 권한이 없습니다");
    }

    @Test
    @DisplayName("검토의견 첨부 HTTP: 무관한 사용자의 다운로드와 미리보기는 실제 인가 경로에서 403")
    void reviewCommentAttachment_httpDownloadAndPreview_deniedByRealAuthorization()
            throws Exception {
        String docMngNo = NS + "DOC" + uid;
        String author = NS + "A" + uid;
        String unrelated = NS + "U" + uid;
        String leadDept = NS + "D" + uid;
        String otherDept = NS + "X" + uid;
        String flMpnId = NS + "FLHTTP" + uid;
        insertRequirementDoc(docMngNo, leadDept, author);
        long commentId = insertReviewComment(docMngNo, author);
        insertFile(flMpnId, KIND_REVIEW_COMMENT, Long.toString(commentId), author);

        CustomUserDetails viewer = user(unrelated, otherDept);

        mockMvc.perform(
                        get("/api/files/{flMpnId}/download", flMpnId)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.user(viewer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        get("/api/files/{flMpnId}/preview", flMpnId)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.user(viewer)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("첨부 대상 HTTP: 파일 소유자도 타인 게시물로 메타 재연결하면 403이고 원본 연결은 유지")
    void boardTarget_httpMetadataReparent_deniedWithoutMutation() throws Exception {
        String fileOwner = NS + "U" + uid;
        String boardAuthor = NS + "A" + uid;
        String boardNo = NS + "B" + uid;
        String flMpnId = NS + "FB" + uid;
        insertBoardPostOwned(
                boardNo,
                "Y",
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                boardAuthor);
        insertFile(flMpnId, KIND_REQUIREMENT, NS + "OLD" + uid, fileOwner);

        mockMvc.perform(
                        put("/api/files/{flMpnId}", flMpnId)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.user(
                                                user(fileOwner, NS + "D" + uid)))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"apgFlKdNm\":\"공통게시판\",\"apgFlLnkCtzNm\":\"" + boardNo + "\"}"))
                .andExpect(status().isForbidden());

        assertFileTarget(flMpnId, KIND_REQUIREMENT, NS + "OLD" + uid);
    }

    @Test
    @DisplayName("첨부 대상 HTTP: 파일 소유자도 타인 검토의견으로 메타 재연결하면 403이고 원본 연결은 유지")
    void reviewTarget_httpMetadataReparent_deniedWithoutMutation() throws Exception {
        String docMngNo = NS + "DOC" + uid;
        String fileOwner = NS + "U" + uid;
        String commentAuthor = NS + "A" + uid;
        String flMpnId = NS + "FR" + uid;
        insertRequirementDoc(docMngNo, NS + "D" + uid, commentAuthor);
        long commentId = insertReviewComment(docMngNo, commentAuthor);
        insertFile(flMpnId, KIND_REQUIREMENT, NS + "OLD" + uid, fileOwner);

        mockMvc.perform(
                        put("/api/files/{flMpnId}", flMpnId)
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.user(
                                                user(fileOwner, NS + "X" + uid)))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"apgFlKdNm\":\"검토의견\",\"apgFlLnkCtzNm\":\"" + commentId + "\"}"))
                .andExpect(status().isForbidden());

        assertFileTarget(flMpnId, KIND_REQUIREMENT, NS + "OLD" + uid);
    }

    // ═════════════════════════════════════════
    // 격리(read-only) — 격리 행은 일반 사용자에게 노출되지 않는다
    // ═════════════════════════════════════════

    @Test
    @DisplayName("격리 행(read-only): 활성 격리 파일은 일반 사용자에게 모두 거부된다")
    void quarantineRows_notExposedToNormalUser() {
        // 정규화 기준의 격리 형태: APG_FL_KD_NM null, APG_FL_LNK_CTZ_NM null, 또는 종류가 6종에 없는 활성 파일.
        // 운영/격리 행만 대상으로 하고(SEC05 제외), 조회만 한다(수정·삭제 금지).
        List<QuarantineRow> quarantined =
                jdbcTemplate.query(
                        "SELECT FL_MPN_ID, APG_FL_KD_NM, APG_FL_LNK_CTZ_NM FROM TPRMPP_CFILEM "
                                + "WHERE DEL_YN = 'N' AND FL_MPN_ID NOT LIKE '"
                                + NS
                                + "%' "
                                + "AND (APG_FL_KD_NM IS NULL OR APG_FL_LNK_CTZ_NM IS NULL OR APG_FL_KD_NM NOT IN "
                                + "('요구사항정의서','가이드문서','사업계획서','타당성검토표','협의회관련자료','공통게시판','검토의견'))",
                        (rs, rowNum) ->
                                new QuarantineRow(
                                        rs.getString("FL_MPN_ID"),
                                        rs.getString("APG_FL_KD_NM"),
                                        rs.getString("APG_FL_LNK_CTZ_NM")));

        // 정규화 직후 기준(50 정상 + 7 격리)으로 격리 행이 존재해야 하며, 그 어느 것도 일반 사용자에게 노출되면 안 된다.
        assertThat(quarantined).isNotEmpty();
        CustomUserDetails normal = user(NS + "U" + uid, NS + "D" + uid);
        for (QuarantineRow row : quarantined) {
            Cfilem file =
                    Cfilem.builder()
                            .flMpnId(row.flMpnId())
                            .apgFlKdNm(row.apgFlKdNm())
                            .apgFlLnkCtzNm(row.apgFlLnkCtzNm())
                            .build();
            assertThat(registry.canRead(file, normal))
                    .as("격리 행 %s 은 일반 사용자에게 거부되어야 한다", row.flMpnId())
                    .isFalse();
        }
    }

    // ─────────────────────────────────────────
    // 픽스처 빌더 / 헬퍼
    // ─────────────────────────────────────────

    /** 종류·부모만 채운 인메모리 파일 엔티티(판정은 APG_FL_KD_NM·APG_FL_LNK_CTZ_NM의 순수 함수). */
    private Cfilem fileOfKind(String apgFlKdNm, String apgFlLnkCtzNm) {
        return Cfilem.builder().flMpnId(NS + "MEM" + uid).apgFlKdNm(apgFlKdNm).apgFlLnkCtzNm(apgFlLnkCtzNm).build();
    }

    private CustomUserDetails user(String eno, String bbrC) {
        return new CustomUserDetails(eno, ATH_USER, bbrC);
    }

    private CustomUserDetails user(String eno, List<String> athIds, String bbrC) {
        return new CustomUserDetails(eno, athIds, bbrC);
    }

    private FileDto.SearchCondition condition(String apgFlKdNm, String apgFlLnkCtzNm) {
        return FileDto.SearchCondition.builder().apgFlKdNm(apgFlKdNm).apgFlLnkCtzNm(apgFlLnkCtzNm).build();
    }

    private String guid() {
        return UUID.randomUUID().toString();
    }

    /** 요구사항정의서 최신 버전(DOC_VRS_SNO=100)을 작성자·주관부서와 함께 INSERT. */
    private void insertRequirementDoc(String docMngNo, String svnDpmC, String fstEnrUsid) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_BRDOCM (DOC_MNG_NO, DOC_VRS_SNO, REQ_TTL, SVN_DPM_C, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, 100, ?, ?, ?, SYSDATE, 'N', ?, 1, ?, SYSDATE)",
                docMngNo,
                NS + " 요구사항",
                svnDpmC,
                fstEnrUsid,
                guid(),
                fstEnrUsid);
    }

    /** 검토의견(BRIVGM) — 실제 시퀀스로 부모 의견일련번호를 생성하고 작성자를 통제합니다. */
    private long insertReviewComment(String docMngNo, String fstEnrUsid) {
        Long commentId =
                jdbcTemplate.queryForObject(
                        "SELECT SQ_TPRMPP_BRIVGM_1.NEXTVAL FROM DUAL", Long.class);
        if (commentId == null) {
            throw new IllegalStateException("검토의견 시퀀스 값을 생성하지 못했습니다.");
        }
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_BRIVGM (IPM_OPNN_SNO, DOC_MNG_NO, DOC_VRS_SNO, "
                        + "IT_PTL_RPL_OPNN_TC, IVG_OPNN_CONE, FSG_YN, FST_ENR_USID, FST_ENR_DTM, "
                        + "DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, 100, 'G', ?, 'N', ?, SYSDATE, 'N', ?, 1, ?, SYSDATE)",
                commentId,
                docMngNo,
                NS + " 검토의견",
                fstEnrUsid,
                guid(),
                fstEnrUsid);
        return commentId;
    }

    /** 협의회 사업(BPROJM) — 주관부서(SVN_DPM_C)를 통제. */
    private void insertProject(String abusMngNo, int sno, String svnDpmC) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_BPROJM (ABUS_MNG_NO, SNO, ABUS_NM, ABUS_TC, SVN_DPM_C, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, ?, '0', ?, '00000000000000', SYSDATE, 'N', ?, 1, '00000000000000', SYSDATE)",
                abusMngNo,
                sno,
                NS + " 사업",
                svnDpmC,
                guid());
    }

    /** 협의회(BASCTM) — 사업(ABUS_MNG_NO, SNO)과 연결. */
    private void insertCouncil(String asctId, String abusMngNo, int sno) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_BASCTM (IT_PTL_ASCT_ID, ABUS_MNG_NO, SNO, IT_PTL_ASCT_PRG_STS_TC, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, ?, '01', '00000000000000', SYSDATE, 'N', ?, 1, '00000000000000', SYSDATE)",
                asctId,
                abusMngNo,
                sno,
                guid());
    }

    /** 협의회 위원(BCMMTM) — 복합키(협의회ID, 위원유형, 사번). */
    private void insertCommitteeMember(String asctId, String mebTc, String eno) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_BCMMTM (IT_PTL_ASCT_ID, IT_PTL_ASCT_MEB_TC, ENO, CNFM_YN, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, ?, 'Y', '00000000000000', SYSDATE, 'N', ?, 1, '00000000000000', SYSDATE)",
                asctId,
                mebTc,
                eno,
                guid());
    }

    /** 게시물(CBLBCM) — 노출여부(XPO_YN)와 공개기간(STT_DTM~END_DTM) 통제. */
    private void insertBoardPost(String nacNo, String xpoYn, LocalDate sttDt, LocalDate endDt) {
        insertBoardPostOwned(nacNo, xpoYn, sttDt, endDt, "00000000000000");
    }

    /** 게시물(CBLBCM) — 작성자를 지정해 첨부 대상 쓰기 권한을 검증합니다. */
    private void insertBoardPostOwned(
            String nacNo, String xpoYn, LocalDate sttDt, LocalDate endDt, String fstEnrUsid) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CBLBCM (NAC_NO, BLB_ID, NAC_TTL, ANC_YN, XPO_YN, NAC_INQ_NBR, "
                        + "APG_FL_NBR, FL_APG_YN, GRP_SQN_SNO, NAC_LEV_MNG_SNO, NAC_UNQ_ID, STT_DTM, END_DTM, "
                        + "FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, 'SEC05BLB', ?, 'N', ?, 0, 0, 'N', 0, 0, ?, ?, ?, "
                        + "?, SYSDATE, 'N', ?, 1, ?, SYSDATE)",
                new Object[] {
                    nacNo,
                    NS + " 게시물",
                    xpoYn,
                    nacNo,
                    sqlDate(sttDt),
                    sqlDate(endDt),
                    fstEnrUsid,
                    guid(),
                    fstEnrUsid
                },
                new int[] {
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.DATE,
                    Types.DATE,
                    Types.VARCHAR,
                    Types.VARCHAR,
                    Types.VARCHAR
                });
    }

    /** 파일 연결 대상이 거부 전 값으로 유지됐는지 확인합니다. */
    private void assertFileTarget(String flMpnId, String expectedKind, String expectedParent) {
        Map<String, Object> row =
                jdbcTemplate.queryForMap(
                        "SELECT APG_FL_KD_NM, APG_FL_LNK_CTZ_NM FROM TPRMPP_CFILEM WHERE FL_MPN_ID = ?",
                        flMpnId);
        assertThat(row.get("APG_FL_KD_NM")).isEqualTo(expectedKind);
        assertThat(row.get("APG_FL_LNK_CTZ_NM")).isEqualTo(expectedParent);
    }

    /** 파일(CFILEM) — 종류·부모·업로더를 통제. */
    private void insertFile(String flMpnId, String apgFlKdNm, String apgFlLnkCtzNm, String fstEnrUsid) {
        jdbcTemplate.update(
                "INSERT INTO TPRMPP_CFILEM (FL_MPN_ID, FL_NM, FL_PYS_NM, FL_KPN_PTH, FL_TP_CONE, "
                        + "APG_FL_KD_NM, APG_FL_LNK_CTZ_NM, FST_ENR_USID, FST_ENR_DTM, DEL_YN, GUID, GUID_PRG_SNO, "
                        + "LST_CHG_USID, LST_CHG_DTM) "
                        + "VALUES (?, ?, ?, ?, '첨부파일', ?, ?, ?, SYSDATE, 'N', ?, 1, ?, SYSDATE)",
                flMpnId,
                NS + "-" + flMpnId + ".pdf",
                NS + "_phys.pdf",
                "/data/files/sec05",
                apgFlKdNm,
                apgFlLnkCtzNm,
                fstEnrUsid,
                guid(),
                fstEnrUsid);
    }

    private Date sqlDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    /** 격리 행 조회용 경량 레코드(FL_MPN_ID·APG_FL_KD_NM·APG_FL_LNK_CTZ_NM). */
    private record QuarantineRow(String flMpnId, String apgFlKdNm, String apgFlLnkCtzNm) {}
}
