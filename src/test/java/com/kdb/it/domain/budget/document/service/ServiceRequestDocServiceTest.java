package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.common.iam.entity.CuserI;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * ServiceRequestDocService 단위 테스트
 *
 * <p>
 * 복합키 (DOC_MNG_NO, DOC_VRS) 기반 요구사항 정의서 서비스의
 * 버전 관리 로직을 검증합니다.
 * </p>
 *
 * <p>
 * TDD Red 단계: 아직 구현되지 않은 메서드(createNewVersion, 버전 지정 조회/삭제)에
 * 대한 기대 동작을 먼저 정의합니다.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceRequestDocServiceTest {

    /** 요구사항 정의서 리포지토리 (mock) */
    @Mock
    private ServiceRequestDocRepository repository;

    /** 사용자 정보 리포지토리 (mock): 서비스 의존성 충족용 */
    @Mock
    private UserRepository cuserIRepository;

    /** 테스트 대상 서비스 */
    @InjectMocks
    private ServiceRequestDocService service;

    // ─────────────────────────────────────────────────────────────────
    // 인증 사용자 헬퍼 (소유권 검증용)
    // ─────────────────────────────────────────────────────────────────

    /** 문서 소유자 본인 (FST_ENR_USID=E0001 과 일치) */
    private static CustomUserDetails owner() {
        return new CustomUserDetails("E0001", List.of(CustomUserDetails.ATH_USER), "101");
    }

    /** 소유자가 아닌 일반 사용자 */
    private static CustomUserDetails other() {
        return new CustomUserDetails("E0002", List.of(CustomUserDetails.ATH_USER), "101");
    }

    /** 시스템관리자 (소유자가 아니어도 허용) */
    private static CustomUserDetails admin() {
        return new CustomUserDetails("E0099", List.of(CustomUserDetails.ATH_ADMIN), "101");
    }

    @Test
    @DisplayName("신규 문서 생성 시 버전은 0.01 이다")
    void createDocument_setsInitialVersion() {
        // Arrange
        given(repository.getNextSequenceValue()).willReturn(1L);
        given(repository.existsByDocMngNoAndDelYn(anyString(), eq("N"))).willReturn(false);
        ServiceRequestDocDto.CreateRequest req = ServiceRequestDocDto.CreateRequest.builder()
                .reqTtl("테스트 문서")
                .build();
        Brdocm saved = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("0.01"))
                .reqTtl("테스트 문서")
                .build();
        given(repository.save(any(Brdocm.class))).willReturn(saved);

        // Act
        String result = service.createDocument(req);

        // Assert: 화면 버전 0.01은 저장 정수 1(× 100)로 영속화된다
        then(repository).should().save(argThat(entity ->
                new BigDecimal("1").compareTo(entity.getDocVrsSno()) == 0
        ));
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("새 버전 생성 시 기존 최신 버전 + 0.01 로 생성된다")
    void createNewVersion_incrementsVersion() {
        // Arrange: 최신 버전 엔티티는 저장 정수 1(화면 0.01)을 보유
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("1"))
                .reqTtl("문서")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));
        given(repository.save(any(Brdocm.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        BigDecimal newVersion = service.createNewVersion("DOC-001", admin());

        // Assert: 응답은 화면 소수 0.02, 저장 엔티티는 정수 2(× 100)
        assertThat(newVersion).isEqualByComparingTo(new BigDecimal("0.02"));
        then(repository).should().save(argThat(entity ->
                new BigDecimal("2").compareTo(entity.getDocVrsSno()) == 0
        ));
    }

    @Test
    @DisplayName("새 버전 생성 시 문서가 없으면 예외가 발생한다")
    void createNewVersion_throwsWhenNotFound() {
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("MISSING", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createNewVersion("MISSING", admin()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("version 파라미터 없이 조회 시 최신 버전을 반환한다")
    void getDocument_withoutVersion_returnsLatest() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("3"))   // 저장 정수 3 = 화면 0.03
                .reqTtl("최신 문서")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));

        ServiceRequestDocDto.Response result = service.getDocument("DOC-001", null);

        assertThat(result.getDocVrsSno()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("version 파라미터 지정 시 해당 버전을 반환한다")
    void getDocument_withVersion_returnsSpecific() {
        Brdocm v1 = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("1"))   // 저장 정수 1 = 화면 0.01
                .reqTtl("v0.01 문서")
                .build();
        // 화면 입력 0.01 → 저장 정수 1로 변환되어 조회된다
        given(repository.findByDocMngNoAndDocVrsSnoAndDelYn("DOC-001", new BigDecimal("1"), "N"))
                .willReturn(Optional.of(v1));

        ServiceRequestDocDto.Response result = service.getDocument("DOC-001", new BigDecimal("0.01"));

        assertThat(result.getDocVrsSno()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("version 없이 삭제 시 전체 버전이 소프트 삭제된다")
    void deleteDocument_withoutVersion_deletesAll() {
        Brdocm v1 = Brdocm.builder().docMngNo("DOC-001").docVrsSno(new BigDecimal("0.01")).build();
        Brdocm v2 = Brdocm.builder().docMngNo("DOC-001").docVrsSno(new BigDecimal("0.02")).build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(v2));
        given(repository.findAllByDocMngNoAndDelYn("DOC-001", "N")).willReturn(List.of(v1, v2));

        service.deleteDocument("DOC-001", null, admin());

        // BaseEntity.delete()가 호출되어 delYn이 'Y'로 변경됨 (JPA Dirty Checking)
        assertThat(v1.getDelYn()).isEqualTo("Y");
        assertThat(v2.getDelYn()).isEqualTo("Y");
    }

    // ─────────────────────────────────────────────────────────────────
    // deleteDocument — 특정 버전 삭제 및 미존재 예외
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("특정 버전 삭제 시 해당 버전만 소프트 삭제된다")
    void deleteDocument_withVersion_deletesSpecificVersion() {
        // Arrange: 화면 0.02 버전 엔티티(저장 정수 2) 준비
        Brdocm v2 = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("2"))
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(v2));
        // 화면 입력 0.02 → 저장 정수 2로 변환되어 조회된다
        given(repository.findByDocMngNoAndDocVrsSnoAndDelYn(
                "DOC-001", new BigDecimal("2"), "N"))
                .willReturn(Optional.of(v2));

        // Act
        service.deleteDocument("DOC-001", new BigDecimal("0.02"), admin());

        // Assert: 해당 버전만 논리 삭제 (DEL_YN='Y')
        assertThat(v2.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("전체 버전 삭제 시 문서가 없으면 예외가 발생한다")
    void deleteDocument_withoutVersion_throwsWhenNotFound() {
        // Arrange: 존재하지 않는 문서관리번호
        given(repository.findAllByDocMngNoAndDelYn("MISSING", "N"))
                .willReturn(List.of());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteDocument("MISSING", null, admin()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("특정 버전 삭제 시 해당 버전이 없으면 예외가 발생한다")
    void deleteDocument_withVersion_throwsWhenNotFound() {
        // Arrange: 소유권 검증용 최신 버전은 존재하되, 화면 0.99(저장 정수 99) 버전은 미존재
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001").docVrsSno(new BigDecimal("1")).build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));
        given(repository.findByDocMngNoAndDocVrsSnoAndDelYn(
                "DOC-001", new BigDecimal("99"), "N"))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteDocument("DOC-001", new BigDecimal("0.99"), admin()))
                .isInstanceOf(RuntimeException.class);
    }

    // ─────────────────────────────────────────────────────────────────
    // 소유권 검증 (Task 5)
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: 소유자가 아닌 사용자는 AccessDeniedException")
    void update_deniedForOther() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-2026-0001")
                .docVrsSno(new BigDecimal("1"))
                .reqTtl("문서")
                .fstEnrUsid("E0001")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-2026-0001", "N"))
                .willReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.updateDocument(
                "DOC-2026-0001",
                ServiceRequestDocDto.UpdateRequest.builder().reqTtl("수정").build(),
                other()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("deleteDocument: 소유자가 아닌 사용자는 AccessDeniedException")
    void delete_deniedForOther() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-2026-0001")
                .docVrsSno(new BigDecimal("1"))
                .reqTtl("문서")
                .fstEnrUsid("E0001")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-2026-0001", "N"))
                .willReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.deleteDocument("DOC-2026-0001", null, other()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("updateDocument: 관리자는 소유자가 아니어도 허용된다")
    void update_allowedForAdmin() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-2026-0001")
                .docVrsSno(new BigDecimal("1"))
                .reqTtl("기존")
                .fstEnrUsid("E0001")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-2026-0001", "N"))
                .willReturn(Optional.of(latest));

        String result = service.updateDocument(
                "DOC-2026-0001",
                ServiceRequestDocDto.UpdateRequest.builder().reqTtl("관리자 수정").build(),
                admin());

        assertThat(result).isEqualTo("DOC-2026-0001");
        assertThat(latest.getReqTtl()).isEqualTo("관리자 수정");
    }

    // ─────────────────────────────────────────────────────────────────
    // updateDocument
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: 미존재 문서관리번호이면 예외가 발생한다")
    void updateDocument_throwsWhenNotFound() {
        // Arrange
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("MISSING", "N"))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.updateDocument(
                "MISSING",
                ServiceRequestDocDto.UpdateRequest.builder()
                        .reqTtl("수정명")
                        .build(),
                admin()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateDocument: 존재하는 최신 버전 문서의 요구사항명을 수정한다")
    void updateDocument_updatesLatestVersion() {
        // Arrange: 최신 버전 존재
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("0.02"))
                .reqTtl("기존 문서명")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));

        // Act
        String result = service.updateDocument(
                "DOC-001",
                ServiceRequestDocDto.UpdateRequest.builder()
                        .reqTtl("수정된 문서명")
                        .build(),
                admin());

        // Assert: 문서관리번호 반환, JPA Dirty Checking으로 reqNm 수정 반영
        assertThat(result).isEqualTo("DOC-001");
        assertThat(latest.getReqTtl()).isEqualTo("수정된 문서명");
    }

    // ─────────────────────────────────────────────────────────────────
    // getVersionHistory
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVersionHistory: 동일 문서의 전체 버전 목록을 내림차순으로 반환한다")
    void getVersionHistory_returnsAllVersionsDescending() {
        // Arrange: 저장 정수 3,2,1(화면 0.03,0.02,0.01) 내림차순 반환
        Brdocm v3 = Brdocm.builder()
                .docMngNo("DOC-001").docVrsSno(new BigDecimal("3")).reqTtl("v3").build();
        Brdocm v2 = Brdocm.builder()
                .docMngNo("DOC-001").docVrsSno(new BigDecimal("2")).reqTtl("v2").build();
        Brdocm v1 = Brdocm.builder()
                .docMngNo("DOC-001").docVrsSno(new BigDecimal("1")).reqTtl("v1").build();
        given(repository.findAllByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(List.of(v3, v2, v1));

        // Act
        List<ServiceRequestDocDto.VersionResponse> result = service.getVersionHistory("DOC-001");

        // Assert: 3개 버전, 첫 번째가 최신 버전(0.03)
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDocVrsSno()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(result.get(2).getDocVrsSno()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("getVersionHistory: 이력이 없으면 빈 목록을 반환한다")
    void getVersionHistory_returnsEmptyWhenNoHistory() {
        // Arrange
        given(repository.findAllByDocMngNoAndDelYnOrderByDocVrsSnoDesc("NONE", "N"))
                .willReturn(List.of());

        // Act
        List<ServiceRequestDocDto.VersionResponse> result = service.getVersionHistory("NONE");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getDocumentList: 작성자명은 findByEnoIn 1회로 배치 조회하고 findById는 호출하지 않는다")
    void getDocumentList_batchesAuthorNames() {
        Brdocm d1 = Brdocm.builder()
                .docMngNo("DOC-1").docVrsSno(new BigDecimal("0.01")).reqTtl("문서1").fstEnrUsid("E001").build();
        Brdocm d2 = Brdocm.builder()
                .docMngNo("DOC-2").docVrsSno(new BigDecimal("0.01")).reqTtl("문서2").fstEnrUsid("E002").build();
        Brdocm d3 = Brdocm.builder()
                .docMngNo("DOC-3").docVrsSno(new BigDecimal("0.01")).reqTtl("문서3").fstEnrUsid("E001").build();
        CuserI u1 = CuserI.builder().eno("E001").usrNm("홍길동").build();
        CuserI u2 = CuserI.builder().eno("E002").usrNm("김철수").build();
        given(repository.findLatestVersionsAll()).willReturn(List.of(d1, d2, d3));
        given(cuserIRepository.findByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any()))
                .willReturn(List.of(u1, u2));

        List<ServiceRequestDocDto.Response> result = service.getDocumentList();

        // 작성자명이 배치 결과로 매핑되고, 동일 사번(E001)이 여러 행에서 재사용된다
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFstEnrUsNm()).isEqualTo("홍길동");
        assertThat(result.get(1).getFstEnrUsNm()).isEqualTo("김철수");
        assertThat(result.get(2).getFstEnrUsNm()).isEqualTo("홍길동");
        then(cuserIRepository).should(times(1))
                .findByEnoIn(ArgumentMatchers.<java.util.Collection<String>>any());
        then(cuserIRepository).should(never()).findById(anyString());
    }

    @Test
    @DisplayName("문서 목록 조회 시 최초생성자 사번이 없으면 사용자 조회를 하지 않는다")
    void getDocumentList_skipsUserLookupWhenCreatorEmpty() {
        Brdocm document = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("0.01"))
                .reqTtl("문서")
                .fstEnrUsid("")
                .build();
        given(repository.findLatestVersionsAll()).willReturn(List.of(document));

        List<ServiceRequestDocDto.Response> result = service.getDocumentList();

        assertThat(result).hasSize(1);
        then(cuserIRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("직접 입력한 문서관리번호가 중복이면 생성 예외가 발생한다")
    void createDocument_throwsWhenManualDocumentNumberDuplicated() {
        ServiceRequestDocDto.CreateRequest req = ServiceRequestDocDto.CreateRequest.builder()
                .docMngNo("DOC-MANUAL")
                .reqTtl("중복 문서")
                .build();
        given(repository.existsByDocMngNoAndDelYn("DOC-MANUAL", "N")).willReturn(true);

        assertThatThrownBy(() -> service.createDocument(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이미 존재");
    }

    @Test
    @DisplayName("직접 입력한 문서관리번호가 중복이 아니면 저장하고 HTML을 정제한다")
    void createDocument_savesManualDocumentNumberAndSanitizesContent() {
        ServiceRequestDocDto.CreateRequest req = ServiceRequestDocDto.CreateRequest.builder()
                .docMngNo("DOC-MANUAL")
                .reqTtl("직접 문서")
                .redtConeInf("<p>본문</p><script>alert(1)</script>")
                .build();
        given(repository.existsByDocMngNoAndDelYn("DOC-MANUAL", "N")).willReturn(false);
        given(repository.save(any(Brdocm.class))).willAnswer(invocation -> invocation.getArgument(0));

        String result = service.createDocument(req);

        assertThat(result).isEqualTo("DOC-MANUAL");
        then(repository).should().save(argThat(entity ->
                entity.getDocMngNo().equals("DOC-MANUAL")
                        && entity.getRedtConeInf().contains("본문")
                        && !entity.getRedtConeInf().contains("script")
        ));
    }

    @Test
    @DisplayName("최신 문서 조회 시 없으면 예외가 발생한다")
    void getDocument_withoutVersionThrowsWhenMissing() {
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("MISSING", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocument("MISSING", null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("존재하지 않는 문서관리번호");
    }

    @Test
    @DisplayName("특정 버전 문서 조회 시 없으면 예외가 발생한다")
    void getDocument_withVersionThrowsWhenMissing() {
        // 화면 입력 0.99 → 저장 정수 99로 변환되어 조회된다
        given(repository.findByDocMngNoAndDocVrsSnoAndDelYn("DOC-001", new BigDecimal("99"), "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDocument("DOC-001", new BigDecimal("0.99")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("해당 버전");
    }

    @Test
    @DisplayName("문서 수정 시 내용이 null이면 본문 바이트도 null로 갱신한다")
    void updateDocument_setsContentNullWhenRequestContentNull() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrsSno(new BigDecimal("0.02"))
                .reqTtl("기존")
                .redtConeInf("기존")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsSnoDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));

        service.updateDocument("DOC-001", ServiceRequestDocDto.UpdateRequest.builder()
                .reqTtl("수정")
                .redtConeInf(null)
                .reqDttNo("REQ")
                .bzDttNm("BZ")
                .rvwFsgTlmDt(LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE))
                .build(),
                admin());

        assertThat(latest.getReqTtl()).isEqualTo("수정");
        assertThat(latest.getRedtConeInf()).isNull();
        assertThat(latest.getReqDttNo()).isEqualTo("REQ");
    }

    @Test
    @DisplayName("대시보드는 Timestamp, Date, null 완료기한을 상태로 변환한다")
    void getDashboard_convertsRecentReviewingDateTypes() {
        given(repository.countTotalByBbrC("101")).willReturn(5);
        given(repository.countReviewingByBbrC("101")).willReturn(3);
        given(repository.countCompletedByBbrC("101")).willReturn(1);
        given(repository.countOverdueByBbrC("101")).willReturn(1);
        given(repository.findMonthlyTrendByBbrC("101"))
                .willReturn(java.util.Collections.singletonList(new Object[]{"2026-05", 2}));
        given(repository.findRecentReviewingByBbrC("101")).willReturn(List.of(
                new Object[]{"DOC-OLD", "지연", "홍길동", "2026-05-01", Timestamp.valueOf(LocalDate.now().minusDays(1).atStartOfDay())},
                new Object[]{"DOC-TODAY", "검토", "김길동", "2026-05-02", Date.valueOf(LocalDate.now().plusDays(1))},
                new Object[]{"DOC-NULL", "미정", "이길동", "2026-05-03", null}
        ));

        ServiceRequestDocDto.DashboardResponse result = service.getDashboard("101");

        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getMonthlyTrend()).extracting(ServiceRequestDocDto.MonthlyCount::getCount)
                .containsExactly(2);
        assertThat(result.getRecentReviewing()).extracting(ServiceRequestDocDto.ReviewingItem::getStatus)
                .containsExactly("delayed", "reviewing", "reviewing");
    }

    @Test
    @DisplayName("배지 건수는 검토 진행 중 건수를 반환한다")
    void getBadgeCount_returnsReviewingCount() {
        given(repository.countReviewingByBbrC("101")).willReturn(7);

        ServiceRequestDocDto.BadgeCountResponse result = service.getBadgeCount("101");

        assertThat(result.getReviewingCount()).isEqualTo(7);
    }
}
