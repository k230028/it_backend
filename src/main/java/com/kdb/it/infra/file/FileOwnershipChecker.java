package com.kdb.it.infra.file;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.authz.FileReadAuthorizerRegistry;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * 파일 소유권·읽기 권한 검증 컴포넌트 — SEC-02, SEC-05
 *
 * <p>파일 쓰기(수정·삭제)는 업로더 본인 또는 관리자만 허용합니다.
 * 소유권 기준: {@code CFILEM.FST_ENR_USID}(최초등록자사번) = 현재 사용자 사번.</p>
 *
 * <p>파일 읽기는 파일 종류(PK_COL_NM)별 authorizer로 판정합니다(미등록=관리자만).
 * 판정은 {@link FileReadAuthorizerRegistry}에 위임하며, 종류별 규칙은 각 authorizer가 소유합니다.</p>
 */
@Component
@RequiredArgsConstructor
public class FileOwnershipChecker {

    private final FileRepository fileRepository;
    private final FileReadAuthorizerRegistry readAuthorizerRegistry;

    /**
     * 파일 쓰기(수정/삭제) 권한 검증 — 본인 또는 관리자만 허용(403).
     *
     * <p>나머지 쓰기 경로의 "owner OR admin" 표준({@link OwnershipVerifier})과 일관되게,
     * 소유자 본인 또는 시스템관리자만 파일 메타 수정·삭제를 허용합니다.</p>
     *
     * @param flMpnId 검증할 파일매핑ID
     * @param user    현재 인증 사용자
     * @throws CustomGeneralException 파일 미존재
     * @throws org.springframework.security.access.AccessDeniedException 본인도 관리자도 아닌 경우
     */
    public void verifyWriteAccess(String flMpnId, CustomUserDetails user) {
        Cfilem file = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));
        OwnershipVerifier.verifyOwnerOrAdmin(file.getFstEnrUsid(), user);
    }

    /**
     * 파일 다운로드 권한 검증 — 읽기 가능 여부를 {@link #canRead(Cfilem, CustomUserDetails)}로 일원화합니다.
     *
     * <p>단건 다운로드·미리보기·조회 경로에서 파일이 없거나 읽기 권한이 없으면 예외를 던집니다.</p>
     *
     * @param flMpnId 파일매핑ID
     * @param user    현재 사용자
     * @throws CustomGeneralException 파일이 없는 경우
     * @throws AccessDeniedException 읽기 권한이 없는 경우(403). 쓰기 거부와 동일하게 403으로 매핑합니다.
     */
    public void checkReadAccess(String flMpnId, CustomUserDetails user) {
        Cfilem file = fileRepository.findByFlMpnIdAndDelYn(flMpnId, "N")
                .orElseThrow(() -> new CustomGeneralException("파일을 찾을 수 없습니다: " + flMpnId));

        if (!canRead(file, user)) {
            throw new AccessDeniedException("파일 읽기 권한이 없습니다.");
        }
    }

    /**
     * 파일 읽기 가능 여부 판정 (목록 필터링·단건 권한 검증 공통 사용).
     *
     * <p>파일 종류(PK_COL_NM)별 authorizer로 판정합니다(미등록 종류=관리자만, default-deny).
     * 판정 규칙은 {@link FileReadAuthorizerRegistry}와 각 종류별 authorizer가 소유합니다.</p>
     *
     * @param file 대상 파일 엔티티
     * @param user 현재 사용자 (null이면 비인증)
     * @return 읽기 가능하면 true, 아니면 false
     */
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        return readAuthorizerRegistry.canRead(file, user);
    }
}
