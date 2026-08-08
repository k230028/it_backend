package com.kdb.it.domain.council.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.council.dto.CouncilDto;
import com.kdb.it.domain.council.entity.Bmqnam;
import com.kdb.it.domain.council.entity.BmqnamId;
import com.kdb.it.domain.council.repository.CouncilRepository;
import com.kdb.it.domain.council.repository.MainQnaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MainQnaServiceTest {

    private static final String ASCT_ID = "ASCT-2026-0001";

    @Mock private MainQnaRepository mainQnaRepository;

    @Mock private CouncilRepository councilRepository;

    @Mock private EntityManager entityManager;

    @InjectMocks private MainQnaService service;

    @Test
    @DisplayName("getMainQnaList: 협의회가 없으면 예외를 던진다")
    void getMainQnaList_missingCouncil_throws() {
        given(councilRepository.existsById(ASCT_ID)).willReturn(false);

        assertThatThrownBy(() -> service.getMainQnaList(ASCT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 협의회");
    }

    @Test
    @DisplayName("getMainQnaList: 삭제되지 않은 본회의 QnA를 응답 DTO로 변환한다")
    void getMainQnaList_returnsResponses() {
        Bmqnam qna =
                Bmqnam.builder()
                        .qtnId("MQT-1")
                        .itPtlAsctId(ASCT_ID)
                        .qtnDwuUsid("E001")
                        .qtnCone("질의")
                        .repDwuUsid("E002")
                        .repCone("답변")
                        .qtnRpdRltYn("Y")
                        .build();
        given(councilRepository.existsById(ASCT_ID)).willReturn(true);
        given(mainQnaRepository.findByItPtlAsctIdAndDelYnOrderByFstEnrDtmAsc(ASCT_ID, "N"))
                .willReturn(List.of(qna));

        List<CouncilDto.QnaResponse> result = service.getMainQnaList(ASCT_ID);

        assertThat(result)
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.qtnId()).isEqualTo("MQT-1");
                            assertThat(item.qtnCone()).isEqualTo("질의");
                            assertThat(item.repCone()).isEqualTo("답변");
                            assertThat(item.repYn()).isEqualTo("Y");
                        });
    }

    @Test
    @DisplayName("createMainQna: 다음 순번으로 QTN_ID를 만들고 persist로 저장한다")
    void createMainQna_persists() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        // createMainQna는 existsById 대신 findByIdForUpdate(비관적 잠금)로 협의회 존재를 검증한다
        given(councilRepository.findByIdForUpdate(ASCT_ID))
                .willReturn(
                        java.util.Optional.of(
                                org.mockito.Mockito.mock(
                                        com.kdb.it.domain.council.entity.Basctm.class)));
        given(mainQnaRepository.getNextQtnSeq(ASCT_ID)).willReturn(3);

        String result =
                service.createMainQna(
                        ASCT_ID,
                        new CouncilDto.QnaCreateRequest("본회의 질의"),
                        new CustomUserDetails(
                                "E001", List.of(CustomUserDetails.ATH_ADMIN), "D001"));

        assertThat(result).isEqualTo("MQT-ASCT-2026-0001-03");
        ArgumentCaptor<Bmqnam> captor = ArgumentCaptor.forClass(Bmqnam.class);
        verify(entityManager).persist(captor.capture());
        assertThat(captor.getValue().getQtnId()).isEqualTo("MQT-ASCT-2026-0001-03");
        assertThat(captor.getValue().getQtnDwuUsid()).isEqualTo("E001");
        assertThat(captor.getValue().getQtnCone()).isEqualTo("본회의 질의");
        assertThat(captor.getValue().getQtnRpdRltYn()).isEqualTo("N");
    }

    @Test
    @DisplayName("createMainQna: 협의회가 없으면 예외를 던진다")
    void createMainQna_missingCouncil_throws() {
        // createMainQna는 findByIdForUpdate가 empty를 반환하면 IllegalArgumentException을 던진다
        given(councilRepository.findByIdForUpdate(ASCT_ID)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.createMainQna(
                                        ASCT_ID,
                                        new CouncilDto.QnaCreateRequest("본회의 질의"),
                                        new CustomUserDetails(
                                                "E001",
                                                List.of(CustomUserDetails.ATH_ADMIN),
                                                "D001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 협의회");
    }

    @Test
    @DisplayName("updateMainQna: 협의회ID가 일치하면 질의 내용을 수정한다")
    void updateMainQna_updatesQuestion() {
        Bmqnam qna = Bmqnam.builder().qtnId("MQT-1").itPtlAsctId(ASCT_ID).qtnCone("기존").build();
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-1")))
                .willReturn(Optional.of(qna));

        service.updateMainQna(ASCT_ID, "MQT-1", new CouncilDto.QnaUpdateRequest("수정"));

        assertThat(qna.getQtnCone()).isEqualTo("수정");
    }

    @Test
    @DisplayName("updateMainQna: 질의응답이 없으면 예외를 던진다")
    void updateMainQna_missingQna_throws() {
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-404")))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.updateMainQna(
                                        ASCT_ID, "MQT-404", new CouncilDto.QnaUpdateRequest("수정")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");
    }

    @Test
    @DisplayName("replyMainQna: 협의회ID가 일치하면 답변자와 답변 여부를 갱신한다")
    void replyMainQna_replies() {
        Bmqnam qna = Bmqnam.builder().qtnId("MQT-1").itPtlAsctId(ASCT_ID).qtnRpdRltYn("N").build();
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-1")))
                .willReturn(Optional.of(qna));

        service.replyMainQna(
                ASCT_ID,
                "MQT-1",
                new CouncilDto.QnaReplyRequest("답변"),
                new CustomUserDetails("E002", List.of(CustomUserDetails.ATH_ADMIN), "D001"));

        assertThat(qna.getRepDwuUsid()).isEqualTo("E002");
        assertThat(qna.getRepCone()).isEqualTo("답변");
        assertThat(qna.getQtnRpdRltYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("replyMainQna: 질의응답이 없으면 예외를 던진다")
    void replyMainQna_missingQna_throws() {
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-404")))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.replyMainQna(
                                        ASCT_ID,
                                        "MQT-404",
                                        new CouncilDto.QnaReplyRequest("답변"),
                                        new CustomUserDetails(
                                                "E002",
                                                List.of(CustomUserDetails.ATH_ADMIN),
                                                "D001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");
    }

    @Test
    @DisplayName("deleteMainQna: 협의회ID가 일치하면 Soft Delete 처리한다")
    void deleteMainQna_deletes() {
        Bmqnam qna = Bmqnam.builder().qtnId("MQT-1").itPtlAsctId(ASCT_ID).build();
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-1")))
                .willReturn(Optional.of(qna));

        service.deleteMainQna(ASCT_ID, "MQT-1");

        assertThat(qna.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("deleteMainQna: 질의응답이 없으면 예외를 던진다")
    void deleteMainQna_missingQna_throws() {
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-404")))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMainQna(ASCT_ID, "MQT-404"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");
    }

    @Test
    @DisplayName("수정: 다른 협의회의 동일 질의ID는 조회되지 않는다")
    void updateMainQna_wrongCouncil_isolatedByCompositeId() {
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-1")))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.updateMainQna(
                                        ASCT_ID, "MQT-1", new CouncilDto.QnaUpdateRequest("수정")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");
    }

    @Test
    @DisplayName("답변/삭제: 다른 협의회의 동일 질의ID는 조회되지 않는다")
    void replyAndDelete_wrongCouncil_isolatedByCompositeId() {
        given(mainQnaRepository.findById(new BmqnamId(ASCT_ID, "MQT-1")))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.replyMainQna(
                                        ASCT_ID,
                                        "MQT-1",
                                        new CouncilDto.QnaReplyRequest("답변"),
                                        new CustomUserDetails(
                                                "E002",
                                                List.of(CustomUserDetails.ATH_ADMIN),
                                                "D001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");

        assertThatThrownBy(() -> service.deleteMainQna(ASCT_ID, "MQT-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 본회의 질의응답");
    }
}
