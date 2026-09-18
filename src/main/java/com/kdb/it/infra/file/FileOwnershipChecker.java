package com.kdb.it.infra.file;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.authz.BudgetFileDeleteAuthorizer;
import com.kdb.it.infra.file.authz.FileReadAuthorizerRegistry;
import com.kdb.it.infra.file.authz.ReviewCommentFileWriteAuthorizer;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 파일 소유권·읽기 권한 검증 컴포넌트 — SEC-02, SEC-05
 *
 * <p>파일 연결 수정은 업로더 또는 관리자, 예산 첨부 삭제는 주관부서 사용자도 허용합니다. 검토의견 첨부는 활성 검토의견 작성자 또는 관리자에게 허용합니다.
 *
 * <p>파일 읽기는 파일 종류(APG_FL_KD_NM)별 authorizer로 판정합니다(미등록=관리자만). 판정은 {@link
 * FileReadAuthorizerRegistry}에 위임하며, 종류별 규칙은 각 authorizer가 소유합니다.
 */
@Component
@RequiredArgsConstructor
public class FileOwnershipChecker {

    private final FileRepository fileRepository;
    private final FileReadAuthorizerRegistry readAuthorizerRegistry;
    private final ReviewCommentFileWriteAuthorizer reviewCommentFileWriteAuthorizer;
    private final BudgetFileDeleteAuthorizer budgetFileDeleteAuthorizer;

    /**
     * 파일 연결 수정 권한을 검증합니다. 삭제는 별도 verifyDeleteAccess를 사용합니다.
     *
     * <p>검토의견 첨부는 활성 부모의 댓글 작성자 또는 관리자만 허용합니다. 다른 파일 종류는 "업로더 OR 관리자" 표준({@link
     * OwnershipVerifier})을 적용합니다.
     *
     * @param flMpnId 검증할 파일매핑ID
     * @param user 현재 인증 사용자
     * @throws CustomGeneralException 파일 미존재
     * @throws org.springframework.security.access.AccessDeniedException 해당 파일 종류의 쓰기 권한이 없는 경우
     */
    public void verifyWriteAccess(String flMpnId, CustomUserDetails user) {
        verifyWriteAccess(requireFile(flMpnId), user);
    }

    /**
     * 예산 첨부는 업로더·주관부서 사용자·관리자에게 삭제를 허용하고 다른 종류는 기존 권한을 적용합니다.
     *
     * @param flMpnId 삭제할 파일 매핑 ID
     * @param user 현재 인증 사용자
     * @throws CustomGeneralException 활성 파일이 없는 경우
     * @throws AccessDeniedException 삭제 권한이 없는 경우
     */
    public void verifyDeleteAccess(String flMpnId, CustomUserDetails user) {
        Cfilem file = requireFile(flMpnId);
        if (canDeleteForDepartment(file.getApgFlKdNm(), file.getApgFlLnkCtzNm(), user)) {
            return;
        }
        verifyWriteAccess(file, user);
    }

    /**
     * 예산 첨부의 부서 삭제 권한을 확인합니다. 일괄 삭제는 같은 부모에 대해 한 번만 호출합니다.
     *
     * @param kind 파일 종류
     * @param parentId 예산 관리번호
     * @param user 현재 인증 사용자
     * @return 활성 예산 원장의 주관부서가 일치하면 true, 다른 종류·부모 누락은 false
     */
    public boolean canDeleteForDepartment(String kind, String parentId, CustomUserDetails user) {
        return budgetFileDeleteAuthorizer.canDeleteForDepartment(kind, parentId, user);
    }

    private Cfilem requireFile(String flMpnId) {
        return fileRepository
                .findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));
    }

    private void verifyWriteAccess(Cfilem file, CustomUserDetails user) {
        if (ReviewCommentFileWriteAuthorizer.REVIEW_COMMENT_KIND.equals(file.getApgFlKdNm())) {
            if (!reviewCommentFileWriteAuthorizer.canWrite(file, user)) {
                throw new AccessDeniedException("파일 쓰기 권한이 없습니다.");
            }
            return;
        }
        OwnershipVerifier.verifyOwnerOrAdmin(file.getFstEnrUsid(), user);
    }

    /**
     * 파일 다운로드 권한 검증 — 읽기 가능 여부를 {@link #canRead(Cfilem, CustomUserDetails)}로 일원화합니다.
     *
     * <p>단건 다운로드·미리보기·조회 경로에서 파일이 없거나 읽기 권한이 없으면 예외를 던집니다.
     *
     * @param flMpnId 파일매핑ID
     * @param user 현재 사용자
     * @throws CustomGeneralException 파일이 없는 경우
     * @throws AccessDeniedException 읽기 권한이 없는 경우(403). 쓰기 거부와 동일하게 403으로 매핑합니다.
     */
    public void checkReadAccess(String flMpnId, CustomUserDetails user) {
        Cfilem file =
                fileRepository
                        .findByFlMpnIdAndDelYn(flMpnId, "N")
                        .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));

        if (!canRead(file, user)) {
            throw new AccessDeniedException("파일 읽기 권한이 없습니다.");
        }
    }

    /**
     * 파일 읽기 가능 여부 판정 (목록 필터링·단건 권한 검증 공통 사용).
     *
     * <p>파일 종류(APG_FL_KD_NM)별 authorizer로 판정합니다(미등록 종류=관리자만, default-deny). 판정 규칙은 {@link
     * FileReadAuthorizerRegistry}와 각 종류별 authorizer가 소유합니다.
     *
     * @param file 대상 파일 엔티티
     * @param user 현재 사용자 (null이면 비인증)
     * @return 읽기 가능하면 true, 아니면 false
     */
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return readAuthorizerRegistry.canRead(file, user);
    }
}
