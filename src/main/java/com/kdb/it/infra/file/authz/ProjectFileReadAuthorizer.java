package com.kdb.it.infra.file.authz;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 정보화사업·경상사업 첨부 읽기 판정기 — 활성 사업이 존재하면 인증 사용자 전체.
 *
 * <p>두 사업 구분은 같은 원장({@code BPROJM})의 경상여부({@code ODN_YN})로만 갈리므로 파일 종류도 {@link #PROJECT_KIND} 하나를
 * 공유한다. 사업 상세 API가 인증 사용자 전체에 열려 있으므로 첨부 읽기도 같은 범위를 쓴다(default-deny의 명시적 예외). 다만 부모 사업이 없거나 삭제된
 * 고아 첨부는 읽을 수 없다.
 */
@Component
@RequiredArgsConstructor
public class ProjectFileReadAuthorizer implements FileReadAuthorizer {

    /** 정보화사업·경상사업 첨부 종류(APG_FL_KD_NM). */
    public static final String PROJECT_KIND = "정보화사업";

    private final ProjectRepository projectRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(PROJECT_KIND);
    }

    /**
     * 정보화사업 첨부 읽기 가능 여부.
     *
     * @param file 대상 파일(부모 사업관리번호는 {@code APG_FL_LNK_CTZ_NM} 값)
     * @param user 현재 사용자(null이면 비인증 → 불가)
     * @return 인증 사용자이고 삭제되지 않은 부모 사업이 있으면 true. 부모 ID가 없거나 사업을 찾지 못하면 false
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null || !StringUtils.hasText(file.getApgFlLnkCtzNm())) {
            return false;
        }
        return projectRepository.findByAbusMngNoAndDelYn(file.getApgFlLnkCtzNm(), "N").isPresent();
    }
}
