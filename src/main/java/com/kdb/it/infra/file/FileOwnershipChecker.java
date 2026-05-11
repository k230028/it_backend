package com.kdb.it.infra.file;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.entity.Cblbmm;
import com.kdb.it.common.board.repository.BoardMetaRepository;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 파일 소유권 검증 컴포넌트 — SEC-02
 *
 * <p>파일 관리번호와 현재 사용자의 사번을 비교하여 본인이 업로드한 파일인지 확인합니다.
 * 타인의 파일에 대한 수정·삭제를 차단하기 위해 사용합니다.</p>
 *
 * <p>소유권 기준: {@code CFILEM.FST_ENR_USID}(최초등록자사번) = 현재 사용자 사번</p>
 *
 * <p>ORC_DTT="공통게시판"인 파일은 게시물 가시성 규칙을 추가로 적용합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class FileOwnershipChecker {

    private final FileRepository fileRepository;
    private final BoardMetaRepository boardMetaRepository;
    private final BoardPostRepository boardPostRepository;

    /**
     * 파일 소유권을 확인합니다.
     *
     * <p>파일이 존재하지 않거나, 업로드자와 현재 사용자가 다르면 예외를 발생시킵니다.</p>
     *
     * @param flMngNo 검증할 파일 관리번호
     * @param userEno 현재 로그인 사용자의 사번
     * @throws CustomGeneralException 파일이 없거나 소유권 불일치인 경우
     */
    public void checkOwnership(String flMngNo, String userEno) {
        Cfilem file = fileRepository.findByFlMngNoAndDelYn(flMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMngNo));

        if (!file.getFstEnrUsid().equals(userEno)) {
            throw new CustomGeneralException("본인이 업로드한 파일만 삭제할 수 있습니다.");
        }
    }

    /**
     * 파일 다운로드 권한 검증 — ORC_DTT별 분기
     *
     * <p>ORC_DTT="공통게시판"인 파일은 게시물 가시성 규칙을 적용한다.</p>
     *
     * @param flMngNo 파일관리번호
     * @param user    현재 사용자
     * @throws CustomGeneralException 파일이 없거나 접근 권한이 없는 경우
     */
    public void checkReadAccess(String flMngNo, CustomUserDetails user) {
        Cfilem file = fileRepository.findByFlMngNoAndDelYn(flMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMngNo));

        if ("공통게시판".equals(file.getOrcDtt())) {
            verifyBoardFileAccess(file, user);
        }
        // 다른 ORC_DTT는 별도 정책 없으면 읽기 허용
    }

    /**
     * 공통게시판 파일 접근 권한 검증
     *
     * <p>게시판 조회권한 및 게시물 공개 여부(화면여부·공개기간)를 확인합니다.
     * 관리자는 모든 파일에 접근 가능합니다.</p>
     *
     * @param file 검증 대상 파일 엔티티
     * @param user 현재 사용자
     * @throws CustomGeneralException 게시물·게시판을 찾을 수 없거나 권한이 없는 경우
     */
    private void verifyBoardFileAccess(Cfilem file, CustomUserDetails user) {
        if (user.isAdmin()) return;

        String nacMngNo = file.getOrcPkVl();
        Cblbcm post = boardPostRepository.findByNacMngNoAndDelYn(nacMngNo, "N")
                .orElseThrow(() -> new CustomGeneralException("첨부파일의 게시물을 찾을 수 없습니다."));

        Cblbmm board = boardMetaRepository.findByBlbMngNoAndDelYn(post.getBlbMngNo(), "N")
                .orElseThrow(() -> new CustomGeneralException("첨부파일의 게시판을 찾을 수 없습니다."));

        boolean boardOk = "ALL".equals(board.getInqAthC())
                || user.getAuthorities().stream()
                       .anyMatch(a -> a.getAuthority().equals(board.getInqAthC()));
        if (!boardOk) {
            throw new CustomGeneralException("파일 다운로드 권한이 없습니다.");
        }

        LocalDate today = LocalDate.now();
        boolean postOk = "Y".equals(post.getSreYn())
                && (post.getSttYmd() == null || !post.getSttYmd().isAfter(today))
                && (post.getEndYmd() == null || !post.getEndYmd().isBefore(today));
        if (!postOk) {
            throw new CustomGeneralException("파일 다운로드 권한이 없습니다.");
        }
    }
}
