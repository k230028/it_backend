package com.kdb.it.common.board.service;

import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 게시판 유형 코드로 활성 게시판을 단일 조회하는 공통 resolver입니다. */
@Service
@RequiredArgsConstructor
public class BoardTypeResolver {

    public static final String FAQ_BOARD_TYPE = "004";
    public static final String QNA_BOARD_TYPE = "005";

    private final BoardMetaRepository boardMetaRepository;

    /**
     * 사용 중이고 삭제되지 않은 게시판 중 유형 코드에 해당하는 단일 게시판을 반환합니다.
     *
     * @param typeCode 게시판 유형 코드
     * @return 유일한 활성 게시판
     * @throws NotFoundException 활성 게시판이 없는 경우
     * @throws CustomGeneralException 활성 게시판이 둘 이상인 경우
     */
    public Cblbmm requireUniqueActiveBoard(String typeCode) {
        List<Cblbmm> boards =
                boardMetaRepository.findAllByItPtlBlbTcAndUseYnAndDelYn(typeCode, "Y", "N");
        if (boards.isEmpty()) {
            throw new NotFoundException("고유 게시판을 찾을 수 없습니다: " + typeCode);
        }
        if (boards.size() > 1) {
            throw new CustomGeneralException("고유 게시판은 활성 상태로 하나만 지정해야 합니다: " + typeCode);
        }
        return boards.getFirst();
    }
}
