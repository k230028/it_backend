package com.kdb.it.domain.deliberation.service;

import com.kdb.it.common.system.security.CustomUserDetails;
import com.kdb.it.common.system.security.OwnershipVerifier;
import com.kdb.it.domain.budget.cost.repository.CostRepository;
import com.kdb.it.domain.budget.project.repository.ProjectRepository;
import com.kdb.it.domain.deliberation.dto.DeliberationDto;
import com.kdb.it.domain.deliberation.entity.Bdelim;
import com.kdb.it.domain.deliberation.repository.DeliberationRepository;
import com.kdb.it.infra.eai.config.GweProperties;
import com.kdb.it.infra.eai.dto.EaiRequest;
import com.kdb.it.infra.eai.dto.EaiResult;
import com.kdb.it.infra.eai.dto.GwePayload;
import com.kdb.it.infra.eai.service.EaiService;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 과업심의 서비스. 상태 61→65→69. 대상구분 100=사업/200=전산업무비. 쓰기 주체: 작성중=신청자/부서, 진행중 결과입력=작업자. 상태 전이는 인접만 허용. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DeliberationService {

    static final String STS_DRAFT = "61";
    static final String STS_IN_PROGRESS = "65";
    static final String STS_DONE = "69";
    static final String TGT_PROJECT = "100";
    static final String TGT_COST = "200";

    private final DeliberationRepository deliberationRepository;
    private final ProjectRepository projectRepository;
    private final CostRepository costRepository;
    private final com.kdb.it.domain.budget.project.service.BprojaSyncService bprojaSyncService;
    private final EaiService eaiService;
    private final GweProperties gweProperties;

    /**
     * 과업심의 신규 신청 생성.
     *
     * @param req 신규 신청 요청 (대상구분, 대상관리번호, 요청내용)
     * @param user 요청자 인증 정보
     * @return 채번된 문서번호 (DLB-{YYYY}-{4자리})
     * @throws IllegalArgumentException 알 수 없는 대상구분 또는 대상 미존재
     * @throws IllegalStateException 동일 대상에 진행 중(61/65) 문서 존재
     */
    @Transactional
    public String create(DeliberationDto.CreateRequest req, CustomUserDetails user) {
        validateTarget(req.ioeC(), req.cncdRfrNo());
        if (deliberationRepository.existsByIoeCAndCncdRfrNoAndStsTcInAndDelYn(
                req.ioeC(), req.cncdRfrNo(), List.of(STS_DRAFT, STS_IN_PROGRESS), "N")) {
            throw new IllegalStateException("해당 대상에 진행 중인 과업심의가 이미 있습니다.");
        }
        String docNo =
                String.format(
                        "DLB-%d-%04d", Year.now().getValue(), deliberationRepository.nextDocSeq());
        deliberationRepository.save(
                Bdelim.builder()
                        .docMngNo(docNo)
                        .docVrsSno(1)
                        .lstYn("Y")
                        .ioeC(req.ioeC())
                        .cncdRfrNo(req.cncdRfrNo())
                        .stsTc(STS_DRAFT)
                        .reqCone(req.reqCone())
                        .taskDbrTc(Bdelim.TYPE_CONFIRM)
                        .taskDbrRltTc(Bdelim.RESULT_PENDING)
                        .taskDbrTod(Bdelim.ROUND_FIRST)
                        .taskDbrOmtYn("N")
                        .build());
        if (TGT_PROJECT.equals(req.ioeC())) {
            bprojaSyncService.upsert(req.cncdRfrNo(), docNo, STS_DRAFT);
        }
        return docNo;
    }

    /**
     * 대상 유효성 검증 — 대상구분에 따라 사업 또는 전산업무비 존재 여부 확인.
     *
     * @param ioeC 예산성격구분코드(대상구분)
     * @param cncdRfrNo 관련참조번호(대상관리번호)
     * @throws IllegalArgumentException 알 수 없는 대상구분이거나 대상이 존재하지 않는 경우
     */
    private void validateTarget(String ioeC, String cncdRfrNo) {
        boolean ok;
        if (TGT_PROJECT.equals(ioeC)) {
            ok = projectRepository.existsByAbusMngNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else if (TGT_COST.equals(ioeC)) {
            ok = costRepository.existsByCostBgNoAndLstYnAndDelYn(cncdRfrNo, "Y", "N");
        } else {
            throw new IllegalArgumentException("알 수 없는 대상구분: " + ioeC);
        }
        if (!ok) throw new IllegalArgumentException("대상을 찾을 수 없습니다: " + ioeC + "/" + cncdRfrNo);
    }

    /**
     * 마스터 수정 — 작성중(61) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param req 수정 요청 (요청내용)
     * @param user 요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void update(String docNo, DeliberationDto.UpdateRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc()))
            throw new IllegalStateException("작성중 상태에서만 수정할 수 있습니다.");
        e.updateRequest(req.reqCone());
    }

    /**
     * Soft delete — 작성중(61) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param user 요청자 인증 정보
     * @throws IllegalStateException 작성중이 아닌 경우
     */
    @Transactional
    public void delete(String docNo, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_DRAFT.equals(e.getStsTc()))
            throw new IllegalStateException("작성중 상태에서만 삭제할 수 있습니다.");
        e.delete();
        if (TGT_PROJECT.equals(e.getIoeC())) {
            bprojaSyncService.softDelete(e.getCncdRfrNo(), docNo);
        }
    }

    /**
     * 상태 전이 — 인접 전이만 허용: 61→65, 65→69.
     *
     * @param docNo 문서관리번호
     * @param req 상태 전이 요청 (목표 상태코드)
     * @param user 요청자 인증 정보
     * @throws IllegalStateException 허용되지 않은 전이인 경우
     */
    @Transactional
    public void changeStatus(
            String docNo, DeliberationDto.StatusRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyAdmin(user);
        String from = e.getStsTc(), to = req.stsTc();
        boolean ok =
                (STS_DRAFT.equals(from) && STS_IN_PROGRESS.equals(to))
                        || (STS_IN_PROGRESS.equals(from) && STS_DONE.equals(to));
        if (!ok) throw new IllegalStateException("허용되지 않은 상태 전이입니다: " + from + " → " + to);
        e.changeStatus(to);
        if (TGT_PROJECT.equals(e.getIoeC())) {
            bprojaSyncService.upsert(e.getCncdRfrNo(), docNo, to);
        }
        sendStatusEai("과업심의", docNo, from, to, user);
    }

    /**
     * 심의 결과 입력 — 진행중(65) 상태에서만 가능.
     *
     * @param docNo 문서관리번호
     * @param req 결과 입력 요청 (심의구분, 결과구분, 심의일자 등)
     * @param user 요청자 인증 정보
     * @throws IllegalStateException 진행중이 아닌 경우
     */
    @Transactional
    public void saveResult(
            String docNo, DeliberationDto.ResultRequest req, CustomUserDetails user) {
        Bdelim e = loadCurrent(docNo);
        OwnershipVerifier.verifyOwnerOrAdmin(e.getFstEnrUsid(), user);
        if (!STS_IN_PROGRESS.equals(e.getStsTc()))
            throw new IllegalStateException("진행중 상태에서만 심의 결과를 입력할 수 있습니다.");
        String omt = req.taskDbrOmtYn() == null ? "N" : req.taskDbrOmtYn();
        // taskDbrTc/taskDbrRltTc/taskDbrTod는 NOT NULL 컬럼이다. 프론트가 '' || undefined로
        // 일부 필드만 보내는 부분 저장을 허용하므로, 요청에 값이 없으면(null 또는 '') 기존 값을
        // 유지해 NULL로 덮어쓰지 않는다. Oracle은 ''를 NULL로 저장하므로 blank도 null과
        // 동일하게 취급해야 한다(직접 API 호출은 DTO @Size(max=2) 검증만으로는 ''을 막지 못한다).
        e.updateResult(
                StringUtils.hasText(req.taskDbrTc()) ? req.taskDbrTc() : e.getTaskDbrTc(),
                StringUtils.hasText(req.taskDbrRltTc()) ? req.taskDbrRltTc() : e.getTaskDbrRltTc(),
                req.taskDbrDt(),
                StringUtils.hasText(req.taskDbrTod()) ? req.taskDbrTod() : e.getTaskDbrTod(),
                omt,
                req.taskDbrOmtRsn(),
                req.opnnCone(),
                req.apvTrdnRsnCone());
    }

    /**
     * 상세 조회 — 현재 유효 마스터와 대상명을 함께 반환.
     *
     * @param docNo 문서관리번호
     * @return 상세 응답 DTO
     */
    public DeliberationDto.Detail get(String docNo) {
        var row =
                deliberationRepository
                        .findCurrentDetail(docNo)
                        .orElseThrow(
                                () -> new IllegalArgumentException("과업심의 문서를 찾을 수 없습니다: " + docNo));
        return DeliberationDto.Detail.fromProjection(row);
    }

    /**
     * 목록 조회 — 관리자는 전체, 그 외는 소속 부서 한정.
     *
     * @param stsTc 상태구분코드 필터 (null이면 전체)
     * @param ioeC 대상구분코드 필터 (null이면 전체)
     * @param cncdRfrNo 대상관리번호 필터 (null이면 전체)
     * @param user 요청자 인증 정보
     * @return 목록 항목 리스트
     */
    public List<DeliberationDto.ListItem> list(
            String stsTc, String ioeC, String cncdRfrNo, CustomUserDetails user) {
        String bbrC = user.isAdmin() ? null : user.getBbrC();
        return deliberationRepository.search(stsTc, ioeC, cncdRfrNo, bbrC);
    }

    /**
     * 현재 유효 마스터 조회 (lstYn='Y', delYn='N').
     *
     * @param docNo 문서관리번호
     * @return 현재 유효 마스터
     * @throws IllegalArgumentException 문서를 찾을 수 없는 경우
     */
    Bdelim loadCurrent(String docNo) {
        return deliberationRepository
                .findByDocMngNoAndLstYnAndDelYn(docNo, "Y", "N")
                .orElseThrow(() -> new IllegalArgumentException("과업심의 문서를 찾을 수 없습니다: " + docNo));
    }

    private void sendStatusEai(
            String domainName, String docNo, String from, String to, CustomUserDetails user) {
        try {
            EaiResult result =
                    eaiService.sendEai(
                            EaiRequest.gwe(
                                    gweProperties.ifId(),
                                    GwePayload.builder()
                                            .msgGubun("1")
                                            .recvIds(user.getEno())
                                            .subject("[IT Portal] " + domainName + " 상태 변경")
                                            .contents(
                                                    domainName
                                                            + " 문서 "
                                                            + docNo
                                                            + " 상태가 "
                                                            + from
                                                            + "에서 "
                                                            + to
                                                            + "로 변경되었습니다.")
                                            .sendId("systemalert")
                                            .sendName("IT Portal")
                                            .build()));
            if (!result.success() && !result.skipped()) {
                log.warn(
                        "EAI 발송 실패 — 원 업무 처리는 유지합니다. domain={}, docNo={}, 사유={}",
                        domainName,
                        docNo,
                        result.errorMessage());
            }
        } catch (RuntimeException e) {
            log.warn("EAI 발송 실패 — 원 업무 처리는 유지합니다.", e);
        }
    }
}
