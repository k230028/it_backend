package com.kdb.it.common.board.service;

import com.kdb.it.common.board.dto.BoardMetaDto;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.exception.CustomGeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoardMetaServiceTest {

    @Mock
    private BoardMetaRepository boardMetaRepository;

    @InjectMocks
    private BoardMetaService service;

    @Test
    @DisplayName("활성 게시판 목록을 응답 DTO로 변환한다")
    void getAllActive_returnsResponses() {
        Cblbmm board = board("BLBM-2026-0001", "공지사항");
        given(boardMetaRepository.findAllActiveOrdered()).willReturn(List.of(board));

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
            .isInstanceOf(CustomGeneralException.class)
            .hasMessageContaining("BLBM-404");
    }

    @Test
    @DisplayName("게시판을 생성하면 연도 기반 관리번호를 채번하고 저장한다")
    void createBoard_savesEntityWithGeneratedId() {
        given(boardMetaRepository.getNextSequenceValue()).willReturn(7L);
        ArgumentCaptor<Cblbmm> captor = ArgumentCaptor.forClass(Cblbmm.class);
        BoardMetaDto.CreateRequest request = createRequest();

        String result = service.createBoard(request);

        assertThat(result).isEqualTo("BLBM-" + LocalDate.now().getYear() + "-0007");
        verify(boardMetaRepository).save(captor.capture());
        assertThat(captor.getValue().getBlbMngNo()).isEqualTo(result);
        assertThat(captor.getValue().getBlbNm()).isEqualTo("공지사항");
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
    @DisplayName("게시판 삭제는 활성 엔티티를 소프트 삭제한다")
    void deleteBoard_existingBoard_softDeletes() {
        Cblbmm board = board("BLBM-2026-0001", "공지사항");
        given(boardMetaRepository.findByBlbMngNoAndDelYn("BLBM-2026-0001", "N"))
            .willReturn(Optional.of(board));

        service.deleteBoard("BLBM-2026-0001");

        assertThat(board.getDelYn()).isEqualTo("Y");
    }

    private static Cblbmm board(String id, String name) {
        return Cblbmm.builder()
            .blbMngNo(id)
            .blbNm(name)
            .blbTp("NOTICE")
            .inqAthC("ALL")
            .enrAthC("ALL")
            .repUseYn("Y")
            .cmmtUseYn("Y")
            .flEsnYn("N")
            .hrkFxnUseYn("Y")
            .bbrLmtnUseYn("N")
            .sreSqnNo(1)
            .useYn("Y")
            .delYn("N")
            .build();
    }

    private static BoardMetaDto.CreateRequest createRequest() {
        BoardMetaDto.CreateRequest request = new BoardMetaDto.CreateRequest();
        request.setBlbTp("NOTICE");
        request.setBlbNm("공지사항");
        request.setInqAthC("ALL");
        request.setEnrAthC("ALL");
        request.setRepUseYn("Y");
        request.setCmmtUseYn("Y");
        request.setFlEsnYn("N");
        request.setHrkFxnUseYn("Y");
        request.setBbrLmtnUseYn("N");
        request.setSreSqnNo(1);
        return request;
    }

    private static BoardMetaDto.UpdateRequest updateRequest(String name) {
        BoardMetaDto.UpdateRequest request = new BoardMetaDto.UpdateRequest();
        request.setBlbNm(name);
        request.setInqAthC("ALL");
        request.setEnrAthC("ALL");
        request.setRepUseYn("Y");
        request.setCmmtUseYn("Y");
        request.setFlEsnYn("N");
        request.setHrkFxnUseYn("Y");
        request.setBbrLmtnUseYn("N");
        request.setSreSqnNo(2);
        request.setUseYn("Y");
        return request;
    }
}
