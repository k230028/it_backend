package com.kdb.it.infra.file;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 파일 소유권 검증 컴포넌트 — SEC-02
 *
 * <p>파일매핑ID와 현재 사용자의 사번을 비교하여 본인이 업로드한 파일인지 확인합니다.
 * 타인의 파일에 대한 수정·삭제를 차단하기 위해 사용합니다.</p>
 *
 * <p>소유권 기준: {@code CFILEM.FST_ENR_USID}(최초등록자사번) = 현재 사용자 사번</p>
 *
 * <p>주식별자컬럼명(PK_COL_NM)="공통게시판"인 파일은 게시물 가시성 규칙을 추가로 적용합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileOwnershipChecker {

    private final FileRepository fileRepository;
    private final BoardPostRepository boardPostRepository;

    /**
     * 파일 소유권을 확인합니다.
     *
     * @param flMpnId 검증할 파일매핑ID
     * @param userEno 현재 로그인 사용자의 사번
     * @throws CustomGeneralException 파일이 없거나 소유권 불일치인 경우
     */
    public void checkOwnership(String flMpnId, String userEno) {
        Cfilem file = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));

        if (!file.getFstEnrUsid().equals(userEno)) {
            throw new CustomGeneralException("본인이 업로드한 파일만 삭제할 수 있습니다.");
        }
    }

    /**
     * 파일 다운로드 권한 검증 — 주식별자컬럼명(PK_COL_NM)별 분기.
     *
     * <p>읽기 가능 여부는 {@link #canRead(Cfilem, CustomUserDetails)}로 일원화하고,
     * 본 메서드는 단건 다운로드·미리보기·조회 경로에서 권한 없음 시 예외를 던집니다.</p>
     *
     * @param flMpnId 파일매핑ID
     * @param user    현재 사용자
     * @throws CustomGeneralException 파일이 없거나 접근 권한이 없는 경우
     */
    public void checkReadAccess(String flMpnId, CustomUserDetails user) {
        Cfilem file = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));

        if (!canRead(file, user)) {
            throw new CustomGeneralException("파일 다운로드 권한이 없습니다.");
        }
    }

    /**
     * 파일 읽기 가능 여부 판정 (목록 필터링·단건 권한 검증 공통 사용).
     *
     * <p>판정 규칙:</p>
     * <ul>
     *   <li>주식별자컬럼명이 "공통게시판"이 아니면 항상 읽기 허용(true) — 리포지토리 조회 없음.</li>
     *   <li>관리자({@link CustomUserDetails#isAdmin()})는 항상 읽기 허용.</li>
     *   <li>그 외에는 연결된 게시물을 조회해 공개 여부(화면여부 sreYn=Y + 공개기간)를 판정.
     *       게시물이 없으면 읽기 불가(false).</li>
     * </ul>
     *
     * @param file 대상 파일 엔티티
     * @param user 현재 사용자 (null이면 관리자 우회 없음)
     * @return 읽기 가능하면 true, 아니면 false
     */
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (!"공통게시판".equals(file.getPkColNm())) {
            return true;
        }
        if (user != null && user.isAdmin()) {
            return true;
        }

        String nacMngNo = file.getPkCone();
        var postOpt = boardPostRepository.findByNacMngNoAndDelYn(nacMngNo, "N");
        if (postOpt.isEmpty()) {
            // 파일이 존재하지 않는 게시물을 참조 — 데이터 정합성 문제이므로 경고 로깅 후 읽기 불가.
            log.warn("게시판 파일이 존재하지 않는 게시물을 참조합니다: flMpnId={}, pkCone={}",
                    file.getFlMpnId(), nacMngNo);
            return false;
        }
        return isPostVisible(postOpt.get());
    }

    /**
     * 게시물 공개 여부 판정 — 화면여부(sreYn=Y)이고 공개기간(sttDt~endDt) 내인지 확인.
     */
    private boolean isPostVisible(Cblbcm post) {
        LocalDate today = LocalDate.now();
        return "Y".equals(post.getSreYn())
                && (post.getSttDt() == null || !post.getSttDt().isAfter(today))
                && (post.getEndDt() == null || !post.getEndDt().isBefore(today));
    }
}
