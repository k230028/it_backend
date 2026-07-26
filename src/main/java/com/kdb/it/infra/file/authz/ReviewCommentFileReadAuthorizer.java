package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.document.repository.BrivgmRepository;
import com.kdb.it.domain.budget.document.repository.ServiceRequestDocRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 검토의견 첨부 읽기 판정기 — 관리자 OR 검토의견 작성자 OR 해당 문서 버전 주관부서.
 *
 * <p>부모 키를 의견일련번호로 해석해 검토의견을 찾고, 검토의견이 가리키는 문서관리번호와 문서버전으로 정확한 문서 버전을 조회합니다.
 */
@Component
@RequiredArgsConstructor
public class ReviewCommentFileReadAuthorizer implements FileReadAuthorizer {

    private final BrivgmRepository brivgmRepository;
    private final ServiceRequestDocRepository serviceRequestDocRepository;

    @Override
    public Set<String> supportedPkColNms() {
        return Set.of("검토의견");
    }

    /**
     * 검토의견 첨부 읽기 가능 여부를 판정합니다.
     *
     * @param file 대상 파일이며 부모 키는 의견일련번호여야 합니다.
     * @param user 현재 사용자
     * @return 부모 키가 유효하고 관리자, 댓글 작성자 또는 정확한 문서 버전의 주관부서 사용자이면 {@code true}
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null || file == null || !StringUtils.hasText(file.getPkCone())) {
            return false;
        }

        final long commentId;
        try {
            commentId = Long.parseLong(file.getPkCone());
        } catch (NumberFormatException ignored) {
            return false;
        }

        if (user.isAdmin()) {
            return true;
        }

        return brivgmRepository
                .findByIpmOpnnSnoAndDelYn(commentId, "N")
                .map(
                        comment -> {
                            if (Objects.equals(user.getEno(), comment.getFstEnrUsid())) {
                                return true;
                            }
                            return serviceRequestDocRepository
                                    .findByDocMngNoAndDocVrsSnoAndDelYn(
                                            comment.getDocMngNo(), comment.getDocVrsSno(), "N")
                                    .map(
                                            doc ->
                                                    user.getBbrC() != null
                                                            && user.getBbrC()
                                                                    .equals(doc.getSvnDpmC()))
                                    .orElse(false);
                        })
                .orElse(false);
    }
}
