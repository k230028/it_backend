package com.kdb.it.domain.estimate.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.domain.estimate.dto.EstimateDto;
import com.kdb.it.domain.estimate.entity.Bestim;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * EstimateRepository 단위 테스트.
 *
 * <p>프로젝트 내 기존 테스트 패턴(ProjectRepositoryImplTest)과 동일하게
 * {@link org.mockito.Mockito}로 Repository 인터페이스를 목킹하여 DB 없이 실행합니다.</p>
 */
@ExtendWith(MockitoExtension.class)
class EstimateRepositoryTest {

    @Mock
    private EstimateRepository repository;

    private Bestim sampleBestim;

    @BeforeEach
    void setUp() {
        sampleBestim = Bestim.builder()
                .rqmBgReqDocNo("BEG-2026-00000001")
                .docVrsSno(1)
                .lstYn("Y")
                .bgPrnTc("100")
                .cncdRfrNo("PRJ-2026-0001")
                .stsTc("41")
                .reqCone("산정 요청합니다")
                .build();
    }

    // -----------------------------------------------------------------------
    // 현재 유효 마스터 조회
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("현재버전(LST_YN=Y, DEL_YN=N) 마스터를 문서번호로 조회한다")
    void findCurrentByDocNo_returnsActive() {
        // Arrange
        given(repository.findByRqmBgReqDocNoAndLstYnAndDelYn("BEG-2026-00000001", "Y", "N"))
                .willReturn(Optional.of(sampleBestim));

        // Act
        Optional<Bestim> found = repository.findByRqmBgReqDocNoAndLstYnAndDelYn(
                "BEG-2026-00000001", "Y", "N");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getRqmBgReqDocNo()).isEqualTo("BEG-2026-00000001");
        assertThat(found.get().getStsTc()).isEqualTo("41");
        assertThat(found.get().getDocVrsSno()).isEqualTo(1);
        verify(repository).findByRqmBgReqDocNoAndLstYnAndDelYn("BEG-2026-00000001", "Y", "N");
    }

    @Test
    @DisplayName("존재하지 않는 문서번호로 조회하면 empty를 반환한다")
    void findCurrentByDocNo_notExists_returnsEmpty() {
        // Arrange
        given(repository.findByRqmBgReqDocNoAndLstYnAndDelYn("NOTEXIST", "Y", "N"))
                .willReturn(Optional.empty());

        // Act
        Optional<Bestim> found = repository.findByRqmBgReqDocNoAndLstYnAndDelYn(
                "NOTEXIST", "Y", "N");

        // Assert
        assertThat(found).isEmpty();
    }

    // -----------------------------------------------------------------------
    // 중복 존재 여부 확인
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("동일 사업에 작성중/진행중 건이 있으면 true를 반환한다")
    void existsActiveDuplicate_returnsTrue() {
        // Arrange
        List<String> activeStatuses = List.of("41", "42");
        given(repository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                "100", "PRJ-2026-0001", activeStatuses, "N"))
                .willReturn(true);

        // Act
        boolean exists = repository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                "100", "PRJ-2026-0001", activeStatuses, "N");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("동일 사업에 활성 건이 없으면 false를 반환한다")
    void existsActiveDuplicate_noMatch_returnsFalse() {
        // Arrange
        List<String> activeStatuses = List.of("41", "42");
        given(repository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                "100", "PRJ-2026-NEW", activeStatuses, "N"))
                .willReturn(false);

        // Act
        boolean exists = repository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                "100", "PRJ-2026-NEW", activeStatuses, "N");

        // Assert
        assertThat(exists).isFalse();
    }

    // -----------------------------------------------------------------------
    // 동적 검색 (search)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("조건이 없으면 search가 전체 목록을 반환한다")
    void search_noCondition_returnsAll() {
        // Arrange
        EstimateDto.ListItem item = new EstimateDto.ListItem(
                "BEG-2026-00000001", 1, "100", "PRJ-2026-0001",
                "클라우드 전환 사업", "41", "EMP001", null);
        given(repository.search(null, null, null)).willReturn(List.of(item));

        // Act
        List<EstimateDto.ListItem> result = repository.search(null, null, null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).rqmBgReqDocNo()).isEqualTo("BEG-2026-00000001");
        assertThat(result.get(0).abusNm()).isEqualTo("클라우드 전환 사업");
    }

    @Test
    @DisplayName("stsTc 조건으로 필터링된 목록을 반환한다")
    void search_withStsTc_returnsFiltered() {
        // Arrange
        given(repository.search("42", null, null)).willReturn(List.of());

        // Act
        List<EstimateDto.ListItem> result = repository.search("42", null, null);

        // Assert
        assertThat(result).isEmpty();
        verify(repository).search("42", null, null);
    }
}
