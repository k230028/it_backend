package com.kdb.it.common.board.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaListRow;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardMetaServiceTest {

    @Mock private BoardMetaRepository boardMetaRepository;

    @InjectMocks private BoardMetaService service;

    @BeforeEach
    void bridgeLegacyLookupStubs() {
        lenient()
                .when(
                        boardMetaRepository.findByBlbMngNoAndUseYnAndDelYn(
                                anyString(), eq("Y"), eq("N")))
                .thenAnswer(
                        invocation ->
                                boardMetaRepository.findByBlbMngNoAndDelYn(
                                        invocation.getArgument(0), "N"));
    }

    @Test
    @DisplayName("활성 게시판 목록을 응답 DTO로 변환한다")
    void getAllActive_returnsResponses() {
        BoardMetaListRow row = metaRow("BLBM-2026-0001", "공지사항");
        given(boardMetaRepository.findAllActiveOrderedRows()).willReturn(List.of(row));

        List<BoardMetaDto.Response> result = service.getAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBlbMngNo()).isEqualTo("BLBM-2026-0001");
        assertThat(result.get(0).getBlbNm()).isEqualTo("공지사항");
    }

    @Test
    @DisplayName("게시판 단건을 조회한다")
    void getOne_existingBoard_returnsResponse() {
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(board("BLBM-2026-0001", "공지사항")));

        BoardMetaDto.Response result = service.getOne("BLBM-2026-0001");

        assertThat(result.getBlbMngNo()).isEqualTo("BLBM-2026-0001");
        assertThat(result.getBlbNm()).isEqualTo("공지사항");
    }

    @Test
    @DisplayName("없는 게시판 단건 조회는 예외를 던진다")
    void getOne_missingBoard_throws() {
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-404", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOne("BLBM-404"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("BLBM-404");
    }

    @Test
    @DisplayName("사용 중지 게시판 단건은 사용자에게 404로 숨긴다")
    void getOne_inactiveBoard_throwsNotFound() {
        given(boardMetaRepository.findByBlbMngNoAndUseYnAndDelYn("BLBM-2026-0001", "Y", "N"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOne("BLBM-2026-0001"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("게시판을 생성하면 연도 없는 관리번호를 채번하고 저장한다")
    void createBoard_savesEntityWithGeneratedId() {
        given(boardMetaRepository.getNextSequenceValue()).willReturn(7L);
        ArgumentCaptor<Cblbmm> captor = ArgumentCaptor.forClass(Cblbmm.class);
        BoardMetaDto.CreateRequest request = createRequest();

        String result = service.createBoard(request);

        assertThat(result).isEqualTo("BLBM-0007");
        verify(boardMetaRepository).save(captor.capture());
        assertThat(captor.getValue().getBlbMngNo()).isEqualTo(result);
        assertThat(captor.getValue().getBlbNm()).isEqualTo("공지사항");
    }

    @Test
    @DisplayName("FAQ·Q&A 게시판은 유형별로 하나만 활성화할 수 있다")
    void createBoard_secondSpecialBoardIsRejected() {
        given(boardMetaRepository.findAllByItPtlBlbTcAndUseYnAndDelYn("005", "Y", "N"))
                .willReturn(List.of(boardWithType("BLBM-0427", "005")));

        assertThatThrownBy(() -> service.createBoard(createRequestWithType("005")))
                .isInstanceOf(CustomGeneralException.class)
                .hasMessageContaining("고유 게시판");

        verify(boardMetaRepository, never()).save(any());
    }

    @Test
    @DisplayName("게시판 수정은 활성 엔티티에 변경 명령을 적용한다")
    void updateBoard_existingBoard_updatesEntity() {
        Cblbmm board = board("BLBM-2026-0001", "공지사항");
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(board));
        BoardMetaDto.UpdateRequest request = updateRequest("수정 게시판");

        service.updateBoard("BLBM-2026-0001", request);

        assertThat(board.getBlbNm()).isEqualTo("수정 게시판");
    }

    @Test
    @DisplayName("관리자는 사용 중지 게시판도 del-only 조회로 재활성화할 수 있다")
    void updateBoard_inactiveBoard_canReactivate() {
        Cblbmm inactive = board("BLBM-2026-0001", "중지 게시판");
        inactive.update(new Cblbmm.UpdateCommand("중지 게시판", "Y", "Y", "N", "N", 1, "N", null));
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(inactive));

        service.updateBoard("BLBM-2026-0001", updateRequest("재활성화"));

        assertThat(inactive.getUseYn()).isEqualTo("Y");
        assertThat(inactive.getBlbNm()).isEqualTo("재활성화");
    }

    @Test
    @DisplayName("게시판 삭제는 활성 엔티티를 소프트 삭제한다")
    void deleteBoard_existingBoard_softDeletes() {
        Cblbmm board = board("BLBM-2026-0001", "공지사항");
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
                .willReturn(Optional.of(board));

        service.deleteBoard("BLBM-2026-0001");

        assertThat(board.getDelYn()).isEqualTo("Y");
    }

    @Test
    @DisplayName("getAllActive: 활성 게시판이 없으면 빈 리스트를 반환한다")
    void getAllActive_empty_returnsEmpty() {
        given(boardMetaRepository.findAllActiveOrderedRows()).willReturn(List.of());

        assertThat(service.getAllActive()).isEmpty();
    }

    @Test
    @DisplayName("updateBoard: 미존재 게시판이면 예외를 던진다")
    void updateBoard_missing_throws() {
        given(boardMetaRepository.findByBlbMngNoAndDelYn("NOPE", "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateBoard("NOPE", updateRequest("X")))
                .isInstanceOf(CustomGeneralException.class);
    }

    @Test
    @DisplayName("deleteBoard: 미존재 게시판이면 예외를 던진다")
    void deleteBoard_missing_throws() {
        given(boardMetaRepository.findByBlbMngNoAndDelYn("NOPE", "N")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBoard("NOPE"))
                .isInstanceOf(CustomGeneralException.class);
    }

    private static Cblbmm board(String id, String name) {
        return boardWithType(id, "001", name);
    }

    private static Cblbmm boardWithType(String id, String type) {
        return boardWithType(id, type, "게시판");
    }

    private static Cblbmm boardWithType(String id, String type, String name) {
        return Cblbmm.builder()
                .blbMngNo(id)
                .blbNm(name)
                .itPtlBlbTc(type)
                .repUseYn("Y")
                .cmmtUseYn("Y")
                .flEsnYn("N")
                .sreSqnNo(1)
                .useYn("Y")
                .delYn("N")
                .build();
    }

    private static BoardMetaListRow metaRow(String id, String name) {
        return new BoardMetaListRow(id, name, "001", "Y", "Y", "N", "N", 1, "Y", null);
    }

    private static BoardMetaDto.CreateRequest createRequest() {
        BoardMetaDto.CreateRequest request = new BoardMetaDto.CreateRequest();
        request.setItPtlBlbTc("001");
        request.setBlbNm("공지사항");
        request.setRepUseYn("Y");
        request.setCmmtUseYn("Y");
        request.setFlEsnYn("N");
        request.setSreSqnNo(1);
        return request;
    }

    private static BoardMetaDto.CreateRequest createRequestWithType(String typeCode) {
        BoardMetaDto.CreateRequest request = createRequest();
        request.setItPtlBlbTc(typeCode);
        request.setBlbNm("Q&A");
        return request;
    }

    private static BoardMetaDto.UpdateRequest updateRequest(String name) {
        BoardMetaDto.UpdateRequest request = new BoardMetaDto.UpdateRequest();
        request.setBlbNm(name);
        request.setRepUseYn("Y");
        request.setCmmtUseYn("Y");
        request.setFlEsnYn("N");
        request.setSreSqnNo(2);
        request.setUseYn("Y");
        return request;
    }
}
