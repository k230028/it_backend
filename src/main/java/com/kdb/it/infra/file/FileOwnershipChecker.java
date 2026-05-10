package com.kdb.it.infra.file;

import com.kdb.it.exception.CustomGeneralException;
import com.kdb.it.infra.file.entity.Cfilem;
import com.kdb.it.infra.file.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 파일 소유권 검증 컴포넌트 — SEC-02
 *
 * <p>파일 관리번호와 현재 사용자의 사번을 비교하여 본인이 업로드한 파일인지 확인합니다.
 * 타인의 파일에 대한 수정·삭제를 차단하기 위해 사용합니다.</p>
 *
 * <p>소유권 기준: {@code CFILEM.FST_ENR_USID}(최초등록자사번) = 현재 사용자 사번</p>
 */
@Component
@RequiredArgsConstructor
public class FileOwnershipChecker {

    private final FileRepository fileRepository;

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
}
