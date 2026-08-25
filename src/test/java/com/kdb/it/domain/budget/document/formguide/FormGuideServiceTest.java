package com.kdb.it.domain.budget.document.formguide;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.kdb.it.domain.budget.document.entity.Bgdocm;
import com.kdb.it.domain.budget.document.repository.GuideDocRepository;
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 사업 입력 길라잡이 서비스의 조회·저장·삭제 계약을 검증합니다. */
@ExtendWith(MockitoExtension.class)
class FormGuideServiceTest {

    private static final String GUIDE_ID = "info.basic.abusNm";

    @Mock private GuideDocRepository guideDocRepository;

    @InjectMocks private FormGuideService formGuideService;

    @Test
    @DisplayName("공개 조회는 scope의 등록된 활성 길라잡이만 사용자 응답으로 변환한다")
    void getPublished_활성길라잡이_사용자응답반환() {
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo("FDOC-2026-0001")
                        .docTtlCone(GUIDE_ID)
                        .nacTxtInf("<p>사업명 안내</p>")
                        .build();
        given(guideDocRepository.findActiveFormGuides("FDOC-", "info."))
                .willReturn(List.of(document));

        List<FormGuideDto.PublicResponse> result =
                formGuideService.getPublished(FormGuideScope.INFO);

        assertThat(result)
                .containsExactly(new FormGuideDto.PublicResponse(GUIDE_ID, "사업명", "<p>사업명 안내</p>"));
    }

    @Test
    @DisplayName("공개 조회는 저장소 계약을 우회한 null 또는 공백 본문을 방어적으로 제외한다")
    void getPublished_null또는공백본문_제외() {
        Bgdocm nullContent =
                Bgdocm.builder()
                        .docMngNo("FDOC-2026-0001")
                        .docTtlCone(GUIDE_ID)
                        .nacTxtInf(null)
                        .build();
        Bgdocm blankContent =
                Bgdocm.builder()
                        .docMngNo("FDOC-2026-0002")
                        .docTtlCone("info.basic.bgYy")
                        .nacTxtInf(" \t\n")
                        .build();
        given(guideDocRepository.findActiveFormGuides("FDOC-", "info."))
                .willReturn(List.of(nullContent, blankContent));

        List<FormGuideDto.PublicResponse> result =
                formGuideService.getPublished(FormGuideScope.INFO);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("공개 조회는 구조만 있는 빈 HTML을 제외하고 미디어와 표는 보존한다")
    void getPublished_의미없는HTML제외_미디어표보존() {
        List<Bgdocm> documents =
                List.of(
                        guide("FDOC-2026-0001", GUIDE_ID, "<p><br></p>"),
                        guide("FDOC-2026-0002", "info.basic.bgYy", "<p>\u200B</p>"),
                        guide("FDOC-2026-0003", "info.overview.prjDes", "<p><img src=\"x\"></p>"),
                        guide(
                                "FDOC-2026-0004",
                                "info.scope.prjRng",
                                "<table><tbody></tbody></table>"));
        given(guideDocRepository.findActiveFormGuides("FDOC-", "info.")).willReturn(documents);

        List<FormGuideDto.PublicResponse> result =
                formGuideService.getPublished(FormGuideScope.INFO);

        assertThat(result)
                .extracting(FormGuideDto.PublicResponse::guideId)
                .containsExactlyInAnyOrder("info.overview.prjDes", "info.scope.prjRng");
    }

    @Test
    @DisplayName("관리 카탈로그 조회는 미등록 항목도 현재 문서번호 없이 반환한다")
    void getCatalog_미등록항목_전체카탈로그반환() {
        given(guideDocRepository.findActiveFormGuides("FDOC-", "info.")).willReturn(List.of());

        List<FormGuideDto.CatalogResponse> result =
                formGuideService.getCatalog(FormGuideScope.INFO);

        assertThat(result)
                .anySatisfy(
                        guide -> {
                            assertThat(guide.guideId()).isEqualTo(GUIDE_ID);
                            assertThat(guide.fieldLabel()).isEqualTo("사업명");
                            assertThat(guide.docMngNo()).isNull();
                            assertThat(guide.contentHtml()).isNull();
                        });
    }

    @Test
    @DisplayName("관리 카탈로그는 의미 없는 활성 문서를 미등록 상태로 반환한다")
    void getCatalog_의미없는HTML_미등록상태() {
        given(guideDocRepository.findActiveFormGuides("FDOC-", "info."))
                .willReturn(List.of(guide("FDOC-2026-0001", GUIDE_ID, "<p><br></p>")));

        FormGuideDto.CatalogResponse result =
                formGuideService.getCatalog(FormGuideScope.INFO).stream()
                        .filter(item -> GUIDE_ID.equals(item.guideId()))
                        .findFirst()
                        .orElseThrow();

        assertThat(result.docMngNo()).isNull();
        assertThat(result.contentHtml()).isNull();
    }

    @Test
    @DisplayName("신규 저장은 HTML을 정화하고 FDOC 관리번호를 발급한다")
    void save_신규등록_HTML정화후저장() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                GUIDE_ID, "FDOC-", "N"))
                .willReturn(Optional.empty());
        given(guideDocRepository.getNextSequenceValue()).willReturn(12L);

        String guideDocNo =
                formGuideService.save(
                        GUIDE_ID, new FormGuideDto.SaveRequest("<script>x</script><p>안내</p>"));

        ArgumentCaptor<Bgdocm> document = ArgumentCaptor.forClass(Bgdocm.class);
        verify(guideDocRepository).save(document.capture());
        assertThat(guideDocNo).matches("FDOC-\\d{4}-0012");
        assertThat(document.getValue().getDocMngNo()).isEqualTo(guideDocNo);
        assertThat(document.getValue().getDocTtlCone()).isEqualTo(GUIDE_ID);
        assertThat(document.getValue().getNacTxtInf()).isEqualTo("<p>안내</p>");
    }

    @Test
    @DisplayName("기존 등록 저장은 같은 문서를 정화한 본문으로 수정한다")
    void save_기존등록_본문수정() {
        Bgdocm document =
                Bgdocm.builder()
                        .docMngNo("FDOC-2026-0001")
                        .docTtlCone(GUIDE_ID)
                        .nacTxtInf("<p>이전</p>")
                        .build();
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                GUIDE_ID, "FDOC-", "N"))
                .willReturn(Optional.of(document));

        String guideDocNo =
                formGuideService.save(GUIDE_ID, new FormGuideDto.SaveRequest("<p>수정</p>"));

        assertThat(guideDocNo).isEqualTo("FDOC-2026-0001");
        assertThat(document.getNacTxtInf()).isEqualTo("<p>수정</p>");
    }

    @Test
    @DisplayName("카탈로그 밖 ID 저장은 400 대상의 잘못된 인자 예외를 던진다")
    void save_지원하지않는ID_잘못된인자예외() {
        assertThatThrownBy(
                        () ->
                                formGuideService.save(
                                        "info.unknown", new FormGuideDto.SaveRequest("<p>안내</p>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("지원하지 않는");
    }

    @Test
    @DisplayName("정화 결과가 빈 HTML인 저장 요청은 400 대상의 잘못된 인자 예외를 던진다")
    void save_정화후빈본문_잘못된인자예외() {
        assertThatThrownBy(
                        () ->
                                formGuideService.save(
                                        GUIDE_ID,
                                        new FormGuideDto.SaveRequest("<script>안전하지 않음</script>")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("본문");

        verifyNoInteractions(guideDocRepository);
    }

    @Test
    @DisplayName("구조만 있거나 제로폭 문자뿐인 본문은 저장하지 않는다")
    void save_의미없는HTML_잘못된인자예외() {
        for (String content : List.of("<p></p>", "<p><br></p>", "<p>\u200B\uFEFF</p>")) {
            assertThatThrownBy(
                            () ->
                                    formGuideService.save(
                                            GUIDE_ID, new FormGuideDto.SaveRequest(content)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본문");
        }
        verifyNoInteractions(guideDocRepository);
    }

    private static Bgdocm guide(String docMngNo, String guideId, String contentHtml) {
        return Bgdocm.builder()
                .docMngNo(docMngNo)
                .docTtlCone(guideId)
                .nacTxtInf(contentHtml)
                .build();
    }

    @Test
    @DisplayName("신규 등록의 데이터 제약 위반은 409 충돌로 변환한다")
    void save_동시중복_충돌예외() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                GUIDE_ID, "FDOC-", "N"))
                .willReturn(Optional.empty());
        given(guideDocRepository.getNextSequenceValue()).willReturn(13L);
        given(guideDocRepository.save(any(Bgdocm.class)))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(
                        () ->
                                formGuideService.save(
                                        GUIDE_ID, new FormGuideDto.SaveRequest("<p>안내</p>")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("신규 등록의 flush 시점 제약 위반도 409 충돌로 변환한다")
    void save_flush시점동시중복_충돌예외() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                GUIDE_ID, "FDOC-", "N"))
                .willReturn(Optional.empty());
        given(guideDocRepository.getNextSequenceValue()).willReturn(14L);
        doThrow(new DataIntegrityViolationException("duplicate")).when(guideDocRepository).flush();

        assertThatThrownBy(
                        () ->
                                formGuideService.save(
                                        GUIDE_ID, new FormGuideDto.SaveRequest("<p>안내</p>")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        exception ->
                                assertThat(((ResponseStatusException) exception).getStatusCode())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("미등록 길라잡이 삭제는 404 대상의 미존재 예외를 던진다")
    void delete_미등록_미존재예외() {
        given(
                        guideDocRepository.findByDocTtlConeAndDocMngNoStartingWithAndDelYn(
                                GUIDE_ID, "FDOC-", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> formGuideService.delete(GUIDE_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(GUIDE_ID);
    }
}
