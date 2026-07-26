package com.kdb.it.infra.file.authz;

import com.kdb.it.common.board.repository.BoardPostRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 공통게시판 첨부 대상 쓰기 판정기 — 활성 게시물 작성자 또는 관리자만 허용합니다. */
@Component
@RequiredArgsConstructor
public class BoardFileTargetWriteAuthorizer implements FileTargetWriteAuthorizer {

    private static final String BOARD_KIND = "공통게시판";

    private final BoardPostRepository boardPostRepository;

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of(BOARD_KIND);
    }

    /**
     * 공통게시판 첨부 대상 쓰기 가능 여부를 판정합니다.
     *
     * @param pkCone 게시물관리번호
     * @param user 현재 사용자
     * @return 활성 게시물이 존재하고 게시물 작성자 또는 관리자이면 {@code true}
     */
    @Override
    public boolean canWrite(String pkCone, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(pkCone)) {
            return false;
        }
        return boardPostRepository
                .findByNacMngNoAndDelYn(pkCone, "N")
                .map(post -> user.isAdmin() || Objects.equals(user.getEno(), post.getFstEnrUsid()))
                .orElse(false);
    }
}
