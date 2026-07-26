package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 검토의견 첨부 쓰기 판정기 — 활성 검토의견 작성자 또는 관리자만 허용합니다. */
@Component
@RequiredArgsConstructor
public class ReviewCommentFileWriteAuthorizer {

    public static final String REVIEW_COMMENT_KIND = "검토의견";

    private final BrivgmRepository brivgmRepository;

    /**
     * 검토의견 첨부 수정·삭제 가능 여부를 판정합니다.
     *
     * @param file 대상 파일이며 부모 키는 의견일련번호여야 합니다.
     * @param user 현재 사용자
     * @return 활성 부모가 존재하고 검토의견 작성자 또는 관리자이면 {@code true}
     */
    public boolean canWrite(Cfilem file, CustomUserDetails user) {
        if (user == null || file == null || !StringUtils.hasText(file.getPkCone())) {
            return false;
        }

        final long commentId;
        try {
            commentId = Long.parseLong(file.getPkCone());
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
