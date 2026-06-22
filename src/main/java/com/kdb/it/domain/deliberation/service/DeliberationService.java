package com.kdb.it.domain.deliberation.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.repository.DeliberationRepository;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과업심의 서비스. 상태 51→52→59. 대상구분 100=사업/200=전산업무비.
 * 쓰기 주체: 작성중=신청자/부서, 진행중 결과입력=작업자. 상태 전이는 인접만 허용.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliberationService {

    static final String STS_DRAFT = "51";
    static final String STS_IN_PROGRESS = "52";
    static final String STS_DONE = "59";
    static final String TGT_PROJECT = "100";
    static final String TGT_COST = "200";

    private final DeliberationRepository deliberationRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;

    /**
     * 과업심의 신규 신청 생성.
     *
     * @param req  신규 신청 요청 (대상구분, 대상관리번호, 요청내용)
     * @param user 요청자 인증 정보
     * @return 채번된 문서번호 (DLB-{YYYY}-{4자리})
     * @throws IllegalArgumentException 알 수 없는 대상구분 또는 대상 미존재
     * @throws IllegalStateException    동일 대상에 진행 중(51/52) 문서 존재
     */
    @Transactional
    public String create(DeliberationDto.CreateRequest req, CustomUserDetails user) {
        validateTarget(req.bgPrnTc(), req.cncdRfrNo());
        if (deliberationRepository.existsByBgPrnTcAndCncdRfrNoAndStsTcInAndDelYn(
                req.bgPrnTc(), req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 대상에 진행 중인 과업심의가 이미 있습니다.");
        }
        String docNo = String.format("DLB-%d-%04d", Year.now().getValue(), deliberationRepository.nextDocSeq());
        deliberationRepository.save(Bdelim.builder()
                .docMngNo(docNo).docVrsSno(1).lstYn("Y")
                .bgPrnTc(req.bgPrnTc()).cncdRfrNo(req.cncdRfrNo())
                .stsTc(STS_DRAFT).reqCone(req.reqCone()).taskDbrOmtYn("N").build());
        return docNo;
    }

    /**
     * 대상 유효성 검증 — 대상구분에 따라 사업 또는 전산업무비 존재 여부 확인.
     *
     * @param bgPrnTc   예산성격구분코드(대상구분)
     * @param cncdRfrNo 관련참조번호(대상관리번호)
     * @throws IllegalArgumentException 알 수 없는 대상구분이거나 대상이 존재하지 않는 경우
     */
    private void validateTarget(String bgPrnTc, String cncdRfrNo) {
        boolean ok;
        if (TGT_PROJECT.equals(bgPrnTc)) {
            ok = projectRepository.existsByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else if (TGT_COST.equals(bgPrnTc)) {
            ok = costRepository.existsByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else {
            throw new IllegalArgumentException("알 수 없는 대상구분: " + bgPrnTc);
        }
        if (!ok) throw new IllegalArgumentException("대상을 찾을 수 없습니다: " + bgPrnTc + "/" + cncdRfrNo);
    }

    /**
     * 마스터 수정 — 작성중(51) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param req   수정 요청 (요청내용)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void update(String docNo, DeliberationDto.UpdateRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        e.updateRequest(req.reqCone());
    }

    /**
     * Soft delete — 작성중(51) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc())) throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        e.delete();
    }

    /**
     * 상태 전이 — 인접 전이만 허용: 51→52, 52→59.
     *
     * @param docNo 문서관리번호
     * @param req   상태 전이 요청 (목표 상태코드)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    @Transactional
    public void changeStatus(String docNo, DeliberationDto.StatusRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        String from = e.getStsTc(), to = req.stsTc();
        boolean ok = (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!ok) throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        e.changeStatus(to);
    }

    /**
     * 심의 결과 입력 — 진행중(52) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param req   결과 입력 요청 (심의구분, 결과구분, 심의일자 등)
     * @param user  요청자 인증 정보
     * @throws IllegalStateException 진행중이 아닌 경우
     */
    @Transactional
    public void saveResult(String docNo, DeliberationDto.ResultRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_IN_PROGRESS.equals(e.getStsTc())) throw new IllegalStateException("진행중 상태에서만 심의 결과를 입력할 수 있습니다.");
        String omt = req.taskDbrOmtYn() == null ? "N" : req.taskDbrOmtYn();
        e.updateResult(req.taskDbrTc(), req.taskDbrRltTc(), req.taskDbrDt(), req.taskDbrTod(),
                omt, req.taskDbrOmtRsn(), req.opnnCone(), req.apvTrdnRsnCone());
    }

    /**
     * 상세 조회 — 현재 유효 마스터와 대상명을 함께 반환.
     *
     * @param docNo 문서관리번호
     * @return 상세 응답 DTO
     */
    public DeliberationDto.Detail get(String docNo) {
        Bdelim e = loadCurrent(docNo);
        String tgtNm = resolveTargetName(e.getBgPrnTc(), e.getCncdRfrNo());
        return new DeliberationDto.Detail(
                e.getDocMngNo(), e.getDocVrsSno(), e.getBgPrnTc(), e.getCncdRfrNo(), tgtNm,
                e.getStsTc(), e.getReqCone(), e.getTaskDbrTc(), e.getTaskDbrRltTc(), e.getTaskDbrDt(),
                e.getTaskDbrTod(), e.getTaskDbrOmtYn(), e.getTaskDbrOmtRsn(), e.getOpnnCone(), e.getApvTrdnRsnCone(),
                e.getFstEnrUsid(), e.getFstEnrDtm());
    }

    /**
     * 대상명 해석.
     *
     * <p>사업(100)이면 {@code ABUS_NM}, 전산업무비(200)이면 계약명({@code CTT_NM}).
     * 해당 레코드가 없으면 null을 반환하며, 프론트엔드가 대상번호로 폴백 표시합니다.</p>
     *
     * @param bgPrnTc   예산성격구분코드(대상구분)
     * @param cncdRfrNo 관련참조번호(대상관리번호)
     * @return 대상 명칭 (없으면 null)
     */
    private String resolveTargetName(String bgPrnTc, String cncdRfrNo) {
        if (TGT_PROJECT.equals(bgPrnTc)) {
            return projectRepository.findByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N")
                    .map(p -> p.getAbusNm()).orElse(null);
        }
        if (TGT_COST.equals(bgPrnTc)) {
            // 전산업무비 명칭은 계약명(CTT_NM). 전산업무비 레코드의 주요 식별자이며
            // 실제 계약서상 명칭(예: "2026년 서버 유지보수 계약")이 사용자에게 표시되는 이름이다.
            return costRepository.findByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N")
                    .map(c -> c.getCttNm()).orElse(null);
        }
        return null;
    }

    /**
     * 목록 조회 — 관리자는 전체, 그 외는 소속 부서 한정.
     *
     * @param stsTc     상태구분코드 필터 (null이면 전체)
     * @param bgPrnTc   대상구분코드 필터 (null이면 전체)
     * @param cncdRfrNo 대상관리번호 필터 (null이면 전체)
     * @param user      요청자 인증 정보
     * @return 목록 항목 리스트
     */
    public List<DeliberationDto.ListItem> list(String stsTc, String bgPrnTc, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return deliberationRepository.search(stsTc, bgPrnTc, cncdRfrNo, bbrC);
    }

    /**
     * 현재 유효 마스터 조회 (lstYn='Y', delYn='N').
     *
     * @param docNo 문서관리번호
     * @return 현재 유효 마스터
     * @throws IllegalArgumentException 문서를 찾을 수 없는 경우
     */
    Bdelim loadCurrent(String docNo) {
        return deliberationRepository.findByDocMngNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("과업심의 문서를 찾을 수 없습니다: " + docNo));
    }
}
