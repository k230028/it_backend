package com.kdb.it.infra.file.authz;

import com.kdb.it.common.board.entity.Cblbcm;
import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.infra.file.entity.Cfilem;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공통게시판 첨부 읽기 판정기 — 관리자 OR 게시물 공개(화면표시 + 공개기간).
 *
 * <p>기존 {@code FileOwnershipChecker}의 게시물 가시성 규칙을 이관한 것이다.</p>
 */
@Component
@RequiredArgsConstructor
public class BoardFileReadAuthorizer implements FileReadAuthorizer {

    private final BoardPostRepository boardPostRepository;

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of("공통게시판");
    }

    /**
     * 공통게시판 파일 읽기 가능 여부.
     *
     * @param file 대상 파일(부모 게시물번호는 {@code PK_CONE} 값)
     * @param user 현재 사용자(null이면 비인증 → 불가)
     * @return 관리자이거나 연결 게시물이 공개(화면표시 + 공개기간 내)이면 true
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (!org.springframework.util.StringUtils.hasText(file.getPkCone())) {
            return false;
        }
        return boardPostRepository.findByNacMngNoAndDelYn(file.getPkCone(), "N")
                .map(this::isPostVisible)
                .orElse(false);
    }

    /** 게시물 공개 여부 — 화면표시(sreYn=Y)이고 공개기간(sttDt~endDt) 내. */
    private boolean isPostVisible(Cblbcm post) {
        LocalDate today = LocalDate.now();
        return "Y".equals(post.getSreYn())
                && (post.getSttDt() == null || !post.getSttDt().isAfter(today))
                && (post.getEndDt() == null || !post.getEndDt().isBefore(today));
    }
}
