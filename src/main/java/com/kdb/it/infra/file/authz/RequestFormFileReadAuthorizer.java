package com.kdb.it.infra.file.authz;

import com.kdb.it.common.approval.entity.Cappla;
import com.kdb.it.common.approval.repository.ApplicationMapRepository;
import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.domain.budget.cost.entity.Bcostm;
import com.kdb.it.domain.budget.cost.entity.BcostmId;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.entity.Bprojm;
import com.kdb.it.domain.budget.project.entity.BprojmId;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.migration.request.service.RequestFormSourceFileArchiver;
import com.kdb.it.infra.file.entity.Cfilem;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 편성요청서 반입 원본 파일 읽기 판정기.
 *
 * <p>{@code APG_FL_LNK_CTZ_NM}가 반입받은 신청서번호이므로, 그 신청서가 가리키는 원장({@code TPRMPP_CAPPLA})의 주관부서({@code
 * SVN_DPM_C})를 사용자 부서({@code bbrC})와 비교합니다.
 *
 * <ul>
 *   <li>관리자 → 허용
 *   <li>연결된 <b>활성</b> 원장의 주관부서가 사용자 부서와 같으면 허용
 *   <li>그 외(미인증, 부모 없음, 원장 없음, 매핑·원장이 논리 삭제됨) → 거부
 * </ul>
 *
 * <p>매핑과 원장 모두 {@code DEL_YN='N'}인 것만 봅니다. 권한 판정은 실패 시 거부여야 하므로, 논리 삭제나 재매핑 뒤에도 구 부서 사용자가 원본을 계속
 * 열람하는 경로를 남기지 않습니다(SEC-15).
 *
 * <p>판정은 {@code (APG_FL_KD_NM, APG_FL_LNK_CTZ_NM, user)}의 순수 함수라는 {@link FileReadAuthorizer}의 불변식을
 * 지킵니다. 개별 파일의 다른 속성을 보지 않습니다.
 */
@Component
@RequiredArgsConstructor
public class RequestFormFileReadAuthorizer implements FileReadAuthorizer {

    /** 원천테이블명: 정보화사업·경상사업 마스터. */
    private static final String TABLE_PROJECT = "BPROJM";

    /** 원천테이블명: 전산업무비 마스터. */
    private static final String TABLE_COST = "BCOSTM";

    /** 삭제여부: 미삭제. */
    private static final String NOT_DELETED = "N";

    private final ApplicationMapRepository applicationMapRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;

    @Override
    public Set<String> supportedApgFlKdNms() {
        return Set.of(RequestFormSourceFileArchiver.APG_FL_KD_NM);
    }

    /**
     * 반입 원본 파일 읽기 가능 여부를 판정합니다.
     *
     * @param file 대상 파일. 부모는 {@code APG_FL_LNK_CTZ_NM}의 반입받은 신청서번호입니다
     * @param user 현재 사용자. null이면 비인증으로 거부합니다
     * @return 관리자이거나 연결 원장의 주관부서가 사용자 부서와 같으면 true, 그 외에는 false
     */
    @Override
    public boolean canRead(Cfilem file, CustomUserDetails user) {
        if (user == null) {
            return false;
        }
        if (user.isAdmin()) {
            return true;
        }
        if (file == null) {
            return false;
        }

        String apfMngNo = file.getApgFlLnkCtzNm();
        if (!StringUtils.hasText(apfMngNo) || !StringUtils.hasText(user.getBbrC())) {
            return false;
        }

        return applicationMapRepository.findByApfDcmNoAndDelYn(apfMngNo, NOT_DELETED).stream()
                .anyMatch(map -> user.getBbrC().equals(departmentOf(map)));
    }

    /**
     * 매핑이 가리키는 <b>활성</b> 원장의 주관부서코드를 조회합니다.
     *
     * @param map 신청서와 원장 사이의 매핑
     * @return 지원하는 활성 원장의 주관부서코드. 매핑이 불완전하거나, 지원하지 않는 원천이거나, 원장이 논리 삭제됐으면 null
     */
    private String departmentOf(Cappla map) {
        String table = map.getFntTbNm();
        String key = map.getPkColNm();
        Integer sno = map.getFntTbCrySno();
        if (!StringUtils.hasText(table) || !StringUtils.hasText(key) || sno == null) {
            return null;
        }
        if (TABLE_PROJECT.equals(table)) {
            return projectRepository
                    .findById(new BprojmId(key, sno))
                    .filter(project -> NOT_DELETED.equals(project.getDelYn()))
                    .map(Bprojm::getSvnDpmC)
                    .orElse(null);
        }
        if (TABLE_COST.equals(table)) {
            return costRepository
                    .findById(new BcostmId(key, sno))
                    .filter(cost -> NOT_DELETED.equals(cost.getDelYn()))
                    .map(Bcostm::getCostSvnDpmC)
                    .orElse(null);
        }
        return null;
    }
}
