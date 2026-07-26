package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 검토의견 첨부 쓰기 판정기 — 활성 검토의견 작성자 또는 관리자만 허용합니다. */
@Component
@RequiredArgsConstructor
public class ReviewCommentFileWriteAuthorizer implements FileTargetWriteAuthorizer {

    public static final String REVIEW_COMMENT_KIND = "검토의견";

    private final BrivgmRepository brivgmRepository;

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of(REVIEW_COMMENT_KIND);
    }

    /**
     * 검토의견 첨부 수정·삭제 가능 여부를 판정합니다.
     *
     * @param file 대상 파일이며 부모 키는 의견일련번호여야 합니다.
     * @param user 현재 사용자
     * @return 활성 부모가 존재하고 검토의견 작성자 또는 관리자이면 {@code true}
     */
    public boolean canWrite(Cfilem file, CustomUserDetails user) {
        if (file == null) {
            return false;
        }
        return canWrite(file.getPkCone(), user);
    }

    /**
     * 검토의견 첨부 대상 쓰기 가능 여부를 판정합니다.
     *
     * @param pkCone 의견일련번호
     * @param user 현재 사용자
     * @return 활성 검토의견이 존재하고 댓글 작성자 또는 관리자이면 {@code true}
     */
    @Override
    public boolean canWrite(String pkCone, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(pkCone)) {
            return false;
        }

        final long commentId;
        try {
            commentId = Long.parseLong(pkCone);
        } catch (NumberFormatException ignored) {
            return false;
        }

        return brivgmRepository
                .findByIpmOpnnSnoAndDelYn(commentId, "N")
                .map(
                        comment ->
                                user.isAdmin()
                                        || Objects.equals(user.getEno(), comment.getFstEnrUsid()))
                .orElse(false);
    }
}
