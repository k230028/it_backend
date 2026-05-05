package com.kdb.it.domain.budget.document.service;

import com.kdb.it.common.iam.repository.UserRepository;
import com.kdb.it.domain.budget.document.dto.ServiceRequestDocDto;
import com.kdb.it.domain.budget.document.entity.Brdocm;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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

    @Test
    @DisplayName("신규 문서 생성 시 버전은 0.01 이다")
    void createDocument_setsInitialVersion() {
        // Arrange
        given(repository.getNextSequenceValue()).willReturn(1L);
        given(repository.existsByDocMngNoAndDelYn(anyString(), eq("N"))).willReturn(false);
        ServiceRequestDocDto.CreateRequest req = ServiceRequestDocDto.CreateRequest.builder()
                .reqNm("테스트 문서")
                .build();
        Brdocm saved = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.01"))
                .reqNm("테스트 문서")
                .build();
        given(repository.save(any(Brdocm.class))).willReturn(saved);

        // Act
        String result = service.createDocument(req);

        // Assert
        then(repository).should().save(argThat(entity ->
                new BigDecimal("0.01").compareTo(entity.getDocVrs()) == 0
        ));
        assertThat(result).isNotBlank();
    }

    @Test
    @DisplayName("새 버전 생성 시 기존 최신 버전 + 0.01 로 생성된다")
    void createNewVersion_incrementsVersion() {
        // Arrange
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.01"))
                .reqNm("문서")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));
        given(repository.save(any(Brdocm.class))).willAnswer(inv -> inv.getArgument(0));

        // Act
        BigDecimal newVersion = service.createNewVersion("DOC-001");

        // Assert
        assertThat(newVersion).isEqualByComparingTo(new BigDecimal("0.02"));
        then(repository).should().save(argThat(entity ->
                new BigDecimal("0.02").compareTo(entity.getDocVrs()) == 0
        ));
    }

    @Test
    @DisplayName("새 버전 생성 시 문서가 없으면 예외가 발생한다")
    void createNewVersion_throwsWhenNotFound() {
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsDesc("MISSING", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.createNewVersion("MISSING"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("version 파라미터 없이 조회 시 최신 버전을 반환한다")
    void getDocument_withoutVersion_returnsLatest() {
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.03"))
                .reqNm("최신 문서")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));

        ServiceRequestDocDto.Response result = service.getDocument("DOC-001", null);

        assertThat(result.getDocVrs()).isEqualByComparingTo(new BigDecimal("0.03"));
    }

    @Test
    @DisplayName("version 파라미터 지정 시 해당 버전을 반환한다")
    void getDocument_withVersion_returnsSpecific() {
        Brdocm v1 = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.01"))
                .reqNm("v0.01 문서")
                .build();
        given(repository.findByDocMngNoAndDocVrsAndDelYn("DOC-001", new BigDecimal("0.01"), "N"))
                .willReturn(Optional.of(v1));

        ServiceRequestDocDto.Response result = service.getDocument("DOC-001", new BigDecimal("0.01"));

        assertThat(result.getDocVrs()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("version 없이 삭제 시 전체 버전이 소프트 삭제된다")
    void deleteDocument_withoutVersion_deletesAll() {
        Brdocm v1 = Brdocm.builder().docMngNo("DOC-001").docVrs(new BigDecimal("0.01")).build();
        Brdocm v2 = Brdocm.builder().docMngNo("DOC-001").docVrs(new BigDecimal("0.02")).build();
        given(repository.findAllByDocMngNoAndDelYn("DOC-001", "N")).willReturn(List.of(v1, v2));

        service.deleteDocument("DOC-001", null);

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
        // Arrange: 0.02 버전 엔티티 준비
        Brdocm v2 = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.02"))
                .build();
        given(repository.findByDocMngNoAndDocVrsAndDelYn(
                "DOC-001", new BigDecimal("0.02"), "N"))
                .willReturn(Optional.of(v2));

        // Act
        service.deleteDocument("DOC-001", new BigDecimal("0.02"));

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
        assertThatThrownBy(() -> service.deleteDocument("MISSING", null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("특정 버전 삭제 시 해당 버전이 없으면 예외가 발생한다")
    void deleteDocument_withVersion_throwsWhenNotFound() {
        // Arrange: 0.99 버전 미존재
        given(repository.findByDocMngNoAndDocVrsAndDelYn(
                "DOC-001", new BigDecimal("0.99"), "N"))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.deleteDocument("DOC-001", new BigDecimal("0.99")))
                .isInstanceOf(RuntimeException.class);
    }

    // ─────────────────────────────────────────────────────────────────
    // updateDocument
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateDocument: 미존재 문서관리번호이면 예외가 발생한다")
    void updateDocument_throwsWhenNotFound() {
        // Arrange
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsDesc("MISSING", "N"))
                .willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.updateDocument(
                "MISSING",
                ServiceRequestDocDto.UpdateRequest.builder()
                        .reqNm("수정명")
                        .build()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateDocument: 존재하는 최신 버전 문서의 요구사항명을 수정한다")
    void updateDocument_updatesLatestVersion() {
        // Arrange: 최신 버전 존재
        Brdocm latest = Brdocm.builder()
                .docMngNo("DOC-001")
                .docVrs(new BigDecimal("0.02"))
                .reqNm("기존 문서명")
                .build();
        given(repository.findTopByDocMngNoAndDelYnOrderByDocVrsDesc("DOC-001", "N"))
                .willReturn(Optional.of(latest));

        // Act
        String result = service.updateDocument(
                "DOC-001",
                ServiceRequestDocDto.UpdateRequest.builder()
                        .reqNm("수정된 문서명")
                        .build());

        // Assert: 문서관리번호 반환, JPA Dirty Checking으로 reqNm 수정 반영
        assertThat(result).isEqualTo("DOC-001");
        assertThat(latest.getReqNm()).isEqualTo("수정된 문서명");
    }

    // ─────────────────────────────────────────────────────────────────
    // getVersionHistory
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getVersionHistory: 동일 문서의 전체 버전 목록을 내림차순으로 반환한다")
    void getVersionHistory_returnsAllVersionsDescending() {
        // Arrange: 0.03, 0.02, 0.01 버전 내림차순 반환
        Brdocm v3 = Brdocm.builder()
                .docMngNo("DOC-001").docVrs(new BigDecimal("0.03")).reqNm("v3").build();
        Brdocm v2 = Brdocm.builder()
                .docMngNo("DOC-001").docVrs(new BigDecimal("0.02")).reqNm("v2").build();
        Brdocm v1 = Brdocm.builder()
                .docMngNo("DOC-001").docVrs(new BigDecimal("0.01")).reqNm("v1").build();
        given(repository.findAllByDocMngNoAndDelYnOrderByDocVrsDesc("DOC-001", "N"))
                .willReturn(List.of(v3, v2, v1));

        // Act
        List<ServiceRequestDocDto.VersionResponse> result = service.getVersionHistory("DOC-001");

        // Assert: 3개 버전, 첫 번째가 최신 버전(0.03)
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDocVrs()).isEqualByComparingTo(new BigDecimal("0.03"));
        assertThat(result.get(2).getDocVrs()).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("getVersionHistory: 이력이 없으면 빈 목록을 반환한다")
    void getVersionHistory_returnsEmptyWhenNoHistory() {
        // Arrange
        given(repository.findAllByDocMngNoAndDelYnOrderByDocVrsDesc("NONE", "N"))
                .willReturn(List.of());

        // Act
        List<ServiceRequestDocDto.VersionResponse> result = service.getVersionHistory("NONE");

        // Assert
        assertThat(result).isEmpty();
    }
}
